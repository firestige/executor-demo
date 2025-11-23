# T-016 持久化方案最终设计

> 任务: T-016  
> 阶段: 详细设计  
> 日期: 2025-11-23  
> 状态: **已确认需求，进入设计阶段**

---

## 1. 需求确认（已明确）

| 需求 | 答案 | 影响 |
|------|------|------|
| **是否需要自动恢复？** | ❌ 否，人在回路，SRE 手动触发 | → 不需要启动时扫描恢复 |
| **是否需要重建状态？** | ✅ 是，需要查询 Plan/Task/Checkpoint | → 必须持久化聚合核心状态 |
| **租户锁是否持久化？** | ✅ 是，避免重复执行 | → Redis 分布式锁 |
| **事件是否持久化？** | ❌ 否，未来接 Kafka | → 保持内存 |
| **任务执行时长？** | Plan 2小时，Task < 1分钟 | → 核心状态持久化即可 |
| **外部系统能力？** | 记录状态，触发重试，但不知道 Stage | → 需要持久化 Plan-Task-Checkpoint 关系 |
| **SLA 要求？** | 1分钟内恢复 | → Redis 读取 + 状态重建 |
| **多实例部署？** | 当前单实例，未来多实例 | → 设计需支持分布式 |

---

## 2. 最终方案：状态查询型持久化

### 2.1 核心设计思想

**不是为了自动恢复，而是为了状态查询与手动重试**

- 系统重启后，**不自动恢复执行**
- 外部系统通过 API 查询 Plan/Task 状态
- SRE 根据状态决定是否重试（从 Checkpoint 恢复）
- 租户锁持久化，避免新 Plan 冲突

### 2.2 与自动恢复方案的差异

| 维度 | 自动恢复方案 | 状态查询方案（本方案） |
|------|------------|----------------------|
| 启动行为 | 扫描 RUNNING 任务并恢复 | 不扫描，等待外部查询 |
| 状态索引 | 需要（快速扫描） | 可选（通过 Plan-Task 关联查询） |
| 持久化粒度 | 相同 | 相同 |
| 租户锁 | 恢复时检查 | 始终生效（持久化） |
| API 设计 | 不需要查询 API | 需要查询 API |

---

## 3. 需要持久化的状态

### 3.1 Plan 聚合状态

**持久化字段**：

| 字段 | 类型 | 理由 |
|------|------|------|
| planId | PlanId | 主键 |
| status | PlanStatus | 查询进度，判断是否可重试 |
| taskIds | List<TaskId> | Plan-Task 关联 |
| maxConcurrency | int | 重建编排器配置（如果需要） |
| createdAt | LocalDateTime | 审计 |
| startedAt | LocalDateTime | 审计 |

**不持久化字段**：
- �� progress（通过 Task 状态聚合计算）
- ❌ completedAt（终态，无需查询）

**Redis 结构**：
```
Key: executor:plan:{planId}
Type: Hash
Fields:
  planId: "plan-123"
  status: "RUNNING"
  taskIds: "task-1,task-2,task-3"  (逗号分隔或 JSON 数组)
  maxConcurrency: "5"
  createdAt: "2025-11-23T10:00:00"
  startedAt: "2025-11-23T10:01:00"
TTL: 7天
```

**写入时机**：
- 创建时：HSET 所有字段
- 状态变更时：HSET status + timestamp

---

### 3.2 Task 聚合状态

**持久化字段**：

| 字段 | 类型 | 理由 |
|------|------|------|
| taskId | TaskId | 主键 |
| tenantId | TenantId | **关键**：外部系统通过 tenantId 查询 |
| planId | PlanId | Plan-Task 关联 |
| status | TaskStatus | 查询进度，判断是否可重试 |
| pauseRequested | boolean | 区分"暂停"还是"崩溃" |
| createdAt | LocalDateTime | 审计 |
| startedAt | LocalDateTime | 审计 |

**不持久化字段**：
- ❌ stageProgress（Checkpoint 已有）
- ❌ completedStages（Checkpoint 已有）
- ❌ retryPolicy（重试时重新设置）
- ❌ tenantConfig（外部系统保存）

