package xyz.firestige.executor.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import xyz.firestige.executor.api.dto.DeployStrategy;
import xyz.firestige.executor.api.dto.ServiceConfig;
import xyz.firestige.executor.deployer.ConfigDeployer;

/**
 * 配置下发策略执行器
 * 支持租户维度的并发/顺序下发
 */
@Component
public class DeployStrategyExecutor {
    
    private static final Logger log = LoggerFactory.getLogger(DeployStrategyExecutor.class);
    
    /**
     * 默认线程池（用于并发下发）
     */
    private final ExecutorService defaultExecutor;
    
    public DeployStrategyExecutor() {
        this.defaultExecutor = Executors.newCachedThreadPool();
    }
    
    /**
     * 执行配置下发
     * 
     * @param serviceConfig 服务配置
     * @param tenantIds 租户ID列表
     * @param configMap 租户ID -> 配置数据的映射
     * @param deployer 配置下发器
     * @param strategy 下发策略（可选，如果为null则使用服务配置中的策略）
     * @return 下发结果，租户ID -> 是否成功的映射
     */
    public Map<String, Boolean> execute(ServiceConfig serviceConfig, 
                                       List<String> tenantIds, 
                                       Map<String, Map<String, Object>> configMap,
                                       ConfigDeployer deployer,
                                       DeployStrategy strategy) {
        
        // 确定使用的策略
        DeployStrategy effectiveStrategy = strategy != null ? strategy : 
            (serviceConfig.getDeployStrategy() != null ? serviceConfig.getDeployStrategy() : DeployStrategy.CONCURRENT);
        
        log.info("Executing deploy with strategy: {} for service: {}", 
            effectiveStrategy, serviceConfig.getServiceId());
        
        if (effectiveStrategy == DeployStrategy.CONCURRENT) {
            return deployConcurrently(serviceConfig, tenantIds, configMap, deployer);
        } else {
            return deploySequentially(serviceConfig, tenantIds, configMap, deployer);
        }
    }
    
    /**
     * 并发下发配置
     */
    private Map<String, Boolean> deployConcurrently(ServiceConfig serviceConfig,
                                                    List<String> tenantIds,
                                                    Map<String, Map<String, Object>> configMap,
                                                    ConfigDeployer deployer) {
        
        Map<String, Boolean> results = new ConcurrentHashMap<>();
        Integer maxConcurrency = serviceConfig.getMaxConcurrency();
        
        // 创建执行器
        ExecutorService executor = maxConcurrency != null && maxConcurrency > 0
            ? Executors.newFixedThreadPool(maxConcurrency)
            : defaultExecutor;
        
        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            for (String tenantId : tenantIds) {
                Map<String, Object> config = configMap.get(tenantId);
                if (config == null) {
                    log.warn("No config found for tenant: {}, skipping", tenantId);
                    results.put(tenantId, false);
                    continue;
                }
                
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        boolean success = deployer.deploy(serviceConfig, tenantId, config);
                        results.put(tenantId, success);
                    } catch (Exception e) {
                        log.error("Error deploying config for tenant: {}", tenantId, e);
                        results.put(tenantId, false);
                    }
                }, executor);
                
                futures.add(future);
            }
            
            // 等待所有任务完成
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            
        } finally {
            // 如果是自定义线程池，需要关闭
            if (maxConcurrency != null && maxConcurrency > 0) {
                executor.shutdown();
            }
        }
        
        return results;
    }
    
    /**
     * 顺序下发配置
     */
    private Map<String, Boolean> deploySequentially(ServiceConfig serviceConfig,
                                                    List<String> tenantIds,
                                                    Map<String, Map<String, Object>> configMap,
                                                    ConfigDeployer deployer) {
        
        Map<String, Boolean> results = new ConcurrentHashMap<>();
        
        for (String tenantId : tenantIds) {
            Map<String, Object> config = configMap.get(tenantId);
            if (config == null) {
                log.warn("No config found for tenant: {}, skipping", tenantId);
                results.put(tenantId, false);
                continue;
            }
            
            try {
                boolean success = deployer.deploy(serviceConfig, tenantId, config);
                results.put(tenantId, success);
                
                if (!success) {
                    log.warn("Deploy failed for tenant: {}, continuing to next tenant", tenantId);
                }
            } catch (Exception e) {
                log.error("Error deploying config for tenant: {}", tenantId, e);
                results.put(tenantId, false);
            }
        }
        
        return results;
    }
    
    /**
     * 关闭执行器
     */
    public void shutdown() {
        defaultExecutor.shutdown();
    }
}
