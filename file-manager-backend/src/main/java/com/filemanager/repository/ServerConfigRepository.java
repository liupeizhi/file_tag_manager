package com.filemanager.repository;

import com.filemanager.entity.ServerConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ServerConfigRepository extends JpaRepository<ServerConfig, Long> {
    List<ServerConfig> findByUserId(Long userId);
}