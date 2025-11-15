package xyz.firestige.executor.unit.orchestration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.orchestration.ExecutionMode;
import xyz.firestige.executor.orchestration.ExecutionUnit;
import xyz.firestige.executor.orchestration.ExecutionUnitStatus;
import xyz.firestige.executor.util.TestDataFactory;
import xyz.firestige.executor.util.TimingExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExecutionUnit 单元测试
 * 测试执行单的基本功能
 *
 * 预计执行时间：30 秒（4 个测试）
 */
@Tag("unit")
@Tag("fast")
@ExtendWith(TimingExtension.class)
@DisplayName("ExecutionUnit 单元测试")
class ExecutionUnitTest {

    @Test
    @DisplayName("场景 6.1.1: 创建执行单")
    void testCreateExecutionUnit() {
        // Given: 租户配置列表
        List<TenantDeployConfig> configs = TestDataFactory.createConfigList(3, 1001L);

        // When: 创建执行单
        ExecutionUnit unit = new ExecutionUnit(1001L, configs, ExecutionMode.CONCURRENT);

        // Then: 执行单属性正确
        assertNotNull(unit.getId(), "ID应该自动生成");
        assertEquals(1001L, unit.getPlanId(), "planId应该正确");
        assertEquals(3, unit.getTenantConfigs().size(), "应该有3个配置");
        assertEquals(ExecutionMode.CONCURRENT, unit.getExecutionMode(), "执行模式应该正确");
        assertEquals(ExecutionUnitStatus.CREATED, unit.getStatus(), "初始状态应该是CREATED");
    }

    @Test
    @DisplayName("场景 6.1.2: 获取租户ID列表")
    void testGetTenantIds() {
        // Given: 创建执行单
        List<TenantDeployConfig> configs = List.of(
            TestDataFactory.createMinimalConfig("tenant1", 1001L),
            TestDataFactory.createMinimalConfig("tenant2", 1001L),
            TestDataFactory.createMinimalConfig("tenant3", 1001L)
        );
        ExecutionUnit unit = new ExecutionUnit(1001L, configs, ExecutionMode.CONCURRENT);

        // When: 获取租户ID列表
        List<String> tenantIds = unit.getTenantIds();

        // Then: 包含所有租户ID
        assertEquals(3, tenantIds.size(), "应该有3个租户ID");
        assertTrue(tenantIds.contains("tenant1"), "应该包含tenant1");
        assertTrue(tenantIds.contains("tenant2"), "应该包含tenant2");
        assertTrue(tenantIds.contains("tenant3"), "应该包含tenant3");
    }

    @Test
    @DisplayName("场景 6.1.3: 检查是否包含租户")
    void testContainsTenant() {
        // Given: 创建执行单
        List<TenantDeployConfig> configs = List.of(
            TestDataFactory.createMinimalConfig("tenant1", 1001L),
            TestDataFactory.createMinimalConfig("tenant2", 1001L)
        );
        ExecutionUnit unit = new ExecutionUnit(1001L, configs, ExecutionMode.CONCURRENT);

        // When & Then: 检查租户
        assertTrue(unit.containsTenant("tenant1"), "应该包含tenant1");
        assertTrue(unit.containsTenant("tenant2"), "应该包含tenant2");
        assertFalse(unit.containsTenant("tenant3"), "不应该包含tenant3");
    }

    @Test
    @DisplayName("场景 6.1.4: 状态标记")
    void testStatusMarking() {
        // Given: 创建执行单
        List<TenantDeployConfig> configs = TestDataFactory.createConfigList(2);
        ExecutionUnit unit = new ExecutionUnit(1001L, configs, ExecutionMode.CONCURRENT);

        // Then: 初始状态
        assertEquals(ExecutionUnitStatus.CREATED, unit.getStatus());

        // When: 标记为已调度
        unit.markAsScheduled();
        assertEquals(ExecutionUnitStatus.SCHEDULED, unit.getStatus());

        // When: 标记为运行中
        unit.markAsRunning();
        assertEquals(ExecutionUnitStatus.RUNNING, unit.getStatus());

        // When: 标记为完成
        unit.markAsCompleted();
        assertEquals(ExecutionUnitStatus.COMPLETED, unit.getStatus());
    }
}

