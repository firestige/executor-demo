package xyz.firestige.executor.execution.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.executor.execution.checkpoint.Checkpoint;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存检查点管理器
 * 将检查点保存在内存中（适用于测试或单机部署）
 */
public class InMemoryCheckpointManager implements CheckpointManager {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryCheckpointManager.class);

    /**
     * 检查点存储
     * Key: taskId, Value: Checkpoint
     */
    private final Map<String, Checkpoint> checkpoints = new ConcurrentHashMap<>();

    @Override
    public void saveCheckpoint(String taskId, String stageName, Checkpoint checkpoint) {
        logger.debug("保存检查点: taskId={}, stageName={}", taskId, stageName);
        checkpoints.put(taskId, checkpoint);
    }

    @Override
    public Checkpoint loadCheckpoint(String taskId) {
        Checkpoint checkpoint = checkpoints.get(taskId);
        if (checkpoint != null) {
            logger.debug("加载检查点: taskId={}, stageName={}", taskId, checkpoint.getStageName());
        } else {
            logger.debug("未找到检查点: taskId={}", taskId);
        }
        return checkpoint;
    }

    @Override
    public void clearCheckpoint(String taskId) {
        logger.debug("清除检查点: taskId={}", taskId);
        checkpoints.remove(taskId);
    }

    @Override
    public boolean hasCheckpoint(String taskId) {
        return checkpoints.containsKey(taskId);
    }

    /**
     * 获取所有检查点数量（用于监控）
     */
    public int getCheckpointCount() {
        return checkpoints.size();
    }

    /**
     * 清空所有检查点（用于测试）
     */
    public void clearAll() {
        logger.info("清空所有检查点");
        checkpoints.clear();
    }
}

