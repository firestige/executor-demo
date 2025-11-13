package xyz.firestige.executor.example;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import xyz.firestige.executor.api.dto.ServiceConfig;
import xyz.firestige.executor.processor.ConfigProcessor;

/**
 * 自定义配置处理器示例
 * 展示如何实现自定义的配置处理器
 */
@Component
public class CustomConfigProcessor implements ConfigProcessor {
    
    @Override
    public Map<String, Object> process(ServiceConfig serviceConfig, String tenantId, Map<String, Object> config) {
        // 创建新的配置映射
        Map<String, Object> processedConfig = new HashMap<>(config);
        
        // 添加租户特定的配置
        processedConfig.put("tenantId", tenantId);
        processedConfig.put("tenantPrefix", "tenant_" + tenantId);
        
        // 添加服务特定的配置
        processedConfig.put("serviceId", serviceConfig.getServiceId());
        processedConfig.put("serviceName", serviceConfig.getServiceName());
        
        // 添加时间戳
        processedConfig.put("processedAt", System.currentTimeMillis());
        
        // 可以根据租户ID或服务ID做特殊处理
        if ("tenant-001".equals(tenantId)) {
            processedConfig.put("priority", "high");
        }
        
        if ("user-service".equals(serviceConfig.getServiceId())) {
            processedConfig.put("cacheEnabled", true);
            processedConfig.put("cacheTimeout", 3600);
        }
        
        return processedConfig;
    }
    
    @Override
    public int getOrder() {
        // 优先级，数字越小越先执行
        return 100;
    }
}
