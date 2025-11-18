package xyz.firestige.deploy.domain.plan;

import java.util.List;
import java.util.Optional;

/**
 * Plan Repository 接口（DDD 重构：简化方案）
 * <p>
 * 职责：Plan 聚合根的持久化和基本查询
 * <p>
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
}

