package xyz.firestige.deploy.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基础设施配置属性 (Phase1 新增)
 * prefix: executor.infrastructure
 * 包含 redis / nacos / verify / fallbackInstances / auth
 * 仅用于配置绑定与防腐层访问，不直接暴露给业务代码（通过 SharedStageResources 封装）
 */
@ConfigurationProperties(prefix = "executor.infrastructure")
@Validated
public class InfrastructureProperties {

    @NotNull
    private RedisProperties redis = new RedisProperties();
    @NotNull
    private NacosProperties nacos = new NacosProperties();
    @NotNull
    private VerifyProperties verify = new VerifyProperties();

    /** Nacos 不可用时的降级实例列表 serviceKey -> [host:port] */
    private Map<String, List<String>> fallbackInstances = new HashMap<>();
    /** 认证配置 serviceKey -> auth config */
    private Map<String, AuthProperties> auth = new HashMap<>();

    // ========== Redis ==========
    public static class RedisProperties {
        /** Redis Hash Key 前缀 */
        @NotBlank
        private String hashKeyPrefix = "icc_ai_ops_srv:tenant_config:";
        /** Redis Pub/Sub Topic */
        @NotBlank
        private String pubsubTopic = "icc_ai_ops_srv:tenant_config:topic";
        public String getHashKeyPrefix() { return hashKeyPrefix; }
        public void setHashKeyPrefix(String hashKeyPrefix) { this.hashKeyPrefix = hashKeyPrefix; }
        public String getPubsubTopic() { return pubsubTopic; }
        public void setPubsubTopic(String pubsubTopic) { this.pubsubTopic = pubsubTopic; }
    }

    // ========== Nacos ==========
    public static class NacosProperties {
        /** 是否启用 Nacos 服务发现 */
        private boolean enabled = false;
        /** Nacos 服务器地址 host:port */
        @NotBlank
        private String serverAddr = "127.0.0.1:8848";
        /** 默认命名空间（用于 null 参数时的回退） */
        private String defaultNamespace = "public";
        /** Nacos 用户名（可选，用于鉴权） */
        private String username;
        /** 客户端空闲超时时间（分钟），超过此时间未使用则驱逐，默认 5 分钟 */
        private long clientIdleTimeoutMinutes = 5;
        /** 驱逐检查间隔（分钟），默认 1 分钟 */
        private long evictionIntervalMinutes = 1;
        /** 是否启用 Nacos 实例健康检查 */
        private boolean healthCheckEnabled = false;
        /** 服务标识到 Nacos 服务名映射 */
        private Map<String, String> services = new HashMap<>() {{
            put("blueGreenGatewayService", "blue-green-gateway-service");
            put("portalService", "portal-service");
            put("asbcService", "asbc-gateway-service");
            put("obService", "ob-service");
        }};
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getServerAddr() { return serverAddr; }
        public void setServerAddr(String serverAddr) { this.serverAddr = serverAddr; }
        public String getDefaultNamespace() { return defaultNamespace; }
        public void setDefaultNamespace(String defaultNamespace) { this.defaultNamespace = defaultNamespace; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public long getClientIdleTimeoutMinutes() { return clientIdleTimeoutMinutes; }
        public void setClientIdleTimeoutMinutes(long clientIdleTimeoutMinutes) {
            this.clientIdleTimeoutMinutes = clientIdleTimeoutMinutes;
        }
        public long getEvictionIntervalMinutes() { return evictionIntervalMinutes; }
        public void setEvictionIntervalMinutes(long evictionIntervalMinutes) {
            this.evictionIntervalMinutes = evictionIntervalMinutes;
        }
        public boolean isHealthCheckEnabled() { return healthCheckEnabled; }
        public void setHealthCheckEnabled(boolean healthCheckEnabled) { this.healthCheckEnabled = healthCheckEnabled; }
        public Map<String, String> getServices() { return services; }
        public void setServices(Map<String, String> services) { this.services = services; }
    }

    // ========== Verify (原 healthCheck 语义修正) ==========
    public static class VerifyProperties {
        /** Verify 端点路径模板 {tenantId} 占位符 */
        @NotBlank
        private String defaultPath = "/actuator/bg-sdk/{tenantId}";
        /** 重试间隔 (秒) */
        @Min(1)
        private int intervalSeconds = 3;
        /** 最大重试次数 */
        @Min(1)
        private int maxAttempts = 10;
        public String getDefaultPath() { return defaultPath; }
        public void setDefaultPath(String defaultPath) { this.defaultPath = defaultPath; }
        public int getIntervalSeconds() { return intervalSeconds; }
        public void setIntervalSeconds(int intervalSeconds) { this.intervalSeconds = intervalSeconds; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    }

    // ========== Auth ==========
    public static class AuthProperties {
        private boolean enabled = false;
        private String tokenProvider = "random";
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getTokenProvider() { return tokenProvider; }
        public void setTokenProvider(String tokenProvider) { this.tokenProvider = tokenProvider; }
    }

    public RedisProperties getRedis() { return redis; }
    public void setRedis(RedisProperties redis) { this.redis = redis; }
    public NacosProperties getNacos() { return nacos; }
    public void setNacos(NacosProperties nacos) { this.nacos = nacos; }
    public VerifyProperties getVerify() { return verify; }
    public void setVerify(VerifyProperties verify) { this.verify = verify; }
    public Map<String, List<String>> getFallbackInstances() { return fallbackInstances; }
    public void setFallbackInstances(Map<String, List<String>> fallbackInstances) { this.fallbackInstances = fallbackInstances; }
    public Map<String, AuthProperties> getAuth() { return auth; }
    public void setAuth(Map<String, AuthProperties> auth) { this.auth = auth; }
}
