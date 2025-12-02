# Task 重启恢复方案分析 - Checkpoint 与状态驱动问题

> 分析人员：GitHub Copilot  
> 时间：2025-12-02  
> 状态：方案讨论阶段（不修改代码）

---

## 1. 问题描述

### 1.1 当前情况

从 `testRollbackCheckpointBehavior` 测试用例可以看到：

1. **正常执行依赖 Checkpoint**：
   - Task 首次执行在 stage-2 失败
   - stage-1 成功后保存了 Checkpoint（`lastCompletedStageIndex=0`）
   - 回滚时从 Checkpoint 恢复，只执行 stage-1 和 stage-2（部分回滚）

2. **回滚成功依赖两个输入**：
   ```java
   TaskRuntimeContext rollbackContext = new TaskRuntimeContext(...);
   rollbackContext.requestRollback(prevVersion);  // 设置回滚标志
   rollbackContext.addVariable("deployVersion", prevVersion);  // 使用旧版本
   taskRuntimeRepository.saveContext(task.getTaskId(), rollbackContext);
   ```

### 1.2 核心问题

**重启场景的困境**：

```
应用重启后：
├── 已知信息（外部传入）
│   ├── TenantConfig（包含 previousConfig）
│   ├── 上一次执行失败的 StageName
│   └── 所有 Task 有相同的 StageList
│
├── 可恢复数据（Redis）
│   ├── Checkpoint（lastCompletedStageIndex, completedStageNames）
│   ├── Task 状态投影（TaskStateProjection）
│   └── Tenant 索引（TenantId → TaskId）
│
└── 问题：如何重建正确的执行上下文？
    ├── ❌ 问题1：Task 状态是 FAILED，但重启后内存中没有 TaskAggregate
    ├── ❌ 问题2：Checkpoint 依赖 Task 状态机驱动（FAILED → ROLLING_BACK）
    ├── ❌ 问题3：TaskExecutor 需要状态转换合法才能执行
    └── ❌ 问题4：StageFactory 需要正确的配置来生成 Stages
```

---

## 2. 架构分析

### 2.1 当前执行流程（内存态）

```
正常执行流（无重启）：
┌─────────────────────────────────────────────────┐
│ 1. 创建 Task                                     │
│    TaskAggregate task = new TaskAggregate(...)  │
│    status = PENDING                             │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│ 2. 执行失败                                      │
│    TaskExecutor.execute()                       │
│    → stage-2 失败                               │
│    → taskDomainService.failTask()               │
│    → status = FAILED                            │
│    → checkpointService.saveCheckpoint()         │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│ 3. 回滚（内存态 Task 存在）                       │
│    TaskRuntimeContext ctx = new ...             │
│    ctx.requestRollback(prevVersion)             │
│    taskRuntimeRepository.saveContext(taskId, ctx)│
│                                                  │
│    TaskExecutor executor = factory.create(       │
│        task,  ← 内存中的聚合                     │
│        stages,                                   │
│        ctx                                       │
│    )                                             │
│    executor.execute()                            │
│    → StateTransitionService.canTransition()      │
│       ✅ 检查 task.status (FAILED) 合法          │
│    → 执行成功                                    │
└─────────────────────────────────────────────────┘
```

### 2.2 重启后恢复流程（问题所在）

```
重启后恢复流（Task 内存态丢失）：
┌─────────────────────────────────────────────────┐
│ 1. 外部输入                                      │
│    TenantConfig config (有 previousConfig)      │
│    String failedStageName = "stage-2"           │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│ 2. 从 Redis 恢复状态                             │
│    TaskStateProjection projection =             │
│        projectStore.get(taskId)                 │
│    → status = FAILED                            │
│    → lastCompletedStageIndex = 0                │
│    → stageNames = ["stage-1","stage-2","stage-3"]│
│                                                  │
│    TaskCheckpoint checkpoint =                  │
│        checkpointStore.get(taskId)              │
│    → lastCompletedStageIndex = 0                │
│    → completedStageNames = ["stage-1"]          │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│ ❓ 问题：如何重建 TaskAggregate？                 │
│                                                  │
│ Option A（当前不可行）：                          │
│    TaskAggregate task = new TaskAggregate(...)  │
│    → status = CREATED（初始状态）                │
│    → ❌ StateTransitionService 检查失败          │
│       （CREATED 不能转换到 ROLLING_BACK）        │
│                                                  │
│ Option B（绕过状态机）：                          │
│    TaskAggregate task = reconstitute(projection)│
│    → 直接设置 status = FAILED                    │
│    → ⚠️ 绕过领域模型不变式保护                   │
│                                                  │
│ Option C（事件溯源）：                            │
│    重放所有历史事件重建聚合状态                   │
│    → ❌ 当前没有事件存储                         │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│ 3. 构建执行上下文                                │
│    TaskRuntimeContext ctx = new ...             │
│    ctx.requestRollback(prevVersion)             │
│                                                  │
│    List<TaskStage> stages = stageFactory.create( │
│        config.getPreviousConfig()  ← 旧配置      │
│    )                                             │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│ 4. 创建执行器                                    │
│    TaskExecutor executor = factory.create(       │
│        task,   ← ❓ 如何获得正确状态的 task？    │
│        stages,                                   │
│        ctx                                       │
│    )                                             │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│ 5. 执行回滚                                      │
│    executor.execute()                            │
│    → StateTransitionService.canTransition(       │
│          task,                                   │
│          TaskStatus.ROLLING_BACK,                │
│          ctx                                     │
│       )                                          │
│    → ❓ 需要 task.status == FAILED 才合法        │
└─────────────────────────────────────────────────┘
```

---

## 3. 解决方案分析

### 方案 A：聚合重构（Reconstitution from Projection）

#### 3.1 核心思想

在 DDD 中，当聚合需要从持久化状态恢复时，可以提供专门的"重构构造器"（Reconstitution Constructor），直接设置内部状态，绕过正常的业务规则校验。

#### 3.2 实现方式

```java
public class TaskAggregate {
    
    // 正常业务构造器（有不变式保护）
    public TaskAggregate(PlanId planId, TenantId tenantId, ...) {
        this.status = TaskStatus.CREATED;  // 初始状态
        // ... 业务规则校验
    }
    
    // ✅ 重构构造器（从投影恢复）
    public static TaskAggregate reconstitute(
        TaskStateProjection projection,
        TaskCheckpoint checkpoint
    ) {
        TaskAggregate task = new TaskAggregate();
        
        // 直接设置内部状态（绕过业务规则）
        task.taskId = TaskId.of(projection.getTaskId());
        task.tenantId = TenantId.of(projection.getTenantId());
        task.planId = PlanId.of(projection.getPlanId());
        task.status = projection.getStatus();  // ✅ 直接设置为 FAILED
        task.stageProgress = new StageProgress(
            projection.getStageNames(),
            checkpoint != null ? checkpoint.getLastCompletedStageIndex() : -1
        );
        task.checkpoint = checkpoint;
        
        return task;
    }
}
```

