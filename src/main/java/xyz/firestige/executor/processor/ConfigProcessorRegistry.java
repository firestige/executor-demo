package xyz.firestige.executor.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 配置处理器注册中心
 * 按服务维度注册和管理处理器链
 */
@Component
public class ConfigProcessorRegistry {
    
    /**
     * 服务ID -> 处理器列表的映射
     */
    private final Map<String, List<ConfigProcessor>> serviceProcessors;
    
    /**
     * 默认处理器列表（当服务没有特定处理器时使用）
     */
    private final List<ConfigProcessor> defaultProcessors;
    
    public ConfigProcessorRegistry() {
        this.serviceProcessors = new ConcurrentHashMap<>();
        this.defaultProcessors = new ArrayList<>();
    }
    
    /**
     * 为指定服务注册处理器
     * 
     * @param serviceId 服务ID
     * @param processor 配置处理器
     */
    public void register(String serviceId, ConfigProcessor processor) {
        serviceProcessors.computeIfAbsent(serviceId, k -> new ArrayList<>()).add(processor);
    }
    
    /**
     * 注册默认处理器（适用于所有服务）
     * 
     * @param processor 配置处理器
     */
    public void registerDefault(ConfigProcessor processor) {
        defaultProcessors.add(processor);
    }
    
    /**
     * 获取指定服务的处理器链
     * 
     * @param serviceId 服务ID
     * @return 处理器链
     */
    public ConfigProcessorChain getProcessorChain(String serviceId) {
        List<ConfigProcessor> processors = serviceProcessors.get(serviceId);
        
        if (processors != null && !processors.isEmpty()) {
            return new ConfigProcessorChain(processors);
        }
        
        // 如果没有特定处理器，使用默认处理器
        if (!defaultProcessors.isEmpty()) {
            return new ConfigProcessorChain(defaultProcessors);
        }
        
        // 返回空链
        return new ConfigProcessorChain();
    }
    
    /**
     * 检查服务是否有注册的处理器
     * 
     * @param serviceId 服务ID
     * @return true 如果有处理器，false 否则
     */
    public boolean hasProcessors(String serviceId) {
        List<ConfigProcessor> processors = serviceProcessors.get(serviceId);
        return (processors != null && !processors.isEmpty()) || !defaultProcessors.isEmpty();
    }
    
    /**
     * 清空指定服务的处理器
     * 
     * @param serviceId 服务ID
     */
    public void clear(String serviceId) {
        serviceProcessors.remove(serviceId);
    }
    
    /**
     * 清空所有处理器
     */
    public void clearAll() {
        serviceProcessors.clear();
        defaultProcessors.clear();
    }
}
