# T-018: Redis 续期服务设计方案

> **任务 ID**: T-018  
> **优先级**: P1  
> **状态**: 设计中  
> **创建日期**: 2025-11-24

---

## 1. 需求分析

### 1.1 核心需求

1. **接口与实现分离**：
   - 定义通用的续期服务接口
   - 默认使用 Spring Data Redis 实现
   - 读取 Spring Boot 标准配置

2. **灵活的续期策略**：
   - 动态 TTL（根据条件调整过期时间）
   - 可配置续期间隔
   - 循环续期直到指定时间
   - 基于 KeyGenerator 批量续期

3. **高性能调度**：
   - 使用时间轮（Time Wheel）或定时任务
   - 单线程维护大量续期任务
   - 低资源消耗

4. **可观测性**：
   - 定时打印续期指标
   - 监控续期成功率、失败次数
   - 提供健康检查端点

### 1.2 设计目标

- ✅ 通用可复用（独立于 deploy 域）
- ✅ 低资源消耗（单线程，时间轮）
- ✅ 高可靠性（失败重试，异常隔离）
- ✅ 易于监控（指标暴露，日志记录）
- ✅ Spring Boot 原生集成

---

## 2. 架构设计

### 2.1 分层结构

```
xyz.firestige.infrastructure.redis.renewal
├── api/                          # 接口层（核心抽象，无依赖）
│   ├── KeyRenewalService         # 续期服务接口
│   ├── RenewalStrategy           # 续期策略接口
│   ├── KeyGenerator              # Key 生成器接口
│   ├── RedisClient               # Redis 客户端抽象 ⭐ 新增
│   └── RenewalMetrics            # 指标接口
├── core/                         # 核心实现（仅依赖 api + Netty）
│   ├── TimeWheelRenewalService   # 时间轮实现
│   ├── ScheduledRenewalService   # ScheduledExecutorService 实现
│   ├── AsyncRenewalExecutor      # 异步执行器 ⭐ 新增（解决 IO 阻塞）
│   └── RenewalTask               # 续期任务模型
├── strategy/                     # 策略实现（仅依赖 api）
│   ├── FixedTtlStrategy          # 固定 TTL 策略
│   ├── DynamicTtlStrategy        # 动态 TTL 策略
│   ├── UntilTimeStrategy         # 续期至指定时间策略
│   └── ConditionalStrategy       # 条件续期策略
├── generator/                    # Key 生成器（依赖 api）
│   ├── PatternKeyGenerator       # 模式匹配生成器
│   ├── PrefixKeyGenerator        # 前缀生成器
│   └── CompositeKeyGenerator     # 组合生成器
├── client/                       # Redis 客户端实现 ⭐ 新增
│   ├── spring/                   # Spring Data Redis 实现
│   │   ├── SpringRedisClient     # Spring 适配器
│   │   └── SpringRedisKeyScanner # Spring SCAN 实现
│   ├── jedis/                    # Jedis 实现（可选）
│   │   └── JedisRedisClient
│   └── lettuce/                  # Lettuce 实现（可选）
│       └── LettuceRedisClient
├── metrics/                      # 监控指标（仅依赖 api）
│   ├── RenewalMetricsCollector   # 指标收集器
│   └── RenewalHealthIndicator    # 健康检查
└── autoconfigure/                # 自动配置（依赖 Spring）
    ├── spring/
    │   ├── RedisRenewalAutoConfiguration
    │   └── RedisRenewalProperties
    └── spi/                      # SPI 加载（脱离 Spring 后使用）
        └── RedisClientLoader
```

### 2.2 依赖关系

```
api（核心接口）
 ↑
 ├─ core（核心实现，依赖 Netty）
 ├─ strategy（策略实现）
 ├─ generator（生成器）
 ├─ metrics（指标）
 └─ client（客户端实现）
     ├─ spring（依赖 Spring Data Redis）
     ├─ jedis（依赖 Jedis）
     └─ lettuce（依赖 Lettuce）

autoconfigure（自动配置，依赖 Spring Boot）
 └─ 组装 core + client/spring
```

**模块化优势**：
- 核心逻辑（api + core）无 Spring 依赖
- Spring 实现独立打包（可选依赖）
- 支持 SPI 加载其他实现
- 便于后续拆包和扩展

### 2.2 核心接口设计

#### KeyRenewalService

```java
public interface KeyRenewalService {
    /**
     * 注册续期任务
     * @param task 续期任务
     * @return 任务 ID
     */
    String register(RenewalTask task);
    
    /**
     * 取消续期任务
     * @param taskId 任务 ID
     */
    void cancel(String taskId);
    
    /**
     * 暂停续期任务
     * @param taskId 任务 ID
     */
    void pause(String taskId);
    
    /**
     * 恢复续期任务
     * @param taskId 任务 ID
     */
    void resume(String taskId);
    
    /**
     * 获取任务状态
     */
    RenewalTaskStatus getStatus(String taskId);
    
    /**
     * 获取所有任务
     */
    Collection<RenewalTask> getAllTasks();
}
```

#### RenewalStrategy

```java
public interface RenewalStrategy {
    /**
     * 计算下次续期的 TTL
     * @param context 续期上下文
     * @return TTL（秒）
     */
    long calculateTtl(RenewalContext context);
    
    /**
     * 是否应该继续续期
     */
    boolean shouldContinue(RenewalContext context);
    
    /**
     * 策略名称
     */
    String getStrategyName();
}
```

#### KeyGenerator

```java
public interface KeyGenerator {
    /**
     * 生成需要续期的 Key 列表
     */
    Collection<String> generateKeys();
    
    /**
     * 生成器名称
     */
    String getGeneratorName();
}
```

#### RedisClient（新增 - Redis 客户端抽象）

