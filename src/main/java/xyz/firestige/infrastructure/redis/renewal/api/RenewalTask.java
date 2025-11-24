package xyz.firestige.infrastructure.redis.renewal.api;

import xyz.firestige.infrastructure.redis.renewal.condition.CountBasedStopCondition;
import xyz.firestige.infrastructure.redis.renewal.condition.NeverStopCondition;
import xyz.firestige.infrastructure.redis.renewal.condition.TimeBasedStopCondition;
import xyz.firestige.infrastructure.redis.renewal.interval.AdaptiveIntervalStrategy;
import xyz.firestige.infrastructure.redis.renewal.interval.FixedIntervalStrategy;
import xyz.firestige.infrastructure.redis.renewal.strategy.FixedTtlStrategy;
import xyz.firestige.infrastructure.redis.renewal.strategy.MaxRenewalsStrategy;
import xyz.firestige.infrastructure.redis.renewal.strategy.UntilTimeStrategy;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * 续期任务模型
 *
 * <p>定义续期任务的配置信息，使用 Builder 模式创建。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * RenewalTask task = RenewalTask.builder()
 *     .keySelector(new StaticKeySelector(List.of("key1", "key2")))
 *     .ttlStrategy(new FixedTtlStrategy(Duration.ofMinutes(5)))
 *     .intervalStrategy(new FixedIntervalStrategy(Duration.ofMinutes(2)))
 *     .stopCondition(new TimeBasedStopCondition(endTime))
 *     .build();
 * }</pre>
 *
 * @author T-018
 * @since 1.0.0
 */
public class RenewalTask {

    private final KeySelector keySelector;
    private final RenewalStrategy ttlStrategy;
    private final RenewalIntervalStrategy intervalStrategy;
    private final StopCondition stopCondition;
    private final FailureHandler failureHandler;
    private final RenewalLifecycleListener listener;
    private final RenewalFilter filter;
    private final int maxRetries;

    private RenewalTask(Builder builder) {
        this.keySelector = Objects.requireNonNull(builder.keySelector, "keySelector cannot be null");
        this.ttlStrategy = Objects.requireNonNull(builder.ttlStrategy, "ttlStrategy cannot be null");
        this.intervalStrategy = Objects.requireNonNull(builder.intervalStrategy, "intervalStrategy cannot be null");
        this.stopCondition = Objects.requireNonNull(builder.stopCondition, "stopCondition cannot be null");
        this.failureHandler = builder.failureHandler;
        this.listener = builder.listener;
        this.filter = builder.filter;
        this.maxRetries = builder.maxRetries;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 创建固定间隔续期任务（模板方法）
     *
     * @param keys 要续期的 Key 列表
     * @param ttl TTL 时长
     * @param interval 续期间隔
     * @return 续期任务
     */
    public static RenewalTask fixedRenewal(Collection<String> keys, Duration ttl, Duration interval) {
        return builder()
            .keys(keys)
            .ttlStrategy(new FixedTtlStrategy(ttl))
            .intervalStrategy(new FixedIntervalStrategy(interval))
            .stopCondition(new NeverStopCondition())
            .build();
    }

    /**
     * 创建续期至指定时间的任务（模板方法）
     *
     * @param keys 要续期的 Key 列表
     * @param baseTtl 基础 TTL
     * @param endTime 结束时间
     * @return 续期任务
     */
    public static RenewalTask untilTime(Collection<String> keys, Duration baseTtl, Instant endTime) {
        return builder()
            .keys(keys)
            .ttlStrategy(new UntilTimeStrategy(endTime, baseTtl))
            .intervalStrategy(new AdaptiveIntervalStrategy(0.5))
            .stopCondition(new TimeBasedStopCondition(endTime))
            .build();
    }

    /**
     * 创建最多续期 N 次的任务（模板方法）
     *
     * @param keys 要续期的 Key 列表
     * @param ttl TTL 时长
     * @param maxCount 最大续期次数
     * @return 续期任务
     */
    public static RenewalTask maxRenewals(Collection<String> keys, Duration ttl, long maxCount) {
        return builder()
            .keys(keys)
            .ttlStrategy(new MaxRenewalsStrategy(ttl, maxCount))
            .intervalStrategy(new FixedIntervalStrategy(
                Duration.ofMillis(ttl.toMillis() / 2)
            ))
            .stopCondition(new CountBasedStopCondition(maxCount))
            .build();
    }

    // Getters
    public KeySelector getKeySelector() { return keySelector; }
    public RenewalStrategy getTtlStrategy() { return ttlStrategy; }
    public RenewalIntervalStrategy getIntervalStrategy() { return intervalStrategy; }
    public StopCondition getStopCondition() { return stopCondition; }
    public FailureHandler getFailureHandler() { return failureHandler; }
    public RenewalLifecycleListener getListener() { return listener; }
    public RenewalFilter getFilter() { return filter; }
    public int getMaxRetries() { return maxRetries; }

    /**
     * Builder 类
     */
    public static class Builder {
        private KeySelector keySelector;
        private RenewalStrategy ttlStrategy;
        private RenewalIntervalStrategy intervalStrategy;
        private StopCondition stopCondition;
        private FailureHandler failureHandler;
        private RenewalLifecycleListener listener;
        private RenewalFilter filter;
        private int maxRetries = 3;

        /**
         * 设置 Key 选择器
         */
        public Builder keySelector(KeySelector keySelector) {
            this.keySelector = keySelector;
            return this;
        }

        /**
         * 设置固定 Key 列表（快捷方法）
         */
        public Builder keys(Collection<String> keys) {
            this.keySelector = new StaticKeySelector(keys);
            return this;
        }

        /**
         * 设置 TTL 策略
         */
        public Builder ttlStrategy(RenewalStrategy ttlStrategy) {
            this.ttlStrategy = ttlStrategy;
            return this;
        }

        /**
         * 设置续期间隔策略
         */
        public Builder intervalStrategy(RenewalIntervalStrategy intervalStrategy) {
            this.intervalStrategy = intervalStrategy;
            return this;
        }

        /**
         * 设置停止条件
         */
        public Builder stopCondition(StopCondition stopCondition) {
            this.stopCondition = stopCondition;
            return this;
        }

        /**
         * 设置失败处理器
         */
        public Builder failureHandler(FailureHandler failureHandler) {
            this.failureHandler = failureHandler;
            return this;
        }

        /**
         * 设置生命周期监听器
         */
        public Builder listener(RenewalLifecycleListener listener) {
            this.listener = listener;
            return this;
        }

        /**
         * 设置过滤器
         */
        public Builder filter(RenewalFilter filter) {
            this.filter = filter;
            return this;
        }

        /**
         * 设置最大重试次数
         */
        public Builder maxRetries(int maxRetries) {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries must be >= 0");
            }
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * 构建任务
         */
        public RenewalTask build() {
            return new RenewalTask(this);
        }
    }

    /**
     * 静态 Key 选择器（内部类，用于快捷方法）
     */
    private static class StaticKeySelector implements KeySelector {
        private final Collection<String> keys;

        StaticKeySelector(Collection<String> keys) {
            this.keys = Collections.unmodifiableCollection(keys);
        }

        @Override
        public Collection<String> selectKeys(RenewalContext context) {
            return keys;
        }

        @Override
        public String getName() {
            return "StaticKeySelector";
        }
    }
}

