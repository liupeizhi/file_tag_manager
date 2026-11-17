package com.filemanager.service;

import com.filemanager.dto.FavoriteDTO;
import com.filemanager.dto.FavoriteRequest;
import com.filemanager.dto.FavoriteToggleResult;
import com.filemanager.entity.Favorite;
import com.filemanager.entity.User;
import com.filemanager.exception.FavoriteException;
import com.filemanager.repository.FavoriteRepository;
import com.filemanager.repository.ServerConfigRepository;
import com.filemanager.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FavoriteService {

    @Autowired
    private FavoriteRepository favoriteRepository;

    @Autowired
    private ServerConfigRepository serverConfigRepository;

    @Autowired
    private UserRepository userRepository;

    public List<FavoriteDTO> list() {
        Long userId = currentUserId();
        Map<Long, String> serverNames = serverNames();
        return favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(f -> toDTO(f, serverNames))
                .collect(Collectors.toList());
    }

    /**
     * 切换收藏状态: 已收藏则取消, 未收藏则添加。
     */
    @Transactional
    public FavoriteToggleResult toggle(FavoriteRequest request) {
        if (request == null || request.getServerId() == null) {
            throw new FavoriteException("serverId 不能为空");
        }
        String path = request.getPath();
        if (path == null || path.isEmpty()) {
            throw new FavoriteException("path 不能为空");
        }

        Long userId = currentUserId();
        var existing = favoriteRepository
                .findByUserIdAndServerIdAndPath(userId, request.getServerId(), path);

        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
            return FavoriteToggleResult.off();
        }

        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setServerId(request.getServerId());
        favorite.setPath(path);
        favorite.setName(resolveName(request.getName(), path));
        favorite.setIsDirectory(Boolean.TRUE.equals(request.getIsDirectory()));

        Favorite saved = favoriteRepository.save(favorite);
        return FavoriteToggleResult.on(toDTO(saved, serverNames()));
    }

    @Transactional
    public void remove(Long id) {
        Long userId = currentUserId();
        favoriteRepository.findById(id).ifPresent(f -> {
            if (userId.equals(f.getUserId())) {
                favoriteRepository.delete(f);
            }
        });
    }

    @Transactional
    public void clear() {
        favoriteRepository.deleteByUserId(currentUserId());
    }

    private Map<Long, String> serverNames() {
        Map<Long, String> map = new HashMap<>();
        serverConfigRepository.findAll().forEach(s -> map.put(s.getId(), s.getName()));
        return map;
    }

    private FavoriteDTO toDTO(Favorite favorite, Map<Long, String> serverNames) {
        FavoriteDTO dto = new FavoriteDTO();
        dto.setId(favorite.getId());
        dto.setServerId(favorite.getServerId());
        dto.setServerName(serverNames.get(favorite.getServerId()));
        dto.setPath(favorite.getPath());
        dto.setName(favorite.getName());
        dto.setIsDirectory(favorite.getIsDirectory());
        dto.setCreatedAt(favorite.getCreatedAt());
        return dto;
    }

    private String resolveName(String name, String path) {
        if (name != null && !name.isEmpty()) {
            return name;
        }
        String normalized = path.endsWith("/") && path.length() > 1
                ? path.substring(0, path.length() - 1) : path;
        int idx = normalized.lastIndexOf('/');
        String leaf = idx >= 0 ? normalized.substring(idx + 1) : normalized;
        return leaf.isEmpty() ? "/" : leaf;
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new FavoriteException("未登录");
        }
        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new FavoriteException("用户不存在"));
        return user.getId();
    }
}
