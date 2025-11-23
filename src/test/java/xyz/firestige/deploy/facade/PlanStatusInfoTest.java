package xyz.firestige.deploy.facade;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import xyz.firestige.deploy.domain.plan.PlanStatus;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.infrastructure.persistence.projection.PlanStateProjection;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * PlanStatusInfo DTO 单元测试
 *
 * 测试目标：
 * - fromProjection 转换正确性
 * - 字段映射完整性
 */
@DisplayName("PlanStatusInfo DTO 测试")
class PlanStatusInfoTest {

    @Test
    @DisplayName("从投影转换 - 成功场景")
    void fromProjection_success() {
        // Given
        PlanId planId = PlanId.of("plan-" + UUID.randomUUID());
        List<TaskId> taskIds = List.of(
            TaskId.of("task-" + UUID.randomUUID()),
            TaskId.of("task-" + UUID.randomUUID()),
            TaskId.of("task-" + UUID.randomUUID())
        );
        LocalDateTime now = LocalDateTime.now();

        PlanStateProjection projection = PlanStateProjection.builder()
            .planId(planId)
            .status(PlanStatus.RUNNING)
            .taskIds(taskIds)
            .maxConcurrency(5)
            .createdAt(now.minusHours(1))
            .updatedAt(now)
            .build();

        // When
        PlanStatusInfo info = PlanStatusInfo.fromProjection(projection);

        // Then
        assertThat(info).isNotNull();
        assertThat(info.getPlanId()).isEqualTo(planId);
        assertThat(info.getStatus()).isEqualTo(PlanStatus.RUNNING);
        assertThat(info.getTaskCount()).isEqualTo(3);
        assertThat(info.getTaskIds()).hasSize(3);
        assertThat(info.getTaskIds().get(0)).isEqualTo(taskIds.get(0).getValue());
        assertThat(info.getMaxConcurrency()).isEqualTo(5);
        assertThat(info.getCreatedAt()).isEqualTo(now.minusHours(1));
        assertThat(info.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("从投影转换 - 空任务列表")
    void fromProjection_emptyTaskList() {
        // Given
        PlanStateProjection projection = PlanStateProjection.builder()
            .planId(PlanId.of("plan-" + UUID.randomUUID()))
            .status(PlanStatus.READY)
            .taskIds(List.of())
            .maxConcurrency(10)
            .build();

        // When
        PlanStatusInfo info = PlanStatusInfo.fromProjection(projection);

        // Then
        assertThat(info.getTaskCount()).isEqualTo(0);
        assertThat(info.getTaskIds()).isEmpty();
    }

    @Test
    @DisplayName("从投影转换 - null任务列表")
    void fromProjection_nullTaskList() {
        // Given
        PlanStateProjection projection = PlanStateProjection.builder()
            .planId(PlanId.of("plan-" + UUID.randomUUID()))
            .status(PlanStatus.COMPLETED)
            .taskIds(null)
            .maxConcurrency(3)
            .build();

        // When
        PlanStatusInfo info = PlanStatusInfo.fromProjection(projection);

        // Then
        assertThat(info.getTaskCount()).isEqualTo(0);
        assertThat(info.getTaskIds()).isEmpty();
    }

    @Test
    @DisplayName("TaskId转字符串 - 验证不会抛异常")
    void fromProjection_taskIdToString() {
        // Given
        TaskId taskId = TaskId.of("task-test-123");
        PlanStateProjection projection = PlanStateProjection.builder()
            .planId(PlanId.of("plan-" + UUID.randomUUID()))
            .status(PlanStatus.RUNNING)
            .taskIds(List.of(taskId))
            .maxConcurrency(1)
            .build();

        // When
        PlanStatusInfo info = PlanStatusInfo.fromProjection(projection);

        // Then
        assertThat(info.getTaskIds()).containsExactly("task-test-123");
    }
}

