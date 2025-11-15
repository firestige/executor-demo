package xyz.firestige.executor.service.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.execution.pipeline.PipelineContext;
import xyz.firestige.executor.service.NotificationResult;
import xyz.firestige.executor.service.strategy.ServiceNotificationStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务通知适配器基类
 * 用于聚合多个操作步骤或多个策略
 */
public abstract class ServiceNotificationAdapter implements ServiceNotificationStrategy {

    private static final Logger logger = LoggerFactory.getLogger(ServiceNotificationAdapter.class);

    /**
     * 子策略列表（用于组合多个策略）
     */
    protected List<ServiceNotificationStrategy> childStrategies;

    protected ServiceNotificationAdapter() {
        this.childStrategies = new ArrayList<>();
    }

    /**
     * 添加子策略
     */
    public void addChildStrategy(ServiceNotificationStrategy strategy) {
        if (strategy != null) {
            this.childStrategies.add(strategy);
        }
    }

    /**
     * 批量添加子策略
     */
    public void addChildStrategies(List<ServiceNotificationStrategy> strategies) {
        if (strategies != null && !strategies.isEmpty()) {
            this.childStrategies.addAll(strategies);
        }
    }

    /**
     * 顺序执行所有子策略
     * 如果任一策略失败，则停止执行并返回失败结果
     */
    protected NotificationResult executeChildStrategies(TenantDeployConfig config, PipelineContext context) {
        logger.info("开始执行子策略，数量: {}", childStrategies.size());

        for (ServiceNotificationStrategy strategy : childStrategies) {
            logger.info("执行子策略: {}", strategy.getServiceName());

            NotificationResult result = strategy.notify(config, context);

            if (!result.isSuccess()) {
                logger.error("子策略执行失败: {}, 错误: {}",
                        strategy.getServiceName(), result.getMessage());
                return result;
            }

            logger.info("子策略执行成功: {}", strategy.getServiceName());
        }

        return NotificationResult.success(getServiceName(), "所有子策略执行成功");
    }

    /**
     * 逆序回滚所有子策略
     */
    protected void rollbackChildStrategies(TenantDeployConfig config, PipelineContext context) {
        logger.info("开始回滚子策略，数量: {}", childStrategies.size());

        // 逆序回滚
        for (int i = childStrategies.size() - 1; i >= 0; i--) {
            ServiceNotificationStrategy strategy = childStrategies.get(i);

            try {
                logger.info("回滚子策略: {}", strategy.getServiceName());
                strategy.rollback(config, context);
                logger.info("子策略回滚成功: {}", strategy.getServiceName());
            } catch (Exception e) {
                logger.error("子策略回滚失败: {}, 错误: {}",
                        strategy.getServiceName(), e.getMessage(), e);
                // 继续回滚其他策略
            }
        }
    }

    /**
     * 获取所有子策略名称
     */
    public List<String> getChildStrategyNames() {
        List<String> names = new ArrayList<>();
        for (ServiceNotificationStrategy strategy : childStrategies) {
            names.add(strategy.getServiceName());
        }
        return names;
    }

    public List<ServiceNotificationStrategy> getChildStrategies() {
        return new ArrayList<>(childStrategies);
    }
}

