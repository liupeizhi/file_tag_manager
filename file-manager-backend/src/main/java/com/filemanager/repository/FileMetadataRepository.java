package com.filemanager.repository;

import com.filemanager.entity.FileMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long>, 
                                                JpaSpecificationExecutor<FileMetadata> {
    List<FileMetadata> findByServerIdAndPath(Long serverId, String path);
    Optional<FileMetadata> findByServerIdAndPathAndName(Long serverId, String path, String name);
    void deleteByServerId(Long serverId);
    Page<FileMetadata> findByServerIdAndIsDirectoryFalse(Long serverId, Pageable pageable);
}