#### 3.3 恢复流程

```java
// 1. 查询投影和 Checkpoint
TaskStateProjection projection = projectionStore.get(taskId);
TaskCheckpoint checkpoint = checkpointStore.get(taskId);

// 2. 重构聚合（带正确状态）
TaskAggregate task = TaskAggregate.reconstitute(projection, checkpoint);
// task.status == FAILED  ✅ 状态正确

// 3. 构建回滚上下文
TaskRuntimeContext ctx = new TaskRuntimeContext(...);
ctx.requestRollback(prevVersion);

// 4. 生成旧配置的 Stages
List<TaskStage> stages = stageFactory.create(
    config.getPreviousConfig()
);

// 5. 创建执行器并执行
TaskExecutor executor = factory.create(task, stages, ctx);
TaskResult result = executor.execute();
// ✅ StateTransitionService 检查通过（FAILED → ROLLING_BACK）
```

#### 3.4 优点

- ✅ 符合 DDD 重构模式（Reconstitution Pattern）
- ✅ 不破坏现有状态机逻辑
- ✅ 清晰的语义：`reconstitute` vs 正常构造器
- ✅ 可以在重构时做必要的数据校验（非业务规则）

#### 3.5 缺点

- ⚠️ 需要在聚合中添加新方法
- ⚠️ 绕过了部分不变式保护（需要文档说明）
- ⚠️ 重构逻辑需要与投影保持同步

---

### 方案 B：状态机扩展（支持恢复转换）

#### 3.1 核心思想

在状态机中添加特殊的"恢复"转换路径，允许从 CREATED 直接跳到恢复场景需要的状态。

#### 3.2 实现方式

```java
public class StateTransitionService {
    
    public boolean canTransition(
        TaskAggregate task,
        TaskStatus targetStatus,
        TaskRuntimeContext context
    ) {
        TaskStatus currentStatus = task.getStatus();
        
        // ✅ 特殊情况：恢复模式
        if (context.isRecoveryMode()) {
            // 允许从 CREATED 跳到恢复目标状态
            return switch (targetStatus) {
                case RUNNING -> true;      // 恢复执行
                case ROLLING_BACK -> true; // 恢复回滚
                case PAUSED -> true;       // 恢复暂停
                default -> false;
            };
        }
        
        // 正常状态机逻辑
        return switch (currentStatus) {
            case CREATED -> targetStatus == TaskStatus.PENDING;
            case PENDING -> targetStatus == TaskStatus.RUNNING;
            // ... 其他转换规则
        };
    }
}
```

#### 3.3 恢复流程

```java
// 1. 创建新 Task（初始状态 CREATED）
TaskAggregate task = new TaskAggregate(planId, tenantId, ...);
// task.status == CREATED

// 2. 从 Redis 恢复 Checkpoint
TaskCheckpoint checkpoint = checkpointStore.get(taskId);
task.restoreFromCheckpoint(checkpoint);  // ❌ 会校验状态，不允许

// 3. 构建恢复上下文
TaskRuntimeContext ctx = new TaskRuntimeContext(...);
ctx.setRecoveryMode(true);  // ✅ 标记为恢复模式
ctx.requestRollback(prevVersion);

// 4. 执行回滚
TaskExecutor executor = factory.create(task, stages, ctx);
executor.execute();
// ✅ StateTransitionService 检查通过（恢复模式允许 CREATED → ROLLING_BACK）
```

#### 3.4 优点

- ✅ 不修改聚合内部状态
- ✅ 状态转换逻辑集中在一处
- ✅ 恢复模式显式标记，便于审计

#### 3.5 缺点

- ❌ 状态机逻辑变复杂（增加特殊分支）
- ❌ Task 实际状态不正确（CREATED vs FAILED）
- ❌ 可能导致其他业务逻辑出错（依赖状态判断的地方）
- ❌ 违反了 DDD 聚合状态一致性原则

---

### 方案 C：事件溯源（Event Sourcing）

#### 3.1 核心思想

保存所有领域事件，重启后重放事件流重建聚合状态。

#### 3.2 实现方式

```java
// 1. 保存事件到事件存储
public class RedisEventStore {
    // Key: executor:events:{taskId}
    // Value: List<DomainEvent>（JSON 序列化）
    
    public void append(TaskId taskId, DomainEvent event) {
        String key = "executor:events:" + taskId;
        redis.rpush(key, serialize(event));
    }
    
    public List<DomainEvent> getEvents(TaskId taskId) {
        String key = "executor:events:" + taskId;
        return redis.lrange(key, 0, -1).stream()
            .map(this::deserialize)
            .toList();
    }
}

// 2. 从事件流重建聚合
public class TaskAggregate {
    
    public static TaskAggregate replayEvents(List<DomainEvent> events) {
        TaskAggregate task = new TaskAggregate();  // 空对象
        
        for (DomainEvent event : events) {
            task.apply(event);  // 应用每个事件
        }
        
        return task;
    }
    
    private void apply(DomainEvent event) {
        switch (event) {
            case TaskCreatedEvent e -> {
                this.taskId = e.getTaskId();
                this.status = TaskStatus.CREATED;
            }
            case TaskStartedEvent e -> {
                this.status = TaskStatus.RUNNING;
            }
            case TaskFailedEvent e -> {
                this.status = TaskStatus.FAILED;
            }
            // ... 其他事件
        }
    }
}
```

#### 3.3 恢复流程

```java
// 1. 查询事件流
List<DomainEvent> events = eventStore.getEvents(taskId);

// 2. 重放事件重建聚合
TaskAggregate task = TaskAggregate.replayEvents(events);
// task.status == FAILED  ✅ 状态正确

// 3. 执行回滚
TaskExecutor executor = factory.create(task, stages, ctx);
executor.execute();
```

#### 3.4 优点

- ✅ 完美的状态恢复（事件是真相之源）
- ✅ 可审计（所有状态变更有迹可循）
- ✅ 符合 CQRS/ES 最佳实践
- ✅ 可以支持时间旅行（恢复到任意历史状态）

#### 3.5 缺点

- ❌ 架构变更巨大（需要引入事件存储）
- ❌ 性能开销（每次恢复需要重放事件）
- ❌ 事件版本管理复杂（事件 schema 演进）
- ❌ 当前项目不需要这么重的方案

---

### 方案 D：混合方案（Checkpoint + 快照）

#### 3.1 核心思想

在 Checkpoint 中不仅保存进度信息，还保存关键的状态快照，重启时直接恢复。

#### 3.2 实现方式

