package xyz.firestige.deploy.infrastructure.config;

import com.alibaba.nacos.api.exception.NacosException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import xyz.firestige.deploy.infrastructure.config.model.InfrastructureConfig;
import xyz.firestige.deploy.infrastructure.discovery.NacosServiceDiscovery;
import xyz.firestige.deploy.infrastructure.discovery.ServiceDiscoveryHelper;

/**
 * 服务发现配置
 *
 * @since T-025
 */
@Configuration
public class ServiceDiscoveryConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ServiceDiscoveryConfiguration.class);

    /**
     * Nacos 服务发现 Bean（仅在启用时创建）
     */
    @Bean
    @ConditionalOnProperty(prefix = "infrastructure.nacos", name = "enabled", havingValue = "true")
    public NacosServiceDiscovery nacosServiceDiscovery(DeploymentConfigLoader configLoader) {
        InfrastructureConfig.NacosConfig nacosConfig = configLoader.getInfrastructure().getNacos();

        if (nacosConfig == null || nacosConfig.getServerAddr() == null) {
            throw new IllegalStateException("Nacos enabled but serverAddr not configured");
        }

        try {
            NacosServiceDiscovery discovery = new NacosServiceDiscovery(nacosConfig.getServerAddr());
            log.info("Nacos 服务发现已启用: serverAddr={}", nacosConfig.getServerAddr());
            return discovery;

        } catch (NacosException e) {
            log.error("Nacos 初始化失败，将使用 fallback 配置", e);
            throw new IllegalStateException("Failed to initialize Nacos", e);
        }
    }

    /**
     * 服务发现辅助类 Bean
     */
    @Bean
    public ServiceDiscoveryHelper serviceDiscoveryHelper(
            DeploymentConfigLoader configLoader,
            RestTemplate restTemplate,
            @Autowired(required = false) NacosServiceDiscovery nacosDiscovery) {

        log.info("创建 ServiceDiscoveryHelper: nacosEnabled={}", nacosDiscovery != null);

        return new ServiceDiscoveryHelper(
            configLoader.getInfrastructure(),
            nacosDiscovery,  // 可能为 null
            restTemplate
        );
    }
}

