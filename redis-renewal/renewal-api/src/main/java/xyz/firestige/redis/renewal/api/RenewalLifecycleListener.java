package xyz.firestige.redis.renewal.api;

/**
 * 续期生命周期监听器接口
 *
 * <p>监听续期任务的生命周期事件，用于监控、日志、事件发布等。
 *
 * <h3>预置实现</h3>
 * <ul>
 *   <li>{@code NoOpLifecycleListener} - 空实现（默认）</li>
 * </ul>
 *
 * @author T-018
 * @since 1.0.0
 */
public interface RenewalLifecycleListener {

    /**
     * 任务注册时触发
     */
    void onTaskRegistered(String taskId, RenewalTask task);

    /**
     * 续期执行前触发
     */
    void beforeRenewal(String taskId, java.util.Collection<String> keys);

    /**
     * 续期执行后触发
     */
    void afterRenewal(String taskId, RenewalResult result);

    /**
     * 任务完成时触发
     */
    void onTaskCompleted(String taskId, CompletionReason reason);

    /**
     * 任务失败时触发
     */
    void onTaskFailed(String taskId, Throwable error);

    /**
     * 任务完成原因
     */
    enum CompletionReason {
        STOP_CONDITION_MET,  // 停止条件满足
        CANCELLED,           // 手动取消
        ERROR                // 错误导致停止
    }
}

