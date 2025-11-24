# 架构设计总纲

> **最后更新**: 2025-11-23  
> **状态**: Active（基于已验证 4+1 视图重写）

---

## 1. 概述
`executor-demo` 是一个多租户配置切换执行模块（蓝绿/渐进/自定义阶段组合），聚焦于“执行机”与任务编排逻辑。它本身不是独立部署系统，而是可嵌入更大平台中的一个业务模块。模块内部遵循 DDD 战术模式 + 事件驱动，对外暴露统一 Facade，内部以 Plan / Task / Stage 为核心分层。

### 1.1 作用域（Scope）
- 不负责：全局部署编排、环境治理、全链路观测平台
- 负责：单次部署计划中的租户级任务编排、执行、恢复、回滚、重试、进度与事件发布
- 非目标：物理部署拓扑（由宿主系统决定）

### 1.2 核心模型
- Plan：部署计划（聚合根），管理任务 ID 列表与生命周期、并发阈值
- Task：租户级执行单元（聚合根），封装状态机、Stage 进度、重试/回滚/暂停语义
- Stage：原子执行阶段（不可切片），由多个 Step 组成
- Step：最小执行指令（Redis 写入 / HTTP 请求 / 健康检查 / Pub/Sub 广播）

---

## 2. 架构原则（Architecture Principles）
| 编号 | 原则 | 说明 |
|------|------|------|
| AP-01 | 聚合最小一致性边界 | Plan 与 Task 为独立聚合根，跨聚合仅通过 ID 引用（RF-07）|
| AP-02 | 充血模型优先 | 业务状态转换与不变式保护封装在聚合方法中（RF-06/13）|
| AP-03 | 分层 + 依赖倒置 | Facade → Application → Domain ← Infrastructure；Domain 不依赖外层 |
| AP-04 | 事件驱动演进 | 状态变化、阶段进度均生成领域事件（RF-11/19）|
| AP-05 | 显式错误与恢复 | FailureInfo + Checkpoint 支持失败诊断与断点续传（RF-19）|
| AP-06 | 协作式控制 | 暂停/取消仅在 Stage 边界响应，避免中间状态污染 |
| AP-07 | 幂等与可重复 | 重试从 Checkpoint 恢复，补偿进度事件保持事件序列连续 |
| AP-08 | 低成本前置验证 | StateTransitionService 先内存校验，再调用高成本领域服务（RF-18）|
| AP-09 | 可组合阶段 | StageFactory 允许通过配置组合不同服务阶段（RF-13/扩展）|
| AP-10 | 简化仓储接口 | Repository 只暴露聚合完整生命周期方法（RF-09）|

---

## 3. 战术设计与演进里程碑
| RF 编号 | 演进摘要 | 对应决策 |
|---------|----------|-----------|
| RF-06 | 贫血模型 → 充血聚合 | 引入业务行为方法、状态守卫 |
| RF-07 | 修正聚合边界 | Plan 仅持有 TaskId 列表，Task 反向引用 PlanId |
| RF-08/13 | 值对象与策略扩展 | 引入 TaskId/PlanId/TenantId/DeployVersion 等 VO，Stage/Step 可扩展 |
| RF-09 | 仓储简化 | CRUD → save/remove/find + 语义化查询 |
| RF-11 | 领域事件内聚 | 聚合收集事件，应用层统一发布 |
| RF-18 | 状态转换优化 | 前置内存校验 + 后置聚合行为 + 事件驱动 |
| RF-19 | Checkpoint / Stage 事件增强 | 精细化 StageStarted/Completed/Failed；恢复补偿进度 |
| RF-20 | 编排层拆分 | 引入 TaskExecutionOrchestrator 分离执行调度与业务逻辑 |
| **T-016** | **投影持久化与查询API** | CQRS + Event Sourcing；分布式锁；最小兜底查询 |
| **T-017** | **配置管理体系** | 完全解耦的配置加载；自动发现；零修改扩展；详见 [配置管理设计](design/configuration-management.md) |

---

## 4. 分层结构（High-Level Layering）

