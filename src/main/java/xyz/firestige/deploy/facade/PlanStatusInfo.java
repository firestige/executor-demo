package xyz.firestige.deploy.facade;

import xyz.firestige.deploy.domain.plan.PlanStatus;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.infrastructure.persistence.projection.PlanStateProjection;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Plan 状态信息 DTO（Phase3 新增）
 * 封装查询侧投影供 Facade 返回，避免直接暴露投影模型。
 */
public class PlanStatusInfo {
    private PlanId planId;
    private PlanStatus status;
    private int taskCount;
    private List<String> taskIds; // 字符串简化暴露
    private int maxConcurrency;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PlanStatusInfo fromProjection(PlanStateProjection p) {
        PlanStatusInfo info = new PlanStatusInfo();
        info.planId = p.getPlanId();
        info.status = p.getStatus();
        info.taskCount = p.getTaskIds() != null ? p.getTaskIds().size() : 0;
        info.taskIds = p.getTaskIds().stream().map(t -> t.getValue()).toList();
        info.maxConcurrency = p.getMaxConcurrency();
        info.createdAt = p.getCreatedAt();
        info.updatedAt = p.getUpdatedAt();
        return info;
    }

    public PlanId getPlanId() { return planId; }
    public PlanStatus getStatus() { return status; }
    public int getTaskCount() { return taskCount; }
    public List<String> getTaskIds() { return taskIds; }
    public int getMaxConcurrency() { return maxConcurrency; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

