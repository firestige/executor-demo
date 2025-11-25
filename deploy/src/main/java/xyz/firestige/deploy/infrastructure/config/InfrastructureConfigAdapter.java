package xyz.firestige.deploy.infrastructure.config;

import xyz.firestige.deploy.config.properties.InfrastructureProperties;
import xyz.firestige.deploy.infrastructure.config.model.InfrastructureConfig;

/**
 * 将新 InfrastructureProperties 适配为旧 InfrastructureConfig (Phase1 过渡用)
 * 仅映射 ServiceDiscoveryHelper 所需的 nacos / fallbackInstances / auth / healthCheck 字段。
 */
public final class InfrastructureConfigAdapter {

    private InfrastructureConfigAdapter() {}

    public static InfrastructureConfig adapt(InfrastructureProperties props) {
        InfrastructureConfig cfg = new InfrastructureConfig();
        // Redis (minimal)
        InfrastructureConfig.RedisConfig redis = new InfrastructureConfig.RedisConfig();
        redis.setHashKeyPrefix(props.getRedis().getHashKeyPrefix());
        redis.setPubsubTopic(props.getRedis().getPubsubTopic());
        cfg.setRedis(redis);
        // Nacos
        InfrastructureConfig.NacosConfig nacos = new InfrastructureConfig.NacosConfig();
        nacos.setEnabled(props.getNacos().isEnabled());
        nacos.setServerAddr(props.getNacos().getServerAddr());
        nacos.setHealthCheckEnabled(props.getNacos().isHealthCheckEnabled());
        nacos.setServices(props.getNacos().getServices());
        cfg.setNacos(nacos);
        // HealthCheck (rename verify)
        InfrastructureConfig.HealthCheckConfig hc = new InfrastructureConfig.HealthCheckConfig();
        hc.setDefaultPath(props.getVerify().getDefaultPath());
        hc.setIntervalSeconds(props.getVerify().getIntervalSeconds());
        hc.setMaxAttempts(props.getVerify().getMaxAttempts());
        cfg.setHealthCheck(hc);
        // Fallback instances
        cfg.setFallbackInstances(props.getFallbackInstances());
        // Auth
        if (props.getAuth() != null) {
            java.util.Map<String, InfrastructureConfig.AuthConfig> map = new java.util.HashMap<>();
            props.getAuth().forEach((k,v)->{
                InfrastructureConfig.AuthConfig ac = new InfrastructureConfig.AuthConfig();
                ac.setEnabled(v.isEnabled());
                ac.setTokenProvider(v.getTokenProvider());
                map.put(k, ac);
            });
            cfg.setAuth(map);
        }
        return cfg;
    }
}

