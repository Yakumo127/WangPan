package com.filemanager.service;

import com.filemanager.dto.UserLogDTO;
import com.filemanager.entity.UserLog;
import com.filemanager.repository.UserLogRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AuditLogQueryService {
    private final UserLogRepository userLogRepository;

    public Page<UserLogDTO> query(
            Set<String> actions,
            Set<String> resourceTypes,
            String status,
            String keyword,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int size,
            Sort sort
    ) {
        Specification<UserLog> spec = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (actions != null && !actions.isEmpty()) {
                ps.add(root.get("actionType").in(actions));
            }
            if (resourceTypes != null && !resourceTypes.isEmpty()) {
                ps.add(root.get("resourceType").in(resourceTypes));
            }
            if (status != null && !status.isBlank()) {
                ps.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("createTime"), from));
            }
            if (to != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("createTime"), to));
            }
            if (keyword != null && !keyword.isBlank()) {
                Join<Object, Object> userJoin = root.join("user");
                String like = "%" + keyword.trim() + "%";
                ps.add(cb.or(
                        cb.like(userJoin.get("username"), like),
                        cb.like(userJoin.get("displayName"), like)
                ));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1), sort);
        Page<UserLog> result = userLogRepository.findAll(spec, pageable);
        List<UserLogDTO> content = result.getContent().stream().map(this::toDTO).toList();
        return new PageImpl<>(content, pageable, result.getTotalElements());
    }

    public java.util.List<UserLogDTO> queryAllLimited(
            Set<String> actions,
            Set<String> resourceTypes,
            String status,
            String keyword,
            LocalDateTime from,
            LocalDateTime to,
            int limit,
            Sort sort
    ) {
        if (limit <= 0) return java.util.List.of();
        Specification<UserLog> spec = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (actions != null && !actions.isEmpty()) {
                ps.add(root.get("actionType").in(actions));
            }
            if (resourceTypes != null && !resourceTypes.isEmpty()) {
                ps.add(root.get("resourceType").in(resourceTypes));
            }
            if (status != null && !status.isBlank()) {
                ps.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("createTime"), from));
            }
            if (to != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("createTime"), to));
            }
            if (keyword != null && !keyword.isBlank()) {
                Join<Object, Object> userJoin = root.join("user");
                String like = "%" + keyword.trim() + "%";
                ps.add(cb.or(
                        cb.like(userJoin.get("username"), like),
                        cb.like(userJoin.get("displayName"), like)
                ));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        PageRequest pr = PageRequest.of(0, limit, sort);
        Page<UserLog> result = userLogRepository.findAll(spec, pr);
        return result.getContent().stream().map(this::toDTO).toList();
    }

    private UserLogDTO toDTO(UserLog log) {
        UserLogDTO dto = new UserLogDTO();
        dto.setId(log.getId());
        if (log.getUser() != null) {
            dto.setUserId(log.getUser().getId());
            dto.setUsername(log.getUser().getUsername());
            dto.setDisplayName(log.getUser().getDisplayName());
        }
        dto.setActionType(log.getActionType());
        dto.setActionDescription(log.getActionDescription());
        dto.setResourceType(log.getResourceType());
        dto.setResourceId(log.getResourceId());
        dto.setResourceName(log.getResourceName());
        dto.setStatus(log.getStatus());
        dto.setErrorMessage(log.getErrorMessage());
        dto.setIpAddress(log.getIpAddress());
        dto.setUserAgent(log.getUserAgent());
        dto.setExecutionTime(log.getExecutionTime());
        dto.setCreateTime(log.getCreateTime());
        return dto;
    }
}
