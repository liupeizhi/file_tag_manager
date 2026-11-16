package com.filemanager.repository;

import com.filemanager.entity.UserTheme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserThemeRepository extends JpaRepository<UserTheme, Long> {
}
