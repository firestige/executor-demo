package xyz.firestige.executor.unit.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.awaitility.Awaitility;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.application.PlanApplicationService;
import xyz.firestige.executor.application.TaskApplicationService;
import xyz.firestige.executor.application.dto.PlanCreationResult;
import xyz.firestige.executor.application.dto.TaskOperationResult;
import xyz.firestige.executor.checkpoint.CheckpointService;
import xyz.firestige.executor.checkpoint.InMemoryCheckpointStore;
import xyz.firestige.executor.config.ExecutorProperties;
import xyz.firestige.executor.event.SpringTaskEventSink;
import xyz.firestige.executor.execution.DefaultTaskWorkerFactory;
import xyz.firestige.executor.execution.TaskWorkerFactory;
import xyz.firestige.executor.factory.PlanFactory;
import xyz.firestige.executor.orchestration.PlanOrchestrator;
import xyz.firestige.executor.orchestration.TaskScheduler;
import xyz.firestige.executor.service.health.MockHealthCheckClient;
import xyz.firestige.executor.state.TaskStateManager;
import xyz.firestige.executor.state.TaskStatus;
import xyz.firestige.executor.support.conflict.ConflictRegistry;
import xyz.firestige.executor.validation.ValidationChain;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Advanced tests for rollback and retry flows using multi-stage factory.
 */
@org.junit.jupiter.api.Disabled("Legacy advanced scenarios (rollback/retry multi-stage) deferred; keeping file for future implementation.")
@DisplayName("[LEGACY][DEFERRED] TaskApplicationService 高级用例：Rollback & Retry")
class TaskApplicationServiceAdvancedTest {

    private PlanApplicationService planService;
    private TaskApplicationService taskService;
    private TaskStateManager stateManager;
    private ExecutorProperties props;

    @BeforeEach
    void setup() {
        ValidationChain vc = new ValidationChain();
        stateManager = new TaskStateManager();
        ConflictRegistry conflictRegistry = new ConflictRegistry();
        props = new ExecutorProperties();
        props.setHealthCheckMaxAttempts(1); // speed up health check for tests
        props.setHealthCheckIntervalSeconds(1);
        TaskScheduler scheduler = new TaskScheduler(Runtime.getRuntime().availableProcessors());
        PlanOrchestrator orchestrator = new PlanOrchestrator(scheduler, conflictRegistry, props);
        TaskWorkerFactory workerFactory = new DefaultTaskWorkerFactory();
        MockHealthCheckClient healthClient = new MockHealthCheckClient();
        CheckpointService checkpointService = new CheckpointService(new InMemoryCheckpointStore());
        SpringTaskEventSink sink = new SpringTaskEventSink(stateManager, conflictRegistry);

        // Use multi-stage factory
        TestMultiStageFactory testFactory = new TestMultiStageFactory();

        planService = new PlanApplicationService(
                vc,
                stateManager,
                new PlanFactory(),
                orchestrator,
                testFactory,
                workerFactory,
                props,
                healthClient,
                checkpointService,
                sink,
                conflictRegistry
        );

        taskService = new TaskApplicationService(
                stateManager,
                workerFactory,
                props,
                checkpointService,
                sink,
                conflictRegistry,
                planService.getTaskRegistry(),
                planService.getContextRegistry(),
                planService.getStageRegistry(),
                planService.getExecutorRegistry()
        );
    }

    @Test
    @DisplayName("Rollback 成功：两阶段任务回滚后状态为 ROLLED_BACK")
    void testRollbackSuccess() {
        String tenantId = "tenant_rb";
        List<TenantDeployConfig> configs = createConfigs(tenantId);
        PlanCreationResult result = planService.createSwitchTask(configs);
        assertTrue(result.isSuccess());
        String taskId = result.getPlanInfo().getTasks().get(0).getTaskId();

        // 等待任务两个阶段全部完成（currentStageIndex==2 或状态 COMPLETED）
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> planService.getTaskRegistry().get(taskId).getCurrentStageIndex() >= 2
                        || planService.getTaskRegistry().get(taskId).getStatus() == TaskStatus.COMPLETED);

        // 触发回滚
        TaskOperationResult rbRes = taskService.rollbackTaskByTenant(tenantId);
        assertTrue(rbRes.isSuccess());
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> planService.getTaskRegistry().get(taskId).getStatus() == TaskStatus.ROLLED_BACK);
        assertEquals(TaskStatus.ROLLED_BACK, planService.getTaskRegistry().get(taskId).getStatus());
    }

    @Test
    @DisplayName("Retry 成功：完成后从头重试仍再次 COMPLETED")
    void testRetryFromScratchSuccess() {
        String tenantId = "tenant_retry";
        List<TenantDeployConfig> configs = createConfigs(tenantId);
        PlanCreationResult result = planService.createSwitchTask(configs);
        assertTrue(result.isSuccess());
        String taskId = result.getPlanInfo().getTasks().get(0).getTaskId();

        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> planService.getTaskRegistry().get(taskId).getCurrentStageIndex() >= 2
                        || planService.getTaskRegistry().get(taskId).getStatus() == TaskStatus.COMPLETED);

        TaskOperationResult retryRes = taskService.retryTaskByTenant(tenantId, false);
        assertTrue(retryRes.isSuccess());

        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> planService.getTaskRegistry().get(taskId).getCurrentStageIndex() >= 2
                        || planService.getTaskRegistry().get(taskId).getStatus() == TaskStatus.COMPLETED);
        assertEquals(TaskStatus.COMPLETED, planService.getTaskRegistry().get(taskId).getStatus());
    }

    private List<TenantDeployConfig> createConfigs(String tenantId) {
        List<TenantDeployConfig> list = new ArrayList<>();
        TenantDeployConfig cfg = new TenantDeployConfig();
        cfg.setPlanId(4001L);
        cfg.setTenantId(tenantId);
        cfg.setDeployUnitId(ThreadLocalRandom.current().nextLong(1, 10000));
        cfg.setDeployUnitVersion(200L);
        cfg.setDeployUnitName("DU-" + tenantId);
        xyz.firestige.entity.deploy.NetworkEndpoint ep = new xyz.firestige.entity.deploy.NetworkEndpoint();
        ep.setValue("http://localhost:8080/health");
        cfg.setNetworkEndpoints(List.of(ep));
        list.add(cfg);
        return list;
    }

    // TODO(RF01-LEGACY): Implement multi-stage success path without health check timing issues
    // TODO(RF01-LEGACY): Add rollback success + partial failure scenarios
    // TODO(RF01-LEGACY): Add retry from checkpoint and from scratch differentiation
    // TODO(RF01-LEGACY): Add conflict registry release assertions
}
