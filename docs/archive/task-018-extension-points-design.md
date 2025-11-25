# T-018 扩展点设计清单

> **目标**：提供高层抽象接口 + 预置实现，平衡扩展性与易用性

---

## 1. 核心扩展点（必需）

### 1.1 RenewalStrategy（续期策略）

**职责**：决定每次续期的 TTL 和是否继续续期

```java
public interface RenewalStrategy {
    /**
     * 计算下次续期的 TTL
     * @param context 续期上下文（包含续期次数、上次时间等）
     * @return TTL（秒）
     */
    long calculateTtl(RenewalContext context);
    
    /**
     * 是否应该继续续期
     * @param context 续期上下文
     * @return true 继续，false 停止
     */
    boolean shouldContinue(RenewalContext context);
    
    /**
     * 策略名称（用于日志和监控）
     */
    String getName();
}
```

**预置实现**：

| 实现类 | 说明 | 使用场景 |
|--------|------|----------|
| `FixedTtlStrategy` | 固定 TTL，永久续期 | 简单场景，需要手动取消 |
| `DynamicTtlStrategy` | 根据上下文动态计算 TTL | 需要根据业务状态调整 TTL |
| `UntilTimeStrategy` | 续期至指定时间点 | 有明确结束时间的任务 |
| `MaxRenewalsStrategy` | 最多续期 N 次后停止 | 限制续期次数，防止无限续期 |
| `ConditionalStrategy` | 基于外部条件判断 | 复杂业务逻辑控制 |

---

### 1.2 RenewalIntervalStrategy（续期间隔策略）

**职责**：决定两次续期之间的时间间隔

```java
public interface RenewalIntervalStrategy {
    /**
     * 计算下次续期间隔
     * @param context 续期上下文
     * @return 间隔时长
     */
    Duration calculateInterval(RenewalContext context);
    
    String getName();
}
```

**预置实现**：

| 实现类 | 说明 | 使用场景 |
|--------|------|----------|
| `FixedIntervalStrategy` | 固定间隔 | 大多数场景 |
| `ExponentialBackoffStrategy` | 指数退避（失败后延长间隔） | 减轻 Redis 压力 |
| `AdaptiveIntervalStrategy` | 根据 TTL 自适应调整 | TTL 的 1/2 或 2/3 续期 |
| `RandomizedIntervalStrategy` | 添加随机抖动 | 避免续期任务集中 |

---

### 1.3 KeySelector（Key 选择器）

**职责**：决定哪些 Key 需要续期

```java
public interface KeySelector {
    /**
     * 选择需要续期的 Key
     * @param context 续期上下文
     * @return Key 集合
     */
    Collection<String> selectKeys(RenewalContext context);
    
    String getName();
}
```

**预置实现**：

| 实现类 | 说明 | 使用场景 |
|--------|------|----------|
| `StaticKeySelector` | 固定 Key 列表 | Key 在创建时已知 |
| `PatternKeySelector` | 按模式匹配（SCAN） | Key 动态生成，需扫描 |
| `PrefixKeySelector` | 按前缀扫描 | 特定前缀的所有 Key |
| `FunctionKeySelector` | 用户自定义函数 | 复杂选择逻辑 |
| `CompositeKeySelector` | 组合多个选择器 | 多种规则组合 |

---

### 1.4 StopCondition（停止条件）

**职责**：决定何时停止续期任务

```java
public interface StopCondition {
    /**
     * 判断是否应该停止续期
     * @param context 续期上下文
     * @return true 停止，false 继续
     */
    boolean shouldStop(RenewalContext context);
    
    String getName();
}
```

**预置实现**：

| 实现类 | 说明 | 使用场景 |
|--------|------|----------|
| `NeverStopCondition` | 永不停止 | 需要手动取消的长期任务 |
| `TimeBasedStopCondition` | 到达指定时间停止 | 有明确截止时间 |
| `CountBasedStopCondition` | 续期 N 次后停止 | 限制续期次数 |
| `KeyNotExistsStopCondition` | Key 不存在时停止 | Key 被删除后自动停止 |
| `ExternalSignalStopCondition` | 外部信号触发停止 | 业务状态变化时停止 |
| `CompositeStopCondition` | 多个条件组合（AND/OR） | 复杂停止逻辑 |

