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
     * 获取服务类型配置
     */
    public ServiceTypeConfig getServiceType(String serviceType) {
        if (config == null || config.getServiceTypes() == null) {
            throw new IllegalStateException("Service type configuration not loaded");
        }
        return config.getServiceTypes().get(serviceType);
    }
    
    /**
     * 检查是否支持指定的服务类型
     */
    public boolean supportsServiceType(String serviceType) {
        return config != null 
                && config.getServiceTypes() != null 
                && config.getServiceTypes().containsKey(serviceType);
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
        
        if (config.getServiceTypes() == null || config.getServiceTypes().isEmpty()) {
            throw new IllegalStateException("No service types configured");
        }
        
        log.info("Configuration validated: {} service types configured", 
                config.getServiceTypes().size());
    }
}
