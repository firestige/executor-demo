package xyz.firestige.deploy.infrastructure.execution.stage.factory;

import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;

/**
 * Stage 组装器接口
 * 职责：判断是否需要创建 Stage，并构建 Stage 实例
 *
 * @since RF-19-06 策略化重构
 */
public interface StageAssembler {

    /**
     * Stage 逻辑名称（用于日志与配置推断）
     * 例如: "asbc-gateway", "portal", "blue-green-gateway", "ob-service"
     *
     * @return Stage 名称，应与 defaultServiceNames 配置中的名称一致
     */
    String stageName();

    /**
     * 判断是否需要为给定租户配置创建该 Stage
     *
     * @param cfg 租户配置
     * @return true 表示需要创建该 Stage
     */
    boolean supports(TenantConfig cfg);

    /**
     * 构建 Stage 实例
     *
     * @param cfg 租户配置
     * @param resources 共享基础设施依赖
     * @return 构建的 TaskStage 实例
     */
    TaskStage buildStage(TenantConfig cfg, SharedStageResources resources);
}

