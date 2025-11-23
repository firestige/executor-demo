# 进程视图 - 补充说明

> **最后更新**: 2025-11-22  
> **PlantUML 图**: 
> - [Plan 状态机](process-view-plan-state.puml)
> - [Task 状态机](process-view-task-state.puml)
> - [Plan 启动执行时序](process-view-plan-execution.puml)
> - [Task 重试时序](process-view-task-retry.puml)

---

## 核心执行流程说明

### 1. Plan 创建与启动
```java
// 示例代码位置: PlanFacade.startPlan()
public Result<PlanDTO> startPlan(Long planId) {
    return planApplicationService.startPlan(planId)
        .map(planMapper::toDTO);
}
```

**关键步骤**:
1. 验证 Plan 状态（必须是 CREATED）
2. Plan 状态转换为 RUNNING
3. 遍历所有 Task，逐个调用 execute()
4. 根据 Task 执行结果更新 Plan 状态

**事务边界**: 整个 startPlan 操作在一个事务内完成

### 2. Task 执行流程
每个 Task 的执行独立进行，包含以下阶段：

1. **状态验证**: 检查 Task 当前状态是否允许执行
2. **状态转换**: CREATED → RUNNING
3. **执行器调用**: 根据 `executorType` 选择对应的 TaskExecutor
4. **结果处理**: 
   - 成功 → COMPLETED
   - 失败 → FAILED（保存 FailureInfo）
5. **持久化**: 保存 Task 的最新状态

**代码位置**: `TaskOperationService.executeTask()`

### 3. Checkpoint 机制
Checkpoint 用于支持任务的断点续传，适用于长时间运行的任务。

**保存时机**:
- Task 执行失败时自动保存
- Task 被暂停时保存当前进度
- 执行器在关键步骤主动保存

**恢复机制**:
```java
// Task.retry() 支持从 Checkpoint 恢复
public Result<Void> retry(TaskExecutor executor, boolean fromCheckpoint) {
    if (fromCheckpoint && this.checkpoint != null) {
        // 从断点继续执行
        return executor.execute(this, this.checkpoint);
    } else {
        // 从头开始执行
        return executor.execute(this, null);
    }
}
```

**Checkpoint 数据结构**:
```json
{
  "step": "SWITCH_GATEWAY",
  "data": {
    "completedTenants": ["tenant1", "tenant2"],
    "currentTenant": "tenant3",
    "rollbackInfo": {...}
  },
  "createdAt": "2025-11-22T10:30:00"
}
```

### 4. 暂停与恢复流程
**暂停 (Pause)**:
- Plan 级别暂停会传播到所有 RUNNING 状态的 Task
- Task 暂停时保存 Checkpoint
- 状态变更: RUNNING → PAUSED

**恢复 (Resume)**:
- Plan 恢复会恢复所有 PAUSED 状态的 Task
- Task 从保存的 Checkpoint 继续执行
- 状态变更: PAUSED → RUNNING

**代码位置**: 
- `PlanLifecycleService.pausePlan()` / `resumePlan()`
- `TaskOperationService.pauseTask()` / `resumeTask()`

---

## 并发控制策略

### 当前实现
- **乐观锁**: 使用 JPA 的 `@Version` 字段防止并发修改冲突
- **事务隔离**: `READ_COMMITTED` 级别
- **单线程执行**: 每个 Plan 的 Task 顺序执行（同步模式）

### 并发场景处理

#### 场景 1: 同时暂停和执行
```
线程A: startPlan(1)  → 更新 Plan 状态为 RUNNING
线程B: pausePlan(1)  → 检查状态，更新为 PAUSED

结果: 乐观锁冲突，线程B重试或失败
```

#### 场景 2: Task 并发执行（未来优化）
当前设计支持改造为并发执行模式：
- 使用线程池异步执行 Task
- 通过 CompletableFuture 聚合结果
- Plan 的完成条件检查通过回调触发

---

## 状态转换约束

### Plan 状态转换矩阵
| 当前状态 | start() | pause() | resume() | complete() | fail() |
|---------|---------|---------|----------|------------|--------|
| CREATED | ✅ RUNNING | ❌ | ❌ | ❌ | ❌ |
| RUNNING | ❌ | ✅ PAUSED | ❌ | ✅ COMPLETED | ✅ FAILED |
| PAUSED  | ❌ | ❌ | ✅ RUNNING | ❌ | ❌ |
| COMPLETED | ❌ | ❌ | ❌ | ❌ | ❌ |
| FAILED  | ❌ | ❌ | ❌ | ❌ | ❌ |

### Task 状态转换矩阵
| 当前状态 | execute() | pause() | resume() | retry() | rollback() |
|---------|-----------|---------|----------|---------|------------|
| CREATED | ✅ RUNNING | ❌ | ❌ | ❌ | ❌ |
| RUNNING | ❌ | ✅ PAUSED | ❌ | ❌ | ❌ |
| PAUSED  | ❌ | ❌ | ✅ RUNNING | ✅ RUNNING | ❌ |
| COMPLETED | ❌ | ❌ | ❌ | ❌ | ❌ |
| FAILED  | ❌ | ❌ | ❌ | ✅ RUNNING | ✅ ROLLED_BACK |
| ROLLED_BACK | ❌ | ❌ | ❌ | ❌ | ❌ |

---

## 异常处理策略

### 异常分类
1. **业务异常**: 状态转换非法、业务规则违反
2. **技术异常**: 数据库连接失败、外部服务不可用
3. **执行器异常**: 部署操作失败、超时

### 处理机制
- **Result 模式**: 所有领域操作返回 `Result<T>` 而非抛出异常
- **FailureInfo**: 记录失败详情（reason, errorCode, timestamp）
- **重试支持**: 失败的 Task 可以重试，最多 3 次（可配置）

**示例**:
```java
// TaskOperationService.executeTask()
public Result<Void> executeTask(TaskId taskId) {
    return taskRepository.findById(taskId)
        .map(task -> task.execute(executorFactory.create(task.getExecutorType())))
        .orElse(Result.failure("Task not found"));
}
```

---

## 性能考虑

### 当前瓶颈
1. **Task 顺序执行**: Plan 内的 Task 串行执行，总耗时 = ∑Task耗时
2. **状态检查开销**: 每次操作都需要查询数据库验证状态
3. **Plan 暂停遍历**: 暂停 Plan 需要遍历所有 Task

### 优化方向
1. **并行执行**: 引入线程池，Task 并发执行
2. **状态缓存**: 在内存中缓存 Plan/Task 状态，减少数据库查询
3. **事件驱动**: Task 完成后发布事件，Plan 监听事件更新状态
4. **批量操作**: 批量更新 Task 状态，减少数据库交互

---

## 相关文档

- [架构总纲](../architecture-overview.md)
- [逻辑视图](logical-view.puml) - 领域模型
- [状态管理详细设计](../design/state-management.md)
- [执行策略设计](../design/execution-strategy.md)

