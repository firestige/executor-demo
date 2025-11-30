package xyz.firestige.deploy.domain.task;

import java.util.Objects;

/**
 * Stage 执行范围（Value Object）
 * <p>
 * 职责：
 * 1. 定义本次执行的 Stage 范围 [startIndex, endIndex)（半开区间）
 * 2. 提供范围判断方���（是否在范围内、是否是最后一个等）
 * 3. 不可变对象，线程安全
 * <p>
 * 设计理念：
 * - 与 StageProgress 分离：ExecutionRange 是静态的，StageProgress 是动态的
 * - 支持不同执行模式：正常执行、重试执行、回滚执行
 * <p>
 * 使用场景：
 * - 正常执行：[0, totalStages)
 * - 重试执行：[checkpoint+1, totalStages)
 * - 回滚执行：[0, checkpoint+2)
 *
 * @since T-034 分离执行范围和执行进度
 */
public final class ExecutionRange {

    /**
     * 执行起点索引（包含）
     */
    private final int startIndex;

    /**
     * 执行终点索引（不包含，半开区间）
     * <p>
     * null 表示执行到最后
     */
    private final Integer endIndex;

    /**
     * 私有构造函数（通过工厂方法创建）
     */
    private ExecutionRange(int startIndex, Integer endIndex) {
        if (startIndex < 0) {
            throw new IllegalArgumentException("startIndex 不能为负数");
        }
        if (endIndex != null && endIndex <= startIndex) {
            throw new IllegalArgumentException(
                String.format("endIndex (%d) 必须大于 startIndex (%d)", endIndex, startIndex)
            );
        }
        this.startIndex = startIndex;
        this.endIndex = endIndex;
    }

    // ============================================
    // 工厂方法
    // ============================================

    /**
     * 创建完整范围（正常执行）
     * <p>
     * 从头执行到最后：[0, totalStages)
     *
     * @param totalStages Stage 总数
     * @return ExecutionRange 实例
     */
    public static ExecutionRange full(int totalStages) {
        return new ExecutionRange(0, totalStages);
    }

    /**
     * 创建从头到指定位置的范围（回滚执行）
     * <p>
     * 从头执行到 checkpoint+1（半开区间）：[0, lastCompletedIndex+2)
     *
     * @param lastCompletedIndex 最后完成的 Stage 索引
     * @return ExecutionRange 实例
     */
    public static ExecutionRange forRollback(int lastCompletedIndex) {
        int endIndex = lastCompletedIndex + 2;  // [0, lastCompleted+2)
        return new ExecutionRange(0, endIndex);
    }

    /**
     * 创建从检查点到最后的范围（重试执行）
     * <p>
     * 从 checkpoint+1 执行到最后：[lastCompletedIndex+1, totalStages)
     *
     * @param lastCompletedIndex 最后完成的 Stage 索引
     * @param totalStages Stage 总数
     * @return ExecutionRange 实例
     */
    public static ExecutionRange forRetry(int lastCompletedIndex, int totalStages) {
        int startIndex = lastCompletedIndex + 1;
        return new ExecutionRange(startIndex, totalStages);
    }

    /**
     * 从检查点创建回滚范围（支持外部传入）
     *
     * @param checkpoint 检查点对象
     * @return ExecutionRange 实例
     */
    public static ExecutionRange forRollback(TaskCheckpoint checkpoint) {
        if (checkpoint == null) {
            throw new IllegalArgumentException("checkpoint 不能为空");
        }
        return forRollback(checkpoint.getLastCompletedStageIndex());
    }

    /**
     * 从检查点创建重试范围（支持外部传入）
     *
     * @param checkpoint 检查点对象
     * @return ExecutionRange 实例
     */
    public static ExecutionRange forRetry(TaskCheckpoint checkpoint) {
        if (checkpoint == null) {
            throw new IllegalArgumentException("checkpoint 不能为空");
        }
        int totalStages = checkpoint.getAllStageNames().size();
        return forRetry(checkpoint.getLastCompletedStageIndex(), totalStages);
    }

    // ============================================
    // 业务方法
    // ============================================

    /**
     * 判断指定索引是否在执行范围内
     *
     * @param stageIndex Stage 索引
     * @param totalStages Stage 总数
     * @return true = 在范围内，false = 不在范围内
     */
    public boolean contains(int stageIndex, int totalStages) {
        int effectiveEnd = getEffectiveEndIndex(totalStages);
        return stageIndex >= startIndex && stageIndex < effectiveEnd;
    }

    /**
     * 判断指定索引是否是执行范围内的最后一个
     * <p>
     * 用于判断是否应该保存检查点
     *
     * @param stageIndex Stage 索引
     * @param totalStages Stage 总数
     * @return true = 是范围内最后一个，false = 不是
     */
    public boolean isLastInRange(int stageIndex, int totalStages) {
        int effectiveEnd = getEffectiveEndIndex(totalStages);
        return stageIndex == effectiveEnd - 1;
    }

    /**
     * 获取有效的执行终点索引
     *
     * @param totalStages Stage 总数
     * @return 终点索引（半开区间）
     */
    public int getEffectiveEndIndex(int totalStages) {
        return endIndex != null ? endIndex : totalStages;
    }

    /**
     * 获取执行范围的 Stage 数量
     *
     * @param totalStages Stage 总数
     * @return Stage 数量
     */
    public int getStageCount(int totalStages) {
        return getEffectiveEndIndex(totalStages) - startIndex;
    }

    // ============================================
    // Getter 方法
    // ============================================

    public int getStartIndex() {
        return startIndex;
    }

    public Integer getEndIndex() {
        return endIndex;
    }

    // ============================================
    // equals / hashCode / toString
    // ============================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExecutionRange that = (ExecutionRange) o;
        return startIndex == that.startIndex &&
               Objects.equals(endIndex, that.endIndex);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startIndex, endIndex);
    }

    @Override
    public String toString() {
        return String.format("ExecutionRange[%d, %s)",
            startIndex,
            endIndex != null ? endIndex.toString() : "end");
    }
}

