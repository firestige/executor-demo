package xyz.firestige.executor.facade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.dto.TenantDeployConfig;
import xyz.firestige.executor.exception.ErrorType;
import xyz.firestige.executor.exception.FailureInfo;
import xyz.firestige.executor.exception.TaskNotFoundException;
import xyz.firestige.executor.orchestration.ExecutionMode;
import xyz.firestige.executor.orchestration.ExecutionUnit;
import xyz.firestige.executor.orchestration.TaskOrchestrator;
import xyz.firestige.executor.state.TaskStateManager;
import xyz.firestige.executor.state.TaskStatus;
import xyz.firestige.executor.validation.ValidationChain;
import xyz.firestige.executor.validation.ValidationSummary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 部署任务 Facade 实现
 */
public class DeploymentTaskFacadeImpl implements DeploymentTaskFacade {

    private static final Logger logger = LoggerFactory.getLogger(DeploymentTaskFacadeImpl.class);

    /**
     * 校验链
     */
    private final ValidationChain validationChain;

    /**
     * 任务编排器
     */
    private final TaskOrchestrator taskOrchestrator;

    /**
     * 状态管理器
     */
    private final TaskStateManager stateManager;

    /**
     * 默认执行模式
     */
    private ExecutionMode defaultExecutionMode = ExecutionMode.CONCURRENT;

    public DeploymentTaskFacadeImpl(ValidationChain validationChain,
                                   TaskOrchestrator taskOrchestrator,
                                   TaskStateManager stateManager) {
        this.validationChain = validationChain;
        this.taskOrchestrator = taskOrchestrator;
        this.stateManager = stateManager;
    }

    @Override
    public TaskCreationResult createSwitchTask(List<TenantDeployConfig> configs) {
        logger.info("开始创建切换任务，配置数量: {}", configs != null ? configs.size() : 0);

        try {
            // Step 1: 校验配置
            if (configs == null || configs.isEmpty()) {
                FailureInfo failureInfo = FailureInfo.of(
                        ErrorType.VALIDATION_ERROR,
                        "配置列表不能为空"
                );
                return TaskCreationResult.failure(failureInfo, "配置列表为空");
            }

            // 发布任务创建事件
            String taskId = generateTaskId();
            stateManager.initializeTask(taskId, TaskStatus.CREATED);
            stateManager.publishTaskCreatedEvent(taskId, configs.size());

            // 开始校验
            stateManager.updateState(taskId, TaskStatus.VALIDATING);

            ValidationSummary validationSummary = validationChain.validateAll(configs);

            if (validationSummary.hasErrors()) {
                logger.error("配置校验失败，错误数量: {}", validationSummary.getAllErrors().size());

                FailureInfo failureInfo = FailureInfo.of(
                        ErrorType.VALIDATION_ERROR,
                        "配置校验失败，有 " + validationSummary.getInvalidCount() + " 个无效配置"
                );

                // 发布校验失败事件
                stateManager.publishTaskValidationFailedEvent(taskId, failureInfo, validationSummary.getAllErrors());
                stateManager.updateState(taskId, TaskStatus.VALIDATION_FAILED, failureInfo);

                TaskCreationResult result = TaskCreationResult.validationFailure(validationSummary);
                result.setTaskId(taskId);
                return result;
            }

            logger.info("配置校验通过，有效配置数量: {}", validationSummary.getValidCount());

            // 发布校验通过事件
            stateManager.publishTaskValidatedEvent(taskId, validationSummary.getValidCount());
            stateManager.updateState(taskId, TaskStatus.PENDING);

            // Step 2: 创建执行单
            List<ExecutionUnit> executionUnits = createExecutionUnits(
                    validationSummary.getValidConfigs(),
                    defaultExecutionMode
            );

            logger.info("创建执行单数量: {}", executionUnits.size());

            // Step 3: 提交执行单到编排器
            List<String> executionUnitIds = taskOrchestrator.submitExecutionUnits(executionUnits);

            logger.info("任务创建成功: taskId={}, executionUnitCount={}", taskId, executionUnitIds.size());

            return TaskCreationResult.success(taskId, executionUnitIds);

        } catch (IllegalStateException e) {
            // 租户冲突异常
            logger.error("任务创建失败: 租户冲突", e);

            FailureInfo failureInfo = FailureInfo.of(
                    ErrorType.VALIDATION_ERROR,
                    e.getMessage()
            );

            return TaskCreationResult.failure(failureInfo, "租户冲突");

        } catch (Exception e) {
            logger.error("任务创建失败", e);

            FailureInfo failureInfo = FailureInfo.fromException(
                    e,
                    ErrorType.SYSTEM_ERROR,
                    "createSwitchTask"
            );

            return TaskCreationResult.failure(failureInfo, "任务创建失败: " + e.getMessage());
        }
    }