### 4.1 核心分层
- **Facade**：统一入口（DeploymentTaskFacade），参数校验 + DTO 转换
- **Application**：
  - 生命周期管理：PlanLifecycleService、TaskOperationService
  - 编排调度：TaskExecutionOrchestrator（线程池与并发控制）
  - 冲突协调：TenantConflictCoordinator（应用层冲突检测）
  - Checkpoint 管理：CheckpointService
  - **查询服务**（T-016）：TaskQueryService（最小兜底查询）
  - **投影更新**（T-016）：TaskStateProjectionUpdater、PlanStateProjectionUpdater（事件监听器）
- **Domain**：PlanAggregate、TaskAggregate、领域服务、值对象、状态枚举、事件层次
- **Infrastructure**：
  - 执行引擎：TaskExecutor / TaskStage / StageStep
  - 仓储实现：InMemory/Redis（Checkpoint、投影存储）
  - 冲突管理：TenantConflictManager（底层锁管理）
  - 心跳与指标：HeartbeatScheduler

### 4.2 T-016 新增组件（投影持久化与查询）
| 组件 | 层次 | 职责 |
|------|------|------|
| TaskQueryService | Application | 最小兜底查询（按租户查询、Plan 状态、Checkpoint 检查） |
| TaskStateProjectionUpdater | Application | 监听 Task 事件，更新 Task 状态投影到 Redis |
| PlanStateProjectionUpdater | Application | 监听 Plan 事件，更新 Plan 状态投影到 Redis |
| RedisTaskStateProjectionStore | Infrastructure | Task 状态投影 Redis 存储实现 |
| RedisPlanStateProjectionStore | Infrastructure | Plan 状态投影 Redis 存储实现 |
| RedisTenantTaskIndexStore | Infrastructure | 租户 → 任务映射索引 Redis 存储 |
| RedisTenantLockManager | Infrastructure | Redis 分布式租户锁实现 |

> 详见：分层与包结构视图：[development-view.puml](./views/development-view.puml) | 补充说明文档：[development-view.md](./views/development-view.md)

---

## 5. 领域模型摘要
- 双聚合：PlanAggregate / TaskAggregate 独立生命周期
- 事件层次：DomainEvent → PlanStatusEvent / TaskStatusEvent → TaskStageStatusEvent → 具体事件
- 值对象族：PlanId、TaskId、TenantId、DeployVersion、TimeRange、TaskCheckpoint、StageProgress、TaskDuration、FailureInfo、RetryPolicy 等
- 状态机：PlanStatus(11)、TaskStatus(13)；转换由 StateTransitionService 预验证 + 聚合方法执行

> 详见：领域模型视图：[logical-view.puml](./views/logical-view.puml) | 补充说明文档：[logical-view.md](./views/logical-view.md)

---

## 6. 生命周期与执行流程
- 执行主线：创建 Plan → 创建 Tasks → READY → RUNNING（任务并发受 maxConcurrency + 租户冲突约束） → 完成 / 部分失败 / 失败 / 回滚 / 取消
- Task 执行：TaskExecutionOrchestrator 分配执行 → TaskExecutor 依序执行 Stage → 保存阶段性 Checkpoint → 心跳与进度事件上报
- 重试：FAILED / PAUSED 状态 → 从 Checkpoint 恢复 → 跳过已完成 Stage
- 回滚：逆序执行已完成 Stage 的 rollback 操作（若实现）

> 详见：进程与执行视图：[process-view.puml](./views/process-view.puml) | 补充说明文档：[process-view.md](./views/process-view.md)

---

## 7. 并发与隔离策略
| 策略 | 说明 |
|------|------|
| 租户锁（两层架构） | **TenantConflictManager**（Infrastructure）：底层锁管理（内存/Redis）<br>**TenantConflictCoordinator**（Application）：应用层协调与冲突检测<br>同一租户在任意时刻仅一个 RUNNING/PAUSED 任务 |
| 全局并发 (maxConcurrency) | Plan 级并发额度控制任务提交速率 |
| 协作式暂停 | 仅在完成当前 Stage 后检查暂停标志 |
| 心跳与进度 | HeartbeatScheduler 每 10s 上报 TaskProgressEvent |

**租户锁架构说明**：
- `TenantConflictManager`：基础设施层，提供 tryAcquire/release/renew 等锁操作，支持内存和 Redis 两种实现
- `TenantConflictCoordinator`：应用层，封装冲突检测逻辑，协调 Plan 创建和 Task 执行的租户冲突检查
- 详见：[conflict-coordination.md](./design/conflict-coordination.md)

