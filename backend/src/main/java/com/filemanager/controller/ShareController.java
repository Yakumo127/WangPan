package com.filemanager.controller;

import com.filemanager.entity.Share;
import com.filemanager.entity.ShareACL;
import com.filemanager.entity.File;
import com.filemanager.service.DownloadTokenService;
import com.filemanager.service.FileService;
import com.filemanager.service.ShareService;
import com.filemanager.service.UserService;
import com.filemanager.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;
    private final UserService userService;
    private final FileService fileService;
    private final DownloadTokenService downloadTokenService;
    private final com.filemanager.repository.FileRepository fileRepository;
    private final com.filemanager.repository.FolderRepository folderRepository;
    private final AuditLogService auditLogService;

    // ---------- 受保护接口 ----------
    @PostMapping("/shares")
    public ResponseEntity<?> createShare(@RequestBody ShareService.CreateShareRequest request) {
        Long userId = currentUserId();
        long start = System.currentTimeMillis();
        try {
            Share share = shareService.createShare(request, userId);
            auditLogService.logSuccess(userId, "SHARE_CREATE", "SHARE", share.getId(),
                    share.getResourceType().name(), "创建分享", System.currentTimeMillis() - start);
            return ResponseEntity.ok(Map.of(
                    "id", share.getId(),
                    "resourceId", share.getResourceId(),
                    "resourceType", share.getResourceType(),
                    "expireTime", share.getExpireTime(),
                    "status", share.getStatus()
            ));
        } catch (Exception e) {
            auditLogService.logFailure(userId, "SHARE_CREATE", "SHARE", null, null,
                    "创建分享失败", e.getMessage(), System.currentTimeMillis() - start);
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/shares/{id}")
    public ResponseEntity<?> updateShare(@PathVariable Long id, @RequestBody ShareService.CreateShareRequest request) {
        Long userId = currentUserId();
        long start = System.currentTimeMillis();
        try {
            Share share = shareService.updateShare(id, request, userId);
            auditLogService.logSuccess(userId, "SHARE_UPDATE", "SHARE", share.getId(),
                    share.getResourceType().name(), "更新分享", System.currentTimeMillis() - start);
            return ResponseEntity.ok(Map.of(
                    "id", share.getId(),
                    "expireTime", share.getExpireTime(),
                    "status", share.getStatus()
            ));
        } catch (Exception e) {
            auditLogService.logFailure(userId, "SHARE_UPDATE", "SHARE", id, null,
                    "更新分享失败", e.getMessage(), System.currentTimeMillis() - start);
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/shares/{id}")
    public ResponseEntity<?> revokeShare(@PathVariable Long id) {
        Long userId = currentUserId();
        long start = System.currentTimeMillis();
        try {
            shareService.revokeShare(id, userId);
            auditLogService.logSuccess(userId, "SHARE_REVOKE", "SHARE", id, null, "撤销分享", System.currentTimeMillis() - start);
            return ResponseEntity.ok(Map.of("message", "已取消分享"));
        } catch (Exception e) {
            auditLogService.logFailure(userId, "SHARE_REVOKE", "SHARE", id, null,
                    "撤销分享失败", e.getMessage(), System.currentTimeMillis() - start);
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/shares")
    public ResponseEntity<?> listMyShares(@RequestParam(value = "page", required = false, defaultValue = "0") int page,
                                          @RequestParam(value = "size", required = false, defaultValue = "10") int size) {
        Long userId = currentUserId();
        try {
            ShareService.ShareListResult result = shareService.listMyShares(userId, page, size);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/shares/{id}/acl")
    public ResponseEntity<?> getAcl(@PathVariable Long id) {
        Long userId = currentUserId();
        try {
            List<ShareACL> aclList = shareService.getAclForOwner(id, userId);
            return ResponseEntity.ok(aclList);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/shares/{id}/acl")
    public ResponseEntity<?> updateAcl(@PathVariable Long id, @RequestBody List<ShareService.ACLItem> acl) {
        Long userId = currentUserId();
        long start = System.currentTimeMillis();
        try {
            List<ShareACL> saved = shareService.replaceAcl(id, userId, acl);
            auditLogService.logSuccess(userId, "SHARE_ACL_UPDATE", "SHARE", id, null, "更新分享 ACL", System.currentTimeMillis() - start);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            auditLogService.logFailure(userId, "SHARE_ACL_UPDATE", "SHARE", id, null,
                    "更新分享 ACL 失败", e.getMessage(), System.currentTimeMillis() - start);
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ---------- 公开接口 ----------
    @GetMapping("/public/shares/{id}")
    public ResponseEntity<?> publicShare(@PathVariable Long id) {
        long start = System.currentTimeMillis();
        try {
            ShareService.SharePublicView view = shareService.getPublicShare(id);
            shareService.incrementView(id);
            auditLogService.logSystemSuccess("SHARE_VIEW", "SHARE", id, view.getResourceName(), "公开分享查看元数据", System.currentTimeMillis() - start);
            return ResponseEntity.ok(view);
        } catch (com.filemanager.exception.ForbiddenException fe) {
            auditLogService.logSystemFailure("SHARE_VIEW", "SHARE", id, null, "公开分享查看失败", fe.getMessage(), System.currentTimeMillis() - start);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", fe.getMessage()));
        } catch (Exception e) {
            auditLogService.logSystemFailure("SHARE_VIEW", "SHARE", id, null, "公开分享查看异常", e.getMessage(), System.currentTimeMillis() - start);
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/public/shares/{id}/validate")
    public ResponseEntity<?> validateShare(@PathVariable Long id, @RequestBody Map<String, String> body) {
        long start = System.currentTimeMillis();
        try {
            String code = body.get("code");
            ShareService.Principal p = new ShareService.Principal();
            String pt = body.get("principalType");
            String pv = body.get("principalValue");
            if (pt != null && !pt.isBlank() && pv != null && !pv.isBlank()) {
                try {
                    p.setType(ShareACL.PrincipalType.valueOf(pt));
                    p.setValue(pv);
                } catch (Exception ignore) {
                }
            }
            ShareService.ShareSession session = shareService.validateAccess(id, code, p);
            auditLogService.logSystemSuccess("SHARE_VALIDATE", "SHARE", id, null, "公开分享验证通过", System.currentTimeMillis() - start);
            return ResponseEntity.ok(session);
        } catch (com.filemanager.exception.ForbiddenException fe) {
            auditLogService.logSystemFailure("SHARE_VALIDATE", "SHARE", id, null, "公开分享验证失败", fe.getMessage(), System.currentTimeMillis() - start);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", fe.getMessage()));
        } catch (Exception e) {
            auditLogService.logSystemFailure("SHARE_VALIDATE", "SHARE", id, null, "公开分享验证异常", e.getMessage(), System.currentTimeMillis() - start);
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/public/shares/{id}/files/{fileId}/download-url")
    public ResponseEntity<?> getShareDownloadUrl(@PathVariable Long id,
                                                 @PathVariable Long fileId,
                                                 @RequestParam("token") String sessionToken) {
        try {
            String url = shareService.generateDownloadUrl(id, fileId, sessionToken);
            return ResponseEntity.ok(Map.of("url", url, "expiresAt", System.currentTimeMillis() + shareService.getShareDownloadTtlSeconds() * 1000L));
        } catch (com.filemanager.exception.ForbiddenException fe) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", fe.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/public/shares/direct-download")
    public ResponseEntity<StreamingResponseBody> directDownload(@RequestParam("token") String token) {
        ShareService.ShareDownloadPayload payload;
        long start = System.currentTimeMillis();
        try {
            payload = shareService.parseDownloadToken(token);
        } catch (com.filemanager.exception.ForbiddenException fe) {
            auditLogService.logSystemFailure("SHARE_DOWNLOAD", "SHARE", null, null, "下载拒绝", fe.getMessage(), System.currentTimeMillis() - start);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        } catch (Exception e) {
            auditLogService.logSystemFailure("SHARE_DOWNLOAD", "SHARE", null, null, "下载异常", e.getMessage(), System.currentTimeMillis() - start);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
        // 单次使用限制
        try {
            downloadTokenService.assertNotUsedAndMarkUsed(token);
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.GONE).body(null);
        }

        try {
            File file = fileService.getFileByIdForAdmin(payload.fileId);
            Path path = Path.of(file.getFilePath());
            if (!Files.exists(path) || !Files.isReadable(path)) {
                auditLogService.logSystemFailure("SHARE_DOWNLOAD", "SHARE", payload.share.getId(), null, "文件不存在", "not found", System.currentTimeMillis() - start);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }
            shareService.incrementDownload(payload.share);
            StreamingResponseBody body = outputStream -> {
                long rateLimit = downloadTokenService.getRateLimitBytesPerSecond();
                if (rateLimit <= 0) {
                    Files.copy(path, outputStream);
                    return;
                }
                try (java.io.InputStream in = Files.newInputStream(path)) {
                    byte[] buffer = new byte[64 * 1024];
                    long bytesThisSecond = 0;
                    long windowStart = System.nanoTime();
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, len);
                        bytesThisSecond += len;
                        long elapsedNanos = System.nanoTime() - windowStart;
                        if (bytesThisSecond >= rateLimit) {
                            long targetNanos = 1_000_000_000L;
                            if (elapsedNanos < targetNanos) {
                                long sleepMs = (targetNanos - elapsedNanos) / 1_000_000L;
                                if (sleepMs > 0) {
                                    try {
                                        Thread.sleep(sleepMs);
                                    } catch (InterruptedException ie) {
                                        Thread.currentThread().interrupt();
                                        throw new RuntimeException("下载被中断", ie);
                                    }
                                }
                            }
                            bytesThisSecond = 0;
                            windowStart = System.nanoTime();
                        }
                    }
                }
            };
            String name = file.getOriginalFilename();
            String safeName = (name == null || name.isBlank()) ? "file" : name.replace("\"", "");
            String asciiName = sanitizeAsciiFilename(safeName);
            String encoded = org.springframework.web.util.UriUtils.encode(safeName, java.nio.charset.StandardCharsets.UTF_8);
            String disposition = String.format("attachment; filename=\"%s\"; filename*=UTF-8''%s", asciiName, encoded);
            auditLogService.logSystemSuccess("SHARE_DOWNLOAD", "SHARE", payload.share.getId(), name, "公开下载成功", System.currentTimeMillis() - start);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(body);
        } catch (Exception e) {
            auditLogService.logSystemFailure("SHARE_DOWNLOAD", "SHARE", payload.share.getId(), null, "下载异常", e.getMessage(), System.currentTimeMillis() - start);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @GetMapping("/public/shares/{id}/list")
    public ResponseEntity<?> listShareContent(@PathVariable Long id,
                                              @RequestParam(value = "token", required = false) String sessionToken,
                                              @RequestParam(value = "folderId", required = false) Long folderId,
                                              @RequestParam(value = "page", required = false, defaultValue = "0") int page,
                                              @RequestParam(value = "size", required = false, defaultValue = "100") int size) {
        long start = System.currentTimeMillis();
        try {
            Share share = shareService.getActiveShare(id);
            if (share.getResourceType() == Share.ResourceType.FILE) {
                com.filemanager.entity.File f = fileService.getFileByIdForAdmin(share.getResourceId());
                return ResponseEntity.ok(Map.of(
                        "total", 1,
                        "items", List.of(Map.of(
                                "id", f.getId(),
                                "name", f.getOriginalFilename(),
                                "size", f.getSize(),
                                "type", "file",
                                "createTime", f.getCreateTime()
                        ))
                ));
            }
            if (sessionToken == null || sessionToken.isBlank()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "缺少访问令牌"));
            }
            ShareService.DecodedSession session = shareService.parseSessionTokenInternal(sessionToken);
            if (!id.equals(session.shareId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "令牌无效"));
            }
            if (!session.allowPreview && !session.allowDownload) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "未授权访问"));
            }
            Long targetFolderId = folderId != null ? folderId : share.getResourceId();
            com.filemanager.entity.Folder targetFolder = folderRepository.findById(targetFolderId).orElse(null);
            if (targetFolder == null || !shareService.isInFolderSubtree(targetFolder, share.getResourceId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "目标目录不在分享范围内"));
            }
            Long ownerId = share.getOwner().getId();
            int safePage = Math.max(page, 0);
            int safeSize = Math.max(1, Math.min(size, 200));
            long folderTotal = folderRepository.countByUser_IdAndParent_IdAndDeletedFalse(ownerId, targetFolderId);
            long fileTotal = fileRepository.countByUser_IdAndFolder_IdAndDeletedFalse(ownerId, targetFolderId);
            long total = folderTotal + fileTotal;
            int fetchLimit = (int) Math.min((long) (safePage + 1) * safeSize, Math.min(total, (long) Integer.MAX_VALUE));
            var folderPage = folderRepository.findByUser_IdAndParent_IdAndDeletedFalse(ownerId, targetFolderId,
                    org.springframework.data.domain.PageRequest.of(0, Math.max(fetchLimit, safeSize), org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createTime")));
            var filePage = fileRepository.findByUser_IdAndFolder_IdAndDeletedFalse(ownerId, targetFolderId,
                    org.springframework.data.domain.PageRequest.of(0, Math.max(fetchLimit, safeSize), org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createTime")));
            List<com.filemanager.entity.Folder> folders = folderPage.getContent();
            List<com.filemanager.entity.File> files = filePage.getContent();
            List<Map<String, Object>> items = new java.util.ArrayList<>();
            int from = safePage * safeSize;
            int to = Math.min(from + safeSize, (int) Math.min(total, (long) Integer.MAX_VALUE));
            int idx = 0;
            int fi = 0;
            int fo = 0;
            while (idx < to && (fo < folders.size() || fi < files.size())) {
                boolean pickFolder;
                if (fo >= folders.size()) {
                    pickFolder = false;
                } else if (fi >= files.size()) {
                    pickFolder = true;
                } else {
                    java.time.LocalDateTime fa = folders.get(fo).getCreateTime() != null ? folders.get(fo).getCreateTime() : java.time.LocalDateTime.MIN;
                    java.time.LocalDateTime fb = files.get(fi).getCreateTime() != null ? files.get(fi).getCreateTime() : java.time.LocalDateTime.MIN;
                    pickFolder = fa.isAfter(fb) || fa.equals(fb);
                }
                if (pickFolder) {
                    var f = folders.get(fo++);
                    if (idx >= from) {
                        items.add(Map.of("id", f.getId(), "name", f.getName(), "size", 0, "type", "folder", "createTime", f.getCreateTime()));
                    }
                } else {
                    var f = files.get(fi++);
                    if (idx >= from) {
                        items.add(Map.of("id", f.getId(), "name", f.getOriginalFilename(), "size", f.getSize(), "type", "file", "createTime", f.getCreateTime()));
                    }
                }
                idx++;
            }
            auditLogService.logSystemSuccess("SHARE_LIST", "SHARE", id, null, "公开分享列表访问", System.currentTimeMillis() - start);
            return ResponseEntity.ok(Map.of("total", total, "items", items));
        } catch (com.filemanager.exception.ForbiddenException fe) {
            auditLogService.logSystemFailure("SHARE_LIST", "SHARE", id, null, "公开分享列表拒绝", fe.getMessage(), System.currentTimeMillis() - start);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", fe.getMessage()));
        } catch (Exception e) {
            auditLogService.logSystemFailure("SHARE_LIST", "SHARE", id, null, "公开分享列表异常", e.getMessage(), System.currentTimeMillis() - start);
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new com.filemanager.exception.ForbiddenException("未登录");
        }
        return userService.getUserIdByUsername(auth.getName());
    }

    private String sanitizeAsciiFilename(String name) {
        if (name == null) return "file";
        return name.replaceAll("[^\\x20-\\x7E]", "_");
    }
}
