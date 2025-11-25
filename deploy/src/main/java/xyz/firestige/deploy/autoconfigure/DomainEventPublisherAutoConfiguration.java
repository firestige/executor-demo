package xyz.firestige.deploy.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.firestige.deploy.domain.shared.event.DomainEventPublisher;
import xyz.firestige.deploy.infrastructure.event.CompositeDomainEventPublisher;
import xyz.firestige.deploy.infrastructure.event.SpringDomainEventPublisher;
import xyz.firestige.deploy.infrastructure.message.KafkaDomainEventPublisher;
import xyz.firestige.deploy.infrastructure.message.RocketMQDomainEventPublisher;

 import java.util.ArrayList;

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

    /**
     * Kafka 事件发布器
     */
    @Bean
    @ConditionalOnMissingBean(DomainEventPublisher.class)
    @ConditionalOnProperty(name = "executor.event.publisher.type", havingValue = "kafka")
    @ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
    public DomainEventPublisher kafkaDomainEventPublisher(
            Object kafkaTemplate, // 实际类型：KafkaTemplate<String, String>
            ObjectMapper objectMapper,
            DomainEventPublisherProperties properties) {

        String topicPrefix = properties.getKafka().getTopicPrefix();
        log.info("Configuring KafkaDomainEventPublisher with topic prefix: {}", topicPrefix);
        return new KafkaDomainEventPublisher(kafkaTemplate, objectMapper, topicPrefix);
    }

    /**
     * RocketMQ 事件发布器
     */
    @Bean
    @ConditionalOnMissingBean(DomainEventPublisher.class)
    @ConditionalOnProperty(name = "executor.event.publisher.type", havingValue = "rocketmq")
    @ConditionalOnClass(name = "org.apache.rocketmq.spring.core.RocketMQTemplate")
    public DomainEventPublisher rocketMQDomainEventPublisher(
            Object rocketMQTemplate, // 实际类型：RocketMQTemplate
            ObjectMapper objectMapper,
            DomainEventPublisherProperties properties) {

        String topicPrefix = properties.getRocketmq().getTopicPrefix();
        log.info("Configuring RocketMQDomainEventPublisher with topic prefix: {}", topicPrefix);
        return new RocketMQDomainEventPublisher(rocketMQTemplate, objectMapper, topicPrefix);
    }

    /**
     * 复合事件发布器（支持多目标发布）
     */
    @Bean
    @ConditionalOnMissingBean(DomainEventPublisher.class)
    @ConditionalOnProperty(name = "executor.event.publisher.type", havingValue = "composite")
    public DomainEventPublisher compositeDomainEventPublisher(
            ApplicationEventPublisher applicationEventPublisher,
            ObjectMapper objectMapper,
            DomainEventPublisherProperties properties) {

        var composite = properties.getComposite();
        var publishers = new ArrayList<DomainEventPublisher>();

        // 本地事件总线
        if (composite.isEnableLocal()) {
            log.info("Composite mode: adding SpringDomainEventPublisher");
            publishers.add(new SpringDomainEventPublisher(applicationEventPublisher));
        }

        // Kafka
        if (composite.isEnableKafka()) {
            try {
                Object kafkaTemplate = getKafkaTemplate();
                String topicPrefix = properties.getKafka().getTopicPrefix();
                log.info("Composite mode: adding KafkaDomainEventPublisher with topic prefix: {}", topicPrefix);
                publishers.add(new KafkaDomainEventPublisher(kafkaTemplate, objectMapper, topicPrefix));
            } catch (Exception e) {
                log.warn("Composite mode: KafkaTemplate not available, skipping Kafka publisher", e);
            }
        }

        // RocketMQ
        if (composite.isEnableRocketmq()) {
            try {
                Object rocketMQTemplate = getRocketMQTemplate();
                String topicPrefix = properties.getRocketmq().getTopicPrefix();
                log.info("Composite mode: adding RocketMQDomainEventPublisher with topic prefix: {}", topicPrefix);
                publishers.add(new RocketMQDomainEventPublisher(rocketMQTemplate, objectMapper, topicPrefix));
            } catch (Exception e) {
                log.warn("Composite mode: RocketMQTemplate not available, skipping RocketMQ publisher", e);
            }
        }

        if (publishers.isEmpty()) {
            log.warn("Composite mode: no publishers configured, falling back to Spring local event bus");
            publishers.add(new SpringDomainEventPublisher(applicationEventPublisher));
        }

        log.info("Configuring CompositeDomainEventPublisher with {} publisher(s)", publishers.size());
        return new CompositeDomainEventPublisher(publishers.toArray(new DomainEventPublisher[0]));
    }

    // Helper methods to resolve optional dependencies
    private Object getKafkaTemplate() throws Exception {
        // 通过 Spring 上下文查找 KafkaTemplate Bean
        throw new UnsupportedOperationException("KafkaTemplate resolution not implemented yet");
    }

    private Object getRocketMQTemplate() throws Exception {
        // 通过 Spring 上下文查找 RocketMQTemplate Bean
        throw new UnsupportedOperationException("RocketMQTemplate resolution not implemented yet");
    }
}

