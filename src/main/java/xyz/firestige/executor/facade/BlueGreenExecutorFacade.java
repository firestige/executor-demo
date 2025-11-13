package xyz.firestige.executor.facade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import xyz.firestige.executor.api.dto.ExecutionOrder;
import xyz.firestige.executor.domain.Task;
import xyz.firestige.executor.manager.TaskManager;

/**
 * 蓝绿环境切换执行器门面
 * 对外封装蓝绿环境切换的执行细节，只暴露核心能力：
 * - 创建切换任务
 * - 停止任务
 * - 重试任务
 * - 暂停任务
 * - 回滚任务
 */
@Service
public class BlueGreenExecutorFacade {
    
    private static final Logger log = LoggerFactory.getLogger(BlueGreenExecutorFacade.class);
    
    private final TaskManager taskManager;
    
    public BlueGreenExecutorFacade(TaskManager taskManager) {
        this.taskManager = taskManager;
    }
    
    /**
     * 创建蓝绿环境切换任务
     * 
     * @param executionOrder 执行单，包含切换任务的所有输入信息
     * @return 任务ID，用于后续操作和查询
     */
    public String createTask(ExecutionOrder executionOrder) {
        log.info("Creating blue-green switch task: targetEnv={}, tenants={}, services={}", 
            executionOrder.getTargetEnvironment(),
            executionOrder.getTenantIds().size(),
            executionOrder.getServiceConfigs().size());
        
        String taskId = taskManager.createTask(executionOrder);
        
        log.info("Blue-green switch task created: taskId={}", taskId);
        
        return taskId;
    }
    
    /**
     * 停止任务
     * 
     * @param taskId 任务ID
     */
    public void stopTask(String taskId) {
        log.info("Stopping task: taskId={}", taskId);
        
        taskManager.stopTask(taskId);
        
        log.info("Task stop requested: taskId={}", taskId);
    }
    
    /**
     * 重试任务（仅支持失败状态的任务）
     * 
     * @param taskId 任务ID
     */
    public void retryTask(String taskId) {
        log.info("Retrying task: taskId={}", taskId);
        
        taskManager.retryTask(taskId);
        
        log.info("Task retry initiated: taskId={}", taskId);
    }
    
    /**
     * 暂停任务（仅支持运行中的任务）
     * 
     * @param taskId 任务ID
     */
    public void pauseTask(String taskId) {
        log.info("Pausing task: taskId={}", taskId);
        
        taskManager.pauseTask(taskId);
        
        log.info("Task pause requested: taskId={}", taskId);
    }
    
    /**
     * 恢复任务（从暂停状态恢复）
     * 
     * @param taskId 任务ID
     */
    public void resumeTask(String taskId) {
        log.info("Resuming task: taskId={}", taskId);
        
        taskManager.resumeTask(taskId);
        
        log.info("Task resume initiated: taskId={}", taskId);
    }
    
    /**
     * 回滚任务
     * 
     * @param taskId 要回滚的任务ID
     * @return 回滚任务ID
     */
    public String rollbackTask(String taskId) {
        log.info("Rolling back task: taskId={}", taskId);
        
        String rollbackTaskId = taskManager.rollbackTask(taskId);
        
        log.info("Rollback task created: rollbackTaskId={}, originalTaskId={}", 
            rollbackTaskId, taskId);
        
        return rollbackTaskId;
    }
    
    /**
     * 查询任务信息
     * 
     * @param taskId 任务ID
     * @return 任务对象
     */
    public Task getTask(String taskId) {
        return taskManager.getTask(taskId);
    }
    
    /**
     * 检查任务是否存在
     * 
     * @param taskId 任务ID
     * @return true 如果任务存在
     */
    public boolean taskExists(String taskId) {
        return taskManager.exists(taskId);
    }
}
