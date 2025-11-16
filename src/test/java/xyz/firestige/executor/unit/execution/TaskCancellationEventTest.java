package xyz.firestige.executor.unit.execution;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskRuntimeContext;
import xyz.firestige.executor.state.TaskStateManager;
import xyz.firestige.executor.state.TaskStatus;
import xyz.firestige.executor.state.event.TaskCancelledEvent;
import xyz.firestige.executor.state.event.TaskStatusEvent;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TaskCancellationEventTest {

    @Test
    void testCancellationEventPublished() {
        List<TaskStatusEvent> events = new ArrayList<>();
        ApplicationEventPublisher publisher = e -> { if (e instanceof TaskStatusEvent) events.add((TaskStatusEvent)e); };
        TaskStateManager mgr = new TaskStateManager(publisher);
        String taskId = "t-cancel";
        mgr.initializeTask(taskId, TaskStatus.PENDING);
        // prepare aggregate and stage names to resolve lastStage
        TaskAggregate agg = new TaskAggregate(taskId, "plan-x", "tenant-1");
        TaskRuntimeContext ctx = new TaskRuntimeContext("plan-x", taskId, "tenant-1", null);
        mgr.registerTaskAggregate(taskId, agg, ctx, 2);
        mgr.registerStageNames(taskId, List.of("stage-A", "stage-B"));
        agg.setCurrentStageIndex(2); // means last completed is index 1 => stage-B

        mgr.publishTaskCancelledEvent(taskId, "test");
        boolean found = events.stream().anyMatch(e -> e instanceof TaskCancelledEvent);
        assertTrue(found, "Cancellation event should be published");
        TaskCancelledEvent evt = (TaskCancelledEvent) events.stream().filter(e -> e instanceof TaskCancelledEvent).findFirst().get();
        assertTrue(evt.getSequenceId() > 0, "Sequence id should be set on cancellation event");
        assertEquals("test", evt.getCancelledBy(), "cancelledBy should be set");
        assertEquals("stage-B", evt.getLastStage(), "lastStage should resolve from registered stage names");
    }
}
