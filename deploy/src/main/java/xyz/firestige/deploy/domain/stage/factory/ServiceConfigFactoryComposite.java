package xyz.firestige.deploy.domain.stage.factory;

import org.springframework.stereotype.Component;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.domain.stage.config.ServiceConfig;

import java.util.List;

/**
 * 服务配置工厂组合器（防腐层核心）
 * 
 * 职责：
 * 1. 管理所有服务类型的配置工厂
 * 2. 根据服务类型路由到对应的工厂实现
 * 3. 提供统一的配置创建入口
 * 
 * 设计模式：
 * - 组合模式：统一管理多个工厂实现
 * - 策略模式：运行时选择合适的工厂
 * - 依赖注入：自动发现所有 ServiceConfigFactory 实现
 */
@Component
public class ServiceConfigFactoryComposite {
    
    private final List<ServiceConfigFactory> factories;
    
    /**
     * Spring 自动注入所有 ServiceConfigFactory 实现
     */
    public ServiceConfigFactoryComposite(List<ServiceConfigFactory> factories) {
        if (factories == null || factories.isEmpty()) {
            throw new IllegalStateException("At least one ServiceConfigFactory must be registered");
        }
        this.factories = factories;
    }
    
    /**
     * 创建服务特定的配置对象
     * 
     * @param serviceType 服务类型标识
     * @param tenantConfig 租户配置（应用层 DTO）
     * @return 服务配置（领域层模型）
     * @throws UnsupportedOperationException 如果没有工厂支持该服务类型
     * @throws IllegalArgumentException 如果 tenantConfig 数据不完整或不合法
     */
    public ServiceConfig createConfig(String serviceType, TenantConfig tenantConfig) {
        if (serviceType == null || serviceType.isBlank()) {
            throw new IllegalArgumentException("serviceType cannot be null or blank");
        }
        if (tenantConfig == null) {
            throw new IllegalArgumentException("tenantConfig cannot be null");
        }
        
        // 查找支持该服务类型的工厂
        ServiceConfigFactory factory = factories.stream()
                .filter(f -> f.supports(serviceType))
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException(
                        "No factory found for service type: " + serviceType));
        
        // 委托给具体工厂创建配置
        return factory.create(tenantConfig);
    }
    
    /**
     * 检查是否支持指定的服务类型
     */
    public boolean supports(String serviceType) {
        return factories.stream()
                .anyMatch(f -> f.supports(serviceType));
    }
    
    /**
     * 获取所有支持的服务类型
     */
    public List<String> getSupportedServiceTypes() {
        return List.of("blue-green-gateway", "portal", "asbc-gateway");
    }
}
