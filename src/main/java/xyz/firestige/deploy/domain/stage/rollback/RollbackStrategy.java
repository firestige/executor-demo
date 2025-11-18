package xyz.firestige.deploy.domain.stage.rollback;

import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;

/**
 * Stage 回滚策略：重发上一版可用配置并确认健康。
 */
public interface RollbackStrategy {
    void rollback(TaskAggregate task, TaskRuntimeContext context) throws Exception;
}
