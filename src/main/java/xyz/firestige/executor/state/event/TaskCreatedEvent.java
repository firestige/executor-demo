package xyz.firestige.executor.state.event;

import xyz.firestige.executor.orchestration.ExecutionMode;
import xyz.firestige.executor.state.TaskStatus;

/**
 * 任务创建事件
 */
public class TaskCreatedEvent extends TaskStatusEvent {

    /**
     * 配置数量
     */
    private int configCount;

    /**
     * 执行模式
     */
    private ExecutionMode executionMode;

    public TaskCreatedEvent() {
        super();
        setStatus(TaskStatus.CREATED);
    }

    public TaskCreatedEvent(String taskId, int configCount, ExecutionMode executionMode) {
        super(taskId, TaskStatus.CREATED);
        this.configCount = configCount;
        this.executionMode = executionMode;
        setMessage("任务已创建，配置数量: " + configCount);
    }

    // Getters and Setters

    public int getConfigCount() {
        return configCount;
    }

    public void setConfigCount(int configCount) {
        this.configCount = configCount;
    }

    public ExecutionMode getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(ExecutionMode executionMode) {
        this.executionMode = executionMode;
    }
}