    @Override
    public TaskOperationResult pauseTaskByTenant(String tenantId) {
        logger.info("暂停租户任务: tenantId={}", tenantId);

        try {
            taskOrchestrator.pauseByTenant(tenantId);

            ExecutionUnit unit = taskOrchestrator.findExecutionUnitByTenant(tenantId);

            return TaskOperationResult.success(
                    unit != null ? unit.getId() : null,
                    TaskStatus.PAUSED,
                    "租户任务已暂停"
            );

        } catch (TaskNotFoundException e) {
            logger.error("暂停任务失败: 任务不存在", e);
            return TaskOperationResult.failure(e.getMessage());

        } catch (Exception e) {
            logger.error("暂停任务失败", e);
            FailureInfo failureInfo = FailureInfo.fromException(e, ErrorType.SYSTEM_ERROR, "pauseTask");
            return TaskOperationResult.failure(null, failureInfo, "暂停任务失败");
        }
    }

    @Override
    public TaskOperationResult pauseTaskByPlan(Long planId) {
        logger.info("暂停计划任务: planId={}", planId);

        try {
            taskOrchestrator.pauseByPlan(planId);

            return TaskOperationResult.success(
                    null,
                    TaskStatus.PAUSED,
                    "计划任务已暂停"
            );

        } catch (TaskNotFoundException e) {
            logger.error("暂停任务失败: 任务不存在", e);
            return TaskOperationResult.failure(e.getMessage());

        } catch (Exception e) {
            logger.error("暂停任务失败", e);
            FailureInfo failureInfo = FailureInfo.fromException(e, ErrorType.SYSTEM_ERROR, "pauseTask");
            return TaskOperationResult.failure(null, failureInfo, "暂停任务失败");
        }
    }

    @Override
    public TaskOperationResult resumeTaskByTenant(String tenantId) {
        logger.info("恢复租户任务: tenantId={}", tenantId);

        try {
            taskOrchestrator.resumeByTenant(tenantId);

            ExecutionUnit unit = taskOrchestrator.findExecutionUnitByTenant(tenantId);

            return TaskOperationResult.success(
                    unit != null ? unit.getId() : null,
                    TaskStatus.RUNNING,
                    "租户任务已恢复"
            );

        } catch (TaskNotFoundException e) {
            logger.error("恢复任务失败: 任务不存在", e);
            return TaskOperationResult.failure(e.getMessage());

        } catch (Exception e) {
            logger.error("恢复任务失败", e);
            FailureInfo failureInfo = FailureInfo.fromException(e, ErrorType.SYSTEM_ERROR, "resumeTask");
            return TaskOperationResult.failure(null, failureInfo, "恢复任务失败");
        }
    }

    @Override
    public TaskOperationResult resumeTaskByPlan(Long planId) {
        logger.info("恢复计划任务: planId={}", planId);

        try {
            taskOrchestrator.resumeByPlan(planId);

            return TaskOperationResult.success(
                    null,
                    TaskStatus.RUNNING,
                    "计划任务已恢复"
            );

        } catch (TaskNotFoundException e) {
            logger.error("恢复任务失败: 任务不存在", e);
            return TaskOperationResult.failure(e.getMessage());

        } catch (Exception e) {
            logger.error("恢复任务失败", e);
            FailureInfo failureInfo = FailureInfo.fromException(e, ErrorType.SYSTEM_ERROR, "resumeTask");
            return TaskOperationResult.failure(null, failureInfo, "恢复任务失败");
        }
    }

