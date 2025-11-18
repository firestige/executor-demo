package xyz.firestige.deploy.domain.stage;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 单个步骤执行结果
 */
public class StepExecutionResult {
    private String stepName;
    private boolean success;
    private String message;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private long durationMillis;

    public static StepExecutionResult start(String stepName) {
        StepExecutionResult r = new StepExecutionResult();
        r.stepName = stepName;
        r.startTime = LocalDateTime.now();
        return r;
    }

    public void finishSuccess() {
        this.success = true;
        this.endTime = LocalDateTime.now();
        this.durationMillis = Duration.between(startTime, endTime).toMillis();
    }

    public void finishFailure(String msg) {
        this.success = false;
        this.message = msg;
        this.endTime = LocalDateTime.now();
        this.durationMillis = Duration.between(startTime, endTime).toMillis();
    }

    public String getStepName() { return stepName; }
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public long getDurationMillis() { return durationMillis; }
}

