package xyz.firestige.executor.domain.plan;

import xyz.firestige.executor.domain.state.PlanStateMachine;

import java.util.List;
import java.util.Optional;

/**
 * Plan Repository 接口（DDD 重构：简化方案）
 *
 * 职责：Plan 聚合根的持久化和基本查询
 *
 * 设计原则：
 * - 只管理聚合根的完整生命周期
 * - 使用 Optional 返回值
 * - 保持简单实用
 *
 * @since DDD 重构 Phase 18 - RF-09 简化方案
 */
public interface PlanRepository {

    // ========== 命令方法 ==========

    /**
     * 保存 Plan 聚合
     *
     * @param plan Plan 聚合
     */
    void save(PlanAggregate plan);

    /**
     * 删除 Plan 聚合
     *
     * @param planId Plan ID
     */
    void remove(String planId);

    // ========== 查询方法 ==========

    /**
     * 根据 Plan ID 查找 Plan
     *
     * @param planId Plan ID
     * @return Plan 聚合，如果不存在则返回 empty
     */
    Optional<PlanAggregate> findById(String planId);

    /**
     * 获取所有 Plan
     *
     * @return Plan 列表，如果不存在则返回空列表
     */
    List<PlanAggregate> findAll();

    // ========== 状态机管理 ==========

    /**
     * 保存 PlanStateMachine
     *
     * @param planId Plan ID
     * @param stateMachine PlanStateMachine 实例
     */
    void saveStateMachine(String planId, PlanStateMachine stateMachine);

    /**
     * 获取 PlanStateMachine
     *
     * @param planId Plan ID
     * @return PlanStateMachine，如果不存在则返回 empty
     */
    Optional<PlanStateMachine> getStateMachine(String planId);

    // ========== 向后兼容方法（逐步淘汰）==========

    /**
     * 根据 Plan ID 获取 Plan（向后兼容）
     *
     * @param planId Plan ID
     * @return Plan 聚合，如果不存在则返回 null
     * @deprecated 请使用 findById(String)，返回 Optional
     */
    @Deprecated
    default PlanAggregate get(String planId) {
        return findById(planId).orElse(null);
    }

    /**
     * 获取 PlanStateMachine（向后兼容）
     *
     * @param planId Plan ID
     * @return PlanStateMachine，如果不存在则返回 null
     * @deprecated 请使用 getStateMachine(String)，返回 Optional
     */
    @Deprecated
    default PlanStateMachine getStateMachine(String planId) {
        return getStateMachine(planId).orElse(null);
    }
}