---

## 8. Checkpoint 与恢复机制

### 8.1 Checkpoint（断点续传）
| 要素 | 描述 |
|------|------|
| 存储 | RedisCheckpointRepository（JSON 序列化 + TTL 可配置），InMemoryCheckpointRepository（测试/回退）|
| 内容 | lastCompletedStageIndex、completedStages、contextData、savedAt |
| 保存时机 | Stage 成功、失败、暂停、异常中断 |
| 恢复策略 | 从 (lastCompletedStageIndex + 1) 开始执行；补偿一次进度事件 |
| 回滚交互 | 回滚不依赖 Checkpoint（按已完成列表逆序）|
| TTL 策略 | 默认 7 天，可配置（`executor.persistence.redis.ttl.checkpoint`）|

### 8.2 投影持久化（T-016：CQRS + Event Sourcing）
| 要素 | 描述 |
|------|------|
| **状态投影** | **事件驱动更新 Task/Plan 状态投影到 Redis，支持重启后查询** |
| 投影内容（Task） | taskId, tenantId, planId, status, pauseRequested, stageNames, lastCompletedStageIndex, createdAt, updatedAt |
| 投影内容（Plan） | planId, status, taskIds, progress, createdAt, updatedAt |
| 租户索引 | TenantId → TaskId 映射，支持按租户查询任务 |
| TTL 策略 | 默认 30 天，可配置（`executor.persistence.redis.ttl.projection`）|
| 更新机制 | 事件监听器（TaskStateProjectionUpdater、PlanStateProjectionUpdater）异步更新 |
| 一致性 | 最终一致性（毫秒级延迟），可接受 |

### 8.3 租户锁（T-016：分布式部署支持）
| 要素 | 描述 |
|------|------|
| **租户锁** | **Redis SET NX 分布式锁，TTL 2.5小时，防止多实例冲突** |
| 实现 | RedisTenantLockManager（原子获取/释放/续租/存在检查）|
| Fallback | InMemoryTenantLockManager（单实例场景）|
| TTL 策略 | 默认 9000 秒（2.5 小时），可配置（`executor.persistence.redis.ttl.lock`）|
| 续租机制 | 任务执行期间定期续租，防止锁过期 |

### 8.4 查询 API（T-016：最小兜底查询）
| 要素 | 描述 |
|------|------|
| **查询API** | **最小兜底查询：queryTasksByTenant、queryPlanStatus、hasCheckpoint** |
| 使用场景 | 仅用于重启后状态恢复，不建议常规调用 |
| 数据源 | Redis 投影存储（Task/Plan 状态投影、租户索引）|
| 性能 | 直接 Redis 读取，毫秒级响应 |
| 设计约束 | 避免演变为查询平台，保持最小化（3 个核心方法）|

> 详见：[persistence.md](./design/persistence.md)、[checkpoint-mechanism.md](./design/checkpoint-mechanism.md)、[task-016-final-implementation-report.md](./temp/task-016-final-implementation-report.md)

---

## 9. 事件模型（Event Model）
| 事件类别 | 示例 | 触发点 |
|----------|------|--------|
| Plan 状态 | PlanStartedEvent / PlanCompletedEvent | 聚合状态变更 |
| Task 状态 | TaskStartedEvent / TaskPausedEvent / TaskCompletedEvent | 聚合方法调用 |
| Task Stage 状态 | TaskStageStartedEvent / TaskStageCompletedEvent / TaskStageFailedEvent | Stage 执行前后 |
| 进度/心跳 | TaskProgressEvent | 心跳调度器周期触发 |
| 重试/回滚 | TaskRetryStartedEvent / TaskRolledBackEvent | 操作入口调用 |

> 发布流程详解：将在执行机设计文档中说明（待补充：[execution-engine.md](./design/execution-engine.md)）

### 9.1 事件监听与消费（Plan）
- 自监听机制：DomainEventPublisher(Spring) → @EventListener 监听器（进程内自消费）
- 监听器与职责：
  - PlanStartedListener：接收 PlanStartedEvent → 委托 PlanExecutionFacade.executePlan(planId)
  - PlanResumedListener：接收 PlanResumedEvent → PlanExecutionFacade.resumePlanExecution(planId)
  - PlanPausedListener：接收 PlanPausedEvent → 记录审计/通知；暂停在 TaskExecutor Stage 边界协作式生效
  - PlanCompletionListener：接收 PlanCompleted/Failed → 调用 PlanSchedulingStrategy.onPlanCompleted 清理冲突标记
