package xyz.firestige.deploy.orchestration.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.firestige.deploy.support.conflict.ConflictRegistry;

/**
 * 调度策略配置类（RF-12）
 *
 * 支持两种策略：
 * 1. 细粒度策略（FINE_GRAINED）：默认，创建时不检查冲突，启动时跳过冲突任务
 * 2. 粗粒度策略（COARSE_GRAINED）：创建时检查冲突，有任何重叠租户则立即拒绝
 *
 * @since Phase 18 - RF-12
 */
@Configuration
public class SchedulingStrategyConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SchedulingStrategyConfiguration.class);

    @Bean
    @ConditionalOnProperty(
        name = "executor.scheduling.strategy",
        havingValue = "FINE_GRAINED",
        matchIfMissing = true  // 默认细粒度
    )
    public PlanSchedulingStrategy fineGrainedStrategy(ConflictRegistry conflictRegistry) {
        log.info("启用细粒度调度策略（Fine-Grained）");
        return new FineGrainedSchedulingStrategy(conflictRegistry);
    }

    @Bean
    @ConditionalOnProperty(
        name = "executor.scheduling.strategy",
        havingValue = "COARSE_GRAINED"
    )
    public PlanSchedulingStrategy coarseGrainedStrategy(ConflictRegistry conflictRegistry) {
        log.info("启用粗粒度调度策略（Coarse-Grained）");
        return new CoarseGrainedSchedulingStrategy(conflictRegistry);
    }
}

