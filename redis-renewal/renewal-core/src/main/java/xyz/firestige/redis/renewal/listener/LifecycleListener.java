package xyz.firestige.redis.renewal.listener;

import xyz.firestige.redis.renewal.RenewalTask;
import xyz.firestige.redis.renewal.RenewalResult;

/**
 * 续期生命周期监听器接口
 *
 * <p>监听续期任务的生命周期事件，用于监控、日志、事件发布等。
 *
 * @author T-018
 * @since 1.0.0
 */
public interface LifecycleListener {

    /**
     * 任务注册时触发
     */
    default void onTaskRegistered(String taskId, RenewalTask task) {}

    /**
     * 续期执行前触发
     */
    default void beforeRenewal(String taskId, java.util.Collection<String> keys) {}

    /**
     * 续期执行后触发
     */
    default void afterRenewal(String taskId, RenewalResult result) {}

    /**
     * 任务完成时触发
     */
    default void onTaskCompleted(String taskId, CompletionReason reason) {}

    /**
     * 任务失败时触发
     */
    default void onTaskFailed(String taskId, Throwable error) {}

    /**
     * 任务完成原因
     */
    enum CompletionReason {
        STOP_CONDITION_MET,  // 停止条件满足
        CANCELLED,           // 手动取消
        ERROR                // 错误导致停止
    }
}

