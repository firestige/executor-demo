# T-018 Redis 续期服务实施方案

> **任务 ID**: T-018  
> **预计工期**: 8.5 天  
> **状态**: 待实施  
> **创建时间**: 2025-11-24

---

## 📋 实施阶段总览

| 阶段 | 名称 | 工期 | 交付物 | 依赖 |
|------|------|------|--------|------|
| Phase 1 | 核心接口与模型 | 1 天 | 10 个接口 + 2 个模型 | 无 |
| Phase 2 | Redis 客户端实现 | 0.5 天 | Spring 客户端实现 + SPI 后备机制 | Phase 1 |
| Phase 3 | 异步执行器 | 0.5 天 | AsyncRenewalExecutor | Phase 1, 2 |
| Phase 4 | 时间轮核心实现 | 2 天 | TimeWheelRenewalService | Phase 1, 2, 3 |
| Phase 5 | 高频扩展点实现 | 3 天 | 20 种预置实现 | Phase 1 |
| Phase 6 | 中低频扩展点实现 | 1 天 | 6 种最小实现 | Phase 1 |
| Phase 7 | 易用性增强 | 1 天 | Builder + 流式 API + 模板 | Phase 4, 5, 6 |
| Phase 8 | 监控与可观测性 | 1 天 | 指标收集 + 健康检查 + 报告 | Phase 4 |
| Phase 9 | Spring Boot 集成 | 0.5 天 | AutoConfiguration + Properties | All |
| Phase 10 | 文档与测试 | 1 天 | 完整文档 + 性能测试 | All |

**总工期**: 10.5 天（优化后 8.5 天并行执行）

---

## Phase 1: 核心接口与模型 ✅

### 任务目标
定义续期服务的核心抽象，建立清晰的接口边界。

### 具体任务

#### 1.1 服务接口
- [ ] `KeyRenewalService` - 续期服务主接口
  ```java
  String register(RenewalTask task);
  void cancel(String taskId);
  void pause(String taskId);
  void resume(String taskId);
  RenewalTaskStatus getStatus(String taskId);
  Collection<RenewalTask> getAllTasks();
  ```

#### 1.2 扩展点接口（10 个）
- [ ] `RenewalStrategy` - 决定 TTL 和是否继续
- [ ] `RenewalIntervalStrategy` - 决定续期间隔
- [ ] `KeySelector` - 选择需要续期的 Key
- [ ] `StopCondition` - 判断何时停止
- [ ] `RedisClient` ⭐ - Redis 客户端抽象
- [ ] `FailureHandler` - 失败处理
- [ ] `RenewalLifecycleListener` - 生命周期监听
- [ ] `RenewalFilter` - 续期拦截器
- [ ] `BatchStrategy` - 批量策略
- [ ] `KeyGenerationStrategy` - Key 生成策略

#### 1.3 数据模型
- [ ] `RenewalTask` - 续期任务模型
- [ ] `RenewalContext` - 续期上下文
- [ ] `RenewalResult` - 续期结果
- [ ] `RenewalTaskStatus` - 任务状态
- [ ] `ValidationResult` - 验证结果（可选）

#### 1.4 测试
- [ ] 接口定义的单元测试（Mock 实现）
- [ ] 模型类的单元测试

### 验收标准
- ✅ 所有接口都有 JavaDoc
- ✅ 接口职责清晰，边界明确
- ✅ 测试覆盖率 > 90%

### 交付物
- `xyz.firestige.infrastructure.redis.renewal.api` 包
- 10 个接口定义
- 4 个数据模型类
- 单元测试

---

## Phase 2: Redis 客户端实现 ✅

### 任务目标
实现 Spring Data Redis 客户端适配器，提供 SPI 后备加载机制。

### 具体任务

#### 2.1 Spring Data Redis 实现（核心）
- [ ] `SpringRedisClient` 实现 `RedisClient` 接口
  - [ ] `expire(key, ttl)` - 单个 Key 续期
  - [ ] `batchExpire(keys, ttl)` - 批量续期（Pipeline）
  - [ ] `expireAsync(key, ttl)` - 异步续期
  - [ ] `batchExpireAsync(keys, ttl)` - 批量异步续期
  - [ ] `scan(pattern, count)` - Key 扫描（使用 SCAN 命令）
  - [ ] `exists(key)` - 检查存在性
  - [ ] `ttl(key)` - 获取剩余 TTL

