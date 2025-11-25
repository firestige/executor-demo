package xyz.firestige.redis.renewal.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 续期任务配置
 * <p>
 * 不可变对象，包含续期任务的所有配置信息。
 *
 * @author AI
 * @since 1.0
 */
public final class RenewalTask {

    private final String taskId;
    private final List<String> keys;
    private final TtlStrategy ttlStrategy;
    private final IntervalStrategy intervalStrategy;
    private final StopCondition stopCondition;
    private final KeySelector keySelector;
    private final KeyGenerator keyGenerator;
    private final KeyFilter keyFilter;

    private RenewalTask(Builder builder) {
        this.taskId = builder.taskId != null ? builder.taskId : generateTaskId();
        this.keys = Collections.unmodifiableList(builder.keys);
        this.ttlStrategy = Objects.requireNonNull(builder.ttlStrategy, "ttlStrategy is required");
        this.intervalStrategy = Objects.requireNonNull(builder.intervalStrategy, "intervalStrategy is required");
        this.stopCondition = builder.stopCondition;
        this.keySelector = builder.keySelector;
        this.keyGenerator = builder.keyGenerator;
        this.keyFilter = builder.keyFilter;
    }

    // Getters
    public String getTaskId() { return taskId; }
    public List<String> getKeys() { return keys; }
    public TtlStrategy getTtlStrategy() { return ttlStrategy; }
    public IntervalStrategy getIntervalStrategy() { return intervalStrategy; }
    public StopCondition getStopCondition() { return stopCondition; }
    public KeySelector getKeySelector() { return keySelector; }
    public KeyGenerator getKeyGenerator() { return keyGenerator; }
    public KeyFilter getKeyFilter() { return keyFilter; }

    // Static factory methods

    /**
     * 创建固定 TTL + 固定间隔的简单续期任务
     */
    public static RenewalTask fixedRenewal(List<String> keys, Duration ttl, Duration interval) {
        return builder()
            .keys(keys)
            .ttlStrategy(TtlStrategy.fixed(ttl))
            .intervalStrategy(IntervalStrategy.fixed(interval))
            .build();
    }

    /**
     * 创建续期至指定时间的任务
     */
    public static RenewalTask untilTime(List<String> keys, Duration ttl, Instant stopTime) {
        return builder()
            .keys(keys)
            .ttlStrategy(TtlStrategy.fixed(ttl))
            .intervalStrategy(IntervalStrategy.fixed(Duration.ofSeconds(ttl.toSeconds() / 2)))
            .stopCondition(StopCondition.untilTime(stopTime))
            .build();
    }

    /**
     * 创建最大续期次数限制的任务
     */
    public static RenewalTask maxRenewals(List<String> keys, Duration ttl, Duration interval, int maxRenewals) {
        return builder()
            .keys(keys)
            .ttlStrategy(TtlStrategy.fixed(ttl))
            .intervalStrategy(IntervalStrategy.fixed(interval))
            .stopCondition(StopCondition.maxRenewals(maxRenewals))
            .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String taskId;
        private List<String> keys;
        private TtlStrategy ttlStrategy;
        private IntervalStrategy intervalStrategy;
        private StopCondition stopCondition;
        private KeySelector keySelector;
        private KeyGenerator keyGenerator;
        private KeyFilter keyFilter;

        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder keys(List<String> keys) {
            this.keys = keys;
            return this;
        }

        public Builder ttlStrategy(TtlStrategy ttlStrategy) {
            this.ttlStrategy = ttlStrategy;
            return this;
        }

        public Builder intervalStrategy(IntervalStrategy intervalStrategy) {
            this.intervalStrategy = intervalStrategy;
            return this;
        }

        public Builder stopCondition(StopCondition stopCondition) {
            this.stopCondition = stopCondition;
            return this;
        }

        public Builder keySelector(KeySelector keySelector) {
            this.keySelector = keySelector;
            return this;
        }

        public Builder keyGenerator(KeyGenerator keyGenerator) {
            this.keyGenerator = keyGenerator;
            return this;
        }

        public Builder keyFilter(KeyFilter keyFilter) {
            this.keyFilter = keyFilter;
            return this;
        }

        public RenewalTask build() {
            return new RenewalTask(this);
        }
    }

    private static String generateTaskId() {
        return "renewal-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000);
    }
}