---

## 2. 高级扩展点（可选）

### 2.1 RenewalFilter（续期过滤器）

**职责**：在续期执行前后进行拦截和处理

```java
public interface RenewalFilter {
    /**
     * 续期前拦截
     * @param keys 待续期的 Key
     * @param ttl 待设置的 TTL
     * @return 过滤后的 Key（可修改或排除）
     */
    Collection<String> beforeRenewal(Collection<String> keys, long ttl);
    
    /**
     * 续期后处理
     * @param results 续期结果
     */
    void afterRenewal(Map<String, Boolean> results);
    
    String getName();
}
```

**预置实现**：

| 实现类 | 说明 | 使用场景 |
|--------|------|----------|
| `ExistenceCheckFilter` | 续期前检查 Key 是否存在 | 避免续期不存在的 Key |
| `RateLimitFilter` | 限制续期频率 | 保护 Redis |
| `LoggingFilter` | 记录续期日志 | 调试和审计 |
| `MetricsFilter` | 记录详细指标 | 监控和分析 |

---

### 2.2 FailureHandler（失败处理器）

**职责**：处理续期失败情况

```java
public interface FailureHandler {
    /**
     * 处理续期失败
     * @param taskId 任务 ID
     * @param keys 失败的 Key
     * @param error 异常信息
     * @return 处理结果（是否重试）
     */
    FailureHandleResult handle(String taskId, Collection<String> keys, Throwable error);
    
    String getName();
}
```

**预置实现**：

| 实现类 | 说明 | 使用场景 |
|--------|------|----------|
| `RetryFailureHandler` | 失败重试（带退避策略） | 临时网络问题 |
| `LogAndIgnoreHandler` | 记录日志并忽略 | 非关键 Key |
| `AlertFailureHandler` | 失败告警（钉钉/邮件） | 关键业务 Key |
| `FallbackHandler` | 降级处理 | 多级容错 |

---

### 2.3 KeyGenerationStrategy（Key 生成策略）

**职责**：生成 Key 名称（用于动态 Key 场景）

```java
public interface KeyGenerationStrategy {
    /**
     * 生成 Key
     * @param template Key 模板（如 "task:{tenantId}:{taskId}"）
     * @param context 上下文（包含变量值）
     * @return 生成的 Key
     */
    String generateKey(String template, Map<String, Object> context);
    
    String getName();
}
```

**预置实现**：

| 实现类 | 说明 | 使用场景 |
|--------|------|----------|
| `PlaceholderKeyGenerator` | 占位符替换（{var}） | 简单模板 |
| `SpELKeyGenerator` | Spring EL 表达式 | 复杂逻辑 |
| `FunctionKeyGenerator` | 用户自定义函数 | 完全自定义 |

---

### 2.4 BatchStrategy（批量策略）

**职责**：决定如何批量续期

```java
public interface BatchStrategy {
    /**
     * 将 Key 分批
     * @param keys 所有 Key
     * @return 批次列表
     */
    List<List<String>> batch(Collection<String> keys);
    
    String getName();
}
```

**预置实现**：

| 实现类 | 说明 | 使用场景 |
|--------|------|----------|
| `FixedSizeBatchStrategy` | 固定批次大小 | 大多数场景 |
| `DynamicBatchStrategy` | 根据 Key 数量动态调整 | 优化性能 |
| `PriorityBatchStrategy` | 按优先级分批 | 重要 Key 优先 |

---

### 2.5 RenewalLifecycleListener（生命周期监听器）

**职责**：监听续期任务生命周期事件

