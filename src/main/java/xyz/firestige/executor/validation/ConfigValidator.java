package xyz.firestige.executor.validation;

import xyz.firestige.dto.TenantDeployConfig;

/**
 * 配置校验器接口
 * 用于校验租户部署配置的各个方面
 */
public interface ConfigValidator {

    /**
     * 校验配置
     *
     * @param config 租户部署配置
     * @return 校验结果
     */
    ValidationResult validate(TenantDeployConfig config);

    /**
     * 获取校验器名称
     *
     * @return 校验器名称
     */
    String getValidatorName();

    /**
     * 获取执行顺序
     * 数字越小优先级越高
     *
     * @return 执行顺序
     */
    default int getOrder() {
        return 100;
    }
}

