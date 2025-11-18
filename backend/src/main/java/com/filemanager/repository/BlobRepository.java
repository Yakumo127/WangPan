package com.filemanager.repository;

import com.filemanager.entity.Blob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BlobRepository extends JpaRepository<Blob, String> {

    // 找出未被任何版本引用的 Blob
    @Query("select b from Blob b where not exists (select 1 from FileVersion v where v.blobHash = b.hash)")
    List<Blob> findUnreferenced();
}
