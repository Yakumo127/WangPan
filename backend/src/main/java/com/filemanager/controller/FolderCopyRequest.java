package com.filemanager.controller;

import lombok.Data;

@Data
public class FolderCopyRequest {
    private Long targetParentId;
    private String newName;
}
