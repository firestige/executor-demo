package xyz.firestige.redis.renewal.strategy.stop;

import xyz.firestige.redis.renewal.Named;
import xyz.firestige.redis.renewal.RenewalContext;

/**
 * 停止策略接口
 */
@FunctionalInterface
public interface StopStrategy extends Named {
    /**
     * 决定是否停止续期
     *
     * @param context 续期上下文
     * @return 是否要停止
     */
    boolean shouldStop(RenewalContext context);
}
