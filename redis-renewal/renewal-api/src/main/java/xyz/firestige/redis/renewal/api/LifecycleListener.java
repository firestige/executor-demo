package xyz.firestige.redis.renewal.api;

public interface LifecycleListener {
    default void onTaskStarted(String taskId) {}
    default void onTaskPaused(String taskId) {}
    default void onTaskResumed(String taskId) {}
    default void onTaskCompleted(String taskId) {}
    default void onTaskCancelled(String taskId) {}
    default void onRenewalSuccess(String taskId, String key) {}
    default void onRenewalFailure(String taskId, String key, Throwable error) {}
}
