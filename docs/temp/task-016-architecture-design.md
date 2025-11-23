# T-016 持久化方案架构设计（修订版）

> 任务: T-016  
> 阶段: 架构设计（符合 DDD 规范）  
> 日期: 2025-11-23  
> 状态: **设计确认中**

---

## 0. 设计原则确认

### 0.1 核心约束
1. **符合当前 DDD 战略/战术规范**：不破坏聚合边界、依赖倒置、分层清晰
2. **接口隔离 + 通用命名**：不绑定特定技术（Redis/DB），支持实现替换
3. **一次性完整实施**：不做渐进式，避免历史欠账
4. **Git 回退风险控制**：每次重大操作前提交可用版本

### 0.2 方案定位
**状态投影型持久化（Projection-based Persistence）**

- 聚合（PlanAggregate / TaskAggregate）仍驻留内存，保持纯粹
- 投影（Plan/Task 状态快照）持久化到外部存储，供查询使用
- 通过领域事件驱动投影更新（CQRS 模式）
- 重启后通过投影查询状态，SRE 手动触发重试/回滚

---

## 1. 分层架构设计（符合 DDD）

### 1.1 四层职责划分

```
┌─────────────────────────────────────────────────────────┐
│ Facade 层                                                │
│ - DeploymentTaskFacade（命令 + 查询 API）                │
└─────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────┐
│ Application 层                                           │
│ - 命令服务：PlanLifecycleService, TaskOperationService   │
│ - 查询服务：TaskQueryService（新增）                     │
│ - 投影更新器：TaskStateProjectionUpdater（事件监听器）   │
└─────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────┐
│ Domain 层（纯粹，无技术依赖）                             │
│ - 聚合：PlanAggregate, TaskAggregate                     │
│ - 领域服务：PlanDomainService, TaskDomainService         │
│ - 仓储接口：PlanRepository, TaskRepository（聚合加载）   │
│ - 领域事件：PlanStartedEvent, TaskCompletedEvent...     │
└─────────────────────────────────────────────────────────┘
                            ↑ 依赖倒置
┌─────────────────────────────────────────────────────────┐
│ Infrastructure 层（技术适配）                             │
│ - 聚合仓储实现：InMemoryTaskRepository（保持）           │
│ - 投影仓储接口（通用）：TaskStateProjection*             │
│ - 投影仓储实现：RedisTaskStateProjectionRepository       │
│               或 JdbcTaskStateProjectionRepository       │
│ - 锁管理接口：TenantLockManager（通用）                  │
│ - 锁管理实现：RedisTenantLockManager / JdbcTenant...    │
│ - Checkpoint：CheckpointStorage（通用接口）              │
└─────────────────────────────────────────────────────────┘
```

---

## 2. 核心概念澄清

### 2.1 聚合仓储 vs 投影仓储

| 概念 | 所属层 | 职责 | 返回类型 | 示例 |
|------|--------|------|----------|------|
| **聚合仓储** | Domain 接口 | 支撑业务行为的聚合加载/保存 | TaskAggregate | TaskRepository.find(taskId) |
| **投影仓储** | Infrastructure 接口 | 持久化状态快照供查询 | TaskStateProjection DTO | TaskStateProjectionStore.load(taskId) |

**关键区别**：
- 聚合仓储：Domain 层定义，返回聚合根，参与不变式检查
- 投影仓储：Infrastructure 层定义，返回简单 DTO，仅供查询

### 2.2 为什么投影仓储接口不在 Domain 层？

**Domain 层的仓储接口应该只关心业务行为需要的聚合加载/保存**。  
投影是为了查询优化而存在的"技术关注点"，不属于核心领域逻辑。

因此：
- `TaskRepository`（聚合仓储）→ Domain 层接口
- `TaskStateProjectionStore`（投影仓储）→ Infrastructure 层接口（或 Application Port）

---

## 3. 通用接口设计（不绑定技术）

### 3.1 投影存储接口（通用命名）

