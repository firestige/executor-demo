package xyz.firestige.deploy.domain.task;

import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;
import xyz.firestige.deploy.infrastructure.execution.TaskExecutor;

import java.util.List;
import java.util.Optional;

/**
 * Task Runtime Repository 接口（DDD 重构：简化方案）
 * <p>
 * 职责：管理 Task 的运行时状态（Executor、Context、Stages）
 * <p>
 * 设计说明：
 * - 与持久化的聚合根分离
 * - 专门管理临时运行时状态
 * - 运行时状态通常不需要持久化到数据库
 * - 适合存储在内存或 Redis 中
 *
 * @since DDD 重构 Phase 18 - RF-09 简化方案
 */
public interface TaskRuntimeRepository {

    // ========== Executor 管理 ==========

    /**
     * 保存 TaskExecutor
     *
     * @param taskId Task ID
     * @param executor TaskExecutor 实例
     */
    void saveExecutor(TaskId taskId, TaskExecutor executor);

    /**
     * 获取 TaskExecutor
     *
     * @param taskId Task ID
     * @return TaskExecutor，如果不存在则返回 empty
     */
    Optional<TaskExecutor> getExecutor(TaskId taskId);

    // ========== Context 管理 ==========

    /**
     * 保存 Task 的运行时上下文
     *
     * @param taskId Task ID
     * @param context TaskRuntimeContext 实例
     */
    void saveContext(TaskId taskId, TaskRuntimeContext context);

    /**
     * 获取 Task 的运行时上下文
     *
     * @param taskId Task ID
     * @return TaskRuntimeContext，如果不存在则返回 empty
     */
    Optional<TaskRuntimeContext> getContext(TaskId taskId);

    // ========== Stages 管理 ==========

    /**
     * 保存 Task 的 Stage 列表
     *
     * @param taskId Task ID
     * @param stages Stage 列表
     */
    void saveStages(TaskId taskId, List<TaskStage> stages);

    /**
     * 获取 Task 的 Stage 列表
     *
     * @param taskId Task ID
     * @return Stage 列表，如果不存在则返回 empty
     */
    Optional<List<TaskStage>> getStages(TaskId taskId);

    // ========== 清理方法 ==========

    /**
     * 删除 Task 的所有运行时状态
     *
     * @param taskId Task ID
     */
    void remove(TaskId taskId);

    /**
     * 删除 TaskExecutor
     *
     * @param taskId Task ID
     */
    void removeExecutor(TaskId taskId);

    /**
     * 删除 Context
     *
     * @param taskId Task ID
     */
    void removeContext(TaskId taskId);

    /**
     * 删除 Stages
     *
     * @param taskId Task ID
     */
    void removeStages(TaskId taskId);
}

