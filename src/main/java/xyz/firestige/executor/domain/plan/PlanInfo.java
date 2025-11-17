package xyz.firestige.executor.domain.plan;

import xyz.firestige.executor.domain.task.TaskInfo;

import xyz.firestige.executor.domain.plan.PlanAggregate;
import xyz.firestige.executor.domain.plan.PlanStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Plan 聚合信息
 * 值对象，表达 Plan 聚合的基本信息和包含的 Task 列表
 * 不可变对象，体现 Plan 包含 Task 的聚合关系
 */
public class PlanInfo {

    private final String planId;
    private final int maxConcurrency;
    private final PlanStatus status;
    private final List<TaskInfo> tasks;     // Plan 包含的 Task 列表（聚合关系）
    private final LocalDateTime createdAt;

    public PlanInfo(String planId, int maxConcurrency, PlanStatus status,
                    List<TaskInfo> tasks, LocalDateTime createdAt) {
        this.planId = planId;
        this.maxConcurrency = maxConcurrency;
        this.status = status;
        this.tasks = Collections.unmodifiableList(new ArrayList<>(tasks));  // 防御性拷贝 + 不可变
        this.createdAt = createdAt;
    }

    /**
     * 静态工厂方法：从领域模型构造（RF-07 重构：需要传入 taskInfos）
     * 因为 PlanAggregate 现在只持有 taskIds，需要应用层组装完整信息
     *
     * @param plan Plan 聚合
     * @param taskInfos Task 信息列表（由应用层查询并组装）
     * @return PlanInfo
     */
    public static PlanInfo from(PlanAggregate plan, List<TaskInfo> taskInfos) {
        return new PlanInfo(
            plan.getPlanId(),
            plan.getMaxConcurrency(),
            plan.getStatus(),
            taskInfos != null ? taskInfos : Collections.emptyList(),
            plan.getCreatedAt()
        );
    }

    /**
     * 静态工厂方法：不包含 Task 信息（向后兼容）
     * @deprecated 请使用 from(PlanAggregate, List<TaskInfo>)
     */
    @Deprecated
    public static PlanInfo from(PlanAggregate plan) {
        return new PlanInfo(
            plan.getPlanId(),
            plan.getMaxConcurrency(),
            plan.getStatus(),
            Collections.emptyList(),  // 空列表
            plan.getCreatedAt()
        );
    }

    // Getters only (值对象不可变，无 Setters)

    public String getPlanId() {
        return planId;
    }

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public List<TaskInfo> getTasks() {
        return tasks;  // 已经是不可变的
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "PlanInfo{" +
                "planId='" + planId + '\'' +
                ", maxConcurrency=" + maxConcurrency +
                ", status=" + status +
                ", tasks=" + tasks.size() +
                ", createdAt=" + createdAt +
                '}';
    }
}


