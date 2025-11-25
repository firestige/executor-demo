package xyz.firestige.redis.ack.spring.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import xyz.firestige.redis.ack.api.AckMetricsRecorder;
import xyz.firestige.redis.ack.api.AckResult;

/**
 * 基于 Micrometer 的 ACK 指标记录器
 * <p>
 * 记录以下指标：
 * - redis_ack_executions: 总执行次数
 * - redis_ack_success: 成功次数
 * - redis_ack_mismatch: Footprint 不匹配次数
 * - redis_ack_timeout: 超时次数
 * - redis_ack_error: 错误次数
 * - redis_ack_duration: 执行耗时分布
 *
 * @author AI
 * @since 1.0
 */
public class MicrometerAckMetricsRecorder implements AckMetricsRecorder {

    private final Counter executions;
    private final Counter success;
    private final Counter mismatch;
    private final Counter timeout;
    private final Counter error;
    private final Timer executionTimer;

    public MicrometerAckMetricsRecorder(MeterRegistry registry) {
        this.executions = Counter.builder("redis_ack_executions")
            .description("Total ACK executions")
            .register(registry);
        this.success = Counter.builder("redis_ack_success")
            .description("Successful ACK verifications")
            .register(registry);
        this.mismatch = Counter.builder("redis_ack_mismatch")
            .description("Footprint mismatches")
            .register(registry);
        this.timeout = Counter.builder("redis_ack_timeout")
            .description("Timeout occurrences")
            .register(registry);
        this.error = Counter.builder("redis_ack_error")
            .description("Errors during ACK execution")
            .register(registry);
        this.executionTimer = Timer.builder("redis_ack_duration")
            .description("ACK execution duration")
            .publishPercentileHistogram()
            .register(registry);
    }

    @Override
    public void record(AckResult result) {
        executions.increment();
        if (result.isSuccess()) {
            success.increment();
        } else if (result.isFootprintMismatch()) {
            mismatch.increment();
        } else if (result.isTimeout()) {
            timeout.increment();
        } else if (result.isError()) {
            error.increment();
        }
        executionTimer.record(result.getElapsed());
    }
}

