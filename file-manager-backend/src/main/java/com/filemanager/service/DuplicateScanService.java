package com.filemanager.service;

import com.filemanager.dto.DuplicateCompareRequest;
import com.filemanager.dto.DuplicateGroupDTO;
import com.filemanager.dto.DuplicateGroupPageDTO;
import com.filemanager.dto.DuplicateMemberDTO;
import com.filemanager.dto.DuplicateSummaryDTO;
import com.filemanager.dto.DuplicateTaskDTO;
import com.filemanager.dto.FileResource;
import com.filemanager.entity.ServerConfig;
import com.filemanager.repository.ServerConfigRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 目录重复文件检测（只读）。
 *
 * 流程：
 *   1. 递归列出左右两个目录（跳过隐藏/系统文件与 0 字节文件）
 *   2. 按文件大小分桶，只保留 ≥2 个成员的桶 —— 大小不同不可能重复
 *   3. 桶内计算指纹：≤8MB 全量哈希，否则头尾各 4MB 采样哈希（L2）
 *   4. 指纹相同的 ≥2 个文件构成一个「重复组」
 *
 * 系统只出报告，不删除、不复制任何文件。
 */
@Slf4j
@Service
public class DuplicateScanService {

    /** L2 采样长度：头尾各 4MB */
    private static final long SAMPLE_BYTES = 4L * 1024 * 1024;

    /** 小于等于这个大小的文件直接全量哈希，采样没有意义 */
    private static final long FULL_HASH_LIMIT = 8L * 1024 * 1024;

    private static final int READ_BUFFER = 64 * 1024;

    /** 单次任务最多扫描的文件数，避免误选巨目录把内存打满 */
    private static final int MAX_FILES = 200_000;

    /** 内存里最多保留多少个任务 */
    private static final int MAX_TASKS = 12;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private FileService fileService;

    @Autowired
    private ServerConfigRepository serverConfigRepository;

    private final ExecutorService coordinator = Executors.newFixedThreadPool(2);
    private final ExecutorService hashPool = Executors.newFixedThreadPool(4);
    private final Map<String, DuplicateTask> tasks = new ConcurrentHashMap<>();

    @PreDestroy
    public void shutdown() {
        coordinator.shutdownNow();
        hashPool.shutdownNow();
    }

    /* ==================== 对外接口 ==================== */

    public String createTask(DuplicateCompareRequest request) {
        if (request == null
                || request.getLeftServerId() == null || request.getRightServerId() == null
                || request.getLeftPath() == null || request.getRightPath() == null) {
            throw new IllegalArgumentException("请选择左右两侧的服务器与目录");
        }

        String leftPath = normalize(request.getLeftPath());
        String rightPath = normalize(request.getRightPath());
        request.setLeftPath(leftPath);
        request.setRightPath(rightPath);

        if (request.getLeftServerId().equals(request.getRightServerId())) {
            // 同一台服务器上路径不区分大小写(macOS / Windows 的常见情况),
            // 所以 "/Photos" 和 "/photos" 必须当成同一个目录
            if (leftPath.equalsIgnoreCase(rightPath)) {
                throw new IllegalArgumentException("左右两侧是同一个目录，无法比较");
            }
            if (isUnder(leftPath, rightPath) || isUnder(rightPath, leftPath)) {
                throw new IllegalArgumentException("两个目录存在包含关系（一个是另一个的子目录），会把同一批文件当成重复");
            }
        }

        ServerConfig leftServer = serverConfigRepository.findById(request.getLeftServerId())
                .orElseThrow(() -> new IllegalArgumentException("左侧服务器不存在"));
        ServerConfig rightServer = serverConfigRepository.findById(request.getRightServerId())
                .orElseThrow(() -> new IllegalArgumentException("右侧服务器不存在"));

        DuplicateTask task = new DuplicateTask(request);
        task.leftLabel = leftServer.getName() + " · " + leftPath;
        task.rightLabel = rightServer.getName() + " · " + rightPath;

        tasks.put(task.id, task);
        evictOldTasks();

        coordinator.submit(() -> run(task));
        return task.id;
    }

