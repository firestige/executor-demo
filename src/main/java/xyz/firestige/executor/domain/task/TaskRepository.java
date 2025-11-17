package xyz.firestige.executor.domain.task;

import xyz.firestige.executor.domain.stage.TaskStage;
import xyz.firestige.executor.execution.TaskExecutor;

import java.util.List;

/**
 * Task Repository 接口
 *
 * 职责：Task 聚合的持久化和查询
 *
 * 设计说明：
 * - 抽象存储实现（内存、Redis、数据库）
 * - 提供类型安全的查询方法
 * - 管理 Task 的运行时状态（Context、Executor、Stages）
 *
 * @since DDD 重构
 */
public interface TaskRepository {

    // ========== Task 聚合操作 ==========

    /**
     * 保存 Task
     */
    void save(TaskAggregate task);

    /**
     * 根据 Task ID 获取 Task
     */
    TaskAggregate get(String taskId);

    /**
     * 根据租户 ID 查找 Task
     */
    TaskAggregate findByTenantId(String tenantId);

    /**
     * 根据 Plan ID 获取所有 Task
     */
    List<TaskAggregate> findByPlanId(String planId);

    /**
     * 删除 Task
     */
    void remove(String taskId);

    // ========== Task 运行时状态管理 ==========

    /**
     * 保存 Task 的 Stage 列表
     */
    void saveStages(String taskId, List<TaskStage> stages);

    /**
     * 获取 Task 的 Stage 列表
     */
    List<TaskStage> getStages(String taskId);

    /**
     * 保存 Task 的运行时上下文
     */
    void saveContext(String taskId, TaskRuntimeContext context);

    /**
     * 获取 Task 的运行时上下文
     */
    TaskRuntimeContext getContext(String taskId);

    /**
     * 保存 TaskExecutor
     */
    void saveExecutor(String taskId, TaskExecutor executor);

    /**
     * 获取 TaskExecutor
     */
    TaskExecutor getExecutor(String taskId);

    // ========== 运行时控制标志 ==========

    /**
     * 请求暂停 Task
     */
    void requestPause(String taskId);

    /**
     * 清除暂停标志
     */
    void clearPause(String taskId);

    /**
     * 请求取消 Task
     */
    void requestCancel(String taskId);

    /**
     * 检查是否请求暂停
     */
    boolean isPauseRequested(String taskId);

    /**
     * 检查是否请求取消
     */
    boolean isCancelRequested(String taskId);
}

