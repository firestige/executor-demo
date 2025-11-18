package xyz.firestige.deploy.domain.stage;

import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.config.ExecutorProperties;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.service.health.HealthCheckClient;

import java.util.List;

/**
 * Factory for building ordered stages per task (FIFO). No concurrency inside stage.
 */
public interface StageFactory {
    List<TaskStage> buildStages(TaskAggregate task,
                                TenantConfig cfg,
                                ExecutorProperties props,
                                HealthCheckClient healthClient);
}

