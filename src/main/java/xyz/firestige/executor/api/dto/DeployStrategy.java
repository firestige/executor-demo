package xyz.firestige.executor.api.dto;

/**
 * 配置下发策略
 * 定义租户维度配置下发的执行方式
 */
public enum DeployStrategy {
    /**
     * 并发下发 - 多个租户同时下发配置
     */
    CONCURRENT,
    
    /**
     * 顺序下发 - 租户按顺序逐个下发配置
     */
    SEQUENTIAL
}
