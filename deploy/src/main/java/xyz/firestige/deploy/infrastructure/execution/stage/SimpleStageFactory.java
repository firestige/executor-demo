package xyz.firestige.deploy.infrastructure.execution.stage;

import xyz.firestige.deploy.application.dto.TenantConfig;
import java.util.List;
import java.util.Collections;

/**
 * Simple fallback StageFactory returning empty stage list.
 * Used to allow compilation when full orchestrated factory dependencies are absent.
 */
public class SimpleStageFactory implements StageFactory {
    @Override
    public List<TaskStage> buildStages(TenantConfig cfg) {
        return Collections.emptyList();
    }
}

