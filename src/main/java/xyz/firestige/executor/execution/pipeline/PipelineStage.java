package xyz.firestige.executor.execution.pipeline;

import xyz.firestige.executor.execution.StageResult;

/**
 * Pipeline Stage 接口
 * 表示 Pipeline 中的一个执行阶段
 */
public interface PipelineStage {

    /**
     * 获取 Stage 名称
     *
     * @return Stage 名称
     */
    String getName();

    /**
     * 执行 Stage
     *
     * @param context Pipeline 上下文
     * @return Stage 执行结果
     */
    StageResult execute(PipelineContext context);

    /**
     * 回滚 Stage
     * 当任务失败或需要回滚时调用
     *
     * @param context Pipeline 上下文
     */
    void rollback(PipelineContext context);

    /**
     * 获取执行顺序
     * 数字越小优先级越高
     *
     * @return 执行顺序
     */
    int getOrder();

    /**
     * 是否支持回滚
     *
     * @return true 表示支持回滚
     */
    default boolean supportsRollback() {
        return true;
    }

    /**
     * 是否可以跳过
     * 某些条件下 Stage 可以被跳过
     *
     * @param context Pipeline 上下文
     * @return true 表示可以跳过
     */
    default boolean canSkip(PipelineContext context) {
        return false;
    }
}