```java
/**
 * Redis 客户端抽象接口
 * 
 * <p>设计目标：
 * <ul>
 *   <li>抽象 Redis 操作，支持多种实现</li>
 *   <li>核心接口不依赖具体 Redis 客户端库</li>
 *   <li>支持同步和异步操作</li>
 * </ul>
 */
public interface RedisClient {
    
    /**
     * 为 Key 设置过期时间
     * 
     * @param key Redis Key
     * @param ttl 过期时间（秒）
     * @return true 如果设置成功，false 如果 Key 不存在
     */
    boolean expire(String key, long ttl);
    
    /**
     * 批量为 Key 设置过期时间（同步）
     * 
     * @param keys Redis Key 列表
     * @param ttl 过期时间（秒）
     * @return 续期结果（Key -> 是否成功）
     */
    Map<String, Boolean> batchExpire(Collection<String> keys, long ttl);
    
    /**
     * 异步为 Key 设置过期时间
     * 
     * @param key Redis Key
     * @param ttl 过期时间（秒）
     * @return CompletableFuture
     */
    CompletableFuture<Boolean> expireAsync(String key, long ttl);
    
    /**
     * 批量异步设置过期时间
     * 
     * @param keys Redis Key 列表
     * @param ttl 过期时间（秒）
     * @return CompletableFuture<Map<Key, Success>>
     */
    CompletableFuture<Map<String, Boolean>> batchExpireAsync(
        Collection<String> keys, long ttl);
    
    /**
     * 扫描匹配模式的 Key
     * 
     * @param pattern 匹配模式（如 "prefix:*"）
     * @param count 每次扫描数量
     * @return Key 集合
     */
    Collection<String> scan(String pattern, int count);
    
    /**
     * 检查 Key 是否存在
     * 
     * @param key Redis Key
     * @return true 如果存在
     */
    boolean exists(String key);
    
    /**
     * 获取 Key 的剩余 TTL
     * 
     * @param key Redis Key
     * @return TTL（秒），-1 表示永不过期，-2 表示不存在
     */
    long ttl(String key);
    
    /**
     * 关闭客户端
     */
    void close();
}
```

**设计说明**：
- ✅ 只定义续期相关的最小操作集
- ✅ 支持同步和异步（异步用于避免阻塞时间轮）
- ✅ 支持批量操作（减少网络开销）
- ✅ 无具体实现依赖（可用 Spring / Jedis / Lettuce 实现）

---

## 3. 核心实现方案

### 3.1 时间轮实现（推荐）

**优势**：
- 单线程高效调度
- O(1) 插入和删除
- 适合大量定时任务

**关键设计**：
- ⭐ 时间轮线程只负责调度，不执行 IO 操作
- ⭐ IO 操作提交到独立的异步执行器（线程池）
- ⭐ 避免网络阻塞影响时间轮精度

#### 3.1.1 异步执行器

```java
/**
 * 异步续期执行器
 * 
 * <p>职责：
 * <ul>
 *   <li>接收时间轮提交的续期任务</li>
 *   <li>异步执行 Redis 操作，避免阻塞时间轮</li>
 *   <li>任务队列满时拒绝策略：记录警告但不阻塞</li>
 * </ul>
 */
public class AsyncRenewalExecutor {
    
    private static final Logger log = LoggerFactory.getLogger(AsyncRenewalExecutor.class);
    
    private final ExecutorService executor;
    private final RedisClient redisClient;
    private final RenewalMetricsCollector metrics;
    
    public AsyncRenewalExecutor(
            RedisClient redisClient,
            RenewalMetricsCollector metrics,
            int threadPoolSize) {
        
        this.redisClient = redisClient;
        this.metrics = metrics;
        
        // 创建有界队列的线程池
        this.executor = new ThreadPoolExecutor(
            threadPoolSize,
            threadPoolSize,
            60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1000),
            new ThreadFactoryBuilder()
                .setNameFormat("renewal-executor-%d")
                .setDaemon(true)
                .build(),
            new ThreadPoolExecutor.CallerRunsPolicy() // 队列满时由调用线程执行
        );
    }
    
    /**
     * 提交续期任务（异步）
     * 
     * @return CompletableFuture<RenewalResult>
     */
    public CompletableFuture<RenewalResult> submitRenewal(
            String taskId,
            Collection<String> keys,
            long ttl) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return performRenewal(taskId, keys, ttl);
            } catch (Exception e) {
                log.error("续期执行异常: taskId={}", taskId, e);
                return RenewalResult.failure(taskId, e);
            }
        }, executor);
    }
    
    private RenewalResult performRenewal(
            String taskId,
            Collection<String> keys,
            long ttl) {
        
        long startTime = System.currentTimeMillis();
        
        // 批量续期（同步）
        Map<String, Boolean> results = redisClient.batchExpire(keys, ttl);
        
        long duration = System.currentTimeMillis() - startTime;
        
        // 统计结果
        long successCount = results.values().stream()
            .filter(Boolean::booleanValue)
            .count();
        long failureCount = results.size() - successCount;
        
        // 记录指标
        metrics.recordRenewal(taskId, (int) successCount, (int) failureCount);
        
        log.debug("续期完成: taskId={}, keys={}, success={}, failure={}, duration={}ms",
            taskId, keys.size(), successCount, failureCount, duration);
        
        return RenewalResult.success(taskId, successCount, failureCount, duration);
    }
    
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
```

#### 3.1.2 时间轮服务实现

