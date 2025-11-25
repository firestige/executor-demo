package xyz.firestige.deploy.infrastructure.discovery;

/**
 * 实例选择策略
 *
 * @since T-025
 */
public enum SelectionStrategy {
    /**
     * 全部实例（用于并发健康检查、并发通知）
     * 使用场景：BlueGreen, ObService
     */
    ALL,

    /**
     * 随机选择一个实例（简单负载均衡）
     * 使用场景：Portal, ASBC
     */
    RANDOM,

    /**
     * 轮询选择一个实例（有状态负载均衡）
     * 使用场景：未来扩展
     */
    ROUND_ROBIN
}

