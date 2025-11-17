package xyz.firestige.executor.unit.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.application.PlanApplicationService;
import xyz.firestige.executor.application.TaskApplicationService;
import xyz.firestige.executor.application.dto.PlanCreationResult;
import xyz.firestige.executor.application.dto.TaskOperationResult;
import xyz.firestige.executor.checkpoint.CheckpointService;
import xyz.firestige.executor.checkpoint.InMemoryCheckpointStore;
import xyz.firestige.executor.config.ExecutorProperties;
import xyz.firestige.executor.domain.stage.DefaultStageFactory;
import xyz.firestige.executor.domain.stage.StageFactory;
import xyz.firestige.executor.event.SpringTaskEventSink;
import xyz.firestige.executor.execution.DefaultTaskWorkerFactory;
import xyz.firestige.executor.execution.TaskWorkerFactory;
import xyz.firestige.executor.facade.TaskStatusInfo;
import xyz.firestige.executor.factory.PlanFactory;
import xyz.firestige.executor.orchestration.PlanOrchestrator;
import xyz.firestige.executor.orchestration.TaskScheduler;
import xyz.firestige.executor.service.health.MockHealthCheckClient;
import xyz.firestige.executor.state.TaskStateManager;
import xyz.firestige.executor.state.TaskStatus;
import xyz.firestige.executor.support.conflict.ConflictRegistry;
import xyz.firestige.executor.validation.ValidationChain;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskApplicationService 单元测试
 */
@DisplayName("TaskApplicationService 单元测试")
@Tag("rf01")
@Tag("positive")
@Tag("application-service")
class TaskApplicationServiceTest {

    private TaskApplicationService taskApplicationService;
    private PlanApplicationService planApplicationService;
    private TaskStateManager stateManager;

    @BeforeEach
    void setUp() {
        // 初始化依赖
        ValidationChain validationChain = new ValidationChain();
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

        // 创建 TaskApplicationService（共享注册表）
        taskApplicationService = new TaskApplicationService(
                stateManager,
                workerFactory,
                executorProperties,
                checkpointService,
                eventSink,
                conflictRegistry,
                planApplicationService.getTaskRegistry(),
                planApplicationService.getContextRegistry(),
                planApplicationService.getStageRegistry(),
                planApplicationService.getExecutorRegistry()
        );
    }

