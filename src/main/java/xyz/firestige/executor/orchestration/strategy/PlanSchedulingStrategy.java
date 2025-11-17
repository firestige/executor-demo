package xyz.firestige.executor.orchestration.strategy;

import java.util.List;

/**
 * Plan 调度策略接口
 *
 * 支持两种策略：
 * 1. 细粒度（Fine-Grained）：租户级冲突检测，高并发
 * 2. 粗粒度（Coarse-Grained）：创建时租户冲突检测，立即拒绝
 *
 * @since Phase 18 - RF-12
 */
public interface PlanSchedulingStrategy {

    /**
     * 检查是否可以创建 Plan
     *
     * @param tenantIds 租户 ID 列表
     * @return true=允许创建，false=拒绝创建
     */
    boolean canCreatePlan(List<String> tenantIds);

    /**
     * 检查是否可以启动 Plan
     *
     * @param planId Plan ID
     * @param tenantIds 租户 ID 列表
     * @return true=允许启动，false=拒绝启动
     */
    boolean canStartPlan(String planId, List<String> tenantIds);

    /**
     * Plan 创建完成通知
     *
     * @param planId Plan ID
     * @param tenantIds 租户 ID 列表
     */
    void onPlanCreated(String planId, List<String> tenantIds);

    /**
     * Plan 完成通知（包括成功和失败）
     *
     * @param planId Plan ID
     * @param tenantIds 租户 ID 列表
     */
    void onPlanCompleted(String planId, List<String> tenantIds);
}

