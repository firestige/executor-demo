package xyz.firestige.executor.domain.shared.vo;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * 时间范围值对象（Value Object）
 * <p>
 * 职责：
 * 1. 封装 createdAt, startedAt, endedAt 三个时间点
 * 2. 提供时间范围计算方法
 * 3. 不可变对象，线程安全
 *
 * @since Phase 17 - RF-08
 */
public final class TimeRange {

    private final LocalDateTime createdAt;
    private final LocalDateTime startedAt;
    private final LocalDateTime endedAt;

    private TimeRange(LocalDateTime createdAt, LocalDateTime startedAt, LocalDateTime endedAt) {
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
    }

    // ============================================
    // 工厂方法
    // ============================================

    /**
     * 创建未开始的时间范围（只有 createdAt）
     */
    public static TimeRange notStarted() {
        return new TimeRange(LocalDateTime.now(), null, null);
    }

    /**
     * 创建完整的时间范围
     */
    public static TimeRange of(LocalDateTime createdAt, LocalDateTime startedAt, LocalDateTime endedAt) {
        return new TimeRange(createdAt, startedAt, endedAt);
    }

    // ============================================
    // 业务方法（不可变，返回新对象）
    // ============================================

    /**
     * 开始执行（设置 startedAt）
     */
    public TimeRange start() {
        return new TimeRange(createdAt, LocalDateTime.now(), endedAt);
    }

    /**
     * 结束执行（设置 endedAt）
     */
    public TimeRange end() {
        return new TimeRange(createdAt, startedAt, LocalDateTime.now());
    }

    /**
     * 重置结束时间（用于重试场景）
     */
    public TimeRange resetEnd() {
        return new TimeRange(createdAt, startedAt, null);
    }

    /**
     * 计算持续时长
     */
    public Duration getDuration() {
        if (startedAt == null || endedAt == null) {
            return null;
        }
        return Duration.between(startedAt, endedAt);
    }

    /**
     * 计算从创建到开始的等待时长
     */
    public Duration getWaitDuration() {
        if (startedAt == null) {
            return null;
        }
        return Duration.between(createdAt, startedAt);
    }

    // ============================================
    // Getter 方法
    // ============================================

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    // ============================================
    // equals / hashCode / toString
    // ============================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TimeRange timeRange = (TimeRange) o;
        return Objects.equals(createdAt, timeRange.createdAt) &&
               Objects.equals(startedAt, timeRange.startedAt) &&
               Objects.equals(endedAt, timeRange.endedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(createdAt, startedAt, endedAt);
    }

    @Override
    public String toString() {
        return String.format("TimeRange[created=%s, started=%s, ended=%s]",
            createdAt, startedAt, endedAt);
    }
}
