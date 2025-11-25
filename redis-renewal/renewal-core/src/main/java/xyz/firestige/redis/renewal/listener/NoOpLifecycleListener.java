package xyz.firestige.infrastructure.redis.renewal.listener;

import xyz.firestige.infrastructure.redis.renewal.api.RenewalLifecycleListener;
import xyz.firestige.infrastructure.redis.renewal.api.RenewalResult;
import xyz.firestige.infrastructure.redis.renewal.api.RenewalTask;

import java.util.Collection;

/**
 * 空操作生命周期监听器（默认实现）
 * <p>不执行任何操作
 */
public class NoOpLifecycleListener implements RenewalLifecycleListener {

    @Override
    public void onTaskRegistered(String taskId, RenewalTask task) {
        // no-op
    }

    @Override
    public void beforeRenewal(String taskId, Collection<String> keys) {
        // no-op
    }

    @Override
    public void afterRenewal(String taskId, RenewalResult result) {
        // no-op
    }

    @Override
    public void onTaskCompleted(String taskId, CompletionReason reason) {
        // no-op
    }

    @Override
    public void onTaskFailed(String taskId, Throwable error) {
        // no-op
    }
}

