package xyz.firestige.deploy.factory;

import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.deploy.domain.plan.PlanAggregate;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskCheckpoint;
import xyz.firestige.deploy.domain.task.TenantDeployConfigSnapshot;
import xyz.firestige.entity.deploy.NetworkEndpoint;

import java.util.List;
import java.util.UUID;

/**
 * 胶水层：将外部 DTO 转为内部聚合模型，避免直接持有外部对象引用。
 */
public class PlanFactory {

    /**
     * 根据租户配置列表构建 Plan + Task 列表（深拷贝必要字段）。
     * @param planId 计划ID（从入参 DTO 传入）
     * @param configs 租户维度配置
     */
    public PlanAggregate createPlan(String planId, List<TenantDeployConfig> configs) {
        PlanAggregate plan = new PlanAggregate(planId);
        if (configs == null) {
            return plan;
        }
        for (TenantDeployConfig cfg : configs) {
            String taskId = UUID.randomUUID().toString();
            TaskAggregate t = new TaskAggregate(taskId, planId, cfg.getTenantId());
            t.setDeployUnitId(cfg.getDeployUnitId());
            t.setDeployUnitVersion(cfg.getDeployUnitVersion());
            t.setDeployUnitName(cfg.getDeployUnitName());
            t.setCheckpoint(new TaskCheckpoint());
            // 生成上一版可用配置快照：优先使用 sourceTenantDeployConfig；否则当前配置作为第一版基准
            TenantDeployConfig source = cfg.getSourceTenantDeployConfig();
            List<NetworkEndpoint> eps = source != null ? source.getNetworkEndpoints() : cfg.getNetworkEndpoints();
            java.util.List<String> endpointValues = new java.util.ArrayList<>();
            if (eps != null) {
                for (NetworkEndpoint ep : eps) {
                    if (ep == null) continue;
                    String v = ep.getValue();
                    if (v == null || v.isEmpty()) {
                        v = ep.getTargetDomain() != null && !ep.getTargetDomain().isEmpty() ? ep.getTargetDomain() : ep.getTargetIp();
                    }
                    if (v != null && !v.isEmpty()) endpointValues.add(v);
                }
            }
            TenantDeployConfigSnapshot snapshot = new TenantDeployConfigSnapshot(
                    cfg.getTenantId(),
                    source != null ? source.getDeployUnitId() : cfg.getDeployUnitId(),
                    source != null ? source.getDeployUnitVersion() : cfg.getDeployUnitVersion(),
                    source != null ? source.getDeployUnitName() : cfg.getDeployUnitName(),
                    endpointValues
            );
            t.setPrevConfigSnapshot(snapshot);
            t.setLastKnownGoodVersion(snapshot.getDeployUnitVersion());
            // ✅ RF-07 重构：传递 taskId
            plan.addTask(t.getTaskId());
        }
        return plan;
    }
}
