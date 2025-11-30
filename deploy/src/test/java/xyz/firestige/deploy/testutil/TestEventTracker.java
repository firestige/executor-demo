package xyz.firestige.deploy.testutil;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.task.TaskStatus;
import xyz.firestige.deploy.domain.task.event.TaskCompletedEvent;
import xyz.firestige.deploy.domain.task.event.TaskFailedEvent;
import xyz.firestige.deploy.domain.task.event.TaskPausedEvent;
import xyz.firestige.deploy.domain.task.event.TaskResumedEvent;
import xyz.firestige.deploy.domain.task.event.TaskRolledBackEvent;
import xyz.firestige.deploy.domain.task.event.TaskStageCompletedEvent;
import xyz.firestige.deploy.domain.task.event.TaskStageFailedEvent;
import xyz.firestige.deploy.domain.task.event.TaskStageStartedEvent;
import xyz.firestige.deploy.domain.task.event.TaskStartedEvent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class TestEventTracker {

    private final List<TrackedEvent> events = Collections.synchronizedList(new ArrayList<>());

    @EventListener
    public void onTaskStarted(TaskStartedEvent event) {
        events.add(new TrackedEvent(
                EventType.TASK_STARTED,
                event.getTaskId(),
                event.getStatus(),
                null
        ));
    }

    @EventListener
    public void onTaskCompleted(TaskCompletedEvent event) {
        events.add(new TrackedEvent(
                EventType.TASK_COMPLETED,
                event.getTaskId(),
                event.getStatus(),
                null
        ));
    }

    @EventListener
    public void onTaskFailed(TaskFailedEvent event) {
        events.add(TrackedEvent.of(event));
    }

    @EventListener
    public void onTaskPaused(TaskPausedEvent event) {
        events.add(new TrackedEvent(
                EventType.TASK_PAUSED,
                event.getTaskId(),
                TaskStatus.PAUSED,
                null
        ));
    }

    @EventListener
    public void onTaskResumed(TaskResumedEvent event) {
        events.add(new TrackedEvent(
                EventType.TASK_RESUMED,
                event.getTaskId(),
                TaskStatus.RUNNING,
                null
        ));
    }

    @EventListener
    public void onStageStarted(TaskStageStartedEvent event) {
        events.add(new TrackedEvent(
                EventType.STAGE_STARTED,
                event.getTaskId(),
                null,
                event.getStageName()
        ));
    }

    @EventListener
    public void onStageCompleted(TaskStageCompletedEvent event) {
        events.add(new TrackedEvent(
                EventType.STAGE_COMPLETED,
                event.getTaskId(),
                null,
                event.getStageName()
        ));
    }

    @EventListener
    public void onStageFailed(TaskStageFailedEvent event) {
        events.add(new TrackedEvent(
                EventType.STAGE_FAILED,
                event.getTaskId(),
                null,
                event.getStageName()
        ));
    }

    @EventListener
    public void onTaskRolledBack(TaskRolledBackEvent event) {
        events.add(TrackedEvent.of(event));
    }

    // 查询方法
    public List<TrackedEvent> getEvents() {
        return new ArrayList<>(events);
    }

    public List<TaskStatus> getTaskStatusHistory(TaskId taskId) {
        return events.stream()
                .filter(e -> e.taskId.equals(taskId) && e.status != null)
                .map(e -> e.status)
                .toList();
    }

    public List<String> getExecutedStages(TaskId taskId) {
        return events.stream()
                .filter(e -> e.taskId.equals(taskId)
                        && (e.type == EventType.STAGE_STARTED || e.type == EventType.STAGE_COMPLETED))
                .map(e -> e.stageName)
                .toList();
    }

    /**
     * 获取特定类型的事件
     */
    public List<TrackedEvent> getEventsOfType(TaskId taskId, EventType type) {
        return events.stream()
                .filter(e -> e.taskId.equals(taskId) && e.type == type)
                .toList();
    }

    public void clear() {
        events.clear();
    }

    // 事件数据类
    public static class TrackedEvent {
        public final EventType type;
        public final TaskId taskId;
        public final TaskStatus status;
        public final String stageName;
        public final LocalDateTime timestamp;
        public final FailureInfo failureInfo;

        public static TrackedEvent of(TaskStartedEvent event) {
            return new TrackedEvent(
                    EventType.TASK_STARTED,
                    event.getTaskId(),
                    event.getStatus());
        }

        public static TrackedEvent of(TaskCompletedEvent event) {
            return new TrackedEvent(
                    EventType.TASK_COMPLETED,
                    event.getTaskId(),
                    event.getStatus());
        }

        public static TrackedEvent of(TaskFailedEvent event) {
            return new TrackedEvent(
                    EventType.TASK_FAILED,
                    event.getTaskId(),
                    TaskStatus.FAILED,
                    null,
                    event.getFailureInfo());
        }

        public static TrackedEvent of(TaskPausedEvent event) {
            return new TrackedEvent(
                    EventType.TASK_PAUSED,
                    event.getTaskId(),
                    TaskStatus.PAUSED);
        }

        public  static TrackedEvent of(TaskResumedEvent event) {
            return new TrackedEvent(
                    EventType.TASK_RESUMED,
                    event.getTaskId(),
                    event.getStatus());
        }

        public static TrackedEvent of(TaskRolledBackEvent event) {
            return new TrackedEvent(
                    EventType.TASK_COMPLETED,
                    event.getTaskId(),
                    TaskStatus.COMPLETED
            );
        }

        public TrackedEvent(EventType type,
                            TaskId taskId,
                            TaskStatus status) {
            this(type, taskId, status, null);
        }

        public TrackedEvent(EventType type,
                            TaskId taskId,
                            TaskStatus status,
                            String stageName) {
            this(type, taskId, status, stageName, null);
        }

        public TrackedEvent(EventType type,
                            TaskId taskId,
                            TaskStatus status,
                            String stageName,
                            FailureInfo failureInfo) {
            this.type = type;
            this.taskId = taskId;
            this.status = status;
            this.stageName = stageName;
            this.timestamp = LocalDateTime.now();
            this.failureInfo = failureInfo;
        }
    }

    public enum EventType {
        TASK_STARTED,
        TASK_COMPLETED,
        TASK_FAILED,
        TASK_PAUSED,
        TASK_RESUMED,
        STAGE_STARTED,
        STAGE_COMPLETED,
        STAGE_FAILED
    }
}
