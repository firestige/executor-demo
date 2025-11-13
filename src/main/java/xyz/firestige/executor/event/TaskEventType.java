package xyz.firestige.executor.event;

/**
 * 任务事件类型
 * 区分事前事件和事后事件
 */
public enum TaskEventType {
    /**
     * 事前事件 - 在状态变化或操作执行之前发布
     */
    BEFORE,
    
    /**
     * 事后事件 - 在状态变化或操作执行之后发布
     */
    AFTER
}
