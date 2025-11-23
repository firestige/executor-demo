package xyz.firestige.deploy.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import xyz.firestige.deploy.application.query.TaskQueryService;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.domain.task.TaskStatus;
import xyz.firestige.deploy.facade.PlanStatusInfo;
import xyz.firestige.deploy.facade.TaskStatusInfo;
import xyz.firestige.deploy.infrastructure.persistence.projection.*;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * T-016 Phase 4 查询 API 集成测试
 *
 * 测试目标：
 * - 投影存储与查询服务集成
 * - Facade 查询方法端到端验证
 * - hasCheckpoint 判断逻辑验证
 */
@SpringBootTest
@TestPropertySource(properties = {
    "executor.persistence.store-type=memory",
    "executor.checkpoint.store-type=memory"
})
@DisplayName("Phase4 查询API集成测试")
class Phase4QueryApiIntegrationTest {

    @Autowired
    private TaskQueryService taskQueryService;

    @Autowired
    private TaskStateProjectionStore taskProjectionStore;

    @Autowired
    private PlanStateProjectionStore planProjectionStore;

    @Autowired
    private TenantTaskIndexStore tenantTaskIndexStore;

    @Test
    @DisplayName("完整流程：保存投影 → 查询任务状态")
    void fullFlow_saveProjection_thenQuery() {
        // Given: 创建并保存任务投影
        TenantId tenantId = TenantId.of("tenant-phase4-001");
        TaskId taskId = TaskId.of("task-" + UUID.randomUUID());
        PlanId planId = PlanId.of("plan-" + UUID.randomUUID());

        TaskStateProjection projection = TaskStateProjection.builder()
            .taskId(taskId)
            .tenantId(tenantId)
            .planId(planId)
            .status(TaskStatus.RUNNING)
            .stageNames(List.of("Init", "Deploy", "Verify"))
            .lastCompletedStageIndex(1) // 完成了 Init 和 Deploy
            .build();

        taskProjectionStore.save(projection);
        tenantTaskIndexStore.put(tenantId, taskId);

        // When: 通过租户ID查询
        TaskStatusInfo result = taskQueryService.queryByTenantId(tenantId);

        // Then: 验证返回数据
        assertThat(result).isNotNull();
        assertThat(result.getTaskId()).isEqualTo(taskId);
        assertThat(result.getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(result.getCurrentStage()).isEqualTo(2); // lastCompletedStageIndex + 1
        assertThat(result.getTotalStages()).isEqualTo(3);
    }

    @Test
    @DisplayName("hasCheckpoint：未完成任何阶段 → false")
    void hasCheckpoint_noneCompleted_returnsFalse() {
        // Given
        TenantId tenantId = TenantId.of("tenant-phase4-002");
        TaskId taskId = TaskId.of("task-" + UUID.randomUUID());

        TaskStateProjection projection = TaskStateProjection.builder()
            .taskId(taskId)
            .tenantId(tenantId)
            .planId(PlanId.of("plan-" + UUID.randomUUID()))
            .status(TaskStatus.PENDING)
            .stageNames(List.of("Stage1", "Stage2"))
            .lastCompletedStageIndex(-1) // 未完成
            .build();

        taskProjectionStore.save(projection);
        tenantTaskIndexStore.put(tenantId, taskId);

        // When
        boolean hasCheckpoint = taskQueryService.hasCheckpoint(tenantId);

        // Then
        assertThat(hasCheckpoint).isFalse();
    }

    @Test
    @DisplayName("hasCheckpoint：完成至少一个阶段 → true")
    void hasCheckpoint_someCompleted_returnsTrue() {
        // Given
        TenantId tenantId = TenantId.of("tenant-phase4-003");
        TaskId taskId = TaskId.of("task-" + UUID.randomUUID());

        TaskStateProjection projection = TaskStateProjection.builder()
            .taskId(taskId)
            .tenantId(tenantId)
            .planId(PlanId.of("plan-" + UUID.randomUUID()))
            .status(TaskStatus.RUNNING)
            .stageNames(List.of("Stage1", "Stage2", "Stage3"))
            .lastCompletedStageIndex(0) // 完成了第一个阶段
            .build();

        taskProjectionStore.save(projection);
        tenantTaskIndexStore.put(tenantId, taskId);

        // When
        boolean hasCheckpoint = taskQueryService.hasCheckpoint(tenantId);

        // Then
        assertThat(hasCheckpoint).isTrue();
    }

    @Test
    @DisplayName("查询Plan状态：保存 → 查询")
    void queryPlanStatus_saveAndLoad() {
        // Given
        PlanId planId = PlanId.of("plan-" + UUID.randomUUID());
        List<TaskId> taskIds = List.of(
            TaskId.of("task-" + UUID.randomUUID()),
            TaskId.of("task-" + UUID.randomUUID())
        );

        PlanStateProjection projection = PlanStateProjection.builder()
            .planId(planId)
            .status(xyz.firestige.deploy.domain.plan.PlanStatus.RUNNING)
            .taskIds(taskIds)
            .maxConcurrency(5)
            .build();

        planProjectionStore.save(projection);

        // When
        PlanStateProjection loaded = taskQueryService.queryPlanStatus(planId);

        // Then
        assertThat(loaded).isNotNull();
        assertThat(loaded.getPlanId()).isEqualTo(planId);
        assertThat(loaded.getStatus()).isEqualTo(xyz.firestige.deploy.domain.plan.PlanStatus.RUNNING);
        assertThat(loaded.getTaskIds()).hasSize(2);
        assertThat(loaded.getMaxConcurrency()).isEqualTo(5);
    }

    @Test
    @DisplayName("查询不存在的租户 → 返回失败信息")
    void queryByTenantId_notFound_returnsFailure() {
        // Given
        TenantId unknownTenant = TenantId.of("tenant-unknown-999");

        // When
        TaskStatusInfo result = taskQueryService.queryByTenantId(unknownTenant);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTaskId()).isNull();
        assertThat(result.getStatus()).isNull();
        assertThat(result.getMessage()).contains("未找到租户对应的任务");
    }

