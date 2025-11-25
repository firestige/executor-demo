package xyz.firestige.deploy.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import xyz.firestige.deploy.config.ExecutorStagesProperties;

/**
 * 阶段配置自动装配 (Phase2)
 * 依赖 @EnableConfigurationProperties 自动注册绑定的 Bean，避免重复定义。
 */
@AutoConfiguration
@EnableConfigurationProperties(ExecutorStagesProperties.class)
public class ExecutorStagesAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(ExecutorStagesAutoConfiguration.class);
}
