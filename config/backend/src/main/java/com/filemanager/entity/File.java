package com.filemanager.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "files")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class File {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String filename;
    
    @Column(nullable = false)
    private String originalFilename;
    
    private String contentType;
    
    @Column(nullable = false)
    private Long size;
    
    @Column(nullable = false)
    private String filePath;
    
    private String thumbnailPath;
    
    @Column(nullable = false)
    private String fileHash;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private Folder folder;
    
    @CreationTimestamp
    private LocalDateTime createTime;
    
    @UpdateTimestamp
    private LocalDateTime updateTime;
    
    private LocalDateTime deleteTime;
    
    @Column(nullable = false)
    private Boolean deleted = false;
    
    private Integer downloadCount = 0;
    
    private String description;
    
    public enum FileType {
        IMAGE, DOCUMENT, VIDEO, AUDIO, ARCHIVE, OTHER
    }
}