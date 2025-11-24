package xyz.firestige.infrastructure.redis.renewal.condition;

import org.junit.jupiter.api.Test;
import xyz.firestige.infrastructure.redis.renewal.api.RenewalContext;

import static org.junit.jupiter.api.Assertions.*;

class CompositeStopConditionTest {

    @Test
    void allOf_allMustBeTrue() {
        CountBasedStopCondition c1 = new CountBasedStopCondition(5);
        CountBasedStopCondition c2 = new CountBasedStopCondition(3);

        CompositeStopCondition composite = CompositeStopCondition.allOf(c1, c2);
        RenewalContext ctx = new RenewalContext("test");

        ctx.incrementRenewalCount();
        ctx.incrementRenewalCount();
        assertFalse(composite.shouldStop(ctx));

        ctx.incrementRenewalCount();
        assertTrue(composite.shouldStop(ctx));
    }

    @Test
    void anyOf_anyCanBeTrue() {
        CountBasedStopCondition c1 = new CountBasedStopCondition(5);
        CountBasedStopCondition c2 = new CountBasedStopCondition(2);

        CompositeStopCondition composite = CompositeStopCondition.anyOf(c1, c2);
        RenewalContext ctx = new RenewalContext("test");

        ctx.incrementRenewalCount();
        assertFalse(composite.shouldStop(ctx));

        ctx.incrementRenewalCount();
        assertTrue(composite.shouldStop(ctx));
    }
}

