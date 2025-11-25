package xyz.firestige.deploy.infrastructure.persistence.projection;

import xyz.firestige.deploy.domain.shared.vo.PlanId;

import java.util.List;

/**
 * Plan 状态投影存储接口（技术无关）
 * <p>
 * 职责：
 * - 持久化 Plan 状态投影供查询
 * - 不返回聚合根，仅返回投影 DTO
 * - 不参与业务不变式，仅供查询使用
 * <p>
 * 实现可以是：
 * - Redis Hash（默认）
 * - JDBC Table
 * - InMemory Map（测试）
 *
 * @since T-016 投影型持久化
 */
public interface PlanStateProjectionStore {

    /**
     * 保存 Plan 状态投影
     *
     * @param projection Plan 状态投影
     */
    void save(PlanStateProjection projection);

    /**
     * 加载 Plan 状态投影
     *
     * @param planId 计划 ID
     * @return Plan 状态投影，不存在返回 null
     */
    PlanStateProjection load(PlanId planId);

    /**
     * 删除投影（Plan 完成后清理）
     *
     * @param planId 计划 ID
     */
    void remove(PlanId planId);

    /**
     * 批量保存（性能优化，可选）
     *
     * @param projections 投影列表
     */
    default void saveAll(List<PlanStateProjection> projections) {
        if (projections != null) {
            projections.forEach(this::save);
        }
    }

    /**
     * 检查投影是否存在
     *
     * @param planId 计划 ID
     * @return true=存在，false=不存在
     */
    default boolean exists(PlanId planId) {
        return load(planId) != null;
    }
}