```java
// Infrastructure 层接口
package xyz.firestige.deploy.infrastructure.persistence.projection;

/**
 * Task 状态投影存储接口（技术无关）
 * 实现可以是 Redis / JDBC / File / InMemory
 */
public interface TaskStateProjectionStore {
    
    /**
     * 保存 Task 状态投影
     */
    void save(TaskStateProjection projection);
    
    /**
     * 加载 Task 状态投影
     */
    TaskStateProjection load(TaskId taskId);
    
    /**
     * 通过租户 ID 查询 Task 状态
     */
    TaskStateProjection findByTenantId(TenantId tenantId);
    
    /**
     * 删除投影（Task 完成后清理）
     */
    void remove(TaskId taskId);
    
    /**
     * 批量保存（性能优化，可选）
     */
    default void saveAll(List<TaskStateProjection> projections) {
        projections.forEach(this::save);
    }
}

/**
 * Plan 状态投影存储接口
 */
public interface PlanStateProjectionStore {
    void save(PlanStateProjection projection);
    PlanStateProjection load(PlanId planId);
    void remove(PlanId planId);
}

/**
 * TenantId → TaskId 索引存储接口
 */
public interface TenantTaskIndexStore {
    void put(TenantId tenantId, TaskId taskId);
    TaskId get(TenantId tenantId);
    void remove(TenantId tenantId);
}
```

### 3.2 租户锁管理接口（通用命名）

```java
// Infrastructure 层接口
package xyz.firestige.deploy.infrastructure.lock;

/**
 * 租户锁管理接口（技术无关）
 * 实现可以是 Redis / DB / Zookeeper / InMemory
 */
public interface TenantLockManager {
    
    /**
     * 尝试获取租户锁
     * @return true=成功，false=已被占用
     */
    boolean tryAcquire(TenantId tenantId, TaskId taskId, Duration ttl);
    
    /**
     * 释放租户锁
     */
    void release(TenantId tenantId);
    
    /**
     * 续租（可选，长任务场景）
     */
    boolean renew(TenantId tenantId, Duration additionalTtl);
    
    /**
     * 检查锁是否存在
     */
    boolean exists(TenantId tenantId);
}
```

### 3.3 Checkpoint 存储接口（已有，保持通用）

```java
// 已存在，保持不变
package xyz.firestige.deploy.infrastructure.persistence.checkpoint;

public interface CheckpointRepository {
    void put(TaskId taskId, TaskCheckpoint checkpoint);
    TaskCheckpoint get(TaskId taskId);
    void remove(TaskId taskId);
}
```

---

## 4. 投影数据模型（DTO）

### 4.1 Task 状态投影

```java
// Infrastructure 层 DTO
package xyz.firestige.deploy.infrastructure.persistence.projection;

/**
 * Task 状态投影（纯数据，无行为）
 */
public class TaskStateProjection {
    private TaskId taskId;
    private TenantId tenantId;
    private PlanId planId;
    private TaskStatus status;
    private boolean pauseRequested;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime updatedAt;
    
    // Getter/Setter/Constructor
    // 可序列化为 JSON / DB Row / Redis Hash
}
```

### 4.2 Plan 状态投影

```java
public class PlanStateProjection {
    private PlanId planId;
    private PlanStatus status;
    private List<TaskId> taskIds;
    private int maxConcurrency;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime updatedAt;
}
```

---

## 5. 实现类命名（技术相关）

### 5.1 Redis 实现

```java
// Infrastructure 层实现
package xyz.firestige.deploy.infrastructure.persistence.projection.redis;

public class RedisTaskStateProjectionStore implements TaskStateProjectionStore {
    private final RedisTemplate<String, String> redisTemplate;
    
    @Override
    public void save(TaskStateProjection projection) {
        String key = "executor:task:" + projection.getTaskId();
        Map<String, String> hash = toHash(projection);
        redisTemplate.opsForHash().putAll(key, hash);
        redisTemplate.expire(key, Duration.ofDays(7));
    }
    
    @Override
    public TaskStateProjection load(TaskId taskId) {
        String key = "executor:task:" + taskId;
        Map<Object, Object> hash = redisTemplate.opsForHash().entries(key);
        return fromHash(hash);
    }
    
    // ...其他方法
}

public class RedisTenantLockManager implements TenantLockManager {
    private final RedisTemplate<String, String> redisTemplate;
    
    @Override
    public boolean tryAcquire(TenantId tenantId, TaskId taskId, Duration ttl) {
        String key = "executor:lock:tenant:" + tenantId;
        String value = taskId.toString();
        return Boolean.TRUE.equals(
            redisTemplate.opsForValue().setIfAbsent(key, value, ttl)
        );
    }
    
    // ...其他方法
}
```

### 5.2 JDBC 实现（示例）

