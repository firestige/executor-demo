package xyz.firestige.executor.domain.stage.rollback;

import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskRuntimeContext;

/**
 * Stage 回滚策略：重发上一版可用配置并确认健康。
 */
public interface RollbackStrategy {
    void rollback(TaskAggregate task, TaskRuntimeContext context) throws Exception;
}
