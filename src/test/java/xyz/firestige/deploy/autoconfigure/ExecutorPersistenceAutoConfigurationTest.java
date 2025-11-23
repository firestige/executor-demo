package xyz.firestige.deploy.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import xyz.firestige.deploy.infrastructure.lock.TenantLockManager;
import xyz.firestige.deploy.infrastructure.persistence.projection.PlanStateProjectionStore;
import xyz.firestige.deploy.infrastructure.persistence.projection.TaskStateProjectionStore;
import xyz.firestige.deploy.infrastructure.persistence.projection.TenantTaskIndexStore;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-016 Phase 2: 投影持久化自动配置测试
 * <p>
 * 验证：
 * - Redis 投影存储 Bean 正确装配
 * - 租户锁管理器正确装配
 * - 内存 Fallback 正常工作
 */
@SpringBootTest
@TestPropertySource(properties = {
        "executor.persistence.store-type=memory"  // 测试使用内存存储
})
class ExecutorPersistenceAutoConfigurationTest {

    @Autowired(required = false)
    private TaskStateProjectionStore taskProjectionStore;

    @Autowired(required = false)
    private PlanStateProjectionStore planProjectionStore;

    @Autowired(required = false)
    private TenantTaskIndexStore tenantTaskIndexStore;

    @Autowired(required = false)
    private TenantLockManager tenantLockManager;

    @Test
    void shouldAutoConfigureProjectionStores() {
        assertThat(taskProjectionStore).isNotNull();
        assertThat(planProjectionStore).isNotNull();
        assertThat(tenantTaskIndexStore).isNotNull();
    }

    @Test
    void shouldAutoConfigureTenantLockManager() {
        assertThat(tenantLockManager).isNotNull();
    }

    @Test
    void taskProjectionStore_shouldBeInMemoryImplementation() {
        // 由于配置了 store-type=memory，应该使用内存实现
        assertThat(taskProjectionStore.getClass().getSimpleName())
                .contains("InMemory");
    }

    @Test
    void tenantLockManager_shouldBeInMemoryImplementation() {
        // 由于配置了 store-type=memory，应该使用内存实现
        assertThat(tenantLockManager.getClass().getSimpleName())
                .contains("InMemory");
    }
}

