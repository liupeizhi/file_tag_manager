package com.filemanager.repository;

import com.filemanager.entity.TagGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TagGroupRepository extends JpaRepository<TagGroup, Long> {
    List<TagGroup> findAllByOrderBySortOrderAsc();
}