```java
public interface RenewalLifecycleListener {
    /**
     * 任务注册时
     */
    void onTaskRegistered(String taskId, RenewalTask task);
    
    /**
     * 续期执行前
     */
    void beforeRenewal(String taskId, Collection<String> keys);
    
    /**
     * 续期执行后
     */
    void afterRenewal(String taskId, RenewalResult result);
    
    /**
     * 任务完成时
     */
    void onTaskCompleted(String taskId, CompletionReason reason);
    
    /**
     * 任务失败时
     */
    void onTaskFailed(String taskId, Throwable error);
}
```

**预置实现**：

| 实现类 | 说明 | 使用场景 |
|--------|------|----------|
| `LoggingLifecycleListener` | 记录生命周期日志 | 调试 |
| `MetricsLifecycleListener` | 记录生命周期指标 | 监控 |
| `EventPublishingListener` | 发布领域事件 | 事件驱动架构 |

---

### 2.6 RenewalScheduler（调度器抽象）

**职责**：抽象任务调度机制

```java
public interface RenewalScheduler {
    /**
     * 调度续期任务
     * @param task 续期任务
     * @param delay 延迟时间
     */
    void schedule(Runnable task, Duration delay);
    
    /**
     * 取消调度
     */
    void cancel(String taskId);
    
    /**
     * 关闭调度器
     */
    void shutdown();
}
```

**预置实现**：

| 实现类 | 说明 | 使用场景 |
|--------|------|----------|
| `TimeWheelScheduler` | 基于时间轮 | 高性能，大量任务 |
| `ScheduledExecutorScheduler` | 基于 ScheduledExecutorService | 简单场景 |
| `QuartzScheduler` | 基于 Quartz | 需要持久化调度 |

---

### 2.7 RenewalContext（上下文增强）

**职责**：提供续期执行的上下文信息

```java
public interface RenewalContext {
    // 基础信息
    String getTaskId();
    long getRenewalCount();
    Instant getStartTime();
    Instant getLastRenewalTime();
    
    // 扩展属性
    <T> T getAttribute(String key);
    void setAttribute(String key, Object value);
    
    // 统计信息
    long getTotalSuccessCount();
    long getTotalFailureCount();
    Duration getAverageDuration();
}
```

---

## 3. 易用性增强（建议全部采用）

### 3.1 建造者模式

```java
RenewalTask task = RenewalTask.builder()
    .keys(List.of("key1", "key2"))
    .ttlStrategy(new FixedTtlStrategy(Duration.ofMinutes(5)))
    .intervalStrategy(new FixedIntervalStrategy(Duration.ofMinutes(2)))
    .stopStrategy(new TimeBasedStopCondition(endTime))
    .build();
```

### 3.2 流式 API

```java
renewalService.create()
    .withKeys("key1", "key2")
    .fixedTtl(Duration.ofMinutes(5))
    .fixedInterval(Duration.ofMinutes(2))
    .stopAt(endTime)
    .onFailure(handler)
    .register();
```

### 3.3 预设模板

```java
// 模板方法
RenewalTask task = RenewalTask.fixedRenewal(keys, ttl, interval);
RenewalTask task = RenewalTask.untilTime(keys, ttl, endTime);
RenewalTask task = RenewalTask.maxRenewals(keys, ttl, maxCount);

// 服务方法
String taskId = renewalService.renewUntil(keys, ttl, endTime);
String taskId = renewalService.renewForever(keys, ttl, interval);
```

### 3.4 注解驱动（Spring 环境）

```java
@RenewRedisKey(
    key = "task:{tenantId}:{taskId}",
    ttl = "5m",
    interval = "2m",
    stopStrategy = "timeBasedStop",
    stopAt = "#{task.estimatedEndTime}"
)
public void executeTask() {
    // 方法执行期间自动续期
}
```

---

## 4. 扩展点组合示例

### 示例 1：简单固定续期

```java
RenewalTask task = RenewalTask.builder()
    .keySelector(new StaticKeySelector(List.of("key1", "key2")))
    .ttlStrategy(new FixedTtlStrategy(Duration.ofMinutes(5)))
    .intervalStrategy(new FixedIntervalStrategy(Duration.ofMinutes(2)))
    .stopStrategy(new NeverStopCondition())
    .build();
```

