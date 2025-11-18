package xyz.firestige.deploy.domain.stage;

import java.util.Objects;

/**
 * Stage 进度值对象（Value Object）
 * <p>
 * 职责：
 * 1. 封装 currentStageIndex 和 totalStages
 * 2. 提供进度计算和判断方法
 * 3. 不可变对象，线程安全
 * <p>
 * DDD 原则：值对象通过值相等性判断，不依赖标识符
 *
 * @since Phase 18 - RF-13
 */
public final class StageProgress {

    private final int currentStageIndex;
    private final int totalStages;

    private StageProgress(int currentStageIndex, int totalStages) {
        if (totalStages <= 0) {
            throw new IllegalArgumentException("totalStages 必须大于 0");
        }
        if (currentStageIndex < 0) {
            throw new IllegalArgumentException("currentStageIndex 不能为负数");
        }
        if (currentStageIndex > totalStages) {
            throw new IllegalArgumentException(
                String.format("currentStageIndex (%d) 不能大于 totalStages (%d)", 
                    currentStageIndex, totalStages)
            );
        }
        this.currentStageIndex = currentStageIndex;
        this.totalStages = totalStages;
    }

    // ============================================
    // 工厂方法
    // ============================================

    /**
     * 创建新的 StageProgress（初始状态）
     *
     * @param totalStages Stage 总数
     * @return StageProgress 实例
     */
    public static StageProgress initial(int totalStages) {
        return new StageProgress(0, totalStages);
    }

    /**
     * 创建 StageProgress（指定当前索引）
     *
     * @param currentStageIndex 当前 Stage 索引
     * @param totalStages Stage 总数
     * @return StageProgress 实例
     */
    public static StageProgress of(int currentStageIndex, int totalStages) {
        return new StageProgress(currentStageIndex, totalStages);
    }

    // ============================================
    // 业务方法
    // ============================================

    /**
     * 推进到下一个 Stage
     *
     * @return 新的 StageProgress（不可变）
     */
    public StageProgress advance() {
        if (isCompleted()) {
            throw new IllegalStateException("已完成所有 Stage，无法继续推进");
        }
        return new StageProgress(currentStageIndex + 1, totalStages);
    }

    /**
     * 重置到初始状态（用于重试场景）
     *
     * @return 新的 StageProgress（currentStageIndex = 0）
     */
    public StageProgress reset() {
        return new StageProgress(0, totalStages);
    }

    /**
     * 判断是否所有 Stage 完成
     *
     * @return true = 已完成，false = 未完成
     */
    public boolean isCompleted() {
        return currentStageIndex >= totalStages;
    }

    /**
     * 计算进度百分比
     *
     * @return 0.0 ~ 1.0
     */
    public double getProgressPercentage() {
        if (totalStages == 0) {
            return 0.0;
        }
        return (double) currentStageIndex / totalStages;
    }

    /**
     * 获取剩余 Stage 数量
     *
     * @return 剩余数量
     */
    public int getRemainingStages() {
        return Math.max(0, totalStages - currentStageIndex);
    }

    // ============================================
    // Getter 方法
    // ============================================

    public int getCurrentStageIndex() {
        return currentStageIndex;
    }

    public int getTotalStages() {
        return totalStages;
    }

    // ============================================
    // equals / hashCode / toString
    // ============================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StageProgress that = (StageProgress) o;
        return currentStageIndex == that.currentStageIndex &&
               totalStages == that.totalStages;
    }

    @Override
    public int hashCode() {
        return Objects.hash(currentStageIndex, totalStages);
    }

    @Override
    public String toString() {
        return String.format("StageProgress[%d/%d, %.1f%%]", 
            currentStageIndex, totalStages, getProgressPercentage() * 100);
    }
}