**Redis 结构**：
```
Key: executor:task:{taskId}
Type: Hash
Fields:
  taskId: "task-456"
  tenantId: "tenant-001"
  planId: "plan-123"
  status: "RUNNING"
  pauseRequested: "false"
  createdAt: "2025-11-23T10:01:00"
  startedAt: "2025-11-23T10:02:00"
TTL: 7天
```

**写入时机**：
- 创建时：HSET 所有字段
- 状态变更时：HSET status + timestamp
- 暂停请求时：HSET pauseRequested

---

### 3.3 TenantId → TaskId 反向索引

**关键需求**：外部系统通过 tenantId 查询 TaskId

**Redis 结构**：
```
Key: executor:index:tenant:{tenantId}
Type: String
Value: {taskId}
TTL: 7天
```

**用途**：
```
外部系统传入 tenantId
  ↓
查询 executor:index:tenant:{tenantId} 获取 taskId
  ↓
查询 executor:task:{taskId} 获取 Task 状态
  ↓
查询 executor:ckpt:{taskId} 获取 Checkpoint
  ↓
调用 retryTaskByTenant(tenantId, fromCheckpoint=true)
```

---

### 3.4 租户锁（分布式）

**Redis 结构**：
```
Key: executor:lock:tenant:{tenantId}
Type: String
Value: {planId}:{taskId}  (哪个 Plan/Task 持有锁)
TTL: 2小时（Plan 最大时长）+ 30分钟缓冲 = 2.5小时
```

**操作**：
- 获取锁：`SET NX EX 9000`（2.5小时）
- 释放锁：`DEL`
- 续租（可选）：Task 执行中每 30 分钟 `EXPIRE` 续期

**多实例安全**：
- SET NX 保证原子性
- TTL 防止崩溃后锁泄漏
- 支持多实例部署

---

### 3.5 Checkpoint（已有，增强关联）

**当前结构**（保持不变）：
```
Key: executor:ckpt:{taskId}
Type: String
Value: JSON {
  lastCompletedStageIndex: 2,
  completedStages: ["stage-1", "stage-2"],
  contextData: {...},
  savedAt: "2025-11-23T10:10:00"
}
TTL: 7天
```

**增强**：在 Task 状态中记录 `hasCheckpoint` 标志（可选）

---

## 4. 不持久化的状态

| 状态 | 理由 |
|------|------|
| **TaskExecutor** | 运行态，重启后不恢复执行 |
| **HeartbeatScheduler** | 运行态，重启后不恢复 |
| **Orchestrator 线程池** | 运行态，重启后不恢复 |
| **领域事件** | 未来接 Kafka |
| **StageProgress** | Checkpoint 已有 |

---

## 5. 持久化时机与性能

### 5.1 写入时机

| 触发点 | 写入内容 | 频率 | Redis 操作 |
|--------|----------|------|-----------|
| **Plan 创建** | Plan Hash + Task Hash (批量) | 1次/Plan | Pipeline HSET × N |
| **Task 状态变更** | Task Hash (status) | ~10次/Task | HSET 1个字段 |
| **Stage 完成** | Checkpoint | ~10次/Task | SET (已有) |
| **租户锁获取** | 租户锁 | 1次/Task | SET NX |
| **租户锁释放** | 删除锁 | 1次/Task | DEL |

### 5.2 性能估算

**假设**：
- 单 Plan：10 Task
- 单 Task：10 Stage
- 并发：100 Plan

**写入次数**（每 Plan）：
- Plan 状态：2次（创建 + 启动）
- Task 状态：10 × 10 = 100次（10个Task × 10次状态变更）
- Checkpoint：10 × 10 = 100次（已有）
- 租户锁：10 × 2 = 20次（获取 + 释放）
- **总计**：约 222次写入/Plan

**Redis 负载**：
- 单次 HSET：< 1ms
- Pipeline 批量写入：< 10ms
- **可接受**

### 5.3 读取时机

| 场景 | 读取内容 | Redis 操作 |
|------|----------|-----------|
| **外部查询 Plan 状态** | Plan Hash | HGETALL |
| **外部查询 Task 状态** | Task Hash | HGETALL |
| **通过 tenantId 查询** | 索引 + Task Hash | GET + HGETALL |
| **重试前查询 Checkpoint** | Checkpoint | GET (已有) |

---

## 6. Redis Key 设计总结

