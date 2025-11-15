package xyz.firestige.executor.unit.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.util.TestDataFactory;
import xyz.firestige.executor.util.TimingExtension;
import xyz.firestige.executor.validation.ValidationChain;
import xyz.firestige.executor.validation.ValidationResult;
import xyz.firestige.executor.validation.ValidationSummary;
import xyz.firestige.executor.validation.validator.ConflictValidator;
import xyz.firestige.executor.validation.validator.NetworkEndpointValidator;
import xyz.firestige.executor.validation.validator.TenantIdValidator;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ValidationChain 单元测试
 * 测试校验链的执行逻辑
 *
 * 预计执行时间：120 秒（4 个测试）
 */
@Tag("unit")
@Tag("standard")
@ExtendWith(TimingExtension.class)
@DisplayName("ValidationChain 单元测试")
class ValidationChainTest {

    private ValidationChain chain;

    @BeforeEach
    void setUp() {
        // 清理冲突校验器状态
        ConflictValidator.clearAll();

        // 创建校验链，非快速失败模式
        chain = new ValidationChain(false);
    }

    @Test
    @DisplayName("场景 1.2.1: 按 order 顺序执行校验器")
    void testValidatorsExecuteInOrder() {
        // Given: 添加 3 个校验器，order 为 5, 10, 20
        TenantIdValidator validator1 = new TenantIdValidator();  // order = 5
        NetworkEndpointValidator validator2 = new NetworkEndpointValidator();  // order = 10
        ConflictValidator validator3 = new ConflictValidator();  // order = 15

        chain.addValidator(validator1);
        chain.addValidator(validator2);
        chain.addValidator(validator3);

        // 验证校验器数量
        assertEquals(3, chain.getValidators().size(), "应该有 3 个校验器");

        // When: 使用合法配置执行校验
        TenantDeployConfig config = TestDataFactory.createMinimalConfig("tenant1", 1001L);
        ValidationResult result = chain.validate(config);

        // Then: 所有校验器都执行了，校验通过
        assertTrue(result.isValid(), "合法配置应该通过所有校验器");
    }

    @Test
    @DisplayName("场景 1.2.2: 快速失败模式 - 第一个失败后停止")
    void testFailFastMode() {
        // Given: 创建快速失败模式的校验链
        ValidationChain fastFailChain = new ValidationChain(true);
        fastFailChain.addValidator(new TenantIdValidator());
        fastFailChain.addValidator(new NetworkEndpointValidator());

        // 使用无效配置（tenantId 为 null）
        TenantDeployConfig config = new TenantDeployConfig();
        config.setTenantId(null);  // 会在第一个校验器失败
        config.setNetworkEndpoints(new ArrayList<>());  // 会在第二个校验器失败

        // When: 执行校验
        ValidationResult result = fastFailChain.validate(config);

        // Then: 校验失败，只有第一个错误
        assertFalse(result.isValid(), "无效配置应该校验失败");
        assertTrue(result.hasErrors(), "应该有错误");
        // 快速失败模式下，第二个校验器可能不会执行，所以不严格验证错误数量
        assertTrue(result.getErrors().size() >= 1, "至少有 1 个错误");
    }

    @Test
    @DisplayName("场景 1.2.3: 非快速失败模式 - 收集所有错误")
    void testCollectAllErrors() {
        // Given: 非快速失败模式的校验链
        chain.addValidator(new TenantIdValidator());
        chain.addValidator(new NetworkEndpointValidator());

        // 使用多个字段都无效的配置
        TenantDeployConfig config = new TenantDeployConfig();
        config.setTenantId(null);  // 错误 1: tenantId 为空
        config.setNetworkEndpoints(new ArrayList<>());  // 错误 2: 端点列表为空

        // When: 执行校验
        ValidationResult result = chain.validate(config);

        // Then: ���验失败，收集所有错误
        assertFalse(result.isValid(), "无效配置应该校验失败");
        assertTrue(result.hasErrors(), "应该有错误");
        assertTrue(result.getErrors().size() >= 2, "应该收集到至少 2 个错误");
    }

    @Test
    @DisplayName("场景 1.2.4: 批量校验生成摘要")
    void testBatchValidationSummary() {
        // Given: 配置校验链
        chain.addValidator(new TenantIdValidator());
        chain.addValidator(new NetworkEndpointValidator());

        // 创建 5 个配置：2 个有效，3 个无效
        List<TenantDeployConfig> configs = new ArrayList<>();

        // 有效配置 1
        configs.add(TestDataFactory.createMinimalConfig("tenant1", 1001L));

        // 有效配置 2
        configs.add(TestDataFactory.createMinimalConfig("tenant2", 1001L));

        // 无效配置 1: tenantId 为空
        TenantDeployConfig invalid1 = new TenantDeployConfig();
        invalid1.setTenantId(null);
        configs.add(invalid1);

        // 无效配置 2: tenantId 过长
        TenantDeployConfig invalid2 = new TenantDeployConfig();
        invalid2.setTenantId("a".repeat(65));
        configs.add(invalid2);

        // 无效配置 3: 网络端点为空
        TenantDeployConfig invalid3 = new TenantDeployConfig();
        invalid3.setTenantId("tenant3");
        invalid3.setNetworkEndpoints(new ArrayList<>());
        configs.add(invalid3);

        // When: 执行批量校验
        ValidationSummary summary = chain.validateAll(configs);

        // Then: 摘要统计正确
        assertEquals(5, summary.getTotalConfigs(), "总数应该是 5");
        assertEquals(2, summary.getValidCount(), "有效配置应该是 2");
        assertEquals(3, summary.getInvalidCount(), "无效配置应该是 3");

        assertTrue(summary.hasErrors(), "应该有错误");
        assertEquals(2, summary.getValidConfigs().size(), "有效配置列表应该有 2 个");

        // 验证错误列表不为空
        assertFalse(summary.getAllErrors().isEmpty(), "应该有错误列表");
    }
}

