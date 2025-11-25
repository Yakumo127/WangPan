package com.filemanager.dto;

import java.time.LocalDateTime;

public class DashboardFileDTO {
    private Long id;
    private String name;
    private Long size;
    private LocalDateTime createTime;
    private String folderPath;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public String getFolderPath() { return folderPath; }
    public void setFolderPath(String folderPath) { this.folderPath = folderPath; }
}
