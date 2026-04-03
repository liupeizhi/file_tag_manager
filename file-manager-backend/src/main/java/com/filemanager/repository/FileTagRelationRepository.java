package com.filemanager.repository;

import com.filemanager.entity.FileTagRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileTagRelationRepository extends JpaRepository<FileTagRelation, Long> {
    List<FileTagRelation> findByFilePathAndServerId(String filePath, Long serverId);
    List<FileTagRelation> findByTagId(Long tagId);
    Optional<FileTagRelation> findByFilePathAndServerIdAndTagId(String filePath, Long serverId, Long tagId);
    void deleteByFilePathAndServerId(String filePath, Long serverId);
    void deleteByTagId(Long tagId);
    void deleteByFileId(Long fileId);
    List<FileTagRelation> findByFilePathStartingWithAndServerId(String pathPrefix, Long serverId);
    void deleteByFilePathStartingWithAndServerId(String pathPrefix, Long serverId);
}