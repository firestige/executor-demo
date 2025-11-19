package xyz.firestige.deploy.application.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.domain.shared.validation.ValidationError;
import xyz.firestige.deploy.domain.shared.validation.ValidationSummary;
import xyz.firestige.deploy.domain.shared.vo.TenantId;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 业务规则校验器
 *
 * 职责：
 * - 业务规则校验（需要访问数据库/缓存）
 * - 检查租户ID重复
 * - 检查租户是否存在
 * - 检查业务数据合法性
 *
 * 注意：
 * - 由 Application 层使用
 * - 可以访问数据库和缓存
 * - 返回 ValidationSummary 而不是抛出异常
 *
 * @since DDD 重构 Phase 3
 */
@Component
public class BusinessValidator {

    private static final Logger logger = LoggerFactory.getLogger(BusinessValidator.class);

    // TODO: 根据实际需要注入 Repository 或其他服务
    // private final TenantRepository tenantRepository;

    public BusinessValidator() {
        // 构造函数，可以注入依赖
    }

    /**
     * 批量校验租户配置的业务规则
     *
     * @param configs 租户配置列表
     * @return 校验结果
     */
    public ValidationSummary validate(List<TenantConfig> configs) {
        logger.debug("[BusinessValidator] 开始业务规则校验，配置数量: {}", configs.size());

        ValidationSummary summary = new ValidationSummary();
        summary.setTotalConfigs(configs.size());

        // 收集所有错误
        List<ValidationError> allErrors = new ArrayList<>();

        // 1. 检查租户ID重复
        allErrors.addAll(checkDuplicateTenantIds(configs));

        // 2. 检查租户是否存在（需要访问数据库）
        // TODO: 如果需要检查租户是否存在，可以在这里实现
        // allErrors.addAll(checkTenantsExist(configs));

        // 3. 检查每个配置的业务规则
        for (int i = 0; i < configs.size(); i++) {
            TenantConfig config = configs.get(i);
            List<ValidationError> configErrors = validateSingleConfig(config, i);
            allErrors.addAll(configErrors);
        }

        // 构建 ValidationSummary
        // 注意：ValidationSummary 设计为处理 TenantDeployConfig，
        // 我们这里处理 TenantConfig，所以需要特殊处理
        if (!allErrors.isEmpty()) {
            // 有错误：创建一个假的 TenantDeployConfig 来记录错误
            // 这是一个权宜之计，理想情况下应该重构 ValidationSummary
            summary.addInvalidConfig(null, allErrors);
            logger.warn("[BusinessValidator] 业务规则校验失败，发现 {} 个错误", allErrors.size());
            for (ValidationError error : allErrors) {
                logger.warn("[BusinessValidator] 校验错误: {} = {}", error.getField(), error.getMessage());
            }
        } else {
            logger.debug("[BusinessValidator] 业务规则校验通过，所有配置有效");
        }

        return summary;
    }

    /**
     * 检查租户ID重复
     */
    private List<ValidationError> checkDuplicateTenantIds(List<TenantConfig> configs) {
        List<ValidationError> errors = new ArrayList<>();

        // 统计租户ID出现次数
        Map<TenantId, Long> tenantIdCounts = configs.stream()
                .map(TenantConfig::getTenantId)
                .collect(Collectors.groupingBy(id -> id, Collectors.counting()));

        // 找出重复的租户ID
        Set<TenantId> duplicates = tenantIdCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        if (!duplicates.isEmpty()) {
            for (TenantId tenantId : duplicates) {
                errors.add(new ValidationError(
                        "tenantId",
                        tenantId.getValue(),
                        "租户ID重复: " + tenantId
                ));
            }
        }

        return errors;
    }

    /**
     * 校验单个配置的业务规则
     */
    private List<ValidationError> validateSingleConfig(TenantConfig config, int index) {
        List<ValidationError> errors = new ArrayList<>();

        // 示例：检查 planId 是否有效
        if (config.getPlanId() == null) {
            errors.add(new ValidationError(
                    "configs[" + index + "].planId",
                    null,
                    "Plan ID 不能为空"
            ));
        }

        // TODO: 添加更多业务规则校验
        // 例如：
        // - 检查部署单元版本是否存在
        // - 检查网络端点是否可达
        // - 检查配置冲突
        // 等等

        return errors;
    }

    /**
     * 检查租户是否存在（示例）
     *
     * 注意：这需要访问数据库，是典型的业务规则校验
     */
    @SuppressWarnings("unused")
    private List<ValidationError> checkTenantsExist(List<TenantConfig> configs) {
        List<ValidationError> errors = new ArrayList<>();

        // TODO: 实现租户存在性检查
        // for (TenantConfig config : configs) {
        //     if (!tenantRepository.exists(config.getTenantId())) {
        //         errors.add(new ValidationError(
        //                 "tenantId",
        //                 config.getTenantId(),
        //                 "租户不存在: " + config.getTenantId()
        //         ));
        //     }
        // }

        return errors;
    }
}