```java
/**
 * 基于时间轮的续期服务
 * 
 * <p>关键设计：
 * <ul>
 *   <li>时间轮线程只负责调度，不执行 IO</li>
 *   <li>IO 操作提交到 AsyncRenewalExecutor</li>
 *   <li>避免网络阻塞影响调度精度</li>
 * </ul>
 */
public class TimeWheelRenewalService implements KeyRenewalService {
    
    private static final Logger log = LoggerFactory.getLogger(TimeWheelRenewalService.class);
    
    private final HashedWheelTimer timer;
    private final AsyncRenewalExecutor asyncExecutor;
    private final RedisClient redisClient;
    private final RenewalMetricsCollector metrics;
    private final Map<String, RenewalTaskWrapper> tasks;
    
    public TimeWheelRenewalService(
            RedisClient redisClient,
            RedisRenewalProperties properties) {
        
        this.redisClient = redisClient;
        this.metrics = new RenewalMetricsCollector();
        this.tasks = new ConcurrentHashMap<>();
        
        // 创建异步执行器
        this.asyncExecutor = new AsyncRenewalExecutor(
            redisClient,
            metrics,
            properties.getExecutorThreadPoolSize()
        );
        
        // 创建时间轮：100ms tick，512 slots
        TimeWheelConfig config = properties.getTimeWheel();
        this.timer = new HashedWheelTimer(
            new ThreadFactoryBuilder()
                .setNameFormat("redis-renewal-timer-%d")
                .setDaemon(true)
                .build(),
            config.getTickDuration(),
            TimeUnit.MILLISECONDS,
            config.getTicksPerWheel()
        );
        
        log.info("时间轮续期服务初始化: tick={}ms, slots={}, executorThreads={}",
            config.getTickDuration(),
            config.getTicksPerWheel(),
            properties.getExecutorThreadPoolSize());
    }
    
    @Override
    public String register(RenewalTask task) {
        String taskId = UUID.randomUUID().toString();
        RenewalTaskWrapper wrapper = new RenewalTaskWrapper(taskId, task);
        tasks.put(taskId, wrapper);
        
        // 调度首次续期
        scheduleRenewal(wrapper);
        
        log.info("注册续期任务: {}, keys={}, interval={}s",
            taskId, task.getKeys().size(), task.getInterval().getSeconds());
        
        return taskId;
    }
    
    private void scheduleRenewal(RenewalTaskWrapper wrapper) {
        RenewalTask task = wrapper.getTask();
        
        Timeout timeout = timer.newTimeout(t -> {
            // ⭐ 关键：时间轮回调中不执行 IO，只提交任务
            handleRenewalTick(wrapper);
        }, task.getInterval().toMillis(), TimeUnit.MILLISECONDS);
        
        wrapper.setTimeout(timeout);
    }
    
    /**
     * 处理续期 tick（时间轮线程调用）
     * 
     * <p>⭐ 关键：此方法在时间轮线程中执行，必须快速返回
     */
    private void handleRenewalTick(RenewalTaskWrapper wrapper) {
        RenewalTask task = wrapper.getTask();
        RenewalContext context = wrapper.getContext();
        
        try {
            // 检查是否应该继续续期
            if (!task.getStrategy().shouldContinue(context)) {
                tasks.remove(wrapper.getTaskId());
                log.info("续期任务完成: {}", wrapper.getTaskId());
                return;
            }
            
            // 计算 TTL
            long ttl = task.getStrategy().calculateTtl(context);
            
            // 获取 Key 列表
            Collection<String> keys = task.getKeyGenerator() != null
                ? task.getKeyGenerator().generateKeys()
                : task.getKeys();
            
            if (keys.isEmpty()) {
                log.warn("续期任务无 Key: {}", wrapper.getTaskId());
                scheduleRenewal(wrapper); // 继续调度
                return;
            }
            
            // ⭐ 提交到异步执行器（不阻塞时间轮）
            asyncExecutor.submitRenewal(wrapper.getTaskId(), keys, ttl)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        handleRenewalFailure(wrapper, ex);
                    } else {
                        handleRenewalSuccess(wrapper, result);
                    }
                });
            
            // ⭐ 立即调度下次续期（不等待 IO 完成）
            scheduleRenewal(wrapper);
            
        } catch (Exception e) {
            log.error("续期调度失败: {}", wrapper.getTaskId(), e);
            metrics.recordFailure(wrapper.getTaskId());
            
            // 失败重试
            if (wrapper.incrementFailureCount() < task.getMaxRetries()) {
                scheduleRenewal(wrapper);
            } else {
                tasks.remove(wrapper.getTaskId());
                log.error("续期任务失败次数过多，已取消: {}", wrapper.getTaskId());
            }
        }
    }
    
    private void handleRenewalSuccess(
            RenewalTaskWrapper wrapper,
            RenewalResult result) {
        
        RenewalContext context = wrapper.getContext();
        context.incrementRenewalCount();
        context.setLastRenewalTime(Instant.now());
        
        log.debug("续期成功: taskId={}, success={}, failure={}",
            wrapper.getTaskId(), result.getSuccessCount(), result.getFailureCount());
    }
    
    private void handleRenewalFailure(
            RenewalTaskWrapper wrapper,
            Throwable ex) {
        
        log.error("续期失败: {}", wrapper.getTaskId(), ex);
        metrics.recordFailure(wrapper.getTaskId());
        wrapper.incrementFailureCount();
    }
    
    @Override
    public void cancel(String taskId) {
        RenewalTaskWrapper wrapper = tasks.remove(taskId);
        if (wrapper != null) {
            Timeout timeout = wrapper.getTimeout();
            if (timeout != null) {
                timeout.cancel();
            }
            log.info("取消续期任务: {}", taskId);
        }
    }
    
    @Override
    public void pause(String taskId) {
        RenewalTaskWrapper wrapper = tasks.get(taskId);
        if (wrapper != null) {
            wrapper.setPaused(true);
            log.info("暂停续期任务: {}", taskId);
        }
    }
    
    @Override
    public void resume(String taskId) {
        RenewalTaskWrapper wrapper = tasks.get(taskId);
        if (wrapper != null) {
            wrapper.setPaused(false);
            scheduleRenewal(wrapper); // 重新调度
            log.info("恢复续期任务: {}", taskId);
        }
    }
    
    @Override
    public RenewalTaskStatus getStatus(String taskId) {
        RenewalTaskWrapper wrapper = tasks.get(taskId);
        return wrapper != null ? wrapper.getStatus() : null;
    }
    
    @Override
    public Collection<RenewalTask> getAllTasks() {
        return tasks.values().stream()
            .map(RenewalTaskWrapper::getTask)
            .collect(Collectors.toList());
    }
    
    public void shutdown() {
        log.info("关闭续期服务...");
        timer.stop();
        asyncExecutor.shutdown();
        log.info("续期服务已关闭");
    }
}
```

**设计要点**：

1. **时间轮与 IO 分离**：
   - 时间轮线程只负责调度（`handleRenewalTick`）
   - IO 操作提交到 `AsyncRenewalExecutor`
   - 时间轮回调快速返回，不等待 IO 完成

2. **异步续期**：
   - `submitRenewal` 返回 `CompletableFuture`
   - 续期结果通过 `whenComplete` 异步处理
   - 不阻塞时间轮调度

