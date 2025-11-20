package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.application.orchestration.ExecutionMode;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.task.TaskInfo;
import xyz.firestige.deploy.domain.task.TaskStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务创建事件
 */
public class TaskCreatedEvent extends TaskStatusEvent {

    /**
     * 配置数量
     */
    private final List<String> stageNames;

    public TaskCreatedEvent(TaskInfo info, List<String> stageNames) {
        super(info);
        this.stageNames = stageNames;
        setMessage("任务已创建，配置数量: [" + String.join(", ", stageNames) + "]");
    }

    // Getters and Setters

    public List<String> getStageNames() {
        return stageNames;
    }
}

