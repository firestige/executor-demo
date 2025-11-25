package xyz.firestige.redis.renewal.strategy.batch;

import xyz.firestige.redis.renewal.Named;

import java.util.Collection;
import java.util.List;

/**
 * 批量策略接口
 *
 * <p>决定如何将 Key 分批进行续期。
 *
 * <h3>预置实现</h3>
 * <ul>
 *   <li>{@code FixedSizeBatchStrategy} - 固定批次大小（默认 100）</li>
 * </ul>
 *
 * @author T-018
 * @since 1.0.0
 */
@FunctionalInterface
public interface BatchStrategy extends Named {

    /**
     * 将 Key 分批
     *
     * @param keys 所有 Key
     * @return 批次列表，每个批次包含一组 Key
     */
    List<List<String>> batch(Collection<String> keys);
}

