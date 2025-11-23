package xyz.firestige.deploy.application.query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.domain.task.TaskStatus;
import xyz.firestige.deploy.facade.TaskStatusInfo;
import xyz.firestige.deploy.infrastructure.persistence.projection.PlanStateProjection;
import xyz.firestige.deploy.infrastructure.persistence.projection.PlanStateProjectionStore;
import xyz.firestige.deploy.infrastructure.persistence.projection.TaskStateProjection;
import xyz.firestige.deploy.infrastructure.persistence.projection.TaskStateProjectionStore;
import xyz.firestige.deploy.infrastructure.persistence.projection.TenantTaskIndexStore;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * TaskQueryService 单元测试
 *
 * 测试目标：
 * - 查询逻辑正确性
 * - 空值处理
 * - hasCheckpoint 判断逻辑
 */
@DisplayName("TaskQueryService 单元测试")
class TaskQueryServiceTest {

    private TaskQueryService queryService;
    private TaskStateProjectionStore taskProjectionStore;
    private PlanStateProjectionStore planProjectionStore;
    private TenantTaskIndexStore tenantTaskIndexStore;

    @BeforeEach
    void setUp() {
        taskProjectionStore = mock(TaskStateProjectionStore.class);
        planProjectionStore = mock(PlanStateProjectionStore.class);
        tenantTaskIndexStore = mock(TenantTaskIndexStore.class);

        queryService = new TaskQueryService(
            taskProjectionStore,
            planProjectionStore,
            tenantTaskIndexStore
        );
    }