3. **容错机制**：
   - 线程池队列满时使用 `CallerRunsPolicy`（降级到同步）
   - 续期失败不影响下次调度
   - 失败次数超限自动取消任务

4. **性能优化**：
   - 批量续期减少网络往返
   - 异步执行器支持并发
   - 时间轮精度不受 IO 影响

### 3.2 定时任务实现（备选）

使用 `ScheduledExecutorService`：

```java
public class ScheduledRenewalService implements KeyRenewalService {
    
    private final ScheduledExecutorService scheduler;
    private final RedisTemplate<String, String> redisTemplate;
    private final Map<String, ScheduledFuture<?>> scheduledTasks;
    
    public ScheduledRenewalService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.scheduledTasks = new ConcurrentHashMap<>();
        
        // 单线程调度器
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder()
                .setNameFormat("redis-renewal-scheduler")
                .setDaemon(true)
                .build()
        );
    }
    
    @Override
    public String register(RenewalTask task) {
        String taskId = UUID.randomUUID().toString();
        
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
            () -> performRenewal(taskId, task),
            0,
            task.getInterval().toMillis(),
            TimeUnit.MILLISECONDS
        );
        
        scheduledTasks.put(taskId, future);
        return taskId;
    }
}
```

---

## 4. 续期策略实现

### 4.1 固定 TTL 策略

```java
public class FixedTtlStrategy implements RenewalStrategy {
    private final Duration ttl;
    
    public FixedTtlStrategy(Duration ttl) {
        this.ttl = ttl;
    }
    
    @Override
    public long calculateTtl(RenewalContext context) {
        return ttl.getSeconds();
    }
    
    @Override
    public boolean shouldContinue(RenewalContext context) {
        // 永久续期，除非手动取消
        return true;
    }
}
```

### 4.2 动态 TTL 策略

```java
public class DynamicTtlStrategy implements RenewalStrategy {
    private final Function<RenewalContext, Duration> ttlCalculator;
    
    @Override
    public long calculateTtl(RenewalContext context) {
        Duration ttl = ttlCalculator.apply(context);
        return ttl.getSeconds();
    }
    
    @Override
    public boolean shouldContinue(RenewalContext context) {
        Duration ttl = ttlCalculator.apply(context);
        return ttl.getSeconds() > 0;
    }
}
```

### 4.3 续期至指定时间策略

```java
public class UntilTimeStrategy implements RenewalStrategy {
    private final Instant endTime;
    private final Duration baseTtl;
    
    public UntilTimeStrategy(Instant endTime, Duration baseTtl) {
        this.endTime = endTime;
        this.baseTtl = baseTtl;
    }
    
    @Override
    public long calculateTtl(RenewalContext context) {
        Instant now = Instant.now();
        Duration remaining = Duration.between(now, endTime);
        
        // TTL 不超过剩余时间
        return Math.min(baseTtl.getSeconds(), remaining.getSeconds());
    }
    
    @Override
    public boolean shouldContinue(RenewalContext context) {
        return Instant.now().isBefore(endTime);
    }
}
```

### 4.4 条件续期策略

```java
public class ConditionalStrategy implements RenewalStrategy {
    private final Duration ttl;
    private final Predicate<RenewalContext> condition;
    
    @Override
    public long calculateTtl(RenewalContext context) {
        return ttl.getSeconds();
    }
    
    @Override
    public boolean shouldContinue(RenewalContext context) {
        return condition.test(context);
    }
}
```

---

## 5. Key 生成器实现

### 5.1 模式匹配生成器

```java
public class PatternKeyGenerator implements KeyGenerator {
    private final RedisTemplate<String, String> redisTemplate;
    private final String pattern;
    
    @Override
    public Collection<String> generateKeys() {
        Set<String> keys = redisTemplate.keys(pattern);
        return keys != null ? keys : Collections.emptySet();
    }
}
```

### 5.2 前缀生成器

```java
public class PrefixKeyGenerator implements KeyGenerator {
    private final RedisTemplate<String, String> redisTemplate;
    private final String prefix;
    
    @Override
    public Collection<String> generateKeys() {
        // 使用 SCAN 命令避免阻塞
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions()
            .match(prefix + "*")
            .count(100)
            .build();
        
        try (Cursor<byte[]> cursor = redisTemplate.getConnectionFactory()
                .getConnection()
                .scan(options)) {
            while (cursor.hasNext()) {
                keys.add(new String(cursor.next()));
            }
        }
        
        return keys;
    }
}
```

---

## 6. Redis 客户端实现

### 6.1 Spring Data Redis 实现

```java
/**
 * Spring Data Redis 客户端适配器
 */
public class SpringRedisClient implements RedisClient {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    public SpringRedisClient(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    @Override
    public boolean expire(String key, long ttl) {
        Boolean result = redisTemplate.expire(key, ttl, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }
    
    @Override
    public Map<String, Boolean> batchExpire(Collection<String> keys, long ttl) {
        Map<String, Boolean> results = new HashMap<>();
        
        // 使用 Pipeline 批量执行
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String key : keys) {
                connection.expire(key.getBytes(), ttl);
            }
            return null;
        });
        
        // 获取结果
        for (String key : keys) {
            results.put(key, true); // Pipeline 默认认为成功
        }
        
        return results;
    }
    
    @Override
    public CompletableFuture<Boolean> expireAsync(String key, long ttl) {
        return CompletableFuture.supplyAsync(() -> expire(key, ttl));
    }
    
    @Override
    public CompletableFuture<Map<String, Boolean>> batchExpireAsync(
            Collection<String> keys, long ttl) {
        return CompletableFuture.supplyAsync(() -> batchExpire(keys, ttl));
    }
    
    @Override
    public Collection<String> scan(String pattern, int count) {
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions()
            .match(pattern)
            .count(count)
            .build();
        
        try (Cursor<byte[]> cursor = redisTemplate.getConnectionFactory()
                .getConnection()
                .scan(options)) {
            while (cursor.hasNext()) {
                keys.add(new String(cursor.next()));
            }
        }
        
        return keys;
    }
    
    @Override
    public boolean exists(String key) {
        Boolean result = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(result);
    }
    
    @Override
    public long ttl(String key) {
        Long result = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return result != null ? result : -2;
    }
    
    @Override
    public void close() {
        // Spring 管理生命周期，不需要手动关闭
    }
}
```

