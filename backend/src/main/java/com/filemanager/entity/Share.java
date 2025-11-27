package com.filemanager.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "shares")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Share {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResourceType resourceType;

    @Column(nullable = false)
    private Long resourceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    @JsonIgnore
    private User owner;

    @Column(name = "code_hash")
    private String codeHash;

    @Column(name = "expire_time")
    private LocalDateTime expireTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    // 权限位（可组合）
    @Column(name = "allow_preview")
    private Boolean allowPreview = true;

    @Column(name = "allow_download")
    private Boolean allowDownload = true;

    @Column(name = "allow_upload")
    private Boolean allowUpload = false;

    @Column(name = "allow_reshare")
    private Boolean allowReshare = false;

    @Column(name = "allow_delete_move")
    private Boolean allowDeleteMove = false;

    @Column(name = "view_count")
    private Long viewCount = 0L;

    @Column(name = "download_count")
    private Long downloadCount = 0L;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    // 提取码防爆破
    @Column(name = "code_fail_count")
    private Integer codeFailCount = 0;

    @Column(name = "code_ban_until")
    private LocalDateTime codeBanUntil;

    @OneToMany(mappedBy = "share", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<ShareACL> aclEntries = new ArrayList<>();

    public enum ResourceType {
        FILE, FOLDER
    }

    public enum Status {
        ACTIVE, REVOKED
    }
}
