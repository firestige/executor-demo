package xyz.firestige.executor.application.plan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.executor.application.dto.TenantConfig;
import xyz.firestige.executor.application.validation.BusinessValidator;
import xyz.firestige.executor.domain.plan.PlanDomainService;
import xyz.firestige.executor.domain.plan.PlanInfo;
import xyz.firestige.executor.domain.stage.StageFactory;
import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskDomainService;
import xyz.firestige.executor.service.health.HealthCheckClient;
import xyz.firestige.executor.validation.ValidationSummary;

import java.util.List;

/**
 * 部署计划创建器（RF-10 重构：提取职责）
 *
 * 职责：
 * 1. 负责部署计划的创建流程编排
 * 2. 协调 Plan 和 Task 的创建
 * 3. 业务规则校验
 * 4. Stage 构建
 *
 * 设计说明：
 * - 从 DeploymentApplicationService 中提取
 * - 单一职责：只负责创建流程
 * - 无状态对象，可复用
 *
 * @since DDD 重构 Phase 18 - RF-10
 */
public class DeploymentPlanCreator {

    private static final Logger logger = LoggerFactory.getLogger(DeploymentPlanCreator.class);

    private final PlanDomainService planDomainService;
    private final TaskDomainService taskDomainService;
    private final StageFactory stageFactory;
    private final HealthCheckClient healthCheckClient;
    private final BusinessValidator businessValidator;

    public DeploymentPlanCreator(
            PlanDomainService planDomainService,
            TaskDomainService taskDomainService,
            StageFactory stageFactory,
            HealthCheckClient healthCheckClient,
            BusinessValidator businessValidator) {
        this.planDomainService = planDomainService;
        this.taskDomainService = taskDomainService;
        this.stageFactory = stageFactory;
        this.healthCheckClient = healthCheckClient;
        this.businessValidator = businessValidator;
    }

    /**
     * 创建部署计划
     *
     * @param configs 租户配置列表
     * @return PlanCreationContext 包含创建的 Plan 信息和验证结果
     * @throws PlanCreationException 如果创建失败
     */
    public PlanCreationContext createPlan(List<TenantConfig> configs) {
        logger.info("[DeploymentPlanCreator] 开始创建部署计划，租户数量: {}",
                configs != null ? configs.size() : 0);

        // Step 1: 业务规则校验
        ValidationSummary businessValidation = businessValidator.validate(configs);
        if (businessValidation.hasErrors()) {
            logger.warn("[DeploymentPlanCreator] 业务规则校验失败，无效配置数: {}",
                    businessValidation.getInvalidCount());
            return PlanCreationContext.validationFailure(businessValidation);
        }

        // Step 2: 提取 Plan ID
        String planId = extractPlanId(configs);
        logger.info("[DeploymentPlanCreator] 使用 Plan ID: {}", planId);

        try {
            // Step 3: 创建 Plan
            planDomainService.createPlan(planId, configs.size());

            // Step 4: 为每个租户创建 Task
            for (TenantConfig config : configs) {
                createAndLinkTask(planId, config);
            }

            // Step 5: 标记 Plan 为 READY
            planDomainService.markPlanAsReady(planId);

            // Step 6: 启动 Plan 执行
            planDomainService.startPlan(planId);

            // Step 7: 获取 Plan 信息并返回
            PlanInfo planInfo = planDomainService.getPlanInfo(planId);
            logger.info("[DeploymentPlanCreator] 部署计划创建成功，planId: {}", planId);

            return PlanCreationContext.success(planInfo);

        } catch (Exception e) {
            logger.error("[DeploymentPlanCreator] 创建部署计划失败", e);
            throw new PlanCreationException("创建部署计划失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建并关联 Task 到 Plan
     *
     * @param planId Plan ID
     * @param config 租户配置
     */
    private void createAndLinkTask(String planId, TenantConfig config) {
        // 创建 Task 聚合
        TaskAggregate task = taskDomainService.createTask(planId, config);

        // 构建 Task 的 Stages
        taskDomainService.buildTaskStages(task, config, stageFactory, healthCheckClient);

        // 关联 Task 到 Plan（聚合间通过 ID 引用）
        planDomainService.addTaskToPlan(planId, task.getTaskId());

        logger.debug("[DeploymentPlanCreator] Task 创建并关联成功: {}", task.getTaskId());
    }

    /**
     * 从配置列表中提取 Plan ID
     *
     * @param configs 配置列表
     * @return Plan ID
     */
    private String extractPlanId(List<TenantConfig> configs) {
        return configs.stream()
                .map(TenantConfig::getPlanId)
                .findFirst()
                .map(String::valueOf)
                .orElseThrow(() -> new IllegalArgumentException("配置列表为空或缺少 Plan ID"));
    }
}

