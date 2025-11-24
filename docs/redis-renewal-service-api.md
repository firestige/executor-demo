# Redis 续期服务 API 文档

> **版本**: v1.0  
> **最后更新**: 2025-11-24

---

## 📚 目录

1. [快速开始](#1-快速开始)
2. [核心概念](#2-核心概念)
3. [扩展点使用指南](#3-扩展点使用指南)
4. [完整示例](#4-完整示例)
5. [配置参考](#5-配置参考)
6. [FAQ](#6-faq)

---

## 1. 快速开始

### 1.1 添加依赖

**Maven**：
```xml
<!-- Spring Boot 项目 -->
<dependency>
    <groupId>xyz.firestige.infrastructure</groupId>
    <artifactId>redis-renewal-spring</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- 非 Spring 项目 -->
<dependency>
    <groupId>xyz.firestige.infrastructure</groupId>
    <artifactId>redis-renewal-core</artifactId>
    <version>1.0.0</version>
</dependency>
<dependency>
    <groupId>xyz.firestige.infrastructure</groupId>
    <artifactId>redis-renewal-jedis</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Gradle**：
```gradle
// Spring Boot 项目
implementation 'xyz.firestige.infrastructure:redis-renewal-spring:1.0.0'

// 非 Spring 项目
implementation 'xyz.firestige.infrastructure:redis-renewal-core:1.0.0'
implementation 'xyz.firestige.infrastructure:redis-renewal-jedis:1.0.0'
```

### 1.2 配置（Spring Boot）

```yaml
# application.yml
redis:
  renewal:
    enabled: true
    type: time-wheel
    executor-thread-pool-size: 4

spring:
  data:
    redis:
      host: localhost
      port: 6379
```

### 1.3 第一个续期任务

```java
@Service
public class DeploymentService {
    
    @Autowired
    private KeyRenewalService renewalService;
    
    public void startDeployment(String tenantId) {
        // 创建续期任务
        RenewalTask task = RenewalTask.builder()
            .keys(List.of("deployment:" + tenantId + ":config"))
            .ttlStrategy(new FixedTtlStrategy(Duration.ofMinutes(5)))
            .intervalStrategy(new FixedIntervalStrategy(Duration.ofMinutes(2)))
            .stopCondition(new TimeBasedStopCondition(estimatedEndTime))
            .build();
        
        String taskId = renewalService.register(task);
        log.info("注册续期任务: {}", taskId);
    }
}
```

---

## 2. 核心概念

### 2.1 核心组件

```
┌─────────────────────────────────────────┐
│  KeyRenewalService                      │  ← 续期服务入口
│  ├─ register(task)                      │
│  ├─ cancel(taskId)                      │
│  ├─ pause(taskId)                       │
│  └─ resume(taskId)                      │
└─────────────────────────────────────────┘
           │
           ├─ RenewalTask（续期任务）
           │    ├─ KeySelector          ← 选择哪些 Key
           │    ├─ RenewalStrategy      ← 决定 TTL
           │    ├─ IntervalStrategy     ← 决定续期间隔
           │    └─ StopCondition        ← 决定何时停止
           │
           └─ RedisClient（Redis 客户端）
```

### 2.2 扩展点分类

| 扩展点 | 职责 | 使用频率 | 预置实现 |
|--------|------|----------|----------|
| `KeySelector` | 选择需要续期的 Key | 🔴 高 | 5 种 |
| `RenewalStrategy` | 计算 TTL，决定是否继续 | 🔴 高 | 5 种 |
| `RenewalIntervalStrategy` | 计算续期间隔 | 🔴 高 | 4 种 |
| `StopCondition` | 判断何时停止 | 🔴 高 | 6 种 |
| `FailureHandler` | 处理续期失败 | 🟡 中 | 1 种 |
| `RenewalLifecycleListener` | 监听生命周期事件 | 🟡 中 | 1 种 |
| `RenewalFilter` | 续期前后拦截 | 🟡 中 | 1 种 |
| `BatchStrategy` | 批量续期策略 | 🟢 低 | 1 种 |
| `KeyGenerationStrategy` | 生成 Key 名称 | 🟢 低 | 1 种 |
| `RenewalScheduler` | 任务调度器 | 🟢 低 | 1 种 |

---

## 3. 扩展点使用指南

### 3.1 KeySelector（Key 选择器）🔴

**职责**：选择需要续期的 Key。

#### 3.1.1 StaticKeySelector - 固定列表

适用场景：Key 在创建任务时已知。

```java
KeySelector selector = new StaticKeySelector(List.of(
    "deployment:tenant1:config",
    "deployment:tenant1:status"
));

RenewalTask task = RenewalTask.builder()
    .keySelector(selector)
    // ...
    .build();
```

#### 3.1.2 PatternKeySelector - 模式匹配

适用场景：Key 动态生成，需要扫描 Redis。

```java
// 续期所有匹配 "deployment:*" 的 Key
KeySelector selector = new PatternKeySelector(
    "deployment:*",   // 匹配模式
    redisClient,      // Redis 客户端
    100               // 每次扫描数量
);
```

**⚠️ 注意**：使用 SCAN 命令，不会阻塞 Redis，但性能取决于 Key 数量。

#### 3.1.3 PrefixKeySelector - 前缀扫描

适用场景：扫描特定前缀的所有 Key。

```java
KeySelector selector = new PrefixKeySelector(
    "deployment:",    // 前缀
    redisClient
);
```

#### 3.1.4 FunctionKeySelector - 函数式选择

适用场景：复杂的动态选择逻辑。

```java
KeySelector selector = new FunctionKeySelector(ctx -> {
    // 动态获取 Key 列表
    return deploymentService.getActiveDeploymentKeys(ctx.getTaskId());
});
```

#### 3.1.5 CompositeKeySelector - 组合选择器

适用场景：多种规则组合。

```java
KeySelector selector = new CompositeKeySelector(
    new StaticKeySelector(List.of("key1", "key2")),
    new PatternKeySelector("dynamic:*", redisClient)
);
```

---

### 3.2 RenewalStrategy（续期策略）🔴

**职责**：计算每次续期的 TTL，决定是否继续续期。

#### 3.2.1 FixedTtlStrategy - 固定 TTL

适用场景：简单场景，TTL 固定不变。

```java
RenewalStrategy strategy = new FixedTtlStrategy(Duration.ofMinutes(5));
```

**说明**：
- 每次续期设置相同的 TTL
- 永久续期，直到手动取消或停止条件满足

#### 3.2.2 DynamicTtlStrategy - 动态 TTL

适用场景：根据业务状态动态调整 TTL。

```java
RenewalStrategy strategy = new DynamicTtlStrategy(ctx -> {
    // 前 10 次续期使用 5 分钟 TTL
    if (ctx.getRenewalCount() < 10) {
        return Duration.ofMinutes(5);
    }
    // 之后使用 10 分钟 TTL
    return Duration.ofMinutes(10);
});
```

**上下文信息**：
- `ctx.getRenewalCount()`：当前续期次数
- `ctx.getLastRenewalTime()`：上次续期时间
- `ctx.getAttribute(key)`：自定义属性

#### 3.2.3 UntilTimeStrategy - 续期至指定时间

适用场景：有明确结束时间的任务。

```java
Instant estimatedEndTime = Instant.now().plus(Duration.ofHours(2));

RenewalStrategy strategy = new UntilTimeStrategy(
    estimatedEndTime,           // 结束时间
    Duration.ofMinutes(5)       // 基础 TTL
);
```

**说明**：
- TTL 不会超过剩余时间
- 到达结束时间自动停止续期

#### 3.2.4 MaxRenewalsStrategy - 最大续期次数

适用场景：限制续期次数，防止无限续期。

```java
RenewalStrategy strategy = new MaxRenewalsStrategy(
    Duration.ofMinutes(5),      // 每次 TTL
    100                         // 最多续期 100 次
);
```

#### 3.2.5 ConditionalTtlStrategy - 条件判断

适用场景：复杂业务逻辑控制。

```java
RenewalStrategy strategy = new ConditionalTtlStrategy(
    // TTL 计算
    ctx -> Duration.ofMinutes(5),
    
    // 继续条件
    ctx -> deploymentService.isDeploymentActive(ctx.getTaskId())
);
```

---

### 3.3 RenewalIntervalStrategy（续期间隔策略）🔴

**职责**：计算两次续期之间的间隔。

#### 3.3.1 FixedIntervalStrategy - 固定间隔

适用场景：大多数场景。

```java
RenewalIntervalStrategy strategy = new FixedIntervalStrategy(
    Duration.ofMinutes(2)  // 每 2 分钟续期一次
);
```

#### 3.3.2 ExponentialBackoffStrategy - 指数退避

适用场景：减轻 Redis 压力，失败后延长间隔。

```java
RenewalIntervalStrategy strategy = new ExponentialBackoffStrategy(
    Duration.ofSeconds(30),   // 初始间隔
    Duration.ofMinutes(10),   // 最大间隔
    2.0                       // 退避因子
);
```

**说明**：
- 第 1 次：30 秒
- 第 2 次：60 秒
- 第 3 次：120 秒
- ...
- 最大：10 分钟

#### 3.3.3 AdaptiveIntervalStrategy - 自适应间隔

适用场景：根据 TTL 自动调整间隔。

```java
RenewalIntervalStrategy strategy = new AdaptiveIntervalStrategy(
    0.5  // TTL 的 50% 续期一次
);
```

**示例**：
- TTL = 5 分钟 → 间隔 = 2.5 分钟
- TTL = 10 分钟 → 间隔 = 5 分钟

#### 3.3.4 RandomizedIntervalStrategy - 随机抖动

适用场景：避免续期任务集中。

```java
RenewalIntervalStrategy strategy = new RandomizedIntervalStrategy(
    Duration.ofMinutes(2),    // 基础间隔
    Duration.ofSeconds(30)    // 随机抖动范围（±30秒）
);
```

---

### 3.4 StopCondition（停止条件）🔴

**职责**：判断何时停止续期任务。

#### 3.4.1 NeverStopCondition - 永不停止

适用场景：需要手动取消的长期任务。

```java
StopCondition condition = new NeverStopCondition();
```

**说明**：需要手动调用 `renewalService.cancel(taskId)` 停止。

#### 3.4.2 TimeBasedStopCondition - 时间停止

适用场景：有明确截止时间。

```java
Instant endTime = Instant.now().plus(Duration.ofHours(2));
StopCondition condition = new TimeBasedStopCondition(endTime);
```

#### 3.4.3 CountBasedStopCondition - 次数停止

适用场景：限制续期次数。

```java
StopCondition condition = new CountBasedStopCondition(100);  // 续期 100 次后停止
```

#### 3.4.4 KeyNotExistsStopCondition - Key 不存在停止

适用场景：Key 被删除后自动停止。

```java
StopCondition condition = new KeyNotExistsStopCondition(redisClient);
```

**说明**：检查任意一个 Key 不存在即停止。

#### 3.4.5 ExternalSignalStopCondition - 外部信号停止

适用场景：业务状态变化时停止。

```java
StopCondition condition = new ExternalSignalStopCondition(
    () -> deploymentService.isDeploymentCompleted(taskId)
);
```

#### 3.4.6 CompositeStopCondition - 组合条件

适用场景：复杂停止逻辑。

```java
// 任一条件满足即停止
StopCondition condition = CompositeStopCondition.anyOf(
    new TimeBasedStopCondition(endTime),
    new CountBasedStopCondition(100),
    new KeyNotExistsStopCondition(redisClient)
);

// 所有条件满足才停止
StopCondition condition = CompositeStopCondition.allOf(
    new TimeBasedStopCondition(endTime),
    new ExternalSignalStopCondition(() -> completed)
);
```

---

### 3.5 中低频扩展点

#### 3.5.1 FailureHandler - 失败处理器 🟡

**默认实现**：`LogAndContinueFailureHandler`（记录日志并继续）

**自定义示例**：
```java
public class RetryFailureHandler implements FailureHandler {
    private final int maxRetries;
    
    @Override
    public FailureHandleResult handle(String taskId, Collection<String> keys, Throwable error) {
        if (getRetryCount(taskId) < maxRetries) {
            return FailureHandleResult.retry(Duration.ofSeconds(10));
        }
        return FailureHandleResult.giveUp();
    }
}

// 使用
RenewalTask task = RenewalTask.builder()
    .failureHandler(new RetryFailureHandler(3))
    .build();
```

#### 3.5.2 RenewalLifecycleListener - 生命周期监听器 🟡

**默认实现**：`NoOpLifecycleListener`（空实现）

**自定义示例**：
```java
public class MetricsListener implements RenewalLifecycleListener {
    @Override
    public void afterRenewal(String taskId, RenewalResult result) {
        // 记录 Prometheus 指标
        prometheusRegistry.counter("renewal_total", "taskId", taskId).inc();
    }
}

// 使用
RenewalTask task = RenewalTask.builder()
    .listener(new MetricsListener())
    .build();
```

#### 3.5.3 RenewalFilter - 续期过滤器 🟡

**默认实现**：`PassThroughFilter`（直通）

**自定义示例**：
```java
public class ExistenceCheckFilter implements RenewalFilter {
    @Override
    public Collection<String> beforeRenewal(Collection<String> keys, long ttl) {
        // 过滤掉不存在的 Key
        return keys.stream()
            .filter(key -> redisClient.exists(key))
            .collect(Collectors.toList());
    }
}

// 使用
RenewalTask task = RenewalTask.builder()
    .filter(new ExistenceCheckFilter())
    .build();
```

---

## 4. 完整示例

### 4.1 简单部署任务续期

```java
@Service
public class DeploymentService {
    
    @Autowired
    private KeyRenewalService renewalService;
    
    public String startDeployment(String tenantId, String taskId) {
        // 估算任务完成时间（2 小时）
        Instant estimatedEndTime = Instant.now().plus(Duration.ofHours(2));
        
        // 创建续期任务
        RenewalTask task = RenewalTask.builder()
            // Key 列表
            .keySelector(new StaticKeySelector(List.of(
                "deployment:" + tenantId + ":config",
                "deployment:" + tenantId + ":status"
            )))
            
            // 固定 5 分钟 TTL
            .ttlStrategy(new FixedTtlStrategy(Duration.ofMinutes(5)))
            
            // 每 2 分钟续期一次
            .intervalStrategy(new FixedIntervalStrategy(Duration.ofMinutes(2)))
            
            // 2 小时后停止
            .stopCondition(new TimeBasedStopCondition(estimatedEndTime))
            
            .build();
        
        // 注册任务
        String renewalTaskId = renewalService.register(task);
        log.info("部署任务 {} 注册续期任务 {}", taskId, renewalTaskId);
        
        return renewalTaskId;
    }
    
    public void completeDeployment(String renewalTaskId) {
        // 部署完成，取消续期
        renewalService.cancel(renewalTaskId);
        log.info("部署完成，取消续期任务 {}", renewalTaskId);
    }
}
```

### 4.2 动态 Key + 自适应间隔

```java
public class AdvancedRenewalExample {
    
    public String createDynamicRenewal(String prefix) {
        RenewalTask task = RenewalTask.builder()
            // 动态扫描 Key
            .keySelector(new PatternKeySelector(
                prefix + ":*",
                redisClient,
                100
            ))
            
            // 动态调整 TTL
            .ttlStrategy(new DynamicTtlStrategy(ctx -> {
                // 前 10 次：5 分钟
                if (ctx.getRenewalCount() < 10) {
                    return Duration.ofMinutes(5);
                }
                // 10-50 次：10 分钟
                if (ctx.getRenewalCount() < 50) {
                    return Duration.ofMinutes(10);
                }
                // 50 次以上：30 分钟
                return Duration.ofMinutes(30);
            }))
            
            // 自适应间隔（TTL 的 50%）
            .intervalStrategy(new AdaptiveIntervalStrategy(0.5))
            
            // 组合停止条件
            .stopCondition(CompositeStopCondition.anyOf(
                new CountBasedStopCondition(100),
                new KeyNotExistsStopCondition(redisClient)
            ))
            
            .build();
        
        return renewalService.register(task);
    }
}
```

### 4.3 完整功能示例

```java
public class CompleteRenewalExample {
    
    public String createCompleteRenewal() {
        RenewalTask task = RenewalTask.builder()
            // 函数式 Key 选择
            .keySelector(new FunctionKeySelector(ctx -> 
                deploymentService.getActiveKeys(ctx.getTaskId())
            ))
            
            // 条件 TTL
            .ttlStrategy(new ConditionalTtlStrategy(
                ctx -> calculateTtl(ctx),
                ctx -> shouldContinue(ctx)
            ))
            
            // 指数退避
            .intervalStrategy(new ExponentialBackoffStrategy(
                Duration.ofSeconds(30),
                Duration.ofMinutes(10),
                2.0
            ))
            
            // 外部信号停止
            .stopCondition(new ExternalSignalStopCondition(() -> 
                deploymentService.isCompleted()
            ))
            
            // 失败重试
            .failureHandler(new RetryFailureHandler(3))
            
            // 生命周期监听
            .listener(new MetricsLifecycleListener())
            
            // 存在性检查过滤器
            .filter(new ExistenceCheckFilter())
            
            .build();
        
        return renewalService.register(task);
    }
    
    private Duration calculateTtl(RenewalContext ctx) {
        // 自定义 TTL 计算逻辑
        return Duration.ofMinutes(5);
    }
    
    private boolean shouldContinue(RenewalContext ctx) {
        // 自定义继续条件
        return ctx.getRenewalCount() < 100;
    }
}
```

### 4.4 使用预设模板

```java
// 简单固定续期
String taskId = RenewalTask.fixedRenewal(
    keys,
    Duration.ofMinutes(5),    // TTL
    Duration.ofMinutes(2)     // 间隔
);

// 续期至指定时间
String taskId = RenewalTask.untilTime(
    keys,
    Duration.ofMinutes(5),    // TTL
    endTime                   // 结束时间
);

// 最多续期 N 次
String taskId = RenewalTask.maxRenewals(
    keys,
    Duration.ofMinutes(5),    // TTL
    100                       // 最大次数
);
```

---

## 5. 配置参考

### 5.1 完整配置示例

```yaml
redis:
  renewal:
    # 是否启用
    enabled: true
    
    # 实现类型：time-wheel（推荐）| scheduled
    type: time-wheel
    
    # 时间轮配置
    time-wheel:
      tick-duration: 100        # Tick 间隔（毫秒），默认 100
      ticks-per-wheel: 512      # 时间轮槽数，默认 512
    
    # 异步执行器配置
    executor-thread-pool-size: 4  # 线程池大小，默认 4
    
    # 监控配置
    metrics-report-interval: 60   # 指标报告间隔（秒），默认 60
    
    # 默认值
    default-renewal-interval: 30  # 默认续期间隔（秒），默认 30
    default-ttl: 300              # 默认 TTL（秒���，默认 300

spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: 0
      timeout: 3000
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0

management:
  endpoints:
    web:
      exposure:
        include: health,metrics
  health:
    redis:
      enabled: true
```

### 5.2 配置说明

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `redis.renewal.enabled` | `true` | 是否启用续期服务 |
| `redis.renewal.type` | `time-wheel` | 调度器类型 |
| `redis.renewal.time-wheel.tick-duration` | `100` | 时间轮 tick 间隔（毫秒） |
| `redis.renewal.time-wheel.ticks-per-wheel` | `512` | 时间轮槽数 |
| `redis.renewal.executor-thread-pool-size` | `4` | 异步执行器线程池大小 |
| `redis.renewal.metrics-report-interval` | `60` | 指标报告间隔（秒） |

---

## 6. FAQ

### Q1: 续期间隔和 TTL 应该如何配置？

**A**: 推荐续期间隔为 TTL 的 1/2 到 2/3。

```
TTL = 5 分钟
推荐间隔 = 2-3 分钟
```

### Q2: 如何手动停止续期任务？

**A**: 调用 `cancel` 方法。

```java
renewalService.cancel(taskId);
```

### Q3: 续期失败会怎样？

**A**: 默认记录日志并继续下次续期。可自定义失败处理器实现重试等逻辑。

### Q4: 如何监控续期任务？

**A**: 
1. 查看定时打印的指标报告（每分钟）
2. 访问健康检查端点：`/actuator/health`
3. 自定义 `RenewalLifecycleListener` 接入监控系统

### Q5: 支持哪些 Redis 客户端？

**A**: 
- Spring Data Redis（默认）
- Jedis（可选）
- Lettuce（可选）
- 自定义实现 `RedisClient` 接口

### Q6: 如何测试续期功能？

**A**: 

```java
@SpringBootTest
class RenewalServiceTest {
    
    @Autowired
    private KeyRenewalService renewalService;
    
    @Test
    void testRenewal() {
        RenewalTask task = RenewalTask.builder()
            .keys(List.of("test:key"))
            .ttlStrategy(new FixedTtlStrategy(Duration.ofSeconds(10)))
            .intervalStrategy(new FixedIntervalStrategy(Duration.ofSeconds(3)))
            .stopCondition(new CountBasedStopCondition(3))
            .build();
        
        String taskId = renewalService.register(task);
        
        // 等待续期执行
        Thread.sleep(Duration.ofSeconds(15).toMillis());
        
        // 验证 Key 仍然存在
        assertTrue(redisTemplate.hasKey("test:key"));
    }
}
```

### Q7: 性能如何？

**A**: 
- 单任务续期延迟：< 100ms
- CPU 占用：< 5%（1000 个任务）
- 内存占用：< 100MB（1000 个任务）

### Q8: 如何实现续期任务持久化？

**A**: 当前版本不支持，计划在中期扩展中实现。临时方案：应用重启时重新注册续期任务。

---

## 📖 相关文档

- [设计文档](design/redis-renewal-service.md)
- [扩展点设计清单](temp/task-018-extension-points-design.md)
- [实施方案](temp/task-018-redis-renewal-service-design.md)

---

**文档版本**: v1.0  
**维护者**: 架构团队  
**反馈**: 如有问题请提 Issue

