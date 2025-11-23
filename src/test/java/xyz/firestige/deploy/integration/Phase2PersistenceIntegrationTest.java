package xyz.firestige.deploy.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import xyz.firestige.deploy.application.query.TaskQueryService;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.infrastructure.lock.TenantLockManager;
import xyz.firestige.deploy.infrastructure.persistence.projection.PlanStateProjectionStore;
import xyz.firestige.deploy.infrastructure.persistence.projection.TaskStateProjectionStore;
import xyz.firestige.deploy.infrastructure.persistence.projection.TaskStateProjection;
import xyz.firestige.deploy.infrastructure.persistence.projection.TenantTaskIndexStore;
import xyz.firestige.deploy.domain.task.TaskStatus;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-016 Phase 2: 状态持久化集成测试
 * <p>
 * 验证：
 * - 投影存储正常工作
 * - 租户索引正常工作
 * - 租户锁正常工作
 * - TaskQueryService 正常工作
 */
@SpringBootTest
@TestPropertySource(properties = {
        "executor.persistence.store-type=memory",  // 使用内存存储进行测试
        "executor.checkpoint.store-type=memory"
})
class Phase2PersistenceIntegrationTest {

    @Autowired
    private TaskStateProjectionStore taskProjectionStore;

    @Autowired
    private PlanStateProjectionStore planProjectionStore;

    @Autowired
    private TenantTaskIndexStore tenantTaskIndexStore;

    @Autowired
    private TenantLockManager tenantLockManager;

    @Autowired
    private TaskQueryService taskQueryService;

    @Test
    void contextLoads() {
        // 验证所有 Bean 都成功装配
        assertThat(taskProjectionStore).isNotNull();
        assertThat(planProjectionStore).isNotNull();
        assertThat(tenantTaskIndexStore).isNotNull();
        assertThat(tenantLockManager).isNotNull();
        assertThat(taskQueryService).isNotNull();
    }

    @Test
    void shouldSaveAndLoadTaskProjection() {
        // Given: 创建一个 Task 投影
        TaskId taskId = TaskId.of("task-" + UUID.randomUUID());
        TenantId tenantId = TenantId.of("tenant-001");

        TaskStateProjection projection = TaskStateProjection.builder()
                .taskId(taskId)
                .tenantId(tenantId)
                .planId(xyz.firestige.deploy.domain.shared.vo.PlanId.of("plan-" + UUID.randomUUID()))
                .status(TaskStatus.PENDING)
                .stageNames(List.of("stage-1", "stage-2", "stage-3"))
                .lastCompletedStageIndex(-1)
                .build();

        // When: 保存投影
        taskProjectionStore.save(projection);

        // Then: 可以加载投影
        TaskStateProjection loaded = taskProjectionStore.load(taskId);
        assertThat(loaded).isNotNull();
        assertThat(loaded.getTaskId()).isEqualTo(taskId);
        assertThat(loaded.getTenantId()).isEqualTo(tenantId);
        assertThat(loaded.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(loaded.getStageNames()).hasSize(3);
    }

    @Test
    void shouldMaintainTenantTaskIndex() {
        // Given: 创建租户和任务
        TenantId tenantId = TenantId.of("tenant-002");
        TaskId taskId = TaskId.of("task-" + UUID.randomUUID());

        // When: 建立索引
        tenantTaskIndexStore.put(tenantId, taskId);

        // Then: 可以通过租户 ID 查询任务 ID
        TaskId foundTaskId = tenantTaskIndexStore.get(tenantId);
        assertThat(foundTaskId).isEqualTo(taskId);
    }

    @Test
    void shouldManageTenantLock() {
        // Given: 租户和任务
        TenantId tenantId = TenantId.of("tenant-003");
        TaskId taskId = TaskId.of("task-" + UUID.randomUUID());

        // When: 尝试获取锁
        boolean acquired = tenantLockManager.tryAcquire(tenantId, taskId, Duration.ofMinutes(5));

        // Then: 成功获取锁
        assertThat(acquired).isTrue();
        assertThat(tenantLockManager.exists(tenantId)).isTrue();

        // When: 再次尝试获取锁（同一租户）
        TaskId anotherTaskId = TaskId.of("task-" + UUID.randomUUID());
        boolean acquiredAgain = tenantLockManager.tryAcquire(tenantId, anotherTaskId, Duration.ofMinutes(5));

        // Then: 失败（锁已被占用）
        assertThat(acquiredAgain).isFalse();

        // When: 释放锁
        tenantLockManager.release(tenantId);

        // Then: 锁不再存在
        assertThat(tenantLockManager.exists(tenantId)).isFalse();

        // When: 再次尝试获取锁
        boolean reacquired = tenantLockManager.tryAcquire(tenantId, anotherTaskId, Duration.ofMinutes(5));

        // Then: 成功（锁已释放）
        assertThat(reacquired).isTrue();
    }

    @Test
    void shouldUpdateProjectionStatus() {
        // Given: 创建并保存投影
        TaskId taskId = TaskId.of("task-" + UUID.randomUUID());
        TenantId tenantId = TenantId.of("tenant-004");

        TaskStateProjection projection = TaskStateProjection.builder()
                .taskId(taskId)
                .tenantId(tenantId)
                .planId(xyz.firestige.deploy.domain.shared.vo.PlanId.of("plan-" + UUID.randomUUID()))
                .status(TaskStatus.PENDING)
                .stageNames(List.of("stage-1"))
                .build();

        taskProjectionStore.save(projection);

        // When: 更新状态
        TaskStateProjection loaded = taskProjectionStore.load(taskId);
        loaded.setStatus(TaskStatus.RUNNING);
        taskProjectionStore.save(loaded);

        // Then: 状态已更新
        TaskStateProjection updated = taskProjectionStore.load(taskId);
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.RUNNING);
    }

    @Test
    void shouldHandleProjectionRemoval() {
        // Given: 创建并保存投影
        TaskId taskId = TaskId.of("task-" + UUID.randomUUID());
        TenantId tenantId = TenantId.of("tenant-005");

        TaskStateProjection projection = TaskStateProjection.builder()
                .taskId(taskId)
                .tenantId(tenantId)
                .planId(xyz.firestige.deploy.domain.shared.vo.PlanId.of("plan-" + UUID.randomUUID()))
                .status(TaskStatus.COMPLETED)
                .stageNames(List.of())
                .build();

        taskProjectionStore.save(projection);
        assertThat(taskProjectionStore.load(taskId)).isNotNull();

        // When: 删除投影
        taskProjectionStore.remove(taskId);

        // Then: 投影不再存在
        assertThat(taskProjectionStore.load(taskId)).isNull();
    }
}

