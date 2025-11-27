package com.filemanager.controller;

import com.filemanager.entity.Share;
import com.filemanager.entity.ShareACL;
import com.filemanager.service.DownloadTokenService;
import com.filemanager.service.FileService;
import com.filemanager.service.ShareService;
import com.filemanager.service.UserService;
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

    // ---------- 受保护接口 ----------
    @PostMapping("/shares")
    public ResponseEntity<?> createShare(@RequestBody ShareService.CreateShareRequest request) {
        Long userId = currentUserId();
        try {
            Share share = shareService.createShare(request, userId);
            return ResponseEntity.ok(Map.of(
                    "id", share.getId(),
                    "resourceId", share.getResourceId(),
                    "resourceType", share.getResourceType(),
                    "expireTime", share.getExpireTime(),
                    "status", share.getStatus()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/shares/{id}")
    public ResponseEntity<?> updateShare(@PathVariable Long id, @RequestBody ShareService.CreateShareRequest request) {
        Long userId = currentUserId();
        try {
            Share share = shareService.updateShare(id, request, userId);
            return ResponseEntity.ok(Map.of(
                    "id", share.getId(),
                    "expireTime", share.getExpireTime(),
                    "status", share.getStatus()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/shares/{id}")
    public ResponseEntity<?> revokeShare(@PathVariable Long id) {
        Long userId = currentUserId();
        try {
            shareService.revokeShare(id, userId);
            return ResponseEntity.ok(Map.of("message", "已取消分享"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/shares")
    public ResponseEntity<?> listMyShares() {
        Long userId = currentUserId();
        try {
            List<Share> list = shareService.listMyShares(userId);
            return ResponseEntity.ok(list);
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
        try {
            ShareService.CreateShareRequest req = new ShareService.CreateShareRequest();
            req.setAcl(acl);
            Share share = shareService.updateShare(id, req, userId);
            return ResponseEntity.ok(Map.of("id", share.getId(), "aclSize", acl == null ? 0 : acl.size()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ---------- 公开接口 ----------
    @GetMapping("/public/shares/{id}")
    public ResponseEntity<?> publicShare(@PathVariable Long id) {
        try {
            ShareService.SharePublicView view = shareService.getPublicShare(id);
            shareService.incrementView(id);
            return ResponseEntity.ok(view);
        } catch (com.filemanager.exception.ForbiddenException fe) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", fe.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/public/shares/{id}/validate")
    public ResponseEntity<?> validateShare(@PathVariable Long id, @RequestBody Map<String, String> body) {
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
            return ResponseEntity.ok(session);
        } catch (com.filemanager.exception.ForbiddenException fe) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", fe.getMessage()));
        } catch (Exception e) {
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
        try {
            payload = shareService.parseDownloadToken(token);
        } catch (com.filemanager.exception.ForbiddenException fe) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        } catch (Exception e) {
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
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }
            shareService.incrementDownload(payload.share);
            StreamingResponseBody body = outputStream -> Files.copy(path, outputStream);
            String name = file.getOriginalFilename();
            String asciiName = sanitizeAsciiFilename(name);
            String encoded = org.springframework.web.util.UriUtils.encode(asciiName, java.nio.charset.StandardCharsets.UTF_8);
            String disposition = String.format("attachment; filename=\"%s\"; filename*=UTF-8''%s", asciiName, encoded);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(body);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @GetMapping("/public/shares/{id}/list")
    public ResponseEntity<?> listShareContent(@PathVariable Long id,
                                              @RequestParam(value = "token", required = false) String sessionToken) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(Map.of("message", "文件夹内容列出暂未实现"));
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