#### 2.2 SPI 后备加载机制（可选）
- [ ] `RedisClientProvider` 接口（定义）
- [ ] `RedisClientLoader` SPI 加载器（简化版）
- [ ] `META-INF/services` 配置文件（预留）
- [ ] `SpringRedisClientProvider` 实现

**说明**：
- SPI 机制作为后备方案，用于非 Spring 环境
- 第一版本优先完成 Spring 实现
- SPI 扩展在未来版本中完善

#### 2.3 测试
- [ ] Spring Data Redis 实现的单元测试
- [ ] Pipeline 批量操作测试
- [ ] 异步操作测试
- [ ] 集成测试（使用 TestContainers Redis）

### 验收标准
- ✅ Spring 实现通过所有测试
- ✅ 批量操作使用 Pipeline
- ✅ 异步操作不阻塞
- ✅ 测试覆盖率 > 90%

### 交付物
- `xyz.firestige.infrastructure.redis.renewal.client.spring` 包
- `SpringRedisClient` 实现
- SPI 接口定义（预留扩展）
- 单元测试 + 集成测试

**注**：Jedis/Lettuce 实现留待后续版本

---

## Phase 3: 异步执行器 ⭐

### 任务目标
实现异步执行器，解决时间轮 IO 阻塞问题。

### 具体任务

#### 3.1 AsyncRenewalExecutor
- [ ] 创建有界队列的线程池
  ```java
  ThreadPoolExecutor(
      threadPoolSize, threadPoolSize,
      60L, TimeUnit.SECONDS,
      new ArrayBlockingQueue<>(1000),
      new CallerRunsPolicy()
  );
  ```
- [ ] `submitRenewal(taskId, keys, ttl)` - 提交续期任务
- [ ] `performRenewal(taskId, keys, ttl)` - 执行续期（批量）
- [ ] 结果回调处理（`CompletableFuture`）
- [ ] 线程池监控（队列使用率、活跃线程数）

#### 3.2 容错机制
- [ ] 队列满时降级策略（CallerRunsPolicy）
- [ ] 异常隔离（不传播到时间轮）
- [ ] 超时控制

#### 3.3 测试
- [ ] 单线程顺序执行测试
- [ ] 多线程并发执行测试
- [ ] 队列满场景测试
- [ ] 异常处理测试

### 验收标准
- ✅ 执行器不阻塞时间轮
- ✅ 队列满时不丢失任务
- ✅ 异常不影响其他任务
- ✅ 测试覆盖率 > 90%

### 交付物
- `AsyncRenewalExecutor` 类
- 线程池配置
- 单元测试 + 集成测试

---

## Phase 4: 时间轮核心实现 ⭐⭐

### 任务目标
实现基于 Netty HashedWheelTimer 的续期服务核心。

### 具体任务

#### 4.1 TimeWheelRenewalService
- [ ] 创建 HashedWheelTimer（100ms tick, 512 slots）
- [ ] 实现 `register(task)` - 注册续期任务
- [ ] 实现 `scheduleRenewal(wrapper)` - 调度续期
- [ ] 实现 `handleRenewalTick(wrapper)` ⭐ - 处理 tick（快速返回）
  - 计算 TTL（调用 Strategy）
  - 选择 Key（调用 Selector）
  - 提交到异步执行器（不等待）
  - 立即调度下次续期
- [ ] 实现 `cancel(taskId)` - 取消任务
- [ ] 实现 `pause(taskId)` - 暂停任务
- [ ] 实现 `resume(taskId)` - 恢复任务

#### 4.2 任务管理
- [ ] `RenewalTaskWrapper` - 任务包装器
  - taskId
  - task
  - timeout (Netty Timeout)
  - context
  - failureCount
  - paused flag
- [ ] 任务状态管理（`ConcurrentHashMap`）
- [ ] 任务完成后自动清理

