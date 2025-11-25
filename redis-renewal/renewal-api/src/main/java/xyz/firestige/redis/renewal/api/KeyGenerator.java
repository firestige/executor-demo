package xyz.firestige.redis.renewal.api;

@FunctionalInterface
public interface KeyGenerator {
    String generateKey(RenewalContext context);
}
