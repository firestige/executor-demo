package xyz.firestige.executor.manager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import xyz.firestige.executor.domain.Checkpoint;
import xyz.firestige.executor.domain.Task;
import xyz.firestige.executor.domain.TaskContext;

/**
 * 检查点管理器
 * 管理任务执行检查点，支持暂停恢复和异常重启，记录执行历史
 */
@Component
public class CheckpointManager {
    
    private static final Logger log = LoggerFactory.getLogger(CheckpointManager.class);
    
    // 任务ID -> 检查点历史
    private final Map<String, List<Checkpoint>> checkpointHistory = new ConcurrentHashMap<>();
    
    /**
     * 保存检查点
     * 
     * @param task 任务对象
     * @param serviceId 当前执行的服务ID
     * @param serviceIndex 当前执行的服务索引
     * @param completedTenants 已完成的租户列表
     * @param metadata 额外的元数据
     */
    public void saveCheckpoint(Task task, String serviceId, int serviceIndex, 
                              List<String> completedTenants, Map<String, Object> metadata) {
        String taskId = task.getTaskId();
        
        // 创建检查点
        Checkpoint checkpoint = new Checkpoint(taskId, serviceId, serviceIndex);
        checkpoint.setCreateTime(LocalDateTime.now());
        checkpoint.setCompletedTenants(new java.util.ArrayList<>(completedTenants));
        
        // 添加元数据
        if (metadata != null) {
            metadata.forEach(checkpoint::putContext);
        }
        
        // 保存到任务上下文
        TaskContext context = task.getContext();
        if (context != null) {
            context.addCheckpoint(checkpoint);
        }
        
        // 保存到历史记录
        checkpointHistory.computeIfAbsent(taskId, k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                         .add(checkpoint);
        
        log.info("Checkpoint saved: taskId={}, serviceId={}, serviceIndex={}, completedTenants={}", 
            taskId, serviceId, serviceIndex, completedTenants.size());
    }
    
    /**
     * 获取任务的最新检查点
     * 
     * @param taskId 任务ID
     * @return 最新的检查点，如果不存在则返回null
     */
    public Checkpoint getLatestCheckpoint(String taskId) {
        List<Checkpoint> checkpoints = checkpointHistory.get(taskId);
        if (checkpoints == null || checkpoints.isEmpty()) {
            return null;
        }
        return checkpoints.get(checkpoints.size() - 1);
    }
    
    /**
     * 从任务对象获取最新检查点
     * 
     * @param task 任务对象
     * @return 最新的检查点，如果不存在则返回null
     */
    public Checkpoint getLatestCheckpoint(Task task) {
        TaskContext context = task.getContext();
        if (context != null) {
            return context.getLatestCheckpoint();
        }
        return getLatestCheckpoint(task.getTaskId());
    }
    
    /**
     * 获取任务的所有检查点历史
     * 
     * @param taskId 任务ID
     * @return 检查点列表
     */
    public List<Checkpoint> getCheckpointHistory(String taskId) {
        return checkpointHistory.getOrDefault(taskId, List.of());
    }
    
    /**
     * 检查任务是否可以从检查点恢复
     * 
     * @param taskId 任务ID
     * @return true 如果存在检查点
     */
    public boolean hasCheckpoint(String taskId) {
        List<Checkpoint> checkpoints = checkpointHistory.get(taskId);
        return checkpoints != null && !checkpoints.isEmpty();
    }
    
    /**
     * 从检查点恢复任务执行
     * 
     * @param task 任务对象
     * @return 恢复的检查点，如果不存在则返回null
     */
    public Checkpoint restoreFromCheckpoint(Task task) {
        Checkpoint checkpoint = getLatestCheckpoint(task);
        if (checkpoint == null) {
            log.warn("No checkpoint found for task: taskId={}", task.getTaskId());
            return null;
        }
        
        log.info("Restoring task from checkpoint: taskId={}, serviceId={}, serviceIndex={}", 
            task.getTaskId(), checkpoint.getCurrentServiceId(), checkpoint.getCurrentServiceIndex());
        
        return checkpoint;
    }
    
    /**
     * 清理任务的检查点历史
     * 
     * @param taskId 任务ID
     */
    public void clearCheckpoints(String taskId) {
        List<Checkpoint> removed = checkpointHistory.remove(taskId);
        if (removed != null) {
            log.info("Cleared checkpoints: taskId={}, count={}", taskId, removed.size());
        }
    }
    
    /**
     * 获取检查点统计信息
     * 
     * @param taskId 任务ID
     * @return 统计信息
     */
    public CheckpointStats getStats(String taskId) {
        List<Checkpoint> checkpoints = checkpointHistory.get(taskId);
        if (checkpoints == null || checkpoints.isEmpty()) {
            return new CheckpointStats(taskId, 0, null, null);
        }
        
        Checkpoint first = checkpoints.get(0);
        Checkpoint last = checkpoints.get(checkpoints.size() - 1);
        
        return new CheckpointStats(
            taskId,
            checkpoints.size(),
            first.getCreateTime(),
            last.getCreateTime()
        );
    }
    
    /**
     * 检查点统计信息
     */
    public record CheckpointStats(
        String taskId,
        int totalCheckpoints,
        LocalDateTime firstCheckpointTime,
        LocalDateTime lastCheckpointTime
    ) {}
}