    public DuplicateTaskDTO getTask(String taskId) {
        return toDTO(requireTask(taskId));
    }

    public DuplicateGroupPageDTO listGroups(String taskId, int page, int size,
                                            Long minSize, String verified) {
        DuplicateTask task = requireTask(taskId);
        List<DuplicateGroupDTO> all = task.groups;

        List<DuplicateGroupDTO> filtered = all.stream()
                .filter(g -> minSize == null || minSize <= 0 || g.getSize() >= minSize)
                .filter(g -> {
                    if ("yes".equalsIgnoreCase(verified)) return Boolean.TRUE.equals(g.getVerified());
                    if ("no".equalsIgnoreCase(verified)) return !Boolean.TRUE.equals(g.getVerified());
                    return true;
                })
                .collect(Collectors.toList());

        int pageSize = Math.max(1, Math.min(200, size));
        int pageIndex = Math.max(0, page);
        int from = Math.min(filtered.size(), pageIndex * pageSize);
        int to = Math.min(filtered.size(), from + pageSize);

        DuplicateGroupPageDTO dto = new DuplicateGroupPageDTO();
        dto.setTotal(filtered.size());
        dto.setPage(pageIndex);
        dto.setSize(pageSize);
        dto.setItems(new ArrayList<>(filtered.subList(from, to)));
        return dto;
    }

