package xyz.firestige.executor.unit.orchestration;

import org.junit.jupiter.api.Test;
import xyz.firestige.executor.support.conflict.ConflictRegistry;

import static org.junit.jupiter.api.Assertions.*;

public class ConflictReleaseOnTerminalTest {

    @Test
    void releaseOnTerminalStates() {
        ConflictRegistry r = new ConflictRegistry();
        String tenant = "t-1";
        assertTrue(r.register(tenant, "taskA"));
        assertTrue(r.isRunning(tenant));
        r.release(tenant);
        assertFalse(r.isRunning(tenant));
    }
}

