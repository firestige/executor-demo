package xyz.firestige.executor.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import xyz.firestige.executor.domain.Task;
import xyz.firestige.executor.domain.TaskStatus;
import xyz.firestige.executor.event.TaskCompletedEvent;
import xyz.firestige.executor.event.TaskCompletingEvent;
import xyz.firestige.executor.event.TaskEvent;
import xyz.firestige.executor.event.TaskFailedEvent;
import xyz.firestige.executor.event.TaskFailingEvent;
import xyz.firestige.executor.event.TaskPausedEvent;
import xyz.firestige.executor.event.TaskPausingEvent;
import xyz.firestige.executor.event.TaskStartedEvent;
import xyz.firestige.executor.event.TaskStartingEvent;
import xyz.firestige.executor.event.TaskStoppedEvent;
import xyz.firestige.executor.event.TaskStoppingEvent;
import xyz.firestige.executor.exception.IllegalStateTransitionException;
import xyz.firestige.executor.statemachine.TaskStateMachine;

/**
 * 任务状态管理器
 * 集成状态机，管理任务状态转换，发布事前事后事件
 */
@Component
public class TaskStateManager {
    
    private static final Logger log = LoggerFactory.getLogger(TaskStateManager.class);
    
    private final TaskStateMachine stateMachine;
    private final ApplicationEventPublisher eventPublisher;
    
    public TaskStateManager(TaskStateMachine stateMachine, ApplicationEventPublisher eventPublisher) {
        this.stateMachine = stateMachine;
        this.eventPublisher = eventPublisher;
    }
    
    /**
     * 转换任务状态
     * 
     * @param task 任务对象
     * @param targetStatus 目标状态
     * @throws IllegalStateTransitionException 如果状态转换不合法
     */
    public void changeState(Task task, TaskStatus targetStatus) {
        TaskStatus currentStatus = task.getStatus();
        String taskId = task.getTaskId();
        
        log.info("Changing task state: taskId={}, from={}, to={}", taskId, currentStatus, targetStatus);
        
        // 发布事前事件
        publishBeforeEvent(task, targetStatus);
        
        try {
            // 使用状态机执行转换
            stateMachine.transition(task, targetStatus);
            
            log.info("Task state changed successfully: taskId={}, newStatus={}", taskId, targetStatus);
            
            // 发布事后事件
            publishAfterEvent(task, targetStatus);
            
        } catch (IllegalStateException e) {
            log.error("Failed to change task state: taskId={}, from={}, to={}", 
                taskId, currentStatus, targetStatus, e);
            throw new IllegalStateTransitionException(taskId, currentStatus, targetStatus);
        }
    }
    
    /**
     * 发布事前事件
     */
    private void publishBeforeEvent(Task task, TaskStatus targetStatus) {
        TaskEvent event = createBeforeEvent(task.getTaskId(), targetStatus);
        if (event != null) {
            event.setSource("TaskStateManager");
            event.putContext("currentStatus", task.getStatus());
            event.putContext("targetStatus", targetStatus);
            eventPublisher.publishEvent(event);
        }
    }
    
    /**
     * 发布事后事件
     */
    private void publishAfterEvent(Task task, TaskStatus targetStatus) {
        TaskEvent event = createAfterEvent(task.getTaskId(), targetStatus);
        if (event != null) {
            event.setSource("TaskStateManager");
            event.putContext("previousStatus", task.getStatus());
            event.putContext("currentStatus", targetStatus);
            eventPublisher.publishEvent(event);
        }
    }
    
    /**
     * 创建事前事件
     */
    private TaskEvent createBeforeEvent(String taskId, TaskStatus targetStatus) {
        return switch (targetStatus) {
            case RUNNING -> new TaskStartingEvent(taskId);
            case PAUSED -> new TaskPausingEvent(taskId);
            case STOPPED -> new TaskStoppingEvent(taskId);
            case COMPLETED -> new TaskCompletingEvent(taskId);
            case FAILED -> new TaskFailingEvent(taskId);
            default -> null;
        };
    }
    
    /**
     * 创建事后事件
     */
    private TaskEvent createAfterEvent(String taskId, TaskStatus targetStatus) {
        return switch (targetStatus) {
            case RUNNING -> new TaskStartedEvent(taskId);
            case PAUSED -> new TaskPausedEvent(taskId);
            case STOPPED -> new TaskStoppedEvent(taskId);
            case COMPLETED -> new TaskCompletedEvent(taskId);
            case FAILED -> new TaskFailedEvent(taskId);
            default -> null;
        };
    }
}