### 示例 2：动态 Key + 指数退避

```java
RenewalTask task = RenewalTask.builder()
    .keySelector(new PatternKeySelector("deployment:*"))
    .ttlStrategy(new DynamicTtlStrategy(ctx -> 
        Duration.ofSeconds(300 + ctx.getRenewalCount() * 10)))
    .intervalStrategy(new ExponentialBackoffStrategy(
        Duration.ofSeconds(30), // 初始间隔
        Duration.ofMinutes(10), // 最大间隔
        2.0 // 退避因子
    ))
    .stopStrategy(new KeyNotExistsStopCondition())
    .build();
```

### 示例 3：复杂业务逻辑

```java
RenewalTask task = RenewalTask.builder()
    .keySelector(new FunctionKeySelector(ctx -> 
        deploymentService.getActiveDeploymentKeys()))
    .ttlStrategy(new ConditionalTtlStrategy(ctx -> {
        if (ctx.getRenewalCount() < 10) return Duration.ofMinutes(5);
        if (ctx.getRenewalCount() < 50) return Duration.ofMinutes(10);
        return Duration.ofMinutes(30);
    }))
    .intervalStrategy(new AdaptiveIntervalStrategy(0.5)) // TTL 的 50%
    .stopStrategy(CompositeStopCondition.anyOf(
        new TimeBasedStopCondition(estimatedEndTime),
        new ExternalSignalStopCondition(() -> deploymentCompleted),
        new CountBasedStopCondition(100)
    ))
    .filter(new ExistenceCheckFilter())
    .filter(new RateLimitFilter(1000, Duration.ofSeconds(1)))
    .failureHandler(new RetryFailureHandler(3, ExponentialBackoff))
    .listener(new LoggingLifecycleListener())
    .build();
```

---

## 5. 推荐采用的扩展点

### 5.1 必需扩展点（强烈建议）

| 扩展点 | 优先级 | 理由 |
|--------|--------|------|
| `RenewalStrategy` | P0 | 核心功能，决定 TTL |
| `RenewalIntervalStrategy` | P0 | 核心功能，决定续期频率 |
| `KeySelector` | P0 | 核心功能，决定续期对象 |
| `StopCondition` | P0 | 核心功能，决定何时停止 |

### 5.2 高价值扩展点（推荐）

| 扩展点 | 优先级 | 理由 |
|--------|--------|------|
| `FailureHandler` | P1 | 提高容错能力 |
| `RenewalLifecycleListener` | P1 | 提供可观测性 |
| `BatchStrategy` | P1 | 优化性能 |
| `RenewalFilter` | P2 | 提供拦截能力 |

### 5.3 可选扩展点（按需）

| 扩展点 | 优先级 | 理由 |
|--------|--------|------|
| `KeyGenerationStrategy` | P2 | 动态 Key 场景需要 |
| `RenewalScheduler` | P2 | 已有时间轮实现，抽象可选 |

---

## 6. 实现建议

### 6.1 接口设计原则

1. **单一职责**：每个扩展点只负责一个方面
2. **组合优于继承**：通过组合扩展点实现复杂逻辑
3. **合理默认值**：提供开箱即用的默认实现
4. **函数式接口**：支持 Lambda 表达式

### 6.2 预置实现建议

**第一阶段**（MVP）：
- ✅ `FixedTtlStrategy`
- ✅ `FixedIntervalStrategy`
- ✅ `StaticKeySelector`
- ✅ `NeverStopCondition`
- ✅ `TimeBasedStopCondition`

**第二阶段**（增强）：
- ✅ `DynamicTtlStrategy`
- ✅ `AdaptiveIntervalStrategy`
- ✅ `PatternKeySelector`
- ✅ `CountBasedStopCondition`
- ✅ `KeyNotExistsStopCondition`
- ✅ `RetryFailureHandler`

**第三阶段**（���善）：
- ✅ 其他高级扩展点

### 6.3 易用性建议

