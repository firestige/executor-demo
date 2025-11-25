package xyz.firestige.redis.renewal.api;

@FunctionalInterface
public interface FailureHandler {
    void handleFailure(String key, Throwable error, RenewalContext context);

    static FailureHandler logAndContinue() {
        return (key, error, ctx) ->
            System.err.println("Renewal failed for key: " + key + ", error: " + error.getMessage());
    }
}
