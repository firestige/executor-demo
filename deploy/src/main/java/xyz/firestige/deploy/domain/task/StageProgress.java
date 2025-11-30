package xyz.firestige.deploy.domain.task;

import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Stage 进度值对象（Value Object）
 * <p>
 * 职责：
 * 1. 记录当前执行到哪个 Stage（currentStageIndex）
 * 2. 提供进度查询方法
 * 3. 不可变对象，线程安全
 * <p>
 * 设计理念（T-034）：
 * - 单一职责：只负责进度追踪
 * - 与 ExecutionRange 分离：执行范围由 ExecutionRange 管理
 * - 精简字段：只保存 currentStageIndex 和 stageNames
 * <p>
 * DDD 原则：值对象通过值相等性判断，不依赖标识符
 *
 * @since T-034 分离执行范围和执行进度
 */
public final class StageProgress {

    /**
     * 当前执行到哪个 Stage（核心状态）
     */
    private final int currentStageIndex;

    /**
     * Stage 名称列表（用于查询）
     */
    private final List<String> stageNames;

    private StageProgress(int currentStageIndex, List<String> stageNames) {
        if (stageNames == null || stageNames.isEmpty()) {
            throw new IllegalArgumentException("stageNames 不能为空");
        }
        if (currentStageIndex < 0) {
            throw new IllegalArgumentException("currentStageIndex 不能为负数");
        }
        this.currentStageIndex = currentStageIndex;
        this.stageNames = List.copyOf(stageNames);
    }

    // ============================================
    // 工厂方法
    // ============================================

    /**
     * 创建新的 StageProgress（初始状态）
     *
     * @param stages Stage 集合
     * @return StageProgress 实例
     */
    public static StageProgress initial(List<TaskStage> stages) {
        List<String> names = stages.stream().map(TaskStage::getName).toList();
        return new StageProgress(0, names);
    }

    /**
     * 创建 StageProgress（指定当前索引）
     *
     * @param currentStageIndex 当前 Stage 索引
     * @param stages Stage 集合
     * @return StageProgress 实例
     */
    public static StageProgress of(int currentStageIndex, List<TaskStage> stages) {
        List<String> names = stages.stream().map(TaskStage::getName).toList();
        return new StageProgress(currentStageIndex, names);
    }

    /**
     * 从检查点恢复创建 StageProgress
     * <p>
     * 用于从检查点恢复时重建进度对象（支持重启后恢复）
     *
     * @param checkpoint 检查点对象
     * @return StageProgress 实例
     * @since T-033 状态机简化
     */
    public static StageProgress of(TaskCheckpoint checkpoint) {
        if (checkpoint == null) {
            throw new IllegalArgumentException("checkpoint 不能为空");
        }
        int nextStageIndex = checkpoint.getLastCompletedStageIndex() + 1;
        List<String> allStageNames = checkpoint.getAllStageNames();
        return new StageProgress(nextStageIndex, allStageNames);
    }

    // ============================================
    // 业务方法
    // ============================================

    /**
     * 推进到下一个 Stage
     *
     * @return 新的 StageProgress（不可变）
     */
    public StageProgress next() {
        return new StageProgress(currentStageIndex + 1, stageNames);
    }

    /**
     * 重置到初始状态（用于重试场景）
     *
     * @return 新的 StageProgress（currentStageIndex = 0）
     */
    public StageProgress reset() {
        return new StageProgress(0, stageNames);
    }

    /**
     * 判断是否所有 Stage 完成
     * <p>
     * 注意：此方法已废弃，请使用 ExecutionRange.isCompleted(progress, totalStages)
     *
     * @return true = 已完成，false = 未完成
     * @deprecated 使用 ExecutionRange 判断是否完成
     */
    @Deprecated
    public boolean isCompleted() {
        return currentStageIndex >= stageNames.size();
    }

    /**
     * 计算进度百分比
     *
     * @return 0.0 ~ 1.0
     */
    public double getProgressPercentage() {
        int totalStages = stageNames.size();
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
        return Math.max(0, stageNames.size() - currentStageIndex);
    }

    // ============================================
    // Getter 方法
    // ============================================

    public int getCurrentStageIndex() {
        return currentStageIndex;
    }

    public String getCurrentStageName() {
        if (currentStageIndex >= stageNames.size()) {
            return null;
        }
        return stageNames.get(currentStageIndex);
    }

    public int getTotalStages() {
        return stageNames.size();
    }

    /**
     * 获取所有 Stage 名称列表
     * <p>
     * 用于保存到检查点，支持重启后恢复
     *
     * @return Stage 名称列表（不可变）
     * @since T-033 状态机简化
     */
    public List<String> getStageNames() {
        return Collections.unmodifiableList(stageNames);
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
               Objects.equals(stageNames, that.stageNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(currentStageIndex, stageNames);
    }

    @Override
    public String toString() {
        return String.format("StageProgress{current=%d/%d, %.1f%%}",
            currentStageIndex, stageNames.size(), getProgressPercentage() * 100);
    }
}