```java
// Infrastructure 层实现
package xyz.firestige.deploy.infrastructure.persistence.projection.jdbc;

public class JdbcTaskStateProjectionStore implements TaskStateProjectionStore {
    private final JdbcTemplate jdbcTemplate;
    
    @Override
    public void save(TaskStateProjection projection) {
        String sql = "INSERT INTO task_state_projection " +
                     "(task_id, tenant_id, plan_id, status, ...) " +
                     "VALUES (?, ?, ?, ?) " +
                     "ON CONFLICT (task_id) DO UPDATE SET status=?, updated_at=?";
        jdbcTemplate.update(sql, 
            projection.getTaskId().toString(),
            projection.getTenantId().toString(),
            // ...
        );
    }
    
    // ...其他方法
}

public class JdbcTenantLockManager implements TenantLockManager {
    private final JdbcTemplate jdbcTemplate;
    
    @Override
    public boolean tryAcquire(TenantId tenantId, TaskId taskId, Duration ttl) {
        String sql = "INSERT INTO tenant_lock (tenant_id, task_id, expires_at) " +
                     "VALUES (?, ?, ?) " +
                     "ON CONFLICT (tenant_id) DO NOTHING";
        int rows = jdbcTemplate.update(sql, 
            tenantId.toString(),
            taskId.toString(),
            LocalDateTime.now().plus(ttl)
        );
        return rows > 0;
    }
    
    // ...其他方法
}
```

---

## 6. 事件驱动投影更新（CQRS）

### 6.1 投影更新器（Application 层）

```java
// Application 层事件监听器
package xyz.firestige.deploy.application.projection;

@Component
public class TaskStateProjectionUpdater {
    
    private final TaskStateProjectionStore projectionStore;
    private final TenantTaskIndexStore indexStore;
    
    /**
     * 监听 Task 创建事件
     */
    @EventListener
    public void onTaskCreated(TaskCreatedEvent event) {
        TaskStateProjection projection = TaskStateProjection.builder()
            .taskId(event.getTaskId())
            .tenantId(event.getTenantId())
            .planId(event.getPlanId())
            .status(TaskStatus.PENDING)
            .createdAt(event.getOccurredAt())
            .build();
        
        projectionStore.save(projection);
        indexStore.put(event.getTenantId(), event.getTaskId());
    }
    
    /**
     * 监听 Task 状态变更事件
     */
    @EventListener
    public void onTaskStatusChanged(TaskStatusChangedEvent event) {
        TaskStateProjection projection = projectionStore.load(event.getTaskId());
        if (projection != null) {
            projection.setStatus(event.getNewStatus());
            projection.setUpdatedAt(event.getOccurredAt());
            projectionStore.save(projection);
        }
    }
    
    /**
     * 监听 Task 完成事件
     */
    @EventListener
    public void onTaskCompleted(TaskCompletedEvent event) {
        // 可选：延迟删除或保留一段时间
        projectionStore.remove(event.getTaskId());
        indexStore.remove(event.getTenantId());
    }
    
    // 其他事件监听方法...
}

@Component
public class PlanStateProjectionUpdater {
    // 类似 Task，监听 Plan 事件
}
```

### 6.2 需要新增的领域事件

```java
// Domain 层事件
package xyz.firestige.deploy.domain.task.event;

/**
 * Task 创建事件（新增）
 */
public class TaskCreatedEvent extends TaskStatusEvent {
    private final TenantId tenantId;
    private final PlanId planId;
    // ...
}

/**
 * Task 状态变更事件（通用，已有的事件可复用）
 */
// TaskStartedEvent, TaskPausedEvent, TaskCompletedEvent 等已有
```

---

## 7. 查询服务（Application 层）

```java
// Application 层查询服务
package xyz.firestige.deploy.application.query;

@Service
public class TaskQueryService {
    
    private final TaskStateProjectionStore projectionStore;
    private final TenantTaskIndexStore indexStore;
    private final CheckpointRepository checkpointRepository;
    
    /**
     * 通过租户 ID 查询 Task 状态
     */
    public TaskStatusInfo queryByTenantId(TenantId tenantId) {
        // 1. 通过索引找到 taskId
        TaskId taskId = indexStore.get(tenantId);
        if (taskId == null) {
            return TaskStatusInfo.notFound(tenantId);
        }
        
        // 2. 加载投影
        TaskStateProjection projection = projectionStore.load(taskId);
        if (projection == null) {
            return TaskStatusInfo.notFound(tenantId);
        }
        
        // 3. 检查 Checkpoint
        TaskCheckpoint checkpoint = checkpointRepository.get(taskId);
        
        // 4. 组装返回
        return TaskStatusInfo.builder()
            .taskId(taskId)
            .tenantId(tenantId)
            .planId(projection.getPlanId())
            .status(projection.getStatus())
            .hasCheckpoint(checkpoint != null)
            .lastCompletedStageIndex(checkpoint != null ? checkpoint.getLastCompletedStageIndex() : -1)
            .build();
    }
    
    /**
     * 查询 Plan 状态
     */
    public PlanStatusInfo queryPlanStatus(PlanId planId) {
        // 类似逻辑
    }
}
```

