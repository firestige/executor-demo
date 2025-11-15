package xyz.firestige.executor.factory;

import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.domain.plan.PlanAggregate;
import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskCheckpoint;

import java.util.ArrayList;
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
        List<TaskAggregate> tasks = new ArrayList<>();
        for (TenantDeployConfig cfg : configs) {
            String taskId = UUID.randomUUID().toString();
            TaskAggregate t = new TaskAggregate(taskId, planId, cfg.getTenantId());
            // 深拷贝必要字段（不要保存 cfg 引用）
            t.setDeployUnitId(cfg.getDeployUnitId());
            t.setDeployUnitVersion(cfg.getDeployUnitVersion());
            t.setDeployUnitName(cfg.getDeployUnitName());
            t.setCheckpoint(new TaskCheckpoint());
            tasks.add(t);
            plan.addTask(t);
        }
        return plan;
    }
}
