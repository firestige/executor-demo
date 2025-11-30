# 状态管理机制总结（T-033 最终版）

> **更新日期**：2025-11-29  
> **适用版本**：T-033 完成后

---

## 核心原则

### 三大收口

1. **操作收口在聚合根（TaskAggregate）**
   - 所有状态转换通过聚合根的业务方法执行
   - 聚合根负责保护核心业务不变式
   - 聚合根发布领域事件

2. **驱动收口在 TaskExecutor**
   - TaskExecutor 是唯一的执行入口
   - 通过 ExecutionPreparer 准备状态和上下文
   - 统一的 `execute()` 方法驱动整个流程

3. **状态检查收口在聚合根**
   - **核心不变式**：状态转换方法检查前置条件
   - **辅助操作**：不检查状态，由调用方保证时机
   - 不在领域服务、应用服务中重复检查

---

## 状态检查分类

### 必须检查状态（核心不变式）

这些方法**改变状态**，必须检查前置条件：

| 方法 | 前置状态 | 目标状态 | 说明 |
|------|---------|---------|------|
| `start()` | PENDING | RUNNING | 启动任务 |
| `complete()` | RUNNING | COMPLETED | 完成任务 |
| `retry()` | FAILED | PENDING | 重试任务 |
| `fail()` | RUNNING | FAILED | 任务失败 |
| `applyPauseAtStageBoundary()` | RUNNING | PAUSED | 暂停任务 |
| `resume()` | PAUSED | RUNNING | 恢复任务 |
| `cancel()` | 非终态 | CANCELLED | 取消任务 |

**示例**：
```java
public void start() {
    // ✅ 核心不变式：必须检查状态
    if (status != TaskStatus.PENDING) {
        throw new IllegalStateException("只有 PENDING 状态可以启动");
    }
    this.status = TaskStatus.RUNNING;  // 改变状态
    // 发布事件...
}
```

### 不检查状态（辅助操作）

这些方法**只设置字段**，不改变状态，由调用方保证时机：

| 方法 | 操作 | 调用方 |
|------|------|--------|
| `restoreFromCheckpoint()` | 设置 checkpoint 字段 | ExecutionPreparer |
| `recordCheckpoint()` | 记录检查点 | TaskExecutor |
| `clearCheckpoint()` | 清空检查点 | TaskExecutor |
| `setPrevConfig()` | 设置旧配置快照 | TaskDomainService |
| `markAsRollbackIntent()` | 设置回滚标志 | ExecutionPreparer |
| `setTotalStages()` | 设置 Stage 总数 | TaskDomainService |

**示例**：
```java
public void restoreFromCheckpoint(TaskCheckpoint checkpoint) {
    if (checkpoint == null) {
        throw new IllegalArgumentException("检查点不能为空");
    }
    // ✅ 不检查状态，只设置字段
    this.checkpoint = checkpoint;
}
```

**调用方保证时机**：
```java
// ExecutionPreparer.prepareFailedTask()
deps.getTaskDomainService().retryTask(task, context);     // 1. FAILED → PENDING
deps.getTaskDomainService().startTask(task, context);     // 2. PENDING → RUNNING
int startIndex = loadCheckpointStartIndex(task, deps);     // 3. 此时可以安全调用 restoreFromCheckpoint
```

---

## 为什么这样设计

### 问题：旧设计的"过度保护"

**场景**：重试时从检查点恢复

```java
// ❌ 旧设计：restoreFromCheckpoint() 检查必须是 FAILED
public void restoreFromCheckpoint(TaskCheckpoint checkpoint) {
    if (status != TaskStatus.FAILED) {  // 过度保护
        throw new IllegalStateException("只有 FAILED 状态才能恢复检查点");
    }
    this.checkpoint = checkpoint;
}

// 调用链：
// 1. retryTask()  → FAILED → PENDING
// 2. startTask()  → PENDING → RUNNING
// 3. restoreFromCheckpoint() ← 此时是 RUNNING，抛异常！
```

**根本原因**：
- 状态检查是为**旧的调用流程**设计的
- TaskExecutor 统一驱动后，调用顺序改变了
- 聚合根的检查逻辑没有同步更新