    @Test
    @DisplayName("暂停任务 - 租户不存在时返回失败")
    void testPauseTaskByTenant_NotFound() {
        // When
        TaskOperationResult result = taskApplicationService.pauseTaskByTenant("non_existent_tenant");

        // Then
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("未找到租户任务"));
    }

    @Test
    @DisplayName("暂停任务 - 成功场景 (等待上下文标记)")
    void testPauseTaskByTenant_Success() {
        String tenantId = "tenant_001";
        String taskId = createPlanWithTenants(tenantId);

        // When
        TaskOperationResult result = taskApplicationService.pauseTaskByTenant(tenantId);

        // Then
        assertNotNull(result);
        assertTrue(result.isSuccess());

        // Await until context pauseRequested flag becomes true
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofMillis(300))
                .until(() -> planApplicationService.getContextRegistry().get(taskId) != null && planApplicationService.getContextRegistry().get(taskId).isPauseRequested());

        // Status may remain RUNNING (single stage); assert either RUNNING or PAUSED
        TaskStatus status = planApplicationService.getTaskRegistry().get(taskId).getStatus();
        assertTrue(status == TaskStatus.RUNNING || status == TaskStatus.PAUSED);
    }

    @Test
    @DisplayName("恢复任务 - 租户不存在时返回失败")
    void testResumeTaskByTenant_NotFound() {
        // When
        TaskOperationResult result = taskApplicationService.resumeTaskByTenant("non_existent_tenant");

        // Then
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("未找到租户任务"));
    }

    @Test
    @DisplayName("恢复任务 - 成功场景 (等待上下文清除)")
    void testResumeTaskByTenant_Success() {
        String tenantId = "tenant_001";
        String taskId = createPlanWithTenants(tenantId);
        taskApplicationService.pauseTaskByTenant(tenantId);

        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofMillis(300))
                .until(() -> planApplicationService.getContextRegistry().get(taskId) != null && planApplicationService.getContextRegistry().get(taskId).isPauseRequested());

        // When
        TaskOperationResult result = taskApplicationService.resumeTaskByTenant(tenantId);

        // Then
        assertNotNull(result);
        assertTrue(result.isSuccess());

        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofMillis(300))
                .until(() -> !planApplicationService.getContextRegistry().get(taskId).isPauseRequested());

        TaskStatus status = planApplicationService.getTaskRegistry().get(taskId).getStatus();
        assertTrue(status == TaskStatus.RUNNING || status == TaskStatus.PAUSED);
    }

    @Test
    @DisplayName("查询任务状态 - 任务不存在时返回失败")
    void testQueryTaskStatus_NotFound() {
        // When
        TaskStatusInfo result = taskApplicationService.queryTaskStatus("non_existent_task_id");

        // Then
        assertNotNull(result);
        assertNull(result.getStatus());
        assertTrue(result.getMessage().contains("任务不存在"));
    }

    @Test
    @DisplayName("查询任务状态 - 成功场景")
    void testQueryTaskStatus_Success() {
        // Given
        String tenantId = "tenant_001";
        String taskId = createPlanWithTenants(tenantId);

        // When
        TaskStatusInfo result = taskApplicationService.queryTaskStatus(taskId);

        // Then
        assertNotNull(result);
        assertNotNull(result.getStatus());
        assertEquals(taskId, result.getTaskId());
        assertNotNull(result.getMessage());
    }

    @Test
    @DisplayName("根据租户查询任务状态 - 租户不存在时返回失败")
    void testQueryTaskStatusByTenant_NotFound() {
        // When
        TaskStatusInfo result = taskApplicationService.queryTaskStatusByTenant("non_existent_tenant");

        // Then
        assertNotNull(result);
        assertNull(result.getStatus());
        assertTrue(result.getMessage().contains("未找到租户任务"));
    }

    @Test
    @DisplayName("根据租户查询任务状态 - 成功场景")
    void testQueryTaskStatusByTenant_Success() {
        // Given
        String tenantId = "tenant_001";
        createPlanWithTenants(tenantId);

        // When
        TaskStatusInfo result = taskApplicationService.queryTaskStatusByTenant(tenantId);

        // Then
        assertNotNull(result);
        assertNotNull(result.getStatus());
        assertNotNull(result.getMessage());
    }

    @Test
    @DisplayName("取消任务 - 任务不存在时返回失败")
    void testCancelTask_NotFound() {
        // When
        TaskOperationResult result = taskApplicationService.cancelTask("non_existent_task_id");

        // Then
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("任务不存在"));
    }

    @Test
    @DisplayName("取消任务 - 成功场景")
    void testCancelTask_Success() {
        // Given
        String tenantId = "tenant_001";
        String taskId = createPlanWithTenants(tenantId);

        // When
        TaskOperationResult result = taskApplicationService.cancelTask(taskId);

        // Then
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(TaskStatus.CANCELLED, result.getStatus());
        assertTrue(result.getMessage().contains("取消"));
    }

    @Test
    @DisplayName("根据租户取消任务 - 成功场景")
    void testCancelTaskByTenant_Success() {
        // Given
        String tenantId = "tenant_001";
        createPlanWithTenants(tenantId);

        // When
        TaskOperationResult result = taskApplicationService.cancelTaskByTenant(tenantId);

        // Then
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(TaskStatus.CANCELLED, result.getStatus());
        assertTrue(result.getMessage().contains("取消"));
    }

    @Test
    @DisplayName("回滚任务 - 租户不存在时返回失败")
    void testRollbackTaskByTenant_NotFound() {
        // When
        TaskOperationResult result = taskApplicationService.rollbackTaskByTenant("non_existent_tenant");

        // Then
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("未找到租户任务"));
    }

    @Test
    @DisplayName("重试任务 - 租户不存在时返回失败")
    void testRetryTaskByTenant_NotFound() {
        // When
        TaskOperationResult result = taskApplicationService.retryTaskByTenant("non_existent_tenant", false);

        // Then
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("未找到租户任务"));
    }

    // ========== 辅助方法 ==========

    /**
     * 创建包含指定租户的 Plan，并返回第一个 Task 的 ID
     */
    private String createPlanWithTenants(String... tenantIds) {
        List<TenantDeployConfig> configs = new ArrayList<>();
        java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
        for (String tenantId : tenantIds) {
            TenantDeployConfig config = new TenantDeployConfig();
            config.setPlanId(1001L);
            config.setTenantId(tenantId);
            config.setDeployUnitId(rnd.nextLong(1, 10000));
            config.setDeployUnitVersion(100L);
            config.setDeployUnitName("DU-" + java.util.UUID.randomUUID());
            config.setNacosNameSpace("dev");
            xyz.firestige.entity.deploy.NetworkEndpoint ep = new xyz.firestige.entity.deploy.NetworkEndpoint();
            ep.setValue("http://localhost:8080/health");
            config.setNetworkEndpoints(List.of(ep));
            configs.add(config);
        }

        PlanCreationResult result = planApplicationService.createSwitchTask(configs);
        assertTrue(result.isSuccess());

        return result.getPlanInfo().getTasks().get(0).getTaskId();
    }
}