1. **提供建造者**：`RenewalTask.builder()`
2. **提供模板方法**：`RenewalTask.fixedRenewal()`
3. **提供流式 API**：`renewalService.create().withKeys()...`
4. **提供注解驱动**：`@RenewRedisKey`（Spring 环境）

---

## 7. 对比分析

### 7.1 扩展性 vs 易用性

| 方案 | 扩展性 | 易用性 | 推荐度 |
|------|--------|--------|--------|
| 只提供核心接口 | ⭐⭐⭐⭐⭐ | ⭐⭐ | ❌ 学习成本高 |
| 提供丰富预置实现 | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ✅ 平衡最好 |
| 只提供模板方法 | ⭐⭐ | ⭐⭐⭐⭐⭐ | ❌ 不够灵活 |

### 7.2 接口数量权衡

| 扩展点数量 | 优势 | 劣势 |
|-----------|------|------|
| 少（1-3 个） | 简单易懂 | 扩展受限 |
| 中（4-6 个） | 平衡好 ✅ | 需要文档 |
| 多（7+ 个） | 极度灵活 | 学习成本高 |

**建议**：采用 4-6 个核心扩展点 + 2-3 个高级扩展点

---

## 8. 最终决策（已确认）✅

### 8.1 实施策略

**原则**：
- ✅ **所有扩展点都采用**（保证架构完整性）
- ✅ **高频扩展点提供丰富预置实现**（保证易用性）
- ✅ **中低频扩展点仅提供接口 + 最小实现**（保证可运行性，降低维护成本）

### 8.2 扩展点实施清单

| 扩展点 | 使用频率 | 预置实现策略 | 实施内容 |
|--------|----------|-------------|----------|
| **RenewalStrategy** | 🔴 高 | 丰富实现 | ✅ 5 种预置实现 |
| **RenewalIntervalStrategy** | 🔴 高 | 丰富实现 | ✅ 4 种预置实现 |
| **KeySelector** | 🔴 高 | 丰富实现 | ✅ 5 种预置实现 |
| **StopCondition** | 🔴 高 | 丰富实现 | ✅ 6 种预置实现 |
| **FailureHandler** | 🟡 中 | 最小实现 | ⚠️ 接口 + 1 种默认实现 |
| **RenewalLifecycleListener** | 🟡 中 | 最小实现 | ⚠️ 接口 + 1 种默认实现 |
| **RenewalFilter** | 🟡 中 | 最小实现 | ⚠️ 接口 + 1 种默认实现 |
| **BatchStrategy** | 🟢 低 | 最小实现 | ⚠️ 接口 + 1 种默认实现 |
| **KeyGenerationStrategy** | 🟢 低 | 最小实现 | ⚠️ 接口 + 1 种默认实现 |
| **RenewalScheduler** | 🟢 低 | 最小实现 | ⚠️ 接口 + 1 种默认实现 |

**图例**：
- 🔴 高频：提供 4-6 种预置实现
- 🟡 中频：提供 1 种默认实现（NoOp 或最简单的功能实现）
- 🟢 低频：提供 1 种默认实现

---

## 9. 详细实施计划

### 9.1 高频扩展点（丰富实现）

#### 1. RenewalStrategy（续期策略）✅

**预置实现**：
```java
✅ FixedTtlStrategy                // 固定 TTL，永久续期
✅ DynamicTtlStrategy               // 函数式动态计算 TTL
✅ UntilTimeStrategy                // 续期至指定时间
✅ MaxRenewalsStrategy              // 最多续期 N 次
✅ ConditionalTtlStrategy           // 基于条件判断 TTL
```

**使用示例**：
```java
// 固定 TTL
new FixedTtlStrategy(Duration.ofMinutes(5))

// 动态 TTL（Lambda）
new DynamicTtlStrategy(ctx -> Duration.ofSeconds(300 + ctx.getRenewalCount() * 10))

// 续期至指定时间
new UntilTimeStrategy(endTime, Duration.ofMinutes(5))
```

---

#### 2. RenewalIntervalStrategy（续期间隔策略）✅