```java
public class TaskCheckpoint {
    // 已有字段
    private int lastCompletedStageIndex;
    private List<String> completedStageNames;
    private Map<String, Object> contextData;
    private LocalDateTime savedAt;
    
    // ✅ 新增：状态快照
    private TaskStatus status;  // 保存时的 Task 状态
    private boolean pauseRequested;
    private List<String> allStageNames;  // 完整的 Stage 列表
}

public class TaskAggregate {
    
    // ✅ 从 Checkpoint 快照恢复
    public static TaskAggregate restoreFromSnapshot(
        TaskCheckpoint checkpoint,
        TenantConfig config  // 用于重建其他信息
    ) {
        TaskAggregate task = new TaskAggregate();
        
        // 从快照恢复状态
        task.taskId = TaskId.of(checkpoint.getTaskId());
        task.status = checkpoint.getStatus();  // ✅ 恢复为 FAILED
        task.stageProgress = new StageProgress(
            checkpoint.getAllStageNames(),
            checkpoint.getLastCompletedStageIndex()
        );
        task.pauseRequested = checkpoint.isPauseRequested();
        
        return task;
    }
}
```

#### 3.3 恢复流程

```java
// 1. 查询 Checkpoint（包含状态快照）
TaskCheckpoint checkpoint = checkpointStore.get(taskId);

// 2. 从快照恢复聚合
TaskAggregate task = TaskAggregate.restoreFromSnapshot(
    checkpoint,
    config
);
// task.status == FAILED  ✅ 状态正确

// 3. 执行回滚
TaskExecutor executor = factory.create(task, stages, ctx);
executor.execute();
```

#### 3.4 优点

- ✅ 实现简单（扩展现有 Checkpoint）
- ✅ 性能好（直接恢复，无需重放事件）
- ✅ 不需要架构大改
- ✅ 状态完整（包含所有必要信息）

#### 3.5 缺点

- ⚠️ Checkpoint 变大（需要序列化更多字段）
- ⚠️ 快照一致性（需要确保快照和进度同步）
- ⚠️ 仍然绕过了业务构造器（需要 reconstitute 方法）

---

## 4. 方案对比

| 维度 | 方案A<br>聚合重构 | 方案B<br>状态机扩展 | 方案C<br>事件溯源 | 方案D<br>快照恢复 |
|------|-----------------|-------------------|-----------------|-----------------|
| **实现复杂度** | ⭐⭐ 中等 | ⭐ 简单 | ⭐⭐⭐⭐ 很高 | ⭐⭐ 中等 |
| **架构影响** | 小（仅聚合） | 小（状态机） | 大（全系统） | 小（Checkpoint） |
| **状态正确性** | ✅ 完全正确 | ❌ 不正确 | ✅ 完全正确 | ✅ 完全正确 |
| **性能** | ✅ 快 | ✅ 快 | ❌ 慢（重放事件） | ✅ 快 |
| **可维护性** | ✅ 清晰 | ⚠️ 特殊逻辑 | ⚠️ 复杂 | ✅ 清晰 |
| **DDD 纯度** | ✅ 符合 | ❌ 违反 | ✅ 最佳 | ✅ 符合 |
| **风险** | 低 | 中 | 高 | 低 |
| **推荐指数** | ⭐⭐⭐⭐ | ⭐⭐ | ⭐ | ⭐⭐⭐⭐⭐ |

---

## 5. 推荐方案：方案 D（Checkpoint 快照恢复）

### 5.1 选择理由

1. **实现成本最低**：
   - 扩展现有 `TaskCheckpoint` 即可
   - 无需引入新架构组件
   - 无需修改状态机逻辑

2. **状态正确性有保障**：
   - 快照包含完整状态信息
   - 恢复后的聚合与失败时完全一致

3. **符合当前架构**：
   - 已有 Checkpoint 机制
   - 已有投影存储（可复用）
   - 已有 reconstitute 模式（方案 A 的基础）

4. **扩展性好**：
   - 未来可以逐步演进到事件溯源
   - Checkpoint 快照可以作为事件快照（Snapshot）使用

### 5.2 实施步骤

#### Step 1：扩展 TaskCheckpoint（增加状态快照）

```java
public class TaskCheckpoint {
    // 已有字段
    private int lastCompletedStageIndex;
    private List<String> completedStageNames;
    private Map<String, Object> contextData;
    private LocalDateTime savedAt;
    
    // ✅ 新增：状态快照（用于重启恢复）
    private String taskId;           // Task ID
    private String tenantId;         // Tenant ID
    private String planId;           // Plan ID
    private TaskStatus status;       // Task 状态
    private boolean pauseRequested;  // 暂停标志
    private List<String> allStageNames;  // 完整 Stage 列表
    
    // 构造器和 getter/setter
}
```

#### Step 2：修改 CheckpointService（保存完整快照）

```java
@Service
public class CheckpointService {
    
    public void saveCheckpoint(TaskAggregate task) {
        TaskCheckpoint checkpoint = new TaskCheckpoint();
        
        // 已有字段
        checkpoint.setLastCompletedStageIndex(
            task.getStageProgress().getLastCompletedIndex()
        );
        checkpoint.setCompletedStageNames(
            task.getStageProgress().getCompletedStageNames()
        );
        checkpoint.setSavedAt(LocalDateTime.now());
        
        // ✅ 新增：状态快照
        checkpoint.setTaskId(task.getTaskId().getValue());
        checkpoint.setTenantId(task.getTenantId().getValue());
        checkpoint.setPlanId(task.getPlanId().getValue());
        checkpoint.setStatus(task.getStatus());  // ✅ 保存状态
        checkpoint.setPauseRequested(task.isPauseRequested());
        checkpoint.setAllStageNames(task.getStageNames());  // ✅ 完整列表
        
        store.put(task.getTaskId(), checkpoint);
    }
}
```

#### Step 3：添加聚合重构方法

```java
public class TaskAggregate {
    
    /**
     * 从 Checkpoint 快照恢复聚合（重启场景专用）
     * <p>
     * ⚠️ 注意：此方法绕过了正常的业务构造器和状态转换，
     * 仅用于重启后从持久化快照恢复聚合状态。
     * 
     * @param checkpoint 包含状态快照的检查点
     * @return 重构的聚合
     */
    public static TaskAggregate restoreFromCheckpoint(TaskCheckpoint checkpoint) {
        TaskAggregate task = new TaskAggregate();
        
        // 基础信息
        task.taskId = TaskId.of(checkpoint.getTaskId());
        task.tenantId = TenantId.of(checkpoint.getTenantId());
        task.planId = PlanId.of(checkpoint.getPlanId());
        
        // ✅ 状态快照
        task.status = checkpoint.getStatus();  // 直接恢复为 FAILED
        task.pauseRequested = checkpoint.isPauseRequested();
        
        // ✅ 进度信息
        task.stageProgress = new StageProgress(
            checkpoint.getAllStageNames(),
            checkpoint.getLastCompletedStageIndex()
        );
        
        // ✅ Checkpoint 引用
        task.checkpoint = checkpoint;
        
        return task;
    }
}
```

#### Step 4：外部恢复服务（应用层）

