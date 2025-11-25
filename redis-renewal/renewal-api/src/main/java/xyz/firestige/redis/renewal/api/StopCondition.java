package xyz.firestige.redis.renewal.api;
import java.time.Instant;
@FunctionalInterface
public interface StopCondition {
    boolean shouldStop(RenewalContext context);
    static StopCondition never() {
        return ctx -> false;
    }
    static StopCondition untilTime(Instant stopTime) {
        return ctx -> Instant.now().isAfter(stopTime);
    }
    static StopCondition maxRenewals(int max) {
        return ctx -> ctx.getRenewalCount() >= max;
    }
}
