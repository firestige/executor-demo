package xyz.firestige.redis.renewal.api;
import java.time.Duration;
/**
 * 续期间隔策略接口
 */
@FunctionalInterface
public interface IntervalStrategy {
    Duration nextInterval(RenewalContext context);
    static IntervalStrategy fixed(Duration interval) {
        return ctx -> interval;
    }
}