### 解决：区分"状态转换"和"辅助操作"

**原则**：
1. **状态转换方法**：检查前置状态（核心不变式）
2. **辅助操作方法**：不检查状态，只校验参数（如 null check）
3. **由调用方（TaskExecutor/ExecutionPreparer）保证在正确的时机调用**

**好处**：
1. ✅ **修改执行流程时，不会被旧的状态检查卡住**
2. ✅ **状态检查集中在状态转换方法中，易于维护**
3. ✅ **辅助方法更灵活，可在不同状态下复用**
4. ✅ **聚合根职责清晰：保护核心不变式，不管理调用时序**

---

## 调用链示例

### 正常执行

```
TaskExecutionOrchestrator.execute()
  ↓
TaskExecutor.execute()
  ↓
ExecutionPreparer.prepare()
  - preparePendingTask()
    → taskDomainService.startTask()  // ✅ 检查 PENDING → RUNNING
  ↓
TaskExecutor.executeStages()
  - 循环执行 Stage
  - 每个 Stage 完成后：
    → taskDomainService.completeStage()
    → checkpointService.recordCheckpoint()  // ✅ 不检查状态
  ↓
TaskExecutor.completeTask()
  → taskDomainService.completeTask()  // ✅ 检查 RUNNING → COMPLETED
  → checkpointService.clearCheckpoint()  // ✅ 不检查状态
```

### 重试执行（从检查点）

```
TaskExecutionOrchestrator.execute()
  ↓
TaskExecutor.execute()
  ↓
ExecutionPreparer.prepare()
  - prepareFailedTask()
    1. taskDomainService.retryTask()  // ✅ 检查 FAILED → PENDING
    2. taskDomainService.startTask()  // ✅ 检查 PENDING → RUNNING
    3. loadCheckpointStartIndex()
       → checkpointService.loadCheckpoint()
         → task.restoreFromCheckpoint()  // ✅ 不检查状态，只设置字段
  ↓
TaskExecutor.executeStages(startIndex = checkpoint+1)
  - 从检查点后的 Stage 开始
  ↓
TaskExecutor.completeTask()
  → taskDomainService.completeTask()  // ✅ 检查 RUNNING → COMPLETED
```

### 回滚执行

```
TaskExecutionOrchestrator.execute()
  ↓
TaskExecutor.execute()
  ↓
ExecutionPreparer.prepare()
  - prepareFailedTask() [rollbackRequested]
    1. task.markAsRollbackIntent()  // ✅ 不检查状态，设置标志
    2. taskDomainService.retryTask()  // ✅ 检查 FAILED → PENDING
    3. taskDomainService.startTask()  // ✅ 检查 PENDING → RUNNING
                                       //    发布 TaskRollbackStarted（因为标志位）
  ↓
TaskExecutor.executeStages(startIndex = 0)
  - 使用 prevConfigSnapshot（旧配置）
  - Stage 顺序正常
  ↓
TaskExecutor.completeTask()
  → taskDomainService.completeTask()  // ✅ 检查 RUNNING → COMPLETED
                                       //    发布 TaskRolledBack（因为标志位）
```

---

## 实施检查清单

### 如何判断是否需要状态检查

**问题清单**：
1. 这个方法是否改变 `status` 字段？
   - ✅ 是 → **必须检查状态**（核心不变式）
   - ❌ 否 → 继续下一个问题

2. 这个方法是否只设置其他字段（checkpoint, prevConfig, rollbackIntent 等）？
   - ✅ 是 → **不检查状态**（辅助操作）
   - ❌ 否 → 继续下一个问题

3. 这个方法是否触发复杂的业务逻辑（如并发控制、外部调用）？
   - ✅ 是 → 考虑在调用方检查，不在聚合根检查
   - ❌ 否 → 重新审视职责划分

### 重构步骤

如果发现"过度保护"的状态检查：

1. **确认方法职责**：
   - 是状态转换？→ 保留检查
   - 是辅助操作？→ 移除检查