**预置实现**：
```java
✅ FixedIntervalStrategy            // 固定间隔
✅ ExponentialBackoffStrategy       // 指数退避
✅ AdaptiveIntervalStrategy         // 根据 TTL 自适应（TTL * ratio）
✅ RandomizedIntervalStrategy       // 添加随机抖动
```

**使用示例**：
```java
// 固定间隔
new FixedIntervalStrategy(Duration.ofMinutes(2))

// 指数退避
new ExponentialBackoffStrategy(
    Duration.ofSeconds(30),  // 初始
    Duration.ofMinutes(10),  // 最大
    2.0                      // 因子
)

// 自适应（TTL 的 50%）
new AdaptiveIntervalStrategy(0.5)
```

---

#### 3. KeySelector（Key 选择器）✅

**预置实现**：
```java
✅ StaticKeySelector                // 固定 Key 列表
✅ PatternKeySelector               // 模式匹配（SCAN）
✅ PrefixKeySelector                // 前缀扫描
✅ FunctionKeySelector              // 函数式选择
✅ CompositeKeySelector             // 组合选择器
```

**使用示例**：
```java
// 静态列表
new StaticKeySelector(List.of("key1", "key2"))

// 模式匹配
new PatternKeySelector("deployment:*", redisClient)

// 函数式
new FunctionKeySelector(ctx -> service.getActiveKeys())
```

---

#### 4. StopCondition（停止条件）✅

**预置实现**：
```java
✅ NeverStopCondition                // 永不停止
✅ TimeBasedStopCondition            // 时间停止
✅ CountBasedStopCondition           // 次数停止
✅ KeyNotExistsStopCondition         // Key 不存在停止
✅ ExternalSignalStopCondition       // 外部信号停止
✅ CompositeStopCondition            // 组合条件（AND/OR）
```

**使用示例**：
```java
// 时间停止
new TimeBasedStopCondition(Instant.now().plus(Duration.ofHours(2)))

// 组合条件（任一满足）
CompositeStopCondition.anyOf(
    new TimeBasedStopCondition(endTime),
    new CountBasedStopCondition(100)
)
```

---

### 9.2 中低频扩展点（最小实现）

#### 5. FailureHandler（失败处理器）⚠️

**最小实现**：
```java
✅ LogAndContinueFailureHandler      // 记录日志并继续（默认）
```

**接口保留**：用户可自行实现 `RetryFailureHandler`、`AlertFailureHandler` 等

**默认行为**：
```java
public class LogAndContinueFailureHandler implements FailureHandler {
    @Override
    public FailureHandleResult handle(String taskId, Collection<String> keys, Throwable error) {
        log.error("续期失败: taskId={}, keys={}, error={}", taskId, keys.size(), error.getMessage());
        return FailureHandleResult.continueRenewal(); // 继续执行
    }
}
```

---

#### 6. RenewalLifecycleListener（生命周期监听器）⚠️

**最小实现**：
```java
✅ NoOpLifecycleListener             // 空实现（默认）
```

**接口保留**：用户可自行实现监控、事件发布等

**默认行为**：
```java
public class NoOpLifecycleListener implements RenewalLifecycleListener {
    @Override public void onTaskRegistered(String taskId, RenewalTask task) {}
    @Override public void beforeRenewal(String taskId, Collection<String> keys) {}
    @Override public void afterRenewal(String taskId, RenewalResult result) {}
    @Override public void onTaskCompleted(String taskId, CompletionReason reason) {}
    @Override public void onTaskFailed(String taskId, Throwable error) {}
}
```

---

#### 7. RenewalFilter（续期过滤器）⚠️

**最小实现**：
```java
✅ PassThroughFilter                 // 直通过滤器（默认）
```

**接口保留**：用户可自行实现存在性检查、限流等

**默认行为**：
```java
public class PassThroughFilter implements RenewalFilter {
    @Override
    public Collection<String> beforeRenewal(Collection<String> keys, long ttl) {
        return keys; // 不做任何过滤
    }
    
    @Override
    public void afterRenewal(Map<String, Boolean> results) {
        // 不做任何处理
    }
}
```