#### 4.3 异常处理与重试
- [ ] 续期失败不影响调度
- [ ] 失败计数与最大重试
- [ ] 异常日志记录

#### 4.4 测试
- [ ] 单任务续期测试
- [ ] 多任务并发测试
- [ ] 暂停/恢复测试
- [ ] 取消测试
- [ ] 失败重试测试
- [ ] 长时间运行稳定性测试

### 验收标准
- ✅ 时间轮精度 ±50ms
- ✅ 支持 1000+ 并发任务
- ✅ IO 不阻塞时间轮
- ✅ 测试覆盖率 > 85%

### 交付物
- `TimeWheelRenewalService` 核心实现
- `RenewalTaskWrapper` 任务包装器
- 完整的单元测试和集成测试

---

## Phase 5: 高频扩展点实现 ✅

### 任务目标
实现 4 个高频扩展点的所有预置实现（20 种）。

### 具体任务

#### 5.1 RenewalStrategy（5 种）
- [ ] `FixedTtlStrategy` - 固定 TTL
- [ ] `DynamicTtlStrategy` - 函数式动态 TTL
- [ ] `UntilTimeStrategy` - 续期至指定时间
- [ ] `MaxRenewalsStrategy` - 最多续期 N 次
- [ ] `ConditionalTtlStrategy` - 条件判断 TTL

#### 5.2 RenewalIntervalStrategy（4 种）
- [ ] `FixedIntervalStrategy` - 固定间隔
- [ ] `ExponentialBackoffStrategy` - 指数退避
- [ ] `AdaptiveIntervalStrategy` - 自适应（TTL * ratio）
- [ ] `RandomizedIntervalStrategy` - 随机抖动

#### 5.3 KeySelector（5 种）
- [ ] `StaticKeySelector` - 固定列表
- [ ] `PatternKeySelector` - 模式匹配（SCAN）
- [ ] `PrefixKeySelector` - 前缀扫描
- [ ] `FunctionKeySelector` - 函数式选择
- [ ] `CompositeKeySelector` - 组合选择器

#### 5.4 StopCondition（6 种）
- [ ] `NeverStopCondition` - 永不停止
- [ ] `TimeBasedStopCondition` - 时间停止
- [ ] `CountBasedStopCondition` - 次数停止
- [ ] `KeyNotExistsStopCondition` - Key 不存在停止
- [ ] `ExternalSignalStopCondition` - 外部信号停止
- [ ] `CompositeStopCondition` - 组合条件（AND/OR）

#### 5.5 测试
- [ ] 每个实现的单元测试
- [ ] 组合使用的集成测试

### 验收标准
- ✅ 所有实现有完整 JavaDoc
- ✅ 所有实现有使用示例
- ✅ 测试覆盖率 > 90%

### 交付物
- 20 种预置实现类
- 完整的单元测试

---

## Phase 6: 中低频扩展点实现 ✅

### 任务目标
实现 6 个中低频扩展点的最小默认实现。

### 具体任务

#### 6.1 FailureHandler（1 种）
- [ ] `LogAndContinueFailureHandler` - 记录日志并继续

#### 6.2 RenewalLifecycleListener（1 种）
- [ ] `NoOpLifecycleListener` - 空实现

#### 6.3 RenewalFilter（1 秷）
- [ ] `PassThroughFilter` - 直通过滤器

#### 6.4 BatchStrategy（1 种）
- [ ] `FixedSizeBatchStrategy` - 固定批次大小（默认 100）

#### 6.5 KeyGenerationStrategy（1 种）
- [ ] `PlaceholderKeyGenerator` - 占位符替换（{var}）

#### 6.6 RenewalScheduler（1 种）
- [ ] `TimeWheelScheduler` - 已在 Phase 4 实现

#### 6.7 测试
- [ ] 每个最小实现的单元测试

### 验收标准
- ✅ 所有默认实现保证模块可运行
- ✅ 接口完整，易于用户扩展
- ✅ 测试覆盖率 > 80%

### 交付物
- 6 种最小默认实现
- 单元测试

---

