package xyz.firestige.deploy.domain.stage;

import xyz.firestige.deploy.application.dto.TenantConfig;

import java.util.List;

/**
 * Factory for building ordered stages per task (FIFO). No concurrency inside stage.
 */
public interface StageFactory {
    List<TaskStage> buildStages(TenantConfig cfg);
}

