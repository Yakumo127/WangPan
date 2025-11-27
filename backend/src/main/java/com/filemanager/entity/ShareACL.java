package com.filemanager.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "share_acl")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareACL {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "share_id", nullable = false)
    @JsonIgnore
    private Share share;

    @Enumerated(EnumType.STRING)
    @Column(name = "principal_type", nullable = false, length = 20)
    private PrincipalType principalType;

    @Column(name = "principal_value", nullable = false, length = 255)
    private String principalValue;

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

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum PrincipalType {
        USER, EMAIL, GROUP
    }
}
