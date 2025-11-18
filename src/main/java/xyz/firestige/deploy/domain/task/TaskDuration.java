package xyz.firestige.deploy.domain.task;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * 任务时长值对象（Value Object）
 * <p>
 * 职责：
 * 1. 封装任务持续时长（毫秒）
 * 2. 提供时长计算和格式化方法
 * 3. 不可变对象，线程安全
 *
 * @since Phase 18 - RF-13
 */
public final class TaskDuration {

    private final Long durationMillis;

    private TaskDuration(Long durationMillis) {
        if (durationMillis != null && durationMillis < 0) {
            throw new IllegalArgumentException("durationMillis 不能为负数");
        }
        this.durationMillis = durationMillis;
    }

    // ============================================
    // 工厂方法
    // ============================================

    /**
     * 创建未开始的任务时长（null）
     *
     * @return TaskDuration 实例
     */
    public static TaskDuration notStarted() {
        return new TaskDuration(null);
    }

    /**
     * 根据毫秒数创建
     *
     * @param durationMillis 持续时长（毫秒）
     * @return TaskDuration 实例
     */
    public static TaskDuration ofMillis(Long durationMillis) {
        return new TaskDuration(durationMillis);
    }

    /**
     * 根据开始和结束时间计算时长
     *
     * @param startedAt 开始时间
     * @param endedAt 结束时间
     * @return TaskDuration 实例
     */
    public static TaskDuration between(LocalDateTime startedAt, LocalDateTime endedAt) {
        if (startedAt == null || endedAt == null) {
            return notStarted();
        }
        long millis = ChronoUnit.MILLIS.between(startedAt, endedAt);
        return new TaskDuration(millis);
    }

    // ============================================
    // 业务方法
    // ============================================

    /**
     * 转换为 Java Duration
     *
     * @return Duration 对象，如果未开始则返回 null
     */
    public Duration toDuration() {
        if (durationMillis == null) {
            return null;
        }
        return Duration.ofMillis(durationMillis);
    }

    /**
     * 转换为人类可读格式
     * <p>
     * 示例：
     * - 500ms → "500ms"
     * - 5000ms → "5.0s"
     * - 65000ms → "1m 5s"
     * - 3665000ms → "1h 1m 5s"
     *
     * @return 可读字符串
     */
    public String toHumanReadable() {
        if (durationMillis == null) {
            return "未开始";
        }

        long millis = durationMillis;
        if (millis < 1000) {
            return millis + "ms";
        }

        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%.1fs", seconds + (millis % 1000) / 1000.0);
        }
    }

    /**
     * 判断是否比另一个时长更长
     *
     * @param other 另一个时长
     * @return true = 更长，false = 更短或相等
     */
    public boolean isLongerThan(TaskDuration other) {
        if (this.durationMillis == null || other.durationMillis == null) {
            return false;
        }
        return this.durationMillis > other.durationMillis;
    }

    /**
     * 判断时长是否为空（未开始）
     *
     * @return true = 未开始，false = 已有时长
     */
    public boolean isEmpty() {
        return durationMillis == null;
    }

    // ============================================
    // Getter 方法
    // ============================================

    public Long getDurationMillis() {
        return durationMillis;
    }

    // ============================================
    // equals / hashCode / toString
    // ============================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaskDuration that = (TaskDuration) o;
        return Objects.equals(durationMillis, that.durationMillis);
    }

    @Override
    public int hashCode() {
        return Objects.hash(durationMillis);
    }

    @Override
    public String toString() {
        return "TaskDuration[" + toHumanReadable() + "]";
    }
}