### 6.2 Jedis 实现（可选）

```java
/**
 * Jedis 客户端适配器
 */
public class JedisRedisClient implements RedisClient {
    
    private final JedisPool jedisPool;
    
    public JedisRedisClient(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }
    
    @Override
    public boolean expire(String key, long ttl) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.expire(key, ttl) == 1;
        }
    }
    
    @Override
    public Map<String, Boolean> batchExpire(Collection<String> keys, long ttl) {
        Map<String, Boolean> results = new HashMap<>();
        
        try (Jedis jedis = jedisPool.getResource()) {
            Pipeline pipeline = jedis.pipelined();
            
            for (String key : keys) {
                pipeline.expire(key, ttl);
            }
            
            List<Object> responses = pipeline.syncAndReturnAll();
            int i = 0;
            for (String key : keys) {
                results.put(key, (Long) responses.get(i++) == 1);
            }
        }
        
        return results;
    }
    
    @Override
    public CompletableFuture<Boolean> expireAsync(String key, long ttl) {
        return CompletableFuture.supplyAsync(() -> expire(key, ttl));
    }
    
    @Override
    public CompletableFuture<Map<String, Boolean>> batchExpireAsync(
            Collection<String> keys, long ttl) {
        return CompletableFuture.supplyAsync(() -> batchExpire(keys, ttl));
    }
    
    @Override
    public Collection<String> scan(String pattern, int count) {
        Set<String> keys = new HashSet<>();
        
        try (Jedis jedis = jedisPool.getResource()) {
            String cursor = "0";
            ScanParams params = new ScanParams()
                .match(pattern)
                .count(count);
            
            do {
                ScanResult<String> result = jedis.scan(cursor, params);
                keys.addAll(result.getResult());
                cursor = result.getCursor();
            } while (!"0".equals(cursor));
        }
        
        return keys;
    }
    
    @Override
    public boolean exists(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.exists(key);
        }
    }
    
    @Override
    public long ttl(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.ttl(key);
        }
    }
    
    @Override
    public void close() {
        jedisPool.close();
    }
}
```

### 6.3 SPI 加载机制（脱离 Spring）

```java
/**
 * Redis 客户端 SPI 加载器
 * 
 * <p>用于非 Spring 环境，通过 SPI 加载客户端实现
 */
public class RedisClientLoader {
    
    private static final ServiceLoader<RedisClientProvider> loader = 
        ServiceLoader.load(RedisClientProvider.class);
    
    /**
     * 加载第一个可用的 Redis 客户端
     */
    public static RedisClient loadFirst() {
        Iterator<RedisClientProvider> iterator = loader.iterator();
        if (iterator.hasNext()) {
            return iterator.next().createClient();
        }
        throw new IllegalStateException("未找到 RedisClientProvider 实现");
    }
    
    /**
     * 加载指定类型的客户端
     */
    public static RedisClient load(String providerName) {
        for (RedisClientProvider provider : loader) {
            if (provider.getName().equals(providerName)) {
                return provider.createClient();
            }
        }
        throw new IllegalArgumentException("未找到客户端: " + providerName);
    }
}

/**
 * Redis 客户端提供者接口（SPI）
 */
public interface RedisClientProvider {
    
    /**
     * 提供者名称（如 "spring", "jedis", "lettuce"）
     */
    String getName();
    
    /**
     * 创建客户端实例
     */
    RedisClient createClient();
}
```

**SPI 配置文件**（`META-INF/services/xyz.firestige.infrastructure.redis.renewal.api.RedisClientProvider`）：

```
xyz.firestige.infrastructure.redis.renewal.client.spring.SpringRedisClientProvider
xyz.firestige.infrastructure.redis.renewal.client.jedis.JedisRedisClientProvider
```

---

## 7. 监控指标

### 6.1 指标收集器

```java
public class RenewalMetricsCollector {
    
    private final Map<String, TaskMetrics> taskMetrics = new ConcurrentHashMap<>();
    
    public void recordRenewal(String taskId, int successCount, int failureCount) {
        TaskMetrics metrics = taskMetrics.computeIfAbsent(
            taskId, 
            k -> new TaskMetrics()
        );
        
        metrics.incrementTotalRenewals();
        metrics.addSuccessCount(successCount);
        metrics.addFailureCount(failureCount);
        metrics.setLastRenewalTime(Instant.now());
    }
    
    public void recordFailure(String taskId) {
        TaskMetrics metrics = taskMetrics.computeIfAbsent(
            taskId, 
            k -> new TaskMetrics()
        );
        metrics.incrementTaskFailures();
    }
    
    public Map<String, TaskMetrics> getAllMetrics() {
        return Collections.unmodifiableMap(taskMetrics);
    }
    
    @Data
    public static class TaskMetrics {
        private long totalRenewals = 0;
        private long successCount = 0;
        private long failureCount = 0;
        private long taskFailures = 0;
        private Instant lastRenewalTime;
        private Instant createdTime = Instant.now();
        
        public double getSuccessRate() {
            long total = successCount + failureCount;
            return total > 0 ? (double) successCount / total * 100 : 0;
        }
    }
}
```

### 6.2 定时指标打印

```java
@Component
public class RenewalMetricsReporter {
    
    private static final Logger log = LoggerFactory.getLogger(RenewalMetricsReporter.class);
    
    private final RenewalMetricsCollector metricsCollector;
    
    @Scheduled(fixedRate = 60000) // 每分钟打印一次
    public void reportMetrics() {
        Map<String, TaskMetrics> allMetrics = metricsCollector.getAllMetrics();
        
        if (allMetrics.isEmpty()) {
            log.info("Redis 续期服务：当前无活跃任务");
            return;
        }
        
        log.info("╔════════════════════════════════════════╗");
        log.info("║  Redis 续期服务指标报告                ║");
        log.info("╚════════════════════════════════════════╝");
        log.info("活跃任务数: {}", allMetrics.size());
        
        allMetrics.forEach((taskId, metrics) -> {
            log.info("  任务 {}", taskId.substring(0, 8));
            log.info("    总续期次数: {}", metrics.getTotalRenewals());
            log.info("    成功 Key 数: {}", metrics.getSuccessCount());
            log.info("    失败 Key 数: {}", metrics.getFailureCount());
            log.info("    成功率: {:.2f}%", metrics.getSuccessRate());
            log.info("    任务失败次数: {}", metrics.getTaskFailures());
            log.info("    最后续期时间: {}", metrics.getLastRenewalTime());
        });
        
        log.info("════════════════════════════════════════");
    }
}
```