```java
@Service
public class TaskRecoveryService {
    
    private final CheckpointService checkpointService;
    private final StageFactory stageFactory;
    private final TaskExecutorFactory executorFactory;
    private final TaskRuntimeRepository contextRepository;
    
    /**
     * 重启后恢复回滚任务
     * 
     * @param tenantId 租户 ID
     * @param config 租户配置（包含 previousConfig）
     * @param failedStageName 失败的 Stage 名称（可选，用于验证）
     * @return 执行结果
     */
    public TaskResult recoverAndRollback(
        TenantId tenantId,
        TenantConfig config,
        String failedStageName
    ) {
        // 1. 查询 Checkpoint
        TaskCheckpoint checkpoint = checkpointService.loadCheckpointByTenant(tenantId);
        if (checkpoint == null) {
            throw new IllegalStateException("找不到租户的检查点: " + tenantId);
        }
        
        // 2. 从快照恢复聚合
        TaskAggregate task = TaskAggregate.restoreFromCheckpoint(checkpoint);
        log.info("从 Checkpoint 恢复 Task: taskId={}, status={}", 
            task.getTaskId(), task.getStatus());
        
        // 3. 验证状态（可选）
        if (task.getStatus() != TaskStatus.FAILED) {
            throw new IllegalStateException(
                "Task 状态不是 FAILED，无法回滚: " + task.getStatus()
            );
        }
        
        // 4. 构建回滚上下文
        TaskRuntimeContext ctx = new TaskRuntimeContext(
            task.getPlanId(),
            task.getTaskId(),
            task.getTenantId()
        );
        String prevVersion = config.getPreviousConfig()
            .getDeployUnitVersion().toString();
        ctx.requestRollback(prevVersion);
        ctx.addVariable("deployVersion", prevVersion);
        
        // 5. 使用旧配置生成 Stages
        List<TaskStage> stages = stageFactory.createStages(
            config.getPreviousConfig()
        );
        
        // 6. 保存上下文
        contextRepository.saveContext(task.getTaskId(), ctx);
        
        // 7. 创建执行器并执行回滚
        TaskExecutor executor = executorFactory.create(task, stages, ctx);
        TaskResult result = executor.execute();
        
        log.info("回滚完成: taskId={}, success={}", 
            task.getTaskId(), result.isSuccess());
        
        return result;
    }
}
```

#### Step 5：使用示例

```java
// 重启后外部系统调用
@RestController
public class RecoveryController {
    
    @Autowired
    private TaskRecoveryService recoveryService;
    
    @PostMapping("/api/recovery/rollback")
    public ResponseEntity<?> recoverAndRollback(
        @RequestBody RecoveryRequest request
    ) {
        TenantId tenantId = TenantId.of(request.getTenantId());
        TenantConfig config = configService.getConfig(tenantId);
        
        TaskResult result = recoveryService.recoverAndRollback(
            tenantId,
            config,
            request.getFailedStageName()
        );
        
        return ResponseEntity.ok(result);
    }
}
```

### 5.3 方案优势总结

1. **无缝集成**：
   - 利用现有 Checkpoint 机制
   - 不影响正常执行流程
   - 重启恢复是独立路径

2. **状态完整**：
   - Checkpoint 包含所有必要信息
   - 恢复的聚合与失败时一致
   - 状态机检查通过

3. **清晰语义**：
   - `restoreFromCheckpoint()` 明确标识恢复场景
   - 与正常构造器分离
   - 注释说明绕过业务规则的原因

4. **易于测试**：
   - 可以单独测试恢复逻辑
   - 不影响现有测试用例
   - 恢复场景可模拟

---

## 6. 其他考虑

### 6.1 Checkpoint TTL 管理

- 当前 TTL：7 天
- 建议：根据业务需求调整（如保留 30 天以支持长期恢复）
- 监控：Checkpoint 过期后无法恢复，需要告警

### 6.2 投影一致性

- TaskStateProjection 和 Checkpoint 应保持同步
- 建议：在 TaskStateProjectionUpdater 中同时更新 Checkpoint
- 或：Checkpoint 作为真相之源，投影可选

### 6.3 并发冲突

- 重启恢复时可能有正在执行的 Task
- 建议：使用分布式租户锁（已有 RedisTenantLockManager）
- 恢复前先尝试获取锁，避免冲突

### 6.4 回滚失败处理

- 回滚可能再次失败
- 建议：保留回滚前的 Checkpoint
- 支持多次重试回滚

---

## 7. 实施计划

### Phase 1：扩展 Checkpoint（2天）
- [ ] 扩展 `TaskCheckpoint` 数据结构
- [ ] 修改 `CheckpointService.saveCheckpoint()`
- [ ] 更新序列化/反序列化逻辑
- [ ] 单元测试

### Phase 2：聚合重构方法（1天）
- [ ] 添加 `TaskAggregate.restoreFromCheckpoint()`
- [ ] 单元测试（验证状态恢复）
- [ ] 集成测试（验证状态机检查）

### Phase 3：恢复服务（2天）
- [ ] 实现 `TaskRecoveryService`
- [ ] 添加外部 API（可选）
- [ ] 集成测试（模拟重启场景）
- [ ] 端到端测试

### Phase 4：文档和监控（1天）
- [ ] 更新架构文档
- [ ] 添加恢复流程说明
- [ ] 配置监控告警（Checkpoint 缺失）
- [ ] 运维手册

**总计：6 天**

---

## 8. 结论（修订版）

### 8.1 原推荐方案（方案 D）

**方案 D**：Checkpoint 快照恢复

**核心思路**：扩展 Checkpoint 保存完整状态快照

**优势**：状态完整、实现简单

**缺点**：
- ⚠️ Checkpoint 变大（序列化更多字段）
- ⚠️ 需要持久化状态到 Redis
- ⚠️ 依赖旧的 taskId 查询

---

### 8.2 ⭐ 新方案：无状态执行器（推荐）

> **架构定位**：执行器模块 = 纯粹的任务执行引擎
> - ✅ 负责：执行逻辑、状态机、事件发布
> - ❌ 不负责：状态持久化、查询 API、投影管理
> - 🎯 依赖：调用方负责所有持久化和状态管理

> 基于以下前提：
> 1. 所有 Task 的 StageList **全局固定且顺序唯一**
> 2. 调用方可以提供 `TenantConfig`、`lastCompletedStageName` 和 `taskId`
> 3. **执行器完全无状态**：不持久化任何数据，所有输入由调用方提供

**核心洞察**：
- ✅ **执行器只管执行**：不持久化状态、不提供查询、不管理投影
- ✅ **调用方管状态**：持久化进度、记录 taskId、追踪失败
- ✅ **完全无状态**：仅凭输入参数即可重建完整流程
- ✅ **职责清晰**：执行器 = 无状态函数，调用方 = 状态管理者

#### 8.2.1 方案设计

**输入参数**：
```java
public class RestartRecoveryRequest {
    TenantConfig config;              // 包含 tenantId, planId, previousConfig
    String lastCompletedStageName;    // 上次执行完成的 Stage 名称
    RecoveryMode mode;                // RETRY / ROLLBACK
    String taskId;                    // 调用方提供的 taskId（新建或复用）
}
```

