package xyz.firestige.deploy.domain.stage.config;

import xyz.firestige.deploy.domain.shared.vo.TenantId;

/**
 * 服务配置标记接口
 * 用于统一不同服务类型的配置表示
 * 
 * 设计目的：
 * 1. 类型安全：通过接口隔离不同服务的配置类型
 * 2. 防腐层：将应用层的 TenantConfig 转换为领域层的配置模型
 * 3. 扩展性：新增服务类型只需实现此接口
 */
public interface ServiceConfig {
    
    /**
     * 获取服务类型
     * @return 服务类型标识
     */
    String getServiceType();
    
    /**
     * 获取租户 ID
     * @return 租户标识
     */
    TenantId getTenantId();
}
