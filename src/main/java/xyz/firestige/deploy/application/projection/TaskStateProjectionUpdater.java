package xyz.firestige.deploy.application.projection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import xyz.firestige.deploy.domain.task.TaskStatus;
import xyz.firestige.deploy.domain.task.event.*;
import xyz.firestige.deploy.infrastructure.persistence.projection.TaskStateProjection;
import xyz.firestige.deploy.infrastructure.persistence.projection.TaskStateProjectionStore;
import xyz.firestige.deploy.infrastructure.persistence.projection.TenantTaskIndexStore;

/**
 * Task 状态投影更新器（事件监听器）
 * 职责：监听任务领域事件并更新查询侧投影（CQRS）
 */
@Component
public class TaskStateProjectionUpdater {
    private static final Logger logger = LoggerFactory.getLogger(TaskStateProjectionUpdater.class);
    private final TaskStateProjectionStore projectionStore;
    private final TenantTaskIndexStore indexStore;

    public TaskStateProjectionUpdater(TaskStateProjectionStore projectionStore,
                                      TenantTaskIndexStore indexStore) {
        this.projectionStore = projectionStore;
        this.indexStore = indexStore;
    }

    @EventListener
    public void onTaskCreated(TaskCreatedEvent event) {
        logger.debug("[投影] TaskCreated: taskId={}, tenantId={}", event.getTaskId(), event.getTenantId());
        TaskStateProjection projection = TaskStateProjection.builder()
                .taskId(event.getTaskId())
                .tenantId(event.getTenantId())
                .planId(event.getPlanId())
                .status(TaskStatus.PENDING)
                .pauseRequested(false)
                .stageNames(event.getStageNames())
                .lastCompletedStageIndex(-1)
                .build();
        projectionStore.save(projection);
        indexStore.put(event.getTenantId(), event.getTaskId());
        logger.info("[投影] TaskCreated persisted: taskId={}, stages={}", event.getTaskId(), event.getStageNames().size());
    }

    @EventListener
    public void onTaskStarted(TaskStartedEvent event) {
        updateStatus(event.getTaskId(), TaskStatus.RUNNING);
        logger.debug("[投影更新] Task启动: taskId={}", event.getTaskId());
    }

    @EventListener
    public void onTaskPaused(TaskPausedEvent event) {
        updatePause(event.getTaskId(), true);
        logger.debug("[投影更新] Task暂停: taskId={}", event.getTaskId());
    }

    @EventListener
    public void onTaskResumed(TaskResumedEvent event) {
        updatePause(event.getTaskId(), false);
        logger.debug("[投影更新] Task恢复: taskId={}", event.getTaskId());
    }

    @EventListener
    public void onTaskCompleted(TaskCompletedEvent event) {
        updateStatus(event.getTaskId(), TaskStatus.COMPLETED);
        logger.debug("[投影更新] Task完成: taskId={}", event.getTaskId());
    }

    @EventListener
    public void onTaskFailed(TaskFailedEvent event) {
        updateStatus(event.getTaskId(), TaskStatus.FAILED);
        logger.debug("[投影更新] Task失败: taskId={}", event.getTaskId());
    }

    @EventListener
    public void onTaskRollingBack(TaskRollingBackEvent event) {
        updateStatus(event.getTaskId(), TaskStatus.ROLLING_BACK);
        logger.debug("[投影更新] Task回滚中: taskId={}", event.getTaskId());
    }

    @EventListener
    public void onTaskRolledBack(TaskRolledBackEvent event) {
        updateStatus(event.getTaskId(), TaskStatus.ROLLED_BACK);
        logger.debug("[投影更新] Task回滚完成: taskId={}", event.getTaskId());
    }

    @EventListener
    public void onTaskRollbackFailed(TaskRollbackFailedEvent event) {
        updateStatus(event.getTaskId(), TaskStatus.ROLLBACK_FAILED);
        logger.debug("[投影更新] Task回滚失败: taskId={}", event.getTaskId());
    }

    @EventListener
    public void onStageCompleted(TaskStageCompletedEvent event) {
        TaskStateProjection p = projectionStore.load(event.getTaskId());
        if (p != null) {
            int idx = p.getStageNames().indexOf(event.getStageName());
            if (idx >= 0 && idx > p.getLastCompletedStageIndex()) {
                p.setLastCompletedStageIndex(idx);
                projectionStore.save(p);
            }
        }
    }

    private void updateStatus(xyz.firestige.deploy.domain.shared.vo.TaskId taskId, TaskStatus status) {
        TaskStateProjection p = projectionStore.load(taskId);
        if (p != null) {
            p.setStatus(status);
            projectionStore.save(p);
        }
    }

    private void updatePause(xyz.firestige.deploy.domain.shared.vo.TaskId taskId, boolean paused) {
        TaskStateProjection p = projectionStore.load(taskId);
        if (p != null) {
            p.setStatus(paused ? TaskStatus.PAUSED : TaskStatus.RUNNING);
            p.setPauseRequested(paused);
            projectionStore.save(p);
        }
    }
}
