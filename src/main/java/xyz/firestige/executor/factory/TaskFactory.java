package xyz.firestige.executor.factory;

import org.springframework.stereotype.Component;

import xyz.firestige.executor.api.dto.ExecutionOrder;
import xyz.firestige.executor.domain.Task;
import xyz.firestige.executor.domain.TaskContext;

/**
 * 任务工厂
 * 负责创建Task对象
 */
@Component
public class TaskFactory {
    
    private final TaskIdFactory taskIdFactory;
    
    public TaskFactory(TaskIdFactory taskIdFactory) {
        this.taskIdFactory = taskIdFactory;
    }
    
    /**
     * 创建新任务
     * 
     * @param executionOrder 执行单
     * @return 新创建的任务对象
     */
    public Task createTask(ExecutionOrder executionOrder) {
        return createTask(executionOrder, null);
    }
    
    /**
     * 创建新任务
     * 
     * @param executionOrder 执行单
     * @param createdBy 创建者标识
     * @return 新创建的任务对象
     */
    public Task createTask(ExecutionOrder executionOrder, String createdBy) {
        String taskId = taskIdFactory.generateTaskId();
        Task task = new Task(taskId);
        task.setExecutionOrder(executionOrder);
        task.setCreatedBy(createdBy);
        
        // 初始化任务上下文
        TaskContext context = new TaskContext(taskId);
        task.setContext(context);
        
        return task;
    }
    
    /**
     * 创建回滚任务
     * 
     * @param originalTaskId 原任务ID
     * @param executionOrder 回滚执行单
     * @param createdBy 创建者标识
     * @return 新创建的回滚任务对象
     */
    public Task createRollbackTask(String originalTaskId, ExecutionOrder executionOrder, String createdBy) {
        Task task = createTask(executionOrder, createdBy);
        task.setOriginalTaskId(originalTaskId);
        return task;
    }
}
