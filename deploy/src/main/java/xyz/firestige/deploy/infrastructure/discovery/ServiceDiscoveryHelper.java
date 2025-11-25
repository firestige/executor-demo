package xyz.firestige.deploy.infrastructure.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;
import xyz.firestige.deploy.config.properties.InfrastructureProperties;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 服务发现辅助类
 *
 * <p>职责：
 * <ul>
 *   <li>从 Nacos 获取服务实例（支持动态 namespace）</li>
 *   <li>降级到 fallbackInstances</li>
 *   <li>实例选择策略（ALL/RANDOM/ROUND_ROBIN）</li>
 *   <li>缓存与 Failback 机制</li>
 *   <li>可选健康检查</li>
 * </ul>
 *
 * @since T-025
 * @updated T-027 迁移至 InfrastructureProperties
 */
public class ServiceDiscoveryHelper {

    private static final Logger log = LoggerFactory.getLogger(ServiceDiscoveryHelper.class);

    private final InfrastructureProperties config;
    private final NacosServiceDiscovery nacosDiscovery;  // nullable
    private final RestTemplate restTemplate;
    private final Map<String, AtomicInteger> roundRobinCounters = new ConcurrentHashMap<>();

    // 缓存相关
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final long cacheTtlMillis = 30_000;  // 30秒

    /**
     * 构造函数
     *
     * @param config 基础设施配置
     * @param nacosDiscovery Nacos 服务发现（可选）
     * @param restTemplate HTTP 客户端（用于健康检查）
     */
    public ServiceDiscoveryHelper(InfrastructureProperties config,
                                   NacosServiceDiscovery nacosDiscovery,
                                   RestTemplate restTemplate) {
        this.config = config;
        this.nacosDiscovery = nacosDiscovery;
        this.restTemplate = restTemplate;
    }

    /**
     * 获取服务实例列表（支持缓存）
     *
     * @param serviceKey 服务标识（如 "blueGreenGatewayService"）
     * @param namespace Nacos 命名空间（从 TenantConfig 传入）
     * @return 实例列表（host:port 格式）
     * @throws ServiceDiscoveryException 无法获取实例时抛出
     */
    public List<String> getInstances(String serviceKey, String namespace) {
        String cacheKey = buildCacheKey(serviceKey, namespace);

        // 1. 检查缓存
        CacheEntry entry = cache.get(cacheKey);
        if (entry != null && !entry.isExpired()) {
            List<String> validInstances = entry.getValidInstances();
            if (!validInstances.isEmpty()) {
                log.debug("从缓存获取实例: service={}, count={}", serviceKey, validInstances.size());
                return new ArrayList<>(validInstances);
            }

            // 缓存中所有实例都失败，强制刷新
            log.warn("缓存实例全部失败，强制刷新: service={}", serviceKey);
        }

        // 2. 尝试从 Nacos 获取
        if (isNacosEnabled()) {
            try {
                String nacosServiceName = getNacosServiceName(serviceKey);
                List<String> instances = nacosDiscovery.getHealthyInstances(nacosServiceName, namespace);

                if (instances != null && !instances.isEmpty()) {
                    log.info("从 Nacos 获取实例: service={}, namespace={}, count={}",
                        serviceKey, namespace, instances.size());

                    // 更新缓存
                    cache.put(cacheKey, new CacheEntry(instances, cacheTtlMillis));
                    return instances;
                }

                log.warn("Nacos 返回空实例列表: service={}, namespace={}", serviceKey, namespace);
            } catch (Exception e) {
                log.warn("Nacos 获取实例失败: service={}, error={}", serviceKey, e.getMessage());
            }
        }

        // 3. 降级到 fallback 配置
        List<String> fallbackInstances = getFallbackInstances(serviceKey);
        if (fallbackInstances != null && !fallbackInstances.isEmpty()) {
            log.info("使用 fallback 实例: service={}, count={}", serviceKey, fallbackInstances.size());
            return new ArrayList<>(fallbackInstances);
        }

        // 4. 完全失败
        throw new ServiceDiscoveryException(
            "无法获取服务实例: serviceKey=" + serviceKey +
            ", namespace=" + namespace +
            ", Nacos=" + (isNacosEnabled() ? "失败" : "未启用") +
            ", Fallback=无配置"
        );
    }

