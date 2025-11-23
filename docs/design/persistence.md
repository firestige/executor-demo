# 持久化与运行态设计（Persistence & Runtime State）

> 任务: T-005（基础）、T-016（投影持久化增强）  
> 状态: 已完成  
> 最后更新: 2025-11-24

---
## 1. 范围
描述执行模块内部“持久化”与“运行态”数据的存储策略：聚合内存驻留、Checkpoint Redis 持久化、事件暂存与发布、租户冲突管理、无 RDBMS 前提下的一致性与隔离策略。

---
## 2. 数据分类
| 分类 | 数据示例 | 生命周期 | 存储介质 | 访问模式 | 是否跨实例共享 | 一致性级别 |
|------|----------|----------|----------|----------|----------------|------------|
| 聚合状态 | PlanAggregate / TaskAggregate 字段 | 任务执行期 | JVM Heap (InMemoryRepository) | 读多写少 | 否 | 单实例强一致 |
| 阶段结果 | List<StageResult> | 任务执行期 | 聚合内部列表 | 顺序追加 | 否 | 与任务生命周期同步 |
| Checkpoint | TaskCheckpoint(lastCompletedIndex, completedStageNames) | 执行失败/暂停时 | Redis (RedisCheckpointRepository) + 本地缓存 | 覆盖写，单 key | 是 | Redis 最终一致（TTL）|
| **状态投影 (T-016)** | TaskStateProjection / PlanStateProjection | 事件驱动更新 | Redis Hash (RedisProjectionStore) / InMemory | 事件监听器写入 | 是 | 最终一致（异步） |
| **租户索引 (T-016)** | TenantId → TaskId 映射 | 任务生命周期 | Redis String / InMemory | 单次写，多次读 | 是 | 强一致（原子写）|
| **租户锁 (T-016)** | 分布式租户锁 | 任务执行期 | Redis SET NX / InMemory | 原子获取/释放 | 是 | 强一致（原子操作）|
| 重试策略 | RetryPolicy | 执行期 | 聚合内部值对象 | 不可变替换 | 否 | 单实例强一致 |
| 进度快照 | Heartbeat 发布的 TaskProgressEvent | 周期（10s） | 事件总线（同步调用） | 发布→消费 | 可广播 | 最终一致（消费者自定义）|
| 租户冲突锁（旧） | runningTenants Map | Task RUNNING 范围内 | ConcurrentHashMap | put/remove | 否（已升级为分布式锁）| 本地一致；多实例潜在冲突 |

---
## 3. 仓储接口与实现
### 3.1 设计原则
- 聚合不直接耦合底层存储（DDD Repository 模式）
- 简化接口：save/find/remove + 语义化查询，避免泛化 CRUD
- InMemory 实现作为运行期缓存与测试替身

### 3.2 CheckpointRepository
| 方法 | 语义 | Redis 实现 | InMemory 实现 |
|------|------|------------|--------------|
| put(taskId, checkpoint) | 覆盖写入当前检查点 | set(namespace+taskId, JSON, TTL) + 本地缓存 | Map.put |
| get(taskId) | 获取检查点 | 读本地缓存→回源 Redis | Map.get |
| remove(taskId) | 删除检查点 | del(namespace+taskId) + 缓存失效 | Map.remove |

Redis Key 规范：`executor:ckpt:{taskId}`  （可通过 namespace 前缀定制，如 `prod:executor:ckpt:`）。
TTL 策略：默认 7d，可配置。过期后自动清理，避免历史断点堆积。

### 3.3 聚合仓储（示意，未展示全部代码）
| 仓储 | 实现类 | 存储结构 | 线程安全 | 说明 |
|------|--------|----------|----------|------|
| PlanRepository | InMemoryPlanRepository | ConcurrentMap<PlanId, PlanAggregate> | 是 | 单实例内存驻留 |
| TaskRepository | InMemoryTaskRepository | ConcurrentMap<TaskId, TaskAggregate> | 是 | 单实例内存驻留 |
| TaskRuntimeRepository | InMemoryTaskRepository / Composite | 同 TaskRepository 或扩展 | 是 | 运行时属性拆分（未来扩展）|

不引入持久化 DB，实例重启即失去聚合状态（需上层保证可重构或任务重新创建）。

### 3.4 投影存储（T-016：CQRS + Event Sourcing）
| 存储 | 实现类 | 存储结构 | 更新方式 | 说明 |
|------|--------|----------|----------|------|
| TaskStateProjectionStore | RedisTaskStateProjectionStore / InMemory | Redis Hash: `executor:task:{taskId}` | 事件驱动（TaskStateProjectionUpdater） | 存储 Task 状态快照供查询 |
| PlanStateProjectionStore | RedisPlanStateProjectionStore / InMemory | Redis Hash: `executor:plan:{planId}` | 事件驱动（PlanStateProjectionUpdater） | 存储 Plan 状态快照供查询 |
| TenantTaskIndexStore | RedisTenantTaskIndexStore / InMemory | Redis String: `executor:index:tenant:{tenantId}` | TaskCreated 事件时建立 | 租户 → 任务映射索引 |

