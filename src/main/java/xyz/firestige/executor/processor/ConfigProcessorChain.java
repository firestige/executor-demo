package xyz.firestige.executor.processor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import xyz.firestige.executor.api.dto.ServiceConfig;

/**
 * 配置处理器链
 * 使用责任链模式按顺序处理配置
 */
public class ConfigProcessorChain {
    
    private final List<ConfigProcessor> processors;
    
    public ConfigProcessorChain() {
        this.processors = new ArrayList<>();
    }
    
    public ConfigProcessorChain(List<ConfigProcessor> processors) {
        this.processors = new ArrayList<>(processors);
        // 按优先级排序
        this.processors.sort(Comparator.comparingInt(ConfigProcessor::getOrder));
    }
    
    /**
     * 添加处理器到链中
     */
    public void addProcessor(ConfigProcessor processor) {
        this.processors.add(processor);
        // 重新排序
        this.processors.sort(Comparator.comparingInt(ConfigProcessor::getOrder));
    }
    
    /**
     * 处理配置
     * 按顺序通过所有处理器处理配置
     * 
     * @param serviceConfig 服务配置
     * @param tenantId 租户ID
     * @param rawConfig 原始配置
     * @return 处理后的配置
     */
    public Map<String, Object> process(ServiceConfig serviceConfig, String tenantId, Map<String, Object> rawConfig) {
        Map<String, Object> result = rawConfig;
        
        for (ConfigProcessor processor : processors) {
            result = processor.process(serviceConfig, tenantId, result);
        }
        
        return result;
    }
    
    /**
     * 获取处理器数量
     */
    public int size() {
        return processors.size();
    }
    
    /**
     * 检查是否为空
     */
    public boolean isEmpty() {
        return processors.isEmpty();
    }
}
