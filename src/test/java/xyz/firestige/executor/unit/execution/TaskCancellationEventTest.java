package xyz.firestige.executor.unit.execution;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import xyz.firestige.executor.state.TaskStateManager;
import xyz.firestige.executor.state.TaskStatus;
import xyz.firestige.executor.state.event.TaskCancelledEvent;
import xyz.firestige.executor.state.event.TaskStatusEvent;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TaskCancellationEventTest {

    @Test
    void testCancellationEventPublished() {
        List<TaskStatusEvent> events = new ArrayList<>();
        ApplicationEventPublisher publisher = e -> { if (e instanceof TaskStatusEvent) events.add((TaskStatusEvent)e); };
        TaskStateManager mgr = new TaskStateManager(publisher);
        String taskId = "t-cancel";
        mgr.initializeTask(taskId, TaskStatus.PENDING);
        mgr.publishTaskCancelledEvent(taskId);
        boolean found = events.stream().anyMatch(e -> e instanceof TaskCancelledEvent);
        assertTrue(found, "Cancellation event should be published");
        long seq = events.stream().filter(e -> e instanceof TaskCancelledEvent).findFirst().get().getSequenceId();
        assertTrue(seq > 0, "Sequence id should be set on cancellation event");
    }
}

