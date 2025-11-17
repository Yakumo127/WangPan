package com.filemanager.repository;

import com.filemanager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByUsername(String username);
    
    Optional<User> findByEmail(String email);
    
    boolean existsByUsername(String username);
    
    boolean existsByEmail(String email);
    
    List<User> findByUsernameContainingOrEmailContaining(String username, String email);

    // 原子扣减：仅当 quota_used + :delta <= quota_limit 时累加，返回影响行数
    @Modifying
    @Query("update User u set u.quotaUsed = u.quotaUsed + :delta where u.id = :userId and (u.quotaUsed + :delta) <= u.quotaLimit")
    int tryUseQuota(@Param("userId") Long userId, @Param("delta") long delta);

    // 兜底释放：将 quota_used 减少 delta，最小为 0
    @Modifying
    @Query("update User u set u.quotaUsed = (case when (u.quotaUsed - :delta) < 0 then 0 else (u.quotaUsed - :delta) end) where u.id = :userId")
    int releaseQuotaAmount(@Param("userId") Long userId, @Param("delta") long delta);
}