    /**
     * 根据策略选择实例（支持健康检查）
     *
     * @param serviceKey 服务标识
     * @param namespace Nacos 命名空间
     * @param strategy 选择策略
     * @param enableHealthCheck 是否启用健康检查
     * @return 选中的实例列表
     */
    public List<String> selectInstances(String serviceKey,
                                        String namespace,
                                        SelectionStrategy strategy,
                                        boolean enableHealthCheck) {
        List<String> allInstances = getInstances(serviceKey, namespace);

        // 健康检查（可选）
        if (enableHealthCheck && isHealthCheckEnabled()) {
            allInstances = filterHealthyInstances(serviceKey, namespace, allInstances);
            if (allInstances.isEmpty()) {
                throw new ServiceDiscoveryException("健康检查后无可用实例: " + serviceKey);
            }
        }

        // 应用选择策略
        switch (strategy) {
            case ALL:
                return allInstances;

            case RANDOM:
                if (allInstances.size() == 1) {
                    return allInstances;
                }
                int randomIndex = new Random().nextInt(allInstances.size());
                return Collections.singletonList(allInstances.get(randomIndex));

            case ROUND_ROBIN:
                String rrKey = buildCacheKey(serviceKey, namespace);
                AtomicInteger counter = roundRobinCounters.computeIfAbsent(
                    rrKey, k -> new AtomicInteger(0)
                );
                int index = counter.getAndIncrement() % allInstances.size();
                return Collections.singletonList(allInstances.get(index));

            default:
                throw new IllegalArgumentException("Unsupported strategy: " + strategy);
        }
    }

    /**
     * 标记实例失败（触发 Failback）
     *
     * @param serviceKey 服务标识
     * @param namespace 命名空间
     * @param failedInstance 失败的实例（host:port）
     */
    public void markInstanceFailed(String serviceKey, String namespace, String failedInstance) {
        String cacheKey = buildCacheKey(serviceKey, namespace);
        CacheEntry entry = cache.get(cacheKey);

        if (entry != null) {
            entry.markFailed(failedInstance);
            log.warn("标记实例失败: service={}, instance={}, 剩余可用={}",
                serviceKey, failedInstance, entry.getValidInstances().size());
        }
    }

    /**
     * 清除缓存
     */
    public void clearCache(String serviceKey, String namespace) {
        String cacheKey = buildCacheKey(serviceKey, namespace);
        cache.remove(cacheKey);
        log.info("清除缓存: service={}, namespace={}", serviceKey, namespace);
    }

    // ---- 私有辅助方法 ----

    private boolean isNacosEnabled() {
        return config.getNacos() != null &&
               config.getNacos().isEnabled() &&
               nacosDiscovery != null;
    }

    private boolean isHealthCheckEnabled() {
        return config.getNacos() != null &&
               config.getNacos().isHealthCheckEnabled();
    }

    private String getNacosServiceName(String serviceKey) {
        if (config.getNacos() == null || config.getNacos().getServices() == null) {
            throw new ServiceDiscoveryException("Nacos services 配置为空");
        }

        String serviceName = config.getNacos().getServices().get(serviceKey);
        if (serviceName == null) {
            throw new ServiceDiscoveryException("未找到 Nacos 服务映射: " + serviceKey);
        }

        return serviceName;
    }

    private List<String> getFallbackInstances(String serviceKey) {
        if (config.getFallbackInstances() == null) {
            return null;
        }

        // 尝试多种 fallbackKey 匹配
        List<String> instances = config.getFallbackInstances().get(serviceKey);
        if (instances != null) {
            return instances;
        }

        // 转换为 kebab-case 匹配
        String kebabKey = toKebabCase(serviceKey);
        return config.getFallbackInstances().get(kebabKey);
    }

    private String toKebabCase(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1-$2")
                        .replaceAll("Service$", "")
                        .toLowerCase();
    }

    private String buildCacheKey(String serviceKey, String namespace) {
        return serviceKey + ":" + (namespace != null ? namespace : "default");
    }

    /**
     * 健康检查：过滤不健康实例
     */
    private List<String> filterHealthyInstances(String serviceKey, String namespace, List<String> instances) {
        List<String> healthyInstances = new ArrayList<>();

        for (String instance : instances) {
            try {
                String url = "http://" + instance + "/actuator/health";
                String response = restTemplate.getForObject(url, String.class);

                if (response != null && response.contains("UP")) {
                    healthyInstances.add(instance);
                } else {
                    log.debug("实例不健康: {}", instance);
                    markInstanceFailed(serviceKey, namespace, instance);
                }
            } catch (Exception e) {
                log.debug("健康检查失败: instance={}, error={}", instance, e.getMessage());
                markInstanceFailed(serviceKey, namespace, instance);
            }
        }

        return healthyInstances;
    }

    /**
     * 缓存条目
     */
    static class CacheEntry {
        private final List<String> instances;
        private final long timestamp;
        private final long ttl;
        private final Set<String> failedInstances = ConcurrentHashMap.newKeySet();

        CacheEntry(List<String> instances, long ttl) {
            this.instances = new ArrayList<>(instances);
            this.timestamp = System.currentTimeMillis();
            this.ttl = ttl;
        }

        boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > ttl;
        }

        void markFailed(String instance) {
            failedInstances.add(instance);
        }

        List<String> getValidInstances() {
            return instances.stream()
                .filter(inst -> !failedInstances.contains(inst))
                .collect(Collectors.toList());
        }
    }

    /**
     * 服务发现异常
     */
    public static class ServiceDiscoveryException extends RuntimeException {
        public ServiceDiscoveryException(String message) {
            super(message);
        }

        public ServiceDiscoveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

