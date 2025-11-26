package xyz.firestige.deploy.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.firestige.deploy.domain.shared.event.DomainEventPublisher;
import xyz.firestige.deploy.infrastructure.event.SpringDomainEventPublisher;

/**
 * 领域事件发布器自动配置
 *
 * 配置属性：
 * - executor.event.publisher.type: 发布器类型（spring/kafka/rocketmq/composite）
 * - executor.event.publisher.kafka.topic-prefix: Kafka Topic 前缀
 * - executor.event.publisher.rocketmq.topic-prefix: RocketMQ Topic 前缀
 * - executor.event.publisher.composite.enable-local: 复合模式下是否启用本地事件
 * - executor.event.publisher.composite.enable-kafka: 复合模式下是否启用 Kafka
 * - executor.event.publisher.composite.enable-rocketmq: 复合模式下是否启用 RocketMQ
 *
 * 使用示例：
 * <pre>
 * # 单机部署 - 使用 Spring 本地事件
 * executor.event.publisher.type=spring
 *
 * # 集群部署 - 使用 Kafka
 * executor.event.publisher.type=kafka
 * executor.event.publisher.kafka.topic-prefix=executor.domain.events
 *
 * # 渐进式迁移 - 双写模式
 * executor.event.publisher.type=composite
 * executor.event.publisher.composite.enable-local=true
 * executor.event.publisher.composite.enable-kafka=true
 * executor.event.publisher.kafka.topic-prefix=executor.domain.events
 * </pre>
 *
 * @since RF-11 改进版
 */
@Configuration
@EnableConfigurationProperties(DomainEventPublisherProperties.class)
public class DomainEventPublisherAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisherAutoConfiguration.class);

    /**
     * Spring 本地事件发布器（默认配置）
     */
    @Bean
    @ConditionalOnMissingBean(DomainEventPublisher.class)
    @ConditionalOnProperty(name = "executor.event.publisher.type", havingValue = "spring", matchIfMissing = true)
    public DomainEventPublisher springDomainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        log.info("Configuring SpringDomainEventPublisher (local event bus for standalone deployment)");
        return new SpringDomainEventPublisher(applicationEventPublisher);
    }
}

