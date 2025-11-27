package com.filemanager.service;

import com.filemanager.entity.File;
import com.filemanager.entity.Share;
import com.filemanager.entity.ShareACL;
import com.filemanager.entity.User;
import com.filemanager.exception.ForbiddenException;
import com.filemanager.exception.NotFoundException;
import com.filemanager.repository.FileRepository;
import com.filemanager.repository.ShareACLRepository;
import com.filemanager.repository.ShareRepository;
import com.filemanager.repository.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ShareService {

    private final ShareRepository shareRepository;
    private final ShareACLRepository shareACLRepository;
    private final UserRepository userRepository;
    private final FileRepository fileRepository;
    private final com.filemanager.repository.FolderRepository folderRepository;
    private final DownloadTokenService downloadTokenService;

    @Value("${share.token.secret:enterpriseFileManagerShareSecretKey2024}")
    private String shareTokenSecret;

    @Value("${share.token.ttl-seconds:1800}")
    private long shareSessionTtlSeconds;

    @Value("${share.download.ttl-seconds:10}")
    private long shareDownloadTtlSeconds;

    private static final int CODE_FAIL_LIMIT = 4;
    private static final long CODE_BAN_HOURS = 24;

    @Transactional
    public Share createShare(CreateShareRequest req, Long ownerId) {
        User owner = userRepository.findById(ownerId).orElseThrow(() -> new NotFoundException("用户不存在"));
        if (req.getResourceType() == null || req.getResourceId() == null) {
            throw new IllegalArgumentException("缺少资源信息");
        }
        sanitizePermissions(req);
        Share share = new Share();
        share.setOwner(owner);
        share.setResourceType(req.getResourceType());
        share.setResourceId(req.getResourceId());
        share.setExpireTime(req.getExpireTime());
        share.setAllowPreview(req.getAllowPreview());
        share.setAllowDownload(req.getAllowDownload());
        share.setAllowUpload(req.getAllowUpload());
        share.setAllowReshare(req.getAllowReshare());
        share.setAllowDeleteMove(req.getAllowDeleteMove());
        if (req.getCode() != null && !req.getCode().isBlank()) {
            share.setCodeHash(sha256(req.getCode()));
        }
        share = shareRepository.save(share);
        if (req.getAcl() != null && !req.getAcl().isEmpty()) {
            List<ShareACL> aclList = new ArrayList<>();
            for (ACLItem item : req.getAcl()) {
                ShareACL acl = new ShareACL();
                acl.setShare(share);
                acl.setPrincipalType(item.getPrincipalType());
                acl.setPrincipalValue(item.getPrincipalValue());
                acl.setAllowPreview(item.getAllowPreview());
                acl.setAllowDownload(item.getAllowDownload());
                acl.setAllowUpload(item.getAllowUpload());
                acl.setAllowReshare(item.getAllowReshare());
                acl.setAllowDeleteMove(item.getAllowDeleteMove());
                aclList.add(acl);
            }
            shareACLRepository.saveAll(aclList);
        }
        return share;
    }

    @Transactional
    public Share updateShare(Long shareId, CreateShareRequest req, Long ownerId) {
        Share share = shareRepository.findByIdAndOwner(shareId, userRepository.findById(ownerId).orElseThrow(() -> new NotFoundException("用户不存在")))
                .orElseThrow(() -> new NotFoundException("分享不存在"));
        sanitizePermissions(req);
        if (req.getExpireTime() != null) {
            share.setExpireTime(req.getExpireTime());
        }
        if (req.getCode() != null) {
            share.setCodeHash(req.getCode().isBlank() ? null : sha256(req.getCode()));
            share.setCodeFailCount(0);
            share.setCodeBanUntil(null);
        }
        if (req.getAllowPreview() != null) share.setAllowPreview(req.getAllowPreview());
        if (req.getAllowDownload() != null) share.setAllowDownload(req.getAllowDownload());
        if (req.getAllowUpload() != null) share.setAllowUpload(req.getAllowUpload());
        if (req.getAllowReshare() != null) share.setAllowReshare(req.getAllowReshare());
        if (req.getAllowDeleteMove() != null) share.setAllowDeleteMove(req.getAllowDeleteMove());
        shareRepository.save(share);

        if (req.getAcl() != null) {
            // 覆盖式更新：先删后插
            shareACLRepository.deleteAll(shareACLRepository.findByShare(share));
            if (!req.getAcl().isEmpty()) {
                List<ShareACL> aclList = new ArrayList<>();
                for (ACLItem item : req.getAcl()) {
                    ShareACL acl = new ShareACL();
                    acl.setShare(share);
                    acl.setPrincipalType(item.getPrincipalType());
                    acl.setPrincipalValue(item.getPrincipalValue());
                    acl.setAllowPreview(item.getAllowPreview());
                    acl.setAllowDownload(item.getAllowDownload());
                    acl.setAllowUpload(item.getAllowUpload());
                    acl.setAllowReshare(item.getAllowReshare());
                    acl.setAllowDeleteMove(item.getAllowDeleteMove());
                    aclList.add(acl);
                }
                shareACLRepository.saveAll(aclList);
            }
        }
        return share;
    }

    @Transactional
    public void revokeShare(Long shareId, Long ownerId) {
        Share share = shareRepository.findByIdAndOwner(shareId, userRepository.findById(ownerId).orElseThrow(() -> new NotFoundException("用户不存在")))
                .orElseThrow(() -> new NotFoundException("分享不存在"));
        share.setStatus(Share.Status.REVOKED);
        shareRepository.save(share);
    }

    public ShareListResult listMyShares(Long ownerId, int page, int size) {
        User owner = userRepository.findById(ownerId).orElseThrow(() -> new NotFoundException("用户不存在"));
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 200));
        var pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        var pageData = shareRepository.findByOwner(owner, pageable);
        List<Map<String, Object>> dto = pageData.getContent().stream().map(s -> {
            Map<String, Object> base = new HashMap<>();
            base.put("id", s.getId());
            base.put("resourceId", s.getResourceId());
            base.put("resourceType", s.getResourceType());
            base.put("expireTime", s.getExpireTime());
            base.put("status", s.getStatus());
            base.put("viewCount", s.getViewCount());
            base.put("downloadCount", s.getDownloadCount());
            base.put("createdAt", s.getCreatedAt());
            base.put("allowPreview", s.getAllowPreview());
            base.put("allowDownload", s.getAllowDownload());
            base.put("allowUpload", s.getAllowUpload());
            base.put("allowReshare", s.getAllowReshare());
            base.put("allowDeleteMove", s.getAllowDeleteMove());
            base.put("shareMode", s.getShareMode());
            try {
                if (s.getResourceType() == Share.ResourceType.FILE) {
                    com.filemanager.entity.File f = fileRepository.findById(s.getResourceId()).orElse(null);
                    if (f != null) {
                        base.put("name", f.getOriginalFilename());
                        base.put("size", f.getSize());
                    }
                } else {
                    com.filemanager.entity.Folder folder = folderRepository.findById(s.getResourceId()).orElse(null);
                    if (folder != null) {
                        base.put("name", folder.getName());
                        base.put("size", 0);
                    }
                }
            } catch (Exception ignore) {}
            return base;
        }).toList();
        ShareListResult result = new ShareListResult();
        result.setTotal(pageData.getTotalElements());
        ShareStats stats = new ShareStats();
        stats.setTotalShares(shareRepository.countByOwner(owner));
        stats.setActiveShares(shareRepository.countByOwnerAndStatus(owner, Share.Status.ACTIVE));
        stats.setTotalDownloads(Optional.ofNullable(shareRepository.sumDownloadCountByOwner(owner)).orElse(0L));
        stats.setTotalViews(Optional.ofNullable(shareRepository.sumViewCountByOwner(owner)).orElse(0L));
        result.setStats(stats);
        result.setItems(dto);
        return result;
    }

    public List<ShareACL> getAclForOwner(Long shareId, Long ownerId) {
        Share share = shareRepository.findByIdAndOwner(shareId, userRepository.findById(ownerId).orElseThrow(() -> new NotFoundException("用户不存在")))
                .orElseThrow(() -> new NotFoundException("分享不存在"));
        return shareACLRepository.findByShare(share);
    }

    @Transactional
    public List<ShareACL> replaceAcl(Long shareId, Long ownerId, List<ACLItem> items) {
        Share share = shareRepository.findByIdAndOwner(shareId, userRepository.findById(ownerId).orElseThrow(() -> new NotFoundException("用户不存在")))
                .orElseThrow(() -> new NotFoundException("分享不存在"));
        shareACLRepository.deleteAll(shareACLRepository.findByShare(share));
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<ShareACL> aclList = new ArrayList<>();
        for (ACLItem item : items) {
            validatePrincipal(item);
            ShareACL acl = new ShareACL();
            acl.setShare(share);
            acl.setPrincipalType(item.getPrincipalType());
            acl.setPrincipalValue(item.getPrincipalValue());
            acl.setAllowPreview(item.getAllowPreview());
            acl.setAllowDownload(item.getAllowDownload());
            acl.setAllowUpload(item.getAllowUpload());
            acl.setAllowReshare(item.getAllowReshare());
            acl.setAllowDeleteMove(item.getAllowDeleteMove());
            aclList.add(acl);
        }
        return shareACLRepository.saveAll(aclList);
    }

    public SharePublicView getPublicShare(Long shareId) {
        Share share = shareRepository.findActiveById(shareId, Share.Status.ACTIVE)
                .orElseThrow(() -> new NotFoundException("分享不存在或已撤销"));
        if (isExpired(share)) {
            throw new ForbiddenException("分享已过期");
        }
        SharePublicView view = new SharePublicView();
        view.setShareId(share.getId());
        view.setResourceId(share.getResourceId());
        view.setResourceType(share.getResourceType());
        view.setExpireTime(share.getExpireTime());
        view.setRequireCode(share.getCodeHash() != null);
        view.setAllowPreview(Boolean.TRUE.equals(share.getAllowPreview()));
        view.setAllowDownload(Boolean.TRUE.equals(share.getAllowDownload()));
        // 附带资源名称/大小
        try {
            if (share.getResourceType() == Share.ResourceType.FILE) {
                File f = fileRepository.findById(share.getResourceId()).orElse(null);
                if (f != null) {
                    view.setResourceName(f.getOriginalFilename());
                    view.setResourceSize(f.getSize());
                }
            } else {
                com.filemanager.entity.Folder folder = folderRepository.findById(share.getResourceId()).orElse(null);
                if (folder != null) {
                    view.setResourceName(folder.getName());
                    view.setResourceSize(0L);
                }
            }
        } catch (Exception ignore) {}
        return view;
    }

    @Transactional
    public ShareSession validateAccess(Long shareId, String code, Principal principal) {
        Share share = shareRepository.findActiveAndNotExpired(shareId, Share.Status.ACTIVE, LocalDateTime.now())
                .orElseThrow(() -> new NotFoundException("分享不存在或已失效"));
        if (share.getCodeBanUntil() != null && share.getCodeBanUntil().isAfter(LocalDateTime.now())) {
            throw new ForbiddenException("提取码已被锁定，请稍后重试");
        }
        if (share.getCodeHash() != null) {
            if (code == null || !share.getCodeHash().equals(sha256(code))) {
                int fails = Optional.ofNullable(share.getCodeFailCount()).orElse(0) + 1;
                share.setCodeFailCount(fails);
                if (fails >= CODE_FAIL_LIMIT) {
                    share.setCodeBanUntil(LocalDateTime.now().plusHours(CODE_BAN_HOURS));
                }
                shareRepository.save(share);
                throw new ForbiddenException(fails >= CODE_FAIL_LIMIT ? "提取码错误次数过多，已封禁" : "提取码错误");
            }
            share.setCodeFailCount(0);
            share.setCodeBanUntil(null);
        }
        // 计算权限组合
        Permissions perms = resolvePermissions(share, principal);
        String token = generateShareSessionToken(share.getId(), principal, perms);
        ShareSession session = new ShareSession();
        session.setShareId(share.getId());
        session.setSessionToken(token);
        session.setAllowPreview(perms.isAllowPreview());
        session.setAllowDownload(perms.isAllowDownload());
        session.setAllowUpload(perms.isAllowUpload());
        session.setAllowReshare(perms.isAllowReshare());
        session.setAllowDeleteMove(perms.isAllowDeleteMove());
        return session;
    }

    public DecodedSession parseSessionTokenInternal(String token) {
        return parseShareSessionToken(token);
    }

    public Share getActiveShare(Long shareId) {
        return shareRepository.findActiveAndNotExpired(shareId, Share.Status.ACTIVE, LocalDateTime.now())
                .orElseThrow(() -> new NotFoundException("分享不存在或已失效"));
    }

    public String generateDownloadUrl(Long shareId, Long fileId, String sessionToken) {
        DecodedSession session = parseShareSessionToken(sessionToken);
        if (!shareId.equals(session.shareId)) {
            throw new ForbiddenException("会话无效");
        }
        Share share = shareRepository.findActiveAndNotExpired(shareId, Share.Status.ACTIVE, LocalDateTime.now())
                .orElseThrow(() -> new NotFoundException("分享不存在或已失效"));
        Permissions perms = new Permissions(session.allowPreview, session.allowDownload, session.allowUpload, session.allowReshare, session.allowDeleteMove);
        if (!perms.isAllowDownload()) {
            throw new ForbiddenException("未授权下载");
        }
        // 校验资源范围
        File file = fileRepository.findById(fileId).orElseThrow(() -> new NotFoundException("文件不存在"));
        if (!file.getUser().getId().equals(share.getOwner().getId())) {
            throw new ForbiddenException("无权下载该文件");
        }
        if (share.getResourceType() == Share.ResourceType.FILE && !share.getResourceId().equals(fileId)) {
            throw new ForbiddenException("文件不在分享范围内");
        }
        if (share.getResourceType() == Share.ResourceType.FOLDER) {
            if (!isInFolderSubtree(file.getFolder(), share.getResourceId())) {
                throw new ForbiddenException("文件不在分享文件夹下");
            }
        }
        String downloadToken = generateShareDownloadToken(shareId, fileId, session.principalType, session.principalValue, perms);
        return "/api/public/shares/direct-download?token=" + downloadToken;
    }

    public ShareDownloadPayload parseDownloadToken(String token) {
        DecodedShareToken decoded = parseShareDownloadToken(token);
        Share share = shareRepository.findActiveAndNotExpired(decoded.shareId, Share.Status.ACTIVE, LocalDateTime.now())
                .orElseThrow(() -> new NotFoundException("分享不存在或已失效"));
        Permissions perms = new Permissions(decoded.allowPreview, decoded.allowDownload, decoded.allowUpload, decoded.allowReshare, decoded.allowDeleteMove);
        if (!perms.isAllowDownload()) {
            throw new ForbiddenException("未授权下载");
        }
        File file = fileRepository.findById(decoded.fileId).orElseThrow(() -> new NotFoundException("文件不存在"));
        if (!file.getUser().getId().equals(share.getOwner().getId())) {
            throw new ForbiddenException("无权下载该文件");
        }
        if (share.getResourceType() == Share.ResourceType.FILE && !share.getResourceId().equals(decoded.fileId)) {
            throw new ForbiddenException("文件不在分享范围内");
        }
        if (share.getResourceType() == Share.ResourceType.FOLDER) {
            if (!isInFolderSubtree(file.getFolder(), share.getResourceId())) {
                throw new ForbiddenException("文件不在分享文件夹下");
            }
        }
        return new ShareDownloadPayload(share, decoded.fileId, perms, decoded.principalType, decoded.principalValue, token);
    }

    @Transactional
    public void incrementView(Long shareId) {
        shareRepository.findById(shareId).ifPresent(s -> {
            s.setViewCount(Optional.ofNullable(s.getViewCount()).orElse(0L) + 1);
            s.setLastAccessedAt(LocalDateTime.now());
            shareRepository.save(s);
        });
    }

    @Transactional
    public void incrementDownload(Share share) {
        share.setDownloadCount(Optional.ofNullable(share.getDownloadCount()).orElse(0L) + 1);
        shareRepository.save(share);
    }

    private boolean isExpired(Share share) {
        return share.getExpireTime() != null && share.getExpireTime().isBefore(LocalDateTime.now());
    }

    private Permissions resolvePermissions(Share share, Principal principal) {
        Permissions base = new Permissions(
                Boolean.TRUE.equals(share.getAllowPreview()),
                Boolean.TRUE.equals(share.getAllowDownload()),
                Boolean.TRUE.equals(share.getAllowUpload()),
                Boolean.TRUE.equals(share.getAllowReshare()),
                Boolean.TRUE.equals(share.getAllowDeleteMove())
        );
        if (principal == null || principal.getType() == null || principal.getValue() == null) {
            return base;
        }
        Optional<ShareACL> aclOpt = shareACLRepository.findByShareAndPrincipal(share, principal.getType(), principal.getValue());
        if (aclOpt.isPresent()) {
            ShareACL a = aclOpt.get();
            return new Permissions(
                    Boolean.TRUE.equals(a.getAllowPreview()),
                    Boolean.TRUE.equals(a.getAllowDownload()),
                    Boolean.TRUE.equals(a.getAllowUpload()),
                    Boolean.TRUE.equals(a.getAllowReshare()),
                    Boolean.TRUE.equals(a.getAllowDeleteMove())
            );
        }
        return base;
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) sb.append('0');
                sb.append(h);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("计算哈希失败", e);
        }
    }

    // -------- share session token --------
    private String generateShareSessionToken(Long shareId, Principal principal, Permissions perms) {
        long exp = System.currentTimeMillis() + shareSessionTtlSeconds * 1000L;
        String payloadJson = String.format(Locale.ROOT,
                "{\"sid\":%d,\"pt\":\"%s\",\"pv\":\"%s\",\"p1\":%s,\"p2\":%s,\"p3\":%s,\"p4\":%s,\"p5\":%s,\"exp\":%d}",
                shareId,
                principal != null && principal.getType() != null ? principal.getType().name() : "",
                principal != null ? escape(principal.getValue()) : "",
                perms.allowPreview, perms.allowDownload, perms.allowUpload, perms.allowReshare, perms.allowDeleteMove,
                exp);
        return sign(payloadJson);
    }

    private DecodedSession parseShareSessionToken(String token) {
        String payload = verify(token);
        try {
            Map<String, String> map = parseSimpleJson(payload);
            DecodedSession s = new DecodedSession();
            s.shareId = Long.parseLong(map.get("sid"));
            s.principalType = parsePrincipalType(map.get("pt"));
            s.principalValue = map.get("pv");
            s.allowPreview = Boolean.parseBoolean(map.get("p1"));
            s.allowDownload = Boolean.parseBoolean(map.get("p2"));
            s.allowUpload = Boolean.parseBoolean(map.get("p3"));
            s.allowReshare = Boolean.parseBoolean(map.get("p4"));
            s.allowDeleteMove = Boolean.parseBoolean(map.get("p5"));
            long exp = Long.parseLong(map.get("exp"));
            if (exp <= System.currentTimeMillis()) {
                throw new ForbiddenException("会话已过期");
            }
            return s;
        } catch (Exception e) {
            throw new ForbiddenException("会话无效");
        }
    }

    // -------- share download token（一性） --------
    private String generateShareDownloadToken(Long shareId, Long fileId, ShareACL.PrincipalType principalType, String principalValue, Permissions perms) {
        long exp = System.currentTimeMillis() + shareDownloadTtlSeconds * 1000L;
        String payloadJson = String.format(Locale.ROOT,
                "{\"sid\":%d,\"fid\":%d,\"pt\":\"%s\",\"pv\":\"%s\",\"p1\":%s,\"p2\":%s,\"p3\":%s,\"p4\":%s,\"p5\":%s,\"exp\":%d,\"nonce\":\"%s\"}",
                shareId, fileId,
                principalType != null ? principalType.name() : "",
                principalValue != null ? escape(principalValue) : "",
                perms.allowPreview, perms.allowDownload, perms.allowUpload, perms.allowReshare, perms.allowDeleteMove,
                exp, UUID.randomUUID().toString());
        return sign(payloadJson);
    }

    private DecodedShareToken parseShareDownloadToken(String token) {
        String payload = verify(token);
        try {
            Map<String, String> map = parseSimpleJson(payload);
            DecodedShareToken s = new DecodedShareToken();
            s.shareId = Long.parseLong(map.get("sid"));
            s.fileId = Long.parseLong(map.get("fid"));
            s.principalType = parsePrincipalType(map.get("pt"));
            s.principalValue = map.get("pv");
            s.allowPreview = Boolean.parseBoolean(map.get("p1"));
            s.allowDownload = Boolean.parseBoolean(map.get("p2"));
            s.allowUpload = Boolean.parseBoolean(map.get("p3"));
            s.allowReshare = Boolean.parseBoolean(map.get("p4"));
            s.allowDeleteMove = Boolean.parseBoolean(map.get("p5"));
            long exp = Long.parseLong(map.get("exp"));
            if (exp <= System.currentTimeMillis()) {
                throw new ForbiddenException("下载链接已过期");
            }
            return s;
        } catch (Exception e) {
            throw new ForbiddenException("下载链接无效");
        }
    }

    private String sign(String payloadJson) {
        String payloadBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signatureBase64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hmacSha256(payloadBase64.getBytes(StandardCharsets.UTF_8), shareTokenSecret.getBytes(StandardCharsets.UTF_8)));
        return payloadBase64 + "." + signatureBase64;
    }

    private String verify(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 2) throw new ForbiddenException("令牌无效");
        byte[] expected = hmacSha256(parts[0].getBytes(StandardCharsets.UTF_8), shareTokenSecret.getBytes(StandardCharsets.UTF_8));
        byte[] actual;
        try {
            actual = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException e) {
            throw new ForbiddenException("令牌无效");
        }
        if (!constantTimeEquals(expected, actual)) {
            throw new ForbiddenException("令牌校验失败");
        }
        return new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
    }

    private byte[] hmacSha256(byte[] data, byte[] key) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256");
            mac.init(keySpec);
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("计算签名失败", e);
        }
    }

    private boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        int res = 0;
        for (int i = 0; i < a.length; i++) {
            res |= a[i] ^ b[i];
        }
        return res == 0;
    }

    private Map<String, String> parseSimpleJson(String json) {
        Map<String, String> map = new HashMap<>();
        String cleaned = json.trim();
        if (cleaned.startsWith("{")) cleaned = cleaned.substring(1);
        if (cleaned.endsWith("}")) cleaned = cleaned.substring(0, cleaned.length() - 1);
        String[] parts = cleaned.split(",");
        for (String p : parts) {
            String[] kv = p.split(":", 2);
            if (kv.length != 2) continue;
            String key = kv[0].trim().replace("\"", "");
            String val = kv[1].trim();
            if (val.startsWith("\"") && val.endsWith("\"")) {
                val = val.substring(1, val.length() - 1);
            }
            map.put(key, val);
        }
        return map;
    }

    private ShareACL.PrincipalType parsePrincipalType(String pt) {
        if (pt == null || pt.isBlank()) return null;
        try {
            return ShareACL.PrincipalType.valueOf(pt);
        } catch (Exception e) {
            return null;
        }
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 公开模式兜底：禁止高风险权限位
     */
    private void sanitizePermissions(CreateShareRequest req) {
        if (req == null) return;
        if (req.getShareMode() == null) {
            req.setShareMode(Share.ShareMode.PUBLIC);
        }
        if (req.getShareMode() == Share.ShareMode.PUBLIC) {
            // 公开模式兜底关闭高风险位
            req.setAllowUpload(false);
            req.setAllowReshare(false);
            req.setAllowDeleteMove(false);
        }
        // 文件分享不允许上传
        if (req.getResourceType() == Share.ResourceType.FILE) {
            req.setAllowUpload(false);
        }
        // 基础权限默认开启预览+下载
        if (req.getAllowPreview() == null) req.setAllowPreview(true);
        if (req.getAllowDownload() == null) req.setAllowDownload(true);
    }

    private void validatePrincipal(ACLItem item) {
        if (item == null || item.getPrincipalType() == null || item.getPrincipalValue() == null || item.getPrincipalValue().isBlank()) {
            throw new IllegalArgumentException("ACL 受邀人信息不完整");
        }
        if (item.getPrincipalType() == ShareACL.PrincipalType.EMAIL) {
            String v = item.getPrincipalValue();
            if (!v.contains("@")) {
                throw new IllegalArgumentException("邮箱格式不正确");
            }
        }
    }

    // -------- DTOs --------
    @Data
    public static class ShareStats {
        private long totalShares;
        private long activeShares;
        private long totalDownloads;
        private long totalViews;
    }

    @Data
    public static class ShareListResult {
        private long total;
        private List<Map<String, Object>> items;
        private ShareStats stats;
    }

    @Data
    public static class CreateShareRequest {
        private Share.ResourceType resourceType;
        private Long resourceId;
        private LocalDateTime expireTime;
        private String code;
        private Boolean allowPreview = true;
        private Boolean allowDownload = true;
        private Boolean allowUpload = false;
        private Boolean allowReshare = false;
        private Boolean allowDeleteMove = false;
        private Share.ShareMode shareMode = Share.ShareMode.PUBLIC;
        private List<ACLItem> acl;
    }

    @Data
    public static class ACLItem {
        private ShareACL.PrincipalType principalType;
        private String principalValue;
        private Boolean allowPreview = true;
        private Boolean allowDownload = true;
        private Boolean allowUpload = false;
        private Boolean allowReshare = false;
        private Boolean allowDeleteMove = false;
    }

    @Data
    public static class Principal {
        private ShareACL.PrincipalType type;
        private String value;
    }

    @Data
    public static class SharePublicView {
        private Long shareId;
        private Long resourceId;
        private Share.ResourceType resourceType;
        private LocalDateTime expireTime;
        private boolean requireCode;
        private boolean allowPreview;
        private boolean allowDownload;
        private String resourceName;
        private Long resourceSize;
    }

    @Data
    public static class ShareSession {
        private Long shareId;
        private String sessionToken;
        private boolean allowPreview;
        private boolean allowDownload;
        private boolean allowUpload;
        private boolean allowReshare;
        private boolean allowDeleteMove;
    }

    public static class Permissions {
        private final boolean allowPreview;
        private final boolean allowDownload;
        private final boolean allowUpload;
        private final boolean allowReshare;
        private final boolean allowDeleteMove;

        public Permissions(boolean allowPreview, boolean allowDownload, boolean allowUpload, boolean allowReshare, boolean allowDeleteMove) {
            this.allowPreview = allowPreview;
            this.allowDownload = allowDownload;
            this.allowUpload = allowUpload;
            this.allowReshare = allowReshare;
            this.allowDeleteMove = allowDeleteMove;
        }

        public boolean isAllowPreview() { return allowPreview; }
        public boolean isAllowDownload() { return allowDownload; }
        public boolean isAllowUpload() { return allowUpload; }
        public boolean isAllowReshare() { return allowReshare; }
        public boolean isAllowDeleteMove() { return allowDeleteMove; }
    }

    public static class ShareDownloadPayload {
        public final Share share;
        public final Long fileId;
        public final Permissions permissions;
        public final ShareACL.PrincipalType principalType;
        public final String principalValue;
        public final String rawToken;
        public ShareDownloadPayload(Share share, Long fileId, Permissions permissions, ShareACL.PrincipalType principalType, String principalValue, String rawToken) {
            this.share = share;
            this.fileId = fileId;
            this.permissions = permissions;
            this.principalType = principalType;
            this.principalValue = principalValue;
            this.rawToken = rawToken;
        }
    }

    public long getShareDownloadTtlSeconds() {
        return shareDownloadTtlSeconds;
    }

    public boolean isInFolderSubtree(com.filemanager.entity.Folder folder, Long rootId) {
        if (folder == null || rootId == null) return false;
        com.filemanager.entity.Folder cur = folder;
        while (cur != null) {
            if (rootId.equals(cur.getId())) return true;
            cur = cur.getParent();
        }
        return false;
    }

    public static class DecodedSession {
        public Long shareId;
        public ShareACL.PrincipalType principalType;
        public String principalValue;
        public boolean allowPreview;
        public boolean allowDownload;
        public boolean allowUpload;
        public boolean allowReshare;
        public boolean allowDeleteMove;
    }

    private static class DecodedShareToken {
        Long shareId;
        Long fileId;
        ShareACL.PrincipalType principalType;
        String principalValue;
        boolean allowPreview;
        boolean allowDownload;
        boolean allowUpload;
        boolean allowReshare;
        boolean allowDeleteMove;
    }
}
