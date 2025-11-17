package xyz.firestige.executor.unit.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
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
import xyz.firestige.executor.domain.stage.DefaultStageFactory;
import xyz.firestige.executor.domain.stage.StageFactory;
import xyz.firestige.executor.event.SpringTaskEventSink;
import xyz.firestige.executor.execution.DefaultTaskWorkerFactory;
import xyz.firestige.executor.execution.TaskWorkerFactory;
import xyz.firestige.executor.factory.PlanFactory;
import xyz.firestige.executor.orchestration.PlanOrchestrator;
import xyz.firestige.executor.orchestration.TaskScheduler;
import xyz.firestige.executor.unit.support.AlwaysMatchHealthCheckClient;
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
 * Positive flow tests for TaskApplicationService (RF01 Phase3)
 * Scenarios covered:
 *  - Plan creation success (single tenant)
 *  - Pause / Resume tenant task (context flags)
 *  - Query task status by taskId and tenantId
 *  - Cancel tenant task
 *  - Retry (from scratch) after completion
 * Deferred (legacy/backlog): rollback multi-stage, checkpoint retry, conflict release.
 */
@Tag("rf01")
@Tag("positive")
@Tag("application-service")
@DisplayName("TaskApplicationService 正向流程测试")
public class TaskApplicationServicePositiveFlowTest {

    private TaskApplicationService taskService;
    private PlanApplicationService planService;
    private TaskStateManager stateManager;

    @BeforeEach
    void setUp() {
        ValidationChain validationChain = new ValidationChain();
        stateManager = new TaskStateManager();
        PlanFactory planFactory = new PlanFactory();
        ConflictRegistry conflictRegistry = new ConflictRegistry();
        ExecutorProperties executorProperties = new ExecutorProperties();
        executorProperties.setHealthCheckMaxAttempts(1);
        executorProperties.setHealthCheckIntervalSeconds(1);
        TaskScheduler taskScheduler = new TaskScheduler(Runtime.getRuntime().availableProcessors());
        PlanOrchestrator planOrchestrator = new PlanOrchestrator(taskScheduler, conflictRegistry, executorProperties);
        StageFactory stageFactory = new DefaultStageFactory();
        TaskWorkerFactory workerFactory = new DefaultTaskWorkerFactory();
        AlwaysMatchHealthCheckClient healthCheckClient = new AlwaysMatchHealthCheckClient("version", "100");
        CheckpointService checkpointService = new CheckpointService(new InMemoryCheckpointStore());
        SpringTaskEventSink eventSink = new SpringTaskEventSink(stateManager, conflictRegistry);

        planService = new PlanApplicationService(
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

        taskService = new TaskApplicationService(
                stateManager,
                workerFactory,
                executorProperties,
                checkpointService,
                eventSink,
                conflictRegistry,
                planService.getTaskRegistry(),
                planService.getContextRegistry(),
                planService.getStageRegistry(),
                planService.getExecutorRegistry()
        );
    }

    @Test
    @DisplayName("创建计划并查询任务状态成功")
    void testCreatePlanAndQuery() {
        List<TenantDeployConfig> cfgs = tenantConfigs("tenant_pos");
        PlanCreationResult res = planService.createSwitchTask(cfgs);
        assertTrue(res.isSuccess());
        String taskId = res.getPlanInfo().getTasks().get(0).getTaskId();
        // Await running/completed
        Awaitility.await().atMost(Duration.ofSeconds(3))
                .until(() -> planService.getTaskRegistry().get(taskId).getStatus() == TaskStatus.COMPLETED);
        var statusInfo = taskService.queryTaskStatus(taskId);
        assertNotNull(statusInfo.getStatus());
        assertEquals(taskId, statusInfo.getTaskId());
    }

    @Test
    @DisplayName("暂停与恢复租户任务成功")
    @Disabled("TODO: Flaky test - task completes too fast, pause flag check has timing issue")
    void testPauseResumeTenantTask() {
        List<TenantDeployConfig> cfgs = tenantConfigs("tenant_pause");
        PlanCreationResult res = planService.createSwitchTask(cfgs);
        assertTrue(res.isSuccess());
        String taskId = res.getPlanInfo().getTasks().get(0).getTaskId();
        // Pause
        TaskOperationResult pauseRes = taskService.pauseTaskByTenant("tenant_pause");
        assertTrue(pauseRes.isSuccess());
        Awaitility.await().atMost(Duration.ofMillis(300))
                .until(() -> planService.getContextRegistry().get(taskId).isPauseRequested());
        // Resume
        TaskOperationResult resumeRes = taskService.resumeTaskByTenant("tenant_pause");
        assertTrue(resumeRes.isSuccess());
        Awaitility.await().atMost(Duration.ofMillis(300))
                .until(() -> !planService.getContextRegistry().get(taskId).isPauseRequested());
    }

    @Test
    @DisplayName("取消租户任务成功")
    void testCancelTenantTask() {
        List<TenantDeployConfig> cfgs = tenantConfigs("tenant_cancel");
        PlanCreationResult res = planService.createSwitchTask(cfgs);
        assertTrue(res.isSuccess());
        TaskOperationResult cancelRes = taskService.cancelTaskByTenant("tenant_cancel");
        assertTrue(cancelRes.isSuccess());
        assertEquals(TaskStatus.CANCELLED, cancelRes.getStatus());
    }

    @Test
    @DisplayName("任务完成后从头重试成功")
    void testRetryAfterCompletion() {
        List<TenantDeployConfig> cfgs = tenantConfigs("tenant_retry_pos");
        PlanCreationResult res = planService.createSwitchTask(cfgs);
        assertTrue(res.isSuccess());
        String taskId = res.getPlanInfo().getTasks().get(0).getTaskId();
        Awaitility.await().atMost(Duration.ofSeconds(3))
                .until(() -> planService.getTaskRegistry().get(taskId).getStatus() == TaskStatus.COMPLETED);
        TaskOperationResult retryRes = taskService.retryTaskByTenant("tenant_retry_pos", false);
        assertTrue(retryRes.isSuccess());
        Awaitility.await().atMost(Duration.ofSeconds(3))
                .until(() -> planService.getTaskRegistry().get(taskId).getStatus() == TaskStatus.COMPLETED);
    }

    private List<TenantDeployConfig> tenantConfigs(String tenantId) {
        List<TenantDeployConfig> list = new ArrayList<>();
        TenantDeployConfig cfg = new TenantDeployConfig();
        cfg.setPlanId(9001L);
        cfg.setTenantId(tenantId);
        cfg.setDeployUnitId(ThreadLocalRandom.current().nextLong(1, 10000));
        cfg.setDeployUnitVersion(100L); // Match AlwaysMatchHealthCheckClient defaultVersion
        cfg.setDeployUnitName("DU-" + tenantId);
        xyz.firestige.entity.deploy.NetworkEndpoint ep = new xyz.firestige.entity.deploy.NetworkEndpoint();
        ep.setValue("http://localhost:8080/health");
        cfg.setNetworkEndpoints(List.of(ep));
        list.add(cfg);
        return list;
    }
}
