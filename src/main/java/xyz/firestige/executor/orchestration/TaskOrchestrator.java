package xyz.firestige.executor.orchestration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.exception.TaskNotFoundException;
import xyz.firestige.executor.execution.TenantTaskExecutor;
import xyz.firestige.executor.state.TaskStateManager;
import xyz.firestige.executor.validation.validator.ConflictValidator;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * 任务编排器
 * 管理执行单的创建、调度和控制
 */
@Deprecated
public class TaskOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(TaskOrchestrator.class);

    /**
     * 执行单调度器
     */
    private final ExecutionUnitScheduler scheduler;

    /**
     * 状态管理器
     */
    private final TaskStateManager stateManager;

    /**
     * 执行单存储
     * Key: executionUnitId, Value: ExecutionUnit
     */
    private final Map<String, ExecutionUnit> executionUnits = new ConcurrentHashMap<>();

    /**
     * 执行单 Future 存储
     * Key: executionUnitId, Value: Future
     */
    private final Map<String, Future<ExecutionUnitResult>> executionFutures = new ConcurrentHashMap<>();

    /**
     * 租户到执行单的映射
     * Key: tenantId, Value: executionUnitId
     */
    private final Map<String, String> tenantToExecutionUnit = new ConcurrentHashMap<>();

    /**
     * 计划到执行单列表的映射
     * Key: planId, Value: List of executionUnitIds
     */
    private final Map<Long, List<String>> planToExecutionUnits = new ConcurrentHashMap<>();

    /**
     * 租户任务执行器存储
     * Key: taskId (executionUnitId_tenantId), Value: TenantTaskExecutor
     */
    private final Map<String, TenantTaskExecutor> tenantExecutors = new ConcurrentHashMap<>();

    public TaskOrchestrator(ExecutionUnitScheduler scheduler, TaskStateManager stateManager) {
        this.scheduler = scheduler;
        this.stateManager = stateManager;
    }

    /**
     * 提交执行单列表
     */
    public List<String> submitExecutionUnits(List<ExecutionUnit> units) {
        logger.info("提交执行单，数量: {}", units.size());

        List<String> executionUnitIds = new ArrayList<>();

        for (ExecutionUnit unit : units) {
            // 检查租户冲突
            for (TenantDeployConfig config : unit.getTenantConfigs()) {
                String tenantId = config.getTenantId();
                if (ConflictValidator.isRunning(tenantId)) {
                    String existingTaskId = ConflictValidator.getRunningTaskId(tenantId);
                    logger.error("租户冲突: tenantId={}, 已在任务 {} 中执行", tenantId, existingTaskId);
                    throw new IllegalStateException("租户 " + tenantId + " 已在任务 " + existingTaskId + " 中执行");
                }
            }

            // 注册租户为运行中
            for (TenantDeployConfig config : unit.getTenantConfigs()) {
                ConflictValidator.registerRunningTenant(config.getTenantId(), unit.getId());
            }

            // 存储执行单
            executionUnits.put(unit.getId(), unit);
            executionUnitIds.add(unit.getId());

            // 建立租户到执行单的映射
            for (TenantDeployConfig config : unit.getTenantConfigs()) {
                tenantToExecutionUnit.put(config.getTenantId(), unit.getId());
            }

            // 建立计划到执行单的映射
            if (unit.getPlanId() != null) {
                planToExecutionUnits.computeIfAbsent(unit.getPlanId(), k -> new ArrayList<>())
                        .add(unit.getId());
            }

            // 调度执行单
            Future<ExecutionUnitResult> future = scheduler.schedule(unit);
            executionFutures.put(unit.getId(), future);

            logger.info("执行单已调度: id={}", unit.getId());
        }

        return executionUnitIds;
    }

    /**
     * 根据租户 ID 暂停任务
     */
    public void pauseByTenant(String tenantId) {
        logger.info("暂停租户任务: tenantId={}", tenantId);

        String executionUnitId = tenantToExecutionUnit.get(tenantId);
        if (executionUnitId == null) {
            throw new TaskNotFoundException("未找到租户的执行单: tenantId=" + tenantId);
        }

        ExecutionUnit unit = executionUnits.get(executionUnitId);
        if (unit == null) {
            throw new TaskNotFoundException("执行单不存在: id=" + executionUnitId);
        }

        // 暂停该执行单中的所有租户任务
        pauseExecutionUnit(unit);
    }

    /**
     * 根据计划 ID 暂停任务
     */
    public void pauseByPlan(Long planId) {
        logger.info("暂停计划任务: planId={}", planId);

        List<String> executionUnitIds = planToExecutionUnits.get(planId);
        if (executionUnitIds == null || executionUnitIds.isEmpty()) {
            throw new TaskNotFoundException("未找到计划的执行单: planId=" + planId);
        }

        for (String executionUnitId : executionUnitIds) {
            ExecutionUnit unit = executionUnits.get(executionUnitId);
            if (unit != null) {
                pauseExecutionUnit(unit);
            }
        }
    }

    /**
     * 暂停执行单
     */
    private void pauseExecutionUnit(ExecutionUnit unit) {
        logger.info("暂停执行单: id={}", unit.getId());

        unit.markAsPaused();

        // 暂停该执行单中的所有租户任务
        for (TenantDeployConfig config : unit.getTenantConfigs()) {
            String taskId = unit.getId() + "_" + config.getTenantId();
            TenantTaskExecutor executor = tenantExecutors.get(taskId);
            if (executor != null) {
                executor.pause();
            }
        }
    }

    /**
     * 根据租户 ID 恢复任务
     */
    public void resumeByTenant(String tenantId) {
        logger.info("恢复租户任务: tenantId={}", tenantId);

        String executionUnitId = tenantToExecutionUnit.get(tenantId);
        if (executionUnitId == null) {
            throw new TaskNotFoundException("未找到租户的执行单: tenantId=" + tenantId);
        }

        ExecutionUnit unit = executionUnits.get(executionUnitId);
        if (unit == null) {
            throw new TaskNotFoundException("执行单不存在: id=" + executionUnitId);
        }

        resumeExecutionUnit(unit);
    }

    /**
     * 根据计划 ID 恢复任务
     */
    public void resumeByPlan(Long planId) {
        logger.info("恢复计划任务: planId={}", planId);

        List<String> executionUnitIds = planToExecutionUnits.get(planId);
        if (executionUnitIds == null || executionUnitIds.isEmpty()) {
            throw new TaskNotFoundException("未找到计划的执行单: planId=" + planId);
        }

        for (String executionUnitId : executionUnitIds) {
            ExecutionUnit unit = executionUnits.get(executionUnitId);
            if (unit != null) {
                resumeExecutionUnit(unit);
            }
        }
    }

    /**
     * 恢复执行单
     */
    private void resumeExecutionUnit(ExecutionUnit unit) {
        logger.info("恢复执行单: id={}", unit.getId());

        unit.markAsRunning();

        // 恢复该执行单中的所有租户任务
        for (TenantDeployConfig config : unit.getTenantConfigs()) {
            String taskId = unit.getId() + "_" + config.getTenantId();
            TenantTaskExecutor executor = tenantExecutors.get(taskId);
            if (executor != null) {
                executor.resume();
            }
        }
    }

    /**
     * 根据租户 ID 回滚任务
     */
    public void rollbackByTenant(String tenantId) {
        logger.info("回滚租户任务: tenantId={}", tenantId);

        String executionUnitId = tenantToExecutionUnit.get(tenantId);
        if (executionUnitId == null) {
            throw new TaskNotFoundException("未找到租户的执行单: tenantId=" + tenantId);
        }

        ExecutionUnit unit = executionUnits.get(executionUnitId);
        if (unit == null) {
            throw new TaskNotFoundException("执行单不存在: id=" + executionUnitId);
        }

        rollbackExecutionUnit(unit);
    }

    /**
     * 根据计划 ID 回滚任务
     */
    public void rollbackByPlan(Long planId) {
        logger.info("回滚计划任务: planId={}", planId);

        List<String> executionUnitIds = planToExecutionUnits.get(planId);
        if (executionUnitIds == null || executionUnitIds.isEmpty()) {
            throw new TaskNotFoundException("未找到计划的执行单: planId=" + planId);
        }

        for (String executionUnitId : executionUnitIds) {
            ExecutionUnit unit = executionUnits.get(executionUnitId);
            if (unit != null) {
                rollbackExecutionUnit(unit);
            }
        }
    }

    /**
     * 回滚执行单
     */
    private void rollbackExecutionUnit(ExecutionUnit unit) {
        logger.info("回滚执行单: id={}", unit.getId());

        // 回滚该执行单中的所有租户任务
        for (TenantDeployConfig config : unit.getTenantConfigs()) {
            String taskId = unit.getId() + "_" + config.getTenantId();
            TenantTaskExecutor executor = tenantExecutors.get(taskId);
            if (executor != null) {
                try {
                    executor.rollback();
                } catch (Exception e) {
                    logger.error("租户任务回滚失败: taskId={}", taskId, e);
                }
            }
        }
    }

    /**
     * 根据租户 ID 重试任务
     */
    public void retryByTenant(String tenantId, boolean fromCheckpoint) {
        logger.info("重试租户任务: tenantId={}, fromCheckpoint={}", tenantId, fromCheckpoint);

        String executionUnitId = tenantToExecutionUnit.get(tenantId);
        if (executionUnitId == null) {
            throw new TaskNotFoundException("未找到租户的执行单: tenantId=" + tenantId);
        }

        String taskId = executionUnitId + "_" + tenantId;
        TenantTaskExecutor executor = tenantExecutors.get(taskId);
        if (executor != null) {
            executor.retry(fromCheckpoint);
        }
    }

    /**
     * 根据计划 ID 重试任务
     */
    public void retryByPlan(Long planId, boolean fromCheckpoint) {
        logger.info("重试计划任务: planId={}, fromCheckpoint={}", planId, fromCheckpoint);

        List<String> executionUnitIds = planToExecutionUnits.get(planId);
        if (executionUnitIds == null || executionUnitIds.isEmpty()) {
            throw new TaskNotFoundException("未找到计划的执行单: planId=" + planId);
        }

        for (String executionUnitId : executionUnitIds) {
            ExecutionUnit unit = executionUnits.get(executionUnitId);
            if (unit != null) {
                for (TenantDeployConfig config : unit.getTenantConfigs()) {
                    String taskId = executionUnitId + "_" + config.getTenantId();
                    TenantTaskExecutor executor = tenantExecutors.get(taskId);
                    if (executor != null) {
                        executor.retry(fromCheckpoint);
                    }
                }
            }
        }
    }

    /**
     * 注册租户任务执行器（供调度器使用）
     */
    public void registerTenantExecutor(String taskId, TenantTaskExecutor executor) {
        tenantExecutors.put(taskId, executor);
    }

    /**
     * 获取执行单
     */
    public ExecutionUnit getExecutionUnit(String executionUnitId) {
        return executionUnits.get(executionUnitId);
    }

    /**
     * 根据租户 ID 查找执行单
     */
    public ExecutionUnit findExecutionUnitByTenant(String tenantId) {
        String executionUnitId = tenantToExecutionUnit.get(tenantId);
        return executionUnitId != null ? executionUnits.get(executionUnitId) : null;
    }

    /**
     * 根据计划 ID 查找执行单列表
     */
    public List<ExecutionUnit> findExecutionUnitsByPlan(Long planId) {
        List<String> executionUnitIds = planToExecutionUnits.get(planId);
        if (executionUnitIds == null || executionUnitIds.isEmpty()) {
            return new ArrayList<>();
        }

        return executionUnitIds.stream()
                .map(executionUnits::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 清理已完成的执行单
     */
    public void cleanupCompletedUnits() {
        logger.info("清理已完成的执行单");

        List<String> toRemove = new ArrayList<>();

        for (Map.Entry<String, ExecutionUnit> entry : executionUnits.entrySet()) {
            ExecutionUnit unit = entry.getValue();
            if (unit.getStatus().isTerminal()) {
                toRemove.add(entry.getKey());

                // 清理租户映射
                for (TenantDeployConfig config : unit.getTenantConfigs()) {
                    tenantToExecutionUnit.remove(config.getTenantId());
                    ConflictValidator.unregisterRunningTenant(config.getTenantId());
                }
            }
        }

        for (String id : toRemove) {
            executionUnits.remove(id);
            executionFutures.remove(id);
        }

        logger.info("清理完成，移除执行单数量: {}", toRemove.size());
    }

    /**
     * 获取所有执行单
     */
    public List<ExecutionUnit> getAllExecutionUnits() {
        return new ArrayList<>(executionUnits.values());
    }

    /**
     * 获取执行单数量
     */
    public int getExecutionUnitCount() {
        return executionUnits.size();
    }
}

