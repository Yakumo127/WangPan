package com.filemanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FolderSimpleDto {
    private Long id;
    private String name;
    private Long parentId;
    private java.time.LocalDateTime createTime;
}