**调用方职责**：
```java
// 1. 监听执行器事件，持久化状态
@EventListener
void onTaskCreated(TaskCreatedEvent event) {
    externalDB.save(event.getTenantId(), event.getTaskId(), "CREATED");
}

@EventListener
void onStageCompleted(TaskStageCompletedEvent event) {
    externalDB.updateProgress(event.getTaskId(), event.getStageName());
}

@EventListener
void onTaskFailed(TaskFailedEvent event) {
    externalDB.save(event.getTaskId(), "FAILED", event.getFailureInfo());
}

// 2. 重启后查询状态，构建恢复请求
public void recoverAfterRestart(String tenantId) {
    // 从外部数据库查询
    TaskRecord record = externalDB.getLatestTask(tenantId);
    
    RestartRecoveryRequest request = new RestartRecoveryRequest(
        config,
        record.getLastCompletedStageName(),  // 外部持久化
        RecoveryMode.ROLLBACK,
        record.getTaskId()                   // 外部持久化
    );
    
    // 调用执行器
    executorService.recoverFromRestart(request);
}
```

**重建逻辑**：
```java
@Service
public class TaskRecoveryService {
    
    /**
     * 重启后恢复执行（无状态重建）
     * <p>
     * 关键：
     * 1. 使用新 taskId（不复用旧 taskId）
     * 2. 从 lastCompletedStageName 计算起点索引
     * 3. 根据 mode 选择配置（当前/旧版本）
     */
    public TaskResult recoverFromRestart(RestartRecoveryRequest request) {
        
        // 1. 生成新 TaskId（关键！）
        TaskId newTaskId = TaskId.generate();
        TenantId tenantId = request.getConfig().getTenantId();
        PlanId planId = request.getConfig().getPlanId();
        
        // 2. 创建新 TaskAggregate（初始状态 CREATED）
        TaskAggregate task = new TaskAggregate(
            newTaskId,
            planId,
            tenantId,
            request.getConfig()
        );
        // ✅ status = CREATED（正常状态，无需特殊处理）
        
        // 3. 根据恢复模式选择配置
        TenantConfig targetConfig = switch (request.getMode()) {
            case RETRY -> request.getConfig();  // 当前版本
            case ROLLBACK -> request.getConfig().getPreviousConfig();  // 旧版本
        };
        
        // 4. 生成 StageList（全局固定）
        List<TaskStage> stages = stageFactory.createStages(targetConfig);
        
        // 5. 从 lastCompletedStageName 计算起点索引
        int startIndex = calculateStartIndex(
            stages,
            request.getLastCompletedStageName()
        );
        
        // 6. 构建执行上下文
        TaskRuntimeContext ctx = new TaskRuntimeContext(
            planId,
            newTaskId,
            tenantId
        );
        
        // 设置起点索引（跳过已完成的 Stage）
        ctx.setStartIndex(startIndex);
        
        // 根据模式设置标志
        if (request.getMode() == RecoveryMode.RETRY) {
            ctx.requestRetry(true);  // 从断点恢复
        } else {
            String version = targetConfig.getDeployUnitVersion().toString();
            ctx.requestRollback(version);
            ctx.addVariable("deployVersion", version);
        }
        
        // 7. 创建执行器并执行
        TaskExecutor executor = executorFactory.create(task, stages, ctx);
        TaskResult result = executor.execute();
        
        log.info("重启恢复完成: newTaskId={}, mode={}, startIndex={}, success={}", 
            newTaskId, request.getMode(), startIndex, result.isSuccess());
        
        return result;
    }
    
    /**
     * 计算起点索引
     * <p>
     * 规则：从 lastCompletedStageName 的下一个 Stage 开始
     */
    private int calculateStartIndex(
        List<TaskStage> stages,
        String lastCompletedStageName
    ) {
        if (lastCompletedStageName == null) {
            return 0;  // 从头开始
        }
        
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).getName().equals(lastCompletedStageName)) {
                return i + 1;  // 下一个 Stage
            }
        }
        
        throw new IllegalArgumentException(
            "找不到 Stage: " + lastCompletedStageName
        );
    }
}
```

#### 8.2.2 TaskExecutor 支持（已有，无需修改）

```java
public class TaskExecutor {
    
    public TaskResult execute() {
        // ...
        
        // 4. ✅ 从上下文读取起点索引（已有逻辑）
        int startIndex = context.getStartIndex();  // 外部设置的起点
        log.info("从 Stage 索引 {} 开始执行, taskId: {}", startIndex, taskId);
        
        // 5. 执行 Stages（从 startIndex 开始）
        for (int i = startIndex; i < stages.size(); i++) {
            // 正常执行...
        }
    }
}
```

**关键点**：
- `TaskRuntimeContext.setStartIndex()` 已存在 ✅
- `TaskExecutor.execute()` 已支持从指定索引开始 ✅
- 无需修改执行器代码 ✅

#### 8.2.3 使用示例

```java
// 重启后外部系统调用
@RestController
public class RecoveryController {
    
    @PostMapping("/api/recovery/restart")
    public ResponseEntity<?> recoverFromRestart(
        @RequestBody RestartRequest request
    ) {
        // 1. 从外部数据库查询租户配置
        TenantConfig config = configService.getConfig(request.getTenantId());
        
        // 2. 构建恢复请求
        RestartRecoveryRequest recoveryRequest = new RestartRecoveryRequest(
            config,
            request.getLastCompletedStageName(),  // 外部提供
            RecoveryMode.ROLLBACK  // 或 RETRY
        );
        
        // 3. 执行恢复
        TaskResult result = recoveryService.recoverFromRestart(recoveryRequest);
        
        return ResponseEntity.ok(result);
    }
}
```

#### 8.2.4 方案优势

| 维度 | 方案 D（快照） | ⭐ 新方案（无状态执行器） |
|------|--------------|-------------------------|
| **架构定位** | ⚠️ 执行器兼做持久化 | ✅ 纯粹的执行引擎 |
| **职责边界** | ⚠️ 模糊（执行+存储） | ✅ 清晰（只管执行） |
| **持久化依赖** | ❌ 依赖 Redis | ✅ 完全无持久化 |
| **taskId 管理** | ⚠️ 需要查询 Redis | ✅ 调用方提供 |
| **Checkpoint 查询** | ❌ 需要查询 Redis | ✅ 调用方提供进度 |
| **投影管理** | ❌ 需要管理投影 | ✅ 无投影，不需要 |
| **状态查询** | ⚠️ 提供查询 API | ✅ 调用方自行查询 |
| **Redis 故障** | ❌ 无法恢复 | ✅ 不受影响 |
| **实现复杂度** | ⭐⭐ 中等 | ⭐ 极简 |
| **扩展性** | ⚠️ 依赖 Redis | ✅ 完全无状态 |
| **测试性** | ⚠️ 需要 Redis | ✅ 纯内存测试 |
| **可维护性** | ⚠️ 状态一致性 | ✅ 无状态逻辑 |
| **模块独立性** | ❌ 耦合存储 | ✅ 完全独立 |

