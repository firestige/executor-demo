package xyz.firestige.deploy.unit.orchestration;

import org.junit.jupiter.api.Test;
import xyz.firestige.deploy.support.conflict.ConflictRegistry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