    @Test
    @DisplayName("更新投影后查询 → 返回最新状态")
    void updateProjection_thenQuery_returnsLatest() {
        // Given: 初始保存
        TenantId tenantId = TenantId.of("tenant-phase4-004");
        TaskId taskId = TaskId.of("task-" + UUID.randomUUID());

        TaskStateProjection projection = TaskStateProjection.builder()
            .taskId(taskId)
            .tenantId(tenantId)
            .planId(PlanId.of("plan-" + UUID.randomUUID()))
            .status(TaskStatus.RUNNING)
            .stageNames(List.of("S1", "S2"))
            .lastCompletedStageIndex(-1)
            .build();

        taskProjectionStore.save(projection);
        tenantTaskIndexStore.put(tenantId, taskId);

        // When: 更新状态（模拟事件监听器行为）
        projection.setStatus(TaskStatus.COMPLETED);
        projection.setLastCompletedStageIndex(1);
        taskProjectionStore.save(projection);

        // Then: 查询应返回最新状态
        TaskStatusInfo result = taskQueryService.queryByTenantId(tenantId);
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.getCurrentStage()).isEqualTo(2);

        boolean hasCheckpoint = taskQueryService.hasCheckpoint(tenantId);
        assertThat(hasCheckpoint).isTrue();
    }

    @Test
    @DisplayName("删除投影后查询 → 返回不存在")
    void removeProjection_thenQuery_returnsNotFound() {
        // Given
        TenantId tenantId = TenantId.of("tenant-phase4-005");
        TaskId taskId = TaskId.of("task-" + UUID.randomUUID());

        TaskStateProjection projection = TaskStateProjection.builder()
            .taskId(taskId)
            .tenantId(tenantId)
            .planId(PlanId.of("plan-" + UUID.randomUUID()))
            .status(TaskStatus.COMPLETED)
            .stageNames(List.of("S1"))
            .lastCompletedStageIndex(0)
            .build();

        taskProjectionStore.save(projection);
        tenantTaskIndexStore.put(tenantId, taskId);

        // When: 删除投影（模拟清理）
        taskProjectionStore.remove(taskId);

        // Then: 查询返回失败
        TaskStatusInfo result = taskQueryService.queryByTenantId(tenantId);
        assertThat(result.getMessage()).contains("未找到任务状态");
    }
}

