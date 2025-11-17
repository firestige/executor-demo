package xyz.firestige.executor.orchestration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.executor.config.ExecutorProperties;
import xyz.firestige.executor.domain.plan.PlanAggregate;
import xyz.firestige.executor.domain.plan.PlanContext;
import xyz.firestige.executor.domain.plan.PlanStatus;
import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.support.conflict.ConflictRegistry;

import java.util.Objects;

/**
 * 计划编排器（新实现）。暂不接线旧流程。
 */
public class PlanOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PlanOrchestrator.class);

    public interface TaskWorkerFactory {
        TaskScheduler.TaskWorker create(TaskAggregate task);
    }

    private final TaskScheduler scheduler;
    private final ConflictRegistry conflicts;
    private final ExecutorProperties props;

    public PlanOrchestrator(TaskScheduler scheduler, ConflictRegistry conflicts, ExecutorProperties props) {
        this.scheduler = Objects.requireNonNull(scheduler);
        this.conflicts = Objects.requireNonNull(conflicts);
        this.props = Objects.requireNonNull(props);
    }

    public void submitPlan(PlanAggregate plan, TaskWorkerFactory workerFactory) {
        int maxConcurrency = plan.getMaxConcurrency() != null ? plan.getMaxConcurrency() : props.getMaxConcurrency();
        PlanContext ctx = new PlanContext(plan.getPlanId());
        plan.setStatus(PlanStatus.RUNNING);
        log.info("提交计划: planId={}, maxConcurrency={}", plan.getPlanId(), maxConcurrency);

        for (TaskAggregate t : plan.getTasks()) {
            // 冲突检测
            if (!conflicts.register(t.getTenantId(), t.getTaskId())) {
                String runningTask = conflicts.getRunningTaskId(t.getTenantId());
                log.warn("租户冲突: tenantId={}, runningTask={}", t.getTenantId(), runningTask);
                continue;
            }
            // 调度（达到并发阈值则入队）
            scheduler.schedule(t.getTaskId(), maxConcurrency, workerFactory.create(t));
        }
    }

    public void releaseTenantLock(String tenantId) {
        if (tenantId != null) conflicts.release(tenantId);
    }
}
