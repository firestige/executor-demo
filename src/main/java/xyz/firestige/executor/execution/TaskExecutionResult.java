package xyz.firestige.executor.execution;

import xyz.firestige.executor.execution.StageResult;
import xyz.firestige.executor.state.TaskStatus;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 新 TaskExecutor 的汇总结果（暂不接线）。
 */
public class TaskExecutionResult {
    private boolean success;
    private String message;
    private final List<StageResult> completedStages = new ArrayList<>();
    private String taskId;
    private String planId;
    private TaskStatus finalStatus;
    private Duration duration;

    public static TaskExecutionResult ok(String planId, String taskId, TaskStatus status, Duration duration, List<StageResult> stages) {
        TaskExecutionResult r = new TaskExecutionResult();
        r.success = true;
        r.planId = planId;
        r.taskId = taskId;
        r.finalStatus = status;
        r.duration = duration;
        if (stages != null) r.completedStages.addAll(stages);
        return r;
    }

    public static TaskExecutionResult fail(String planId, String taskId, TaskStatus status, String msg, Duration duration, List<StageResult> stages) {
        TaskExecutionResult r = new TaskExecutionResult();
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
    public String getTaskId() { return taskId; }
    public String getPlanId() { return planId; }
    public TaskStatus getFinalStatus() { return finalStatus; }
    public Duration getDuration() { return duration; }
}