### 6.3 健康检查

```java
@Component
public class RenewalHealthIndicator implements HealthIndicator {
    
    private final KeyRenewalService renewalService;
    private final RenewalMetricsCollector metricsCollector;
    
    @Override
    public Health health() {
        try {
            Collection<RenewalTask> tasks = renewalService.getAllTasks();
            Map<String, TaskMetrics> metrics = metricsCollector.getAllMetrics();
            
            Map<String, Object> details = new HashMap<>();
            details.put("activeTasks", tasks.size());
            
            // 检查是否有高失败率任务
            boolean hasHighFailureRate = metrics.values().stream()
                .anyMatch(m -> m.getSuccessRate() < 80);
            
            if (hasHighFailureRate) {
                details.put("status", "WARNING");
                details.put("message", "部分任务续期失败率较高");
                return Health.status("WARNING").withDetails(details).build();
            }
            
            return Health.up().withDetails(details).build();
            
        } catch (Exception e) {
            return Health.down()
                .withException(e)
                .withDetail("message", "续期服务异常")
                .build();
        }
    }
}
```

---

## 7. Spring Boot 配置

### 7.1 配置属性

```java
@ConfigurationProperties(prefix = "redis.renewal")
public class RedisRenewalProperties {
    
    /**
     * 是否启用续期服务
     */
    private boolean enabled = true;
    
    /**
     * 实现类型: time-wheel | scheduled
     */
    private ImplementationType type = ImplementationType.TIME_WHEEL;
    
    /**
     * 时间轮配置
     */
    private TimeWheelConfig timeWheel = new TimeWheelConfig();
    
    /**
     * 异步执行器线程池大小
     */
    private int executorThreadPoolSize = 4;
    
    /**
     * 指标报告间隔（秒）
     */
    private int metricsReportInterval = 60;
    
    /**
     * 默认续期间隔（秒）
     */
    private int defaultRenewalInterval = 30;
    
    /**
     * 默认 TTL（秒）
     */
    private int defaultTtl = 300;
    
    public enum ImplementationType {
        TIME_WHEEL,
        SCHEDULED
    }
    
    @Data
    public static class TimeWheelConfig {
        /**
         * Tick 间隔（毫秒）
         */
        private long tickDuration = 100;
        
        /**
         * 时间轮槽数
         */
        private int ticksPerWheel = 512;
    }
}
```

### 7.2 自动配置

```java
@AutoConfiguration
@ConditionalOnClass(RedisTemplate.class)
@EnableConfigurationProperties(RedisRenewalProperties.class)
@ConditionalOnProperty(prefix = "redis.renewal", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisRenewalAutoConfiguration {
    
    /**
     * Redis 客户端（Spring Data Redis 实现）
     */
    @Bean
    @ConditionalOnMissingBean(RedisClient.class)
    public RedisClient redisClient(RedisTemplate<String, String> redisTemplate) {
        return new SpringRedisClient(redisTemplate);
    }
    
    /**
     * 时间轮续期服务
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "redis.renewal", name = "type", havingValue = "time-wheel", matchIfMissing = true)
    public KeyRenewalService timeWheelRenewalService(
            RedisClient redisClient,
            RedisRenewalProperties properties) {
        return new TimeWheelRenewalService(redisClient, properties);
    }
    
    /**
     * 定时任务续期服务（备选）
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "redis.renewal", name = "type", havingValue = "scheduled")
    public KeyRenewalService scheduledRenewalService(
            RedisClient redisClient,
            RedisRenewalProperties properties) {
        return new ScheduledRenewalService(redisClient, properties);
    }
    
    @Bean
    public RenewalMetricsCollector renewalMetricsCollector() {
        return new RenewalMetricsCollector();
    }
    
    @Bean
    public RenewalMetricsReporter renewalMetricsReporter(
            RenewalMetricsCollector metricsCollector,
            RedisRenewalProperties properties) {
        return new RenewalMetricsReporter(metricsCollector, properties);
    }
    
    @Bean
    public RenewalHealthIndicator renewalHealthIndicator(
            KeyRenewalService renewalService,
            RenewalMetricsCollector metricsCollector) {
        return new RenewalHealthIndicator(renewalService, metricsCollector);
    }
}
```

### 7.3 配置示例

```yaml
redis:
  renewal:
    enabled: true
    type: time-wheel  # time-wheel | scheduled
    time-wheel:
      tick-duration: 100  # 时间轮 tick 间隔（毫秒）
      ticks-per-wheel: 512  # 时间轮槽数
    executor-thread-pool-size: 4  # 异步执行器线程池大小
    metrics-report-interval: 60  # 指标报告间隔（秒）
    default-renewal-interval: 30  # 默认续期间隔（秒）
    default-ttl: 300  # 默认 TTL（秒）

spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
```

---

## 8. 使用示例

### 8.1 基本用法

```java
@Service
public class DeploymentService {
    
    @Autowired
    private KeyRenewalService renewalService;
    
    public void startDeployment(String tenantId) {
        // 创建续期任务
        RenewalTask task = RenewalTask.builder()
            .keys(List.of(
                "deployment:" + tenantId + ":config",
                "deployment:" + tenantId + ":status"
            ))
            .strategy(new FixedTtlStrategy(Duration.ofMinutes(5)))
            .interval(Duration.ofMinutes(2))
            .build();
        
        String taskId = renewalService.register(task);
        log.info("注册续期任务: {}", taskId);
    }
}
```

### 8.2 动态 TTL

