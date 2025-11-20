package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.application.orchestration.ExecutionMode;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.task.TaskInfo;
import xyz.firestige.deploy.domain.task.TaskStatus;

/**
 * 任务创建事件
 */
public class TaskCreatedEvent extends TaskStatusEvent {

    /**
     * 配置数量
     */
    private final int configCount;

    public TaskCreatedEvent(TaskInfo info, int configCount, ExecutionMode executionMode) {
        super(info);
        this.configCount = configCount;
        setMessage("任务已创建，配置数量: " + configCount);
    }

    // Getters and Setters

    public int getConfigCount() {
        return configCount;
    }
}

