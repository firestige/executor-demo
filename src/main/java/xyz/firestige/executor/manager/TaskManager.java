package xyz.firestige.executor.manager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import xyz.firestige.executor.api.dto.ExecutionOrder;
import xyz.firestige.executor.domain.Task;
import xyz.firestige.executor.domain.TaskContext;
import xyz.firestige.executor.domain.TaskStatus;
import xyz.firestige.executor.engine.ExecutionEngine;
import xyz.firestige.executor.exception.ExecutionOrderValidationException;
import xyz.firestige.executor.exception.IllegalStateTransitionException;
import xyz.firestige.executor.exception.TaskNotFoundException;
import xyz.firestige.executor.factory.TaskFactory;

/**
 * 任务管理器
 * 任务生命周期管理：创建、停止、暂停、重试、回滚，协调队列管理器和执行引擎
 */
@Component
public class TaskManager {
    
    private static final Logger log = LoggerFactory.getLogger(TaskManager.class);
    
    // 任务存储
    private final Map<String, Task> taskStore = new ConcurrentHashMap<>();
    
    private final TaskFactory taskFactory;
    private final TaskQueueManager queueManager;
    private final TaskStateManager stateManager;
    private final ExecutionEngine executionEngine;
    
    public TaskManager(TaskFactory taskFactory,
                      TaskQueueManager queueManager,
                      TaskStateManager stateManager,
                      ExecutionEngine executionEngine) {
        this.taskFactory = taskFactory;
        this.queueManager = queueManager;
        this.stateManager = stateManager;
        this.executionEngine = executionEngine;
    }
    
    /**
     * 创建任务
     * 
     * @param executionOrder 执行单
     * @return 任务ID
     */
    public String createTask(ExecutionOrder executionOrder) {
        // 验证执行单
        if (!executionOrder.isValid()) {
            throw new ExecutionOrderValidationException("Invalid execution order: " + executionOrder);
        }
        
        // 创建任务
        Task task = taskFactory.createTask(executionOrder);
        String taskId = task.getTaskId();
        
        // 设置租户ID（使用第一个租户ID作为主租户）
        if (!executionOrder.getTenantIds().isEmpty()) {
            task.setTenantId(executionOrder.getTenantIds().get(0));
        }
        
        // 保存任务
        taskStore.put(taskId, task);
        
        // 加入队列
        queueManager.enqueue(task);
        
        log.info("Task created: taskId={}, tenantId={}", taskId, task.getTenantId());
        
        // 尝试启动执行
        tryStartExecution(task);
        
        return taskId;
    }
    
    /**
     * 停止任务
     * 
     * @param taskId 任务ID
     */
    public void stopTask(String taskId) {
        Task task = getTask(taskId);
        
        if (!task.canStop()) {
            throw new IllegalStateTransitionException(taskId, task.getStatus(), TaskStatus.STOPPED);
        }
        
        log.info("Stopping task: taskId={}, currentStatus={}", taskId, task.getStatus());
        
        // 设置停止信号
        TaskContext context = task.getContext();
        if (context != null) {
            context.setControlSignal(TaskContext.ControlSignal.STOP);
        }
        
        // 如果任务在队列中但未执行，直接标记为停止
        if (task.getStatus() == TaskStatus.READY) {
            stateManager.changeState(task, TaskStatus.STOPPED);
            queueManager.dequeue(taskId);
        }
    }
    
    /**
     * 暂停任务
     * 
     * @param taskId 任务ID
     */
    public void pauseTask(String taskId) {
        Task task = getTask(taskId);
        
        if (!task.canPause()) {
            throw new IllegalStateTransitionException(taskId, task.getStatus(), TaskStatus.PAUSED);
        }
        
        log.info("Pausing task: taskId={}", taskId);
        
        // 设置暂停信号
        TaskContext context = task.getContext();
        if (context != null) {
            context.setControlSignal(TaskContext.ControlSignal.PAUSE);
        }
    }
    
    /**
     * 恢复任务（从暂停状态）
     * 
     * @param taskId 任务ID
     */
    public void resumeTask(String taskId) {
        Task task = getTask(taskId);
        
        if (!task.canResume()) {
            throw new IllegalStateTransitionException(taskId, task.getStatus(), TaskStatus.RUNNING);
        }
        
        log.info("Resuming task: taskId={}", taskId);
        
        // 清除控制信号
        TaskContext context = task.getContext();
        if (context != null) {
            context.resetControlSignal();
        }
        
        // 从检查点恢复执行
        executionEngine.resumeFromCheckpoint(task);
    }
    