**设计理念**：
- 命令侧（Command）：聚合负责业务逻辑，保存到内存仓储
- 查询侧（Query）：事件监听器异步更新投影到 Redis，供重启后查询
- 最终一致：投影与聚合之间允许短暂延迟（毫秒级）

**投影字段**（TaskStateProjection）：
- taskId, tenantId, planId
- status（TaskStatus）
- pauseRequested（是否请求暂停）
- stageNames（所有阶段名称）
- lastCompletedStageIndex（已完成阶段索引）
- createdAt, updatedAt

**事件监听器**：
- TaskStateProjectionUpdater：监听 TaskCreated/Started/Paused/Completed/Failed/StageCompleted 事件
- PlanStateProjectionUpdater：监听 PlanReady/Started/Paused/Completed/Failed 事件

### 3.5 租户锁管理（T-016：分布式锁）
| 接口 | 实现类 | 存储结构 | 操作 | 说明 |
|------|--------|----------|------|------|
| TenantLockManager | RedisTenantLockManager / InMemory | Redis String: `executor:lock:tenant:{tenantId}` | SET NX + TTL | 分布式租户锁，支持多实例 |

**关键特性**：
- tryAcquire(tenantId, taskId, ttl)：原子获取锁（Redis SET NX）
- release(tenantId)：释放锁
- renew(tenantId, ttl)：续租（长任务场景）
- exists(tenantId)：检查锁是否存在
- TTL：默认 2.5 小时，防止崩溃后锁泄漏

---
## 4. Redis Checkpoint 实现细节
| 关注点 | 说明 |
|--------|------|
| 序列化 | Jackson + JavaTimeModule，禁用时间戳序列化，直接写 ISO 日期 |
| 覆盖写 | put() 始终覆盖旧值，不做增量合并（简化并发）|
| 本地缓存 | ConcurrentHashMap 作为 read-through 缓存，减少重复反序列化 |
| 异常处理 | 序列化/反序列化失败抛 RuntimeException（可后续改为自定义异常）|
| 并发风险 | 多执行线程同时保存会最后写覆盖，无版本控制（风险低：Stage 边界保存）|
| 删除语义 | remove() 用于任务完成/终止后清理断点数据 |

---
## 5. 一致性与隔离分析
| 场景 | 一致性策略 | 风险 | 处理/缓解 |
|------|------------|------|-----------|
| 任务失败后重试 | 从 Redis 读取最新 Checkpoint | 覆盖导致阶段列表回退 | Stage 索引写入前后逻辑规避回退（单调）|
| 暂停后恢复 | 读取 lastCompletedIndex + completedStages | 并发保存覆盖 | 暂停只在 Stage 边界，单写点 |
| 心跳与断点同时发生 | 两者独立（心跳不写 Checkpoint） | 无 | - |
| 多实例并发执行同租户 | 本地租户锁不共享 | 冲突执行 | 未来引入 Redis 锁 |
| Redis 失效 | 读取失败返回 null | 重试时丢失断点 | 回退从头执行（可接受降级）|

---
## 6. 键空间与命名规范

### 6.1 Key 设计总览（T-016 完整版）

| 类型 | Key 模板 | 示例 | 数据结构 | TTL | 说明 |
|------|----------|------|---------|-----|------|
| Checkpoint | `executor:ckpt:{taskId}` | `executor:ckpt:task-123` | String (JSON) | 7天 | 断点 JSON 序列化对象 |
| **Task投影** | `executor:task:{taskId}` | `executor:task:task-123` | Hash | 30天 | Task 状态投影（字段：taskId, status, tenantId, etc.） |
| **Plan投影** | `executor:plan:{planId}` | `executor:plan:plan-456` | Hash | 30天 | Plan 状态投影（字段：planId, status, taskIds, etc.） |
| **租户索引** | `executor:index:tenant:{tenantId}` | `executor:index:tenant:t-001` | String | 30天 | TenantId → TaskId 映射（单个租户只有一个活跃任务）|
| **租户锁** | `executor:lock:tenant:{tenantId}` | `executor:lock:tenant:t-001` | String | 2.5小时 | 分布式租户锁（SET NX + TTL）|

### 6.2 数据结构详解

#### Checkpoint (String - JSON)
```json
{
  "lastCompletedStageIndex": 2,
  "completedStages": ["stage-0", "stage-1", "stage-2"],
  "contextData": {
    "customKey": "customValue"
  },
  "savedAt": "2025-11-24T10:30:00"
}
```

