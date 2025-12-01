package xyz.firestige.deploy.infrastructure.execution.stage;

import xyz.firestige.deploy.application.dto.TenantConfig;

import java.util.List;

/**
 * Factory for building ordered stages per task (FIFO). No concurrency inside stage.
 * 
 * T-035: 添加 calculateStartIndex 方法用于无状态恢复
 */
public interface StageFactory {
    /**
     * 构建 Stage 列表
     * 
     * @param cfg 租户配置
     * @return Stage 列表（顺序固定，幂等）
     */
    List<TaskStage> buildStages(TenantConfig cfg);
    
    /**
     * T-035: 根据最后完成的 Stage 名称计算其索引
     * <p>
     * 用于无状态恢复场景：
     * - Retry: startIndex = calculateStartIndex(lastCompletedStageName) + 1
     * - Rollback: endIndex = calculateStartIndex(lastCompletedStageName) + 1
     * <p>
     * 要求：buildStages() 必须是幂等的（相同输入 → 相同顺序）
     *
     * @param cfg 租户配置（用于重建 Stage 列表）
     * @param lastCompletedStageName 最后完成的 Stage 名称
     * @return lastCompletedStage 的索引（从 0 开始）
     * @throws IllegalArgumentException 如果找不到对应的 Stage
     */
    int calculateStartIndex(TenantConfig cfg, String lastCompletedStageName);
}


