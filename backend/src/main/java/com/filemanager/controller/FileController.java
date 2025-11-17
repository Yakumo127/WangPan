package com.filemanager.controller;

import com.filemanager.entity.File;
import com.filemanager.entity.Folder;
import com.filemanager.service.FileService;
import com.filemanager.service.AuditLogService;
import com.filemanager.service.FolderService;
import com.filemanager.service.SystemSettingService;
import com.filemanager.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.Channels;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {
    
    private final FileService fileService;
    private final FolderService folderService;
    private final UserService userService;
    private final SystemSettingService systemSettingService;
    private final AuditLogService auditLogService;
    private final com.filemanager.metrics.DownloadMetrics downloadMetrics;
    
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folderId", required = false) Long folderId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);
            
            File uploadedFile = fileService.uploadFile(file, userId, folderId);
            
            return ResponseEntity.ok(Map.of(
                "message", "文件上传成功",
                "fileId", uploadedFile.getId(),
                "filename", uploadedFile.getOriginalFilename(),
                "size", uploadedFile.getSize(),
                "uploadTime", uploadedFile.getCreateTime()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // 秒传：根据文件哈希检查是否已存在
    @PostMapping("/exists")
    public ResponseEntity<Map<String, Object>> checkFileExists(@RequestBody Map<String, String> body) {
        String fileHash = body == null ? null : body.get("fileHash");
        if (fileHash == null || fileHash.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "缺少 fileHash 参数"));
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        Long userId = userService.getUserIdByUsername(username);
        java.util.Optional<File> opt = fileService.findByHashIfExists(userId, fileHash);
        if (opt.isPresent()) {
            File f = opt.get();
            return ResponseEntity.ok(Map.of(
                    "exists", true,
                    "fileId", f.getId(),
                    "filename", f.getOriginalFilename(),
                    "size", f.getSize()
            ));
        }
        return ResponseEntity.ok(Map.of("exists", false));
    }

    // 分片上传：接收单个分片
    @PostMapping("/chunk")
    public ResponseEntity<Map<String, Object>> uploadChunk(
            @RequestParam("file") MultipartFile chunk,
            @RequestParam("fileHash") String fileHash,
            @RequestParam("chunkNumber") Integer chunkNumber,
            @RequestParam("totalChunks") Integer totalChunks
    ) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);
            fileService.saveChunk(chunk, userId, fileHash, chunkNumber, totalChunks);
            return ResponseEntity.ok(Map.of("message", "分片上传成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // 分片合并：将已上传分片合并为最终文件
    @PostMapping("/merge")
    public ResponseEntity<Map<String, Object>> mergeChunks(@RequestBody Map<String, Object> body) {
        try {
            String fileHash = body.get("fileHash") == null ? null : body.get("fileHash").toString();
            String filename = body.get("filename") == null ? null : body.get("filename").toString();
            Integer totalChunks = body.get("totalChunks") instanceof Number ? ((Number) body.get("totalChunks")).intValue() : null;
            Long folderId = body.get("folderId") instanceof Number ? ((Number) body.get("folderId")).longValue() : null;

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);

            File saved = fileService.mergeChunks(userId, fileHash, filename, totalChunks, folderId);
            return ResponseEntity.ok(Map.of(
                    "message", "文件合并成功",
                    "fileId", saved.getId(),
                    "filename", saved.getOriginalFilename(),
                    "size", saved.getSize(),
                    "uploadTime", saved.getCreateTime()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // 管理员：分页获取全量文件（支持关键字和状态筛选）
    @GetMapping("/admin/list")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> adminListFiles(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", defaultValue = "active") String status
    ) {
        Pageable pageable = PageRequest.of(Math.max(page,0), Math.max(size,1), Sort.by(Sort.Direction.DESC, "createTime"));
        Page<com.filemanager.entity.File> pageData;
        boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
        String s = status == null ? "active" : status.trim().toLowerCase();

        if (hasKeyword) {
            if ("deleted".equals(s)) {
                pageData = fileService.searchDeleted(keyword.trim(), pageable);
            } else if ("all".equals(s)) {
                pageData = fileService.searchAll(keyword.trim(), pageable);
            } else { // active
                pageData = fileService.searchActive(keyword.trim(), pageable);
            }
        } else {
            if ("deleted".equals(s)) {
                pageData = fileService.listDeleted(pageable);
            } else if ("all".equals(s)) {
                pageData = fileService.listAll(pageable);
            } else { // active
                pageData = fileService.listActive(pageable);
            }
        }

        java.util.List<com.filemanager.dto.AdminFileDTO> content = pageData.getContent().stream().map(f -> {
            com.filemanager.dto.AdminFileDTO dto = new com.filemanager.dto.AdminFileDTO();
            dto.setId(f.getId());
            dto.setOriginalFilename(f.getOriginalFilename());
            dto.setSize(f.getSize());
            dto.setContentType(f.getContentType());
            dto.setDownloadCount(f.getDownloadCount());
            dto.setCreateTime(f.getCreateTime());
            dto.setDeleted(Boolean.TRUE.equals(f.getDeleted()));
            dto.setOwnerUsername(f.getUser() != null ? f.getUser().getUsername() : null);
            dto.setDeleteTime(f.getDeleteTime());
            dto.setAdminDeleteScheduled(Boolean.TRUE.equals(f.getAdminDeleteScheduled()));
            dto.setAdminDeleteExecuteTime(f.getAdminDeleteExecuteTime());
            dto.setAdminDeleteReason(f.getAdminDeleteReason());
            return dto;
        }).toList();

        Page<com.filemanager.dto.AdminFileDTO> dtoPage = new PageImpl<>(content, pageable, pageData.getTotalElements());
        return ResponseEntity.ok(dtoPage);
    }

    // 管理员：下载任意文件（忽略归属）
    @GetMapping("/admin/download/{fileId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> adminDownload(@PathVariable Long fileId, javax.servlet.http.HttpServletRequest request) {
        long start = System.currentTimeMillis();
        com.filemanager.entity.File file = fileService.getFileByIdForAdmin(fileId);
        Path filePath = Path.of(file.getFilePath());
        if (!java.nio.file.Files.exists(filePath) || !java.nio.file.Files.isReadable(filePath)) {
            throw new com.filemanager.exception.NotFoundException("文件不存在");
        }

        // 记录审计日志（管理员下载）
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String adminUsername = auth != null ? auth.getName() : null;
            if (adminUsername != null) {
                Long adminId = userService.getUserIdByUsername(adminUsername);
                auditLogService.logSuccess(
                        adminId,
                        com.filemanager.entity.UserLog.ACTION_DOWNLOAD,
                        com.filemanager.entity.UserLog.RESOURCE_FILE,
                        file.getId(),
                        file.getOriginalFilename(),
                        "管理员下载文件：" + file.getOriginalFilename(),
                        System.currentTimeMillis() - start
                );
            }
        } catch (Exception ignore) {}

        try { fileService.incrementDownloadCount(file.getId()); } catch (Exception ignore) {}
        return buildDownloadResponse(request, file, filePath);
    }
    
    @GetMapping("/list")
    public ResponseEntity<List<File>> getUserFiles(
            @RequestParam(value = "folderId", required = false) Long folderId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);
            
            List<File> files = fileService.getUserFiles(userId, folderId);
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    @GetMapping("/download/{fileId}")
    public ResponseEntity<?> downloadFile(@PathVariable Long fileId, javax.servlet.http.HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        Long userId = userService.getUserIdByUsername(username);

        // 严格判断 403/404
        File file = fileService.getFileForDownload(fileId, userId);
        Path filePath = Path.of(file.getFilePath());
        if (!java.nio.file.Files.exists(filePath) || !java.nio.file.Files.isReadable(filePath)) {
            throw new com.filemanager.exception.NotFoundException("文件不存在");
        }
        try { fileService.incrementDownloadCount(file.getId()); } catch (Exception ignore) {}
        return buildDownloadResponse(request, file, filePath);
    }

    // HEAD: 用户下载
    @RequestMapping(value = "/download/{fileId}", method = RequestMethod.HEAD)
    public ResponseEntity<?> headDownload(@PathVariable Long fileId, javax.servlet.http.HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        Long userId = userService.getUserIdByUsername(username);
        File file = fileService.getFileForDownload(fileId, userId);
        Path filePath = Path.of(file.getFilePath());
        return buildHeadResponseConditional(request, file, filePath);
    }

    // HEAD: 管理员下载
    @RequestMapping(value = "/admin/download/{fileId}", method = RequestMethod.HEAD)
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> headAdminDownload(@PathVariable Long fileId, javax.servlet.http.HttpServletRequest request) {
        com.filemanager.entity.File file = fileService.getFileByIdForAdmin(fileId);
        Path filePath = Path.of(file.getFilePath());
        return buildHeadResponseConditional(request, file, filePath);
    }

    // 构建下载响应：统一设置 Content-Type/Disposition/Length 及安全相关响应头
    private ResponseEntity<?> buildDownloadResponse(javax.servlet.http.HttpServletRequest request,
                                                    File file,
                                                    Path filePath) {
        try {
            // 路径安全校验：必须位于受控存储根目录下
            Path root = java.nio.file.Paths.get(fileService.getStorageRoot()).toAbsolutePath().normalize();
            Path normalized = filePath.toAbsolutePath().normalize();
            if (!normalized.startsWith(root)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            long total = java.nio.file.Files.size(filePath);
            // 计数：请求总数
            downloadMetrics.incRequest();
            String contentType = (file.getContentType() == null || file.getContentType().isBlank())
                    ? java.nio.file.Files.probeContentType(filePath)
                    : file.getContentType();
            if (contentType == null || contentType.isBlank()) contentType = "application/octet-stream";

            long lastModified = java.nio.file.Files.getLastModifiedTime(filePath).toMillis();
            String asciiName = sanitizeAsciiFilename(file.getOriginalFilename());
            String encoded = org.springframework.web.util.UriUtils.encode(asciiName, java.nio.charset.StandardCharsets.UTF_8);
            String disposition = String.format("attachment; filename=\"%s\"; filename*=UTF-8''%s", asciiName, encoded);

            String eTag = (file.getFileHash() != null && !file.getFileHash().isBlank())
                    ? "W/\"" + file.getFileHash() + "\""
                    : "W/\"" + total + "-" + lastModified + "\"";

            // If-Range：若不匹配，则忽略 Range，返回完整内容
            String rangeHeader = request.getHeader("Range");
            String ifRange = request.getHeader("If-Range");
            boolean serveFullForIfRange = false;
            if (rangeHeader != null && ifRange != null && !ifRange.isBlank()) {
                String normalizedIfRangeEtag = normalizeEtag(ifRange);
                String normalizedRespEtag = normalizeEtag(eTag);
                long ifRangeTime = parseRfc1123Millis(ifRange);
                boolean match = (normalizedIfRangeEtag != null && normalizedIfRangeEtag.equals(normalizedRespEtag))
                        || (ifRangeTime >= 0 && ifRangeTime >= lastModified);
                if (!match) serveFullForIfRange = true;
            }

            // 条件请求：If-None-Match / If-Modified-Since
            String ifNoneMatch = request.getHeader("If-None-Match");
            long ifModifiedSince = request.getDateHeader("If-Modified-Since");
            if (rangeHeader == null) { // 仅对全量/非分块响应应用 304 判断
                if (ifNoneMatch != null && ifNoneMatch.contains(eTag)) {
                    return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                            .eTag(eTag)
                            .lastModified(lastModified)
                            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                            .build();
                }
                if (ifModifiedSince != -1 && lastModified / 1000 <= ifModifiedSince / 1000) {
                    return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                            .eTag(eTag)
                            .lastModified(lastModified)
                            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                            .build();
                }
            }

            // Range 支持
            if (rangeHeader != null && !serveFullForIfRange) {
                try {
                    java.util.List<org.springframework.http.HttpRange> ranges = org.springframework.http.HttpRange.parseRanges(rangeHeader);
                    if (!ranges.isEmpty()) {
                        if (ranges.size() == 1) {
                            org.springframework.http.HttpRange r = ranges.get(0);
                            long start = r.getRangeStart(total);
                            long end = r.getRangeEnd(total);
                            if (start >= total || end >= total || start > end) {
                                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                                        .header(HttpHeaders.CONTENT_RANGE, "bytes */" + total)
                                        .build();
                            }
                            long rangeLen = end - start + 1;
                            final long copyStart = start;
                            final long copyLen = rangeLen;
                            StreamingResponseBody body = outputStream -> {
                                try (FileChannel fc = FileChannel.open(filePath, StandardOpenOption.READ);
                                     WritableByteChannel out = Channels.newChannel(outputStream)) {
                                    long pos = copyStart;
                                    long remaining = copyLen;
                                    while (remaining > 0) {
                                        long transferred = fc.transferTo(pos, remaining, out);
                                        if (transferred <= 0) break;
                                        pos += transferred;
                                        remaining -= transferred;
                                    }
                                }
                            };
                            // 计数：分段下载字节
                            downloadMetrics.incPartial(rangeLen);
                            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                                    .contentType(MediaType.parseMediaType(contentType))
                                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                                    .header("X-Content-Type-Options", "nosniff")
                                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                                    .header(HttpHeaders.ETAG, eTag)
                                    .lastModified(lastModified)
                                    .header(HttpHeaders.CONTENT_RANGE, String.format("bytes %d-%d/%d", start, end, total))
                                    .contentLength(rangeLen)
                                    .body(body);
                        } else {
                            // 多区间：multipart/byteranges
                            long bytesSum = 0L;
                            for (org.springframework.http.HttpRange r : ranges) {
                                long s = r.getRangeStart(total);
                                long e = r.getRangeEnd(total);
                                if (s >= total || e >= total || s > e) continue;
                                bytesSum += (e - s + 1);
                            }
                            downloadMetrics.incPartial(bytesSum);
                            final String boundary = "MULTIPART_BYTERANGES_" + java.util.UUID.randomUUID();
                            StreamingResponseBody body = outputStream -> {
                                try (FileChannel fc = FileChannel.open(filePath, StandardOpenOption.READ)) {
                                    java.nio.charset.Charset ascii = java.nio.charset.StandardCharsets.US_ASCII;
                                    for (org.springframework.http.HttpRange r : ranges) {
                                        long start = r.getRangeStart(total);
                                        long end = r.getRangeEnd(total);
                                        if (start >= total || end >= total || start > end) continue; // 跳过非法片段
                                        String partHeader = "--" + boundary + "\r\n"
                                                + "Content-Type: " + contentType + "\r\n"
                                                + String.format("Content-Range: bytes %d-%d/%d\r\n\r\n", start, end, total);
                                        outputStream.write(partHeader.getBytes(ascii));
                                        long pos = start;
                                        long remaining = (end - start + 1);
                                        WritableByteChannel out = Channels.newChannel(outputStream);
                                        while (remaining > 0) {
                                            long transferred = fc.transferTo(pos, remaining, out);
                                            if (transferred <= 0) break;
                                            pos += transferred;
                                            remaining -= transferred;
                                        }
                                        outputStream.write("\r\n".getBytes(ascii));
                                    }
                                    String endBoundary = "--" + boundary + "--\r\n";
                                    outputStream.write(endBoundary.getBytes(ascii));
                                }
                            };
                            MediaType mt = MediaType.parseMediaType("multipart/byteranges; boundary=" + boundary);
                            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                                    .contentType(mt)
                                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                                    .header("X-Content-Type-Options", "nosniff")
                                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                                    .header(HttpHeaders.ETAG, eTag)
                                    .lastModified(lastModified)
                                    .body(body);
                        }
                    }
                } catch (IllegalArgumentException ignore) {
                    // 无效 Range，忽略按全量返回
                }
            }

            // 全量下载
            StreamingResponseBody body = outputStream -> {
                try (FileChannel fc = FileChannel.open(filePath, StandardOpenOption.READ);
                     WritableByteChannel out = Channels.newChannel(outputStream)) {
                    long pos = 0L;
                    long remaining = total;
                    while (remaining > 0) {
                        long transferred = fc.transferTo(pos, remaining, out);
                        if (transferred <= 0) break;
                        pos += transferred;
                        remaining -= transferred;
                    }
                }
            };
            // 计数：全量下载字节
            downloadMetrics.incFull(total);
            downloadMetrics.incHead();
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header("X-Content-Type-Options", "nosniff")
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.ETAG, eTag)
                    .lastModified(lastModified)
                    .contentLength(total)
                    .body(body);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private ResponseEntity<?> buildHeadResponseConditional(javax.servlet.http.HttpServletRequest request, File file, Path filePath) {
        try {
            // 路径安全校验
            Path root = java.nio.file.Paths.get(fileService.getStorageRoot()).toAbsolutePath().normalize();
            Path normalized = filePath.toAbsolutePath().normalize();
            if (!normalized.startsWith(root)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            long total = java.nio.file.Files.size(filePath);
            String contentType = (file.getContentType() == null || file.getContentType().isBlank())
                    ? java.nio.file.Files.probeContentType(filePath)
                    : file.getContentType();
            if (contentType == null || contentType.isBlank()) contentType = "application/octet-stream";
            long lastModified = java.nio.file.Files.getLastModifiedTime(filePath).toMillis();
            String asciiName = sanitizeAsciiFilename(file.getOriginalFilename());
            String encoded = org.springframework.web.util.UriUtils.encode(asciiName, java.nio.charset.StandardCharsets.UTF_8);
            String disposition = String.format("attachment; filename=\"%s\"; filename*=UTF-8''%s", asciiName, encoded);
            String eTag = (file.getFileHash() != null && !file.getFileHash().isBlank())
                    ? "W/\"" + file.getFileHash() + "\""
                    : "W/\"" + total + "-" + lastModified + "\"";

            // 条件 HEAD：If-None-Match / If-Modified-Since
            String ifNoneMatch = request.getHeader("If-None-Match");
            long ifModifiedSince = request.getDateHeader("If-Modified-Since");
            if (ifNoneMatch != null && ifNoneMatch.contains(eTag)) {
                downloadMetrics.incNotModified();
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .eTag(eTag)
                        .lastModified(lastModified)
                        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                        .build();
            }
            if (ifModifiedSince != -1 && lastModified / 1000 <= ifModifiedSince / 1000) {
                downloadMetrics.incNotModified();
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .eTag(eTag)
                        .lastModified(lastModified)
                        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                        .build();
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header("X-Content-Type-Options", "nosniff")
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.ETAG, eTag)
                    .lastModified(lastModified)
                    .contentLength(total)
                    .build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private long parseRfc1123Millis(String value) {
        try {
            java.time.ZonedDateTime zdt = java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME);
            return zdt.toInstant().toEpochMilli();
        } catch (Exception e) {
            return -1L;
        }
    }

    private String normalizeEtag(String etag) {
        if (etag == null) return null;
        String s = etag.trim();
        if (s.startsWith("W/")) s = s.substring(2);
        if (s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        return s;
    }

    private String sanitizeAsciiFilename(String name) {
        String fallback = "download";
        if (name == null || name.isBlank()) return fallback;
        String s = name.replaceAll("[\\r\\n]", " ");
        s = s.replace('"', '_').replace(';', '_').replace('\\', '_');
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 32 && c < 127) sb.append(c); else sb.append('_');
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? fallback : result;
    }
    
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Map<String, Object>> deleteFile(@PathVariable Long fileId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);
            
            fileService.deleteFile(fileId, userId);
            com.filemanager.entity.User user = userService.getUserById(userId);
            return ResponseEntity.ok(Map.of(
                    "message", "文件删除成功",
                    "quotaUsed", user.getQuotaUsed(),
                    "quotaLimit", user.getQuotaLimit()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
    
    @PutMapping("/{fileId}/rename")
    public ResponseEntity<Map<String, Object>> renameFile(
            @PathVariable Long fileId,
            @RequestBody Map<String, String> request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);
            
            String newName = request.get("name");
            File renamedFile = fileService.renameFile(fileId, userId, newName);
            
            return ResponseEntity.ok(Map.of(
                "message", "文件重命名成功",
                "fileId", renamedFile.getId(),
                "filename", renamedFile.getOriginalFilename()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
    
    @PostMapping("/{fileId}/move")
    public ResponseEntity<Map<String, Object>> moveFile(
            @PathVariable Long fileId,
            @RequestBody Map<String, Object> request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);
            
            Long targetFolderId = request.get("folderId") != null ? 
                Long.parseLong(request.get("folderId").toString()) : null;
            
            File movedFile = fileService.moveFile(fileId, userId, targetFolderId);
            
            return ResponseEntity.ok(Map.of(
                "message", "文件移动成功",
                "fileId", movedFile.getId(),
                "folderId", movedFile.getFolder() != null ? movedFile.getFolder().getId() : null
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
    
    @PostMapping("/{fileId}/copy")
    public ResponseEntity<Map<String, Object>> copyFile(
            @PathVariable Long fileId,
            @RequestBody Map<String, Object> request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);
            
            Long targetFolderId = request.get("folderId") != null ? 
                Long.parseLong(request.get("folderId").toString()) : null;
            
            File copiedFile = fileService.copyFile(fileId, userId, targetFolderId);
            
            return ResponseEntity.ok(Map.of(
                "message", "文件复制成功",
                "fileId", copiedFile.getId(),
                "filename", copiedFile.getOriginalFilename()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
    
    // 回收站相关API
    @GetMapping("/recycle/bin")
    public ResponseEntity<List<File>> getRecycleBinFiles() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);
            
            List<File> files = fileService.getUserRecycleBinFiles(userId);
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/search")
    public ResponseEntity<List<File>> searchFiles(@RequestParam String keyword) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);
            
            List<File> files = fileService.searchFiles(userId, keyword);
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    // 恢复文件

    // 管理员回收站相关API
    @GetMapping("/admin/recycle/bin")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<com.filemanager.dto.AdminFileDTO>> getAllRecycleBinFiles(
            @RequestParam(value = "fromExec", required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
            java.time.LocalDateTime fromExec,
            @RequestParam(value = "toExec", required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
            java.time.LocalDateTime toExec,
            @RequestParam(value = "scheduledOnly", required = false) Boolean scheduledOnly,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "reason", required = false) String reason
    ) {
        try {
            List<File> files = (fromExec != null || toExec != null || Boolean.TRUE.equals(scheduledOnly) || (keyword != null && !keyword.isBlank()) || (reason != null && !reason.isBlank()))
                    ? fileService.getAllRecycleBinFiles(fromExec, toExec, scheduledOnly, keyword, reason)
                    : fileService.getAllRecycleBinFiles();
            List<com.filemanager.dto.AdminFileDTO> list = files.stream().map(f -> {
                com.filemanager.dto.AdminFileDTO dto = new com.filemanager.dto.AdminFileDTO();
                dto.setId(f.getId());
                dto.setOriginalFilename(f.getOriginalFilename());
                dto.setSize(f.getSize());
                dto.setContentType(f.getContentType());
                dto.setDownloadCount(f.getDownloadCount());
                dto.setCreateTime(f.getCreateTime());
                dto.setDeleted(Boolean.TRUE.equals(f.getDeleted()));
                dto.setOwnerUsername(f.getUser() != null ? f.getUser().getUsername() : null);
                dto.setDeleteTime(f.getDeleteTime());
                dto.setAdminDeleteScheduled(Boolean.TRUE.equals(f.getAdminDeleteScheduled()));
                dto.setAdminDeleteExecuteTime(f.getAdminDeleteExecuteTime());
                dto.setAdminDeleteReason(f.getAdminDeleteReason());
                return dto;
            }).toList();
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/admin/{fileId}/restore")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> adminRestoreFile(@PathVariable Long fileId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String adminUsername = auth.getName();
            Long adminId = userService.getUserIdByUsername(adminUsername);
            fileService.adminRestoreFile(fileId, adminId);
            com.filemanager.entity.User admin = userService.getUserById(adminId);
            return ResponseEntity.ok(Map.of(
                    "message", "文件已恢复到管理员网盘",
                    "quotaUsed", admin.getQuotaUsed(),
                    "quotaLimit", admin.getQuotaLimit()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // 管理员：为回收站文件排期删除（进入冷静期），需要理由
    @PostMapping("/admin/recycle/bin/{fileId}/schedule-delete")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> adminScheduleDeleteFile(
            @PathVariable Long fileId,
            @RequestBody Map<String, String> body) {
        try {
            String reason = body != null ? body.getOrDefault("reason", "管理员删除") : "管理员删除";
            java.time.LocalDateTime execAt = fileService.adminScheduleDeleteFile(fileId, reason);
            // 审计：管理员排期删除
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String adminUsername = auth.getName();
            Long adminId = userService.getUserIdByUsername(adminUsername);
            try { auditLogService.logSuccess(adminId, com.filemanager.entity.UserLog.ACTION_ADMIN_SCHEDULE_DELETE,
                    com.filemanager.entity.UserLog.RESOURCE_FILE, fileId, null,
                    "管理员排期删除：执行时间=" + execAt + ", 理由=" + reason, 0L); } catch (Exception ignore) {}
            return ResponseEntity.ok(Map.of(
                    "message", "已排期删除（进入冷静期）",
                    "executeTime", execAt
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
    
    // 用户回收站：恢复文件
    @PutMapping("/{fileId}/restore")
    public ResponseEntity<Map<String, Object>> restoreFile(@PathVariable Long fileId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);

            fileService.restoreFile(fileId, userId);
            com.filemanager.entity.User user = userService.getUserById(userId);
            return ResponseEntity.ok(Map.of(
                    "message", "文件恢复成功",
                    "quotaUsed", user.getQuotaUsed(),
                    "quotaLimit", user.getQuotaLimit()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // 用户回收站：彻底删除文件（对用户隐藏，不物理删除）
    @DeleteMapping("/recycle/bin/{fileId}")
    public ResponseEntity<Map<String, String>> permanentDeleteFile(@PathVariable Long fileId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);

            fileService.permanentDeleteFile(fileId, userId);
            return ResponseEntity.ok(Map.of("message", "已从你的回收站移除"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // 用户回收站：清空回收站
    @DeleteMapping("/recycle/bin/empty")
    public ResponseEntity<Map<String, String>> emptyRecycleBin() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);

            fileService.emptyRecycleBin(userId);
            return ResponseEntity.ok(Map.of("message", "回收站已清空"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // 公开获取缩略图
    @GetMapping("/thumbnail/{fileId}")
    public ResponseEntity<Resource> getThumbnail(@PathVariable Long fileId) {
        try {
            Path thumbPath = fileService.getThumbnailPathPublic(fileId);
            Resource resource = new UrlResource(thumbPath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // 管理员：手动清理到期的排期删除文件（通常依赖定时任务，这里仅提供手动触发）
    @PostMapping("/admin/recycle/bin/purge-expired")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> adminPurgeExpired() {
        try {
            if (!systemSettingService.isManualPurgeEnabled()) {
                return ResponseEntity.status(403).body(Map.of("message", "手动清理未启用"));
            }

            int count = fileService.purgeExpiredScheduledDeletions();
            return ResponseEntity.ok(Map.of("message", "已清理到期文件", "count", count));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
