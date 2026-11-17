package com.filemanager.service;

import com.filemanager.dto.AccessHistoryDTO;
import com.filemanager.dto.AccessHistoryRequest;
import com.filemanager.entity.AccessHistory;
import com.filemanager.entity.User;
import com.filemanager.exception.AccessHistoryException;
import com.filemanager.repository.AccessHistoryRepository;
import com.filemanager.repository.ServerConfigRepository;
import com.filemanager.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AccessHistoryService {

    /** 每个用户保留的最近记录条数 */
    public static final int MAX_ENTRIES = 100;

    @Autowired
    private AccessHistoryRepository historyRepository;

    @Autowired
    private ServerConfigRepository serverConfigRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * 记录一次访问。
     * 同一 (服务器, 路径) 只保留一条, 重复访问仅把时间刷新到最新,
     * 因此历史里不会出现同一个目录刷屏。
     */
    @Transactional
    public AccessHistoryDTO record(AccessHistoryRequest request) {
        if (request == null || request.getServerId() == null) {
            throw new AccessHistoryException("serverId 不能为空");
        }
        String path = request.getPath();
        if (path == null || path.isEmpty()) {
            throw new AccessHistoryException("path 不能为空");
        }

        Long userId = currentUserId();
        boolean isDirectory = Boolean.TRUE.equals(request.getIsDirectory());

        AccessHistory history = historyRepository
                .findByUserIdAndServerIdAndPath(userId, request.getServerId(), path)
                .orElseGet(AccessHistory::new);

        history.setUserId(userId);
        history.setServerId(request.getServerId());
        history.setPath(path);
        history.setName(resolveName(request.getName(), path));
        history.setIsDirectory(isDirectory);
        // 显式设置时间: 仅靠 @PreUpdate 不保险 —— 重复访问同一路径时
        // 其余字段都没变, Hibernate 判定为无脏字段, 不会发 UPDATE,
        // 时间也就不会刷新, 历史排序会错乱。
        history.setAccessedAt(LocalDateTime.now());

        AccessHistory saved = historyRepository.save(history);
        trim(userId);
        return toDTO(saved);
    }

    public List<AccessHistoryDTO> listRecent() {
        Long userId = currentUserId();
        List<AccessHistory> items = historyRepository.findByUserIdOrderByAccessedAtDesc(
                userId, PageRequest.of(0, MAX_ENTRIES));

        // 服务器名批量查, 避免逐条回表; 服务器被删时回退为空
        Map<Long, String> serverNames = new HashMap<>();
        serverConfigRepository.findAll().forEach(s -> serverNames.put(s.getId(), s.getName()));

        return items.stream()
                .map(h -> toDTO(h, serverNames))
                .collect(Collectors.toList());
    }

    @Transactional
    public void remove(Long id) {
        Long userId = currentUserId();
        historyRepository.findById(id).ifPresent(h -> {
            if (userId.equals(h.getUserId())) {
                historyRepository.delete(h);
            }
        });
    }

    @Transactional
    public void clear() {
        historyRepository.deleteByUserId(currentUserId());
    }

    /** 超出上限时删掉最旧的记录 */
    private void trim(Long userId) {
        List<AccessHistory> all = historyRepository.findByUserIdOrderByAccessedAtDesc(userId);
        if (all.size() > MAX_ENTRIES) {
            historyRepository.deleteAll(all.subList(MAX_ENTRIES, all.size()));
        }
    }

    private AccessHistoryDTO toDTO(AccessHistory history) {
        Map<Long, String> serverNames = new HashMap<>();
        serverConfigRepository.findAll().forEach(s -> serverNames.put(s.getId(), s.getName()));
        return toDTO(history, serverNames);
    }

    private AccessHistoryDTO toDTO(AccessHistory history, Map<Long, String> serverNames) {
        AccessHistoryDTO dto = new AccessHistoryDTO();
        dto.setId(history.getId());
        dto.setServerId(history.getServerId());
        dto.setServerName(serverNames.get(history.getServerId()));
        dto.setPath(history.getPath());
        dto.setName(history.getName());
        dto.setIsDirectory(history.getIsDirectory());
        dto.setAccessedAt(history.getAccessedAt());
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
            throw new AccessHistoryException("未登录");
        }
        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new AccessHistoryException("用户不存在"));
        return user.getId();
    }
}
