package com.filemanager.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.*;
import java.nio.file.*;
import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
public class ImportService {

    private final JdbcTemplate jdbcTemplate;
    private final SystemSettingService systemSettingService;
    private final Environment env;

    @org.springframework.beans.factory.annotation.Value("${file.storage.path}")
    private String storagePath;

    public Map<String, Object> precheck(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("备份包为空");
        Map<String, Object> result = new LinkedHashMap<>();
        boolean hasDbSql = false, hasDbJson = false, hasManifest = false;
        long blobCount = 0L, blobBytes = 0L, thumbCount = 0L, totalBytes = 0L;
        Map<String, Object> manifest = new LinkedHashMap<>();
        try (InputStream is = new BufferedInputStream(file.getInputStream()); ZipInputStream zin = new ZipInputStream(is)) {
            ZipEntry e; byte[] buf = new byte[8192];
            while ((e = zin.getNextEntry()) != null) {
                String name = e.getName();
                long size = (e.getSize() > 0 ? e.getSize() : 0);
                totalBytes += Math.max(0, size);
                if ("db.sql".equals(name)) hasDbSql = true;
                if ("db.json".equals(name)) hasDbJson = true;
                if ("manifest.json".equals(name)) {
                    hasManifest = true;
                    // 读取 manifest 内容（小文件）
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    int n; while ((n = zin.read(buf)) != -1) baos.write(buf, 0, n);
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> m = new ObjectMapper().readValue(baos.toByteArray(), Map.class);
                        manifest.putAll(m);
                    } catch (Exception ignore) {}
                } else if (name.startsWith("blobs/")) {
                    if (!e.isDirectory()) {
                        blobCount++;
                        blobBytes += Math.max(0, size);
                    }
                } else if (name.startsWith("thumbnails/")) {
                    if (!e.isDirectory()) thumbCount++;
                }
                zin.closeEntry();
            }
        } catch (IOException ex) {
            throw new RuntimeException("预检失败", ex);
        }
        result.put("hasDbSql", hasDbSql);
        result.put("hasDbJson", hasDbJson);
        result.put("hasManifest", hasManifest);
        result.put("blobCountInZip", blobCount);
        result.put("blobBytesInZip", blobBytes);
        result.put("thumbnailCountInZip", thumbCount);
        result.put("zipBytes", totalBytes);
        if (!manifest.isEmpty()) result.put("manifest", manifest);
        return result;
    }

    @Transactional
    public Map<String, Object> importBackup(MultipartFile file, boolean rebuildThumbnails) {
        return importBackupAsJob(file, rebuildThumbnails, null, null);
    }

    public interface ProgressCallback { void onStage(String stage, int percent); }
    public interface CancelChecker { boolean cancelled(); }

    public Map<String, Object> importBackupAsJob(MultipartFile file, boolean rebuildThumbnails, ProgressCallback cb, CancelChecker cancel) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("备份包为空");
        Path tempDir = null;
        Map<String, Object> stats = new LinkedHashMap<>();
        boolean maintenanceSwitched = false;
        try {
            // 切换维护模式（只读）
            try { systemSettingService.setBoolean(SystemSettingService.KEY_UPLOAD_ALLOW_ALL, systemSettingService.isUploadAllowAll(), null); } catch (Exception ignore) {}
            try {
                setMaintenance(true);
                // 强维护级别：all（拦截读写）
                systemSettingService.setMaintenanceLevel("all", null);
                maintenanceSwitched = true;
            } catch (Exception ignore) {}
            // 冻结后台清理
            try { systemSettingService.setBackupGcFrozen(true, null); } catch (Exception ignore) {}
            try { systemSettingService.setAdminPurgeFrozen(true, null); } catch (Exception ignore) {}
            if (cb != null) cb.onStage("import:maintenance", 5);

            // 解包到临时目录
            tempDir = Files.createTempDirectory("efm-import-" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));
            unzipTo(file.getInputStream(), tempDir);
            if (cb != null) cb.onStage("import:unzip", 15);

            // 读取 manifest（若有）
            Map<String, Object> manifest = readManifest(tempDir);

            // 导入 DB
            Path dbSql = tempDir.resolve("db.sql");
            Path dbJson = tempDir.resolve("db.json");
            if (Files.exists(dbSql)) {
                execSql(dbSql);
                stats.put("dbFormat", "sql");
            } else if (Files.exists(dbJson)) {
                importDbJson(dbJson);
                stats.put("dbFormat", "json");
            } else {
                throw new RuntimeException("备份包缺少 db.sql 或 db.json");
            }
            if (cb != null) cb.onStage("import:database", 55);

            // 合并/覆盖 blobs
            long totalBlobs = countFilesUnder(tempDir.resolve("blobs"));
            long copiedBlobs = copyBlobsWithProgress(tempDir.resolve("blobs"), totalBlobs, cb, cancel);
            stats.put("blobsCopied", copiedBlobs);
            if (cb != null) cb.onStage("import:blobs", 85);

            // 恢复 thumbnails（若存在）
            long copiedThumbs = copyThumbnails(tempDir.resolve("thumbnails"));
            stats.put("thumbnailsCopied", copiedThumbs);
            if (cb != null) cb.onStage("import:thumbnails", 92);

            // 后检验：DB 引用的 Blob 均存在
            List<String> missing = verifyAllBlobsPresent();
            stats.put("postCheck.missingBlobs", missing);
            stats.put("message", missing.isEmpty() ? "导入成功" : "导入完成但存在缺失，请查看清单");
            if (cb != null) cb.onStage("import:verify", 100);
            return stats;
        } catch (Exception e) {
            throw new RuntimeException("导入失败", e);
        } finally {
            // 清理临时目录
            if (tempDir != null) {
                try { deleteRecursively(tempDir); } catch (Exception ignore) {}
            }
            // 退出维护模式
            if (maintenanceSwitched) {
                try { setMaintenance(false); } catch (Exception ignore) {}
                try { systemSettingService.setMaintenanceLevel("write-only", null); } catch (Exception ignore) {}
            }
            // 解除冻结
            try { systemSettingService.setBackupGcFrozen(false, null); } catch (Exception ignore) {}
            try { systemSettingService.setAdminPurgeFrozen(false, null); } catch (Exception ignore) {}
        }
    }

    // =============== helpers ===============

    private void unzipTo(InputStream in, Path base) throws IOException {
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(in))) {
            ZipEntry e; byte[] buf = new byte[8192];
            while ((e = zin.getNextEntry()) != null) {
                String name = e.getName();
                Path out = base.resolve(name).normalize();
                if (!out.startsWith(base)) throw new SecurityException("非法路径：" + name);
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    if (!Files.exists(out.getParent())) Files.createDirectories(out.getParent());
                    try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(out, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
                        int n; while ((n = zin.read(buf)) != -1) os.write(buf, 0, n);
                    }
                }
                zin.closeEntry();
            }
        }
    }

    private Map<String, Object> readManifest(Path base) {
        Path mf = base.resolve("manifest.json");
        if (!Files.exists(mf)) return Map.of();
        try (InputStream in = Files.newInputStream(mf)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = new ObjectMapper().readValue(in, Map.class);
            return m == null ? Map.of() : m;
        } catch (Exception e) { return Map.of(); }
    }

    private void execSql(Path sqlFile) throws Exception {
        DataSource ds = jdbcTemplate.getDataSource();
        if (ds == null) throw new IllegalStateException("DataSource 不可用");
        // 预检 SQL：拒绝数据库级语句
        String head = readHead(sqlFile, 262144);
        if (containsDbLevelStatements(head)) {
            throw new IllegalStateException("检测到数据库级语句（CREATE/DROP DATABASE 或 USE），请使用 JSON 备份或清洗 SQL 后再导入");
        }
        try (Connection c = ds.getConnection()) {
            org.springframework.core.io.FileSystemResource res = new org.springframework.core.io.FileSystemResource(sqlFile.toFile());
            org.springframework.core.io.support.EncodedResource er = new org.springframework.core.io.support.EncodedResource(res, java.nio.charset.StandardCharsets.UTF_8);
            ScriptUtils.executeSqlScript(c, er);
        }
    }

    private String readHead(Path sqlFile, int maxBytes) {
        try (InputStream in = Files.newInputStream(sqlFile)) {
            byte[] buf = in.readNBytes(maxBytes);
            return new String(buf, java.nio.charset.StandardCharsets.UTF_8).toUpperCase();
        } catch (Exception e) { return ""; }
    }
    private boolean containsDbLevelStatements(String s) {
        if (s == null) return false;
        return s.contains("CREATE DATABASE") || s.contains("DROP DATABASE") || s.matches("(?s).*[\\n\\r;]\\s*USE\\s+[`\\w].*");
    }

    private void importDbJson(Path jsonFile) throws Exception {
        String dbName = resolveDbName();
        // 先关闭外键检查与 TRUNCATE ALL
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0");
        try {
            java.util.Set<String> blacklist = new java.util.HashSet<>();
            blacklist.add("backup_jobs");
            List<String> tables = jdbcTemplate.query("SELECT table_name FROM information_schema.tables WHERE table_schema = ? ORDER BY table_name", ps -> ps.setString(1, dbName), (rs, rn) -> rs.getString(1));
            for (String tb : tables) {
                if (blacklist.contains(tb)) continue;
                try { jdbcTemplate.execute("TRUNCATE TABLE `" + tb + "`"); } catch (Exception ignore) {}
            }
        } finally {
            // 不在此处开启，等数据写完再开启
        }

        JsonFactory jf = new JsonFactory();
        try (InputStream in = Files.newInputStream(jsonFile); JsonParser p = jf.createParser(in)) {
            // 结构：{ database:..., tables: { table: [ {..},{..} ], ... } }
            if (p.nextToken() != JsonToken.START_OBJECT) throw new IllegalStateException("db.json 结构不正确");
            while (p.nextToken() != JsonToken.END_OBJECT) {
                String field = p.getCurrentName();
                if (field == null) { p.nextToken(); continue; }
                p.nextToken();
                if ("tables".equals(field) && p.currentToken() == JsonToken.START_OBJECT) {
                    while (p.nextToken() != JsonToken.END_OBJECT) {
                        String tableName = p.getCurrentName();
                        p.nextToken();
                        if (p.currentToken() != JsonToken.START_ARRAY) { p.skipChildren(); continue; }
                        importTableArray(tableName, p);
                    }
                } else {
                    p.skipChildren();
                }
            }
        }

        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=1");
    }

    private void importTableArray(String tableName, JsonParser p) throws IOException {
        // 查询该表 BLOB 列，便于二进制解码
        Set<String> blobCols = getBlobColumns(tableName);
        // 延迟构建 INSERT 语句
        List<String> columnsOrder = null;
        java.sql.PreparedStatement ps = null;
        java.sql.Connection conn = null;
        try {
            conn = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection();
            while (p.nextToken() != JsonToken.END_ARRAY) {
                if (p.currentToken() != JsonToken.START_OBJECT) { p.skipChildren(); continue; }
                Map<String, Object> row = new LinkedHashMap<>();
                while (p.nextToken() != JsonToken.END_OBJECT) {
                    String col = p.getCurrentName();
                    JsonToken t = p.nextToken();
                    Object v;
                    if (t == JsonToken.VALUE_NULL) v = null;
                    else if (t == JsonToken.VALUE_NUMBER_INT || t == JsonToken.VALUE_NUMBER_FLOAT) v = p.getNumberValue();
                    else if (t == JsonToken.VALUE_TRUE || t == JsonToken.VALUE_FALSE) v = p.getBooleanValue();
                    else v = p.getValueAsString();
                    row.put(col, v);
                }
                if (columnsOrder == null) {
                    columnsOrder = new ArrayList<>(row.keySet());
                    String colsSql = String.join(",", columnsOrder.stream().map(c -> "`" + c + "`").toList());
                    String qs = String.join(",", java.util.Collections.nCopies(columnsOrder.size(), "?"));
                    String sql = "INSERT INTO `" + tableName + "` (" + colsSql + ") VALUES (" + qs + ")";
                    ps = conn.prepareStatement(sql);
                }
                for (int i = 0; i < columnsOrder.size(); i++) {
                    String col = columnsOrder.get(i);
                    Object v = row.get(col);
                    if (v == null) { ps.setObject(i + 1, null); continue; }
                    if (v instanceof String s && blobCols.contains(col)) {
                        // 可能是 base64
                        try { ps.setBytes(i + 1, java.util.Base64.getDecoder().decode(s)); }
                        catch (Exception ex) { ps.setString(i + 1, s); }
                    } else {
                        ps.setObject(i + 1, v);
                    }
                }
                ps.executeUpdate();
            }
        } catch (Exception e) {
            throw new IOException("导入表失败: " + tableName, e);
        } finally {
            try { if (ps != null) ps.close(); } catch (Exception ignore) {}
            try { if (conn != null) conn.close(); } catch (Exception ignore) {}
        }
    }

    private Set<String> getBlobColumns(String tableName) {
        try {
            String db = resolveDbName();
            List<String> cols = jdbcTemplate.query(
                    "SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = ? AND data_type IN ('blob','longblob','mediumblob','tinyblob','binary','varbinary')",
                    ps -> { ps.setString(1, db); ps.setString(2, tableName); },
                    (rs, rn) -> rs.getString(1));
            return new java.util.HashSet<>(cols);
        } catch (Exception e) { return java.util.Set.of(); }
    }

    private long copyBlobs(Path blobsDir) throws IOException {
        if (blobsDir == null || !Files.exists(blobsDir)) return 0L;
        long count = 0L;
        Path base = Path.of(storagePath).resolve("blobs");
        if (!Files.exists(base)) Files.createDirectories(base);
        try (java.util.stream.Stream<Path> s = Files.walk(blobsDir)) {
            for (Path p : s.toList()) {
                if (Files.isDirectory(p)) continue;
                Path rel = blobsDir.relativize(p);
                String relStr = rel.toString().replace('\\', '/');
                if (!relStr.matches("[0-9a-f]{2}/[0-9a-f]{2}/[0-9a-f]{6,}")) continue;
                Path dst = Path.of(storagePath).resolve("blobs").resolve(rel);
                if (!Files.exists(dst.getParent())) Files.createDirectories(dst.getParent());
                if (!Files.exists(dst)) Files.copy(p, dst, StandardCopyOption.REPLACE_EXISTING);
                count++;
            }
        }
        return count;
    }

    private long copyBlobsWithProgress(Path blobsDir, long total, ProgressCallback cb, CancelChecker cancel) throws IOException {
        if (blobsDir == null || !Files.exists(blobsDir)) return 0L;
        final long[] count = {0L};
        Path base = Path.of(storagePath).resolve("blobs");
        if (!Files.exists(base)) Files.createDirectories(base);
        try (java.util.stream.Stream<Path> s = Files.walk(blobsDir)) {
            for (Path p : s.toList()) {
                if (Files.isDirectory(p)) continue;
                if (cancel != null && cancel.cancelled()) throw new IOException("CANCELLED");
                Path rel = blobsDir.relativize(p);
                String relStr = rel.toString().replace('\\', '/');
                if (!relStr.matches("[0-9a-f]{2}/[0-9a-f]{2}/[0-9a-f]{6,}")) continue;
                Path dst = Path.of(storagePath).resolve("blobs").resolve(rel);
                if (!Files.exists(dst.getParent())) Files.createDirectories(dst.getParent());
                if (!Files.exists(dst)) Files.copy(p, dst, StandardCopyOption.REPLACE_EXISTING);
                count[0]++;
                if (cb != null) {
                    int percent = 55 + (int)Math.round((count[0] * 30.0) / Math.max(1,total));
                    cb.onStage("import:blobs", Math.min(90, percent));
                }
            }
        }
        return count[0];
    }

    private long countFilesUnder(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) return 0L;
        final long[] cnt = {0L};
        Files.walk(dir).forEach(p -> { if (!Files.isDirectory(p)) cnt[0]++; });
        return cnt[0];
    }

    private long copyThumbnails(Path thumbsDir) throws IOException {
        if (thumbsDir == null || !Files.exists(thumbsDir)) return 0L;
        final long[] count = {0L};
        Path root = Path.of(storagePath).toAbsolutePath().normalize();
        Files.walk(thumbsDir).forEach(p -> {
            try {
                if (Files.isDirectory(p)) return;
                Path rel = thumbsDir.relativize(p);
                Path dst = root.resolve(rel).normalize();
                if (!dst.startsWith(root)) return; // 安全
                if (!Files.exists(dst.getParent())) Files.createDirectories(dst.getParent());
                Files.copy(p, dst, StandardCopyOption.REPLACE_EXISTING);
                count[0]++;
            } catch (Exception ignore) {}
        });
        return count[0];
    }

    private List<String> verifyAllBlobsPresent() {
        Set<String> set = new LinkedHashSet<>();
        try {
            List<String> v = jdbcTemplate.query("SELECT DISTINCT blob_hash FROM file_versions WHERE blob_hash IS NOT NULL", (rs, rn) -> rs.getString(1));
            for (String s : v) if (s != null && !s.isBlank()) set.add(s.toLowerCase());
        } catch (Exception ignore) {}
        try {
            List<String> f = jdbcTemplate.query("SELECT DISTINCT file_hash FROM files WHERE file_hash IS NOT NULL", (rs, rn) -> rs.getString(1));
            for (String s : f) if (s != null && !s.isBlank()) set.add(s.toLowerCase());
        } catch (Exception ignore) {}
        List<String> missing = new ArrayList<>();
        for (String h : set) {
            Path p = shardPath(h);
            if (!Files.exists(p)) missing.add(h);
        }
        return missing;
    }

    private Path shardPath(String hash) {
        String h = hash.toLowerCase();
        String d1 = h.length() >= 2 ? h.substring(0, 2) : "xx";
        String d2 = h.length() >= 4 ? h.substring(2, 4) : "yy";
        return Path.of(storagePath, "blobs", d1, d2, h);
    }

    private void deleteRecursively(Path p) throws IOException {
        if (p == null || !Files.exists(p)) return;
        Files.walk(p)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(x -> { try { Files.deleteIfExists(x); } catch (Exception ignore) {} });
    }

    private void setMaintenance(boolean enabled) {
        try {
            systemSettingService.setBoolean(SystemSettingService.KEY_SYSTEM_MAINTENANCE_ENABLED, enabled, null);
        } catch (Exception ignore) {}
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
}
