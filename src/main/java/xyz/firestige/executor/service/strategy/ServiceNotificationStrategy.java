package xyz.firestige.executor.service.strategy;

import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.execution.pipeline.PipelineContext;
import xyz.firestige.executor.service.NotificationResult;

/**
 * 服务通知策略接口
 * 定义服务通知的标准行为
 */
public interface ServiceNotificationStrategy {

    /**
     * 通知服务
     *
     * @param config 租户部署配置
     * @param context Pipeline 上下文
     * @return 通知结果
     */
    NotificationResult notify(TenantDeployConfig config, PipelineContext context);

    /**
     * 回滚通知
     * 当任务需要回滚时，撤销之前的通知操作
     *
     * @param config 租户部署配置
     * @param context Pipeline 上下文
     */
    void rollback(TenantDeployConfig config, PipelineContext context);

    /**
     * 获取服务名称
     *
     * @return 服务名称
     */
    String getServiceName();

    /**
     * 是否支持回滚
     *
     * @return true 表示支持回滚
     */
    default boolean supportsRollback() {
        return true;
    }

    /**
     * 验证配置
     * 在执行通知前验证配置是否满足要求
     *
     * @param config 租户部署配置
     * @return true 表示配置有效
     */
    default boolean validateConfig(TenantDeployConfig config) {
        return config != null && config.getTenantId() != null;
    }
}

