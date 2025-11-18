package com.filemanager.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.*;
import java.sql.ResultSetMetaData;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BackupService {

    private final SystemSettingService systemSettingService;
    private final JdbcTemplate jdbcTemplate;
    private final BlobService blobService;
    private final Environment env;

    @Value("${file.storage.path}")
    private String storagePath;

    private static final int BUF = 8192;

    public interface ProgressCallback { void onStart(int totalBlobs, String stage); void onBlobCopied(int copiedCount); void onStage(String stage); }
    public interface CancelChecker { boolean cancelled(); }

    public void exportToStream(String format, boolean includeThumbnails, String mode, OutputStream os) {
        exportToStreamWithProgress(format, includeThumbnails, mode, os, null, null);
    }

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public long exportToServerWithProgress(String destDir, String format, boolean includeThumbnails, String mode, ProgressCallback cb, CancelChecker cancel) {
        Path dir = Path.of(destDir);
        if (cb != null) cb.onStage("export:whitelist");
        validateWhitelist(dir);
        try { if (!Files.exists(dir)) Files.createDirectories(dir); } catch (IOException e) { throw new RuntimeException("创建目录失败", e); }
        // 二次校验（realpath 防符号链接逃逸）
        try {
            if (cb != null) cb.onStage("export:realpath");
            List<String> wl = systemSettingService.getWhitelistDirs();
            Path targetReal = dir.toRealPath(java.nio.file.LinkOption.NOFOLLOW_LINKS);
            boolean ok = false;
            for (String w : wl) {
                try {
                    Path baseReal = Path.of(w).toRealPath(java.nio.file.LinkOption.NOFOLLOW_LINKS);
                    if (targetReal.startsWith(baseReal)) { ok = true; break; }
                } catch (Exception ignore) {}
            }
            if (!ok) throw new RuntimeException("目标路径不在白名单内(实路径)");
        } catch (IOException e) { throw new RuntimeException("白名单校验失败", e); }
        String ts = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS").format(new Date());
        String rnd = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        Path out = dir.resolve("backup-" + ts + "-" + rnd + ".zip");
        if (cb != null) cb.onStage("export:openOut");
        try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(out, StandardOpenOption.CREATE_NEW))) {
            exportToStreamWithProgress(format, includeThumbnails, mode, os, cb, cancel);
        } catch (IOException e) {
            String msg = "写入备份文件失败: " + out + " :: " + e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" : e.getMessage());
            throw new RuntimeException(msg, e);
        }
        try { return Files.size(out); } catch (IOException e) { return -1L; }
    }

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    private void exportToStreamWithProgress(String format, boolean includeThumbnails, String mode, OutputStream os, ProgressCallback cb, CancelChecker cancel) {
        Objects.requireNonNull(os, "OutputStream cannot be null");
        boolean online = (mode == null || mode.isBlank()) || "online".equalsIgnoreCase(mode);
        // 冻结后台清理（对用户无感）
        freezeForExport(true);
        long blobBytes = 0L;
        int blobCount = 0;
        int thumbCount = 0;
        java.util.List<String> missing = new ArrayList<>();
        Map<String, Long> tableCounts = new LinkedHashMap<>();
        String dbName = resolveDbName();

        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(os))) {
            if (cb != null) cb.onStage("export:openZip");
            // 1) DB 导出（SQL 优先，回退 JSON）
            String finalFormat = (format == null || format.isBlank()) ? "json" : format.toLowerCase();
            if (cb != null) cb.onStage("export:db");
            if ("sql".equals(finalFormat) && tryWriteDbSql(zip)) {
                // 使用了 db.sql
            } else {
                // 写 db.json
                writeDbJson(zip, dbName, tableCounts);
                finalFormat = "json";
            }

            // 2) 收集 R0
            Set<String> hashes = collectBlobHashes();
            if (cb != null) cb.onStart(hashes.size(), "export:collect");

            // 3) 复制 blobs
            int copied = 0;
            for (String h : hashes) {
                if (cancel != null && cancel.cancelled()) throw new RuntimeException("CANCELLED");
                if (h == null || h.isBlank()) continue;
                if (cb != null) try { cb.onStage("export:copy:" + h.substring(0, Math.min(8, h.length()))); } catch (Exception ignore) {}
                Path src = blobService.blobPath(h);
                if (!Files.exists(src)) {
                    // fallback: 查 Blob 表的 path
                    try {
                        com.filemanager.entity.Blob b = blobService.find(h);
                        if (b != null && b.getPath() != null && Files.exists(Path.of(b.getPath()))) {
                            src = Path.of(b.getPath());
                        }
                    } catch (Exception ignore) {}
                }
                if (!Files.exists(src)) {
                    missing.add(h);
                    continue;
                }
                ZipEntry e = new ZipEntry("blobs/" + shardPath(h));
                zip.putNextEntry(e);
                blobBytes += copyFileToZip(src, zip);
                zip.closeEntry();
                blobCount++;
                copied++;
                if (cb != null) cb.onBlobCopied(copied);
            }

            // 4) thumbnails（默认包含）
            if (includeThumbnails) {
                if (cb != null) cb.onStage("export:thumb");
                Set<String> thumbPaths = collectThumbnailPaths();
                for (String p : thumbPaths) {
                    if (cancel != null && cancel.cancelled()) throw new RuntimeException("CANCELLED");
                    if (p == null || p.isBlank()) continue;
                    Path tp = Path.of(p);
                    if (!Files.exists(tp)) continue;
                    String rel = relativizeUnderStorage(tp);
                    ZipEntry e = new ZipEntry("thumbnails/" + rel);
                    zip.putNextEntry(e);
                    copyFileToZip(tp, zip);
                    zip.closeEntry();
                    thumbCount++;
                }
            }

            // 5) manifest
            if (cb != null) cb.onStage("export:manifest");
            writeManifest(zip, dbName, finalFormat, online ? "online" : "maintenance", tableCounts, blobCount, blobBytes, thumbCount, missing);
            zip.finish();

        } catch (IOException e) {
            String msg = "导出失败: " + e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" : e.getMessage());
            throw new RuntimeException(msg, e);
        } finally {
            freezeForExport(false);
        }
    }

    public long exportToServer(String destDir, String format, boolean includeThumbnails, String mode) {
        return exportToServerWithProgress(destDir, format, includeThumbnails, mode, null, null);
    }

    // =============== helpers ===============

    private void freezeForExport(boolean enable) {
        try { systemSettingService.setBackupGcFrozen(enable, null); } catch (Exception ignore) {}
        try { systemSettingService.setAdminPurgeFrozen(enable, null); } catch (Exception ignore) {}
        try { systemSettingService.incBackupGcFreezeCount(enable ? +1 : -1, null); } catch (Exception ignore) {}
        try { systemSettingService.incAdminPurgeFreezeCount(enable ? +1 : -1, null); } catch (Exception ignore) {}
    }

    private boolean tryWriteDbSql(ZipOutputStream zip) {
        String mysqldump = "mysqldump";
        try {
            Process test = new ProcessBuilder(mysqldump, "--version").start();
            test.waitFor();
        } catch (Exception e) {
            return false;
        }
        String url = env.getProperty("spring.datasource.url", "");
        String user = env.getProperty("spring.datasource.username", "");
        String pass = env.getProperty("spring.datasource.password", "");
        String host = "127.0.0.1";
        String port = "3306";
        String db = resolveDbName();
        try {
            String u = url == null ? "" : url;
            int i = u.indexOf("jdbc:mysql://");
            if (i >= 0) {
                String rest = u.substring("jdbc:mysql://".length());
                String hp = rest.split("/", 2)[0];
                if (hp.contains(":")) { String[] pp = hp.split(":"); host = pp[0]; port = pp[1]; } else { host = hp; }
            }
        } catch (Exception ignore) {}

        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("mysqldump");
            cmd.add("--single-transaction");
            cmd.add("--quick");
            cmd.add("--tz-utc");
            cmd.add("--hex-blob");
            cmd.add("-h"); cmd.add(host);
            cmd.add("-P"); cmd.add(port);
            cmd.add("-u"); cmd.add(user);
            if (pass != null && !pass.isBlank()) cmd.add("-p" + pass);
            cmd.add(db);

            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            ZipEntry e = new ZipEntry("db.sql");
            zip.putNextEntry(e);
            try (InputStream in = p.getInputStream()) {
                byte[] buf = new byte[BUF]; int n; while ((n = in.read(buf)) != -1) zip.write(buf, 0, n);
            }
            zip.closeEntry();
            int code = p.waitFor();
            return code == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void writeDbJson(ZipOutputStream zip, String dbName, Map<String, Long> tableCounts) throws IOException {
        ZipEntry e = new ZipEntry("db.json");
        zip.putNextEntry(e);
        JsonFactory jf = new JsonFactory();
        OutputStream nonClosing = new java.io.FilterOutputStream(zip) {
            @Override public void close() throws IOException { this.flush(); }
        };
        try (JsonGenerator g = jf.createGenerator(nonClosing)) {
            try { g.disable(com.fasterxml.jackson.core.JsonGenerator.Feature.AUTO_CLOSE_TARGET); } catch (Exception ignore) {}
            g.writeStartObject();
            g.writeStringField("database", dbName);
            g.writeObjectFieldStart("tables");
            // 查询库下所有表
            List<String> tables = jdbcTemplate.query("SELECT table_name FROM information_schema.tables WHERE table_schema = ? ORDER BY table_name", ps -> ps.setString(1, dbName), (rs, rn) -> rs.getString(1));
            for (String tb : tables) {
                g.writeArrayFieldStart(tb);
                final long[] count = {0L};
                try {
                    jdbcTemplate.query(con -> {
                        java.sql.PreparedStatement ps = con.prepareStatement("SELECT * FROM `" + tb + "`", java.sql.ResultSet.TYPE_FORWARD_ONLY, java.sql.ResultSet.CONCUR_READ_ONLY);
                        try { ps.setFetchSize(Integer.MIN_VALUE); } catch (Exception ignore) {}
                        return ps;
                    }, rs -> {
                        try {
                            java.sql.ResultSetMetaData md = rs.getMetaData();
                            int cols = md.getColumnCount();
                            while (rs.next()) {
                                g.writeStartObject();
                                for (int c = 1; c <= cols; c++) {
                                    String name = md.getColumnLabel(c);
                                    Object val = rs.getObject(c);
                                    writeJsonValue(g, name, val);
                                }
                                g.writeEndObject();
                                count[0]++;
                            }
                        } catch (IOException ex) {
                            throw new UncheckedIOException(ex);
                        }
                        return null;
                    });
                } catch (UncheckedIOException ex) {
                    throw ex.getCause();
                }
                g.writeEndArray();
                tableCounts.put(tb, count[0]);
            }
            g.writeEndObject(); // tables
            g.writeEndObject();
            g.flush();
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
        zip.closeEntry();
    }

    private void writeJsonValue(JsonGenerator g, String name, Object val) throws IOException {
        g.writeFieldName(name);
        if (val == null) { g.writeNull(); return; }
        if (val instanceof byte[] b) { g.writeBinary(b); return; }
        if (val instanceof java.sql.Timestamp t) { g.writeString(t.toInstant().toString()); return; }
        if (val instanceof java.sql.Date d) { g.writeString(d.toString()); return; }
        if (val instanceof java.sql.Time t) { g.writeString(t.toString()); return; }
        if (val instanceof Number n) { g.writeNumber(n.toString()); return; }
        if (val instanceof Boolean b) { g.writeBoolean(b); return; }
        g.writeString(String.valueOf(val));
    }

    private Set<String> collectBlobHashes() {
        Set<String> set = new LinkedHashSet<>();
        // file_versions
        try {
            List<String> v = jdbcTemplate.query("SELECT DISTINCT blob_hash FROM file_versions WHERE blob_hash IS NOT NULL", (rs, rn) -> rs.getString(1));
            for (String s : v) if (s != null && !s.isBlank()) set.add(s.toLowerCase());
        } catch (Exception ignore) {}
        // files（兼容旧数据）
        try {
            List<String> f = jdbcTemplate.query("SELECT DISTINCT file_hash FROM files WHERE file_hash IS NOT NULL", (rs, rn) -> rs.getString(1));
            for (String s : f) if (s != null && !s.isBlank()) set.add(s.toLowerCase());
        } catch (Exception ignore) {}
        return set;
    }

    private Set<String> collectThumbnailPaths() {
        Set<String> set = new LinkedHashSet<>();
        try {
            List<String> v = jdbcTemplate.query("SELECT DISTINCT thumbnail_path FROM blobs WHERE thumbnail_path IS NOT NULL", (rs, rn) -> rs.getString(1));
            for (String s : v) if (s != null && !s.isBlank()) set.add(s);
        } catch (Exception ignore) {}
        try {
            List<String> v = jdbcTemplate.query("SELECT DISTINCT thumbnail_path FROM files WHERE thumbnail_path IS NOT NULL", (rs, rn) -> rs.getString(1));
            for (String s : v) if (s != null && !s.isBlank()) set.add(s);
        } catch (Exception ignore) {}
        return set;
    }

    private String shardPath(String hash) {
        String h = hash.toLowerCase();
        String d1 = h.length() >= 2 ? h.substring(0, 2) : "xx";
        String d2 = h.length() >= 4 ? h.substring(2, 4) : "yy";
        return d1 + "/" + d2 + "/" + h;
    }

    private String relativizeUnderStorage(Path p) {
        try {
            Path root = Path.of(storagePath).toAbsolutePath().normalize();
            Path abs = p.toAbsolutePath().normalize();
            if (abs.startsWith(root)) {
                return root.relativize(abs).toString().replace('\\', '/');
            }
        } catch (Exception ignore) {}
        String name = p.getFileName() != null ? p.getFileName().toString() : ("f-" + Instant.now().toEpochMilli());
        try {
            long size = Files.exists(p) ? Files.size(p) : 0L;
            long mt = Files.exists(p) ? Files.getLastModifiedTime(p).toMillis() : 0L;
            String raw = p.toAbsolutePath().toString() + "#" + size + "#" + mt;
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 12 && i < d.length; i++) sb.append(String.format("%02x", d[i]));
            return "_external/" + sb + "-" + name;
        } catch (Exception ignore) {}
        return "_external/" + name;
    }

    private long copyFileToZip(Path src, ZipOutputStream zip) throws IOException {
        long bytes = 0L;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(src))) {
            byte[] buf = new byte[BUF];
            int n; while ((n = in.read(buf)) != -1) { zip.write(buf, 0, n); bytes += n; }
        }
        return bytes;
    }

    private void writeManifest(ZipOutputStream zip,
                               String dbName,
                               String format,
                               String mode,
                               Map<String, Long> tableCounts,
                               int blobCount,
                               long blobBytes,
                               int thumbCount,
                               List<String> missing) throws IOException {
        ZipEntry e = new ZipEntry("manifest.json");
        zip.putNextEntry(e);
        JsonFactory jf = new JsonFactory();
        OutputStream nonClosing = new java.io.FilterOutputStream(zip) {
            @Override public void close() throws IOException { this.flush(); }
        };
        try (JsonGenerator g = jf.createGenerator(nonClosing)) {
            try { g.disable(com.fasterxml.jackson.core.JsonGenerator.Feature.AUTO_CLOSE_TARGET); } catch (Exception ignore) {}
            g.writeStartObject();
            g.writeStringField("mode", mode);
            g.writeStringField("format", format);
            g.writeStringField("database", dbName);
            g.writeStringField("exportedAt", Instant.now().toString());
            g.writeStringField("storagePath", storagePath);
            g.writeObjectFieldStart("tableCounts");
            for (Map.Entry<String, Long> en : tableCounts.entrySet()) {
                g.writeNumberField(en.getKey(), en.getValue());
            }
            g.writeEndObject();
            g.writeNumberField("blobRefCount", blobCount);
            g.writeNumberField("blobBytes", blobBytes);
            g.writeNumberField("thumbnailCount", thumbCount);
            g.writeArrayFieldStart("diff.missingBlobs");
            for (String h : missing) g.writeString(h);
            g.writeEndArray();
            g.writeEndObject();
            g.flush();
        }
        zip.closeEntry();
    }

    private String resolveDbName() {
        String url = env.getProperty("spring.datasource.url", "");
        if (url == null) return "";
        try {
            String u = url;
            int i = u.indexOf("jdbc:mysql://");
            if (i >= 0) {
                String rest = u.substring("jdbc:mysql://".length());
                String[] parts = rest.split("/", 2);
                if (parts.length >= 2) {
                    String dbAndParams = parts[1];
                    String db = dbAndParams.split("\\?", 2)[0];
                    return db;
                }
            }
        } catch (Exception ignore) {}
        return "";
    }

    private void validateWhitelist(Path dir) {
        List<String> whitelist = systemSettingService.getWhitelistDirs();
        if (whitelist.isEmpty()) throw new RuntimeException("未配置服务器导出白名单目录");
        Path target = dir.toAbsolutePath().normalize();
        boolean ok = false;
        for (String w : whitelist) {
            try {
                Path base = Path.of(w).toAbsolutePath().normalize();
                if (target.startsWith(base)) { ok = true; break; }
            } catch (Exception ignore) {}
        }
        if (!ok) throw new RuntimeException("目标路径不在白名单内");
    }
}
