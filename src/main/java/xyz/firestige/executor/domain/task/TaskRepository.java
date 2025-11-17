package xyz.firestige.executor.domain.task;

import java.util.List;
import java.util.Optional;

/**
 * Task Repository 接口（DDD 重构：简化方案）
 * <p>
 * 职责：Task 聚合根的持久化和基本查询
 * <p>
 * 设计原则：
 * - 只管理聚合根的完整生命周期
 * - 不暴露聚合内部细节（Stages、Context、Executor）
 * - 保持简单实用，避免过度设计
 * - 使用 Optional 返回值，明确表达"可能不存在"
 *
 * @since DDD 重构 Phase 18 - RF-09 简化方案
 */
public interface TaskRepository {

    // ========== 命令方法 ==========

    /**
     * 保存 Task 聚合（包含所有内部状态）
     *
     * @param task Task 聚合
     */
    void save(TaskAggregate task);

    /**
     * 删除 Task 聚合
     *
     * @param taskId Task ID
     */
    void remove(String taskId);

    // ========== 查询方法 ==========

    /**
     * 根据 Task ID 查找 Task
     *
     * @param taskId Task ID
     * @return Task 聚合，如果不存在则返回 empty
     */
    Optional<TaskAggregate> findById(String taskId);

    /**
     * 根据租户 ID 查找 Task
     *
     * @param tenantId 租户 ID
     * @return Task 聚合，如果不存在则返回 empty
     */
    Optional<TaskAggregate> findByTenantId(String tenantId);

    /**
     * 根据 Plan ID 查找所有 Task
     *
     * @param planId Plan ID
     * @return Task 列表，如果不存在则返回空列表
     */
    List<TaskAggregate> findByPlanId(String planId);
}

