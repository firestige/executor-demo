package xyz.firestige.deploy.facade;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.deploy.application.lifecycle.PlanLifecycleService;
import xyz.firestige.deploy.application.task.TaskOperationService;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.domain.plan.PlanCreationResult;
import xyz.firestige.deploy.domain.plan.PlanInfo;
import xyz.firestige.deploy.domain.plan.PlanOperationResult;
import xyz.firestige.deploy.domain.task.TaskOperationResult;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.facade.converter.TenantConfigConverter;
import xyz.firestige.deploy.facade.exception.PlanNotFoundException;
import xyz.firestige.deploy.facade.exception.TaskCreationException;
import xyz.firestige.deploy.facade.exception.TaskNotFoundException;
import xyz.firestige.deploy.facade.exception.TaskOperationException;


import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 部署任务 Facade（RF-20: 服务拆分优化版）
 * <p>
 * 职责：
 * 1. DTO 转换：外部 DTO (TenantDeployConfig) → 内部 DTO（当前直接使用，后续可优化）
 * 2. 参数校验（快速失败）
 * 3. 调用应用服务（PlanLifecycleService, TaskOperationService）
 * 4. 异常转换：应用层 Result → Facade 异常
 * <p>
 * 设计说明：
 * - 不定义接口，直接使用具体类（符合 YAGNI 原则）
 * - 返回 void（查询操作除外），通过异常机制处理错误
 * - 保护应用层接口稳定，外部 DTO 变化不影响应用层
 * - RF-20: 拆分后依赖 PlanLifecycleService 和 TaskOperationService
 *
 * @since RF-20 - 服务拆分
 */
@Component
public class DeploymentTaskFacade {

    private static final Logger logger = LoggerFactory.getLogger(DeploymentTaskFacade.class);

    private final PlanLifecycleService planLifecycleService;
    private final TaskOperationService taskOperationService;
    private final TenantConfigConverter tenantConfigConverter;
    private final Validator validator;  // Jakarta Validator

    public DeploymentTaskFacade(
            PlanLifecycleService planLifecycleService,
            TaskOperationService taskOperationService,
            TenantConfigConverter tenantConfigConverter,
            Validator validator) {
        this.planLifecycleService = planLifecycleService;
        this.taskOperationService = taskOperationService;
        this.tenantConfigConverter = tenantConfigConverter;
        this.validator = validator;
    }

    /**
     * 创建切换任务
     */
    public void createSwitchTask(List<TenantDeployConfig> configs) {
        logger.info("[Facade] 创建切换任务，配置数量: {}", configs != null ? configs.size() : 0);

        // Step 1: 参数校验（快速失败）
        if (configs == null || configs.isEmpty()) {
            throw new IllegalArgumentException("配置列表不能为空");
        }

        // Step 2: DTO 转换：外部 DTO → 内部 DTO（防腐层职责）
        List<TenantConfig> internalConfigs = tenantConfigConverter.fromExternal(configs);

        // Step 3: 字段格式校验（对转换后的 TenantConfig 使用 Spring Validator）
        for (int i = 0; i < internalConfigs.size(); i++) {
            TenantConfig config = internalConfigs.get(i);
            Set<ConstraintViolation<TenantConfig>> violations = validator.validate(config);

            if (!violations.isEmpty()) {
                String errorDetail = violations.stream()
                        .map(v -> String.format("[%s] %s", v.getPropertyPath(), v.getMessage()))
                        .collect(Collectors.joining("; "));

                logger.warn("[Facade] TenantConfig 格式校验失败 (索引 {}): {}", i, errorDetail);
                throw new IllegalArgumentException("TenantConfig 格式校验失败: " + errorDetail);
            }
        }

        // Step 4: 调用应用服务（使用内部 DTO）
        // 应用服务内部会执行业务规则校验（BusinessValidator）
        PlanCreationResult result = planLifecycleService.createDeploymentPlan(internalConfigs);

        // Step 5: 处理结果
        if (!result.isSuccess()) {

            FailureInfo failureInfo = result.getFailureInfo();
            throw new TaskCreationException(
                failureInfo != null ? failureInfo.getErrorMessage() : "任务创建失败",
                failureInfo
            );
        }

        PlanInfo planInfo = result.getPlanInfo();
        logger.info("[Facade] Plan 创建成功，planId: {}, tasks: {}",
                    planInfo.getPlanId(), planInfo.getTasks().size());
    }

    /**
     * 回滚任务
     *
     * @param rollbackConfig 重试配置
     * @param taskId 任务 ID
     * @param lastCompletedStageName 最后完成的 Stage 名称
     * @param version 计划版本
     */
    public void rollbackTask(TenantDeployConfig rollbackConfig, String taskId, String lastCompletedStageName, Long version) {
        logger.info("[DeploymentTaskFacade] 回滚租户任务: {}, version: {}", rollbackConfig.getTenantId(), version);
        TenantConfig config = tenantConfigConverter.convert(rollbackConfig);
        TaskOperationResult result = taskOperationService.rollbackTask(config, taskId, lastCompletedStageName, version);  // T-015: 异步执行，监听领域事件
        handleTaskOperationResult(result, "回滚任务");
        logger.info("[Facade] 租户任务回滚成功: {}", rollbackConfig.getTenantId());
    }

    /**
     * 重试任务
     *
     * @param retryConfig 重试配置
     * @param taskId 任务 ID
     * @param lastCompletedStageName 最后完成的 Stage 名称
     */
    public void retryTask(TenantDeployConfig retryConfig, String taskId, String lastCompletedStageName) {
        logger.info("[DeploymentTaskFacade] 重试租户任务: {}, from: {}", retryConfig.getTenantId(), lastCompletedStageName);
        TenantConfig config = tenantConfigConverter.convert(retryConfig);
        TaskOperationResult result = taskOperationService.retryTaskByTenant(config, taskId, lastCompletedStageName);  // T-015: 异步执行，监听领域事件
        handleTaskOperationResult(result, "重试任务");
        logger.info("[Facade] 租户任务重试成功: {}", retryConfig.getTenantId());
    }

    /**
     * 查询任务状态
     */
    public TaskStatusInfo queryTaskStatus(String taskId) {
        logger.debug("[Facade] 查询任务状态: {}", taskId);
        TaskStatusInfo result = taskOperationService.queryTaskStatus(TaskId.of(taskId));
        if (result.getStatus() == null) {
            throw new TaskNotFoundException("任务不存在: " + taskId);
        }
        return result;
    }

    /**
     * 根据租户 ID 查询任务状态
     */
    public TaskStatusInfo queryTaskStatusByTenant(String tenantId) {
        logger.debug("[Facade] 查询租户任务状态: {}", tenantId);
        TaskStatusInfo result = taskOperationService.queryTaskStatusByTenant(TenantId.of(tenantId));
        if (result.getStatus() == null) {
            throw new TaskNotFoundException("租户任务不存在: " + tenantId);
        }
        return result;
    }



    // T-035: 移除查询 API - queryPlanStatus 和 hasCheckpoint
    // 调用方应自行通过事件监听维护投影状态

    // ========== 私有辅助方法 ==========

    private void handleTaskOperationResult(TaskOperationResult result, String operation) {
        if (!result.isSuccess()) {
            FailureInfo failureInfo = result.getFailureInfo();
            String message = result.getMessage();
            if (message != null && message.contains("未找到")) {
                throw new TaskNotFoundException(message);
            }
            throw new TaskOperationException(operation + "失败: " + message, failureInfo);
        }
    }
}
