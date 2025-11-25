package xyz.firestige.infrastructure.redis.renewal.examples;

import xyz.firestige.infrastructure.redis.renewal.api.*;
import xyz.firestige.infrastructure.redis.renewal.condition.TimeBasedStopCondition;
import xyz.firestige.infrastructure.redis.renewal.core.AsyncRenewalExecutor;
import xyz.firestige.infrastructure.redis.renewal.core.TimeWheelRenewalService;
import xyz.firestige.infrastructure.redis.renewal.interval.AdaptiveIntervalStrategy;
import xyz.firestige.infrastructure.redis.renewal.selector.FunctionKeySelector;
import xyz.firestige.infrastructure.redis.renewal.strategy.FixedTtlStrategy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Redis 续期服务使用示例
 */
public class RenewalServiceExamples {

    /**
     * 示例 1：简单部署任务续期
     * <p>为部署任务 Key 设置固定 TTL 和续期间隔
     */
    public static void simpleDeploymentRenewal(KeyRenewalService renewalService) {
        // 使用模板方法快速创建
        RenewalTask task = RenewalTask.fixedRenewal(
            List.of("deployment:tenant1:task123"),
            Duration.ofMinutes(5),
            Duration.ofMinutes(2)
        );

        String taskId = renewalService.register(task);
        System.out.println("注册续期任务: " + taskId);

        // 稍后取消
        // renewalService.cancel(taskId);
    }

    /**
     * 示例 2：续期至指定时间
     * <p>部署任务直到指定时间结束
     */
    public static void renewalUntilEndTime(KeyRenewalService renewalService) {
        Instant deploymentEndTime = Instant.now().plusSeconds(3600); // 1小时后

        RenewalTask task = RenewalTask.untilTime(
            List.of("deployment:tenant2:task456"),
            Duration.ofMinutes(10),
            deploymentEndTime
        );

        String taskId = renewalService.register(task);
        System.out.println("续期至 " + deploymentEndTime + ", taskId: " + taskId);
    }

    /**
     * 示例 3：动态 Key + 自适应间隔
     * <p>从业务服务动态获取需要续期的 Key，使用自适应间隔
     */
    public static void dynamicKeysWithAdaptiveInterval(
        KeyRenewalService renewalService,
        DeploymentService deploymentService
    ) {
        RenewalTask task = RenewalTask.builder()
            .keySelector(new FunctionKeySelector(ctx ->
                deploymentService.getActiveDeploymentKeys()
            ))
            .ttlStrategy(new FixedTtlStrategy(Duration.ofMinutes(5)))
            .intervalStrategy(new AdaptiveIntervalStrategy(0.5)) // TTL 的 50% 时续期
            .stopCondition(new TimeBasedStopCondition(Instant.now().plus(Duration.ofHours(2))))
            .build();

        String taskId = renewalService.register(task);
        System.out.println("动态续期任务: " + taskId);
    }

    /**
     * 示例 4：完整功能组合
     * <p>使用生命周期监听器、失败处理、过滤器等高级功能
     */
    public static void fullFeaturedRenewal(KeyRenewalService renewalService) {
        RenewalTask task = RenewalTask.builder()
            .keys(List.of("critical:key1", "critical:key2"))
            .ttlStrategy(new FixedTtlStrategy(Duration.ofMinutes(10)))
            .intervalStrategy(new AdaptiveIntervalStrategy(0.4))
            .stopCondition(new TimeBasedStopCondition(Instant.now().plus(Duration.ofHours(1))))
            .listener(new RenewalLifecycleListener() {
                @Override
                public void onTaskRegistered(String taskId, RenewalTask t) {
                    System.out.println("任务注册: " + taskId);
                }
                @Override
                public void beforeRenewal(String taskId, java.util.Collection<String> keys) {
                    System.out.println("开始续期: " + keys.size() + " keys");
                }
                @Override
                public void afterRenewal(String taskId, RenewalResult result) {
                    System.out.println("续期完成: 成功=" + result.getSuccessCount() +
                                     ", 失败=" + result.getFailureCount());
                }
                @Override
                public void onTaskCompleted(String taskId, CompletionReason reason) {
                    System.out.println("任务完成: " + reason);
                }
                @Override
                public void onTaskFailed(String taskId, Throwable error) {
                    System.err.println("任务失败: " + error.getMessage());
                }
            })
            .maxRetries(3)
            .build();

        String taskId = renewalService.register(task);

        // 暂停和恢复
        renewalService.pause(taskId);
        System.out.println("任务已暂停");

        // 稍后恢复
        renewalService.resume(taskId);
        System.out.println("任务已恢复");
    }

    /**
     * 示例 5：创建续期服务实例
     */
    public static KeyRenewalService createRenewalService(RedisClient redisClient) {
        AsyncRenewalExecutor executor = new AsyncRenewalExecutor(
            redisClient,
            4,    // 线程池大小
            1000  // 队列容量
        );

        return new TimeWheelRenewalService(
            executor,
            100,  // tick 间隔 100ms
            512   // 时间轮槽数
        );
    }

    /**
     * 模拟业务服务接口
     */
    interface DeploymentService {
        List<String> getActiveDeploymentKeys();
    }
}

