package xyz.firestige.deploy.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.web.client.RestTemplate;
import xyz.firestige.deploy.config.properties.InfrastructureProperties;
import xyz.firestige.deploy.infrastructure.discovery.NacosServiceDiscovery;
import xyz.firestige.deploy.infrastructure.discovery.ServiceDiscoveryHelper;

/**
 * 基础设施自动装配（Phase1 新增）
 * @updated T-027 完全迁移至 InfrastructureProperties
 */
@AutoConfiguration
@EnableConfigurationProperties(InfrastructureProperties.class)
public class InfrastructureAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(InfrastructureAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    @ConditionalOnProperty(prefix = "executor.infrastructure.nacos", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public NacosServiceDiscovery nacosServiceDiscovery(InfrastructureProperties props) {
        String serverAddr = props.getNacos().getServerAddr();
        try {
            log.info("[Infrastructure] Initializing NacosServiceDiscovery serverAddr={}", serverAddr);
            return new NacosServiceDiscovery(serverAddr);
        } catch (com.alibaba.nacos.api.exception.NacosException e) {
            log.error("[Infrastructure] Nacos init failed, will use fallback: {}", e.getMessage());
            throw new IllegalStateException("Failed to initialize Nacos", e);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public ServiceDiscoveryHelper serviceDiscoveryHelper(InfrastructureProperties props,
                                                         RestTemplate restTemplate,
                                                         @org.springframework.beans.factory.annotation.Autowired(required = false) NacosServiceDiscovery nacosServiceDiscovery) {
        log.info("[Infrastructure] Building ServiceDiscoveryHelper (nacosEnabled={})", props.getNacos().isEnabled());
        return new ServiceDiscoveryHelper(props, nacosServiceDiscovery, restTemplate);
    }
}
