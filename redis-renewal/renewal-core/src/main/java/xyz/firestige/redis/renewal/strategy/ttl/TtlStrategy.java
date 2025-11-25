package xyz.firestige.redis.renewal.strategy.ttl;

import xyz.firestige.redis.renewal.Named;
import xyz.firestige.redis.renewal.RenewalContext;

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
public interface TtlStrategy extends Named {

    /**
     * 计算下一次续期的 TTL
     *
     * @param context 续期上下文
     * @return TTL 时长
     */
    Duration nextTtl(RenewalContext context);
}

