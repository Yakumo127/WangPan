package com.filemanager.task;

import com.filemanager.entity.UserLog;
import com.filemanager.repository.UserLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class AuditLogPurgeScheduler {

    private final UserLogRepository userLogRepository;

    @Value("${audit.retention-days:180}")
    private int retentionDays;

    @Scheduled(cron = "${audit.purge.cron:0 0 3 * * ?}")
    public void purgeExpired() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(1, retentionDays));
        int batchSize = 500;
        while (true) {
            Specification<UserLog> spec = (root, cq, cb) -> cb.lessThan(root.get("createTime"), cutoff);
            Page<UserLog> page = userLogRepository.findAll(spec, PageRequest.of(0, batchSize, Sort.by(Sort.Direction.ASC, "createTime")));
            if (page.isEmpty()) break;
            try {
                userLogRepository.deleteAllInBatch(page.getContent());
            } catch (Exception e) {
                // 降级删除，避免批删除不支持时失败
                for (UserLog log : page.getContent()) {
                    try { userLogRepository.delete(log); } catch (Exception ignore) {}
                }
            }
            if (page.getNumberOfElements() < batchSize) break;
        }
    }
}