#### Task 投影 (Hash)
```
HGETALL executor:task:task-123
taskId: task-123
tenantId: tenant-001
planId: plan-456
status: RUNNING
pauseRequested: false
stageNames: ["stage-0","stage-1","stage-2"]
lastCompletedStageIndex: 1
createdAt: 2025-11-24T10:00:00
updatedAt: 2025-11-24T10:30:00
```

#### Plan 投影 (Hash)
```
HGETALL executor:plan:plan-456
planId: plan-456
status: RUNNING
taskIds: ["task-123","task-124"]
progress: 0.5
maxConcurrency: 5
createdAt: 2025-11-24T10:00:00
updatedAt: 2025-11-24T10:30:00
```

#### 租户索引 (String)
```
GET executor:index:tenant:tenant-001
task-123
```

#### 租户锁 (String)
```
SET executor:lock:tenant:tenant-001 task-123 NX EX 9000
```

### 6.3 命名空间配置

**配置项**（`application.yml`）：
```yaml
executor:
  persistence:
    redis:
      namespace: "executor"    # 默认前缀
      ttl:
        checkpoint: 604800     # 7 天（秒）
        projection: 2592000    # 30 天（秒）
        lock: 9000             # 2.5 小时（秒）
```

**多环境隔离**：
```yaml
# 生产环境
executor.persistence.redis.namespace: prod-executor

# 测试环境
executor.persistence.redis.namespace: test-executor
```

**实际 Key 示例**：
- 生产：`prod-executor:task:task-123`
- 测试：`test-executor:task:task-123`

### 6.4 索引设计

**租户 → 任务索引**：
- **目的**：支持按租户查询任务（`queryTasksByTenant`）
- **更新时机**：TaskCreated 事件触发时建立
- **清理时机**：TaskCompleted / TaskFailed / TaskCancelled 事件触发时删除
- **冲突处理**：同一租户同时只有一个活跃任务（租户锁保证）

**未来扩展**：
- Plan → 任务列表索引（`executor:index:plan:{planId}` → Set of taskIds）
- 状态索引（`executor:index:status:RUNNING` → Set of taskIds）
- 时间范围索引（`executor:index:time:{date}` → Set of taskIds）

### 6.5 TTL 策略与清理

| 数据类型 | 默认 TTL | 清理策略 | 原因 |
|---------|---------|---------|------|
| Checkpoint | 7天 | 任务完成/失败时主动删除 + TTL 兜底 | 断点数据仅短期有效，长期保留无意义 |
| Task/Plan 投影 | 30天 | 仅 TTL 自动过期 | 保留较长时间供审计和问题排查 |
| 租户索引 | 30天 | 任务终态时主动删除 + TTL 兜底 | 与投影保持一致的保留期 |
| 租户锁 | 2.5小时 | 任务完成时主动释放 + TTL 防泄漏 | 短 TTL 防止崩溃后锁泄漏 |

**手动清理命令**（运维）：
```bash
# 清理指定前缀的所有 Key
redis-cli --scan --pattern "executor:*" | xargs redis-cli del

# 清理特定类型
redis-cli --scan --pattern "executor:ckpt:*" | xargs redis-cli del
redis-cli --scan --pattern "executor:task:*" | xargs redis-cli del
```

### 6.6 监控与告警

**关键指标**：
- Key 数量：`INFO keyspace` 监控 `executor:*` 前缀 Key 数量
- 锁泄漏：`SCAN executor:lock:*` 检查过期未释放的锁
- 投影延迟：对比聚合更新时间与投影更新时间（updatedAt）

**告警规则**：
- Checkpoint Key 数量 > 10000：可能有大量失败任务未清理
- 租户锁 Key 数量 > 100：可能有锁泄漏
- 投影更新延迟 > 5 秒：事件监听器可能异常

---

---
## 7. 查询 API（T-016：最小兜底）

**设计定位**：查询 API 仅作为系统重启后的兜底手段，用于 SRE 手动确认状态并决定是否重试。

### 7.1 最小 API 集合
| API | 用途 | 返回 |
|-----|------|------|
| queryTaskStatusByTenant(TenantId) | 查询任务状态 | TaskStatusInfo（含进度） |
| queryPlanStatus(PlanId) | 查询计划状态 | PlanStatusInfo |
| hasCheckpoint(TenantId) | 判断是否有 checkpoint | boolean |

### 7.2 使用原则
- ✅ **仅兜底使用**：系统重启后、事件丢失时的手动查询
- ✅ **低频调用**：SRE 人工介入场景，非高频轮询
- ❌ **不做监控**：监控指标应通过事件推送到独立系统
- ❌ **不做分析**：不提供统计、聚合、历史查询