---

#### 8. BatchStrategy（批量策略）⚠️

**最小实现**：
```java
✅ FixedSizeBatchStrategy            // 固定批次大小（默认 100）
```

**接口保留**：用户可自行实现动态批次、优先级分批等

**默认行为**：
```java
public class FixedSizeBatchStrategy implements BatchStrategy {
    private final int batchSize;
    
    public FixedSizeBatchStrategy() {
        this(100); // 默认批次大小
    }
    
    @Override
    public List<List<String>> batch(Collection<String> keys) {
        // 简单分批逻辑
    }
}
```

---

#### 9. KeyGenerationStrategy（Key 生成策略）⚠️

**最小实现**：
```java
✅ PlaceholderKeyGenerator           // 占位符替换（{var}）
```

**接口保留**：用户可自行实现 SpEL、复杂模板等

**默认行为**：
```java
public class PlaceholderKeyGenerator implements KeyGenerationStrategy {
    @Override
    public String generateKey(String template, Map<String, Object> context) {
        String result = template;
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result;
    }
}
```

---

#### 10. RenewalScheduler（调度器抽象）⚠️

**最小实现**：
```java
✅ TimeWheelScheduler                // 基于时间轮（默认且唯一）
```

**说明**：调度器是核心实现，提供时间轮版本即可，不需要额外抽象

---

## 10. 实施优先级

### Phase 1: 核心接口 + 高频实现（3 天）

**任务**：
- [ ] 定义所有 10 个扩展点接口
- [ ] 实现 RenewalStrategy（5 种）
- [ ] 实现 RenewalIntervalStrategy（4 种）
- [ ] 实现 KeySelector（5 种）
- [ ] 实现 StopCondition（6 种）
- [ ] 单元测试

**交付**：20 种高频预置实现 + 完整接口定义

---

### Phase 2: 中低频最小实现（1 天）

**任务**：
- [ ] 实现 LogAndContinueFailureHandler
- [ ] 实现 NoOpLifecycleListener
- [ ] 实现 PassThroughFilter
- [ ] 实现 FixedSizeBatchStrategy
- [ ] 实现 PlaceholderKeyGenerator
- [ ] 实现 TimeWheelScheduler
- [ ] 单元测试

**交付**：6 种最小默认实现

---

### Phase 3: 易用性增强（1 天）

**任务**：
- [ ] RenewalTask.builder()
- [ ] 流式 API
- [ ] 预设模板方法
- [ ] 完整示例代码
- [ ] 文档

---

## 11. 用户扩展指南

### 11.1 扩展中低频功能

用户可以基于接口实现自己的策略，例如：

```java
// 自定义失败处理器：失败重试 3 次
public class RetryFailureHandler implements FailureHandler {
    private final int maxRetries;
    
    @Override
    public FailureHandleResult handle(String taskId, Collection<String> keys, Throwable error) {
        if (retryCount < maxRetries) {
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

### 11.2 扩展点组合

```java
// 高频功能：使用预置实现
RenewalTask task = RenewalTask.builder()
    .keySelector(new PatternKeySelector("task:*"))           // 预置
    .ttlStrategy(new FixedTtlStrategy(Duration.ofMinutes(5))) // 预置
    .intervalStrategy(new AdaptiveIntervalStrategy(0.5))      // 预置
    .stopStrategy(new TimeBasedStopCondition(endTime))       // 预置
    
    // 中低频功能：使用默认或自定义
    .failureHandler(new RetryFailureHandler(3))              // 自定义
    .listener(new MetricsLifecycleListener())                // 自定义
    .filter(new ExistenceCheckFilter())                      // 自定义
    .build();
