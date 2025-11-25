package xyz.firestige.redis.ack.api;

/**
 * ACK 指标记录器接口
 * <p>
 * 允许实现自定义的指标收集逻辑，例如集成 Micrometer、Prometheus 或其他监控系统
 *
 * @author AI
 * @since 1.0
 */
@FunctionalInterface
public interface AckMetricsRecorder {

    /**
     * 记录一次 ACK 执行结果
     *
     * @param result ACK 执行结果
     */
    void record(AckResult result);

    /**
     * 空操作实现（默认）
     *
     * @return 不执行任何操作的记录器
     */
    static AckMetricsRecorder noop() {
        return result -> {};
    }
}

