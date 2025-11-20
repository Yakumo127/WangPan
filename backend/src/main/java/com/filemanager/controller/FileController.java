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
    private final com.filemanager.repository.FileVersionRepository fileVersionRepository;
    private final com.filemanager.repository.BlobRepository blobRepository;
    private final com.filemanager.repository.FileRepository fileRepository;
    private final com.filemanager.repository.UserRepository userRepository;
    private final com.filemanager.service.BlobService blobService;
    private final com.filemanager.service.DownloadTokenService downloadTokenService;
    
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folderId", required = false) Long folderId,
            @RequestParam(value = "parentId", required = false) Long parentId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        Long userId = userService.getUserIdByUsername(username);

        long start = System.currentTimeMillis();
        try {
            File uploadedFile = fileService.uploadFile(file, userId, folderId, parentId);
            return ResponseEntity.ok(Map.of(
                    "message", "文件上传成功",
                    "fileId", uploadedFile.getId(),
                    "filename", uploadedFile.getOriginalFilename(),
                    "size", uploadedFile.getSize(),
                    "uploadTime", uploadedFile.getCreateTime()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "文件存储异常"));
        }
    }

    // 版本列表
    @GetMapping("/{fileId}/versions")
    public ResponseEntity<?> listVersions(@PathVariable Long fileId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);
            // 简化：复用服务与仓库，直接查询
            // 基础权限：确保该文件属于当前用户
            File f = fileService.getFile(fileId, userId);
            java.util.List<com.filemanager.entity.FileVersion> list = fileVersionRepository.findByFile_IdOrderByVersionNoAsc(fileId);
            java.util.List<com.filemanager.dto.FileVersionDTO> dto = new java.util.ArrayList<>();
            for (com.filemanager.entity.FileVersion v : list) {
                com.filemanager.dto.FileVersionDTO d = new com.filemanager.dto.FileVersionDTO();
                d.setId(v.getId());
                d.setVersionNo(v.getVersionNo());
                d.setBlobHash(v.getBlobHash());
                d.setSize(v.getSize());
                d.setContentType(v.getContentType());
                d.setComment(v.getComment());
                d.setCreatedBy(v.getCreatedBy());
                d.setCreateTime(v.getCreateTime());
                dto.add(d);
            }
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // 创建新版本（上传文件）
    @PostMapping("/{fileId}/versions/upload")
    public ResponseEntity<?> createVersionByUpload(@PathVariable Long fileId,
                                                   @RequestParam("file") MultipartFile file) {
        long start = System.currentTimeMillis();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        Long userId = userService.getUserIdByUsername(username);
        try {
            File updated = fileService.uploadFile(file, userId, null, fileId);
            return ResponseEntity.ok(Map.of(
                    "message", "已创建新版本",
                    "fileId", updated.getId(),
                    "activeVersion", updated.getActiveVersion() != null ? updated.getActiveVersion().getVersionNo() : null,
                    "size", updated.getSize()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "文件存储异常"));
        }
    }

    // 创建新版本（复用已存在 Blob）
    @PostMapping("/{fileId}/versions/from-blob")
    public ResponseEntity<?> createVersionFromBlob(@PathVariable Long fileId,
                                                   @RequestBody Map<String, Object> body) {
        long start = System.currentTimeMillis();
        String fileHash = body.get("fileHash") == null ? null : body.get("fileHash").toString();
        String filename = body.get("filename") == null ? null : body.get("filename").toString();
        if (fileHash == null || fileHash.isBlank()) throw new IllegalArgumentException("缺少 fileHash");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        Long userId = userService.getUserIdByUsername(username);
        File owned = fileService.getFile(fileId, userId); // 校验归属
        var opt = fileService.findBlobByHash(fileHash);
        if (opt.isEmpty()) throw new com.filemanager.exception.NotFoundException("Blob 不存在");
        var blob = opt.get();
        File updated = fileService.createOrUpdateFromBlob(userId, owned.getFolder() != null ? owned.getFolder().getId() : null, owned.getId(), blob, filename != null ? filename : owned.getOriginalFilename(), "from blob");
        return ResponseEntity.ok(Map.of(
                "message", "已创建新版本",
                "fileId", updated.getId(),
                "activeVersion", updated.getActiveVersion() != null ? updated.getActiveVersion().getVersionNo() : null,
                "size", updated.getSize()
        ));
    }

    // 回档到指定版本：创建一个新的版本指向历史 Blob，并切换激活版本
    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_FILE_REVERT,
            resourceType = com.filemanager.entity.UserLog.RESOURCE_FILE,
            userId = "@auditSpel.currentUserId()",
            resourceId = "#fileId",
            description = "'回档至版本 #' + (#body != null ? #body['versionNo'] : null)"
    )
    @PostMapping("/{fileId}/revert")
    public ResponseEntity<?> revert(@PathVariable Long fileId, @RequestBody Map<String, Object> body) {
        long start = System.currentTimeMillis();
        try {
            Integer versionNo = body == null ? null : (body.get("versionNo") == null ? null : Integer.parseInt(body.get("versionNo").toString()));
            if (versionNo == null || versionNo < 1) return ResponseEntity.badRequest().body(Map.of("message", "缺少或非法的 versionNo"));
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);

            // 基础安全：先取文件，确保归属
            File f = fileService.getFile(fileId, userId);
            java.util.Optional<com.filemanager.entity.FileVersion> target = fileVersionRepository.findByFileIdAndVersionNo(fileId, versionNo);
            if (target.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "版本不存在"));

            com.filemanager.entity.FileVersion t = target.get();
            // 配额差值（仅计当前版本）：以当前激活版本（取最后一个版本）为基准
            java.util.Optional<com.filemanager.entity.FileVersion> last = fileVersionRepository.findFirstByFile_IdOrderByVersionNoDesc(fileId);
            long currentSize = last.map(com.filemanager.entity.FileVersion::getSize).orElse(0L);
            try {
                fileService.adjustQuotaForNewVersion(userId, currentSize, t.getSize() == null ? 0L : t.getSize());
            } catch (RuntimeException ex) {
                return ResponseEntity.status(400).body(Map.of("message", ex.getMessage()));
            }
            // 追加一个新版本指向同一 Blob
            int nextNo = last.map(com.filemanager.entity.FileVersion::getVersionNo).orElse(0) + 1;
            com.filemanager.entity.FileVersion v = new com.filemanager.entity.FileVersion();
            v.setFile(f);
            v.setVersionNo(nextNo);
            v.setBlobHash(t.getBlobHash());
            v.setSize(t.getSize());
            v.setContentType(t.getContentType());
            v.setComment("revert from #" + versionNo);
            v.setCreatedBy(userId);
            v.setCreateTime(java.time.LocalDateTime.now());
            v = fileVersionRepository.save(v);

            // 切换激活版本，并更新文件的路径/哈希为 Blob 值，便于兼容现有下载逻辑
            com.filemanager.entity.Blob b = blobRepository.findById(v.getBlobHash()).orElse(null);
            if (b == null) return ResponseEntity.internalServerError().body(Map.of("message", "Blob 缺失"));

            f.setActiveVersion(v);
            f.setFileHash(b.getHash());
            f.setFilePath(b.getPath());
            f.setContentType(v.getContentType());
            f.setSize(v.getSize());
            f.setUpdateTime(java.time.LocalDateTime.now());
            // 简化：不调整配额差值，后续根据口径再行完善
            fileRepository.save(f);
            return ResponseEntity.ok(Map.of("message", "已回档", "activeVersion", v.getVersionNo()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // 跨用户：根据文件哈希检查 Blob 是否已存在（全局秒传）
    @PostMapping("/exists-global")
    public ResponseEntity<Map<String, Object>> checkFileExistsGlobal(@RequestBody Map<String, String> body) {
        String fileHash = body == null ? null : body.get("fileHash");
        if (fileHash == null || fileHash.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "缺少 fileHash 参数"));
        }
        var opt = fileService.findBlobByHash(fileHash);
        if (opt.isPresent()) {
            var b = opt.get();
            return ResponseEntity.ok(Map.of(
                    "exists", true,
                    "size", b.getSize(),
                    "contentType", b.getContentType()
            ));
        }
        return ResponseEntity.ok(Map.of("exists", false));
    }

    // 秒传创建：基于已有 Blob 快速创建文件或新版本
    @PostMapping("/quick-create")
    public ResponseEntity<?> quickCreate(@RequestBody Map<String, Object> body) {
        String fileHash = body.get("fileHash") == null ? null : body.get("fileHash").toString();
        String filename = body.get("filename") == null ? null : body.get("filename").toString();
        Long folderId = body.get("folderId") instanceof Number ? ((Number) body.get("folderId")).longValue() : null;
        Long parentId = body.get("parentId") instanceof Number ? ((Number) body.get("parentId")).longValue() : null;
        if (fileHash == null || fileHash.isBlank() || filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("缺少 fileHash 或 filename");
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        Long userId = userService.getUserIdByUsername(username);

        var opt = fileService.findBlobByHash(fileHash);
        if (opt.isEmpty()) throw new com.filemanager.exception.NotFoundException("Blob 不存在，无法秒传");
        var blob = opt.get();

        // 通过服务层创建（新文件或新版本）
        File saved = fileService.createOrUpdateFromBlob(userId, folderId, parentId, blob, filename, "quick create");
        return ResponseEntity.ok(Map.of(
                "message", parentId == null ? "已创建文件" : "已创建新版本",
                "fileId", saved.getId(),
                "filename", saved.getOriginalFilename(),
                "size", saved.getSize()
        ));
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

    // 直达：按版本号下载
    @GetMapping("/download/{fileId}/version/{versionNo}")
    public ResponseEntity<StreamingResponseBody> downloadVersion(@PathVariable Long fileId,
                                             @PathVariable Integer versionNo,
                                             jakarta.servlet.http.HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        Long userId = userService.getUserIdByUsername(username);
        File file = fileService.getFileForDownload(fileId, userId);
        var optV = fileVersionRepository.findByFileIdAndVersionNo(fileId, versionNo);
        if (optV.isEmpty()) throw new com.filemanager.exception.NotFoundException("版本不存在");
        var v = optV.get();
        var b = blobRepository.findById(v.getBlobHash()).orElse(null);
        if (b == null) throw new com.filemanager.exception.NotFoundException("Blob 不存在");
        com.filemanager.entity.File f2 = new com.filemanager.entity.File();
        f2.setId(file.getId());
        f2.setOriginalFilename(file.getOriginalFilename());
        f2.setContentType(v.getContentType());
        f2.setFileHash(b.getHash());
        f2.setFilePath(b.getPath());
        f2.setSize(v.getSize());
        java.nio.file.Path p = java.nio.file.Path.of(b.getPath());
        if (!java.nio.file.Files.exists(p) || !java.nio.file.Files.isReadable(p)) {
            throw new com.filemanager.exception.NotFoundException("文件不存在");
        }
        return buildDownloadResponse(request, f2, p);
    }

    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_DOWNLOAD_PROBE,
            resourceType = com.filemanager.entity.UserLog.RESOURCE_FILE,
            userId = "@auditSpel.currentUserId()",
            resourceId = "#fileId",
            description = "'下载探测: 版本 #' + #versionNo"
    )
    @RequestMapping(value = "/download/{fileId}/version/{versionNo}", method = RequestMethod.HEAD)
    public ResponseEntity<?> headDownloadVersion(@PathVariable Long fileId,
                                                 @PathVariable Integer versionNo,
                                                 jakarta.servlet.http.HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        Long userId = userService.getUserIdByUsername(username);
        File file = fileService.getFileForDownload(fileId, userId);
        var optV = fileVersionRepository.findByFileIdAndVersionNo(fileId, versionNo);
        if (optV.isEmpty()) throw new com.filemanager.exception.NotFoundException("版本不存在");
        var v = optV.get();
        var b = blobRepository.findById(v.getBlobHash()).orElse(null);
        if (b == null) throw new com.filemanager.exception.NotFoundException("Blob 不存在");
        com.filemanager.entity.File f2 = new com.filemanager.entity.File();
        f2.setId(file.getId());
        f2.setOriginalFilename(file.getOriginalFilename());
        f2.setContentType(v.getContentType());
        f2.setFileHash(b.getHash());
        f2.setFilePath(b.getPath());
        f2.setSize(v.getSize());
        java.nio.file.Path p = java.nio.file.Path.of(b.getPath());
        return buildHeadResponseConditional(request, f2, p);
    }

    // 分片上传：接收单个分片
    @PostMapping("/chunk")
    public ResponseEntity<Map<String, Object>> uploadChunk(
            @RequestParam("file") MultipartFile chunk,
            @RequestParam("fileHash") String fileHash,
            @RequestParam("chunkNumber") Integer chunkNumber,
            @RequestParam("totalChunks") Integer totalChunks
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        Long userId = userService.getUserIdByUsername(username);
        long start = System.currentTimeMillis();
        try {
            fileService.saveChunk(chunk, userId, fileHash, chunkNumber, totalChunks);
            return ResponseEntity.ok(Map.of("message", "分片上传成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "分片写入失败"));
        }
    }

    // 分片合并：将已上传分片合并为最终文件
    @PostMapping("/merge")
    public ResponseEntity<Map<String, Object>> mergeChunks(@RequestBody Map<String, Object> body) {
            String fileHash = body.get("fileHash") == null ? null : body.get("fileHash").toString();
            String filename = body.get("filename") == null ? null : body.get("filename").toString();
            Integer totalChunks = body.get("totalChunks") instanceof Number ? ((Number) body.get("totalChunks")).intValue() : null;
            Long folderId = body.get("folderId") instanceof Number ? ((Number) body.get("folderId")).longValue() : null;
            Long parentId = body.get("parentId") instanceof Number ? ((Number) body.get("parentId")).longValue() : null;

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);

            long start = System.currentTimeMillis();
            try {
                File saved = fileService.mergeChunks(userId, fileHash, filename, totalChunks, folderId, parentId);
                return ResponseEntity.ok(Map.of(
                        "message", "文件合并成功",
                        "fileId", saved.getId(),
                        "filename", saved.getOriginalFilename(),
                        "size", saved.getSize(),
                        "uploadTime", saved.getCreateTime()
                ));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
            } catch (IOException e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("message", "分片合并失败"));
            }
    }

    // 分片状态：查询已上传的分片编号（断点续传）
    @GetMapping("/chunk/status")
    public ResponseEntity<Map<String, Object>> chunkStatus(@RequestParam("fileHash") String fileHash,
                                                           @RequestParam(value = "totalChunks", required = false) Integer totalChunks) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        Long userId = userService.getUserIdByUsername(username);
        long start = System.currentTimeMillis();
        try {
            java.util.List<Integer> uploaded = fileService.listUploadedChunks(userId, fileHash);
            java.util.Map<String, Object> resp = new java.util.HashMap<>();
            resp.put("uploaded", uploaded);
            if (totalChunks != null && totalChunks > 0) {
                java.util.Set<Integer> set = new java.util.HashSet<>(uploaded);
                java.util.List<Integer> missing = new java.util.ArrayList<>();
                for (int i = 1; i <= totalChunks; i++) if (!set.contains(i)) missing.add(i);
                resp.put("missing", missing);
                resp.put("totalChunks", totalChunks);
            }
            return ResponseEntity.ok(resp);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "查询分片状态失败"));
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
    public ResponseEntity<StreamingResponseBody> adminDownload(@PathVariable Long fileId, jakarta.servlet.http.HttpServletRequest request) {
        long start = System.currentTimeMillis();
        com.filemanager.entity.File file = fileService.getFileByIdForAdmin(fileId);
        Path filePath = Path.of(file.getFilePath());
        if (!java.nio.file.Files.exists(filePath) || !java.nio.file.Files.isReadable(filePath)) {
            throw new com.filemanager.exception.NotFoundException("文件不存在");
        }

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
    public ResponseEntity<StreamingResponseBody> downloadFile(@PathVariable Long fileId,
                                          @RequestParam(value = "version", required = false) Integer version,
                                          jakarta.servlet.http.HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        Long userId = userService.getUserIdByUsername(username);

        // 严格判断 403/404
        File file = fileService.getFileForDownload(fileId, userId);
        File fileMeta = file;
        Path filePath = Path.of(file.getFilePath());
        if (version != null && version >= 1) {
            var optV = fileVersionRepository.findByFileIdAndVersionNo(fileId, version);
            if (optV.isPresent()) {
                var v = optV.get();
                var b = blobRepository.findById(v.getBlobHash()).orElse(null);
                if (b != null) {
                    com.filemanager.entity.File f2 = new com.filemanager.entity.File();
                    f2.setId(file.getId());
                    f2.setOriginalFilename(file.getOriginalFilename());
                    f2.setContentType(v.getContentType());
                    f2.setFileHash(b.getHash());
                    f2.setFilePath(b.getPath());
                    f2.setSize(v.getSize());
                    fileMeta = f2;
                    filePath = Path.of(b.getPath());
                }
            }
        }
        if (!java.nio.file.Files.exists(filePath) || !java.nio.file.Files.isReadable(filePath)) {
            throw new com.filemanager.exception.NotFoundException("文件不存在");
        }
        return buildDownloadResponse(request, fileMeta, filePath);
    }

    /**
     * 预览文件：根据配置与权限在浏览器中内联展示内容（不触发下载），并记录预览审计日志。
     */
    @GetMapping("/{fileId}/preview")
    public ResponseEntity<org.springframework.core.io.Resource> previewFile(@PathVariable Long fileId,
                                                                           jakarta.servlet.http.HttpServletRequest request) {
        long start = System.currentTimeMillis();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String username = auth.getName();
        Long currentUserId;
        try {
            currentUserId = userService.getUserIdByUsername(username);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        boolean isAdmin = auth.getAuthorities() != null &&
                auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        File file;
        try {
            if (isAdmin) {
                file = fileService.getFileByIdForAdmin(fileId);
            } else {
                file = fileService.getFileForDownload(fileId, currentUserId);
            }
        } catch (com.filemanager.exception.NotFoundException e) {
            auditLogService.logFailure(currentUserId, com.filemanager.entity.UserLog.ACTION_PREVIEW,
                    com.filemanager.entity.UserLog.RESOURCE_FILE, fileId, null,
                    "预览失败：文件不存在", e.getMessage(), System.currentTimeMillis() - start);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (com.filemanager.exception.ForbiddenException e) {
            auditLogService.logFailure(currentUserId, com.filemanager.entity.UserLog.ACTION_PREVIEW,
                    com.filemanager.entity.UserLog.RESOURCE_FILE, fileId, null,
                    "预览失败：无权限访问该文件", e.getMessage(), System.currentTimeMillis() - start);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            auditLogService.logFailure(currentUserId, com.filemanager.entity.UserLog.ACTION_PREVIEW,
                    com.filemanager.entity.UserLog.RESOURCE_FILE, fileId, null,
                    "预览失败：获取文件信息异常", e.getMessage(), System.currentTimeMillis() - start);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // 校验后缀是否允许预览
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : file.getFilename();
        String ext = null;
        if (name != null) {
            int idx = name.lastIndexOf('.');
            if (idx >= 0 && idx < name.length() - 1) {
                ext = name.substring(idx + 1).toLowerCase();
            }
        }
        java.util.List<String> allowed = systemSettingService.getPreviewAllowedSuffixes();
        if (ext == null || ext.isBlank() || !allowed.contains(ext)) {
            auditLogService.logFailure(currentUserId, com.filemanager.entity.UserLog.ACTION_PREVIEW,
                    com.filemanager.entity.UserLog.RESOURCE_FILE, file.getId(), name,
                    "预览失败：该类型未配置为可预览", "扩展名=" + ext, System.currentTimeMillis() - start);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        Path filePath = Path.of(file.getFilePath());
        if (!java.nio.file.Files.exists(filePath) || !java.nio.file.Files.isReadable(filePath)) {
            auditLogService.logFailure(currentUserId, com.filemanager.entity.UserLog.ACTION_PREVIEW,
                    com.filemanager.entity.UserLog.RESOURCE_FILE, file.getId(), name,
                    "预览失败：物理文件不存在或不可读", null, System.currentTimeMillis() - start);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // 路径安全校验：必须位于受控存储根目录下
        try {
            java.nio.file.Path root = java.nio.file.Paths.get(fileService.getStorageRoot()).toAbsolutePath().normalize();
            java.nio.file.Path normalized = filePath.toAbsolutePath().normalize();
            if (!normalized.startsWith(root)) {
                auditLogService.logFailure(currentUserId, com.filemanager.entity.UserLog.ACTION_PREVIEW,
                        com.filemanager.entity.UserLog.RESOURCE_FILE, file.getId(), name,
                        "预览失败：非法的文件路径", "normalized=" + normalized, System.currentTimeMillis() - start);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        } catch (Exception ex) {
            auditLogService.logFailure(currentUserId, com.filemanager.entity.UserLog.ACTION_PREVIEW,
                    com.filemanager.entity.UserLog.RESOURCE_FILE, file.getId(), name,
                    "预览失败：路径安全校验异常", ex.getMessage(), System.currentTimeMillis() - start);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        long totalSize;
        try {
            totalSize = java.nio.file.Files.size(filePath);
        } catch (java.nio.file.NoSuchFileException nsf) {
            auditLogService.logFailure(currentUserId, com.filemanager.entity.UserLog.ACTION_PREVIEW,
                    com.filemanager.entity.UserLog.RESOURCE_FILE, file.getId(), name,
                    "预览失败：文件不存在", nsf.getMessage(), System.currentTimeMillis() - start);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (java.nio.file.AccessDeniedException ade) {
            auditLogService.logFailure(currentUserId, com.filemanager.entity.UserLog.ACTION_PREVIEW,
                    com.filemanager.entity.UserLog.RESOURCE_FILE, file.getId(), name,
                    "预览失败：无权读取文件", ade.getMessage(), System.currentTimeMillis() - start);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (java.io.IOException ioe) {
            auditLogService.logFailure(currentUserId, com.filemanager.entity.UserLog.ACTION_PREVIEW,
                    com.filemanager.entity.UserLog.RESOURCE_FILE, file.getId(), name,
                    "预览失败：读取文件失败", ioe.getMessage(), System.currentTimeMillis() - start);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String contentType = determinePreviewContentType(file, filePath, ext);
        String asciiName = sanitizeAsciiFilename(name);
        String encoded = org.springframework.web.util.UriUtils.encode(asciiName, java.nio.charset.StandardCharsets.UTF_8);
        String disposition = String.format("inline; filename=\"%s\"; filename*=UTF-8''%s", asciiName, encoded);

        org.springframework.core.io.Resource resource = new org.springframework.core.io.FileSystemResource(filePath.toFile());

        auditLogService.logSuccess(currentUserId, com.filemanager.entity.UserLog.ACTION_PREVIEW,
                com.filemanager.entity.UserLog.RESOURCE_FILE, file.getId(), name,
                "预览成功", System.currentTimeMillis() - start);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .contentLength(totalSize)
                .body(resource);
    }

    /**
     * 普通用户：获取指定文件的一次性下载链接。
     * 该链接包含短期有效的签名 token，实际下载走 /api/files/direct-download。
     */
    @GetMapping("/{fileId}/download-url")
    public ResponseEntity<Map<String, Object>> getDownloadUrl(@PathVariable Long fileId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "未登录"));
        }
        String username = auth.getName();
        Long userId = userService.getUserIdByUsername(username);
        // 再次校验权限与存在性
        File file = fileService.getFileForDownload(fileId, userId);
        String token = downloadTokenService.generateUserToken(file.getId(), userId);
        long expiresAt = System.currentTimeMillis() + downloadTokenService.getTtlSeconds() * 1000L;
        String url = "/api/files/direct-download?token=" + token;
        return ResponseEntity.ok(Map.of(
                "url", url,
                "expiresAt", expiresAt
        ));
    }

    // HEAD: 用户下载
    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_DOWNLOAD_PROBE,
            resourceType = com.filemanager.entity.UserLog.RESOURCE_FILE,
            userId = "@auditSpel.currentUserId()",
            resourceId = "#fileId",
            description = "'下载探测' + (#version != null ? ' 版本 #' + #version : '')"
    )
    @RequestMapping(value = "/download/{fileId}", method = RequestMethod.HEAD)
    public ResponseEntity<?> headDownload(@PathVariable Long fileId,
                                          @RequestParam(value = "version", required = false) Integer version,
                                          jakarta.servlet.http.HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        Long userId = userService.getUserIdByUsername(username);
        File file = fileService.getFileForDownload(fileId, userId);
        File fileMeta = file;
        Path filePath = Path.of(file.getFilePath());
        if (version != null && version >= 1) {
            var optV = fileVersionRepository.findByFileIdAndVersionNo(fileId, version);
            if (optV.isPresent()) {
                var v = optV.get();
                var b = blobRepository.findById(v.getBlobHash()).orElse(null);
                if (b != null) {
                    com.filemanager.entity.File f2 = new com.filemanager.entity.File();
                    f2.setId(file.getId());
                    f2.setOriginalFilename(file.getOriginalFilename());
                    f2.setContentType(v.getContentType());
                    f2.setFileHash(b.getHash());
                    f2.setFilePath(b.getPath());
                    f2.setSize(v.getSize());
                    fileMeta = f2;
                    filePath = Path.of(b.getPath());
                }
            }
        }
        return buildHeadResponseConditional(request, fileMeta, filePath);
    }

    /**
     * 直链下载入口：通过一次性 token 实现浏览器原生下载，并强制时间与次数限制。
     * 不依赖 JWT Header，而是完全基于 token 校验。
     */
    @GetMapping("/direct-download")
    public ResponseEntity<StreamingResponseBody> directDownload(@RequestParam("token") String token,
                                                                jakarta.servlet.http.HttpServletRequest request) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(null);
        }
        com.filemanager.service.DownloadTokenService.DecodedToken decoded;
        try {
            decoded = downloadTokenService.parseAndValidate(token);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(null);
        }

        // 用户状态与权限校验
        com.filemanager.entity.User user;
        try {
            user = userService.getUserById(decoded.userId);
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        if (!Boolean.TRUE.equals(user.getEnabled()) || Boolean.TRUE.equals(user.getLocked())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }

        File fileMeta;
        Path filePath;
        try {
            if (decoded.admin) {
                // 管理员下载：允许跨用户
                if (user.getRole() != com.filemanager.entity.User.Role.ADMIN) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
                }
                File f = fileService.getFileByIdForAdmin(decoded.fileId);
                fileMeta = f;
                filePath = Path.of(f.getFilePath());
            } else {
                // 普通用户下载：仅允许下载本人文件
                File f = fileService.getFileForDownload(decoded.fileId, decoded.userId);
                fileMeta = f;
                filePath = Path.of(f.getFilePath());
            }
            if (!java.nio.file.Files.exists(filePath) || !java.nio.file.Files.isReadable(filePath)) {
                throw new com.filemanager.exception.NotFoundException("文件不存在");
            }
        } catch (com.filemanager.exception.NotFoundException nf) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        } catch (com.filemanager.exception.ForbiddenException fe) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }

        // 单次使用限制：标记 token 已使用，若重复使用则失败
        try {
            downloadTokenService.assertNotUsedAndMarkUsed(token);
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.GONE).body(null);
        }

        // 将用户放入 SecurityContext，便于复用现有审计逻辑
        try {
            org.springframework.security.core.userdetails.UserDetails userDetails =
                    userService.loadUserByUsername(user.getUsername());
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken authenticationToken =
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
            authenticationToken.setDetails(new org.springframework.security.web.authentication.WebAuthenticationDetailsSource().buildDetails(request));
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        } catch (Exception ignore) {
        }

        return buildDownloadResponse(request, fileMeta, filePath);
    }

    /**
     * 获取预览配置：供普通用户前端判断哪些类型可以预览。
     */
    @GetMapping("/preview/config")
    public ResponseEntity<Map<String, Object>> getPreviewConfigForUser() {
        java.util.List<String> suffixes = systemSettingService.getPreviewAllowedSuffixes();
        return ResponseEntity.ok(Map.of("allowedSuffixes", suffixes));
    }

    /**
     * 获取上传超时策略：供前端根据文件大小计算 axios 超时时间。
     */
    @GetMapping("/upload-timeout-config")
    public ResponseEntity<Map<String, Object>> getUploadTimeoutConfig() {
        String mode = systemSettingService.getUploadTimeoutModeOrDefault("auto");
        int seconds = systemSettingService.getUploadTimeoutSecondsOrDefault(150);
        return ResponseEntity.ok(Map.of("mode", mode, "timeoutSeconds", seconds));
    }

    private String determinePreviewContentType(File file, Path filePath, String ext) {
        String contentType = null;
        if (ext != null) {
            switch (ext) {
                case "jpg":
                case "jpeg":
                    contentType = "image/jpeg";
                    break;
                case "png":
                    contentType = "image/png";
                    break;
                case "gif":
                    contentType = "image/gif";
                    break;
                case "webp":
                    contentType = "image/webp";
                    break;
                case "pdf":
                    contentType = "application/pdf";
                    break;
                case "txt":
                    contentType = "text/plain; charset=UTF-8";
                    break;
                case "mp4":
                    contentType = "video/mp4";
                    break;
                case "doc":
                    contentType = "application/msword";
                    break;
                case "docx":
                    contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                    break;
                case "ppt":
                    contentType = "application/vnd.ms-powerpoint";
                    break;
                case "pptx":
                    contentType = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
                    break;
                default:
                    break;
            }
        }
        if (contentType == null) {
            try {
                contentType = (file.getContentType() == null || file.getContentType().isBlank())
                        ? java.nio.file.Files.probeContentType(filePath)
                        : file.getContentType();
            } catch (Exception ignore) {
                contentType = file.getContentType();
            }
        }
        if (contentType == null || contentType.isBlank()) contentType = "application/octet-stream";
        return contentType;
    }

    /**
     * 管理员：获取任意文件的一次性下载链接。
     */
    @GetMapping("/admin/{fileId}/download-url")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> getAdminDownloadUrl(@PathVariable Long fileId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "未登录"));
        }
        String username = auth.getName();
        Long userId = userService.getUserIdByUsername(username);
        // 管理员权限与文件存在性校验
        com.filemanager.entity.File file = fileService.getFileByIdForAdmin(fileId);
        String token = downloadTokenService.generateAdminToken(file.getId(), userId);
        long expiresAt = System.currentTimeMillis() + downloadTokenService.getTtlSeconds() * 1000L;
        String url = "/api/files/direct-download?token=" + token;
        return ResponseEntity.ok(Map.of(
                "url", url,
                "expiresAt", expiresAt
        ));
    }

    // HEAD: 管理员下载
    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_DOWNLOAD_PROBE,
            resourceType = com.filemanager.entity.UserLog.RESOURCE_FILE,
            userId = "@auditSpel.currentUserId()",
            resourceId = "#fileId",
            description = "'管理员下载探测'"
    )
    @RequestMapping(value = "/admin/download/{fileId}", method = RequestMethod.HEAD)
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> headAdminDownload(@PathVariable Long fileId, jakarta.servlet.http.HttpServletRequest request) {
        com.filemanager.entity.File file = fileService.getFileByIdForAdmin(fileId);
        Path filePath = Path.of(file.getFilePath());
        return buildHeadResponseConditional(request, file, filePath);
    }

    // 构建下载响应：统一设置 Content-Type/Disposition/Length 及安全相关响应头
    private ResponseEntity<StreamingResponseBody> buildDownloadResponse(jakarta.servlet.http.HttpServletRequest request,
                                                    File file,
                                                    Path filePath) {
        try {
            // 路径安全校验：必须位于受控存储根目录下
            Path root = java.nio.file.Paths.get(fileService.getStorageRoot()).toAbsolutePath().normalize();
            Path normalized = filePath.toAbsolutePath().normalize();
            if (!normalized.startsWith(root)) {
                throw new com.filemanager.exception.ForbiddenException("非法的文件路径");
            }
            // 更新 Blob 最近访问时间（若匹配）
            try {
                if (file.getFileHash() != null && !file.getFileHash().isBlank()) {
                    blobRepository.findById(file.getFileHash()).ifPresent(b -> {
                        b.setLastAccessAt(java.time.LocalDateTime.now());
                        try { blobRepository.save(b); } catch (Exception ignore) {}
                    });
                }
            } catch (Exception ignore) {}
            long total;
            try {
                total = java.nio.file.Files.size(filePath);
            } catch (java.nio.file.NoSuchFileException nsf) {
                throw new com.filemanager.exception.NotFoundException("文件不存在");
            } catch (java.nio.file.AccessDeniedException ade) {
                throw new com.filemanager.exception.ForbiddenException("无权读取文件");
            } catch (java.io.IOException ioe) {
                throw new RuntimeException("读取文件失败", ioe);
            }
            // 计数：请求总数
            downloadMetrics.incRequest();
            String contentType;
            try {
                contentType = (file.getContentType() == null || file.getContentType().isBlank())
                        ? java.nio.file.Files.probeContentType(filePath)
                        : file.getContentType();
            } catch (Exception ignore) {
                contentType = file.getContentType();
            }
            if (contentType == null || contentType.isBlank()) contentType = "application/octet-stream";

            long lastModified;
            try {
                lastModified = java.nio.file.Files.getLastModifiedTime(filePath).toMillis();
            } catch (java.nio.file.NoSuchFileException nsf) {
                throw new com.filemanager.exception.NotFoundException("文件不存在");
            } catch (java.nio.file.AccessDeniedException ade) {
                throw new com.filemanager.exception.ForbiddenException("无权读取文件");
            } catch (java.io.IOException ioe) {
                throw new RuntimeException("读取文件失败", ioe);
            }
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
                            final String contentTypeFinal = contentType;
                            StreamingResponseBody body = outputStream -> {
                                long started = System.currentTimeMillis();
                                boolean ok = false;
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
                                    ok = true;
                                } catch (Exception ex) {
                                    try {
                                        Long uid = null;
                                        try { var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication(); if (auth != null) uid = userService.getUserIdByUsername(auth.getName()); } catch (Exception ignore) {}
                                        auditLogService.logFailure(uid,
                                                com.filemanager.entity.UserLog.ACTION_DOWNLOAD,
                                                com.filemanager.entity.UserLog.RESOURCE_FILE,
                                                file.getId(), file.getOriginalFilename(),
                                                "分段下载流传输失败",
                                                ex.getMessage(), System.currentTimeMillis() - started);
                                    } catch (Exception ignore) {}
                                    if (downloadMetrics != null) downloadMetrics.incError();
                                    throw ex;
                                } finally {
                                    if (ok) {
                                        try {
                                            Long uid = null;
                                            try { var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication(); if (auth != null) uid = userService.getUserIdByUsername(auth.getName()); } catch (Exception ignore) {}
                                            auditLogService.logSuccess(uid,
                                                    com.filemanager.entity.UserLog.ACTION_DOWNLOAD,
                                                    com.filemanager.entity.UserLog.RESOURCE_FILE,
                                                    file.getId(), file.getOriginalFilename(),
                                                    "分段下载成功",
                                                    System.currentTimeMillis() - started);
                                        } catch (Exception ignore) {}
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
                            final String contentTypeFinal = contentType;
                            StreamingResponseBody body = outputStream -> {
                                long started = System.currentTimeMillis();
                                boolean ok = false;
                                try (FileChannel fc = FileChannel.open(filePath, StandardOpenOption.READ)) {
                                    java.nio.charset.Charset ascii = java.nio.charset.StandardCharsets.US_ASCII;
                                    for (org.springframework.http.HttpRange r : ranges) {
                                        long start = r.getRangeStart(total);
                                        long end = r.getRangeEnd(total);
                                        if (start >= total || end >= total || start > end) continue; // 跳过非法片段
                                        String partHeader = "--" + boundary + "\r\n"
                                                + "Content-Type: " + contentTypeFinal + "\r\n"
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
                                    ok = true;
                                } catch (Exception ex) {
                                    try {
                                        Long uid = null;
                                        try { var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication(); if (auth != null) uid = userService.getUserIdByUsername(auth.getName()); } catch (Exception ignore) {}
                                        auditLogService.logFailure(uid,
                                                com.filemanager.entity.UserLog.ACTION_DOWNLOAD,
                                                com.filemanager.entity.UserLog.RESOURCE_FILE,
                                                file.getId(), file.getOriginalFilename(),
                                                "多段下载流传输失败",
                                                ex.getMessage(), System.currentTimeMillis() - started);
                                    } catch (Exception ignore) {}
                                    if (downloadMetrics != null) downloadMetrics.incError();
                                    throw ex;
                                } finally {
                                    if (ok) {
                                        try {
                                            Long uid = null;
                                            try { var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication(); if (auth != null) uid = userService.getUserIdByUsername(auth.getName()); } catch (Exception ignore) {}
                                            auditLogService.logSuccess(uid,
                                                    com.filemanager.entity.UserLog.ACTION_DOWNLOAD,
                                                    com.filemanager.entity.UserLog.RESOURCE_FILE,
                                                    file.getId(), file.getOriginalFilename(),
                                                    "多段下载成功",
                                                    System.currentTimeMillis() - started);
                                        } catch (Exception ignore) {}
                                    }
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
                long started = System.currentTimeMillis();
                boolean ok = false;
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
                    ok = true;
                } catch (Exception ex) {
                    try {
                        Long uid = null;
                        try { var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication(); if (auth != null) uid = userService.getUserIdByUsername(auth.getName()); } catch (Exception ignore) {}
                        auditLogService.logFailure(uid,
                                com.filemanager.entity.UserLog.ACTION_DOWNLOAD,
                                com.filemanager.entity.UserLog.RESOURCE_FILE,
                                file.getId(), file.getOriginalFilename(),
                                "全量下载流传输失败",
                                ex.getMessage(), System.currentTimeMillis() - started);
                    } catch (Exception ignore) {}
                    if (downloadMetrics != null) downloadMetrics.incError();
                    throw ex;
                } finally {
                    if (ok) {
                        try {
                            Long uid = null;
                            try { var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication(); if (auth != null) uid = userService.getUserIdByUsername(auth.getName()); } catch (Exception ignore) {}
                            auditLogService.logSuccess(uid,
                                    com.filemanager.entity.UserLog.ACTION_DOWNLOAD,
                                    com.filemanager.entity.UserLog.RESOURCE_FILE,
                                    file.getId(), file.getOriginalFilename(),
                                    "全量下载成功",
                                    System.currentTimeMillis() - started);
                        } catch (Exception ignore) {}
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
            throw new RuntimeException("下载失败", e);
        }
    }

    private ResponseEntity<?> buildHeadResponseConditional(jakarta.servlet.http.HttpServletRequest request, File file, Path filePath) {
        try {
            // 路径安全校验
            Path root = java.nio.file.Paths.get(fileService.getStorageRoot()).toAbsolutePath().normalize();
            Path normalized = filePath.toAbsolutePath().normalize();
            if (!normalized.startsWith(root)) {
                throw new com.filemanager.exception.ForbiddenException("非法的文件路径");
            }
            // 更新 Blob 最近访问时间（若匹配）
            try {
                if (file.getFileHash() != null && !file.getFileHash().isBlank()) {
                    blobRepository.findById(file.getFileHash()).ifPresent(b -> {
                        b.setLastAccessAt(java.time.LocalDateTime.now());
                        try { blobRepository.save(b); } catch (Exception ignore) {}
                    });
                }
            } catch (Exception ignore) {}
            long total;
            try { total = java.nio.file.Files.size(filePath); }
            catch (java.nio.file.NoSuchFileException nsf) { throw new com.filemanager.exception.NotFoundException("文件不存在"); }
            catch (java.nio.file.AccessDeniedException ade) { throw new com.filemanager.exception.ForbiddenException("无权读取文件"); }
            catch (java.io.IOException ioe) { throw new RuntimeException("读取文件失败", ioe); }
            String contentType;
            try {
                contentType = (file.getContentType() == null || file.getContentType().isBlank())
                        ? java.nio.file.Files.probeContentType(filePath)
                        : file.getContentType();
            } catch (Exception ignore) { contentType = file.getContentType(); }
            if (contentType == null || contentType.isBlank()) contentType = "application/octet-stream";
            long lastModified;
            try { lastModified = java.nio.file.Files.getLastModifiedTime(filePath).toMillis(); }
            catch (java.nio.file.NoSuchFileException nsf) { throw new com.filemanager.exception.NotFoundException("文件不存在"); }
            catch (java.nio.file.AccessDeniedException ade) { throw new com.filemanager.exception.ForbiddenException("无权读取文件"); }
            catch (java.io.IOException ioe) { throw new RuntimeException("读取文件失败", ioe); }
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
            throw new RuntimeException("下载探测失败", e);
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

    // 管理员：手动触发 Blob GC（清理未引用 Blob）
    @PostMapping("/admin/blob/gc")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> adminBlobGc() {
        try {
            int count = blobService.gcUnreferenced();
            return ResponseEntity.ok(Map.of("message", "已触发 Blob GC", "removed", count));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // 管理员：手动触发一次迁移批次
    @PostMapping("/admin/migrate/batch")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> adminMigrateBatch(@RequestParam(value = "size", required = false) Integer size) {
        try {
            int n = size == null ? 25 : Math.max(1, size);
            int processed = fileService.migrateFilesBatch(n);
            return ResponseEntity.ok(Map.of("message", "已迁移一批", "processed", processed));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