    @Override
    public TaskOperationResult rollbackTaskByTenant(String tenantId) {
        logger.info("回滚租户任务: tenantId={}", tenantId);

        try {
            taskOrchestrator.rollbackByTenant(tenantId);

            ExecutionUnit unit = taskOrchestrator.findExecutionUnitByTenant(tenantId);

            return TaskOperationResult.success(
                    unit != null ? unit.getId() : null,
                    TaskStatus.ROLLED_BACK,
                    "租户任务已回滚"
            );

        } catch (TaskNotFoundException e) {
            logger.error("回滚任务失败: 任务不存在", e);
            return TaskOperationResult.failure(e.getMessage());

        } catch (Exception e) {
            logger.error("回滚任务失败", e);
            FailureInfo failureInfo = FailureInfo.fromException(e, ErrorType.SYSTEM_ERROR, "rollbackTask");
            return TaskOperationResult.failure(null, failureInfo, "回滚任务失败");
        }
    }

    @Override
    public TaskOperationResult rollbackTaskByPlan(Long planId) {
        logger.info("回滚计划任务: planId={}", planId);

        try {
            taskOrchestrator.rollbackByPlan(planId);

            return TaskOperationResult.success(
                    null,
                    TaskStatus.ROLLED_BACK,
                    "计划任务已回滚"
            );

        } catch (TaskNotFoundException e) {
            logger.error("回滚任务失败: 任务不存在", e);
            return TaskOperationResult.failure(e.getMessage());

        } catch (Exception e) {
            logger.error("回滚任务失败", e);
            FailureInfo failureInfo = FailureInfo.fromException(e, ErrorType.SYSTEM_ERROR, "rollbackTask");
            return TaskOperationResult.failure(null, failureInfo, "回滚任务失败");
        }
    }

    @Override
    public TaskOperationResult retryTaskByTenant(String tenantId, boolean fromCheckpoint) {
        logger.info("重试租户任务: tenantId={}, fromCheckpoint={}", tenantId, fromCheckpoint);

        try {
            taskOrchestrator.retryByTenant(tenantId, fromCheckpoint);

            ExecutionUnit unit = taskOrchestrator.findExecutionUnitByTenant(tenantId);

            return TaskOperationResult.success(
                    unit != null ? unit.getId() : null,
                    TaskStatus.RUNNING,
                    "租户任务已重试"
            );

        } catch (TaskNotFoundException e) {
            logger.error("重试任务失败: 任务不存在", e);
            return TaskOperationResult.failure(e.getMessage());

        } catch (Exception e) {
            logger.error("重试任务失败", e);
            FailureInfo failureInfo = FailureInfo.fromException(e, ErrorType.SYSTEM_ERROR, "retryTask");
            return TaskOperationResult.failure(null, failureInfo, "重试任务失败");
        }
    }

    @Override
    public TaskOperationResult retryTaskByPlan(Long planId, boolean fromCheckpoint) {
        logger.info("重试计划任务: planId={}, fromCheckpoint={}", planId, fromCheckpoint);

        try {
            taskOrchestrator.retryByPlan(planId, fromCheckpoint);

            return TaskOperationResult.success(
                    null,
                    TaskStatus.RUNNING,
                    "计划任务已重试"
            );

        } catch (TaskNotFoundException e) {
            logger.error("重试任务失败: 任务不存在", e);
            return TaskOperationResult.failure(e.getMessage());

        } catch (Exception e) {
            logger.error("重试任务失败", e);
            FailureInfo failureInfo = FailureInfo.fromException(e, ErrorType.SYSTEM_ERROR, "retryTask");
            return TaskOperationResult.failure(null, failureInfo, "重试任务失败");
        }
    }