- 与编排/执行的桥接：监听器仅做"事件到应用门面/策略"的委托，业务逻辑仍在 Facade/Orchestrator/Executor 中实现

### 9.2 事件监听与投影更新（T-016：CQRS + Event Sourcing）
- **投影更新机制**：
  - **TaskStateProjectionUpdater**：监听 Task 相关事件（TaskCreated、TaskStarted、TaskPaused、TaskCompleted、TaskFailed、TaskStageCompleted），异步更新 Task 状态投影到 Redis
  - **PlanStateProjectionUpdater**：监听 Plan 相关事件（PlanReady、PlanStarted、PlanPaused、PlanCompleted、PlanFailed），异步更新 Plan 状态投影到 Redis
  - **租户任务索引**：TaskCreated 事件触发时建立 TenantId → TaskId 映射索引
- **一致性模型**：
  - 命令侧（Command）：聚合负责业务逻辑，保存到内存仓储
  - 查询侧（Query）：事件监听器异步更新投影到 Redis，供重启后查询
  - 最终一致性：投影与聚合之间允许短暂延迟（毫秒级）
- **故障降级**：
  - Redis 不可用时自动降级为 InMemory 投影存储（重启后状态丢失）
  - AutoConfiguration 条件装配，自动选择可用实现
- **设计理念**：
  - 查询 API 仅用于重启后状态恢复（兜底使用），不建议常规调用
  - 避免演变为查询平台，保持最小化设计（3 个核心查询方法）

> 详见：[persistence.md](./design/persistence.md) §3.4 投影存储、[task-016-final-implementation-report.md](./temp/task-016-final-implementation-report.md)

---

## 10. 自动装配与配置（AutoConfiguration）

### 10.1 核心 AutoConfiguration

**ExecutorPersistenceAutoConfiguration**（T-016）：
- 自动装配持久化相关组件（Checkpoint、投影存储、租户锁）
- 条件装配：根据配置自动选择 Redis / InMemory 实现
- 故障降级：Redis 不可用时自动回退到内存实现

**装配组件**：
```
@ConditionalOnProperty("executor.persistence.redis.enabled=true")
├── RedisClient
├── RedisCheckpointRepository
├── RedisTaskStateProjectionStore
├── RedisPlanStateProjectionStore
├── RedisTenantTaskIndexStore
└── RedisTenantLockManager

@ConditionalOnMissingBean (Fallback)
├── InMemoryCheckpointRepository
├── InMemoryTaskStateProjectionStore
├── InMemoryPlanStateProjectionStore
├── InMemoryTenantTaskIndexStore
└── InMemoryTenantLockManager
```

### 10.2 配置示例

#### 开发环境（内存模式）

```yaml
executor:
  persistence:
    redis:
      enabled: false          # 禁用 Redis
  max-concurrency: 10
  health-check:
    interval-seconds: 3
    max-attempts: 10
```

**效果**：
- 所有组件使用内存实现
- 重启后状态丢失
- 适合本地开发和单元测试

#### 生产环境（Redis 模式）

```yaml
spring:
  data:
    redis:
      host: redis.prod.example.com
      port: 6379
      password: ${REDIS_PASSWORD}
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 20
          max-idle: 10

executor:
  persistence:
    redis:
      enabled: true           # 启用 Redis
      namespace: prod-executor
      ttl:
        checkpoint: 604800    # 7 天
        projection: 2592000   # 30 天
        lock: 9000            # 2.5 小时
  max-concurrency: 50
  health-check:
    interval-seconds: 5
    max-attempts: 20
```

**效果**：
- 所有组件使用 Redis 实现
- 支持多实例部署（分布式锁）
- 重启后状态可恢复

#### 测试环境（TestContainers）

```java
@SpringBootTest
@Testcontainers
class IntegrationTest {
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);
    
    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("executor.persistence.redis.enabled", () -> "true");
    }
}
```

### 10.3 配置属性完整列表

