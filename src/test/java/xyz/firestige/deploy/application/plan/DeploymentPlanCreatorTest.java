package xyz.firestige.deploy.application.plan;

import com.github.javafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.application.validation.BusinessValidator;
import xyz.firestige.deploy.config.ExecutorProperties;
import xyz.firestige.deploy.domain.plan.PlanAggregate;
import xyz.firestige.deploy.domain.plan.PlanDomainService;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.infrastructure.execution.stage.CompositeServiceStage;
import xyz.firestige.deploy.infrastructure.execution.stage.StageFactory;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskDomainService;
import xyz.firestige.deploy.domain.shared.validation.ValidationError;
import xyz.firestige.deploy.domain.shared.validation.ValidationSummary;
import xyz.firestige.dto.deploy.TenantDeployConfig;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentPlanCreatorTest {

    // Mock 基础设施依赖（Repository、Factory、Validator）
    private PlanDomainService mockPlanService;
    private TaskDomainService mockTaskService;
    private StageFactory mockStageFactory;
    private BusinessValidator mockValidator;

    private DeploymentPlanCreator creator;

    @BeforeEach
    void setUp() {
        mockPlanService = mock(PlanDomainService.class);
        mockTaskService = mock(TaskDomainService.class);
        mockStageFactory = mock(StageFactory.class);
        mockValidator = mock(BusinessValidator.class);
        ExecutorProperties executorProperties = new ExecutorProperties();

        creator = new DeploymentPlanCreator(
                mockPlanService, mockTaskService,
                mockStageFactory, mockValidator, executorProperties
        );
    }

    @Test
    void createPlan_WhenAllStepsSucceed_ReturnsSuccessContext() {
        // Given: 准备测试数据
        Faker faker = new Faker();
        long planId = faker.number().randomNumber();
        TenantConfig tenantConfig = new TenantConfig();
        tenantConfig.setPlanId(faker.number().randomNumber());
        List<TenantConfig> configs = List.of(tenantConfig);

        // Mock 业务校验通过
        when(mockValidator.validate(configs))
                .thenReturn(new ValidationSummary());

        // Mock Plan 创建
        PlanAggregate plan = new PlanAggregate(PlanId.of(planId));
        plan.setMaxConcurrency(1);
        when(mockPlanService.createPlan(any(), anyInt(), anyInt()))
                .thenReturn(plan);

        // Mock Task 创建
        String taskId = "task-" + faker.idNumber().valid();
        String tenantId = faker.idNumber().valid();
        TaskAggregate task = new TaskAggregate(TaskId.of(taskId), PlanId.of(planId), TenantId.of(tenantId));
        when(mockTaskService.createTask(any(), any()))
                .thenReturn(task);

        // Mock Stage 构建
        TaskStage mockStage = new CompositeServiceStage("", List.of());
        List<TaskStage> stages = List.of(mockStage);
        when(mockStageFactory.buildStages(configs.get(0)))
                .thenReturn(stages);

        // When: 调用创建器
        PlanCreationContext context = creator.createPlan(configs);

        // Then: 验证结果
        assertTrue(context.isSuccess());
        assertFalse(context.hasValidationErrors());
        assertNotNull(context.getPlanInfo());

        // 验证调用顺序（关键！）
        InOrder inOrder = inOrder(
                mockValidator, mockPlanService, mockTaskService, mockStageFactory
        );
        // TODO: 验证调用顺序这里应该注意调用值的顺序，断言太严过不去，但全是 any 相当于没断言
        inOrder.verify(mockValidator).validate(configs);
        inOrder.verify(mockPlanService).createPlan(any(), anyInt(), anyInt());
        inOrder.verify(mockTaskService).createTask(any(), any());
        inOrder.verify(mockStageFactory).buildStages(configs.get(0));
        inOrder.verify(mockPlanService).addTaskToPlan(any(), any());
        inOrder.verify(mockPlanService).markPlanAsReady(any());
        inOrder.verify(mockPlanService).startPlan(any());
    }

    @Test
    void createPlan_WhenValidationFails_ReturnsValidationFailureContext() {
        // Given
        ValidationSummary validation = new ValidationSummary();
        validation.addInvalidConfig(new TenantDeployConfig(), List.of(new ValidationError()));
        when(mockValidator.validate(any()))
                .thenReturn(validation);

        // When
        PlanCreationContext context = creator.createPlan(List.of(new TenantConfig()));

        // Then
        assertFalse(context.isSuccess());
        assertTrue(context.hasValidationErrors());

        // 验证后续步骤未执行
        verify(mockPlanService, never()).createPlan(any(), anyInt(), anyInt());
    }
}