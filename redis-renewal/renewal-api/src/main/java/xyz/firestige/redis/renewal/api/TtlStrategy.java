package xyz.firestige.redis.renewal.api;

import java.time.Duration;

/**
 * TTL 策略接口
 * <p>
 * 决定每次续期时设置的 TTL 值。
 *
 * @author AI
 * @since 1.0
 */
@FunctionalInterface
public interface TtlStrategy {

    /**
     * 计算下一次续期的 TTL
     *
     * @param context 续期上下文
     * @return TTL 时长
     */
    Duration nextTtl(RenewalContext context);

    /**
     * 固定 TTL 策略
     */
    static TtlStrategy fixed(Duration ttl) {
        return ctx -> ttl;
    }

    /**
     * 动态 TTL 策略（基于剩余时间比例）
     */
    static TtlStrategy dynamic(Duration baseTtl, double factor) {
        return ctx -> Duration.ofMillis((long)(baseTtl.toMillis() * factor));
    }
}