| Key 模式 | 数据结构 | 用途 | TTL |
|----------|----------|------|-----|
| `executor:plan:{planId}` | Hash | Plan 状态 | 7天 |
| `executor:task:{taskId}` | Hash | Task 状态 | 7天 |
| `executor:index:tenant:{tenantId}` | String | TenantId → TaskId 索引 | 7天 |
| `executor:lock:tenant:{tenantId}` | String | 租户分布式锁 | 2.5小时 |
| `executor:ckpt:{taskId}` | String | Checkpoint JSON（已有） | 7天 |

**命名空间**：可配置前缀（如 `prod:executor:`）

---

## 7. API 设计（查询接口）

### 7.1 新增查询 API

```java
// DeploymentTaskFacade 新增方法

/**
 * 查询 Plan 状态（从 Redis）
 */
PlanStatusInfo queryPlanStatus(PlanId planId);

/**
 * 查询 Task 状态（从 Redis）
 */
TaskStatusInfo queryTaskStatusByTenant(TenantId tenantId);

/**
 * 查询 Checkpoint 是否存在
 */
boolean hasCheckpoint(TenantId tenantId);
```

### 7.2 查询流程示例

**场景**：SRE 决定重试某个租户的任务

```
1. 外部系统调用 queryTaskStatusByTenant(tenantId)
   ↓
2. 系统查询 executor:index:tenant:{tenantId} → taskId
   ↓
3. 系统查询 executor:task:{taskId} → status=FAILED
   ↓
4. 系统查询 executor:ckpt:{taskId} → lastCompleted=5
   ↓
5. SRE 确认，调用 retryTaskByTenant(tenantId, fromCheckpoint=true)
   ↓
6. 系统从 Stage-6 开始执行
```

---

## 8. 实施方案

### 8.1 Repository 层改造

**新增**：`RedisTaskRepository` / `RedisPlanRepository`

```java
public interface TaskRepository {
    // 原有方法（内存）
    void save(TaskAggregate task);
    TaskAggregate find(TaskId taskId);
    
    // 新增方法（Redis）
    void saveToPersistence(TaskAggregate task);  // 持久化到 Redis
    TaskAggregate loadFromPersistence(TaskId taskId);  // 从 Redis 加载
    TaskId findTaskIdByTenant(TenantId tenantId);  // 反向索引
}
```

**实现策略**：
- **双写**：save() 同时写内存 + Redis
- **读优先内存**：find() 先查内存，miss 则查 Redis（重启后场景）
- **异步删除**：任务完成后延迟清理 Redis（可选）

### 8.2 租户锁改造

**当前**：`TenantConflictManager`（内存 ConcurrentHashMap）

**改造**：`RedisTenantConflictManager`（Redis SET NX）

```java
public class RedisTenantConflictManager implements TenantConflictManager {
    
    @Override
    public boolean tryAcquire(TenantId tenantId, TaskId taskId) {
        String key = "executor:lock:tenant:" + tenantId;
        String value = taskId.toString();
        // SET NX EX 9000 (2.5小时)
        return redisTemplate.opsForValue()
            .setIfAbsent(key, value, Duration.ofSeconds(9000));
    }
    
    @Override
    public void release(TenantId tenantId) {
        String key = "executor:lock:tenant:" + tenantId;
        redisTemplate.delete(key);
    }
}
```

### 8.3 状态写入时机

**在以下领域服务方法中增加 Redis 写入**：

| 领域服务方法 | Redis 写入 |
|-------------|-----------|
| `PlanDomainService.createPlan()` | HSET executor:plan:{planId} |
| `PlanDomainService.startPlan()` | HSET executor:plan:{planId} status |
| `TaskDomainService.createTask()` | HSET executor:task:{taskId} + SET executor:index:tenant:{tid} |
| `TaskDomainService.startTask()` | HSET executor:task:{taskId} status |
| `TaskDomainService.pauseTask()` | HSET executor:task:{taskId} pauseRequested |
| `TaskDomainService.completeTask()` | HSET executor:task:{taskId} status + DEL lock |

**实现方式**：
- DomainService 调用 `Repository.saveToPersistence()`
- Repository 内部执行 Redis 写入

---

## 9. 恢复流程（手动触发）

### 9.1 系统重启后

**不做任何自动操作**，等待外部查询。

### 9.2 SRE 操作流程

