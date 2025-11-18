package xyz.firestige.deploy.application;

import com.github.javafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import xyz.firestige.deploy.application.dto.DeployUnitIdentifier;
import xyz.firestige.deploy.application.dto.MediaRoutingConfig;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.application.plan.DeploymentPlanCreator;
import xyz.firestige.deploy.application.plan.PlanCreationContext;
import xyz.firestige.deploy.domain.plan.PlanCreationResult;
import xyz.firestige.deploy.domain.plan.PlanDomainService;
import xyz.firestige.deploy.domain.plan.PlanInfo;
import xyz.firestige.deploy.domain.plan.PlanStatus;
import xyz.firestige.deploy.domain.task.TaskDomainService;
import xyz.firestige.deploy.domain.shared.exception.ErrorType;
import xyz.firestige.deploy.infrastructure.execution.TaskWorkerFactory;
import xyz.firestige.deploy.infrastructure.scheduling.TenantConflictManager;
import xyz.firestige.deploy.infrastructure.state.TaskStateManager;
import xyz.firestige.deploy.util.TimingExtension;
import xyz.firestige.deploy.domain.shared.validation.ValidationError;
import xyz.firestige.deploy.domain.shared.validation.ValidationSummary;
import xyz.firestige.dto.deploy.TenantDeployConfig;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DeploymentApplicationService 单元测试
 * 测试部署计划创建的应用服务层逻辑
 * <p>
 * 测试范围：
 * - 成功创建部署计划
 * - 验证失败场景
 * - 冲突检测场景
 * <p>
 * 预计执行时间：15 秒（3 个测试）
 */
@Tag("unit")
@Tag("application")
@Tag("fast")
@ExtendWith(TimingExtension.class)
@DisplayName("DeploymentApplicationService 单元测试")
class DeploymentApplicationServiceTest {

    // ✅ 只 Mock 核心依赖：DeploymentPlanCreator
    private DeploymentPlanCreator mockCreator;
    private TenantConflictManager mockConflictManager;

    private DeploymentApplicationService service;

    @BeforeEach
    void setUp() {
        mockCreator = mock(DeploymentPlanCreator.class);
        PlanDomainService mockPlanService = mock(PlanDomainService.class);
        TaskDomainService mockTaskService = mock(TaskDomainService.class);
        mockConflictManager = mock(TenantConflictManager.class);
        TaskWorkerFactory mockWorkerFactory = mock(TaskWorkerFactory.class);
        TaskStateManager mockStateManager = mock(TaskStateManager.class);

        service = new DeploymentApplicationService(
                mockCreator, mockPlanService, mockTaskService,
                mockConflictManager, mockWorkerFactory, mockStateManager
        );
    }

    @Test
    @Tag("happy-path")
    @DisplayName("场景 3.1.1: 创建计划成功 - 创建器返回成功上下文")
    void createDeploymentPlan_WhenCreatorSucceeds_ReturnsSuccess() {
        // Given: 准备测试数据
        Faker faker = new Faker();
        Long planId = faker.number().randomNumber();
        TenantConfig tenantConfig = new TenantConfig();
        tenantConfig.setPlanId(planId);
        tenantConfig.setDeployUnit(
                new DeployUnitIdentifier(faker.number().randomNumber(), new Random().nextLong(1000), "v1.0.0"));
        tenantConfig.setTenantId(faker.idNumber().valid());
        tenantConfig.setNetworkEndpoints(List.of());
        tenantConfig.setNacosNameSpace("");
        tenantConfig.setHealthCheckEndpoints(List.of());
        tenantConfig.setDefaultFlag(false);
        tenantConfig.setPreviousConfig(null);
        tenantConfig.setPlanVersion(new Random().nextLong(1000));
        tenantConfig.setMediaRoutingConfig(new MediaRoutingConfig("", ""));
        List<TenantConfig> configs = List.of(tenantConfig);

        PlanInfo planInfo = new PlanInfo(String.valueOf(planId), 1, PlanStatus.CREATED, List.of(), LocalDateTime.now());
        PlanCreationContext successContext = PlanCreationContext.success(planInfo);

        // Mock 冲突检测通过
        when(mockConflictManager.canCreatePlan(anyList()))
                .thenReturn(TenantConflictManager.ConflictCheckResult.allow());

        // Mock 创建器返回成功
        when(mockCreator.createPlan(configs))
                .thenReturn(successContext);

        // When: 调用应用服务
        PlanCreationResult result = service.createDeploymentPlan(configs);

        // Then: 验证结果
        assertTrue(result.isSuccess());
        assertEquals(planInfo, result.getPlanInfo());

        // 验证交互
        verify(mockCreator).createPlan(configs);
    }

    @Test
    @Tag("validation")
    @Tag("error-handling")
    @DisplayName("场景 3.1.2: 验证失败 - 创建器返回验证失败上下文")
    void createDeploymentPlan_WhenValidationFails_ReturnsValidationFailure() {
        // Given
        ValidationSummary validation = new ValidationSummary();
        validation.addInvalidConfig(new TenantDeployConfig(), List.of(new ValidationError()));

        PlanCreationContext failureContext =
                PlanCreationContext.validationFailure(validation);

        when(mockConflictManager.canCreatePlan(anyList()))
                .thenReturn(TenantConflictManager.ConflictCheckResult.allow());
        when(mockCreator.createPlan(any()))
                .thenReturn(failureContext);

        // When
        PlanCreationResult result = service.createDeploymentPlan(List.of());

        // Then
        assertFalse(result.isSuccess());
        assertNotNull(result.getValidationSummary());
    }

    @Test
    @Tag("conflict")
    @Tag("error-handling")
    @DisplayName("场景 3.1.3: 冲突检测失败 - 租户冲突导致计划创建失败")
    void createDeploymentPlan_WhenConflictDetected_ReturnsConflictFailure() {
        // Given
        when(mockConflictManager.canCreatePlan(anyList()))
                .thenReturn(TenantConflictManager.ConflictCheckResult.reject(List.of("tenant1", "冲突")));

        // When
        PlanCreationResult result = service.createDeploymentPlan(List.of());

        // Then
        assertFalse(result.isSuccess());
        assertEquals(ErrorType.CONFLICT, result.getFailureInfo().getErrorType());

        // 验证创建器未被调用
        verify(mockCreator, never()).createPlan(any());
    }
}