#### 8.2.5 关键前提验证

**前提 1：StageList 全局固定**
```java
// 验证：StageFactory 对相同配置总是生成相同的 Stage 列表
List<TaskStage> stages1 = stageFactory.createStages(config);
List<TaskStage> stages2 = stageFactory.createStages(config);
assert stages1.equals(stages2);  // 必须相同
```

**前提 2：调用方持久化状态**
```java
// 调用方需要持久化以下信息：
// 1. taskId（租户 → taskId 映射）
// 2. lastCompletedStageName（执行进度）
// 3. 失败原因（可选，用于审计）

// 存储方案选择：
// - 关系数据库（MySQL/PostgreSQL）
// - 文档数据库（MongoDB）
// - 事件存储（Event Store）
// - 甚至文件系统（简单场景）
```

**前提 3：事件驱动集成**
```java
// 调用方监听执行器发布的事件
@EventListener
void onTaskEvent(TaskStatusEvent event) {
    // 更新外部状态
    externalDB.updateTaskStatus(
        event.getTaskId(),
        event.getStatus(),
        event.getTimestamp()
    );
}
```

#### 8.2.6 调用方集成模式

##### 模式 1：事件驱动持久化（推荐）

```java
/**
 * 调用方的事件监听器（负责持久化）
 */
@Component
public class TaskStateTracker {
    
    @Autowired
    private ExternalTaskRepository repository;
    
    // 监听创建事件
    @EventListener
    public void onTaskCreated(TaskCreatedEvent event) {
        repository.save(TaskRecord.builder()
            .taskId(event.getTaskId().getValue())
            .tenantId(event.getTenantId().getValue())
            .planId(event.getPlanId().getValue())
            .status("CREATED")
            .stageNames(event.getStageNames())
            .createdAt(event.getTimestamp())
            .build());
    }
    
    // 监听阶段完成事件（关键！）
    @EventListener
    public void onStageCompleted(TaskStageCompletedEvent event) {
        repository.updateProgress(
            event.getTaskId().getValue(),
            event.getStageName(),  // 保存最后完成的 Stage 名称
            event.getTimestamp()
        );
    }
    
    // 监听失败事件
    @EventListener
    public void onTaskFailed(TaskFailedEvent event) {
        repository.updateStatus(
            event.getTaskId().getValue(),
            "FAILED",
            event.getFailureInfo(),
            event.getTimestamp()
        );
    }
    
    // 监听完成事件
    @EventListener
    public void onTaskCompleted(TaskCompletedEvent event) {
        repository.updateStatus(
            event.getTaskId().getValue(),
            "COMPLETED",
            null,
            event.getTimestamp()
        );
    }
}
```

##### 模式 2：重启后恢复

```java
/**
 * 调用方的恢复服务
 */
@Service
public class ApplicationRecoveryService {
    
    @Autowired
    private ExternalTaskRepository taskRepository;
    
    @Autowired
    private TenantConfigService configService;
    
    @Autowired
    private TaskRecoveryService executorRecoveryService;  // 执行器提供
    
    /**
     * 重启后恢复失败的任务
     */
    public void recoverFailedTasks() {
        // 1. 从外部数据库查询失败的任务
        List<TaskRecord> failedTasks = taskRepository.findByStatus("FAILED");
        
        for (TaskRecord record : failedTasks) {
            try {
                // 2. 查询租户配置
                TenantId tenantId = TenantId.of(record.getTenantId());
                TenantConfig config = configService.getConfig(tenantId);
                
                // 3. 构建恢复请求
                RestartRecoveryRequest request = new RestartRecoveryRequest(
                    config,
                    record.getLastCompletedStageName(),  // 外部持久化的进度
                    RecoveryMode.ROLLBACK,
                    record.getTaskId()                   // 外部持久化的 taskId
                );
                
                // 4. 调用执行器恢复（执行器完全无状态）
                TaskResult result = executorRecoveryService.recoverFromRestart(request);
                
                log.info("任务恢复完成: taskId={}, success={}", 
                    record.getTaskId(), result.isSuccess());
                    
            } catch (Exception e) {
                log.error("任务恢复失败: taskId={}", record.getTaskId(), e);
            }
        }
    }
}
```

##### 模式 3：外部数据模型

```java
/**
 * 调用方的数据模型（外部持久化）
 */
@Entity
@Table(name = "task_records")
public class TaskRecord {
    
    @Id
    private String taskId;              // 执行器的 taskId
    
    private String tenantId;            // 租户 ID
    private String planId;              // 计划 ID
    
    private String status;              // CREATED/RUNNING/FAILED/COMPLETED
    
    @Column(columnDefinition = "TEXT")
    private String lastCompletedStageName;  // 最后完成的 Stage（关键！）
    
    @Column(columnDefinition = "JSON")
    private List<String> stageNames;    // 所有 Stage 名称列表
    
    @Column(columnDefinition = "JSON")
    private String failureInfo;         // 失败信息（JSON）
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // getters/setters
}

/**
 * 调用方的仓储接口
 */
@Repository
public interface ExternalTaskRepository extends JpaRepository<TaskRecord, String> {
    
    List<TaskRecord> findByStatus(String status);
    
    Optional<TaskRecord> findByTenantId(String tenantId);
    
    @Modifying
    @Query("UPDATE TaskRecord t SET t.lastCompletedStageName = ?2, t.updatedAt = ?3 WHERE t.taskId = ?1")
    void updateProgress(String taskId, String stageName, LocalDateTime timestamp);
    
    @Modifying
    @Query("UPDATE TaskRecord t SET t.status = ?2, t.failureInfo = ?3, t.updatedAt = ?4 WHERE t.taskId = ?1")
    void updateStatus(String taskId, String status, String failureInfo, LocalDateTime timestamp);
}
```

#### 8.2.7 执行器简化清单

**移除的组件**（不再需要）：

```
❌ TaskStateProjection          - 不再提供状态投影
❌ TaskStateProjectionStore      - 不再持久化投影
❌ TaskStateProjectionUpdater    - 不再监听事件更新投影
❌ PlanStateProjection           - 不再提供计划投影
❌ PlanStateProjectionStore      - 不再持久化投影
❌ PlanStateProjectionUpdater    - 不再监听事件更新投影
❌ TenantTaskIndexStore          - 不再维护租户索引
❌ TaskQueryService              - 不再提供查询 API
❌ TaskCheckpoint（可选）         - 不依赖 Checkpoint 恢复
❌ CheckpointService（可选）      - 可选移除或简化
❌ RedisCheckpointRepository     - 不再持久化 Checkpoint
```

**保留的组件**（核心执行）：