**ExecutorPersistenceProperties**：
```yaml
executor:
  persistence:
    redis:
      enabled: true                    # 是否启用 Redis（默认 false）
      namespace: executor              # Key 前缀（默认 "executor"）
      ttl:
        checkpoint: 604800             # Checkpoint TTL（秒，默认 7 天）
        projection: 2592000            # 投影 TTL（秒，默认 30 天）
        lock: 9000                     # 租户锁 TTL（秒，默认 2.5 小时）
```

**ExecutorProperties**：
```yaml
executor:
  max-concurrency: 10                  # Plan 级最大并发数（默认 10）
  health-check:
    path: /health                      # 健康检查路径（默认 /health）
    version-key: version               # 版本字段名（默认 version）
    interval-seconds: 3                # 轮询间隔秒（默认 3）
    max-attempts: 10                   # 最大尝试次数（默认 10）
  heartbeat:
    interval-seconds: 10               # 心跳间隔秒（默认 10）
```

### 10.4 条件装配详解

**装配逻辑**：
```java
@Configuration
@EnableConfigurationProperties({
    ExecutorPersistenceProperties.class,
    ExecutorProperties.class
})
public class ExecutorPersistenceAutoConfiguration {
    
    // Redis 客户端（条件：配置启用）
    @Bean
    @ConditionalOnProperty(
        name = "executor.persistence.redis.enabled", 
        havingValue = "true"
    )
    public RedisClient redisClient(...) { ... }
    
    // Redis 实现（条件：Redis 客户端存在）
    @Bean
    @ConditionalOnBean(RedisClient.class)
    public RedisCheckpointRepository redisCheckpointRepository(...) { ... }
    
    // InMemory 实现（条件：Redis 实现不存在）
    @Bean
    @ConditionalOnMissingBean(CheckpointRepository.class)
    public InMemoryCheckpointRepository inMemoryCheckpointRepository() { ... }
}
```

**装配优先级**：
1. Redis 实现（如果配置启用且连接成功）
2. InMemory 实现（如果 Redis 不可用）

### 10.5 故障降级

**Redis 连接失败**：
```
[WARN] RedisClient connection failed, falling back to InMemory implementation
```

**自动降级流程**：
1. Spring 尝试创建 RedisClient Bean
2. 连接失败（超时 / 认证失败 / 网络不可达）
3. `@ConditionalOnBean(RedisClient.class)` 条件不满足
4. 触发 `@ConditionalOnMissingBean` 装配 InMemory 实现
5. 应用正常启动，使用内存模式

**影响**：
- ✅ 应用可正常启动和运行
- ⚠️ 重启后状态丢失
- ⚠️ 多实例部署可能冲突（租户锁失效）

**监控建议**：
```java
@Component
public class PersistenceHealthIndicator implements HealthIndicator {
    private final CheckpointRepository repository;
    
    @Override
    public Health health() {
        if (repository instanceof RedisCheckpointRepository) {
            return Health.up().withDetail("mode", "Redis").build();
        } else {
            return Health.status("WARNING")
                .withDetail("mode", "InMemory")
                .withDetail("warning", "State will be lost on restart")
                .build();
        }
    }
}
```

### 10.6 自定义配置

**覆盖默认实现**：
```java
@Configuration
public class CustomPersistenceConfig {
    
    // 覆盖默认 Checkpoint Repository
    @Bean
    @Primary
    public CheckpointRepository customCheckpointRepository() {
        return new MyCustomCheckpointRepository();
    }
    
    // 添加自定义投影存储
    @Bean
    public CustomProjectionStore customProjectionStore() {
        return new CustomProjectionStore();
    }
}
```

**扩展配置属性**：
```java
@ConfigurationProperties("executor.custom")
public class CustomExecutorProperties {
    private String customProperty;
    // getters/setters
}
```

> 详见：[task-016-final-implementation-report.md](./temp/task-016-final-implementation-report.md) §3 AutoConfiguration 设计

---

### 10.7 基础设施扩展模块（Redis Renewal / Redis ACK）
为减少部署编排与通用 Redis 操作的耦合，引入两个独立的可复用基础设施模块：