## Phase 7: 易用性增强 ✅

### 任务目标
提供开箱即用的 API，降低使用门槛。

### 具体任务

#### 7.1 Builder 模式
- [ ] `RenewalTask.builder()` - 建造者模式
  ```java
  RenewalTask.builder()
      .keySelector(...)
      .ttlStrategy(...)
      .intervalStrategy(...)
      .stopStrategy(...)
      .build();
  ```

#### 7.2 流式 API
- [ ] `KeyRenewalService.create()` - 流式 API
  ```java
  renewalService.create()
      .withKeys("key1", "key2")
      .fixedTtl(Duration.ofMinutes(5))
      .fixedInterval(Duration.ofMinutes(2))
      .stopAt(endTime)
      .register();
  ```

#### 7.3 预设模板方法
- [ ] `RenewalTask.fixedRenewal(keys, ttl, interval)`
- [ ] `RenewalTask.untilTime(keys, ttl, endTime)`
- [ ] `RenewalTask.maxRenewals(keys, ttl, maxCount)`

#### 7.4 完整示例代码
- [ ] 简单部署任务续期示例
- [ ] 动态 Key + 自适应间隔示例
- [ ] 完整功能组合示例

#### 7.5 测试
- [ ] Builder 模式测试
- [ ] 流式 API 测试
- [ ] 模板方法测试

### 验收标准
- ✅ API 简洁易用
- ✅ 至少 3 个完整示例
- ✅ 测试覆盖率 > 85%

### 交付物
- Builder 和流式 API
- 模板方法
- 完整示例代码

---

## Phase 8: 监控与可观测性 ✅

### 任务目标
提供完整的监控指标和健康检查能力。

### 具体任务

#### 8.1 指标收集器
- [ ] `RenewalMetricsCollector` - 指标收集
  - totalRenewals - 总续期次数
  - successCount - 成功 Key 数
  - failureCount - 失败 Key 数
  - taskFailures - 任务失败次数
  - successRate - 成功率
  - lastRenewalTime - 最后续期时间

#### 8.2 定时指标报告
- [ ] `RenewalMetricsReporter` - 每分钟打印指标
  - 活跃任务数
  - 续期成功率
  - 失败统计
  - 格式化输出

#### 8.3 健康检查
- [ ] `RenewalHealthIndicator` - Spring Actuator 健康检查
  - 检查活跃任务数
  - 检查续期成功率
  - 返回 UP / WARNING / DOWN

#### 8.4 测试
- [ ] 指标收集准确性测试
- [ ] 定时报告测试
- [ ] 健康检查端点测试

### 验收标准
- ✅ 指标准确完整
- ✅ 定时报告格式清晰
- ✅ 健康检查集成正常
- ✅ 测试覆盖率 > 85%

### 交付物
- 指标收集器
- 定时报告组件
- 健康检查端点

---

## Phase 9: Spring Boot 集成 ✅

### 任务目标
实现 Spring Boot 自动配置，开箱即用。

### 具体任务

#### 9.1 配置属性
- [ ] `RedisRenewalProperties` - 配置类
  ```yaml
  redis.renewal:
    enabled: true
    type: time-wheel
    time-wheel:
      tick-duration: 100
      ticks-per-wheel: 512
    executor-thread-pool-size: 4
    metrics-report-interval: 60
    default-renewal-interval: 30
    default-ttl: 300
  ```

#### 9.2 自动配置
- [ ] `RedisRenewalAutoConfiguration` - 自动装配
  - 注册 `RedisClient` Bean
  - 注册 `KeyRenewalService` Bean
  - 注册 `RenewalMetricsCollector` Bean
  - 注册 `RenewalMetricsReporter` Bean
  - 注册 `RenewalHealthIndicator` Bean

#### 9.3 SPI 注册
- [ ] 添加到 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

#### 9.4 配置示例
- [ ] 完整的 application.yml 示例
- [ ] 多环境配置示例

#### 9.5 测试
- [ ] 自动配置加载测试
- [ ] 配置属性绑定测试
- [ ] 条件装配测试

