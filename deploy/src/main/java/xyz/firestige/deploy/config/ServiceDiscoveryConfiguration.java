package xyz.firestige.deploy.config;

import com.alibaba.nacos.api.exception.NacosException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import xyz.firestige.deploy.config.properties.InfrastructureProperties;
import xyz.firestige.deploy.infrastructure.discovery.NacosServiceDiscovery;
import xyz.firestige.deploy.infrastructure.discovery.ServiceDiscoveryHelper;

/**
 * 服务发现配置
 *
 * @since T-025
 * @updated T-027 迁移至 InfrastructureProperties
 */
@Configuration
public class ServiceDiscoveryConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ServiceDiscoveryConfiguration.class);

    /**
     * Nacos 服务发现 Bean（仅在启用时创建）
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "executor.infrastructure.nacos", name = "enabled", havingValue = "true")
    public NacosServiceDiscovery serviceDiscovery(InfrastructureProperties infrastructureProperties,
                                                        org.springframework.core.env.Environment env) {
        InfrastructureProperties.NacosProperties nacos = infrastructureProperties.getNacos();

        if (nacos == null || nacos.getServerAddr() == null) {
            throw new IllegalStateException("Nacos enabled but serverAddr not configured");
        }

        // ✅ 使用 Builder 模式创建实例
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

        log.info("Nacos 服务发现已启用: serverAddr={}, defaultNamespace={}, username={}, idleTimeout={}min",
                nacos.getServerAddr(), nacos.getDefaultNamespace(),
                nacos.getUsername() != null ? "***" : "null",
                nacos.getClientIdleTimeoutMinutes());

        return builder.build();
    }

    /**
     * 服务发现辅助类 Bean
     */
    @Bean
    public ServiceDiscoveryHelper serviceDiscoveryHelper(
            InfrastructureProperties infrastructureProperties,
            RestTemplate restTemplate,
            @Autowired(required = false) NacosServiceDiscovery nacosDiscovery) {

        log.info("创建 ServiceDiscoveryHelper: nacosEnabled={}", nacosDiscovery != null);

        return new ServiceDiscoveryHelper(
            infrastructureProperties,
            nacosDiscovery,  // 可能为 null
            restTemplate
        );
    }
}

