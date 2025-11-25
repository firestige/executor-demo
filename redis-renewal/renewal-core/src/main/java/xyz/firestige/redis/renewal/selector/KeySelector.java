package xyz.firestige.redis.renewal.selector;

import xyz.firestige.redis.renewal.Named;
import xyz.firestige.redis.renewal.RenewalContext;

import java.util.Collection;

/**
 * 键选择器接口
 */
@FunctionalInterface
public interface KeySelector extends Named {
    Collection<String> selectKeys(RenewalContext context);
}
