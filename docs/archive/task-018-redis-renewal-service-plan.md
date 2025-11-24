# T-018: Redis 续期服务（通用模块）

> **任务 ID**: T-018  
> **优先级**: P1  
> **状态**: 待办  
> **创建时间**: 2025-11-24

---

## 1. 任务目标

开发一个脱离 deploy 域的、可复用的 Redis Key 续期服务，支持灵活的续期策略和条件配置。

---

## 2. 功能需求

### 2.1 核心功能
- 为指定的 Redis Key 或符合规则的 Key 提供自动续期
- 支持多种续期策略（定时、条件、混合）
- 支持 Key 模式匹配（通配符、正则表达式）
- 支持续期条件（时间范围、外部条件等）
- 提供续期监控和指标

### 2.2 使用场景
1. **租户锁续期**：长时间运行的任务需要定期续租锁
2. **临时数据保活**：执行中的任务状态需要延长 TTL
3. **条件续期**：只在满足特定条件时续期（如任务仍在运行）
4. **批量续期**：为一批符合规则的 Key 统一续期

---

## 3. 设计方案

### 3.1 核心接口

```java
/**
 * Redis Key 续期服务
 */
public interface RedisKeyRenewalService {
    
    /**
     * 注册单个 Key 的续期任务
     */
    RenewalTask registerKey(String key, RenewalConfig config);
    
    /**
     * 注册 Key 模式的续期任务（支持通配符）
     */
    RenewalTask registerPattern(String pattern, RenewalConfig config);
    
    /**
     * 取消续期任务
     */
    void cancelRenewal(String taskId);
    
    /**
     * 暂停续期任务
     */
    void pauseRenewal(String taskId);
    
    /**
     * 恢复续期任务
     */
    void resumeRenewal(String taskId);
    
    /**
     * 获取续期任务状态
     */
    RenewalStatus getStatus(String taskId);
}
```

### 3.2 配置模型

```java
/**
 * 续期配置
 */
public class RenewalConfig {
    // 续期策略
    private RenewalStrategy strategy;
    
    // 续期间隔（秒）
    private long intervalSeconds;
    
    // 每次续期的 TTL（秒）
    private long ttlSeconds;
    
    // 续期条件（可选）
    private RenewalCondition condition;
    
    // 最大续期次数（可选，-1 表示无限制）
    private int maxRenewals = -1;
    
    // 续期结束时间（可选）
    private Instant endTime;
    
    // 续期失败重试策略
    private RetryPolicy retryPolicy;
}

/**
 * 续期策略
 */
public enum RenewalStrategy {
    FIXED_INTERVAL,      // 固定间隔续期
    ADAPTIVE,            // 自适应续期（根据剩余 TTL）
    CONDITIONAL,         // 条件触发续期
    MANUAL               // 手动触发
}

/**
 * 续期条件
 */
@FunctionalInterface
public interface RenewalCondition {
    /**
     * 检查是否应该续期
     * @param key Redis Key
     * @param currentTtl 当前剩余 TTL（秒）
     * @return true 如果应该续期
     */
    boolean shouldRenew(String key, long currentTtl);
}
```

### 3.3 实现类设计

```java
/**
 * 默认实现：基于 ScheduledExecutorService
 */
public class ScheduledRedisKeyRenewalService implements RedisKeyRenewalService {
    private final RedisClient redisClient;
    private final ScheduledExecutorService scheduler;
    private final Map<String, RenewalTask> tasks;
    private final MetricsRegistry metrics;
    
    // 实现方法...
}

/**
 * 续期任务
 */
public class RenewalTask {
    private final String taskId;
    private final String keyOrPattern;
    private final RenewalConfig config;
    private final ScheduledFuture<?> scheduledFuture;
    private final AtomicInteger renewalCount;
    private final AtomicReference<RenewalStatus> status;
    
    // 控制方法...
}
```

---

## 4. 使用示例

### 4.1 简单定时续期

```java
// 每 30 秒为租户锁续期 1 小时
RenewalConfig config = RenewalConfig.builder()
    .strategy(RenewalStrategy.FIXED_INTERVAL)
    .intervalSeconds(30)
    .ttlSeconds(3600)
    .build();

RenewalTask task = renewalService.registerKey(
    "executor:lock:tenant:tenant-001", 
    config
);
```

### 4.2 条件续期

```java
// 只在任务仍在运行时续期
RenewalCondition condition = (key, currentTtl) -> {
    String taskId = extractTaskIdFromKey(key);
    return taskRepository.isRunning(taskId);
};

RenewalConfig config = RenewalConfig.builder()
    .strategy(RenewalStrategy.CONDITIONAL)
    .intervalSeconds(60)
    .ttlSeconds(7200)
    .condition(condition)
    .build();

renewalService.registerKey("executor:lock:tenant:tenant-001", config);
```

### 4.3 批量模式续期

```java
// 为所有租户锁续期
RenewalConfig config = RenewalConfig.builder()
    .strategy(RenewalStrategy.FIXED_INTERVAL)
    .intervalSeconds(45)
    .ttlSeconds(3600)
    .build();

renewalService.registerPattern("executor:lock:tenant:*", config);
```

### 4.4 限时续期

