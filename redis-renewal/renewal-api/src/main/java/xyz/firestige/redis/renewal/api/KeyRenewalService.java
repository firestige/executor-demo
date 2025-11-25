package xyz.firestige.redis.renewal.api;

import java.util.Collection;

/**
 * Redis Key 续期服务接口
 *
 * <p>提供 Redis Key 的自动续期能力，支持灵活的续期策略和停止条件。
 *
 * <h3>核心功能</h3>
 * <ul>
 *   <li>注册续期任务 - 根据配置自动维护 Key 的 TTL</li>
 *   <li>任务管理 - 支持取消、暂停、恢复操作</li>
 *   <li>状态查询 - 查询任务状态和所有活跃任务</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * RenewalTask task = RenewalTask.builder()
 *     .keys(List.of("deployment:tenant1:config"))
 *     .ttlStrategy(new FixedTtlStrategy(Duration.ofMinutes(5)))
 *     .intervalStrategy(new FixedIntervalStrategy(Duration.ofMinutes(2)))
 *     .stopCondition(new TimeBasedStopCondition(endTime))
 *     .build();
 *
 * String taskId = renewalService.register(task);
 * // ... 业务逻辑
 * renewalService.cancel(taskId);
 * }</pre>
 *
 * @author T-018
 * @since 1.0.0
 * @see RenewalTask
 * @see RenewalStrategy
 */
public interface KeyRenewalService {

    /**
     * 注册续期任务
     *
     * <p>任务注册后立即开始执行续期，按照配置的间隔策略循环续期，
     * 直到满足停止条件或手动取消。
     *
     * @param task 续期任务配置
     * @return 任务 ID（唯一标识符）
     * @throws IllegalArgumentException 如果任务配置无效
     * @throws IllegalStateException 如果服务已关闭
     */
    String register(RenewalTask task);

    /**
     * 取消续期任务
     *
     * <p>取消后任务立即停止，不再执行续期操作。
     * 如果任务不存在或已完成，此操作无影响。
     *
     * @param taskId 任务 ID
     */
    void cancel(String taskId);

    /**
     * 暂停续期任务
     *
     * <p>暂停后任务停止续期，但保留任务状态。
     * 可以通过 {@link #resume(String)} 恢复。
     *
     * @param taskId 任务 ID
     * @throws IllegalArgumentException 如果任务不存在
     */
    void pause(String taskId);

    /**
     * 恢复续期任务
     *
     * <p>恢复已暂停的任务，立即执行一次续期并继续后续调度。
     *
     * @param taskId 任务 ID
     * @throws IllegalArgumentException 如果任务不存在
     * @throws IllegalStateException 如果任务未暂停
     */
    void resume(String taskId);

    /**
     * 获取任务状态
     *
     * @param taskId 任务 ID
     * @return 任务状态，如果任务不存在返回 {@code null}
     */
    RenewalTaskStatus getStatus(String taskId);

    /**
     * 获取所有活跃任务
     *
     * @return 活跃任务列表（不可修改）
     */
    Collection<RenewalTask> getAllTasks();
}