```
1. 外部系统检测到服务重启
   ↓
2. 外部系统查询上一次执行中的租户列表（从自己的数据库）
   ↓
3. 对每个租户：
   a. 调用 queryTaskStatusByTenant(tenantId)
   b. 检查 status 和 hasCheckpoint
   c. 如果 status=RUNNING/FAILED → 决定是否重试
   ↓
4. SRE 确认后，调用 retryTaskByTenant(tenantId, fromCheckpoint=true)
   ↓
5. 系统从 Checkpoint 恢复并继续执行
```

---

## 10. 多实例演进支持

### 10.1 当前单实例

- 租户锁：Redis SET NX（已支持多实例）
- 状态持久化：Redis Hash（已支持多实例）
- 查询 API：无状态（已支持多实例）

### 10.2 未来多实例

**需要增加**：
- Checkpoint 版本化（防止多实例覆盖）
- 任务分片（避免多实例重复执行）

**不需要改动**：
- 租户锁（已支持）
- 状态查询（已支持）

---

## 11. 一致性与异常处理

### 11.1 写入失败

| 场景 | 策略 |
|------|------|
| Redis 不可用 | 记录日志，继续执行（降级为内存） |
| 聚合写入成功，Redis 写入失败 | 任务继续执行，但重启后状态丢失（可接受） |
| Checkpoint 写入失败 | 任务失败，触发 TaskFailedEvent |

### 11.2 一致性保证

**目标**：最终一致

- 聚合状态与 Redis 状态短暂不一致可接受
- Checkpoint 与聚合状态原子性通过 Redis Transaction（可选）

---

## 12. 实施步骤

### 阶段 1：租户锁迁移（优先）

- [ ] 实现 `RedisTenantConflictManager`
- [ ] 替换 `TenantConflictManager` Bean 配置
- [ ] 测试多实例场景（模拟）

### 阶段 2：状态持久化

- [ ] 实现 `RedisTaskRepository.saveToPersistence()`
- [ ] 实现 `RedisPlanRepository.saveToPersistence()`
- [ ] 在 DomainService 中调用持久化方法
- [ ] 实现 TenantId → TaskId 索引

### 阶段 3：查询 API

- [ ] 新增 `queryPlanStatus()` API
- [ ] 新增 `queryTaskStatusByTenant()` API
- [ ] 新增 `hasCheckpoint()` API
- [ ] 测试查询流程

### 阶段 4：测试验证

- [ ] 单元测试：Repository 持久化逻辑
- [ ] 集成测试：重启后状态查询
- [ ] 压力测试：Redis 写入性能
- [ ] 容错测试：Redis 故障降级

---

## 13. Definition of Done

- [ ] 租户锁使用 Redis SET NX
- [ ] Plan/Task 状态写入 Redis Hash
- [ ] TenantId → TaskId 索引可查询
- [ ] 重启后可通过 API 查询状态
- [ ] 重试可从 Checkpoint 恢复
- [ ] Redis 故障时降级为内存
- [ ] 测试覆盖率 > 80%
- [ ] 文档更新（architecture-overview.md、persistence.md）

---

## 14. 风险与缓解

| 风险 | 缓解 |
|------|------|
| Redis 单点故障 | 降级为内存 + 告警 |
| 状态不一致（聚合 vs Redis） | 最终一致，可接受 |
| Checkpoint 覆盖（多实例） | 未来增加版本号 |
| 租户锁泄漏 | TTL 自动释放 |
| 查询性能（大量任务） | 索引优化 + 本地缓存 |

---

## 15. 后续优化（可选）

| 优化项 | 优先级 | 说明 |
|--------|--------|------|
| Checkpoint 版本化 | P2 | 多实例场景防覆盖 |
| 状态本地缓存 | P2 | 减少 Redis 查询 |
| Pipeline 批量写入 | P3 | Plan 创建时批量写 Task |
| 异步写入 | P3 | 状态变更异步写 Redis（需评估一致性） |
| 监控指标 | P2 | Redis 写入延迟、失败率 |

---

> **当前状态**：需求已明确，方案已设计，等待确认后开始实施。
>
> **关键决策**：
> - ✅ 不自动恢复，提供查询 API
> - ✅ 持久化核心状态（Plan/Task/Checkpoint 关联）
> - ✅ 租户锁 Redis 分布式锁
> - ✅ 支持未来多实例演进