### 7.3 典型使用场景
```java
// 重启后恢复流程
TenantId tenantId = TenantId.of("tenant-001");
TaskStatusInfo status = facade.queryTaskStatusByTenant(tenantId);

if (status.getStatus() == TaskStatus.FAILED) {
    boolean hasCheckpoint = facade.hasCheckpoint(tenantId);
    if (hasCheckpoint) {
        // 从 checkpoint 重试（跳过已完成阶段）
        facade.retryTaskByTenant(tenantId, true);
    }
}
```

### 7.4 AutoConfiguration（T-016）
**自动配置类**：`ExecutorPersistenceAutoConfiguration`

**条件装配**：
```yaml
executor:
  persistence:
    store-type: redis  # 或 memory（fallback）
    namespace: executor
    projection-ttl: 7d
    lock-ttl: 2h30m
```

**提供的 Bean**：
- TaskStateProjectionStore（Redis/InMemory）
- PlanStateProjectionStore（Redis/InMemory）
- TenantTaskIndexStore（Redis/InMemory）
- TenantLockManager（Redis/InMemory）
- TaskQueryService（查询服务）

**故障降级**：Redis 不可用时自动降级为 InMemory 实现（重启后状态丢失）

---
## 8. 失败与降级策略
| 场景 | 策略 | 影响范围 |
|------|------|----------|
| Redis 不可用 | 回退 InMemoryCheckpointRepository（标记为非持久） | 重试/暂停恢复可能失效 |
| Redis 不可用（投影）| AutoConfiguration 自动降级为 InMemory | 重启后查询 API 失效 |
| 反序列化失败 | 抛异常终止当前操作 | 任务失败，触发 TaskFailedEvent |
| 覆盖竞争 | 最后写胜出 | 轻微：阶段边界写入频率低 |
| TTL 过期 | 断点自动清除 | 重试时从头开始执行 |
| 投影延迟 | 事件异步更新，可能短暂不一致 | 可接受（最终一致）|
| 租户锁泄漏 | TTL 自动释放（2.5小时） | 长任务可能提前释放（需续租）|

---
## 9. 演进与改进计划
| 改进项 | 描述 | 优先级 | 状态 |
|--------|------|--------|------|
| Checkpoint 版本化 | 增加 `version` 字段 + CAS 脚本 | P1 | 待实施 |
| **分布式租户锁（T-016）** | Redis 锁替代本地 Map | P1 | ✅ 已完成 |
| **投影持久化（T-016）** | CQRS + Event Sourcing 架构 | P1 | ✅ 已完成 |
| **查询 API（T-016）** | 最小兜底查询接口 | P1 | ✅ 已完成 |
| 增量断点 | 仅追加最近阶段，而非全部覆盖 | P2 | 待实施 |
| 异常分类 | 序列化异常细化为 ERROR_TYPE | P2 | 待实施 |
| 投影批量查询 | 支持批量租户状态查询（如需要） | P3 | 待评估 |
| 指标持久化 | 定期汇总指标写入 Redis | P3 | 待实施 |

---
## 10. Definition of Done（T-005 + T-016）
| 条目 | 标准 | 状态 |
|------|------|------|
| Checkpoint 存储 | Redis + InMemory 双实现 | ✅ |
| 聚合仓储 | InMemory 实现，线程安全 | ✅ |
| 投影存储（T-016） | Redis + InMemory，事件驱动更新 | ✅ |
| 租户锁（T-016） | Redis SET NX，支持多实例 | ✅ |
| 查询 API（T-016） | 最小兜底集合（3个方法） | ✅ |
| AutoConfiguration（T-016） | 条件装配，故障降级 | ✅ |
| 测试覆盖（T-016） | 21个测试用例（单元+集成） | ✅ |
| 文档更新 | 架构总纲 + 持久化设计 | ✅ |

---
## 11. 参考文档
- [T-016 最终实施报告](../temp/task-016-final-implementation-report.md)
- [T-016 Phase 2 实施报告](../temp/task-016-phase2-implementation-report.md)
- [T-016 Phase 3 查询API](../temp/task-016-phase3-completion-report.md)
- [T-016 Phase 4 测试报告](../temp/task-016-phase4-completion-report.md)
- [Checkpoint 机制设计](./checkpoint-mechanism.md)
| 数据分类 | 聚合/Checkpoint/锁/事件/进度分类清晰 |
| 仓储接口 | 方法与语义表述完整（put/get/remove）|
| Redis 细节 | Key/TTL/序列化/缓存说明完整 |
| 一致性分析 | 主要场景风险与缓解列出 |
| 演进计划 | 至少 5 项改进建议 + 优先级 |

---
> 后续：在 checkpoint-mechanism.md 中扩展版本化与增量更新策略设计（T-005 派生）。

