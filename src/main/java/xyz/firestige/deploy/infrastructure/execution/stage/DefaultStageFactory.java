package xyz.firestige.deploy.infrastructure.execution.stage;

import xyz.firestige.deploy.application.dto.TenantConfig;

import java.util.List;

/**
 * @deprecated 已被 {@link DynamicStageFactory} 替代，保留仅为向后兼容
 * 请使用新的配置驱动的动态 Stage Factory 框架
 */
@Deprecated(since = "2025-11-19", forRemoval = true)
public class DefaultStageFactory implements StageFactory {
    @Override
    public List<TaskStage> buildStages(TenantConfig cfg) {
        // 已废弃：此实现已被 DynamicStageFactory 替代
        // 返回空列表避免编译错误
        throw new UnsupportedOperationException(
            "DefaultStageFactory is deprecated. Please use DynamicStageFactory instead.");
    }
}

