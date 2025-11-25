package xyz.firestige.deploy.domain.stage.factory;

import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.domain.stage.config.ServiceConfig;

/**
 * 服务配置工厂接口（防腐层）
 * 
 * 职责：
 * 1. 将应用层的 TenantConfig 转换为领域层的 ServiceConfig
 * 2. 隔离外部数据模型与内部领域模型
 * 3. 封装不同服务类型的配置转换逻辑
 * 
 * 设计模式：
 * - 工厂模式：封装对象创建逻辑
 * - 防腐层（Anti-Corruption Layer）：隔离外部模型污染领域模型
 * - 策略模式：不同服务类型使用不同的转换策略
 */
public interface ServiceConfigFactory {
    
    /**
     * 判断工厂是否支持指定的服务类型
     * 
     * @param serviceType 服务类型标识
     * @return true 如果支持，false 否则
     */
    boolean supports(String serviceType);
    
    /**
     * 从 TenantConfig 创建服务特定的配置对象
     * 
     * @param tenantConfig 租户配置（应用层 DTO）
     * @return 服务配置（领域层模型）
     * @throws IllegalArgumentException 如果 tenantConfig 数据不完整或不合法
     * @throws UnsupportedOperationException 如果不支持该服务类型
     */
    ServiceConfig create(TenantConfig tenantConfig);
}