```
✅ TaskAggregate                 - 核心聚合
✅ TaskExecutor                  - 执行引擎
✅ TaskDomainService             - 领域服务
✅ StateTransitionService        - 状态机
✅ TaskRuntimeContext            - 执行上下文
✅ StageFactory                  - Stage 工厂
✅ DomainEventPublisher          - 事件发布（供调用方监听）
✅ InMemoryTaskRepository        - 运行时内存仓储
```

**架构简化效果**：

```
原架构（包含持久化）：
┌─────────────────────────────────────────┐
│ Executor Module                         │
│  ├─ Execution Engine (TaskExecutor)    │
│  ├─ Domain Model (Aggregates)          │
│  ├─ Persistence (Redis)          ❌     │
│  ├─ Projection (TaskStateProjection) ❌│
│  ├─ Query API (TaskQueryService)   ❌  │
│  └─ Event Publishing                   │
└─────────────────────────────────────────┘

新架构（纯执行器）：
┌─────────────────────────────────────────┐
│ Executor Module (Stateless)            │
│  ├─ Execution Engine (TaskExecutor)    │
│  ├─ Domain Model (Aggregates)          │
│  ├─ State Machine (Transitions)        │
│  └─ Event Publishing                   │
└─────────────────────────────────────────┘
         ↓ Events
┌─────────────────────────────────────────┐
│ Caller's Responsibility                 │
│  ├─ Event Listeners                     │
│  ├─ State Persistence (DB/EventStore)  │
│  ├─ Query API (if needed)               │
│  └─ Recovery Logic                      │
└─────────────────────────────────────────┘
```

#### 8.2.8 实施步骤（简化版）

**Phase 1：验证前提（0.5天）**
- [ ] 验证 StageFactory 幂等性（相同输入→相同输出）
- [ ] 确认调用方有能力持久化状态（数据库/事件存储）

**Phase 2：实现恢复服务（1天）**
- [ ] 实现 `TaskRecoveryService.recoverFromRestart()`
- [ ] 实现 `calculateStartIndex()` 索引计算逻辑
- [ ] 单元测试（无需 Redis，纯内存）

**Phase 3：调用方集成示例（0.5天）**
- [ ] 提供事件监听器示例代码
- [ ] 提供外部数据模型示例
- [ ] 提供恢复服务示例

**Phase 4：清理持久化代码（1天）**
- [ ] 移除 TaskStateProjection 相关代码
- [ ] 移除 TaskQueryService（或标记为废弃）
- [ ] 移除 Checkpoint 持久化（可选保留内存版本用于审计）
- [ ] 更新文档说明职责边界

**总计：3 天**（比原方案节省 3 天）

---

### 8.3 方案对比总结

| 方案 | 复杂度 | 持久化 | 职责边界 | 推荐指数 |
|------|-------|--------|---------|---------|
| 方案 A：聚合重构 | 中 | Redis | 模糊 | ⭐⭐⭐ |
| 方案 B：状态机扩展 | 低 | 无 | 模糊 | ⭐⭐ |
| 方案 C：事件溯源 | 高 | Redis | 清晰 | ⭐ |
| 方案 D：快照恢复 | 中 | Redis | 模糊 | ⭐⭐⭐⭐ |
| ⭐ 新方案：无状态执行器 | 极低 | 调用方 | 极清晰 | ⭐⭐⭐⭐⭐ |

**最终推荐**：⭐ **新方案（无状态执行器）**

**选择理由**：
1. ✅ **职责清晰**：执行器只管执行，调用方管状态
2. ✅ **完全无状态**：无持久化依赖，易于扩展
3. ✅ **实现最简**：移除大量持久化代码
4. ✅ **易于测试**：纯内存逻辑，无需 Mock
5. ✅ **模块独立**：可独立部署、独立升级

**核心约束**：
- ⚠️ **调用方必须持久化状态**（taskId + lastCompletedStageName）
- ⚠️ **调用方必须监听事件**（及时更新外部状态）
- ⚠️ **StageList 必须全局固定**（幂等性保证）

**注意事项**：
- 执行器发布领域事件，调用方负责监听和持久化
- 调用方自行决定状态存储方案（DB/EventStore/File/etc）
- 执行器不再提供查询 API，调用方自行实现查询逻辑

---

## 附录 A：相关代码位置

| 文件 | 路径 | 说明 |
|------|------|------|
| TaskCheckpoint | `domain/task/TaskCheckpoint.java` | ~~需要扩展~~（新方案无需） |
| CheckpointService | `application/checkpoint/CheckpointService.java` | ~~需要修改~~（新方案无需） |
| TaskAggregate | `domain/task/TaskAggregate.java` | ~~需要添加重构方法~~（新方案无需） |
| TaskRuntimeContext | `domain/task/TaskRuntimeContext.java` | ✅ 已有 `setStartIndex()` |
| TaskExecutor | `infrastructure/execution/TaskExecutor.java` | ✅ 已支持从指定索引开始 |
| StageFactory | `infrastructure/factory/StageFactory.java` | ✅ 需要验证幂等性 |
| TaskExecutorTest | `test/.../TaskExecutorTest.java` | 参考测试用例 |

## 附录 B：新方案实现检查清单

### B.1 前提验证

- [ ] **验证 StageList 幂等性**
  ```java
  @Test
  void testStageFactoryIdempotence() {
      TenantConfig config = ...;
      List<TaskStage> stages1 = stageFactory.createStages(config);
      List<TaskStage> stages2 = stageFactory.createStages(config);
      
      // 验证：Stage 数量相同
      assertEquals(stages1.size(), stages2.size());
      
      // 验证：Stage 名称顺序相同
      for (int i = 0; i < stages1.size(); i++) {
          assertEquals(stages1.get(i).getName(), stages2.get(i).getName());
      }
  }
  ```

- [ ] **确认 lastCompletedStageName 来源**
  - 选项 1：监听 `TaskStageCompletedEvent` 记录到外部数据库
  - 选项 2：从日志系统查询（结构化日志）
  - 选项 3：从 Redis 投影查询（TaskStateProjection）

- [ ] **验证新 taskId 约定**
  - 确认不会影响 Plan 层面的统计（completedTaskCount）
  - 确认外部监控系统能接受新 taskId
  - 确认审计日志能关联新旧 taskId（通过 tenantId）

### B.2 核心实现

