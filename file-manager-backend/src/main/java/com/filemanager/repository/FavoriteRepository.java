package com.filemanager.repository;

import com.filemanager.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    List<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Favorite> findByUserIdAndServerIdAndPath(Long userId, Long serverId, String path);

    void deleteByUserId(Long userId);

    void deleteByServerId(Long serverId);
}
