package xyz.firestige.executor.support.conflict;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 冲突注册表：确保同一租户不会并发执行多个任务。
 */
public class ConflictRegistry {

    private static class Entry {
        final String taskId;
        volatile Instant registeredAt = Instant.now();
        Entry(String taskId) { this.taskId = taskId; }
    }

    // tenantId -> Entry
    private final Map<String, Entry> running = new ConcurrentHashMap<>();

    /**
     * 注册租户，如果已在运行则返回 false。
     */
    public boolean register(String tenantId, String taskId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(taskId, "taskId");
        return running.putIfAbsent(tenantId, new Entry(taskId)) == null;
    }

    /** 是否在运行中 */
    public boolean isRunning(String tenantId) {
        return running.containsKey(tenantId);
    }

    /** 检查租户是否存在冲突（RF-12 新增） */
    public boolean hasConflict(String tenantId) {
        return running.containsKey(tenantId);
    }

    /** 返回当前租户对应的任务ID，若无则 null */
    public String getRunningTaskId(String tenantId) {
        Entry e = running.get(tenantId);
        return e == null ? null : e.taskId;
    }

    /** 释放租户占用 */
    public void release(String tenantId) {
        running.remove(tenantId);
    }

    /**
     * 兜底扫描：根据外部策略决定是否释放“疑似泄漏”的占用。
     * 此处仅更新时间戳或留空实现，具体策略后续接入。
     */
    public void scanAndReleaseLeaked() {
        // 预留：可以检查 taskId 是否仍然存在或根据时间阈值释放
    }
}