```java
// 根据业务状态动态调整 TTL
RenewalTask task = RenewalTask.builder()
    .keys(List.of("task:" + taskId))
    .strategy(new DynamicTtlStrategy(context -> {
        // 前 10 次续期使用 5 分钟 TTL
        if (context.getRenewalCount() < 10) {
            return Duration.ofMinutes(5);
        }
        // 之后使用 10 分钟 TTL
        return Duration.ofMinutes(10);
    }))
    .interval(Duration.ofMinutes(2))
    .build();
```

### 8.3 续期至指定时间

```java
// 续期至任务预计完成时间
Instant estimatedEndTime = Instant.now().plus(Duration.ofHours(2));

RenewalTask task = RenewalTask.builder()
    .keys(List.of("task:" + taskId))
    .strategy(new UntilTimeStrategy(estimatedEndTime, Duration.ofMinutes(5)))
    .interval(Duration.ofMinutes(2))
    .build();
```

### 8.4 基于 KeyGenerator

```java
// 动态发现需要续期的 Key
RenewalTask task = RenewalTask.builder()
    .keyGenerator(new PrefixKeyGenerator(redisTemplate, "deployment:"))
    .strategy(new FixedTtlStrategy(Duration.ofMinutes(5)))
    .interval(Duration.ofMinutes(2))
    .build();
```

---

## 9. 实施计划

### Phase 1: 核心接口与模型（1 天）
- [ ] 定义 `KeyRenewalService` 接口
- [ ] 定义 `RenewalStrategy` 接口
- [ ] 定义 `KeyGenerator` 接口
- [ ] ⭐ 定义 `RedisClient` 接口（客户端抽象）
- [ ] 创建 `RenewalTask` 模型
- [ ] 创建 `RenewalContext` 模型
- [ ] 单元测试

### Phase 2: Redis 客户端实现（1 天）
- [ ] 实现 `SpringRedisClient`（Spring Data Redis 适配器）
- [ ] 实现 `JedisRedisClient`（可选）
- [ ] 实现 `SPI` 加载机制
- [ ] 单元测试

### Phase 3: 异步执行器（0.5 天）
- [ ] ⭐ 实现 `AsyncRenewalExecutor`（解决 IO 阻塞）
- [ ] 实现线程池管理
- [ ] 实现结果回调
- [ ] 单元测试

### Phase 4: 时间轮实现（2 天）
- [ ] 实现 `TimeWheelRenewalService`
- [ ] ⭐ 集成异步执行器（时间轮与 IO 分离）
- [ ] 实现任务调度逻辑
- [ ] 实现异常处理与重试
- [ ] 单元测试 + 集成测试

### Phase 5: 策略实现（1 天）
- [ ] 实现 4 种续期策略
- [ ] 单元测试

### Phase 6: Key 生成器（0.5 天）
- [ ] 实现 3 种生成器
- [ ] 单元测试

### Phase 7: 监控与可观测性（1 天）
- [ ] 实现指标收集器
- [ ] 实现定时报告
- [ ] 实现健康检查
- [ ] 集成测试

### Phase 8: Spring Boot 集成（0.5 天）
- [ ] 实现 AutoConfiguration
- [ ] 实现 Properties
- [ ] 添加到 `AutoConfiguration.imports`
- [ ] 配置示例

### Phase 9: 文档与测试（1 天）
- [ ] API 文档
- [ ] 使用指南
- [ ] 性能测试（IO 阻塞场景）
- [ ] 压力测试

**预计总工期**: 8.5 天

---

## 10. 技术选型

### 10.1 时间轮实现

**选项 1: Netty HashedWheelTimer**（推荐）
- ✅ 成熟稳定
- ✅ 高性能
- ✅ 社区支持好
- ❌ 需要引入 Netty 依赖

**选项 2: 自研时间轮**
- ✅ 无额外依赖
- ✅ 可定制
- ❌ 开发成本高
- ❌ 需要充分测试

**选项 3: ScheduledExecutorService**
- ✅ JDK 内置
- ✅ 简单易用
- ❌ 大量任务性能较差（O(log n)）

**推荐**: 使用 Netty HashedWheelTimer，性能和稳定性最优。

### 10.2 依赖项

```xml
<!-- Netty (用于时间轮) -->
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-common</artifactId>
    <version>4.1.104.Final</version>
</dependency>

<!-- Spring Data Redis (已有) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- Spring Boot Actuator (已有) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

---

## 11. 关键设计决策

### 11.1 解决方案：时间轮 IO 阻塞问题

**问题描述**：
时间轮的 tick 线程如果执行 Redis 操作时发生网络阻塞，会影响整个时间轮的调度精度，导致其他任务延迟。

**解决方案**：

#### 1. 时间轮与 IO 操作分离

```
┌────────────────┐     提交任务      ┌──────────────────┐
│  时间轮线程     │ ────────────────→ │  异步执行器       │
│  (调度)        │     (不等待)      │  (IO 线程池)      │
└────────────────┘                   └──────────────────┘
      ↓                                      ↓
  快速返回                              执行 Redis 操作
  继���调度                              (可能阻塞)
```

#### 2. 实现机制

**时间轮回调**（快速返回）：
```java
private void handleRenewalTick(RenewalTaskWrapper wrapper) {
    // ⭐ 只计算和提交，不执行 IO
    long ttl = task.getStrategy().calculateTtl(context);
    Collection<String> keys = getKeys();
    
    // ⭐ 提交到异步执行器（不阻塞）
    asyncExecutor.submitRenewal(taskId, keys, ttl)
        .whenComplete((result, ex) -> {
            // 异步处理结果
        });
    
    // ⭐ 立即调度下次续期
    scheduleRenewal(wrapper);
}
```

**异步执行器**（独立线程池）：
```java
public CompletableFuture<RenewalResult> submitRenewal(...) {
    return CompletableFuture.supplyAsync(() -> {
        // 在独立线程池中执行 Redis 操作
        return redisClient.batchExpire(keys, ttl);
    }, executor);
}
```

#### 3. 优势

- ✅ 时间轮调度不受 Redis IO 影响
- ✅ 时间轮精度得到保障
- ✅ 支持并发续期（多个线程并行执行 IO）
- ✅ 失败不影响时间轮调度

#### 4. 容错机制

- 线程池队列满时使用 `CallerRunsPolicy`（降级到同步，但不丢弃任务）
- Redis 失败不影响下次调度
- 异步结果通过 `CompletableFuture` 处理，不阻塞时间轮

---

### 11.2 解决方案：Redis 客户端抽象

**问题描述**：
核心逻辑与具体 Redis 客户端实现（Spring / Jedis / Lettuce）耦合，难以后续拆包或替换实现。

**解决方案**：

#### 1. 分层架构

```
┌─────────────────────────────────────────────────┐
│  api（核心接口，无外部依赖）                      │
│  - KeyRenewalService                            │
│  - RenewalStrategy                              │
│  - RedisClient ⭐                                │
└─────────────────────────────────────────────────┘
                    ↑
        ┌───────────┼───────────┐
        │           │           │
