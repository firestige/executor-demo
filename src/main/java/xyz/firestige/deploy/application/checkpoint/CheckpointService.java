package xyz.firestige.deploy.application.checkpoint;

import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.task.CheckpointRepository;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskCheckpoint;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 检查点服务（RF-DDD 重构版）
 * <p>
 * 职责：
 * 1. 协调聚合和存储之间的检查点持久化
 * 2. 不直接修改聚合状态，委托给聚合的业务方法
 * 3. 管理外部存储（CheckpointRepository）
 * <p>
 * 设计原则：
 * - Tell, Don't Ask：告诉聚合做什么，而不是修改其内部状态
 * - 聚合封装：通过 recordCheckpoint/restoreFromCheckpoint/clearCheckpoint 业务方法操作
 * - 职责分离：服务负责持久化协调，聚合负责业务规则验证
 */
public class CheckpointService {

    private final CheckpointRepository store;

    public CheckpointService(CheckpointRepository store) {
        this.store = store;
    }

    /**
     * 保存检查点（在 Stage 左边界）
     * <p>
     * 流程：
     * 1. 聚合验证业务规则并创建检查点
     * 2. 服务持久化到外部存储
     * 
     * @param task 任务聚合
     * @param completedStageNames 已完成的 Stage 名称列表
     * @param lastCompletedIndex 最后完成的 Stage 索引
     */
    public void saveCheckpoint(TaskAggregate task, List<String> completedStageNames, int lastCompletedIndex) {
        // ✅ 委托给聚合的业务方法（聚合内部验证不变量）
        task.recordCheckpoint(completedStageNames, lastCompletedIndex);
        
        // ✅ 持久化到外部存储
        TaskCheckpoint checkpoint = task.getCheckpoint();
        if (checkpoint != null) {
            store.put(task.getTaskId(), checkpoint);
        }
    }

    /**
     * 加载检查点（用于 retry 恢复）
     * <p>
     * 流程：
     * 1. 从存储加载检查点
     * 2. 聚合验证业务规则并恢复状态
     * 
     * @param task 任务聚合
     * @return 检查点对象，如果不存在返回 null
     */
    public TaskCheckpoint loadCheckpoint(TaskAggregate task) {
        TaskCheckpoint cp = store.get(task.getTaskId());
        if (cp != null) {
            // ✅ 委托给聚合的业务方法（聚合内部验证状态）
            task.restoreFromCheckpoint(cp);
        }
        return cp;
    }

    /**
     * 清除检查点
     * <p>
     * 使用场景：
     * 1. Task 完成后清理
     * 2. 重新开始（不从检查点恢复）
     * 
     * @param task 任务聚合
     */
    public void clearCheckpoint(TaskAggregate task) {
        // ✅ 委托给聚合的业务方法
        task.clearCheckpoint();
        
        // ✅ 从存储删除
        store.remove(task.getTaskId());
    }

    /**
     * 批量加载检查点（用于查询，不修改聚合）
     * <p>
     * 使用场景：
     * - 批量查询多个 Task 的检查点状态
     * - Plan 级别的恢复决策
     * <p>
     * 注意：此方法只返回数据，不修改传入的聚合对象
     * 
     * @param taskIds Task ID 列表
     * @return taskId -> TaskCheckpoint 的映射
     */
    public Map<TaskId, TaskCheckpoint> loadMultiple(List<TaskId> taskIds) {
        Map<TaskId, TaskCheckpoint> result = new HashMap<>();
        if (taskIds == null || taskIds.isEmpty()) {
            return result;
        }
        
        for (TaskId id : taskIds) {
            TaskCheckpoint cp = store.get(id);
            if (cp != null) {
                result.put(id, cp);
            }
        }
        
        return result;
    }
}
