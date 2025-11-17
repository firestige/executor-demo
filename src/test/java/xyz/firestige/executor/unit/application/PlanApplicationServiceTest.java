package xyz.firestige.executor.unit.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.application.PlanApplicationService;
import xyz.firestige.executor.application.dto.PlanCreationResult;
import xyz.firestige.executor.application.dto.PlanInfo;
import xyz.firestige.executor.application.dto.PlanOperationResult;
import xyz.firestige.executor.checkpoint.CheckpointService;
import xyz.firestige.executor.checkpoint.InMemoryCheckpointStore;
import xyz.firestige.executor.config.ExecutorProperties;
import xyz.firestige.executor.domain.plan.PlanStatus;
import xyz.firestige.executor.domain.stage.DefaultStageFactory;
import xyz.firestige.executor.domain.stage.StageFactory;
import xyz.firestige.executor.event.SpringTaskEventSink;
import xyz.firestige.executor.execution.DefaultTaskWorkerFactory;
import xyz.firestige.executor.execution.TaskWorkerFactory;
import xyz.firestige.executor.factory.PlanFactory;
import xyz.firestige.executor.orchestration.PlanOrchestrator;
import xyz.firestige.executor.orchestration.TaskScheduler;
import xyz.firestige.executor.service.health.MockHealthCheckClient;
import xyz.firestige.executor.state.TaskStateManager;
import xyz.firestige.executor.support.conflict.ConflictRegistry;
import xyz.firestige.executor.validation.ValidationChain;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PlanApplicationService 单元测试
 */
@DisplayName("PlanApplicationService 单元测试")
@Tag("rf01")
@Tag("positive")
@Tag("application-service")
class PlanApplicationServiceTest {

    private PlanApplicationService planApplicationService;
    private ValidationChain validationChain;
    private TaskStateManager stateManager;

    @BeforeEach
    void setUp() {
        // 初始化依赖
        validationChain = new ValidationChain();
        stateManager = new TaskStateManager();
        PlanFactory planFactory = new PlanFactory();
        ConflictRegistry conflictRegistry = new ConflictRegistry();
        ExecutorProperties executorProperties = new ExecutorProperties();
        TaskScheduler taskScheduler = new TaskScheduler(Runtime.getRuntime().availableProcessors());
        PlanOrchestrator planOrchestrator = new PlanOrchestrator(taskScheduler, conflictRegistry, executorProperties);
        StageFactory stageFactory = new DefaultStageFactory();
        TaskWorkerFactory workerFactory = new DefaultTaskWorkerFactory();
        MockHealthCheckClient healthCheckClient = new MockHealthCheckClient();
        CheckpointService checkpointService = new CheckpointService(new InMemoryCheckpointStore());
        SpringTaskEventSink eventSink = new SpringTaskEventSink(stateManager, conflictRegistry);

        // 创建 PlanApplicationService
        planApplicationService = new PlanApplicationService(
                validationChain,
                stateManager,
                planFactory,
                planOrchestrator,
                stageFactory,
                workerFactory,
                executorProperties,
                healthCheckClient,
                checkpointService,
                eventSink,
                conflictRegistry
        );
    }

    @Test
    @DisplayName("创建 Plan - 配置为空时返回失败")
    void testCreateSwitchTask_EmptyConfigs() {
        // Given
        List<TenantDeployConfig> configs = new ArrayList<>();

        // When
        PlanCreationResult result = planApplicationService.createSwitchTask(configs);

        // Then
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertNotNull(result.getFailureInfo());
        assertEquals("配置列表为空", result.getMessage());
    }

    @Test
    @DisplayName("创建 Plan - 配置为 null 时返回失败")
    void testCreateSwitchTask_NullConfigs() {
        // When
        PlanCreationResult result = planApplicationService.createSwitchTask(null);

        // Then
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertNotNull(result.getFailureInfo());
        assertEquals("配置列表为空", result.getMessage());
    }

    @Test
    @DisplayName("创建 Plan - 校验失败场景 (当前无验证器应视为成功)")
    void testCreateSwitchTask_NoValidatorsPasses() {
        // Given
        List<TenantDeployConfig> configs = new ArrayList<>();
        TenantDeployConfig config = new TenantDeployConfig();
        config.setPlanId(1001L); // 仅设置 planId 其余缺失, 由于当前无 validators 应仍成功
        config.setTenantId("tenant_placeholder");
        configs.add(config);

        // When
        PlanCreationResult result = planApplicationService.createSwitchTask(configs);

        // Then
        assertNotNull(result);
        assertTrue(result.isSuccess(), "无 validators 时应成功");
        assertNull(result.getValidationSummary(), "成功结果不返回校验失败摘要");
    }

    @Test
    @DisplayName("创建 Plan - 成功场景")
    void testCreateSwitchTask_Success() {
        // Given
        List<TenantDeployConfig> configs = createValidConfigs();

        // When
        PlanCreationResult result = planApplicationService.createSwitchTask(configs);

        // Then
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.getPlanInfo());

