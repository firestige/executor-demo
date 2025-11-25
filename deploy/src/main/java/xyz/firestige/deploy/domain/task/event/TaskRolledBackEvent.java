package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.task.TaskInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务回滚完成事件
 */
public class TaskRolledBackEvent extends TaskStatusEvent {

    /**
     * 已回滚的 Stage 列表
     */
    private final List<String> rolledBackStages;

    public TaskRolledBackEvent(TaskInfo info, List<String> rolledBackStages) {
        super(info);
        this.rolledBackStages = rolledBackStages != null ? rolledBackStages : new ArrayList<>();
        setMessage("任务回滚完成，回滚 Stage 数: " + this.rolledBackStages.size());
    }

    // Getters and Setters

    public List<String> getRolledBackStages() {
        return rolledBackStages;
    }
}
