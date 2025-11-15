package xyz.firestige.executor.domain.plan;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

/**
 * Plan 执行时上下文
 */
public class PlanContext {
    private final String planId;
    private final Set<String> runningTaskIds = new HashSet<>();
    private final Queue<String> waitingTaskIds = new LinkedList<>();
    private volatile boolean pauseRequested;
    private volatile boolean cancelRequested;

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    public PlanContext(String planId) {
        this.planId = planId;
    }

    public String getPlanId() { return planId; }
    public Set<String> getRunningTaskIds() { return runningTaskIds; }
    public Queue<String> getWaitingTaskIds() { return waitingTaskIds; }

    public boolean isPauseRequested() { return pauseRequested; }
    public void requestPause() { this.pauseRequested = true; }
    public void clearPause() { this.pauseRequested = false; }

    public boolean isCancelRequested() { return cancelRequested; }
    public void requestCancel() { this.cancelRequested = true; }
    public void clearCancel() { this.cancelRequested = false; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
}