    /**
     * 用全量哈希复核一组。
     * 若发现内容其实不同（L2 误判），把这一组从结果里移除。
     */
    public DuplicateGroupDTO verifyGroup(String taskId, String groupId) {
        DuplicateTask task = requireTask(taskId);
        if (!"DONE".equals(task.status)) {
            throw new IllegalArgumentException("比较尚未完成");
        }

        DuplicateGroupDTO target = task.groups.stream()
                .filter(g -> g.getId().equals(groupId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("该重复组已不存在"));

        try {
            Set<String> digests = new HashSet<>();
            for (DuplicateMemberDTO member : target.getMembers()) {
                digests.add(fullDigest(member.getServerId(), member.getPath()));
            }
            if (digests.size() == 1) {
                target.setVerified(true);
                target.setMismatch(false);
                target.setMessage("已用全量哈希确认内容一致");
                task.summary = summarize(task, task.groups);
                return target;
            }

            // 误判：移出结果
            target.setVerified(false);
            target.setMismatch(true);
            target.setMessage("复核后发现内容并不相同，已从重复列表中移除");
            List<DuplicateGroupDTO> remaining = task.groups.stream()
                    .filter(g -> !g.getId().equals(groupId))
                    .collect(Collectors.toList());
            task.groups = remaining;
            task.summary = summarize(task, remaining);
            return target;
        } catch (Exception e) {
            throw new IllegalArgumentException("复核失败：" + e.getMessage());
        }
    }

    public void cancel(String taskId) {
        DuplicateTask task = requireTask(taskId);
        if ("RUNNING".equals(task.status)) {
            task.cancelled = true;
            task.message = "正在取消…";
        }
    }

    /** 导出 CSV（带 UTF-8 BOM，Excel 打开不乱码） */
    public byte[] exportCsv(String taskId) {
        DuplicateTask task = requireTask(taskId);
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append("组号,复核状态,是否跨边,文件大小(字节),可回收(字节),侧,相对路径,完整路径,修改时间\n");
        for (DuplicateGroupDTO g : task.groups) {
            for (DuplicateMemberDTO m : g.getMembers()) {
                sb.append(csv(g.getId())).append(',')
                        .append(csv(Boolean.TRUE.equals(g.getVerified()) ? "已确认" : "未确认")).append(',')
                        .append(csv(Boolean.TRUE.equals(g.getCross()) ? "跨边" : "同边")).append(',')
                        .append(g.getSize()).append(',')
                        .append(g.getReclaimableBytes()).append(',')
                        .append(csv("LEFT".equals(m.getSide()) ? "左" : "右")).append(',')
                        .append(csv(m.getRelativePath())).append(',')
                        .append(csv(m.getPath())).append(',')
                        .append(csv(m.getLastModified())).append('\n');
            }
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /* ==================== 任务执行 ==================== */

    private void run(DuplicateTask task) {
        try {
            List<Item> left = scan(task, "LEFT", task.request.getLeftServerId(), task.request.getLeftPath(), task.scannedLeft);
            if (task.cancelled) { finishCancelled(task); return; }
            task.phase = "SCAN_RIGHT";

            List<Item> right = scan(task, "RIGHT", task.request.getRightServerId(), task.request.getRightPath(), task.scannedRight);
            if (task.cancelled) { finishCancelled(task); return; }

            if (left.isEmpty() && right.isEmpty()) {
                task.phase = "DONE";
                task.groups = List.of();
                task.summary = summarize(task, task.groups);
                finish(task, "DONE", "两侧目录都是空的");
                return;
            }

            task.phase = "HASHING";
            List<Item> candidates = pickCandidates(left, right);
            task.candidateCount = candidates.size();
            boolean strict = Boolean.TRUE.equals(task.request.getStrict());
            task.totalBytes = candidates.stream().mapToLong(i -> bytesToRead(i.size, strict)).sum();

            hashCandidates(task, candidates);
            if (task.cancelled) { finishCancelled(task); return; }

            task.phase = "GROUPING";
            List<DuplicateGroupDTO> groups = buildGroups(candidates);
            task.groups = groups;
            task.summary = summarize(task, groups);
            finish(task, "DONE", groups.isEmpty() ? "两个目录之间没有相同的文件" : "");
        } catch (Exception e) {
            log.warn("重复文件检测失败: {}", e.getMessage());
            task.status = "FAILED";
            task.message = e.getMessage() == null ? "比较失败" : e.getMessage();
            task.finishedAt = LocalDateTime.now().format(TIME_FMT);
        }
    }

    private void finishCancelled(DuplicateTask task) {
        task.status = "CANCELLED";
        task.message = "已取消";
        task.finishedAt = LocalDateTime.now().format(TIME_FMT);
    }

    private void finish(DuplicateTask task, String status, String message) {
        task.status = status;
        if ("DONE".equals(status)) {
            task.phase = "DONE";
            task.percent = 100;
        }
        if (message != null && !message.isEmpty()) task.message = message;
        task.finishedAt = LocalDateTime.now().format(TIME_FMT);
        evictOldTasks();
    }

    /** 递归扫一个目录，返回所有文件条目 */
    private List<Item> scan(DuplicateTask task, String side, Long serverId, String rootPath, AtomicInteger counter) {
        List<Item> items = new ArrayList<>();
        Deque<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(rootPath);

        boolean ignoreHidden = !Boolean.FALSE.equals(task.request.getIgnoreHidden());
        long minSize = task.request.getMinSize() != null ? Math.max(0, task.request.getMinSize()) : 0;

        while (!queue.isEmpty()) {
            if (task.cancelled) return items;
            String dir = queue.poll();
            if (!visited.add(dir)) continue;

            List<FileResource> entries;
            try {
                entries = fileService.listResources(serverId, dir);
            } catch (Exception e) {
                task.warnings.incrementAndGet();
                if (dir.equals(rootPath)) {
                    throw new IllegalStateException("无法读取目录：" + rootPath + "（" + e.getMessage() + "）");
                }
                continue;
            }

            for (FileResource resource : entries) {
                if (task.cancelled) return items;
                String path = normalize(resource.getPath());
                if (path.equals(dir)) continue;

                if (resource.isDirectory()) {
                    queue.add(path);
                    continue;
                }
                long size = resource.getSize();
                if (size <= 0) continue;
                if (size < minSize) continue;
                if (ignoreHidden && isJunk(resource.getName())) continue;
                if (items.size() + counter.get() >= MAX_FILES) {
                    throw new IllegalStateException("文件数量超过 " + MAX_FILES + "，请选择更小的目录范围");
                }

                Item item = new Item();
                item.side = side;
                item.serverId = serverId;
                item.path = path;
                item.name = nfc(resource.getName());
                item.relativePath = nfc(relativePath(rootPath, path));
                item.size = size;
                item.lastModified = resource.getLastModified();
                items.add(item);
                counter.incrementAndGet();
            }
        }
        return items;
    }

    /**
     * 按大小分桶, 只保留「左右两边都有」的桶。
     * 只比较两边之间, 单边目录内部的重复不参与判断 —— 这样候选数更少,
     * 也不会把"自己盘里下重了"报成两边的重复。
     */
    private List<Item> pickCandidates(List<Item> left, List<Item> right) {
        Map<Long, List<Item>> buckets = new HashMap<>();
        for (Item item : left) buckets.computeIfAbsent(item.size, k -> new ArrayList<>()).add(item);
        for (Item item : right) buckets.computeIfAbsent(item.size, k -> new ArrayList<>()).add(item);

        List<Item> candidates = new ArrayList<>();
        for (List<Item> bucket : buckets.values()) {
            boolean hasLeft = bucket.stream().anyMatch(i -> "LEFT".equals(i.side));
            boolean hasRight = bucket.stream().anyMatch(i -> "RIGHT".equals(i.side));
            if (hasLeft && hasRight) candidates.addAll(bucket);
        }
        // 大文件先算：用户更关心大文件能省多少空间
        candidates.sort(Comparator.comparingLong((Item i) -> i.size).reversed());
        return candidates;
    }

    private void hashCandidates(DuplicateTask task, List<Item> candidates) {
        if (candidates.isEmpty()) return;
        boolean strict = Boolean.TRUE.equals(task.request.getStrict());
        int threads = Math.min(4, candidates.size());
        AtomicInteger cursor = new AtomicInteger();

        List<Callable<Void>> workers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            workers.add(() -> {
                while (!task.cancelled) {
                    int pos = cursor.getAndIncrement();
                    if (pos >= candidates.size()) break;
                    Item item = candidates.get(pos);
                    try {
                        item.fingerprint = fingerprint(task, item, strict);
                    } catch (Exception e) {
                        task.warnings.incrementAndGet();
                    } finally {
                        task.hashed.incrementAndGet();
                    }
                }
                return null;
            });
        }
        try {
            hashPool.invokeAll(workers);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * L2 指纹：头 4MB + 尾 4MB；≤8MB 或严格模式时全量。
     * 摘要里带上模式和大小，避免不同模式/不同大小产生同一个 id。
     */
    private String fingerprint(DuplicateTask task, Item item, boolean strict) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        boolean full = strict || item.size <= FULL_HASH_LIMIT;
        md.update((full ? "L3:" : "L2:").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        md.update(Long.toString(item.size).getBytes(java.nio.charset.StandardCharsets.UTF_8));

        if (full) {
            try (InputStream in = fileService.downloadFile(item.serverId, item.path)) {
                pump(task, md, in, Long.MAX_VALUE);
            }
        } else {
            try (InputStream in = fileService.downloadFile(item.serverId, item.path, 0L, SAMPLE_BYTES - 1)) {
                pump(task, md, in, SAMPLE_BYTES);
            }
            try (InputStream in = fileService.downloadFile(item.serverId, item.path, item.size - SAMPLE_BYTES, item.size - 1)) {
                pump(task, md, in, SAMPLE_BYTES);
            }
        }
        return (full ? "L3-" : "L2-") + hex(md.digest());
    }

    private void pump(DuplicateTask task, MessageDigest md, InputStream in, long limit) throws IOException {
        byte[] buffer = new byte[READ_BUFFER];
        long remaining = limit;
        while (remaining > 0 && !task.cancelled) {
            int n = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (n <= 0) break;
            md.update(buffer, 0, n);
            task.readBytes.addAndGet(n);
            remaining -= n;
        }
    }

    private String fullDigest(Long serverId, String path) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = fileService.downloadFile(serverId, path)) {
            byte[] buffer = new byte[READ_BUFFER];
            int n;
            while ((n = in.read(buffer)) > 0) {
                md.update(buffer, 0, n);
            }
        }
        return hex(md.digest());
    }

    private long bytesToRead(long size, boolean strict) {
        if (strict || size <= FULL_HASH_LIMIT) return size;
        return Math.min(size, SAMPLE_BYTES * 2);
    }

    private List<DuplicateGroupDTO> buildGroups(List<Item> candidates) {
        Map<String, List<Item>> byFingerprint = new LinkedHashMap<>();
        for (Item item : candidates) {
            if (item.fingerprint != null) {
                byFingerprint.computeIfAbsent(item.fingerprint, k -> new ArrayList<>()).add(item);
            }
        }

        List<DuplicateGroupDTO> groups = new ArrayList<>();
        for (Map.Entry<String, List<Item>> entry : byFingerprint.entrySet()) {
            List<Item> members = entry.getValue();
            if (members.size() < 2) continue;
            // 同一个桶里也可能出现"左边两份相同、右边那份不同"，这种不算跨边重复
            boolean crosses = members.stream().anyMatch(i -> "LEFT".equals(i.side))
                    && members.stream().anyMatch(i -> "RIGHT".equals(i.side));
            if (!crosses) continue;

            // 左边排前面，便于「以左为准」阅读
            members.sort(Comparator
                    .comparing((Item it) -> !"LEFT".equals(it.side))
                    .thenComparing(it -> it.relativePath));

            DuplicateGroupDTO group = new DuplicateGroupDTO();
            group.setId(entry.getKey());
            long size = members.get(0).size;
            group.setSize(size);
            group.setReclaimableBytes(size * (members.size() - 1L));
            group.setCross(true);
            group.setVerified(false);
            group.setMismatch(false);
            group.setMembers(members.stream().map(this::toMember).collect(Collectors.toList()));
            groups.add(group);
        }

        groups.sort(Comparator.comparingLong(DuplicateGroupDTO::getReclaimableBytes).reversed());
        return groups;
    }

    private DuplicateMemberDTO toMember(Item item) {
        DuplicateMemberDTO dto = new DuplicateMemberDTO();
        dto.setSide(item.side);
        dto.setRelativePath(item.relativePath);
        dto.setPath(item.path);
        dto.setName(item.name);
        dto.setSize(item.size);
        dto.setServerId(item.serverId);
        dto.setLastModified(item.lastModified != null ? item.lastModified.format(TIME_FMT) : null);
        return dto;
    }

    private DuplicateSummaryDTO summarize(DuplicateTask task, List<DuplicateGroupDTO> groups) {
        DuplicateSummaryDTO summary = new DuplicateSummaryDTO();
        int files = 0;
        long reclaimable = 0;
        int verified = 0;
        for (DuplicateGroupDTO g : groups) {
            files += g.getMembers().size();
            reclaimable += g.getReclaimableBytes();
            if (Boolean.TRUE.equals(g.getVerified())) verified++;
        }
        summary.setGroupCount(groups.size());
        summary.setFileCount(files);
        summary.setReclaimableBytes(reclaimable);
        summary.setVerifiedGroupCount(verified);
        summary.setLeftFiles(task.scannedLeft.longValue());
        summary.setRightFiles(task.scannedRight.longValue());
        summary.setDurationMs(System.currentTimeMillis() - task.startedAtMs);
        return summary;
    }

    private DuplicateTaskDTO toDTO(DuplicateTask task) {
        DuplicateTaskDTO dto = new DuplicateTaskDTO();
        dto.setId(task.id);
        dto.setStatus(task.status);
        dto.setPhase(task.phase);
        dto.setMessage(task.message);
        dto.setLeftLabel(task.leftLabel);
        dto.setRightLabel(task.rightLabel);
        dto.setScannedLeft(task.scannedLeft.get());
        dto.setScannedRight(task.scannedRight.get());
        dto.setCandidateCount(task.candidateCount);
        dto.setHashedCount(task.hashed.get());
        dto.setTotalBytes(task.totalBytes);
        dto.setReadBytes(task.readBytes.get());
        dto.setPercent(percentOf(task));
        dto.setWarningCount(task.warnings.get());
        dto.setStartedAt(task.startedAt);
        dto.setFinishedAt(task.finishedAt);
        dto.setSummary(task.summary);
        return dto;
    }

    private int percentOf(DuplicateTask task) {
        if ("DONE".equals(task.status)) return 100;
        if ("FAILED".equals(task.status) || "CANCELLED".equals(task.status)) return task.percent;
        switch (task.phase) {
            case "SCAN_LEFT": return 5;
            case "SCAN_RIGHT": return 12;
            case "HASHING": {
                if (task.candidateCount <= 0) return 20;
                int done = task.hashed.get();
                return 20 + (int) Math.round(70.0 * Math.min(done, task.candidateCount) / task.candidateCount);
            }
            case "GROUPING": return 95;
            default: return 0;
        }
    }

    /* ==================== 工具 ==================== */

    private DuplicateTask requireTask(String taskId) {
        DuplicateTask task = tasks.get(taskId);
        if (task == null) throw new IllegalArgumentException("任务不存在或已过期");
        return task;
    }

    private void evictOldTasks() {
        if (tasks.size() <= MAX_TASKS) return;
        tasks.entrySet().stream()
                .filter(e -> !"RUNNING".equals(e.getValue().status))
                .sorted(Comparator.comparingLong(e -> e.getValue().startedAtMs))
                .limit(tasks.size() - MAX_TASKS)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList())
                .forEach(tasks::remove);
    }

    private static String normalize(String path) {
        if (path == null || path.isBlank()) return "/";
        String s = path.trim().replace('\\', '/');
        if (!s.startsWith("/")) s = "/" + s;
        // 合并重复斜杠: "//a" 与 "/a" 是同一个目录
        s = s.replaceAll("/{2,}", "/");
        while (s.length() > 1 && s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return nfc(s);
    }

    private static boolean isUnder(String parent, String child) {
        String p = parent.toLowerCase(java.util.Locale.ROOT);
        String c = child.toLowerCase(java.util.Locale.ROOT);
        if ("/".equals(p)) return !"/".equals(c);
        return c.startsWith(p + "/");
    }

    private static String relativePath(String root, String path) {
        if ("/".equals(root)) return path.startsWith("/") ? path.substring(1) : path;
        String prefix = root + "/";
        return path.startsWith(prefix) ? path.substring(prefix.length()) : path;
    }

    /** 隐藏文件与各平台的系统垃圾 */
    private static boolean isJunk(String name) {
        if (name == null || name.isBlank()) return true;
        String lower = name.toLowerCase();
        if (lower.equals(".ds_store") || lower.equals("thumbs.db") || lower.equals("desktop.ini")) return true;
        return lower.startsWith(".");
    }

    private static String nfc(String s) {
        return s == null ? null : Normalizer.normalize(s, Normalizer.Form.NFC);
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String csv(String value) {
        if (value == null) return "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /* ==================== 内部数据结构 ==================== */

    private static class Item {
        String side;
        Long serverId;
        String path;
        String name;
        String relativePath;
        long size;
        LocalDateTime lastModified;
        volatile String fingerprint;
    }

    private static class DuplicateTask {
        final String id = UUID.randomUUID().toString().replace("-", "");
        final DuplicateCompareRequest request;
        final long startedAtMs = System.currentTimeMillis();
        final String startedAt = LocalDateTime.now().format(TIME_FMT);

        volatile String status = "RUNNING";
        volatile String phase = "SCAN_LEFT";
        volatile String message = "";
        volatile boolean cancelled = false;
        volatile String finishedAt;
        volatile int candidateCount = 0;
        volatile long totalBytes = 0;
        volatile int percent = 0;
        volatile List<DuplicateGroupDTO> groups = List.of();
        volatile DuplicateSummaryDTO summary;
        volatile String leftLabel = "";
        volatile String rightLabel = "";

        final AtomicInteger scannedLeft = new AtomicInteger();
        final AtomicInteger scannedRight = new AtomicInteger();
        final AtomicInteger hashed = new AtomicInteger();
        final AtomicInteger warnings = new AtomicInteger();
        final AtomicLong readBytes = new AtomicLong();

        DuplicateTask(DuplicateCompareRequest request) {
            this.request = request;
        }
    }
}