    @Test
    @DisplayName("通过租户ID查询任务状态 - 成功场景")
    void queryByTenantId_success() {
        // Given
        TenantId tenantId = TenantId.of("tenant-001");
        TaskId taskId = TaskId.of("task-" + UUID.randomUUID());
        PlanId planId = PlanId.of("plan-" + UUID.randomUUID());

        TaskStateProjection projection = TaskStateProjection.builder()
            .taskId(taskId)
            .tenantId(tenantId)
            .planId(planId)
            .status(TaskStatus.RUNNING)
            .stageNames(List.of("stage-1", "stage-2", "stage-3"))
            .lastCompletedStageIndex(1) // 完成了2个阶段
            .build();

        when(tenantTaskIndexStore.get(tenantId)).thenReturn(taskId);
        when(taskProjectionStore.load(taskId)).thenReturn(projection);

        // When
        TaskStatusInfo result = queryService.queryByTenantId(tenantId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTaskId()).isEqualTo(taskId);
        assertThat(result.getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(result.getCurrentStage()).isEqualTo(2); // lastCompletedStageIndex + 1
        assertThat(result.getTotalStages()).isEqualTo(3);
        assertThat(result.getMessage()).contains("查询成功");

        verify(tenantTaskIndexStore).get(tenantId);
        verify(taskProjectionStore).load(taskId);
    }

    @Test
    @DisplayName("通过租户ID查询任务状态 - 索引不存在")
    void queryByTenantId_indexNotFound() {
        // Given
        TenantId tenantId = TenantId.of("tenant-unknown");
        when(tenantTaskIndexStore.get(tenantId)).thenReturn(null);

        // When
        TaskStatusInfo result = queryService.queryByTenantId(tenantId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTaskId()).isNull();
        assertThat(result.getStatus()).isNull();
        assertThat(result.getMessage()).contains("未找到租户对应的任务");

        verify(tenantTaskIndexStore).get(tenantId);
        verify(taskProjectionStore, never()).load(any());
    }

    @Test
    @DisplayName("通过租户ID查询任务状态 - 投影不存在")
    void queryByTenantId_projectionNotFound() {
        // Given
        TenantId tenantId = TenantId.of("tenant-002");
        TaskId taskId = TaskId.of("task-" + UUID.randomUUID());

        when(tenantTaskIndexStore.get(tenantId)).thenReturn(taskId);
        when(taskProjectionStore.load(taskId)).thenReturn(null);

        // When
        TaskStatusInfo result = queryService.queryByTenantId(tenantId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTaskId()).isNull();
        assertThat(result.getStatus()).isNull();
        assertThat(result.getMessage()).contains("未找到任务状态");

        verify(tenantTaskIndexStore).get(tenantId);
        verify(taskProjectionStore).load(taskId);
    }

    @Test
    @DisplayName("查询Plan状态 - 成功")
    void queryPlanStatus_success() {
        // Given
        PlanId planId = PlanId.of("plan-" + UUID.randomUUID());
        PlanStateProjection projection = PlanStateProjection.builder()
            .planId(planId)
            .status(xyz.firestige.deploy.domain.plan.PlanStatus.RUNNING)
            .maxConcurrency(5)
            .build();

        when(planProjectionStore.load(planId)).thenReturn(projection);

        // When
        PlanStateProjection result = queryService.queryPlanStatus(planId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getPlanId()).isEqualTo(planId);
        assertThat(result.getStatus()).isEqualTo(xyz.firestige.deploy.domain.plan.PlanStatus.RUNNING);

        verify(planProjectionStore).load(planId);
    }

    @Test
    @DisplayName("查询Plan状态 - 不存在")
    void queryPlanStatus_notFound() {
        // Given
        PlanId planId = PlanId.of("plan-" + UUID.randomUUID());
        when(planProjectionStore.load(planId)).thenReturn(null);

        // When
        PlanStateProjection result = queryService.queryPlanStatus(planId);

        // Then
        assertThat(result).isNull();
        verify(planProjectionStore).load(planId);
    }

    @Test
    @DisplayName("hasCheckpoint - 有checkpoint（lastCompletedStageIndex >= 0）")
    void hasCheckpoint_true() {
        // Given
        TenantId tenantId = TenantId.of("tenant-003");
        TaskId taskId = TaskId.of("task-" + UUID.randomUUID());

        TaskStateProjection projection = TaskStateProjection.builder()
            .taskId(taskId)
            .tenantId(tenantId)
            .planId(PlanId.of("plan-" + UUID.randomUUID()))
            .status(TaskStatus.RUNNING)
            .stageNames(List.of("stage-1", "stage-2"))
            .lastCompletedStageIndex(0) // 至少完成一个阶段
            .build();

        when(tenantTaskIndexStore.get(tenantId)).thenReturn(taskId);
        when(taskProjectionStore.load(taskId)).thenReturn(projection);

        // When
        boolean result = queryService.hasCheckpoint(tenantId);

        // Then
        assertThat(result).isTrue();

        verify(tenantTaskIndexStore).get(tenantId);
        verify(taskProjectionStore).load(taskId);
    }

    @Test
    @DisplayName("hasCheckpoint - 无checkpoint（lastCompletedStageIndex = -1）")
    void hasCheckpoint_false_noneCompleted() {
        // Given
        TenantId tenantId = TenantId.of("tenant-004");
        TaskId taskId = TaskId.of("task-" + UUID.randomUUID());

        TaskStateProjection projection = TaskStateProjection.builder()
            .taskId(taskId)
            .tenantId(tenantId)
            .planId(PlanId.of("plan-" + UUID.randomUUID()))
            .status(TaskStatus.PENDING)
            .stageNames(List.of("stage-1"))
            .lastCompletedStageIndex(-1) // 未完成任何阶段
            .build();

        when(tenantTaskIndexStore.get(tenantId)).thenReturn(taskId);
        when(taskProjectionStore.load(taskId)).thenReturn(projection);

        // When
        boolean result = queryService.hasCheckpoint(tenantId);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("hasCheckpoint - 租户索引不存在")
    void hasCheckpoint_false_tenantNotFound() {
        // Given
        TenantId tenantId = TenantId.of("tenant-unknown");
        when(tenantTaskIndexStore.get(tenantId)).thenReturn(null);

        // When
        boolean result = queryService.hasCheckpoint(tenantId);

        // Then
        assertThat(result).isFalse();
        verify(tenantTaskIndexStore).get(tenantId);
        verify(taskProjectionStore, never()).load(any());
    }

    @Test
    @DisplayName("hasCheckpoint - 投影不存在")
    void hasCheckpoint_false_projectionNotFound() {
        // Given
        TenantId tenantId = TenantId.of("tenant-005");
        TaskId taskId = TaskId.of("task-" + UUID.randomUUID());

        when(tenantTaskIndexStore.get(tenantId)).thenReturn(taskId);
        when(taskProjectionStore.load(taskId)).thenReturn(null);

        // When
        boolean result = queryService.hasCheckpoint(tenantId);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("通过TaskId查询投影 - 成功")
    void queryTaskStatus_byTaskId_success() {
        // Given
        TaskId taskId = TaskId.of("task-" + UUID.randomUUID());
        TaskStateProjection projection = TaskStateProjection.builder()
            .taskId(taskId)
            .tenantId(TenantId.of("tenant-006"))
            .planId(PlanId.of("plan-" + UUID.randomUUID()))
            .status(TaskStatus.COMPLETED)
            .stageNames(List.of("stage-1", "stage-2"))
            .lastCompletedStageIndex(1)
            .build();

        when(taskProjectionStore.load(taskId)).thenReturn(projection);

        // When
        TaskStateProjection result = queryService.queryTaskStatus(taskId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTaskId()).isEqualTo(taskId);
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);

        verify(taskProjectionStore).load(taskId);
    }
}

