package xyz.firestige.executor.processor;

import java.util.Map;

import xyz.firestige.executor.api.dto.ServiceConfig;

/**
 * 配置处理器接口
 * 负责将原始配置处理成可下发的配置
 */
public interface ConfigProcessor {
    
    /**
     * 处理配置
     * 
     * @param serviceConfig 服务配置
     * @param tenantId 租户ID
     * @param rawConfig 原始配置数据
     * @return 处理后的配置数据
     */
    Map<String, Object> process(ServiceConfig serviceConfig, String tenantId, Map<String, Object> rawConfig);
    
    /**
     * 获取处理器的优先级（数字越小优先级越高）
     * 用于在链式处理中确定执行顺序
     * 
     * @return 优先级值
     */
    default int getOrder() {
        return 0;
    }
}
