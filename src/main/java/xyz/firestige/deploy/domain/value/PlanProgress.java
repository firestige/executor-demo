package xyz.firestige.deploy.domain.value;

import java.util.Objects;

/**
 * Plan 进度值对象（Value Object）
 * <p>
 * 职责：
 * 1. 封装 Plan 执行进度（0.0 ~ 1.0）
 * 2. 提供进度计算和判断方法
 * 3. 不可变对象，线程安全
 * <p>
 * 规则：progress 取值范围 [0.0, 1.0]
 *
 * @since Phase 18 - RF-13
 */
public final class PlanProgress {

    private final double value; // 0.0 ~ 1.0

    private PlanProgress(double value) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                String.format("progress 必须在 [0.0, 1.0] 范围内，当前值: %.2f", value)
            );
        }
        this.value = value;
    }

    // ============================================
    // 工厂方法
    // ============================================

    /**
     * 创建初始进度（0%）
     *
     * @return PlanProgress 实例
     */
    public static PlanProgress initial() {
        return new PlanProgress(0.0);
    }

    /**
     * 创建完成进度（100%）
     *
     * @return PlanProgress 实例
     */
    public static PlanProgress completed() {
        return new PlanProgress(1.0);
    }

    /**
     * 根据进度值创建
     *
     * @param value 进度值（0.0 ~ 1.0）
     * @return PlanProgress 实例
     */
    public static PlanProgress of(double value) {
        return new PlanProgress(value);
    }

    /**
     * 根据已完成任务数和总任务数创建
     *
     * @param completedTasks 已完成任务数
     * @param totalTasks 总任务数
     * @return PlanProgress 实例
     */
    public static PlanProgress of(int completedTasks, int totalTasks) {
        if (totalTasks <= 0) {
            return initial();
        }
        double value = (double) completedTasks / totalTasks;
        return new PlanProgress(Math.min(value, 1.0));
    }

    // ============================================
    // 业务方法
    // ============================================

    /**
     * 增加进度（返回新对象，不可变）
     *
     * @param delta 增加量（0.0 ~ 1.0）
     * @return 新的 PlanProgress
     */
    public PlanProgress increase(double delta) {
        double newValue = Math.min(this.value + delta, 1.0);
        return new PlanProgress(newValue);
    }

    /**
     * 减少进度（返回新对象，不可变）
     *
     * @param delta 减少量（0.0 ~ 1.0）
     * @return 新的 PlanProgress
     */
    public PlanProgress decrease(double delta) {
        double newValue = Math.max(this.value - delta, 0.0);
        return new PlanProgress(newValue);
    }

    /**
     * 判断是否已完成（100%）
     *
     * @return true = 已完成，false = 未完成
     */
    public boolean isCompleted() {
        return value >= 1.0;
    }

    /**
     * 转换为百分比（整数）
     *
     * @return 0 ~ 100
     */
    public int toPercentage() {
        return (int) Math.round(value * 100);
    }

    /**
     * 判断是否比另一个进度更高
     *
     * @param other 另一个进度
     * @return true = 更高，false = 更低或相等
     */
    public boolean isHigherThan(PlanProgress other) {
        return this.value > other.value;
    }

    // ============================================
    // Getter 方法
    // ============================================

    public double getValue() {
        return value;
    }

    // ============================================
    // equals / hashCode / toString
    // ============================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanProgress that = (PlanProgress) o;
        return Double.compare(that.value, value) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return String.format("PlanProgress[%.1f%%]", value * 100);
    }
}
