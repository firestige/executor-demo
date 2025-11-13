package xyz.firestige.executor.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import xyz.firestige.executor.api.dto.ExecutionOrder;
import xyz.firestige.executor.api.dto.ServiceConfig;
import xyz.firestige.executor.deployer.ConfigDeployer;
import xyz.firestige.executor.domain.Checkpoint;
import xyz.firestige.executor.domain.Task;
import xyz.firestige.executor.domain.TaskContext;
import xyz.firestige.executor.domain.TaskStatus;
import xyz.firestige.executor.exception.ConfigDeployException;
import xyz.firestige.executor.exception.ConfigProcessException;
import xyz.firestige.executor.exception.TaskExecutionException;
import xyz.firestige.executor.manager.CheckpointManager;
import xyz.firestige.executor.manager.TaskStateManager;
import xyz.firestige.executor.processor.ConfigProcessorChain;
import xyz.firestige.executor.processor.ConfigProcessorRegistry;
import xyz.firestige.executor.strategy.DeployStrategyExecutor;

/**
 * 执行引擎
 * 核心协调者，编排整个执行流程：处理配置→部署配置→状态管理→检查点记录
 */
@Component
public class ExecutionEngine {
    
    private static final Logger log = LoggerFactory.getLogger(ExecutionEngine.class);
    
    private final TaskStateManager stateManager;
    private final CheckpointManager checkpointManager;
    private final ConfigProcessorRegistry processorRegistry;
    private final DeployStrategyExecutor strategyExecutor;
    
    public ExecutionEngine(TaskStateManager stateManager,
                          CheckpointManager checkpointManager,
                          ConfigProcessorRegistry processorRegistry,
                          DeployStrategyExecutor strategyExecutor) {
        this.stateManager = stateManager;
        this.checkpointManager = checkpointManager;
        this.processorRegistry = processorRegistry;
        this.strategyExecutor = strategyExecutor;
    }
    
    /**
     * 异步执行任务
     * 
     * @param task 任务对象
     * @return CompletableFuture
     */
    @Async
    public CompletableFuture<Void> executeAsync(Task task) {
        return CompletableFuture.runAsync(() -> execute(task));
    }
    
    /**
     * 执行任务（同步版本）
     * 
     * @param task 任务对象
     */
    public void execute(Task task) {
        String taskId = task.getTaskId();
        log.info("Starting task execution: taskId={}", taskId);
        
        try {
            // 转换为 RUNNING 状态
            stateManager.changeState(task, TaskStatus.RUNNING);
            task.setStartTime(java.time.LocalDateTime.now());
            
            // 获取执行单
            ExecutionOrder order = (ExecutionOrder) task.getExecutionOrder();
            
            // 检查是否从检查点恢复
            Checkpoint checkpoint = checkpointManager.getLatestCheckpoint(task);
            int startServiceIndex = checkpoint != null ? checkpoint.getCurrentServiceIndex() : 0;
            
            if (checkpoint != null) {
                log.info("Resuming task from checkpoint: taskId={}, serviceIndex={}", 
                    taskId, startServiceIndex);
            }
            
            // 按顺序执行每个服务的配置切换
            List<ServiceConfig> services = order.getServiceConfigs();
            for (int i = startServiceIndex; i < services.size(); i++) {
                // 检查暂停/停止信号
                if (checkControlSignals(task)) {
                    return;
                }
                
                ServiceConfig serviceConfig = services.get(i);
                executeService(task, serviceConfig, i, order.getTenantIds());
                
                // 保存检查点
                saveCheckpoint(task, serviceConfig.getServiceId(), i, order.getTenantIds());
            }
            
            // 任务完成
            stateManager.changeState(task, TaskStatus.COMPLETED);
            task.setEndTime(java.time.LocalDateTime.now());
            
            log.info("Task execution completed: taskId={}", taskId);
            
        } catch (Exception e) {
            log.error("Task execution failed: taskId={}", taskId, e);
            handleExecutionFailure(task, e);
        }
    }
    
    /**
     * 从检查点恢复执行
     * 
     * @param task 任务对象
     */
    public void resumeFromCheckpoint(Task task) {
        log.info("Resuming task from checkpoint: taskId={}", task.getTaskId());
        execute(task);
    }
    
    /**
     * 执行单个服务的配置切换
     */
    private void executeService(Task task, ServiceConfig serviceConfig, int serviceIndex, List<String> tenantIds) {
        String taskId = task.getTaskId();
        String serviceId = serviceConfig.getServiceId();
        
        log.info("Executing service: taskId={}, serviceId={}, serviceIndex={}", 
            taskId, serviceId, serviceIndex);
        
        try {
            // 1. 配置处理阶段 - 为每个租户处理配置
            Map<String, Map<String, Object>> processedConfigs = new HashMap<>();
            for (String tenantId : tenantIds) {
                Map<String, Object> processedConfig = processConfig(serviceConfig, tenantId);
                processedConfigs.put(tenantId, processedConfig);
            }
            
            // 2. 配置部署阶段
            deployConfig(task, serviceConfig, processedConfigs, tenantIds);
            
            log.info("Service execution completed: taskId={}, serviceId={}", taskId, serviceId);
            
        } catch (ConfigProcessException e) {
            log.error("Config processing failed: taskId={}, serviceId={}", taskId, serviceId, e);
            throw new TaskExecutionException(taskId, "Config processing failed for service: " + serviceId, e);
        } catch (ConfigDeployException e) {
            log.error("Config deployment failed: taskId={}, serviceId={}", taskId, serviceId, e);
            throw new TaskExecutionException(taskId, "Config deployment failed for service: " + serviceId, e);
        }
    }
    
