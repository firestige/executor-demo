package xyz.firestige.executor.unit.facade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.facade.TaskCreationResult;
import xyz.firestige.executor.util.TestDataFactory;
import xyz.firestige.executor.util.TimingExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DeploymentTaskFacade 单元测试 - 简化版
 * 测试 TaskCreationResult 等结果对象的基本功能
 *
 * 注意：由于 Mockito 与 Java 21 的兼容性问题，
 * 这里只测试结果对象，不测试 Facade 的完整流程
 *
 * 预计执行时间：10 秒（3 个测试）
 */
@Tag("unit")
@Tag("fast")
@ExtendWith(TimingExtension.class)
@DisplayName("DeploymentTaskFacade 结果对象测试")
class DeploymentTaskFacadeTest {

    @Test
    @DisplayName("场景: TaskCreationResult 成功结果")
    void testTaskCreationResultSuccess() {
        // Given: 成功的任务 ID 列表
        List<String> executionUnitIds = List.of("unit-001", "unit-002");

        // When: 创建成功结果
        TaskCreationResult result = TaskCreationResult.success("task-123", executionUnitIds);

        // Then: 结果正确
        assertTrue(result.isSuccess(), "应该是成功的");
        assertEquals("task-123", result.getPlanId());
        assertEquals(2, result.getTaskIds().size());
        assertEquals("unit-001", result.getTaskIds().get(0));
    }

    @Test
    @DisplayName("场景: TaskCreationResult 失败结果")
    void testTaskCreationResultFailure() {
        // Given: 失败信息
        xyz.firestige.executor.exception.FailureInfo failureInfo =
            xyz.firestige.executor.exception.FailureInfo.of(
                xyz.firestige.executor.exception.ErrorType.VALIDATION_ERROR,
                "配置校验失败"
            );

        // When: 创建失败结果
        TaskCreationResult result = TaskCreationResult.failure(failureInfo, "创建失败");

        // Then: 结果正确
        assertFalse(result.isSuccess(), "应该是失败的");
        assertNotNull(result.getFailureInfo());
        assertEquals("创建失败", result.getMessage());
    }

    @Test
    @DisplayName("场景: TaskCreationResult 校验失败结果")
    void testTaskCreationResultValidationFailure() {
        // Given: 校验摘要
        xyz.firestige.executor.validation.ValidationSummary summary =
            new xyz.firestige.executor.validation.ValidationSummary();
        summary.setTotalConfigs(3);

        // 添加无效配置
        List<TenantDeployConfig> configs = TestDataFactory.createConfigList(1);
        summary.addInvalidConfig(
            configs.get(0),
            List.of(xyz.firestige.executor.validation.ValidationError.of("field", "错误"))
        );

        // When: 创建校验失败结果
        TaskCreationResult result = TaskCreationResult.validationFailure(summary);

        // Then: 结果正确
        assertFalse(result.isSuccess(), "应该是失败的");
        assertNotNull(result.getValidationSummary());
        assertEquals("配置校验失败", result.getMessage());
        assertEquals(1, result.getValidationSummary().getInvalidCount());
    }
}

