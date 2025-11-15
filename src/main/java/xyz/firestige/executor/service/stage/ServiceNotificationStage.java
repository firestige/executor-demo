package xyz.firestige.executor.service.stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.exception.ErrorType;
import xyz.firestige.executor.exception.FailureInfo;
import xyz.firestige.executor.execution.StageResult;
import xyz.firestige.executor.execution.StageStatus;
import xyz.firestige.executor.execution.pipeline.PipelineContext;
import xyz.firestige.executor.execution.pipeline.PipelineStage;
import xyz.firestige.executor.service.NotificationResult;
import xyz.firestige.executor.service.strategy.ServiceNotificationStrategy;

import java.time.LocalDateTime;

/**
 * 服务通知 Stage
 * 将 ServiceNotificationStrategy 适配为 PipelineStage
 */
@Deprecated
public class ServiceNotificationStage implements PipelineStage {

    private static final Logger logger = LoggerFactory.getLogger(ServiceNotificationStage.class);

    /**
     * Stage 名称（通常与服务名称相同）
     */
    private String stageName;

    /**
     * 服务通知策略
     */
    private ServiceNotificationStrategy strategy;

    /**
     * 执行顺序
     */
    private int order;

    public ServiceNotificationStage(String stageName, ServiceNotificationStrategy strategy, int order) {
        this.stageName = stageName;
        this.strategy = strategy;
        this.order = order;
    }

    public ServiceNotificationStage(ServiceNotificationStrategy strategy, int order) {
        this.stageName = strategy.getServiceName();
        this.strategy = strategy;
        this.order = order;
    }

    @Override
    public String getName() {
        return stageName;
    }

    @Override
    public StageResult execute(PipelineContext context) {
        logger.info("开始执行服务通知 Stage: {}", stageName);

        StageResult result = new StageResult();
        result.setStageName(stageName);
        result.setStartTime(LocalDateTime.now());

        try {
            TenantDeployConfig config = context.getTenantDeployConfig();

            // 验证配置
            if (!strategy.validateConfig(config)) {
                logger.error("配置验证失败: stage={}, tenantId={}", stageName, config.getTenantId());

                FailureInfo failureInfo = FailureInfo.of(
                        ErrorType.VALIDATION_ERROR,
                        "服务通知配置验证失败: " + stageName
                );

                result.setStatus(StageStatus.FAILED);
                result.setSuccess(false);
                result.setFailureInfo(failureInfo);
                result.setEndTime(LocalDateTime.now());
                result.calculateDuration();

                return result;
            }

            // 调用策略执行通知
            NotificationResult notificationResult = strategy.notify(config, context);

            if (notificationResult.isSuccess()) {
                logger.info("服务通知成功: stage={}, tenantId={}", stageName, config.getTenantId());

                result.setStatus(StageStatus.COMPLETED);
                result.setSuccess(true);

                // 将通知结果保存到 Stage 输出
                result.addOutput("notificationResult", notificationResult);
                result.addOutput("serviceName", strategy.getServiceName());
                result.addOutput("responseData", notificationResult.getResponseData());

            } else {
                logger.error("服务通知失败: stage={}, tenantId={}, 错误: {}",
                        stageName, config.getTenantId(), notificationResult.getMessage());

                result.setStatus(StageStatus.FAILED);
                result.setSuccess(false);
                result.setFailureInfo(notificationResult.getFailureInfo());
            }

        } catch (Exception e) {
            logger.error("服务通知 Stage 执行异常: {}", stageName, e);

            result.setStatus(StageStatus.FAILED);
            result.setSuccess(false);

            FailureInfo failureInfo = FailureInfo.fromException(e, ErrorType.SYSTEM_ERROR, stageName);
            result.setFailureInfo(failureInfo);
        }

        result.setEndTime(LocalDateTime.now());
        result.calculateDuration();

        return result;
    }

    @Override
    public void rollback(PipelineContext context) {
        logger.info("开始回滚服务通知 Stage: {}", stageName);

        try {
            TenantDeployConfig config = context.getTenantDeployConfig();
            strategy.rollback(config, context);
            logger.info("服务通知 Stage 回滚成功: {}", stageName);

        } catch (Exception e) {
            logger.error("服务通知 Stage 回滚失败: {}", stageName, e);
            // 回滚失败不抛出异常
        }
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public boolean supportsRollback() {
        return strategy.supportsRollback();
    }

    @Override
    public boolean canSkip(PipelineContext context) {
        // 可以根据上下文决定是否跳过某些服务通知
        // 例如：如果某个服务已经被标记为禁用，则跳过
        String skipKey = stageName + "_skip";
        Boolean skip = context.getData(skipKey, Boolean.class);
        return skip != null && skip;
    }

    // Getters and Setters

    public String getStageName() {
        return stageName;
    }

    public void setStageName(String stageName) {
        this.stageName = stageName;
    }

    public ServiceNotificationStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(ServiceNotificationStrategy strategy) {
        this.strategy = strategy;
    }

    public void setOrder(int order) {
        this.order = order;
    }
}
