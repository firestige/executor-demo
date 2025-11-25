package xyz.firestige.redis.renewal.api;

import java.util.List;

@FunctionalInterface
public interface KeySelector {
    List<String> selectKeys(RenewalContext context);
}
