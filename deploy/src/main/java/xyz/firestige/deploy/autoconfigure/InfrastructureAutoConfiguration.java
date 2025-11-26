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

    @Bean(destroyMethod = "shutdown")  // ✅ 确保 Spring 容器关闭时调用 shutdown()
    @ConditionalOnProperty(prefix = "executor.infrastructure.nacos", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public NacosServiceDiscovery serviceDiscovery(InfrastructureProperties props,
                                                        org.springframework.core.env.Environment env) {
        InfrastructureProperties.NacosProperties nacos = props.getNacos();

        // ✅ 使用 Builder 模式创建实例（清晰易读）
        NacosServiceDiscovery.Builder builder = NacosServiceDiscovery.builder(nacos.getServerAddr())
                .defaultNamespace(nacos.getDefaultNamespace())
                .clientIdleTimeoutMinutes(nacos.getClientIdleTimeoutMinutes())
                .evictionIntervalMinutes(nacos.getEvictionIntervalMinutes());

        // 可选：设置用户名
        if (nacos.getUsername() != null && !nacos.getUsername().isEmpty()) {
            builder.username(nacos.getUsername());
        }

        // 可选：密码从环境变量读取
        String password = env.getProperty("executor.infrastructure.nacos.password");
        if (password != null && !password.isEmpty()) {
            builder.password(password);
        }

        log.info("[Infrastructure] Initializing NacosServiceDiscovery: serverAddr={}, defaultNamespace={}, username={}, " +
                        "idleTimeout={}min, evictionInterval={}min",
                nacos.getServerAddr(), nacos.getDefaultNamespace(),
                nacos.getUsername() != null ? "***" : "null",
                nacos.getClientIdleTimeoutMinutes(), nacos.getEvictionIntervalMinutes());

        return builder.build();
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
