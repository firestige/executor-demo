package xyz.firestige.infrastructure.redis.renewal.condition;

import org.junit.jupiter.api.Test;
import xyz.firestige.infrastructure.redis.renewal.api.RenewalContext;

import static org.junit.jupiter.api.Assertions.*;

class CountBasedStopConditionTest {

    @Test
    void shouldStop_belowMax_false() {
        CountBasedStopCondition condition = new CountBasedStopCondition(5);
        RenewalContext ctx = new RenewalContext("test");

        ctx.incrementRenewalCount();
        assertFalse(condition.shouldStop(ctx));

        ctx.incrementRenewalCount();
        ctx.incrementRenewalCount();
        ctx.incrementRenewalCount();
        assertFalse(condition.shouldStop(ctx));
    }

    @Test
    void shouldStop_reachedMax_true() {
        CountBasedStopCondition condition = new CountBasedStopCondition(3);
        RenewalContext ctx = new RenewalContext("test");

        ctx.incrementRenewalCount();
        ctx.incrementRenewalCount();
        ctx.incrementRenewalCount();

        assertTrue(condition.shouldStop(ctx));
    }
}