        PlanInfo planInfo = result.getPlanInfo();
        assertNotNull(planInfo.getPlanId());
        assertEquals(2, planInfo.getTasks().size());
        assertNotNull(planInfo.getCreatedAt());
    }

    @Test
    @DisplayName("暂停 Plan - Plan ��存在时返回失败")
    void testPausePlan_NotFound() {
        // When
        PlanOperationResult result = planApplicationService.pausePlan(99999L);

        // Then
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("计划不存在", result.getMessage());
    }

    @Test
    @DisplayName("暂停 Plan - 成功场景")
    void testPausePlan_Success() {
        // Given
        List<TenantDeployConfig> configs = createValidConfigs();
        PlanCreationResult createResult = planApplicationService.createSwitchTask(configs);
        assertTrue(createResult.isSuccess());
        String planId = createResult.getPlanInfo().getPlanId();

        // When
        PlanOperationResult result = planApplicationService.pausePlan(Long.parseLong(planId));

        // Then
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(PlanStatus.PAUSED, result.getStatus());
        assertTrue(result.getMessage().contains("暂停"));
    }

    @Test
    @DisplayName("恢复 Plan - 成功场景")
    void testResumePlan_Success() {
        // Given
        List<TenantDeployConfig> configs = createValidConfigs();
        PlanCreationResult createResult = planApplicationService.createSwitchTask(configs);
        assertTrue(createResult.isSuccess());
        String planId = createResult.getPlanInfo().getPlanId();

        // 先暂停
        planApplicationService.pausePlan(Long.parseLong(planId));

        // When
        PlanOperationResult result = planApplicationService.resumePlan(Long.parseLong(planId));

        // Then
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(PlanStatus.RUNNING, result.getStatus());
        assertTrue(result.getMessage().contains("恢复"));
    }

    @Test
    @DisplayName("回滚 Plan - Plan 不存在时返回失败")
    void testRollbackPlan_NotFound() {
        // When
        PlanOperationResult result = planApplicationService.rollbackPlan(99999L);

        // Then
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("计划不存在", result.getMessage());
    }

    @Test
    @DisplayName("重试 Plan - Plan 不存在时返回失败")
    void testRetryPlan_NotFound() {
        // When
        PlanOperationResult result = planApplicationService.retryPlan(99999L, false);

        // Then
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("计划不存在", result.getMessage());
    }

    @Test
    @DisplayName("创建 Plan - 验证失败场景 (缺少必填字段)")
    void testCreateSwitchTask_ValidationFailure() {
        ValidationChain chainWithValidator = new ValidationChain();
        chainWithValidator.addValidator(new xyz.firestige.executor.validation.RequiredFieldsValidator());
        planApplicationService = new PlanApplicationService(
                chainWithValidator,
                stateManager,
                new PlanFactory(),
                new PlanOrchestrator(new TaskScheduler(Runtime.getRuntime().availableProcessors()), new ConflictRegistry(), new ExecutorProperties()),
                new DefaultStageFactory(),
                new DefaultTaskWorkerFactory(),
                new ExecutorProperties(),
                new MockHealthCheckClient(),
                new CheckpointService(new InMemoryCheckpointStore()),
                new SpringTaskEventSink(stateManager, new ConflictRegistry()),
                new ConflictRegistry()
        );
        TenantDeployConfig invalid = new TenantDeployConfig();
        invalid.setPlanId(2001L); // missing required deployUnitId etc.
        invalid.setTenantId("tenantX");
        List<TenantDeployConfig> configs = List.of(invalid);
        PlanCreationResult res = planApplicationService.createSwitchTask(configs);
        assertFalse(res.isSuccess());
        assertNotNull(res.getValidationSummary());
        assertTrue(res.getValidationSummary().hasErrors());
    }

    // ========== 辅助方法 ==========

    /**
     * 创建有效的配置列表
     */
    private List<TenantDeployConfig> createValidConfigs() {
        List<TenantDeployConfig> configs = new ArrayList<>();
        java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
        TenantDeployConfig config1 = new TenantDeployConfig();
        config1.setPlanId(1001L);
        config1.setTenantId("tenant_001");
        config1.setDeployUnitId(rnd.nextLong(1, 10000));
        config1.setDeployUnitVersion(100L);
        config1.setDeployUnitName("DU-" + java.util.UUID.randomUUID());
        config1.setNacosNameSpace("dev");
        xyz.firestige.entity.deploy.NetworkEndpoint ep1 = new xyz.firestige.entity.deploy.NetworkEndpoint();
        ep1.setValue("http://localhost:8080/health");
        config1.setNetworkEndpoints(List.of(ep1));
        configs.add(config1);
        TenantDeployConfig config2 = new TenantDeployConfig();
        config2.setPlanId(1001L);
        config2.setTenantId("tenant_002");
        config2.setDeployUnitId(rnd.nextLong(1, 10000));
        config2.setDeployUnitVersion(101L);
        config2.setDeployUnitName("DU-" + java.util.UUID.randomUUID());
        config2.setNacosNameSpace("dev");
        xyz.firestige.entity.deploy.NetworkEndpoint ep2 = new xyz.firestige.entity.deploy.NetworkEndpoint();
        ep2.setValue("http://localhost:8081/health");
        config2.setNetworkEndpoints(List.of(ep2));
        configs.add(config2);
        return configs;
    }
}
