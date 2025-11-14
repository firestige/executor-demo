package xyz.firestige.executor.service.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.executor.service.strategy.ServiceNotificationStrategy;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务注册中心
 * 管理所有的服务通知策略
 */
public class ServiceRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ServiceRegistry.class);

    /**
     * 策略存储
     * Key: 服务名称, Value: 策略实例
     */
    private final Map<String, ServiceNotificationStrategy> strategies = new ConcurrentHashMap<>();

    /**
     * 注册策略
     */
    public void registerStrategy(ServiceNotificationStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("策略不能为 null");
        }

        String serviceName = strategy.getServiceName();
        if (serviceName == null || serviceName.trim().isEmpty()) {
            throw new IllegalArgumentException("服务名称不能为空");
        }

        strategies.put(serviceName, strategy);
        logger.info("注册服务通知策略: {}", serviceName);
    }

    /**
     * 批量注册策略
     */
    public void registerStrategies(List<ServiceNotificationStrategy> strategyList) {
        if (strategyList == null || strategyList.isEmpty()) {
            return;
        }

        for (ServiceNotificationStrategy strategy : strategyList) {
            registerStrategy(strategy);
        }
    }

    /**
     * 获取策略
     */
    public ServiceNotificationStrategy getStrategy(String serviceName) {
        ServiceNotificationStrategy strategy = strategies.get(serviceName);
        if (strategy == null) {
            logger.warn("未找到服务通知策略: {}", serviceName);
        }
        return strategy;
    }

    /**
     * 获取所有策略
     */
    public List<ServiceNotificationStrategy> getAllStrategies() {
        return new ArrayList<>(strategies.values());
    }

    /**
     * 获取所有服务名称
     */
    public Set<String> getAllServiceNames() {
        return new HashSet<>(strategies.keySet());
    }

    /**
     * 检查是否包含某个策略
     */
    public boolean hasStrategy(String serviceName) {
        return strategies.containsKey(serviceName);
    }

    /**
     * 移除策略
     */
    public void removeStrategy(String serviceName) {
        ServiceNotificationStrategy removed = strategies.remove(serviceName);
        if (removed != null) {
            logger.info("移除服务通知策略: {}", serviceName);
        }
    }

    /**
     * 清空所有策略
     */
    public void clear() {
        logger.info("清空所有服务通知策略");
        strategies.clear();
    }

    /**
     * 获取策略数量
     */
    public int getStrategyCount() {
        return strategies.size();
    }
}

