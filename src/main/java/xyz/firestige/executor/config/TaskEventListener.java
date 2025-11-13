package xyz.firestige.executor.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import xyz.firestige.executor.event.TaskCompletedEvent;
import xyz.firestige.executor.event.TaskCompletingEvent;
import xyz.firestige.executor.event.TaskFailedEvent;
import xyz.firestige.executor.event.TaskFailingEvent;
import xyz.firestige.executor.event.TaskPausedEvent;
import xyz.firestige.executor.event.TaskPausingEvent;
import xyz.firestige.executor.event.TaskRollbackCompleteEvent;
import xyz.firestige.executor.event.TaskRollingBackEvent;
import xyz.firestige.executor.event.TaskStartedEvent;
import xyz.firestige.executor.event.TaskStartingEvent;
import xyz.firestige.executor.event.TaskStoppedEvent;
import xyz.firestige.executor.event.TaskStoppingEvent;

/**
 * 任务事件监听器
 * 监听任务生命周期中的各种事件
 */
@Component
public class TaskEventListener {
    
    private static final Logger log = LoggerFactory.getLogger(TaskEventListener.class);
    
    /**
     * 任务启动中事件
     */
    @Async
    @EventListener
    public void onTaskStarting(TaskStartingEvent event) {
        log.info("Event received - Task starting: taskId={}, source={}", 
            event.getTaskId(), event.getSource());
    }
    
    /**
     * 任务已启动事件
     */
    @Async
    @EventListener
    public void onTaskStarted(TaskStartedEvent event) {
        log.info("Event received - Task started: taskId={}, source={}", 
            event.getTaskId(), event.getSource());
    }
    
    /**
     * 任务暂停中事件
     */
    @Async
    @EventListener
    public void onTaskPausing(TaskPausingEvent event) {
        log.info("Event received - Task pausing: taskId={}, source={}", 
            event.getTaskId(), event.getSource());
    }
    
    /**
     * 任务已暂停事件
     */
    @Async
    @EventListener
    public void onTaskPaused(TaskPausedEvent event) {
        log.info("Event received - Task paused: taskId={}, source={}", 
            event.getTaskId(), event.getSource());
    }
    
    /**
     * 任务停止中事件
     */
    @Async
    @EventListener
    public void onTaskStopping(TaskStoppingEvent event) {
        log.info("Event received - Task stopping: taskId={}, source={}", 
            event.getTaskId(), event.getSource());
    }
    
    /**
     * 任务已停止事件
     */
    @Async
    @EventListener
    public void onTaskStopped(TaskStoppedEvent event) {
        log.info("Event received - Task stopped: taskId={}, source={}", 
            event.getTaskId(), event.getSource());
    }
    
    /**
     * 任务完成中事件
     */
    @Async
    @EventListener
    public void onTaskCompleting(TaskCompletingEvent event) {
        log.info("Event received - Task completing: taskId={}, source={}", 
            event.getTaskId(), event.getSource());
    }
    
    /**
     * 任务已完成事件
     */
    @Async
    @EventListener
    public void onTaskCompleted(TaskCompletedEvent event) {
        log.info("Event received - Task completed: taskId={}, source={}", 
            event.getTaskId(), event.getSource());
    }
    
    /**
     * 任务失败中事件
     */
    @Async
    @EventListener
    public void onTaskFailing(TaskFailingEvent event) {
        log.warn("Event received - Task failing: taskId={}, source={}", 
            event.getTaskId(), event.getSource());
    }
    
    /**
     * 任务已失败事件
     */
    @Async
    @EventListener
    public void onTaskFailed(TaskFailedEvent event) {
        log.error("Event received - Task failed: taskId={}, source={}", 
            event.getTaskId(), event.getSource());
    }
    
    /**
     * 任务回滚中事件
     */
    @Async
    @EventListener
    public void onTaskRollingBack(TaskRollingBackEvent event) {
        log.warn("Event received - Task rolling back: taskId={}, source={}", 
            event.getTaskId(), event.getSource());
    }
    
    /**
     * 任务回滚完成事件
     */
    @Async
    @EventListener
    public void onTaskRollbackComplete(TaskRollbackCompleteEvent event) {
        log.info("Event received - Task rollback complete: taskId={}, source={}", 
            event.getTaskId(), event.getSource());
    }
}
