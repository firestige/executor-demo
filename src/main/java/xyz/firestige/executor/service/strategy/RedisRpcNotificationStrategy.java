package xyz.firestige.executor.service.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.dto.NetworkEndpoint;
import xyz.firestige.dto.TenantDeployConfig;
import xyz.firestige.executor.exception.ErrorType;
import xyz.firestige.executor.exception.FailureInfo;
import xyz.firestige.executor.execution.pipeline.PipelineContext;
import xyz.firestige.executor.service.NotificationResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis + RPC 组合策略（Mock 实现）
 * 先将配置写入 Redis，然后通知服务从 Redis 读取配置
 */
public class RedisRpcNotificationStrategy implements ServiceNotificationStrategy {

    private static final Logger logger = LoggerFactory.getLogger(RedisRpcNotificationStrategy.class);

    private final String serviceName;
    private final String redisKeyPrefix;

    public RedisRpcNotificationStrategy(String serviceName) {
        this.serviceName = serviceName;
        this.redisKeyPrefix = "bluegreen:config:" + serviceName + ":";
    }

    public RedisRpcNotificationStrategy(String serviceName, String redisKeyPrefix) {
        this.serviceName = serviceName;
        this.redisKeyPrefix = redisKeyPrefix;
    }

    @Override
    public NotificationResult notify(TenantDeployConfig config, PipelineContext context) {
        logger.info("开始 Redis+RPC 通知服务: {}, tenantId: {}", serviceName, config.getTenantId());

        try {
            // Step 1: 验证配置
            if (!validateConfig(config)) {
                FailureInfo failureInfo = FailureInfo.of(
                        ErrorType.VALIDATION_ERROR,
                        "配置验证失败: " + serviceName
                );
                return NotificationResult.failure(serviceName, failureInfo);
            }

            // Step 2: 将配置写入 Redis
            String redisKey = redisKeyPrefix + config.getTenantId();
            logger.info("写入 Redis: key={}", redisKey);

            // Mock: 模拟序列化配置数据
            Map<String, Object> configData = serializeConfig(config);

            // Mock: 模拟 Redis 写入操作
            Thread.sleep(50);
            logger.info("Redis 写入成功: key={}, fields={}", redisKey, configData.size());

            // 保存 Redis key 到上下文，用于回滚
            context.putData(serviceName + "_redis_key", redisKey);
            context.putData(serviceName + "_redis_written", true);

            // Step 3: 调用 RPC 通知服务重新加载配置
            logger.info("调用 RPC 通知服务重新加载: service={}, method=reloadConfigFromRedis, tenantId={}",
                    serviceName, config.getTenantId());

            // Mock: 模拟 RPC 调用
            Thread.sleep(100);

            logger.info("RPC 通知成功: service={}, tenantId={}", serviceName, config.getTenantId());

            // 保存通知状态到上下文
            context.putData(serviceName + "_notified", true);

            String message = String.format("服务 %s 通过 Redis+RPC 方式配置更新成功，租户: %s",
                    serviceName, config.getTenantId());

            return NotificationResult.success(serviceName, message, redisKey);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("通知被中断: {}", serviceName, e);

            FailureInfo failureInfo = FailureInfo.of(
                    ErrorType.SYSTEM_ERROR,
                    "通知被中断: " + e.getMessage()
            );
            return NotificationResult.failure(serviceName, failureInfo);

        } catch (Exception e) {
            logger.error("Redis+RPC 通知失败: {}", serviceName, e);

            FailureInfo failureInfo = FailureInfo.fromException(e, ErrorType.NETWORK_ERROR, serviceName);
            return NotificationResult.failure(serviceName, failureInfo);
        }
    }

    @Override
    public void rollback(TenantDeployConfig config, PipelineContext context) {
        logger.info("开始回滚 Redis+RPC 通知: {}, tenantId: {}", serviceName, config.getTenantId());

        try {
            // Step 1: 检查是否写入了 Redis
            Boolean redisWritten = context.getData(serviceName + "_redis_written", Boolean.class);
            String redisKey = context.getData(serviceName + "_redis_key", String.class);

            if (redisWritten != null && redisWritten && redisKey != null) {
                // Mock: 删除 Redis 中的配置
                logger.info("删除 Redis 配置: key={}", redisKey);
                Thread.sleep(30);
                logger.info("Redis 配置删除成功: key={}", redisKey);

                context.putData(serviceName + "_redis_written", false);
            }

            // Step 2: 检查是否通知了服务
            Boolean notified = context.getData(serviceName + "_notified", Boolean.class);
            if (notified != null && notified) {
                // Mock: 调用 RPC 通知服务回滚
                logger.info("调用 RPC 通知服务回滚配置: service={}, method=rollbackConfig, tenantId={}",
                        serviceName, config.getTenantId());

                Thread.sleep(50);
                logger.info("服务 {} 配置回滚成功，租户: {}", serviceName, config.getTenantId());

                context.putData(serviceName + "_notified", false);
            } else {
                logger.info("服务 {} 未通知成功，无需回滚", serviceName);
            }

        } catch (Exception e) {
            logger.error("Redis+RPC 回滚失败: {}", serviceName, e);
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
        // 验证配置的完整性
        if (config == null || config.getTenantId() == null || config.getTenantId().trim().isEmpty()) {
            return false;
        }

        // 验证网络端点
        List<NetworkEndpoint> endpoints = config.getNetworkEndpoints();
        if (endpoints == null || endpoints.isEmpty()) {
            logger.warn("配置缺少网络端点: tenantId={}", config.getTenantId());
            return false;
        }

        return true;
    }

    /**
     * Mock: 序列化配置为 Map（模拟转换为 Redis Hash）
     */
    private Map<String, Object> serializeConfig(TenantDeployConfig config) {
        Map<String, Object> data = new HashMap<>();
        data.put("tenantId", config.getTenantId());
        data.put("deployUnitId", config.getDeployUnitId());
        data.put("deployUnitName", config.getDeployUnitName());
        data.put("nacosNameSpace", config.getNacosNameSpace());
        data.put("endpointCount", config.getNetworkEndpoints() != null ? config.getNetworkEndpoints().size() : 0);
        return data;
    }
}

