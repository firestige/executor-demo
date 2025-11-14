package xyz.firestige.executor.execution.pipeline;

import xyz.firestige.executor.execution.checkpoint.Checkpoint;

/**
 * 检查点管理器接口
 * 负责检查点的保存和加载
 */
public interface CheckpointManager {

    /**
     * 保存检查点
     *
     * @param taskId 任务 ID
     * @param stageName 当前 Stage 名称
     * @param checkpoint 检查点数据
     */
    void saveCheckpoint(String taskId, String stageName, Checkpoint checkpoint);

    /**
     * 加载检查点
     *
     * @param taskId 任务 ID
     * @return 检查点数据，如果不存在则返回 null
     */
    Checkpoint loadCheckpoint(String taskId);

    /**
     * 清除检查点
     *
     * @param taskId 任务 ID
     */
    void clearCheckpoint(String taskId);

    /**
     * 检查是否存在检查点
     *
     * @param taskId 任务 ID
     * @return true 表示存在检查点
     */
    boolean hasCheckpoint(String taskId);
}