```

---

## 12. 优势总结

### 12.1 架构完整性 ✅
- 所有扩展点都有接口定义
- 职责清晰，边界明确
- 易于理解和扩展

### 12.2 实施成本可控 ✅
- 高频功能：丰富实现（20 种）
- 中低频功能：最小实现（6 种）
- 总计：26 种预置实现（相比全功能减少 50%+ 工作量）

### 12.3 用户体验优秀 ✅
- 高频场景开箱即用
- 中低频场景易于扩展
- 学习曲线平滑

### 12.4 维护成本低 ✅
- 只维护高频实现
- 中低频功能由用户按需扩展
- 代码量可控

---

## 13. 最终实施清单

| 类别 | 数量 | 内容 |
|------|------|------|
| 扩展点接口 | 10 个 | 全部定义 |
| 高频预置实现 | 20 种 | RenewalStrategy(5) + IntervalStrategy(4) + KeySelector(5) + StopCondition(6) |
| 中低频最小实现 | 6 种 | FailureHandler(1) + Lifecycle(1) + Filter(1) + Batch(1) + KeyGen(1) + Scheduler(1) |
| **总计** | **26 种实现** | **接口 10 个 + 实现 26 种** |

---

**决策已确认，准备进入实施阶段！** ✅

---

## 14. 文档支持

### 14.1 文档结构

```
docs/
├── design/
│   └── redis-renewal-service.md          ← 设计文档（技术决策、架构设计）
├── redis-renewal-service-api.md          ← API 文档（用户使用指南）
└── temp/
    ├── task-018-redis-renewal-service-design.md  ← 实施方案
    └── task-018-extension-points-design.md       ← 扩展点设计清单
```

### 14.2 设计文档内容

**位置**：`docs/design/redis-renewal-service.md`

**内容**：
- 概述和适用场景
- 架构设计（分层架构、模块化设计）
- 关键设计决策（IO 阻塞解决方案、客户端抽象、扩展点策略）
- 核心组件详解
- 性能指标和优化
- 监控与可观测性
- 配置设计
- 测试策略
- 部署建议
- 未来扩展
- 参考资料

**目标读者**：架构师、高级开发人员

### 14.3 API 文档内容

**位置**：`docs/redis-renewal-service-api.md`

**内容**：
1. **快速开始**
   - 添加依赖
   - 基础配置
   - 第一个续期任务

2. **核心概念**
   - 核心组件说明
   - 扩展点分类表格

3. **扩展点使用指南**
   - 每个扩展点的详细说明
   - 预置实现介绍
   - 使用示例代码
   - 适用场景说明

4. **完整示例**
   - 简单部署任务续期
   - 动态 Key + 自适应间隔
   - 完整功能示例
   - 使用预设模板

5. **配置参考**
   - 完整配置示例
   - 配置项说明表格

6. **FAQ**
   - 常见问题解答
   - 性能说明
   - 测试示例

**目标读者**：应用开发人员、运维人员

### 14.4 文档维护计划

| 阶段 | 文档任务 | 时间 |
|------|----------|------|
| Phase 1 | 完成设计文档初稿 | 与接口定义同步 |
| Phase 2 | 更新 API 文档（高频扩展点） | 实现完成后 |
| Phase 3 | 补充 API 文档（中低频扩展点） | 实现完成后 |
| Phase 4 | 添加完整示例代码 | 集成测试完成后 |
| Phase 5 | 添加 FAQ 和常见问题 | 测试阶段 |
| Phase 6 | 最终审查和发布 | 上线前 |

### 14.5 文档质量标准

- ✅ 所有公开接口都有 JavaDoc
- ✅ 每个扩展点都有使用示例
- ✅ 提供至少 3 个完整的端到端示例
- ✅ 配置项都有详细说明和默认值
- ✅ FAQ 覆盖常见使用场景
- ✅ 设计文档包含架构图和决策理由

### 14.6 文档生成

**JavaDoc 生成**：
```bash
mvn javadoc:javadoc
```

**Markdown 转 HTML**（可选）：
```bash
# 使用 mkdocs 或其他工具
mkdocs build
```

**文档发布**：
- 内部：Wiki 或 Confluence
- 外部：GitHub Pages 或专用文档站点

---

**文档准备完成，可以开始实施！** 📚✅

