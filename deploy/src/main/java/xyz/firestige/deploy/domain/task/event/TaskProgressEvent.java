package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskInfo;
import xyz.firestige.deploy.domain.task.TaskInfoView;
import xyz.firestige.deploy.domain.task.TaskProgressView;

/**
 * 任务进度事件
 * <p>
 * T-036: 重构为使用三大视图，特别是 TaskProgressView
 */
public class TaskProgressEvent extends TaskStatusEvent {

    private final TaskInfoView taskInfo;
    private final TaskProgressView progress;

    private final String currentStage;
    private final int completedStages;
    private final int totalStages;
    private final double progressPercentage;

    public TaskProgressEvent(TaskAggregate task, String currentStage, int completedStages, int totalStages) {
        super(task);
        this.taskInfo = TaskInfoView.from(task);
        this.progress = task.getProgressView();
        this.currentStage = currentStage;
        this.completedStages = completedStages;
        this.totalStages = totalStages;
        this.progressPercentage = totalStages > 0 ? (double) completedStages / totalStages * 100 : 0;
        setMessage("任务执行中，进度: " + String.format("%.1f", progressPercentage) + "%");
    }

    @Deprecated
    public TaskProgressEvent(TaskInfo info, String currentStage, int completedStages, int totalStages) {
        super(info);
        this.taskInfo = getTaskInfo();
        this.progress = null;
        this.currentStage = currentStage;
        this.completedStages = completedStages;
        this.totalStages = totalStages;
        this.progressPercentage = totalStages > 0 ? (double) completedStages / totalStages * 100 : 0;
        setMessage("任务执行中，进度: " + String.format("%.1f", progressPercentage) + "%");
    }

    public String getCurrentStage() { return currentStage; }
    public int getCompletedStages() { return completedStages; }
    public int getTotalStages() { return totalStages; }
    public double getProgress() { return progressPercentage; }
    public TaskInfoView getTaskInfoView() { return taskInfo; }
    public TaskProgressView getProgressView() { return progress; }
}

