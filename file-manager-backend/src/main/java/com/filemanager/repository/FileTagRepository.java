package com.filemanager.repository;

import com.filemanager.entity.FileTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FileTagRepository extends JpaRepository<FileTag, Long> {
    List<FileTag> findByParentIdIsNullOrderBySortOrderAsc();
    List<FileTag> findByParentIdOrderBySortOrderAsc(Long parentId);
    List<FileTag> findAllByOrderBySortOrderAsc();
    List<FileTag> findByGroupIdOrderBySortOrderAsc(Long groupId);
    List<FileTag> findByGroupIdAndParentIdIsNullOrderBySortOrderAsc(Long groupId);
}