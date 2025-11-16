package xyz.firestige.executor.domain.stage;

import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.config.ExecutorProperties;
import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.service.health.HealthCheckClient;

import java.util.List;

/**
 * Factory for building ordered stages per task (FIFO). No concurrency inside stage.
 */
public interface StageFactory {
    List<TaskStage> buildStages(TaskAggregate task,
                                TenantDeployConfig cfg,
                                ExecutorProperties props,
                                HealthCheckClient healthClient);
}

