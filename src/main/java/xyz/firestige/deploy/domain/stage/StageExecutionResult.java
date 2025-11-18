package xyz.firestige.deploy.domain.stage;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * TaskStage 执行结果
 */
public class StageExecutionResult {
    private String stageName;
    private boolean success;
    private String message;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private long durationMillis;
    private final List<StepExecutionResult> stepResults = new ArrayList<>();

    public static StageExecutionResult start(String stageName) {
        StageExecutionResult r = new StageExecutionResult();
        r.stageName = stageName;
        r.startTime = LocalDateTime.now();
        return r;
    }

    public void finishSuccess() {
        this.success = true;
        this.endTime = LocalDateTime.now();
        this.durationMillis = Duration.between(startTime, endTime).toMillis();
    }

    public void finishFailure(String message) {
        this.success = false;
        this.message = message;
        this.endTime = LocalDateTime.now();
        this.durationMillis = Duration.between(startTime, endTime).toMillis();
    }

    public void addStepResult(StepExecutionResult r) { this.stepResults.add(r); }

    public String getStageName() { return stageName; }
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public long getDurationMillis() { return durationMillis; }
    public List<StepExecutionResult> getStepResults() { return stepResults; }
}