---

## 8. Facade 层集成

```java
// Facade 层（对外 API）
package xyz.firestige.deploy.facade;

public class DeploymentTaskFacade {
    
    private final TaskQueryService taskQueryService;  // 新增
    
    // ========== 查询 API（新增）==========
    
    /**
     * 查询 Task 状态（通过租户 ID）
     */
    public TaskStatusInfo queryTaskStatusByTenant(TenantId tenantId) {
        return taskQueryService.queryByTenantId(tenantId);
    }
    
    /**
     * 查询 Plan 状态
     */
    public PlanStatusInfo queryPlanStatus(PlanId planId) {
        return taskQueryService.queryPlanStatus(planId);
    }
    
    /**
     * 检查是否有 Checkpoint
     */
    public boolean hasCheckpoint(TenantId tenantId) {
        TaskStatusInfo info = taskQueryService.queryByTenantId(tenantId);
        return info != null && info.isHasCheckpoint();
    }
    
    // ========== 命令 API（已有，保持）==========
    // createPlan / startPlan / pauseTask / retryTask / rollbackTask ...
}
```

---

## 9. Spring 配置（实现替换）

```java
// Config 层
@Configuration
public class PersistenceConfiguration {
    
    /**
     * 投影存储实现（可替换）
     */
    @Bean
    @ConditionalOnProperty(name = "executor.projection.store", havingValue = "redis", matchIfMissing = true)
    public TaskStateProjectionStore redisTaskStateProjectionStore(RedisTemplate<String, String> redisTemplate) {
        return new RedisTaskStateProjectionStore(redisTemplate);
    }
    
    @Bean
    @ConditionalOnProperty(name = "executor.projection.store", havingValue = "jdbc")
    public TaskStateProjectionStore jdbcTaskStateProjectionStore(JdbcTemplate jdbcTemplate) {
        return new JdbcTaskStateProjectionStore(jdbcTemplate);
    }
    
    /**
     * 租户锁实现（可替换）
     */
    @Bean
    @ConditionalOnProperty(name = "executor.lock.manager", havingValue = "redis", matchIfMissing = true)
    public TenantLockManager redisTenantLockManager(RedisTemplate<String, String> redisTemplate) {
        return new RedisTenantLockManager(redisTemplate);
    }
    
    @Bean
    @ConditionalOnProperty(name = "executor.lock.manager", havingValue = "jdbc")
    public TenantLockManager jdbcTenantLockManager(JdbcTemplate jdbcTemplate) {
        return new JdbcTenantLockManager(jdbcTemplate);
    }
    
    // 其他 Bean...
}
```

---

## 10. 完整调用链路

### 10.1 写入链路（事件驱动）

```
Task 执行完成一个 Stage
  ↓
TaskDomainService.completeStage()
  ↓ 发布事件
TaskStageCompletedEvent
  ↓ Spring 事件总线
TaskStateProjectionUpdater.onTaskStageCompleted()
  ↓
TaskStateProjectionStore.save()
  ↓ 具体实现
RedisTaskStateProjectionStore / JdbcTaskStateProjectionStore
  ↓
Redis HSET / JDBC UPDATE
```

### 10.2 查询链路

```
外部系统调用 queryTaskStatusByTenant(tenantId)
  ↓
DeploymentTaskFacade
  ↓
TaskQueryService.queryByTenantId()
  ↓
TenantTaskIndexStore.get(tenantId) → taskId
  ↓
TaskStateProjectionStore.load(taskId)
  ↓
CheckpointRepository.get(taskId)
  ↓
组装返回 TaskStatusInfo
```

---

## 11. 一次性实施计划（不做渐进式）

### 11.1 实施前提交点

**提交点 0**：当前可用版本（T-015 已完成）
```bash
git add -A
git commit -m "checkpoint: T-015 completed, before T-016 persistence refactoring"
git tag t-016-before-refactoring
```

### 11.2 实施步骤（一次性完成）

#### 阶段 1：接口与模型定义

- [ ] 创建 `TaskStateProjection` / `PlanStateProjection` DTO
- [ ] 创建 `TaskStateProjectionStore` / `PlanStateProjectionStore` 接口
- [ ] 创建 `TenantTaskIndexStore` 接口
- [ ] 创建 `TenantLockManager` 接口（替换现有 `TenantConflictManager`）
- [ ] 创建领域事件：`TaskCreatedEvent` / `TaskStatusChangedEvent`