| 模块 | 作用 | 核心接口 | 关键扩展点 | 观测性 | 多实例支持 |
|------|------|----------|------------|--------|------------|
| Redis Renewal Service | 统一维护一组 Key 的续期（TTL 延长） | RenewalService / RenewalTask | TTL 策略、间隔策略、停止条件、Key 生成器 | 指标 + 健康检查 | ✅（基于时间轮 + 无共享状态）|
| Redis ACK Service | 配置写入后确认客户端采纳 | RedisAckService (Write→Publish→Verify) | FootprintExtractor、AckEndpoint、RetryStrategy、RedisOperation、消息模板 | 指标 + 健康检查（可选） | ✅（无状态执行 + Redis）|

设计要点：
- 与 deploy 业务解耦；上层只通过接口编排，不直接拼装 Redis 操作细节。
- 均提供 Spring Boot AutoConfiguration 与 properties；未启用时不影响主模块启动。
- 失败不“吞掉”结果：ACK 以 AckResult 理性暴露失败类型（TIMEOUT/MISMATCH/ERROR），Renewal 以指标/日志标注异常 Key。

> 详见设计文档：[redis-renewal-service](./design/redis-renewal-service.md)、[redis-ack-service](./design/redis-ack-service.md)

---

## 11. 非功能特性概览
| 维度 | 当前策略 |
|------|----------|
| 可扩展性 | 新增 StageStep 实现 + 配置驱动 StageFactory 组合 |
| 可观察性 | 领域事件 + 心跳进度；可挂载指标（MetricsRegistry）|
| 异常处理 | FailureInfo 分类（ErrorType：VALIDATION/EXECUTION/TIMEOUT/ROLLBACK/INFRASTRUCTURE）|
| 幂等 | 事件序列（sequenceId 逻辑在事件总线层，可扩展）|
| 性能 | 内存优先读写；Redis 仅用于持久化断点与广播 |
| 安全 | 依赖宿主系统的认证/鉴权（模块内部不处理）|

---

## 12. 技术栈（修正）
| 类别 | 使用 | 说明 |
|------|------|------|
| 语言 / 运行时 | Java 17 | 主开发语言 |
| 框架 | Spring Boot 3.2.x | 应用框架与容器 |
| 持久化 | Redis + InMemory | Checkpoint 与运行态存储；无 RDBMS/JPA |
| 序列化 | Jackson | JSON/YAML/日期类型支持 |
| 验证 | Jakarta Validation + Hibernate Validator | 参数与模型校验 |
| 依赖发现（可选） | Nacos Client | 服务发现（可降级固定 IP）|
| 指标（可选） | Micrometer | 自定义指标挂载 |
| 测试 | JUnit5 / Awaitility / Testcontainers / JMH | 单测、异步、集成、性能 |

---

## 12. 不适用的视图说明
**物理视图 (Physical View)**：本模块不直接定义物理部署拓扑。部署架构（节点、网络、HA、存储规划）由集成该模块的上层系统决定。参见说明文档：[physical-view-not-applicable.md](./views/physical-view-not-applicable.md)。

---

## 13. 文档索引（更新）
| 分类 | 文件 | 描述 |
|------|------|------|
| 逻辑视图 | [logical-view.puml](./views/logical-view.puml) / [logical-view.md](./views/logical-view.md) | 聚合、值对象、事件、仓储、领域服务 |
| 进程视图 | [process-view.puml](./views/process-view.puml) / [process-view.md](./views/process-view.md) | 状态机、执行/重试/暂停时序、Stage 内部 |
| 开发视图 | [development-view.puml](./views/development-view.puml) / [development-view.md](./views/development-view.md) | 分层架构、包结构、接口实现、依赖倒置 |
| 桥接视图 | [plan-to-execution-bridge.puml](./views/plan-to-execution-bridge.puml) | Plan 事件 → 应用层桥接 → 编排/执行 |
| 场景视图 | [scenarios.puml](./views/scenarios.puml) / [scenarios.md](./views/scenarios.md) | 用例图、关系、流程（部署/重试/暂停/回滚）|
| 不适用视图 | [physical-view-not-applicable.md](./views/physical-view-not-applicable.md) | 说明物理视图不在模块范围 |
| 技术栈 | [tech-stack.md](./tech-stack.md) | 已验证技术栈清单 |
| 术语表 | [glossary.md](./glossary.md) | 领域与技术术语定义 |
| 工作流 | [documentation-workflow.md](./workflow/documentation-workflow.md) | 文档更新流程 |
| 开发规范 | [development-workflow.md](./workflow/development-workflow.md) | Git / 测试 / 提交规范 |
| 执行机设计 | [execution-engine.md](./design/execution-engine.md) | 核心执行机与扩展点 |
| Redis 续期服务 | [redis-renewal-service.md](./design/redis-renewal-service.md) | 通用 Key 续期模块设计 |
| Redis ACK 服务 | [redis-ack-service.md](./design/redis-ack-service.md) | 配置写入确认模块设计 |
| 持久化设计 | [persistence.md](./design/persistence.md) | InMemory 与 Redis 策略 |
| Checkpoint 机制 | [checkpoint-mechanism.md](./design/checkpoint-mechanism.md) | 序列化与 TTL |
| 状态管理设计 | [state-management.md](./design/state-management.md) | 状态转换矩阵 |
| 防腐层工厂 | [anti-corruption-layer.md](./design/anti-corruption-layer.md) | ServiceConfigFactory 家族设计 |
| 冲突协调 | [conflict-coordination.md](./design/conflict-coordination.md) | 租户互斥协调与演进 |
| 调度策略 | [scheduling-strategy.md](./design/scheduling-strategy.md) | PlanSchedulingStrategy 对比 |
| 门面层 | [facade-layer.md](./design/facade-layer.md) | 对外与应用内门面职责边界 |

