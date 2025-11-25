package com.filemanager.service;

import com.filemanager.dto.DashboardActivityDTO;
import com.filemanager.dto.DashboardFileDTO;
import com.filemanager.dto.DashboardSummaryDTO;
import com.filemanager.entity.File;
import com.filemanager.entity.User;
import com.filemanager.entity.UserLog;
import com.filemanager.repository.FileRepository;
import com.filemanager.repository.FolderRepository;
import com.filemanager.repository.UserLogRepository;
import com.filemanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final UserLogRepository userLogRepository;

    @Transactional(readOnly = true)
    public DashboardSummaryDTO getUserSummary(Long userId, int activityLimit, int uploadLimit) {
        DashboardSummaryDTO dto = new DashboardSummaryDTO();

        dto.setFileCount(safeCount(() -> fileRepository.countByUser_IdAndDeletedFalse(userId)));
        dto.setFolderCount(safeCount(() -> folderRepository.countByUser_IdAndDeletedFalse(userId)));
        dto.setRecycleCount(safeCount(() -> fileRepository.countByUser_IdAndDeletedTrueAndOwnerHiddenFalse(userId)));

        User user = userRepository.findById(userId).orElse(null);
        dto.setQuotaLimit(user != null && user.getQuotaLimit() != null ? user.getQuotaLimit() : 0L);
        dto.setQuotaUsed(user != null && user.getQuotaUsed() != null ? user.getQuotaUsed() : 0L);

        dto.setRecentUploads(buildRecentUploads(userId, uploadLimit));
        dto.setRecentActivities(buildRecentActivities(userId, activityLimit));

        return dto;
    }

    private List<DashboardFileDTO> buildRecentUploads(Long userId, int limit) {
        int size = normalizeLimit(limit);
        List<File> files = fileRepository.findByUser_IdAndDeletedFalseOrderByCreateTimeDesc(
                userId, PageRequest.of(0, size)
        ).getContent();
        if (files == null || files.isEmpty()) {
            return Collections.emptyList();
        }
        return files.stream().map(this::toFileDto).collect(Collectors.toList());
    }

    private List<DashboardActivityDTO> buildRecentActivities(Long userId, int limit) {
        int size = normalizeLimit(limit);
        List<UserLog> logs = userLogRepository.findByUser_IdOrderByCreateTimeDesc(userId, PageRequest.of(0, size));
        if (logs == null || logs.isEmpty()) {
            return Collections.emptyList();
        }
        return logs.stream().map(this::toActivityDto).collect(Collectors.toList());
    }

    private DashboardFileDTO toFileDto(File file) {
        DashboardFileDTO dto = new DashboardFileDTO();
        dto.setId(file.getId());
        dto.setName(file.getOriginalFilename() != null ? file.getOriginalFilename() : file.getFilename());
        dto.setSize(file.getSize());
        dto.setCreateTime(file.getCreateTime());
        // 复用实体上的逻辑路径，避免在此重复遍历
        dto.setFolderPath(file.getFolderPath());
        return dto;
    }

    private DashboardActivityDTO toActivityDto(UserLog log) {
        DashboardActivityDTO dto = new DashboardActivityDTO();
        dto.setActionType(log.getActionType());
        dto.setResourceType(log.getResourceType());
        dto.setResourceId(log.getResourceId());
        dto.setResourceName(log.getResourceName());
        dto.setStatus(log.getStatus());
        dto.setActionDescription(log.getActionDescription());
        dto.setTime(log.getCreateTime());
        return dto;
    }

    private long safeCount(java.util.concurrent.Callable<Long> callable) {
        try {
            Long v = callable.call();
            return v == null ? 0L : v;
        } catch (Exception e) {
            return 0L;
        }
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) return 5;
        return Math.min(limit, 50);
    }
}
