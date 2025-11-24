package xyz.firestige.infrastructure.redis.renewal.condition;

import org.junit.jupiter.api.Test;
import xyz.firestige.infrastructure.redis.renewal.api.RenewalContext;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ExternalSignalStopConditionTest {

    @Test
    void shouldStop_basedOnSupplier() {
        AtomicBoolean signal = new AtomicBoolean(false);
        ExternalSignalStopCondition condition = new ExternalSignalStopCondition(signal::get);

        RenewalContext ctx = new RenewalContext("test");
        assertFalse(condition.shouldStop(ctx));

        signal.set(true);
        assertTrue(condition.shouldStop(ctx));

        signal.set(false);
        assertFalse(condition.shouldStop(ctx));
    }
}