#### 阶段 2：Redis 实现

- [ ] 实现 `RedisTaskStateProjectionStore`
- [ ] 实现 `RedisPlanStateProjectionStore`
- [ ] 实现 `RedisTenantTaskIndexStore`
- [ ] 实现 `RedisTenantLockManager`

#### 阶段 3：事件监听与投影更新

- [ ] 实现 `TaskStateProjectionUpdater`（监听所有 Task 事件）
- [ ] 实现 `PlanStateProjectionUpdater`（监听所有 Plan 事件）
- [ ] 在领域服务中发布新增事件

#### 阶段 4：查询服务与 API

- [ ] 实现 `TaskQueryService`
- [ ] 在 `DeploymentTaskFacade` 新增查询方法
- [ ] 替换现有 `TenantConflictManager` 为 `TenantLockManager`

#### 阶段 5：配置与集成

- [ ] 配置 Spring Bean（条件注入）
- [ ] 更新 `ExecutorConfiguration`
- [ ] 添加配置项（`application.yml`）

#### 阶段 6：测试

- [ ] 单元测试：投影存储、锁管理
- [ ] 集成测试：事件驱动投影更新
- [ ] 端到端测试：查询流程、重试流程
- [ ] 重启测试：验证状态持久化

### 11.3 实施后提交点

**提交点 1**：T-016 完成
```bash
git add -A
git commit -m "feat(T-016): add projection-based persistence with Redis/JDBC abstraction"
git tag t-016-completed
```

---

## 12. Redis 存储结构（实现细节）

| Key 模式 | 数据结构 | 用途 | TTL |
|----------|----------|------|-----|
| `executor:task:{taskId}` | Hash | Task 状态投影 | 7天 |
| `executor:plan:{planId}` | Hash | Plan 状态投影 | 7天 |
| `executor:index:tenant:{tenantId}` | String | TenantId → TaskId 索引 | 7天 |
| `executor:lock:tenant:{tenantId}` | String | 租户分布式锁 | 2.5小时 |
| `executor:ckpt:{taskId}` | String | Checkpoint JSON（已有） | 7天 |

---

## 13. JDBC 存储结构（实现细节，可选）

```sql
-- Task 状态投影表
CREATE TABLE task_state_projection (
    task_id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    plan_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    pause_requested BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL,
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_plan_id (plan_id)
);

-- Plan 状态投影表
CREATE TABLE plan_state_projection (
    plan_id VARCHAR(64) PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    task_ids TEXT,  -- JSON 数组
    max_concurrency INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL
);

-- 租户锁表
CREATE TABLE tenant_lock (
    tenant_id VARCHAR(64) PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL,
    acquired_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    INDEX idx_expires_at (expires_at)
);
```

---

## 14. 关键设计决策总结

| 决策点 | 结果 | 理由 |
|--------|------|------|
| **投影仓储接口位置** | Infrastructure 层 | 技术关注点，不污染 Domain |
| **接口命名** | 通用（Store/Manager），不含 Redis | 支持实现替换 |
| **投影更新方式** | 事件驱动 | 解耦命令与查询，符合 CQRS |
| **聚合仓储** | 保持 InMemory | 聚合仍驻留内存，不改变 |
| **实施方式** | 一次性完成 | 避免历史欠账，Git 回退风险控制 |
| **Redis vs JDBC** | 都支持，Spring 条件注入 | 接口隔离，实现可替换 |

---

## 15. Definition of Done

- [ ] 接口定义完成（Store/Manager，通用命名）
- [ ] Redis 实现完成
- [ ] 事件监听器完成（投影更新）
- [ ] 查询服务完成
- [ ] Facade API 完成
- [ ] 租户锁替换完成（TenantLockManager）
- [ ] 单元测试覆盖 > 80%
- [ ] 集成测试通过
- [ ] 重启测试通过（状态可查询）
- [ ] 文档更新（architecture-overview.md、persistence.md）
- [ ] Git 提交（t-016-completed）

---

> **关键改进**：
> 1. ✅ 接口通用命名（Store/Manager），不绑定 Redis
> 2. ✅ 投影仓储在 Infrastructure 层，不污染 Domain
> 3. ✅ 一次性完整实施，不做渐进式
> 4. ✅ Git 提交点明确，回退风险可控
> 5. ✅ 支持 Redis / JDBC 实现替换

