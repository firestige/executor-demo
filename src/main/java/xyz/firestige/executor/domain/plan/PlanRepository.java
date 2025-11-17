package xyz.firestige.executor.domain.plan;

import java.util.List;

/**
 * Plan Repository 接口
 *
 * 职责：Plan 聚合的持久化和查询
 *
 * 设计说明：
 * - 抽象存储实现（内存、Redis、数据库）
 * - 提供类型安全的查询方法
 * - 管理 Plan 的状态
 *
 * @since DDD 重构
 */
public interface PlanRepository {

    /**
     * 保存 Plan
     */
    void save(PlanAggregate plan);

    /**
     * 根据 Plan ID 获取 Plan
     */
    PlanAggregate get(String planId);

    /**
     * 获取所有 Plan
     */
    List<PlanAggregate> findAll();

    /**
     * 删除 Plan
     */
    void remove(String planId);

    /**
     * 更新 Plan 状态
     */
    void updateStatus(String planId, PlanStatus status);

    /**
     * 保存 PlanStateMachine
     */
    void saveStateMachine(String planId, xyz.firestige.executor.domain.state.PlanStateMachine stateMachine);

    /**
     * 获取 PlanStateMachine
     */
    xyz.firestige.executor.domain.state.PlanStateMachine getStateMachine(String planId);
}