```java
// 续期直到指定时间
RenewalConfig config = RenewalConfig.builder()
    .strategy(RenewalStrategy.FIXED_INTERVAL)
    .intervalSeconds(30)
    .ttlSeconds(3600)
    .endTime(Instant.now().plus(Duration.ofHours(6)))
    .build();

renewalService.registerKey("executor:lock:tenant:tenant-001", config);
```

### 4.5 自适应续期

```java
// 根据剩余 TTL 自动调整续期时机
RenewalConfig config = RenewalConfig.builder()
    .strategy(RenewalStrategy.ADAPTIVE)
    .ttlSeconds(3600)
    .build();

// 自动在 TTL 剩余 20% 时续期
renewalService.registerKey("executor:lock:tenant:tenant-001", config);
```

---

## 5. 配置支持

### 5.1 YAML 配置

```yaml
redis:
  renewal:
    enabled: true
    default-interval-seconds: 60
    default-ttl-seconds: 3600
    scheduler:
      pool-size: 5
    metrics:
      enabled: true
    
    # 预定义续期规则
    rules:
      - name: tenant-locks
        pattern: "executor:lock:tenant:*"
        strategy: fixed-interval
        interval-seconds: 30
        ttl-seconds: 9000  # 2.5 hours
        
      - name: task-checkpoints
        pattern: "executor:ckpt:*"
        strategy: conditional
        interval-seconds: 60
        ttl-seconds: 604800  # 7 days
        condition: task-running  # 预定义条件
```

### 5.2 配置类

```java
@ConfigurationProperties(prefix = "redis.renewal")
public class RedisRenewalProperties {
    private boolean enabled = true;
    private long defaultIntervalSeconds = 60;
    private long defaultTtlSeconds = 3600;
    private SchedulerConfig scheduler = new SchedulerConfig();
    private MetricsConfig metrics = new MetricsConfig();
    private List<RenewalRule> rules = new ArrayList<>();
    
    // getters/setters
}
```

---

## 6. 监控与指标

### 6.1 关键指标

```java
public interface RenewalMetrics {
    // 续期任务数量
    Gauge activeRenewalTasks();
    
    // 续期成功次数
    Counter renewalSuccessCount();
    
    // 续期失败次数
    Counter renewalFailureCount();
    
    // 续期延迟（实际执行时间 - 预期执行时间）
    Histogram renewalLatency();
    
    // Key 剩余 TTL 分布
    Histogram keyTtlDistribution();
}
```

### 6.2 日志记录

```java
// 续期成功
log.debug("Renewed key: {}, newTTL: {}s, remainingBefore: {}s", 
    key, ttlSeconds, remainingTtl);

// 续期失败
log.warn("Failed to renew key: {}, reason: {}, retryCount: {}", 
    key, reason, retryCount);

// 续期任务结束
log.info("Renewal task completed: {}, totalRenewals: {}, duration: {}", 
    taskId, totalRenewals, duration);
```

---

## 7. 包结构

```
xyz.firestige.common.redis.renewal/
├── RedisKeyRenewalService.java           # 核心接口
├── ScheduledRedisKeyRenewalService.java  # 默认实现
├── config/
│   ├── RenewalConfig.java                # 续期配置
│   ├── RenewalStrategy.java              # 续期策略枚举
│   ├── RenewalCondition.java             # 续期条件接口
│   └── RetryPolicy.java                  # 重试策略
├── model/
│   ├── RenewalTask.java                  # 续期任务
│   ├── RenewalStatus.java                # 任务状态
│   └── RenewalResult.java                # 续期结果
├── condition/
│   ├── TimeBasedCondition.java           # 基于时间的条件
│   ├── TtlBasedCondition.java            # 基于 TTL 的条件
│   └── CustomCondition.java              # 自定义条件支持
├── metrics/
│   ├── RenewalMetrics.java               # 指标接口
│   └── MicrometerRenewalMetrics.java     # Micrometer 实现
└── autoconfigure/
    ├── RedisRenewalAutoConfiguration.java
    └── RedisRenewalProperties.java
```

---

## 8. Definition of Done

- [ ] 核心接口和实现类完成
- [ ] 支持 4 种续期策略（FIXED_INTERVAL, ADAPTIVE, CONDITIONAL, MANUAL）
- [ ] 支持 Key 模式匹配（通配符）
- [ ] 支持续期条件（时间、TTL、自定义）
- [ ] 配置支持（YAML + @ConfigurationProperties）
- [ ] 监控指标集成（Micrometer）
- [ ] 单元测试覆盖率 > 80%
- [ ] 集成测试（Redis + TestContainers）
- [ ] 文档完善（使用指南、API 文档）
- [ ] 与现有系统集成（租户锁续期）

---

## 9. 后续优化

### 9.1 Phase 2
- 支持 Redis Cluster 模式
- 支持分布式协调（避免多实例重复续期）
- 支持续期失败告警

### 9.2 Phase 3
- 支持基于 Lua 脚本的原子续期
- 支持续期历史记录
- 提供 Web UI 管理续期任务

---

## 10. 参考资料

- Redis EXPIRE 命令文档
- Spring Task Scheduling
- Micrometer Metrics
- 现有 RedisTenantLockManager 实现

