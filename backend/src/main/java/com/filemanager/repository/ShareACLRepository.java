package com.filemanager.repository;

import com.filemanager.entity.Share;
import com.filemanager.entity.ShareACL;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShareACLRepository extends JpaRepository<ShareACL, Long> {

    List<ShareACL> findByShare(Share share);

    @Query("SELECT a FROM ShareACL a WHERE a.share = :share AND a.principalType = :type AND a.principalValue = :value")
    Optional<ShareACL> findByShareAndPrincipal(@Param("share") Share share,
                                               @Param("type") ShareACL.PrincipalType type,
                                               @Param("value") String value);
}
