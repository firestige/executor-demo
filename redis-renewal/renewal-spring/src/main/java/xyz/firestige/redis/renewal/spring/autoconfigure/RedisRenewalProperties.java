package xyz.firestige.redis.renewal.spring.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis 续期服务配置属性
 */
@ConfigurationProperties(prefix = "redis.renewal")
public class RedisRenewalProperties {

    /**
     * 是否启用续期服务
     */
    private boolean enabled = true;

    /**
     * 时间轮配置
     */
    private TimeWheel timeWheel = new TimeWheel();

    /**
     * 执行器线程池大小
     */
    private int executorThreadPoolSize = 4;

    /**
     * 执行器队列容量
     */
    private int executorQueueCapacity = 1000;

    /**
     * 指标报告间隔（秒）
     */
    private long metricsReportInterval = 60;

    /**
     * 默认续期间隔（秒）
     */
    private long defaultRenewalInterval = 30;

    /**
     * 默认 TTL（秒）
     */
    private long defaultTtl = 300;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public TimeWheel getTimeWheel() {
        return timeWheel;
    }

    public void setTimeWheel(TimeWheel timeWheel) {
        this.timeWheel = timeWheel;
    }

    public int getExecutorThreadPoolSize() {
        return executorThreadPoolSize;
    }

    public void setExecutorThreadPoolSize(int executorThreadPoolSize) {
        this.executorThreadPoolSize = executorThreadPoolSize;
    }

    public int getExecutorQueueCapacity() {
        return executorQueueCapacity;
    }

    public void setExecutorQueueCapacity(int executorQueueCapacity) {
        this.executorQueueCapacity = executorQueueCapacity;
    }

    public long getMetricsReportInterval() {
        return metricsReportInterval;
    }

    public void setMetricsReportInterval(long metricsReportInterval) {
        this.metricsReportInterval = metricsReportInterval;
    }

    public long getDefaultRenewalInterval() {
        return defaultRenewalInterval;
    }

    public void setDefaultRenewalInterval(long defaultRenewalInterval) {
        this.defaultRenewalInterval = defaultRenewalInterval;
    }

    public long getDefaultTtl() {
        return defaultTtl;
    }

    public void setDefaultTtl(long defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    /**
     * 时间轮配置
     */
    public static class TimeWheel {
        /**
         * Tick 间隔（毫秒）
         */
        private long tickDuration = 100;

        /**
         * 时间轮槽数
         */
        private int ticksPerWheel = 512;

        public long getTickDuration() {
            return tickDuration;
        }

        public void setTickDuration(long tickDuration) {
            this.tickDuration = tickDuration;
        }

        public int getTicksPerWheel() {
            return ticksPerWheel;
        }

        public void setTicksPerWheel(int ticksPerWheel) {
            this.ticksPerWheel = ticksPerWheel;
        }
    }
}

