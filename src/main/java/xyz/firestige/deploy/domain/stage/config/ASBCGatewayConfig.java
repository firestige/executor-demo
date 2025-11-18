package xyz.firestige.deploy.domain.stage.config;

import java.util.List;

/**
 * ASBC 网关配置（领域模型）
 * 
 * ASBC 网关的特殊性：
 * 1. 固定实例列表（不通过 Nacos 服务发现）
 * 2. 自定义数据结构（MediaRoutingConfig）
 * 3. 不支持重试（一次性操作）
 */
public class ASBCGatewayConfig implements ServiceConfig {
    
    private final String tenantId;
    private final Long configVersion;
    private final List<String> fixedInstances;  // 固定的实例 IP 列表
    private final String configEndpoint;        // 配置接口路径，如 /api/v1/config
    private final MediaRouting mediaRouting;    // ASBC 专用的媒体路由配置
    
    public ASBCGatewayConfig(
            String tenantId,
            Long configVersion,
            List<String> fixedInstances,
            String configEndpoint,
            MediaRouting mediaRouting) {
        
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be null or blank");
        }
        if (configVersion == null) {
            throw new IllegalArgumentException("configVersion cannot be null");
        }
        if (fixedInstances == null || fixedInstances.isEmpty()) {
            throw new IllegalArgumentException("fixedInstances cannot be null or empty");
        }
        if (configEndpoint == null || configEndpoint.isBlank()) {
            throw new IllegalArgumentException("configEndpoint cannot be null or blank");
        }
        if (mediaRouting == null) {
            throw new IllegalArgumentException("mediaRouting cannot be null");
        }
        
        this.tenantId = tenantId;
        this.configVersion = configVersion;
        this.fixedInstances = List.copyOf(fixedInstances);
        this.configEndpoint = configEndpoint;
        this.mediaRouting = mediaRouting;
    }
    
    @Override
    public String getServiceType() {
        return "asbc-gateway";
    }
    
    @Override
    public String getTenantId() {
        return tenantId;
    }
    
    public Long getConfigVersion() {
        return configVersion;
    }
    
    public List<String> getFixedInstances() {
        return fixedInstances;
    }
    
    public String getConfigEndpoint() {
        return configEndpoint;
    }
    
    public MediaRouting getMediaRouting() {
        return mediaRouting;
    }
    
    /**
     * ASBC 网关的媒体路由配置（领域值对象）
     */
    public record MediaRouting(String trunkGroup, String calledNumberRules) {
        
        public MediaRouting {
            if (trunkGroup == null || trunkGroup.isBlank()) {
                throw new IllegalArgumentException("trunkGroup cannot be null or blank");
            }
            if (calledNumberRules == null || calledNumberRules.isBlank()) {
                throw new IllegalArgumentException("calledNumberRules cannot be null or blank");
            }
        }
    }
    
    @Override
    public String toString() {
        return "ASBCGatewayConfig{" +
                "tenantId='" + tenantId + '\'' +
                ", configVersion=" + configVersion +
                ", instanceCount=" + fixedInstances.size() +
                ", mediaRouting=" + mediaRouting +
                '}';
    }
}
