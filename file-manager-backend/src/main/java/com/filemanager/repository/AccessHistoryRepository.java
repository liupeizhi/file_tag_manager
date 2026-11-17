package com.filemanager.repository;

import com.filemanager.entity.AccessHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccessHistoryRepository extends JpaRepository<AccessHistory, Long> {

    Optional<AccessHistory> findByUserIdAndServerIdAndPath(Long userId, Long serverId, String path);

    List<AccessHistory> findByUserIdOrderByAccessedAtDesc(Long userId, Pageable pageable);

    List<AccessHistory> findByUserIdOrderByAccessedAtDesc(Long userId);

    void deleteByUserId(Long userId);
}
