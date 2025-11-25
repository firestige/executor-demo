package xyz.firestige.deploy.infrastructure.config.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * 基础设施配置
 * 包含 Redis、Nacos、ASBC、健康检查等固定配置
 */
public class InfrastructureConfig {
    
    private RedisConfig redis;
    private NacosConfig nacos;
    private Map<String, List<String>> fallbackInstances;  // 服务发现降级配置
    private ASBCConfig asbc;
    private HealthCheckConfig healthCheck;
    private Map<String, AuthConfig> auth;  // 服务认证配置

    public RedisConfig getRedis() {
        return redis;
    }
    
    public void setRedis(RedisConfig redis) {
        this.redis = redis;
    }
    
    public NacosConfig getNacos() {
        return nacos;
    }
    
    public void setNacos(NacosConfig nacos) {
        this.nacos = nacos;
    }
    
    public Map<String, List<String>> getFallbackInstances() {
        return fallbackInstances;
    }
    
    public void setFallbackInstances(Map<String, List<String>> fallbackInstances) {
        this.fallbackInstances = fallbackInstances;
    }
    
    public ASBCConfig getAsbc() {
        return asbc;
    }
    
    public void setAsbc(ASBCConfig asbc) {
        this.asbc = asbc;
    }
    
    public HealthCheckConfig getHealthCheck() {
        return healthCheck;
    }
    
    public void setHealthCheck(HealthCheckConfig healthCheck) {
        this.healthCheck = healthCheck;
    }
    
    public Map<String, AuthConfig> getAuth() {
        return auth;
    }

    public void setAuth(Map<String, AuthConfig> auth) {
        this.auth = auth;
    }

    /**
     * 获取指定服务的认证配置
     */
    public AuthConfig getAuthConfig(String serviceName) {
        return auth != null ? auth.get(serviceName) : null;
    }

    /**
     * Redis 配置
     */
    public static class RedisConfig {
        private String hashKeyPrefix;
        private String pubsubTopic;
        
        public String getHashKeyPrefix() {
            return hashKeyPrefix;
        }
        
        public void setHashKeyPrefix(String hashKeyPrefix) {
            this.hashKeyPrefix = hashKeyPrefix;
        }
        
        public String getPubsubTopic() {
            return pubsubTopic;
        }
        
        public void setPubsubTopic(String pubsubTopic) {
            this.pubsubTopic = pubsubTopic;
        }
    }
    
    /**
     * Nacos 配置
     */
    public static class NacosConfig {
        private Map<String, String> services;  // key: 服务标识, value: Nacos 服务名
        
        public Map<String, String> getServices() {
            return services;
        }
        
        public void setServices(Map<String, String> services) {
            this.services = services;
        }
        
        public String getServiceName(String serviceKey) {
            return services != null ? services.get(serviceKey) : null;
        }
    }
    
    /**
     * ASBC 配置
     */
    public static class ASBCConfig {
        private List<String> fixedInstances;
        private String configEndpoint;
        
        public List<String> getFixedInstances() {
            return fixedInstances;
        }
        
        public void setFixedInstances(List<String> fixedInstances) {
            this.fixedInstances = fixedInstances;
        }
        
        public String getConfigEndpoint() {
            return configEndpoint;
        }
        
        public void setConfigEndpoint(String configEndpoint) {
            this.configEndpoint = configEndpoint;
        }
    }
    
    /**
     * 健康检查配置
     */
    public static class HealthCheckConfig {
        private String defaultPath;
        private int intervalSeconds;
        private int maxAttempts;
        
        public String getDefaultPath() {
            return defaultPath;
        }
        
        public void setDefaultPath(String defaultPath) {
            this.defaultPath = defaultPath;
        }
        
        public int getIntervalSeconds() {
            return intervalSeconds;
        }
        
        public void setIntervalSeconds(int intervalSeconds) {
            this.intervalSeconds = intervalSeconds;
        }
        
        public int getMaxAttempts() {
            return maxAttempts;
        }
        
        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }
    }

    /**
     * 认证配置
     *
     * @since RF-19
     */
    public static class AuthConfig {
        private boolean enabled;
        private String tokenProvider;  // random, oauth2, custom

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTokenProvider() {
            return tokenProvider;
        }

        public void setTokenProvider(String tokenProvider) {
            this.tokenProvider = tokenProvider;
        }
    }
}
