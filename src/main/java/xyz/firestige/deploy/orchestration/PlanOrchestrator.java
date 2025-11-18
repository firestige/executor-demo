package xyz.firestige.deploy.orchestration;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import xyz.firestige.deploy.config.ExecutorProperties;
import xyz.firestige.deploy.domain.plan.PlanAggregate;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.support.conflict.TenantConflictManager;

/**
 * 计划编排器（新实现）。暂不接线旧流程。
 */
public class PlanOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PlanOrchestrator.class);

    public interface TaskWorkerFactory {
        TaskScheduler.TaskWorker create(TaskAggregate task);
    }

    private final TaskScheduler scheduler;
    private final TenantConflictManager conflictManager; // RF-14
    private final ExecutorProperties props;

    public PlanOrchestrator(TaskScheduler scheduler, TenantConflictManager conflictManager, ExecutorProperties props) {
        this.scheduler = Objects.requireNonNull(scheduler);
        this.conflictManager = Objects.requireNonNull(conflictManager);
        this.props = Objects.requireNonNull(props);
    }

    /**
     * 提交计划执行（RF-07 重构：接受 Task 列表）
     * 因为 PlanAggregate 现在只持有 taskIds，需要调用方传入 Task 列表
     *
     * @param plan Plan 聚合
     * @param taskAggregates Task 聚合列表
     * @param workerFactory Worker 工厂
     */
    public void submitPlan(PlanAggregate plan, List<TaskAggregate> taskAggregates, TaskWorkerFactory workerFactory) {
        int maxConcurrency = plan.getMaxConcurrency() != null ? plan.getMaxConcurrency() : props.getMaxConcurrency();
        plan.start();
        log.info("提交计划: planId={}, maxConcurrency={}", plan.getPlanId(), maxConcurrency);

        for (TaskAggregate t : taskAggregates) {
            // 冲突检测
            if (!conflictManager.registerTask(t.getTenantId(), t.getTaskId())) {
                String runningTask = conflictManager.getConflictingTaskId(t.getTenantId());
                log.warn("租户冲突: tenantId={}, runningTask={}", t.getTenantId(), runningTask);
                continue;
            }
            // 调度（达到并发阈值则入队）
            scheduler.schedule(t.getTaskId(), maxConcurrency, workerFactory.create(t));
        }
    }

    /**
     * 提交计划执行（向后兼容，逐步淘汰）
     * @deprecated 请使用 submitPlan(PlanAggregate, List<TaskAggregate>, TaskWorkerFactory)
     */
    @Deprecated
    public void submitPlan(PlanAggregate plan, TaskWorkerFactory workerFactory) {
        // 为了向后兼容，暂时保留空实现
        log.warn("使用了已废弃的 submitPlan 方法，请使用新方法传入 taskAggregates");
    }

    public void releaseTenantLock(String tenantId) {
        if (tenantId != null) conflictManager.releaseTask(tenantId);
    }
}
