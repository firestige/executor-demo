package xyz.firestige.deploy.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import xyz.firestige.deploy.infrastructure.config.model.DeploymentConfig;
import xyz.firestige.deploy.infrastructure.config.model.InfrastructureConfig;
import xyz.firestige.deploy.infrastructure.config.model.ServiceTypeConfig;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * YAML 配置加载器
 * 
 * 职责：
 * 1. 从 classpath 加载 deploy-stages.yml
 * 2. 解析为配置对象
 * 3. 提供配置查询接口
 */
@Component
public class DeploymentConfigLoader {
    
    private static final Logger log = LoggerFactory.getLogger(DeploymentConfigLoader.class);
    private static final String CONFIG_FILE = "deploy-stages.yml";
    
    private DeploymentConfig config;
    private final ObjectMapper yamlMapper;
    
    public DeploymentConfigLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }
    
    @PostConstruct
    public void loadConfig() {
        try {
            log.info("Loading deployment configuration from {}", CONFIG_FILE);
            this.config = loadFromYaml(CONFIG_FILE);
            log.info("Deployment configuration loaded successfully");
            
            // 验证配置
            validateConfig();
        } catch (IOException e) {
            log.error("Failed to load deployment configuration", e);
            throw new IllegalStateException("Cannot load deployment configuration", e);
        }
    }
    
    /**
     * 从 classpath 加载 YAML 配置
     */
    private DeploymentConfig loadFromYaml(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource(filename);
        try (InputStream inputStream = resource.getInputStream()) {
            return yamlMapper.readValue(inputStream, DeploymentConfig.class);
        }
    }
    
    /**
     * 获取基础设施配置
     */
    public InfrastructureConfig getInfrastructure() {
        if (config == null || config.getInfrastructure() == null) {
            throw new IllegalStateException("Infrastructure configuration not loaded");
        }
        return config.getInfrastructure();
    }
    
    /**
     * 获取服务类型配置（通过服务名称）
     */
    public ServiceTypeConfig getServiceType(String serviceName) {
        if (config == null || config.getServices() == null) {
            throw new IllegalStateException("Service configuration not loaded");
        }
        return config.getServices().get(serviceName);
    }

    /**
     * 获取服务配置（别名方法，语义更清晰）
     */
    public ServiceTypeConfig getServiceConfig(String serviceName) {
        return getServiceType(serviceName);
    }

    /**
     * 检查是否支持指定的服务
     */
    public boolean supportsServiceType(String serviceName) {
        return config != null
                && config.getServices() != null
                && config.getServices().containsKey(serviceName);
    }

    /**
     * 获取默认服务名称列表
     *
     * @return 默认服务名称列表（有序），如果配置中未定义则返回空列表
     */
    public List<String> getDefaultServiceNames() {
        if (config == null) {
            throw new IllegalStateException("Configuration not loaded");
        }

        List<String> defaultNames = config.getDefaultServiceNames();
        if (defaultNames == null || defaultNames.isEmpty()) {
            log.warn("No default service names configured, returning empty list");
            return List.of();
        }

        log.debug("Loaded default service names: {}", defaultNames);
        return List.copyOf(defaultNames);  // 返回不可变副本
    }

    /**
     * 获取所有已配置的服务名称
     */
    public List<String> getAllServiceNames() {
        if (config == null || config.getServices() == null) {
            return List.of();
        }
        return List.copyOf(config.getServices().keySet());
    }
    
    /**
     * 验证配置完整性
     */
    private void validateConfig() {
        if (config == null) {
            throw new IllegalStateException("Configuration is null");
        }
        
        if (config.getInfrastructure() == null) {
            throw new IllegalStateException("Infrastructure configuration is missing");
        }
        
        if (config.getServices() == null || config.getServices().isEmpty()) {
            throw new IllegalStateException("No services configured");
        }
        
        log.info("Configuration validated: {} services configured",
                config.getServices().size());
    }
}
