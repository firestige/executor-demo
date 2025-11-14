package xyz.firestige.executor.service.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.dto.TenantDeployConfig;
import xyz.firestige.executor.exception.ErrorType;
import xyz.firestige.executor.exception.FailureInfo;
import xyz.firestige.executor.execution.pipeline.PipelineContext;
import xyz.firestige.executor.service.NotificationResult;

/**
 * 直接 RPC 调用策略（Mock 实现）
 * 模拟通过 RPC 直接通知服务更新配置
 */
public class DirectRpcNotificationStrategy implements ServiceNotificationStrategy {

    private static final Logger logger = LoggerFactory.getLogger(DirectRpcNotificationStrategy.class);

    private final String serviceName;

    public DirectRpcNotificationStrategy(String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    public NotificationResult notify(TenantDeployConfig config, PipelineContext context) {
        logger.info("开始 RPC 通知服务: {}, tenantId: {}", serviceName, config.getTenantId());

        try {
            // Mock: 模拟配置验证
            if (!validateConfig(config)) {
                FailureInfo failureInfo = FailureInfo.of(
                        ErrorType.VALIDATION_ERROR,
                        "配置验证失败: " + serviceName
                );
                return NotificationResult.failure(serviceName, failureInfo);
            }

            // Mock: 模拟 RPC 调用
            logger.info("调用 RPC 接口: service={}, method=updateConfig, tenantId={}",
                    serviceName, config.getTenantId());

            // 模拟网络延迟
            Thread.sleep(100);

            // Mock: 模拟成功响应
            String message = String.format("服务 %s 配置更新成功，租户: %s", serviceName, config.getTenantId());
            logger.info(message);

            // 将通知信息保存到上下文，供后续 Stage 使用
            context.putData(serviceName + "_notified", true);
            context.putData(serviceName + "_config", config);

            return NotificationResult.success(serviceName, message, "RPC_RESPONSE_OK");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("RPC 通知被中断: {}", serviceName, e);

            FailureInfo failureInfo = FailureInfo.of(
                    ErrorType.SYSTEM_ERROR,
                    "RPC 通知被中断: " + e.getMessage()
            );
            return NotificationResult.failure(serviceName, failureInfo);

        } catch (Exception e) {
            logger.error("RPC 通知失败: {}", serviceName, e);

            FailureInfo failureInfo = FailureInfo.fromException(e, ErrorType.NETWORK_ERROR, serviceName);
            return NotificationResult.failure(serviceName, failureInfo);
        }
    }

    @Override
    public void rollback(TenantDeployConfig config, PipelineContext context) {
        logger.info("开始回滚 RPC 通知: {}, tenantId: {}", serviceName, config.getTenantId());

        try {
            // Mock: 模拟回滚操作
            Boolean notified = context.getData(serviceName + "_notified", Boolean.class);
            if (notified != null && notified) {
                logger.info("调用 RPC 接口回滚: service={}, method=rollbackConfig, tenantId={}",
                        serviceName, config.getTenantId());

                // 模拟网络延迟
                Thread.sleep(50);

                logger.info("服务 {} 配置回滚成功，租户: {}", serviceName, config.getTenantId());

                // 清除上下文中的通知标记
                context.putData(serviceName + "_notified", false);
            } else {
                logger.info("服务 {} 未通知成功，无需回滚", serviceName);
            }

        } catch (Exception e) {
            logger.error("RPC 回滚失败: {}", serviceName, e);
            // 回滚失败不抛出异常，记录日志即可
        }
    }

    @Override
    public String getServiceName() {
        return serviceName;
    }

    @Override
    public boolean supportsRollback() {
        return true;
    }

    @Override
    public boolean validateConfig(TenantDeployConfig config) {
        // Mock: 简单验证
        return config != null &&
               config.getTenantId() != null &&
               !config.getTenantId().trim().isEmpty();
    }
}