┌───────┴──────┐ ┌──┴────────┐ ┌┴────────────┐
│ core         │ │ strategy  │ │ generator   │
│ (依赖 api)   │ │ (依赖 api)│ │ (依赖 api)  │
└──────────────┘ └───────────┘ └─────────────┘
                    ↑
        ┌───────────┴───────────┐
        │                       │
┌───────┴──────────┐   ┌────────┴────────────┐
│ client/spring    │   │ client/jedis        │
│ (依赖 Spring)    │   │ (依赖 Jedis)        │
└──────────────────┘   └─────────────────────┘
```

#### 2. RedisClient 接口

定义最小操作集，只包含续期相关操作：

```java
public interface RedisClient {
    boolean expire(String key, long ttl);
    Map<String, Boolean> batchExpire(Collection<String> keys, long ttl);
    CompletableFuture<Boolean> expireAsync(String key, long ttl);
    CompletableFuture<Map<String, Boolean>> batchExpireAsync(...);
    Collection<String> scan(String pattern, int count);
    boolean exists(String key);
    long ttl(String key);
    void close();
}
```

#### 3. 多种实现

**Spring Data Redis 实现**（默认）：
```java
public class SpringRedisClient implements RedisClient {
    private final RedisTemplate<String, String> redisTemplate;
    // ... 实现
}
```

**Jedis 实现**（独立包）：
```java
public class JedisRedisClient implements RedisClient {
    private final JedisPool jedisPool;
    // ... 实现
}
```

**Lettuce 实现**（独立包）：
```java
public class LettuceRedisClient implements RedisClient {
    private final RedisClient lettuceClient;
    // ... 实现
}
```

#### 4. 加载机制

**Spring 环境**（AutoConfiguration）：
```java
@Bean
@ConditionalOnMissingBean(RedisClient.class)
public RedisClient redisClient(RedisTemplate<String, String> redisTemplate) {
    return new SpringRedisClient(redisTemplate);
}
```

**非 Spring 环境**（SPI）：
```java
// META-INF/services/RedisClientProvider
xyz.firestige...client.spring.SpringRedisClientProvider
xyz.firestige...client.jedis.JedisRedisClientProvider

// 加载
RedisClient client = RedisClientLoader.loadFirst();
```

#### 5. 模块化打包

```
redis-renewal-api.jar         # 核心接口（无依赖）
redis-renewal-core.jar        # 核心实现（依赖 api + Netty）
redis-renewal-spring.jar      # Spring 集成（依赖 core + Spring Data Redis）
redis-renewal-jedis.jar       # Jedis 集成（依赖 core + Jedis）可选
redis-renewal-lettuce.jar     # Lettuce 集成（依赖 core + Lettuce）可选
```

**使用场景**：

| 场景 | 依赖包 |
|------|--------|
| Spring Boot 项目 | api + core + spring |
| 非 Spring 项目（Jedis） | api + core + jedis |
| 非 Spring 项目（Lettuce） | api + core + lettuce |
| 自定义实现 | api + core + 自定义实现 |

#### 6. 优势

- ✅ 核心逻辑无 Spring 依赖
- ✅ 支持多种 Redis 客户端
- ✅ 便于后续拆包
- ✅ 支持 SPI 扩展
- ✅ 用户可自定义实现

---

## 12. 风险与缓解

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|---------|
| 异步执行器线程池耗尽 | 续期延迟 | 低 | 监控线程池使用率，动态调整；使用 CallerRunsPolicy 降级 |
| Redis 连接池耗尽 | 续期失败 | 中 | 批量操作减少连接占用；监控连接池状态 |
| Key 扫描性能问题 | CPU 占用高 | 中 | 使用 SCAN 命令；限制批量大小；建议使用固定 Key 列表 |
| 内存泄漏（任务未清理） | OOM | 低 | 定期清理已完成任务；监控任务数量 |
| 时间轮任务堆积 | 调度延迟 | 低 | ✅ 已通过异步执行器解决；监控待调度任务数 |
| 多模块依赖冲突 | 编译失败 | 低 | 明确依赖范围（provided/optional）；提供 BOM |

---

## 12. 验收标准

### 功能性
- [ ] 支持注册/取消/暂停/恢复续期任务
- [ ] 支持 4 种续期策略
- [ ] 支持 3 种 Key 生成器
- [ ] 单线程支持 1000+ 并发续期任务
- [ ] 续期失败自动重试

### 性能
- [ ] 单任务续期延迟 < 100ms
- [ ] CPU 占用 < 5%（1000 任务）
- [ ] 内存占用 < 100MB（1000 任务）

### 可观测性
- [ ] 每分钟打印指标报告
- [ ] 提供健康检查端点
- [ ] 异常日志完整

### 集成性
- [ ] 读取 Spring Boot Redis 配置
- [ ] 支持自定义实现替换
- [ ] 提供完整的配置示例

---

## 13. 参考资料

- [Netty HashedWheelTimer 文档](https://netty.io/4.1/api/io/netty/util/HashedWheelTimer.html)
- [Spring Data Redis 文档](https://docs.spring.io/spring-data/redis/docs/current/reference/html/)
- [Redis EXPIRE 命令](https://redis.io/commands/expire/)
- [时间轮算法原理](https://blog.csdn.net/mindfloating/article/details/8033340)

