/**
 * Redis Key 续期服务核心 API
 *
 * <p>提供 Redis Key 自动续期的核心接口和数据模型。
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link xyz.firestige.infrastructure.redis.renewal.api.KeyRenewalService} - 续期服务主接口</li>
 *   <li>{@link xyz.firestige.infrastructure.redis.renewal.api.RenewalTask} - 续期任务模型</li>
 *   <li>{@link xyz.firestige.infrastructure.redis.renewal.api.RenewalContext} - 续期上下文</li>
 * </ul>
 *
 * <h2>扩展点（高频）</h2>
 * <ul>
 *   <li>{@link xyz.firestige.infrastructure.redis.renewal.api.RenewalStrategy} - TTL 策略</li>
 *   <li>{@link xyz.firestige.infrastructure.redis.renewal.api.RenewalIntervalStrategy} - 间隔策略</li>
 *   <li>{@link xyz.firestige.infrastructure.redis.renewal.api.KeySelector} - Key 选择器</li>
 *   <li>{@link xyz.firestige.infrastructure.redis.renewal.api.StopCondition} - 停止条件</li>
 * </ul>
 *
 * <h2>扩展点（中低频）</h2>
 * <ul>
 *   <li>{@link xyz.firestige.infrastructure.redis.renewal.api.RedisClient} - Redis 客户端抽象</li>
 *   <li>{@link xyz.firestige.infrastructure.redis.renewal.api.FailureHandler} - 失败处理器</li>
 *   <li>{@link xyz.firestige.infrastructure.redis.renewal.api.RenewalLifecycleListener} - 生命周期监听器</li>
 *   <li>{@link xyz.firestige.infrastructure.redis.renewal.api.RenewalFilter} - 续期过滤器</li>
 *   <li>{@link xyz.firestige.infrastructure.redis.renewal.api.BatchStrategy} - 批量策略</li>
 *   <li>{@link xyz.firestige.infrastructure.redis.renewal.api.KeyGenerationStrategy} - Key 生成策略</li>
 * </ul>
 *
 * <h2>快速开始</h2>
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
 */
package xyz.firestige.infrastructure.redis.renewal.api;