    /**
     * 处理配置（通过处理器链）
     */
    private Map<String, Object> processConfig(ServiceConfig serviceConfig, String tenantId) {
        String serviceId = serviceConfig.getServiceId();
        
        // 获取服务的处理器链
        ConfigProcessorChain chain = processorRegistry.getProcessorChain(serviceId);
        
        // 执行处理器链
        Map<String, Object> rawConfig = serviceConfig.getConfigData();
        Map<String, Object> result = chain.process(serviceConfig, tenantId, rawConfig);
        
        log.debug("Config processed: serviceId={}, tenantId={}, processors={}", 
            serviceId, tenantId, chain.size());
        
        return result;
    }
    
    /**
     * 部署配置（通过部署器）
     */
    private void deployConfig(Task task, ServiceConfig serviceConfig, 
                             Map<String, Map<String, Object>> processedConfigs, List<String> tenantIds) {
        String serviceId = serviceConfig.getServiceId();
        
        // 获取部署器
        ConfigDeployer deployer = getDeployer(serviceConfig);
        
        // 使用策略执行器部署配置
        Map<String, Boolean> results = strategyExecutor.execute(
            serviceConfig,
            tenantIds,
            processedConfigs,
            deployer,
            serviceConfig.getDeployStrategy()
        );
        
        // 检查部署结果
        boolean allSuccess = results.values().stream().allMatch(Boolean::booleanValue);
        if (!allSuccess) {
            List<String> failedTenants = results.entrySet().stream()
                .filter(e -> !e.getValue())
                .map(Map.Entry::getKey)
                .toList();
            
            throw new ConfigDeployException(task.getTaskId(), 
                new RuntimeException("Config deployment failed for service: " + serviceId + 
                    ", failed tenants: " + failedTenants));
        }
        
        log.info("Config deployed: serviceId={}, tenants={}", serviceId, tenantIds.size());
    }
    
    /**
     * 获取配置部署器
     * TODO: 实现部署器选择逻辑
     * 可以通过以下方式实现：
     * 1. 创建 DeployerRegistry 注册表
     * 2. 通过 Spring 注入 Map<String, ConfigDeployer>
     * 3. 根据 serviceConfig.getDeployerType() 选择对应的部署器
     */
    private ConfigDeployer getDeployer(ServiceConfig serviceConfig) {
        // 暂时抛出异常，提示需要实现
        throw new UnsupportedOperationException(
            "Deployer selection not implemented yet for service: " + serviceConfig.getServiceId());
    }
    
    /**
     * 保存检查点
     */
    private void saveCheckpoint(Task task, String serviceId, int serviceIndex, List<String> tenantIds) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("phase", "service_completed");
        metadata.put("timestamp", java.time.LocalDateTime.now());
        
        checkpointManager.saveCheckpoint(task, serviceId, serviceIndex, 
            new ArrayList<>(tenantIds), metadata);
    }
    
    /**
     * 检查控制信号（暂停/停止）
     * 
     * @return true 如果任务被暂停或停止
     */
    private boolean checkControlSignals(Task task) {
        TaskContext context = task.getContext();
        if (context == null) {
            return false;
        }
        
        String taskId = task.getTaskId();
        
        // 检查暂停信号
        if (context.isPauseRequested()) {
            log.info("Pause signal detected: taskId={}", taskId);
            stateManager.changeState(task, TaskStatus.PAUSED);
            context.resetControlSignal();
            return true;
        }
        
        // 检查停止信号
        if (context.isStopRequested()) {
            log.info("Stop signal detected: taskId={}", taskId);
            stateManager.changeState(task, TaskStatus.STOPPED);
            task.setEndTime(java.time.LocalDateTime.now());
            context.resetControlSignal();
            return true;
        }
        
        return false;
    }
    
    /**
     * 处理执行失败
     */
    private void handleExecutionFailure(Task task, Exception exception) {
        try {
            stateManager.changeState(task, TaskStatus.FAILED);
            task.setEndTime(java.time.LocalDateTime.now());
            
            // 记录失败信息到任务上下文
            TaskContext context = task.getContext();
            if (context != null) {
                context.putExecutionContext("failureReason", exception.getMessage());
                context.putExecutionContext("failureTime", java.time.LocalDateTime.now());
            }
            
        } catch (Exception e) {
            log.error("Failed to handle execution failure: taskId={}", task.getTaskId(), e);
        }
    }
}
