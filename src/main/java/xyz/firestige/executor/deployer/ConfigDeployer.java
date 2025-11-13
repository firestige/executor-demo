package xyz.firestige.executor.deployer;

import java.util.Map;

import xyz.firestige.executor.api.dto.ServiceConfig;

/**
 * 配置下发器接口
 * 负责将配置下发到目标服务
 */
public interface ConfigDeployer {
    
    /**
     * 下发配置
     * 
     * @param serviceConfig 服务配置
     * @param tenantId 租户ID
     * @param config 要下发的配置数据
     * @return 是否下发成功
     */
    boolean deploy(ServiceConfig serviceConfig, String tenantId, Map<String, Object> config);
    
    /**
     * 获取下发器类型名称
     * 
     * @return 下发器类型
     */
    String getType();
}
