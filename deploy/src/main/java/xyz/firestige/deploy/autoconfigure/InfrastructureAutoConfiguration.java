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
import xyz.firestige.deploy.infrastructure.config.InfrastructureConfigAdapter;
import xyz.firestige.deploy.infrastructure.config.model.InfrastructureConfig;
import xyz.firestige.deploy.infrastructure.discovery.NacosServiceDiscovery;
import xyz.firestige.deploy.infrastructure.discovery.ServiceDiscoveryHelper;

/**
 * 基础设施自动装配（Phase1 新增）
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
            log.error("[Infrastructure] Nacos init failed: {}", e.getMessage());
            // 返回一个不可用的占位实现（available=false）供后续降级
            return new NacosServiceDiscoveryPlaceholder();
        }
    }

    /** 占位 NacosServiceDiscovery，标记不可用 */
    static class NacosServiceDiscoveryPlaceholder extends NacosServiceDiscovery {
        NacosServiceDiscoveryPlaceholder() { superSafe(); }
        private void superSafe() { /* no-op unsafe constructor bypass */ }
        @Override public boolean isAvailable() { return false; }
    }

    @Bean
    @ConditionalOnMissingBean
    public ServiceDiscoveryHelper serviceDiscoveryHelper(InfrastructureProperties props,
                                                         RestTemplate restTemplate,
                                                         @org.springframework.beans.factory.annotation.Autowired(required = false) NacosServiceDiscovery nacosServiceDiscovery) {
        InfrastructureConfig adapted = InfrastructureConfigAdapter.adapt(props);
        log.info("[Infrastructure] Building ServiceDiscoveryHelper (nacosEnabled={})", props.getNacos().isEnabled());
        return new ServiceDiscoveryHelper(adapted, nacosServiceDiscovery, restTemplate);
    }
}