---
## 14. 后续设计待补充
| 设计文档 | 说明 | 状态 |
|----------|------|------|
| [design/execution-engine.md](./design/execution-engine.md) | 执行机核心结构与扩展点 | ✅ 已完成 (T-003) |
| [design/domain-model.md](./design/domain-model.md) | 聚合内部结构与不变式 | ✅ 已完成 (T-004) |
| [design/persistence.md](./design/persistence.md) | InMemory 与 Redis 仓储策略；T-016 投影持久化与查询API | ✅ 已完成 (T-005 + T-016) |
| [design/checkpoint-mechanism.md](./design/checkpoint-mechanism.md) | Checkpoint 序列化与 TTL 策略 | ✅ 已完成 (T-005 派生) |
| [design/state-management.md](./design/state-management.md) | 状态机转换矩阵 + 失败路径 | ✅ 已完成 (T-007) |
| [design/anti-corruption-layer.md](./design/anti-corruption-layer.md) | 防腐层工厂设计 | ✅ 已完成 (T-010) |
| [design/conflict-coordination.md](./design/conflict-coordination.md) | 冲突协调设计 | ✅ 已完成 (T-011) |
| [design/scheduling-strategy.md](./design/scheduling-strategy.md) | 计划调度策略 | ✅ 已完成 (T-012) |
| [design/facade-layer.md](./design/facade-layer.md) | 门面层设计 | ✅ 已完成 (T-013) |
| [prompts/onboarding-prompt.md](./prompts/onboarding-prompt.md) | AI/新人入门指引 | ✅ 已完成 (T-006) |
| [prompts/architecture-prompt.md](./prompts/architecture-prompt.md) | 架构深度提示词 | ✅ 已完成 (T-006) |

---

## 15. 风险与待改进项
| 风险 | 描述 | 计划 |
|------|------|------|
| 事件发布耦合 | 事件发布集中在应用服务，缺少异步落地保障 | 引入事件总线适配层，实现重试/缓冲 |
| Checkpoint 竞争 | 并发保存存在覆盖风险 | 引入版本号或乐观锁（未来）|
| 心跳调度资源占用 | 大量并发任务时调度器线程增加 | 合并心跳调度器，批量上报 |
| ~~多实例租户锁~~ | ~~TenantConflictManager 为本地内存~~ | ~~引入分布式锁（Redis / Redisson）~~ ✅ **已完成（T-016）** |
| 投影延迟 | 事件异步更新投影，可能短暂不一致 | 可接受（CQRS 最终一致性）|

---

## 16. 总结
本架构以“精确、内聚、可恢复”为核心：通过独立聚合加值对象确保领域语义清晰；通过协作式暂停与 Checkpoint 提供健壮的运行时控制；通过事件与心跳机制提升可观测性。后续将围绕执行机扩展点、分布式一致性与事件可靠投递继续演进。

> 下一任务：T-002 完成后将进入执行机详细设计（T-003）。