```java
/**
 * 重启恢复服务（无状态重建）
 */
@Service
public class TaskRecoveryService {
    
    private final StageFactory stageFactory;
    private final TaskExecutorFactory executorFactory;
    private final TaskRuntimeRepository contextRepository;
    
    /**
     * 重启后恢复执行
     * 
     * @param config 租户配置（包含 tenantId, planId, previousConfig）
     * @param lastCompletedStageName 上次完成的 Stage 名称（null 表示从头开始）
     * @param mode 恢复模式（RETRY / ROLLBACK）
     * @return 执行结果
     */
    public TaskResult recoverFromRestart(
        TenantConfig config,
        String lastCompletedStageName,
        RecoveryMode mode
    ) {
        // 1. 生成新 TaskId
        TaskId newTaskId = TaskId.generate();
        TenantId tenantId = config.getTenantId();
        PlanId planId = config.getPlanId();
        
        log.info("重启恢复开始: tenantId={}, mode={}, lastCompleted={}", 
            tenantId, mode, lastCompletedStageName);
        
        // 2. 创建新 Task（CREATED 状态）
        TaskAggregate task = new TaskAggregate(
            newTaskId,
            planId,
            tenantId,
            config
        );
        
        // 3. 根据模式选择配置
        TenantConfig targetConfig = (mode == RecoveryMode.ROLLBACK) 
            ? config.getPreviousConfig() 
            : config;
        
        if (targetConfig == null) {
            throw new IllegalArgumentException(
                "ROLLBACK 模式需要 previousConfig: " + tenantId
            );
        }
        
        // 4. 生成 Stages（全局固定）
        List<TaskStage> stages = stageFactory.createStages(targetConfig);
        
        // 5. 计算起点索引
        int startIndex = calculateStartIndex(stages, lastCompletedStageName);
        log.info("计算起点索引: startIndex={}, totalStages={}", 
            startIndex, stages.size());
        
        // 6. 构建执行上下文
        TaskRuntimeContext ctx = new TaskRuntimeContext(planId, newTaskId, tenantId);
        ctx.setStartIndex(startIndex);  // ✅ 设置起点
        
        if (mode == RecoveryMode.RETRY) {
            ctx.requestRetry(true);
        } else {
            String version = targetConfig.getDeployUnitVersion().toString();
            ctx.requestRollback(version);
            ctx.addVariable("deployVersion", version);
        }
        
        // 7. 保存上下文
        contextRepository.saveContext(newTaskId, ctx);
        
        // 8. 创建执行器并执行
        TaskExecutor executor = executorFactory.create(task, stages, ctx);
        TaskResult result = executor.execute();
        
        log.info("重启恢复完成: newTaskId={}, success={}", newTaskId, result.isSuccess());
        return result;
    }
    
    /**
     * 计算起点索引
     */
    private int calculateStartIndex(
        List<TaskStage> stages,
        String lastCompletedStageName
    ) {
        if (lastCompletedStageName == null || lastCompletedStageName.isEmpty()) {
            return 0;
        }
        
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).getName().equals(lastCompletedStageName)) {
                return i + 1;  // 下一个 Stage
            }
        }
        
        // 找不到匹配的 Stage 名称
        throw new IllegalArgumentException(String.format(
            "找不到 Stage '%s'，可用的 Stages: %s",
            lastCompletedStageName,
            stages.stream().map(TaskStage::getName).toList()
        ));
    }
}

/**
 * 恢复模式枚举
 */
public enum RecoveryMode {
    /** 重试：使用当前版本配置 */
    RETRY,
    
    /** 回滚：使用 previousConfig */
    ROLLBACK
}
```

### B.3 外部接口设计

```java
/**
 * 重启恢复请求
 */
public class RestartRecoveryRequest {
    private String tenantId;              // 租户 ID
    private String lastCompletedStageName; // 上次完成的 Stage（可选）
    private RecoveryMode mode;            // RETRY / ROLLBACK
    
    // getters/setters
}

/**
 * 重启恢复 API
 */
@RestController
@RequestMapping("/api/recovery")
public class RecoveryController {
    
    @Autowired
    private TaskRecoveryService recoveryService;
    
    @Autowired
    private TenantConfigService configService;
    
    /**
     * 重启后恢复执行
     * 
     * POST /api/recovery/restart
     * {
     *   "tenantId": "tenant-001",
     *   "lastCompletedStageName": "stage-1",
     *   "mode": "ROLLBACK"
     * }
     */
    @PostMapping("/restart")
    public ResponseEntity<TaskResult> recoverFromRestart(
        @RequestBody RestartRecoveryRequest request
    ) {
        try {
            // 1. 查询租户配置
            TenantId tenantId = TenantId.of(request.getTenantId());
            TenantConfig config = configService.getConfig(tenantId);
            
            // 2. 执行恢复
            TaskResult result = recoveryService.recoverFromRestart(
                config,
                request.getLastCompletedStageName(),
                request.getMode()
            );
            
            return ResponseEntity.ok(result);
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(TaskResult.fail(..., e.getMessage(), ...));
        } catch (Exception e) {
            log.error("重启恢复失败", e);
            return ResponseEntity.status(500)
                .body(TaskResult.fail(..., e.getMessage(), ...));
        }
    }
}
```

### B.4 测试用例

```java
@SpringBootTest
class TaskRecoveryServiceTest {
    
    @Autowired
    private TaskRecoveryService recoveryService;
    
    @Test
    void testRecoverFromMiddleStage() {
        // 场景：stage-1 完成，从 stage-2 重试
        TenantConfig config = createTestConfig();
        
        TaskResult result = recoveryService.recoverFromRestart(
            config,
            "stage-1",  // 上次完成的 Stage
            RecoveryMode.RETRY
        );
        
        assertTrue(result.isSuccess());
        // 验证：只执行了 stage-2 和 stage-3
    }
    
    @Test
    void testRecoverRollbackFromStart() {
        // 场景：回滚，从头开始
        TenantConfig config = createTestConfigWithPrevious();
        
        TaskResult result = recoveryService.recoverFromRestart(
            config,
            null,  // 从头开始
            RecoveryMode.ROLLBACK
        );
        
        assertTrue(result.isSuccess());
        // 验证：使用 previousConfig 执行了所有 Stages
    }
    
    @Test
    void testCalculateStartIndexNotFound() {
        // 场景：Stage 名称不存在
        TenantConfig config = createTestConfig();
        
        assertThrows(IllegalArgumentException.class, () -> {
            recoveryService.recoverFromRestart(
                config,
                "non-existent-stage",
                RecoveryMode.RETRY
            );
        });
    }
}
```

### B.5 监控和日志

```java
// 结构化日志（用于追溯 lastCompletedStageName）
log.info("Stage completed: taskId={}, tenantId={}, stageName={}, stageIndex={}", 
    taskId, tenantId, stageName, index);

// 指标监控
metrics.incrementCounter("recovery.restart.total");
metrics.incrementCounter("recovery.restart.success");
metrics.incrementCounter("recovery.restart.failed");
metrics.recordTimer("recovery.restart.duration", duration);
```

---

## 附录 C：方案演进路径

如果未来需求变化，可以按以下路径演进：

```
当前方案（无状态重建）
    ↓
需求 1：支持复杂状态恢复
    → 添加 Checkpoint 快照（方案 D）
    ↓
需求 2：支持任意时间点恢复
    → 引入事件溯源（方案 C）
    ↓
需求 3：支持跨版本恢复
    → 事件版本管理 + Schema 演进
```

**建议**：先实施无状态方案，验证业务价值后再考虑演进。

