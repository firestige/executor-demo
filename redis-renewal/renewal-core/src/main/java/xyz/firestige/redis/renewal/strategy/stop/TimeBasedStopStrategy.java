package xyz.firestige.redis.renewal.strategy.stop;

import xyz.firestige.redis.renewal.RenewalContext;

import java.time.Instant;
import java.util.Objects;

public class TimeBasedStopStrategy implements StopStrategy {

    private final Instant endTime;

    public TimeBasedStopStrategy(Instant endTime) {
        this.endTime = Objects.requireNonNull(endTime);
    }

    @Override
    public boolean shouldStop(RenewalContext context) {
        return Instant.now().isAfter(endTime);
    }

    @Override
    public String getName() {
        return "TimeBasedStop";
    }
}

