package xyz.firestige.deploy.domain.task;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Task 进度视图（宽表设计）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskProgressView {

    private final Integer currentStageIndex;
    private final String currentStageName;
    private final Integer completedStages;
    private final Integer totalStages;
    private final Integer totalStagesInRange;
    private final Integer startIndex;
    private final Integer endIndex;
    private final Double progressPercentage;

    private TaskProgressView(
        Integer currentStageIndex,
        String currentStageName,
        Integer completedStages,
        Integer totalStages,
        Integer totalStagesInRange,
        Integer startIndex,
        Integer endIndex,
        Double progressPercentage
    ) {
        this.currentStageIndex = currentStageIndex;
        this.currentStageName = currentStageName;
        this.completedStages = completedStages;
        this.totalStages = totalStages;
        this.totalStagesInRange = totalStagesInRange;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.progressPercentage = progressPercentage;
    }

    public static TaskProgressView from(StageProgress progress, ExecutionRange range) {
        if (progress == null || range == null) {
            return null;
        }

        int totalStages = progress.getTotalStages();
        int effectiveEnd = range.getEffectiveEndIndex(totalStages);
        int effectiveStart = range.getStartIndex();

        double progressPercentage = 0.0;
        if (effectiveEnd > effectiveStart) {
            int current = Math.min(progress.getCurrentStageIndex(), effectiveEnd);
            progressPercentage = (double) (current - effectiveStart) / (effectiveEnd - effectiveStart) * 100;
        }

        return new TaskProgressView(
            progress.getCurrentStageIndex(),
            progress.getCurrentStageName(),
            progress.getCurrentStageIndex(),
            totalStages,
            effectiveEnd,
            effectiveStart,
            effectiveEnd,
            progressPercentage
        );
    }

    public Integer getCurrentStageIndex() {
        return currentStageIndex;
    }

    public String getCurrentStageName() {
        return currentStageName;
    }

    public Integer getCompletedStages() {
        return completedStages;
    }

    public Integer getTotalStages() {
        return totalStages;
    }

    public Integer getTotalStagesInRange() {
        return totalStagesInRange;
    }

    public Integer getStartIndex() {
        return startIndex;
    }

    public Integer getEndIndex() {
        return endIndex;
    }

    public Double getProgressPercentage() {
        return progressPercentage;
    }

    @Override
    public String toString() {
        return String.format("TaskProgressView{current=%d/%d, range=[%d,%d), progress=%.1f%%}",
            currentStageIndex, totalStages, startIndex, endIndex, progressPercentage);
    }
}

