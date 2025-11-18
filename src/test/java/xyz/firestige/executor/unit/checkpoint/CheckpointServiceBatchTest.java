package xyz.firestige.executor.unit.checkpoint;

import org.junit.jupiter.api.Test;
import xyz.firestige.executor.checkpoint.CheckpointService;
import xyz.firestige.executor.checkpoint.InMemoryCheckpointStore;
import xyz.firestige.executor.domain.task.TaskAggregate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CheckpointServiceBatchTest {

    @Test
    void loadMultipleReturnsMapForExistingIds() {
        CheckpointService svc = new CheckpointService(new InMemoryCheckpointStore());
        TaskAggregate t1 = new TaskAggregate("task-t1","p","ten");
        TaskAggregate t2 = new TaskAggregate("task-t2","p","ten");
        svc.saveCheckpoint(t1, List.of("s1"), 0);
        svc.saveCheckpoint(t2, List.of("s1","s2"), 1);
        var map = svc.loadMultiple(List.of("t1","t2","t3"));
        assertEquals(2, map.size());
        assertEquals(0, map.get("t1").getLastCompletedStageIndex());
        assertEquals(1, map.get("t2").getLastCompletedStageIndex());
        assertNull(map.get("t3"));
    }
}

