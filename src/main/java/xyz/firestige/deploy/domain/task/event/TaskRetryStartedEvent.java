package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.task.TaskInfo;

/**
 * 任务重试开始事件
 * 
 * <p>携带信息：
 * <ul>
 *   <li>是否从检查点恢复</li>
 *   <li>当前进度（仅在从检查点恢复时有效）</li>
 * </ul>
 */
public class TaskRetryStartedEvent extends TaskStatusEvent {
    private final boolean fromCheckpoint;
    
    /**
     * 基础构造函数（不带进度信息）
     */
    public TaskRetryStartedEvent(TaskInfo info, boolean fromCheckpoint) {
        super(info);
        this.fromCheckpoint = fromCheckpoint;
        setMessage(fromCheckpoint ? "从检查点重试任务" : "从头重试任务");
    }
    
    public boolean isFromCheckpoint() { 
        return fromCheckpoint; 
    }
}
