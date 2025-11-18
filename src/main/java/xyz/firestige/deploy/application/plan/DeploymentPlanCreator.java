package xyz.firestige.deploy.application.plan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.application.validation.BusinessValidator;
import xyz.firestige.deploy.config.ExecutorProperties;
import xyz.firestige.deploy.domain.plan.PlanAggregate;
import xyz.firestige.deploy.domain.plan.PlanDomainService;
import xyz.firestige.deploy.domain.plan.PlanInfo;
import xyz.firestige.deploy.infrastructure.execution.stage.StageFactory;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskDomainService;
import xyz.firestige.deploy.domain.task.TaskInfo;
import xyz.firestige.deploy.domain.shared.validation.ValidationSummary;

import java.util.List;

/**
 * 部署计划创建器（RF-10 重构：提取职责）
 * <p>
 * 职责：
 * 1. 负责部署计划的创建流程编排
 * 2. 协调 Plan 和 Task 的创建
 * 3. 业务规则校验
 * 4. Stage 构建
 * <p>
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
    private final BusinessValidator businessValidator;
    private final ExecutorProperties executorProperties;

    public DeploymentPlanCreator(
            PlanDomainService planDomainService,
            TaskDomainService taskDomainService,
            StageFactory stageFactory,
            BusinessValidator businessValidator,
            ExecutorProperties executorProperties) {
        this.planDomainService = planDomainService;
        this.taskDomainService = taskDomainService;
        this.stageFactory = stageFactory;
        this.businessValidator = businessValidator;
        this.executorProperties = executorProperties;
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
            PlanAggregate plan = planDomainService.createPlan(planId, configs.size(), executorProperties.getMaxConcurrency());

            // Step 4: 为每个租户创建 Task
            List<TaskInfo> tasks = configs.stream().map(this::createAndLinkTask).map(TaskInfo::from).toList();

            // Step 5: 标记 Plan 为 READY
            planDomainService.markPlanAsReady(planId);

            // Step 6: 启动 Plan 执行
            planDomainService.startPlan(planId);

            // Step 7: 获取 Plan 信息并返回

            PlanInfo planInfo = PlanInfo.from(plan, tasks);
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
     * @param config 租户配置
     */
    private TaskAggregate createAndLinkTask(TenantConfig config) {
        String planId = String.valueOf(config.getPlanId());
        // 创建 Task 聚合
        TaskAggregate task = taskDomainService.createTask(planId, config);

        // 构建 Task 的 Stages
        List<TaskStage> stages = buildStagesForTask(task, config);
        taskDomainService.attacheStages(task, stages);

        // 关联 Task 到 Plan（聚合间通过 ID 引用）
        planDomainService.addTaskToPlan(planId, task.getTaskId());

        logger.debug("[DeploymentPlanCreator] Task 创建并关联成功: {}", task.getTaskId());

        return task;
    }

    private List<TaskStage> buildStagesForTask(TaskAggregate task, TenantConfig config) {
        logger.debug("[DeploymentPlanCreator] 构建 Task Stages: {}", task.getTaskId());

        List<TaskStage> stages = stageFactory.buildStages(config);

        logger.debug("[DeploymentPlanCreator] Task Stages 构建完成: {}, stage数量: {}", task.getTaskId(), stages.size());
        return stages;
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

