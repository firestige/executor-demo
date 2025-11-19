package xyz.firestige.deploy.infrastructure.execution;

import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.task.TaskStatus;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 新 TaskExecutor 的汇总结果（暂不接线）。
 */
public class TaskResult {
    private boolean success;
    private String message;
    private final List<StageResult> completedStages = new ArrayList<>();
    private TaskId taskId;
    private PlanId planId;
    private TaskStatus finalStatus;
    private Duration duration;

    public static TaskResult ok(PlanId planId, TaskId taskId, TaskStatus status, Duration duration, List<StageResult> stages) {
        TaskResult r = new TaskResult();
        r.success = true;
        r.planId = planId;
        r.taskId = taskId;
        r.finalStatus = status;
        r.duration = duration;
        if (stages != null) r.completedStages.addAll(stages);
        return r;
    }

    public static TaskResult fail(PlanId planId, TaskId taskId, TaskStatus status, String msg, Duration duration, List<StageResult> stages) {
        TaskResult r = new TaskResult();
        r.success = false;
        r.message = msg;
        r.planId = planId;
        r.taskId = taskId;
        r.finalStatus = status;
        r.duration = duration;
        if (stages != null) r.completedStages.addAll(stages);
        return r;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public List<StageResult> getCompletedStages() { return completedStages; }
    public TaskId getTaskId() { return taskId; }
    public PlanId getPlanId() { return planId; }
    public TaskStatus getFinalStatus() { return finalStatus; }
    public Duration getDuration() { return duration; }
}