2. **移除状态检查**：
   ```java
   // Before
   public void restoreFromCheckpoint(TaskCheckpoint checkpoint) {
       if (status != TaskStatus.FAILED) {  // ❌ 移除
           throw new IllegalStateException("...");
       }
       this.checkpoint = checkpoint;
   }
   
   // After
   public void restoreFromCheckpoint(TaskCheckpoint checkpoint) {
       if (checkpoint == null) {  // ✅ 保留参数校验
           throw new IllegalArgumentException("检查点不能为空");
       }
       this.checkpoint = checkpoint;
   }
   ```

3. **更新注释**：
   ```java
   /**
    * 恢复到检查点
    * <p>
    * T-033: 移除状态检查，这是辅助方法，只设置字段不改变状态
    * 调用方（ExecutionPreparer）负责在正确的时机调用
    */
   ```

4. **验证调用方**：确保调用方在正确的时机调用（通过状态转换后）

5. **编译测试**：确保修改后编译通过，测试通过

---

## 常见问题

### Q1: 移除状态检查后，如何保证方法不被错误调用？

**A**: 通过**架构约束**和**代码审查**：
1. TaskExecutor 是唯一的执行入口
2. ExecutionPreparer 封装了准备逻辑
3. 辅助方法只在内部调用，不暴露给外部
4. 代码审查确保调用顺序正确

### Q2: 如果未来需要修改执行流程怎么办？

**A**: 更灵活：
1. 状态转换方法的检查不变（核心不变式）
2. 辅助方法没有状态约束，可以在不同流程中复用
3. 只需要调整 ExecutionPreparer 的调用顺序

### Q3: 辅助方法被外部错误调用怎么办？

**A**: 通过访问控制和文档：
1. 考虑将辅助方法标记为 package-private（如果可行）
2. 在注释中明确说明"由 TaskExecutor 调用，不要直接调用"
3. 依赖代码审查和架构守护

---

## 总结

### 当前状态管理机制

```
┌─────────────────────────────────────────────────────┐
│              TaskExecutor（驱动收口）                │
│  - 唯一执行入口                                      │
│  - 调用 ExecutionPreparer 准备状态                   │
│  - 调用 TaskDomainService 驱动状态转换               │
└────────────────┬────────────────────────────────────┘
                 ↓
┌─────────────────────────────────────────────────────┐
│           ExecutionPreparer（状态准备）              │
│  - 根据当前状态选择准备策略                          │
│  - 设置辅助字段（checkpoint, rollbackIntent）        │
│  - 调用状态转换方法（retry, start）                  │
└────────────────┬────────────────────────────────────┘
                 ↓
┌─────────────────────────────────────────────────────┐
│         TaskDomainService（领域服务）                │
│  - 包装聚合根调用                                    │
│  - save + publishEvents                             │
│  - 不做状态预检验                                    │
└────────────────┬────────────────────────────────────┘
                 ↓
┌─────────────────────────────────────────────────────┐
│          TaskAggregate（操作收口+状态检查收口）      │
│                                                      │
│  核心不变式（必须检查状态）：                         │
│  - start(), complete(), retry(), fail()...          │
│                                                      │
│  辅助操作（不检查状态）：                             │
│  - restoreFromCheckpoint(), recordCheckpoint()...   │
│                                                      │
│  职责：                                              │
│  - 保护核心业务不变式                                │
│  - 状态转换 + 事件发布                               │
│  - 不管理调用时序（由 TaskExecutor 保证）            │
└─────────────────────────────────────────────────────┘
```

### 关键设计决策

1. **状态转换方法检查状态**（保护核心不变式）
2. **辅助操作不检查状态**（由调用方保证时机）
3. **TaskExecutor 统一驱动**（不分散在多处）
4. **移除预检验层**（聚合根自己保护）

### 优势

- ✅ 修改执行流程时，不会被旧的状态检查卡住
- ✅ 状态检查逻辑集中，易于维护
- ✅ 聚合根职责清晰，只保护核心不变式
- ✅ 代码更简洁，减少重复检查

---

**参考文档**：
- [T-033 详细设计](../temp/T-033-rollback-intent-flag-design.md)
- [状态机简化设计](state-machine-simplification.md)
- [T-032 准备器模式](../temp/T-032-completion-report.md)

