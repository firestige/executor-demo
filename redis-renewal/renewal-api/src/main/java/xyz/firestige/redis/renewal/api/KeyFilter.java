package xyz.firestige.redis.renewal.api;

@FunctionalInterface
public interface KeyFilter {
    boolean shouldRenew(String key, RenewalContext context);
}
