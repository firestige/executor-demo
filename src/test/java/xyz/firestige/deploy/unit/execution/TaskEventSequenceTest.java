package xyz.firestige.deploy.unit.execution;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import xyz.firestige.deploy.state.TaskStateManager;
import xyz.firestige.deploy.state.TaskStatus;
import xyz.firestige.deploy.state.event.TaskStatusEvent;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TaskEventSequenceTest {

    @Test
    void testSequenceIncrement() {
        List<TaskStatusEvent> captured = new ArrayList<>();
        ApplicationEventPublisher publisher = event -> {
            if (event instanceof TaskStatusEvent) {
                captured.add((TaskStatusEvent) event);
            }
        };
        TaskStateManager mgr = new TaskStateManager(publisher);
        String taskId = "t-seq";
        mgr.initializeTask(taskId, TaskStatus.CREATED);
        mgr.updateState(taskId, TaskStatus.VALIDATING);
        mgr.updateState(taskId, TaskStatus.PENDING);
        mgr.publishTaskStartedEvent(taskId, 3);
        mgr.publishTaskProgressEvent(taskId, "Stage1", 1, 3);
        mgr.publishTaskStageCompletedEvent(taskId, "Stage1", null);
        mgr.publishTaskCompletedEvent(taskId, java.time.Duration.ofMillis(10), List.of("Stage1"));

        // sequenceId 应该严格递增且从 1 开始
        long prev = 0;
        for (TaskStatusEvent e : captured) {
            assertTrue(e.getSequenceId() > prev, "sequenceId should increase");
            prev = e.getSequenceId();
        }
        assertEquals(captured.size(), prev, "last sequence should equal count if starting at 1 and no gaps");
    }
}