    /**
     * 重试任务（从失败状态）
     * 
     * @param taskId 任务ID
     */
    public void retryTask(String taskId) {
        Task task = getTask(taskId);
        
        if (task.getStatus() != TaskStatus.FAILED) {
            throw new IllegalStateTransitionException(taskId, task.getStatus(), TaskStatus.RUNNING);
        }
        
        log.info("Retrying task: taskId={}", taskId);
        
        // 清除错误信息
        TaskContext context = task.getContext();
        if (context != null) {
            context.setErrorMessage(null);
            context.setErrorStackTrace(null);
            context.resetControlSignal();
        }
        
        // 转换状态为 READY
        stateManager.changeState(task, TaskStatus.READY);
        
        // 重新加入队列
        queueManager.enqueue(task);
        
        // 尝试启动执行
        tryStartExecution(task);
    }
    
    /**
     * 回滚任务
     * 
     * @param taskId 任务ID
     * @return 回滚任务ID
     */
    public String rollbackTask(String taskId) {
        Task originalTask = getTask(taskId);
        
        log.info("Rolling back task: taskId={}", taskId);
        
        // 创建回滚任务（复制原任务的执行单，但标记为回滚任务）
        ExecutionOrder originalOrder = (ExecutionOrder) originalTask.getExecutionOrder();
        
        // 创建回滚任务
        Task rollbackTask = taskFactory.createTask(originalOrder);
        rollbackTask.setOriginalTaskId(taskId);
        rollbackTask.setTenantId(originalTask.getTenantId());
        
        String rollbackTaskId = rollbackTask.getTaskId();
        
        // 保存回滚任务
        taskStore.put(rollbackTaskId, rollbackTask);
        
        // 加入队列
        queueManager.enqueue(rollbackTask);
        
        // 转换原任务状态为 ROLLING_BACK
        stateManager.changeState(originalTask, TaskStatus.ROLLING_BACK);
        
        log.info("Rollback task created: rollbackTaskId={}, originalTaskId={}", 
            rollbackTaskId, taskId);
        
        // 尝试启动执行
        tryStartExecution(rollbackTask);
        
        return rollbackTaskId;
    }
    
    /**
     * 获取任务
     * 
     * @param taskId 任务ID
     * @return 任务对象
     */
    public Task getTask(String taskId) {
        Task task = taskStore.get(taskId);
        if (task == null) {
            throw new TaskNotFoundException(taskId);
        }
        return task;
    }
    
    /**
     * 查询任务是否存在
     * 
     * @param taskId 任务ID
     * @return true 如果存在
     */
    public boolean exists(String taskId) {
        return taskStore.containsKey(taskId);
    }
    
    /**
     * 删除任务
     * 
     * @param taskId 任务ID
     */
    public void removeTask(String taskId) {
        Task task = taskStore.remove(taskId);
        if (task != null) {
            queueManager.dequeue(taskId);
            log.info("Task removed: taskId={}", taskId);
        }
    }
    
    /**
     * 尝试启动任务执行
     * 检查租户队列，如果该租户没有正在执行的任务，则启动执行
     */
    private void tryStartExecution(Task task) {
        String tenantId = task.getTenantId();
        
        // 检查租户是否有任务正在执行
        if (queueManager.isRunning(tenantId)) {
            log.info("Tenant has running task, task queued: taskId={}, tenantId={}", 
                task.getTaskId(), tenantId);
            return;
        }
        
        // 检查是否是队列首任务
        String nextTaskId = queueManager.peekNext(tenantId);
        if (!task.getTaskId().equals(nextTaskId)) {
            log.info("Task is not next in queue: taskId={}, tenantId={}, nextTaskId={}", 
                task.getTaskId(), tenantId, nextTaskId);
            return;
        }
        
        // 标记为执行中
        queueManager.markRunning(task.getTaskId());
        
        // 异步执行任务
        executionEngine.executeAsync(task).whenComplete((result, throwable) -> {
            // 执行完成后清理
            queueManager.markCompleted(task.getTaskId());
            queueManager.dequeue(task.getTaskId());
            
            // 尝试启动下一个任务
            tryStartNextTask(tenantId);
        });
        
        log.info("Task execution started: taskId={}, tenantId={}", task.getTaskId(), tenantId);
    }
    
    /**
     * 尝试启动租户的下一个任务
     */
    private void tryStartNextTask(String tenantId) {
        String nextTaskId = queueManager.peekNext(tenantId);
        if (nextTaskId != null) {
            Task nextTask = taskStore.get(nextTaskId);
            if (nextTask != null && nextTask.getStatus() == TaskStatus.READY) {
                tryStartExecution(nextTask);
            }
        }
    }
}