    @Override
    public TaskStatusInfo queryTaskStatus(String executionUnitId) {
        logger.info("查询任务状态: executionUnitId={}", executionUnitId);

        try {
            ExecutionUnit unit = taskOrchestrator.getExecutionUnit(executionUnitId);

            if (unit == null) {
                throw new TaskNotFoundException("执行单不存在: " + executionUnitId);
            }

            TaskStatusInfo statusInfo = new TaskStatusInfo(executionUnitId, TaskStatus.RUNNING);
            statusInfo.setMessage("执行单状态: " + unit.getStatus().getDescription());

            return statusInfo;

        } catch (Exception e) {
            logger.error("查询任务状态失败", e);

            TaskStatusInfo statusInfo = new TaskStatusInfo(executionUnitId, null);
            statusInfo.setMessage("查询失败: " + e.getMessage());

            return statusInfo;
        }
    }

    @Override
    public TaskStatusInfo queryTaskStatusByTenant(String tenantId) {
        logger.info("查询租户任务状态: tenantId={}", tenantId);

        try {
            ExecutionUnit unit = taskOrchestrator.findExecutionUnitByTenant(tenantId);

            if (unit == null) {
                throw new TaskNotFoundException("未找到租户的执行单: " + tenantId);
            }

            return queryTaskStatus(unit.getId());

        } catch (Exception e) {
            logger.error("查询任务状态失败", e);

            TaskStatusInfo statusInfo = new TaskStatusInfo(null, null);
            statusInfo.setMessage("查询失败: " + e.getMessage());

            return statusInfo;
        }
    }

    @Override
    public TaskOperationResult cancelTask(String executionUnitId) {
        logger.info("取消任务: executionUnitId={}", executionUnitId);

        try {
            ExecutionUnit unit = taskOrchestrator.getExecutionUnit(executionUnitId);

            if (unit == null) {
                throw new TaskNotFoundException("执行单不存在: " + executionUnitId);
            }

            unit.markAsCancelled();

            return TaskOperationResult.success(
                    executionUnitId,
                    TaskStatus.CANCELLED,
                    "任务已取消"
            );

        } catch (TaskNotFoundException e) {
            logger.error("取消任务失败: 任务不存在", e);
            return TaskOperationResult.failure(e.getMessage());

        } catch (Exception e) {
            logger.error("取消任务失败", e);
            FailureInfo failureInfo = FailureInfo.fromException(e, ErrorType.SYSTEM_ERROR, "cancelTask");
            return TaskOperationResult.failure(executionUnitId, failureInfo, "取消任务失败");
        }
    }

    /**
     * 创建执行单
     * 根据配置列表创建执行单，这里简单实现为一个执行单包含所有配置
     */
    private List<ExecutionUnit> createExecutionUnits(List<TenantDeployConfig> configs, ExecutionMode executionMode) {
        List<ExecutionUnit> units = new ArrayList<>();

        // 按照 planId 分组
        Map<Long, List<TenantDeployConfig>> planGroups = new HashMap<>();

        for (TenantDeployConfig config : configs) {
            Long planId = config.getPlanId() != null ? config.getPlanId() : 0L;
            planGroups.computeIfAbsent(planId, k -> new ArrayList<>()).add(config);
        }

        // 为每个 planId 创建一个执行单
        for (Map.Entry<Long, List<TenantDeployConfig>> entry : planGroups.entrySet()) {
            Long planId = entry.getKey();
            List<TenantDeployConfig> groupConfigs = entry.getValue();

            ExecutionUnit unit = new ExecutionUnit(
                    planId != 0L ? planId : null,
                    groupConfigs,
                    executionMode
            );

            units.add(unit);
        }

        return units;
    }

    /**
     * 生成任务 ID
     */
    private String generateTaskId() {
        return "task_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }

    // Getters and Setters

    public ExecutionMode getDefaultExecutionMode() {
        return defaultExecutionMode;
    }

    public void setDefaultExecutionMode(ExecutionMode defaultExecutionMode) {
        this.defaultExecutionMode = defaultExecutionMode;
    }
}