### 验收标准
- ✅ 零配置启动
- ✅ 配置属性完整
- ✅ 自动装配正常
- ✅ 测试覆盖率 > 90%

### 交付物
- AutoConfiguration 类
- Properties 类
- 配置示例

---

## Phase 10: 文档与测试 ✅

### 任务目标
完善文档，进行性能测试和压力测试。

### 具体任务

#### 10.1 文档
- [x] 设计文档（已完成 ✅ - `docs/design/redis-renewal-service.md`）
- [x] API 文档（已完成 ✅ - `docs/redis-renewal-service-api.md`）
- [ ] JavaDoc 完善
- [ ] README.md 更新
- [ ] CHANGELOG.md 创建
- [ ] 扩展指引文档占位（Jedis / Lettuce / SPI 扩展指南，最终单独文档，不写入实施方案）

#### 10.2 性能测试
- [ ] 单任务续期延迟测试（目标 < 100ms）
- [ ] 1000 并发任务测试（CPU < 5%, Memory < 100MB）
- [ ] IO 阻塞场景测试（时间轮精度验证）
- [ ] 长时间运行稳定性测试（24 小时）

#### 10.3 压力测试
- [ ] 极限并发数测试
- [ ] Redis 连接池耗尽场景
- [ ] 网络延迟模拟（100ms, 500ms, 1000ms）
- [ ] 线程池满载场景

#### 10.4 集成测试
- [ ] 真实 Redis 集成测试
- [ ] Spring Boot 应用集成测试
- [ ] 多种 Redis 客户端测试

#### 10.5 扩展指引文档登记
- 计划在文档交付阶段新增：`docs/redis-renewal-extension-guide.md`
- 内容范围（仅登记，不在本方案展开）：
  * 客户端扩展流程（Jedis / Lettuce）
  * SPI 加载机制示例
  * 测试与注意事项清单
  * 与 Spring AutoConfiguration 的协作模式
- 验收：文件存在且引用于 README 与 API 文档末尾链接

---

## 📊 里程碑

| 里程碑 | 完成标志 | 预计完成日期 |
|--------|----------|-------------|
| M1: 核心框架完成 | Phase 1-4 完成 | D+4 |
| M2: 扩展点实现完成 | Phase 5-6 完成 | D+7 |
| M3: 易用性完成 | Phase 7-8 完成 | D+9 |
| M4: Spring 集成完成 | Phase 9 完成 | D+9.5 |
| M5: 全部完成 | Phase 10 完成 | D+10.5 |

---

## 🎯 验收标准总览

### 功能性
- ✅ 支持注册/取消/暂停/恢复续期任务
- ✅ 支持 10 个扩展点，26 种预置实现
- ✅ 单线程支持 1000+ 并发任务
- ✅ 续期失败自动重试

### 性能
- ✅ 单任务续期延迟 < 100ms
- ✅ CPU 占用 < 5%（1000 任务）
- ✅ 内存占用 < 100MB（1000 任务）
- ✅ 时间轮精度 ±50ms

### 可观测性
- ✅ 每分钟打印指标报告
- ✅ 提供健康检查端点
- ✅ 异常日志完整

### 集成性
- ✅ 读取 Spring Boot Redis 配置
- ✅ 支持自定义实现替换
- ✅ 提供完整的配置示例

### 文档
- ✅ 设计文档完整
- ✅ API 文档完整
- ✅ JavaDoc 完整
- ✅ 至少 3 个端到端示例

### 测试
- ✅ 单元测试覆盖率 > 85%
- ✅ 集成测试覆盖主要场景
- ✅ 性能测试通过
- ✅ 压力测试通过

---

## 📝 风险与缓解

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|---------|
| Netty 依赖冲突 | 构建失败 | 低 | 明确版本，提供 BOM |
| 时间轮实现复杂 | 进度延迟 | 中 | Phase 4 预留 2 天，可调整 |
| 扩展点过多 | 学习成本高 | 中 | 提供易用性 API，文档详细 |
| 性能不达标 | 需优化 | 低 | 早期性能测试，及时调整 |

---

**实施方案已确认，可以开始执行！** 🚀
