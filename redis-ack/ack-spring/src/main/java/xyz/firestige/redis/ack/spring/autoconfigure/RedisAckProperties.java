package xyz.firestige.redis.ack.spring.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Redis ACK 服务配置属性
 *
 * @author AI
 * @since 1.0
 */
@ConfigurationProperties(prefix = "redis.ack")
public class RedisAckProperties {

    /**
     * 是否启用 Redis ACK 服务
     */
    private boolean enabled = true;

    /**
     * 默认超时时间
     */
    private Duration defaultTimeout = Duration.ofSeconds(60);

    /**
     * 默认重试策略配置
     */
    private RetryConfig defaultRetry = new RetryConfig();

    /**
     * HTTP 客户端配置
     */
    private HttpConfig http = new HttpConfig();

    /**
     * Pub/Sub 配置
     */
    private PubSubConfig pubsub = new PubSubConfig();

    /**
     * 监控配置
     */
    private MetricsConfig metrics = new MetricsConfig();

    // Getters and Setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getDefaultTimeout() {
        return defaultTimeout;
    }

    public void setDefaultTimeout(Duration defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
    }

    public RetryConfig getDefaultRetry() {
        return defaultRetry;
    }

    public void setDefaultRetry(RetryConfig defaultRetry) {
        this.defaultRetry = defaultRetry;
    }

    public HttpConfig getHttp() {
        return http;
    }

    public void setHttp(HttpConfig http) {
        this.http = http;
    }

    public PubSubConfig getPubsub() {
        return pubsub;
    }

    public void setPubsub(PubSubConfig pubsub) {
        this.pubsub = pubsub;
    }

    public MetricsConfig getMetrics() {
        return metrics;
    }

    public void setMetrics(MetricsConfig metrics) {
        this.metrics = metrics;
    }

    /**
     * 重试策略配置
     */
    public static class RetryConfig {
        /**
         * 重试策略类型：fixed-delay, exponential-backoff
         */
        private String type = "fixed-delay";

        /**
         * 最大重试次数
         */
        private int maxAttempts = 10;

        /**
         * 固定延迟（固定延迟策略）
         */
        private Duration delay = Duration.ofSeconds(3);

        /**
         * 初始延迟（指数退避策略）
         */
        private Duration initialDelay = Duration.ofSeconds(1);

        /**
         * 倍增因子（指数退避策略）
         */
        private double multiplier = 2.0;

        // Getters and Setters

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getDelay() {
            return delay;
        }

        public void setDelay(Duration delay) {
            this.delay = delay;
        }

        public Duration getInitialDelay() {
            return initialDelay;
        }

        public void setInitialDelay(Duration initialDelay) {
            this.initialDelay = initialDelay;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public void setMultiplier(double multiplier) {
            this.multiplier = multiplier;
        }
    }

    /**
     * HTTP 客户端配置
     */
    public static class HttpConfig {
        /**
         * 连接超时
         */
        private Duration connectTimeout = Duration.ofSeconds(5);

        /**
         * 读取超时
         */
        private Duration readTimeout = Duration.ofSeconds(10);

        /**
         * 最大连接数
         */
        private int maxConnections = 50;

        // Getters and Setters

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }

        public int getMaxConnections() {
            return maxConnections;
        }

        public void setMaxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
        }
    }

    /**
     * Pub/Sub 配置
     */
    public static class PubSubConfig {
        /**
         * 默认 Topic 前缀
         */
        private String defaultTopicPrefix = "ack:";

        // Getters and Setters

        public String getDefaultTopicPrefix() {
            return defaultTopicPrefix;
        }

        public void setDefaultTopicPrefix(String defaultTopicPrefix) {
            this.defaultTopicPrefix = defaultTopicPrefix;
        }
    }

    /**
     * 监控配置
     */
    public static class MetricsConfig {
        /**
         * 是否启用指标收集
         */
        private boolean enabled = true;

        /**
         * 指标报告间隔
         */
        private Duration reportInterval = Duration.ofSeconds(60);

        // Getters and Setters

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getReportInterval() {
            return reportInterval;
        }

        public void setReportInterval(Duration reportInterval) {
            this.reportInterval = reportInterval;
        }
    }
}

