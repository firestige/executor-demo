package xyz.firestige.executor.unit.execution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.execution.pipeline.PipelineContext;
import xyz.firestige.executor.util.TestDataFactory;
import xyz.firestige.executor.util.TimingExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PipelineContext 单元测试
 * 测试 Pipeline 上下文数据传递
 *
 * 预计执行时间：40 秒（5 个测试）
 */
@Tag("unit")
@Tag("fast")
@ExtendWith(TimingExtension.class)
@DisplayName("PipelineContext 单元测试")
class PipelineContextTest {

    private PipelineContext context;

    @BeforeEach
    void setUp() {
        TenantDeployConfig config = TestDataFactory.createMinimalConfig("tenant1", 1001L);
        context = new PipelineContext("task1", config);
    }

    @Test
    @DisplayName("场景 4.1.1: 存储和获取累积数据")
    void testPutAndGetData() {
        // When: 存储数据
        context.putData("key1", "value1");
        context.putData("key2", 123);
        context.putData("key3", true);

        // Then: 可以获取数据
        assertEquals("value1", context.getData("key1"));
        assertEquals(123, context.getData("key2"));
        assertEquals(true, context.getData("key3"));
    }

    @Test
    @DisplayName("场景 4.1.2: 类型安全的数据获取")
    void testTypeSafeDataRetrieval() {
        // Given: 存储不同类型的数据
        context.putData("stringKey", "test");
        context.putData("intKey", 42);

        // When: 使用类型安全的方法获取
        String strValue = context.getData("stringKey", String.class);
        Integer intValue = context.getData("intKey", Integer.class);

        // Then: 返回正确类型的数据
        assertEquals("test", strValue);
        assertEquals(42, intValue);
    }

    @Test
    @DisplayName("场景 4.1.3: 暂停标志控制")
    void testPauseControl() {
        // Given: 初始状态
        assertFalse(context.shouldPause(), "初始不应该暂停");

        // When: 请求暂停
        context.requestPause();

        // Then: 应该暂停
        assertTrue(context.shouldPause(), "请求暂停后应该返回true");

        // When: 恢复
        context.resume();

        // Then: 不应该暂停
        assertFalse(context.shouldPause(), "恢复后应该返回false");
    }

    @Test
    @DisplayName("场景 4.1.4: 恢复标志控制")
    void testResumeControl() {
        // Given: 先暂停
        context.requestPause();
        assertTrue(context.shouldPause());

        // When: 恢复
        context.resume();

        // Then: 不再暂停
        assertFalse(context.shouldPause(), "恢复后不应该暂停");
    }

    @Test
    @DisplayName("场景 4.1.5: 取消标志控制")
    void testCancelControl() {
        // Given: 初始状态
        assertFalse(context.shouldCancel(), "初始不应该取消");

        // When: 请求取消
        context.requestCancel();

        // Then: 应该取消
        assertTrue(context.shouldCancel(), "请求取消后应该返回true");
    }
}

