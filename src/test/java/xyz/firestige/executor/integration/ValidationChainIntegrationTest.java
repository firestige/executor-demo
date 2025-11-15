package xyz.firestige.executor.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.util.TestDataFactory;
import xyz.firestige.executor.util.TimingExtension;
import xyz.firestige.executor.validation.ValidationChain;
import xyz.firestige.executor.validation.ValidationSummary;
import xyz.firestige.executor.validation.validator.ConflictValidator;
import xyz.firestige.executor.validation.validator.NetworkEndpointValidator;
import xyz.firestige.executor.validation.validator.TenantIdValidator;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ValidationChain 集成测试
 * 测试多个校验器的集成
 */
@Tag("integration")
@Tag("standard")
@ExtendWith(TimingExtension.class)
@DisplayName("ValidationChain 集成测试")
class ValidationChainIntegrationTest {

    private ValidationChain chain;

    @BeforeEach
    void setUp() {
        ConflictValidator.clearAll();

        // 创建完整的校验链
        chain = new ValidationChain(false);
        chain.addValidator(new TenantIdValidator());
        chain.addValidator(new NetworkEndpointValidator());
        chain.addValidator(new ConflictValidator());
    }

    @Test
    @DisplayName("场景: 完整校验链 - 所有配置有效")
    void testFullChainWithValidConfigs() {
        // Given: 有效配置列表
        List<TenantDeployConfig> configs = TestDataFactory.createConfigList(3);

        // When: 执行完整校验
        ValidationSummary summary = chain.validateAll(configs);

        // Then: 全部通过
        assertEquals(3, summary.getTotalConfigs());
        assertEquals(3, summary.getValidCount());
        assertEquals(0, summary.getInvalidCount());
        assertFalse(summary.hasErrors());
    }

    @Test
    @DisplayName("场景: 完整校验链 - 部分配置无效")
    void testFullChainWithMixedConfigs() {
        // Given: 混合配置（有效和无效）
        List<TenantDeployConfig> configs = TestDataFactory.createConfigList(2);

        // 添加一个无效配置（tenantId为null）
        TenantDeployConfig invalid = new TenantDeployConfig();
        invalid.setTenantId(null);
        configs.add(invalid);

        // When: 执行完整校验
        ValidationSummary summary = chain.validateAll(configs);

        // Then: 部分失败
        assertEquals(3, summary.getTotalConfigs());
        assertEquals(2, summary.getValidCount());
        assertEquals(1, summary.getInvalidCount());
        assertTrue(summary.hasErrors());
    }

    @Test
    @DisplayName("场景: 完整校验链 - 检测重复租户")
    void testFullChainDetectsDuplicates() {
        // Given: 包含重复租户的配置
        TenantDeployConfig config1 = TestDataFactory.createMinimalConfig("tenant1", 1001L);
        TenantDeployConfig config2 = TestDataFactory.createMinimalConfig("tenant1", 1001L); // 重复

        // When: 执行完整校验
        ValidationSummary summary = chain.validateAll(List.of(config1, config2));

        // Then: 检测到重复
        assertTrue(summary.hasErrors(), "应该检测到重复租户");
    }
}

