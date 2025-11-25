package com.filemanager.repository;

import com.filemanager.entity.UserLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public interface UserLogRepository extends JpaRepository<UserLog, Long>, JpaSpecificationExecutor<UserLog> {

    List<UserLog> findByUser_IdOrderByCreateTimeDesc(Long userId, Pageable pageable);
}
