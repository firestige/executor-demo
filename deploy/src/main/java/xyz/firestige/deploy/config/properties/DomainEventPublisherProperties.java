package xyz.firestige.deploy.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 领域事件发布器配置属性
 *
 * @since RF-11 改进版
 */
@ConfigurationProperties(prefix = "executor.event.publisher")
public class DomainEventPublisherProperties {

    /**
     * 发布器类型：spring（默认）, kafka, rocketmq, composite
     */
    private String type = "spring";

    /**
     * Kafka 配置
     */
    private KafkaProperties kafka = new KafkaProperties();

    /**
     * RocketMQ 配置
     */
    private RocketMQProperties rocketmq = new RocketMQProperties();

    /**
     * 复合模式配置
     */
    private CompositeProperties composite = new CompositeProperties();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public KafkaProperties getKafka() {
        return kafka;
    }

    public void setKafka(KafkaProperties kafka) {
        this.kafka = kafka;
    }

    public RocketMQProperties getRocketmq() {
        return rocketmq;
    }

    public void setRocketmq(RocketMQProperties rocketmq) {
        this.rocketmq = rocketmq;
    }

    public CompositeProperties getComposite() {
        return composite;
    }

    public void setComposite(CompositeProperties composite) {
        this.composite = composite;
    }

    /**
     * Kafka 配置属性
     */
    public static class KafkaProperties {
        /**
         * Topic 前缀，默认：executor.domain.events
         */
        private String topicPrefix = "executor.domain.events";

        public String getTopicPrefix() {
            return topicPrefix;
        }

        public void setTopicPrefix(String topicPrefix) {
            this.topicPrefix = topicPrefix;
        }
    }

    /**
     * RocketMQ 配置属性
     */
    public static class RocketMQProperties {
        /**
         * Topic 前缀，默认：executor_domain_events
         */
        private String topicPrefix = "executor_domain_events";

        public String getTopicPrefix() {
            return topicPrefix;
        }

        public void setTopicPrefix(String topicPrefix) {
            this.topicPrefix = topicPrefix;
        }
    }

    /**
     * 复合模式配置属性
     */
    public static class CompositeProperties {
        /**
         * 是否启用本地事件总线，默认：true
         */
        private boolean enableLocal = true;

        /**
         * 是否启用 Kafka，默认：false
         */
        private boolean enableKafka = false;

        /**
         * 是否启用 RocketMQ，默认：false
         */
        private boolean enableRocketmq = false;

        /**
         * 是否快速失败（任一发布器失败立即抛异常），默认：false
         */
        private boolean failFast = false;

        public boolean isEnableLocal() {
            return enableLocal;
        }

        public void setEnableLocal(boolean enableLocal) {
            this.enableLocal = enableLocal;
        }

        public boolean isEnableKafka() {
            return enableKafka;
        }

        public void setEnableKafka(boolean enableKafka) {
            this.enableKafka = enableKafka;
        }

        public boolean isEnableRocketmq() {
            return enableRocketmq;
        }

        public void setEnableRocketmq(boolean enableRocketmq) {
            this.enableRocketmq = enableRocketmq;
        }

        public boolean isFailFast() {
            return failFast;
        }

        public void setFailFast(boolean failFast) {
            this.failFast = failFast;
        }
    }
}

