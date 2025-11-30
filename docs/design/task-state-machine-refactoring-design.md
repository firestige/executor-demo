# Task 状态机重构设计方案

> 日期: 2025-11-29  
> 状态: 设计阶段  
> 目标: 解决状态流转不受控和检查点保存时序问题

---

## 1. 问题分析

### 1.1 核心问题

**问题1：状态迁移不受控**
- TaskExecutor 作为实际驱动 Task 状态转换的执行器
- 但在各种领域服务、应用服务中也调用检查和修改方法
- 导致状态迁移不受控，过程不符合状态机设计

**问题2：检查点保存时序错误**
- 按理说最后一个 Stage 完成后 Task 进入 COMPLETED
- 每个 Stage 完成时应该保存检查点
- 但现在先 `completeTask()` 再保存检查点，导致检查点状态检查错误
- 要么多保存一个检查点，要么最后一个检查点不保存，直接 COMPLETE Task

### 1.2 当前问题代码示例

```java
// TaskExecutor.java - 问题代码
for (int i = startIndex; i < stages.size(); i++) {
    TaskStage stage = stages.get(i);
    
    // 执行 Stage
    StageResult stageResult = stage.execute(context);
    
    if (stageResult.isSuccess()) {
        // ✅ Stage 完成
        taskDomainService.completeStage(task, stageName, duration, context);
        completedStages.add(stageResult);
        
        // 保存检查点（问题：最后一个 Stage 也保存检查点）
        checkpointService.saveCheckpoint(task, extractStageNames(completedStages), i);
    }
}

// 6. 完成任务（问题：检查点已保存，但此时 Task 状态变更）
taskDomainService.completeTask(task, context);
```

---

## 2. 设计原则

### 2.1 状态管理职责划分

**原则：状态的迁移收束到 TaskExecutor 中**

| 组件 | 职责 | 是否可修改 Task 状态 |
|------|------|---------------------|
| **TaskExecutor** | 唯一驱动 Task 状态转换的组件 | ✅ 是（通过 TaskDomainService） |
| **TaskAggregate** | 状态的持有者，验证业务不变式 | ✅ 是（自身状态） |
| **TaskDomainService** | 协调聚合和基础设施，发布事件 | ✅ 是（通过聚合方法） |
| **StateTransitionService** | 状态转换规则验证 | ❌ 否（只读验证） |
| **应用服务（Facade等）** | 编排和查询 | ❌ 否（只读查询） |
| **PlanDomainService** | Plan 生命周期管理 | ❌ 否（不管理 Task） |
| **CheckpointService** | 检查点持久化 | ❌ 否（不改变状态） |

**例外情况**：
- 测试代码：为了构筑中间状态，可以违反约定
- Plan 生命周期：大于 Task，不受 TaskExecutor 管理
- Task CREATED 状态：不受 TaskExecutor 管理
- Task PENDING 状态：提交到执行线程池等待，不受 TaskExecutor 管理

### 2.2 检查点与状态转换的关系

**设计决策：检查点保存在 Stage 完成后，Task COMPLETED 前**

```
Stage 1 完成 → 保存 Checkpoint(stage1)
Stage 2 完成 → 保存 Checkpoint(stage2)
...
Stage N-1 完成 → 保存 Checkpoint(stageN-1)
Stage N 完成 → 不保存 Checkpoint，直接转换到 COMPLETED
```

**理由**：
1. 检查点用于重试恢复，COMPLETED 是终态不需要重试
2. 最后一个 Stage 完成后立即进入 COMPLETED，不需要检查点
3. 避免 `complete()` 调用时检查点状态检查失败

---

## 3. 状态流转设计

### 3.1 TaskExecutor 驱动的状态流转

#### 正常执行流程

```
PENDING 
  ↓ TaskExecutor.execute() 
  → TaskDomainService.startTask()
  → TaskAggregate.start()
RUNNING
  ↓ Stage 1-N 执行
  → TaskDomainService.startStage()
  → TaskAggregate.startStage()
  → [Stage 执行]
  → TaskDomainService.completeStage()
  → TaskAggregate.completeStage()
  → [保存 Checkpoint，除最后一个]
  ↓ 所有 Stage 完成
  → TaskDomainService.completeTask()
  → TaskAggregate.complete()
COMPLETED
```

#### 失败流程

```
RUNNING
  ↓ Stage 失败
  → TaskDomainService.failStage()  (产生 TaskStageFailedEvent)
  → TaskAggregate.failStage()
  ↓
  → TaskDomainService.failTask()   (产生 TaskFailedEvent)
  → TaskAggregate.fail()
FAILED
```

#### 重试流程

```
FAILED
  ↓ TaskExecutor.retry(fromCheckpoint)
  → CheckpointService.loadCheckpoint()  (只读)
  → TaskDomainService.retryTask()
  → TaskAggregate.retry()
RUNNING
  ↓ 从 Checkpoint 继续执行
```

#### 回滚流程

```
FAILED
  ↓ TaskExecutor.rollback()
  → TaskDomainService.startRollback()
  → TaskAggregate.rollback()
ROLLING_BACK
  ↓ 逆序执行 Stage 回滚
  → 成功: TaskDomainService.completeRollback()
  → 失败: TaskDomainService.failRollback()
ROLLED_BACK / ROLLBACK_FAILED
```

### 3.2 非 TaskExecutor 组件的只读访问

```java
// ✅ 允许：状态查询
TaskStatus status = task.getStatus();
if (status == TaskStatus.RUNNING) {
    // 查询操作
}

// ✅ 允许：状态转换规则验证（不改变状态）
boolean canPause = stateTransitionService.canTransition(task, TaskStatus.PAUSED, context);

// ❌ 禁止：直接修改状态（除了 TaskExecutor 通过 TaskDomainService）
// task.setStatus(TaskStatus.PAUSED);  // 应该没有这个方法

// ❌ 禁止：应用服务直接调用状态转换方法
// facade.completeTask(task);  // 只有 TaskExecutor 可以
```

---

## 4. Checkpoint 保存策略重构

### 4.1 当前问题

```java
// 当前代码（有问题）
for (int i = startIndex; i < stages.size(); i++) {
    // ...执行 Stage
    if (stageResult.isSuccess()) {
        taskDomainService.completeStage(task, stageName, duration, context);
        completedStages.add(stageResult);
        
        // 问题：最后一个 Stage 也保存检查点
        checkpointService.saveCheckpoint(task, extractStageNames(completedStages), i);
    }
}

// 然后完成任务（此时检查点已保存，但 Task 状态还未 COMPLETED）
taskDomainService.completeTask(task, context);
```

### 4.2 重构后的逻辑

```java
// 重构后（正确）
for (int i = startIndex; i < stages.size(); i++) {
    TaskStage stage = stages.get(i);
    boolean isLastStage = (i == stages.size() - 1);
    
    // ...执行 Stage
    if (stageResult.isSuccess()) {
        taskDomainService.completeStage(task, stageName, duration, context);
        completedStages.add(stageResult);
        
        // ✅ 修复：只有非最后一个 Stage 才保存检查点
        if (!isLastStage) {
            checkpointService.saveCheckpoint(task, extractStageNames(completedStages), i);
        }
    }
}

// ✅ 最后一个 Stage 完成后，直接转换到 COMPLETED（不保存检查点）
taskDomainService.completeTask(task, context);
checkpointService.clearCheckpoint(task);  // 清理检查点
```

### 4.3 CheckpointService 验证增强

```java
public void saveCheckpoint(TaskAggregate task, List<String> completedStageNames, int lastCompletedIndex) {
    // ✅ 增强：验证不是最后一个 Stage
    int totalStages = task.getTotalStages();
    if (lastCompletedIndex >= totalStages - 1) {
        // 最后一个 Stage 不应该保存检查点
        logger.warn("跳过检查点保存：已是最后一个 Stage, taskId: {}", task.getTaskId());
        return;
    }
    
    // ✅ 验证 Task 状态必须是 RUNNING
    if (task.getStatus() != TaskStatus.RUNNING) {
        throw new IllegalStateException(
            String.format("只能在 RUNNING 状态保存检查点，当前状态: %s", task.getStatus())
        );
    }
    
    // 委托给聚合的业务方法
    task.recordCheckpoint(completedStageNames, lastCompletedIndex);
    
    // 持久化到外部存储
    TaskCheckpoint checkpoint = task.getCheckpoint();
    if (checkpoint != null) {
        store.put(task.getTaskId(), checkpoint);
    }
}
```

---

## 5. 状态转换的边界控制

### 5.1 TaskExecutor 的状态转换职责

**TaskExecutor 是唯一可以驱动 Task 状态转换的组件**

所有状态转换都通过以下路径：
```
TaskExecutor
  ↓ 调用
TaskDomainService.xxxTask()
  ↓ 前置检查
StateTransitionService.canTransition()
  ↓ 执行转换
TaskAggregate.xxx()
  ↓ 产生事件
DomainEventPublisher.publishAll()
```

### 5.2 其他组件的访问限制

#### 应用服务（Facade）

```java
// ✅ 允许：提交执行请求（间接触发）
public void executeTask(TaskId taskId) {
    // 提交到执行器执行（不直接改变状态）
    taskExecutorPool.submit(taskId);
}

// ✅ 允许：查询状态
public TaskStatusInfo queryTaskStatus(TaskId taskId) {
    TaskAggregate task = taskRepository.findById(taskId);
    return TaskStatusInfo.from(task);  // 只读
}

// ❌ 禁止：直接调用状态转换
// taskDomainService.completeTask(task, context);  // 只有 TaskExecutor 可以
```

#### PlanDomainService

```java
// ✅ 允许：查询 Task 状态汇总
public PlanStatus calculatePlanStatus(List<TaskAggregate> tasks) {
    long completedCount = tasks.stream()
        .filter(t -> t.getStatus() == TaskStatus.COMPLETED)
        .count();
    // ...根据 Task 状态计算 Plan 状态
}

// ❌ 禁止：修改 Task 状态
// task.complete();  // 不允许
```

#### CheckpointService

```java
// ✅ 允许：协调检查点持久化（不改变状态）
public void saveCheckpoint(TaskAggregate task, ...) {
    // 调用聚合的 recordCheckpoint（只记录，不转换状态）
    task.recordCheckpoint(completedStageNames, lastCompletedIndex);
    store.put(task.getTaskId(), task.getCheckpoint());
}

// ✅ 允许：加载检查点（不改变状态）
public TaskCheckpoint loadCheckpoint(TaskAggregate task) {
    TaskCheckpoint cp = store.get(task.getTaskId());
    if (cp != null) {
        task.restoreFromCheckpoint(cp);  // 恢复进度，不转换状态
    }
    return cp;
}
```

---

## 6. 重构实施计划

### 6.1 第一阶段：修复检查点保存逻辑（高优先级）

**目标**：解决最后一个 Stage 的检查点保存问题

**改动文件**：
1. `TaskExecutor.java` - 修改 `execute()` 方法
2. `CheckpointService.java` - 增强验证逻辑
3. `TaskExecutorTest.java` - 更新测试

**预期结果**：
- 最后一个 Stage 完成后不保存检查点
- Task 进入 COMPLETED 前清理检查点
- 测试通过验证流程正确

### 6.2 第二阶段：收束状态转换路径（中优先级）

**目标**：确保只有 TaskExecutor 可以驱动状态转换

**改动内容**：
1. 审查所有调用 `TaskDomainService.xxxTask()` 的地方
2. 确认只有 TaskExecutor 调用状态转换方法
3. 其他地方改为查询方法

**审查清单**：
- [ ] `PlanLifecycleService` - 不应该调用 Task 状态转换
- [ ] `DeploymentApplicationService` - 只能查询
- [ ] `DeploymentTaskFacade` - 只能提交执行请求
- [ ] 所有 Application Service - 只能查询或提交请求

### 6.3 第三阶段：增强状态转换验证（低优先级）

**目标**：运行时验证状态转换路径

**实施方案**：
1. 在 `TaskDomainService` 增加调用栈验证
2. 确保状态转换方法只被 TaskExecutor 调用
3. 增加日志和监控指标

```java
// 示例：调用栈验证
public void completeTask(TaskAggregate task, TaskRuntimeContext context) {
    // ✅ 运行时验证调用者
    if (ENABLE_STRICT_MODE) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        boolean calledFromExecutor = Arrays.stream(stackTrace)
            .anyMatch(e -> e.getClassName().equals(TaskExecutor.class.getName()));
        
        if (!calledFromExecutor) {
            logger.error("非法调用：completeTask() 只能由 TaskExecutor 调用");
            throw new IllegalStateException("状态转换方法只能由 TaskExecutor 调用");
        }
    }
    
    // ...正常逻辑
}
```

---

## 7. 测试验证

### 7.1 单元测试

```java
@Test
void testCheckpointNotSavedForLastStage() {
    // 准备两个 Stage
    List<TaskStage> stages = List.of(
        new AlwaysSuccessStage("stage-1"),
        new AlwaysSuccessStage("stage-2")
    );
    
    TaskExecutor executor = factory.create(task, stages);
    TaskResult result = executor.execute();
    
    // ✅ 验证：Task 完成
    assertThat(result.isSuccess()).isTrue();
    assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
    
    // ✅ 验证：只有第一个 Stage 保存了检查点
    verify(checkpointService, times(1)).saveCheckpoint(any(), any(), eq(0));
    
    // ✅ 验证：完成后清理了检查点
    verify(checkpointService, times(1)).clearCheckpoint(task);
}

@Test
void testStateTransitionOnlyFromExecutor() {
    // ❌ 尝试从应用服务直接调用状态转换
    assertThrows(IllegalStateException.class, () -> {
        facade.directlyCompleteTask(taskId);  // 应该抛出异常
    });
    
    // ✅ 只能通过 Executor 执行
    TaskExecutor executor = factory.create(task, stages);
    TaskResult result = executor.execute();
    assertThat(result.isSuccess()).isTrue();
}
```

### 7.2 集成测试

```java
@Test
void testFullLifecycleWithCheckpoint() {
    // 1. 执行到一半失败
    TaskExecutor executor1 = factory.create(task, List.of(
        new AlwaysSuccessStage("stage-1"),
        new FailOnceStage("stage-2"),
        new AlwaysSuccessStage("stage-3")
    ));
    
    TaskResult result1 = executor1.execute();
    assertThat(result1.isSuccess()).isFalse();
    
    // 2. 验证：只保存了第一个 Stage 的检查点
    TaskCheckpoint cp = checkpointService.loadCheckpoint(task);
    assertThat(cp.getLastCompletedStageIndex()).isEqualTo(0);
    
    // 3. 重试：从检查点恢复
    TaskExecutor executor2 = factory.create(task, stages);
    TaskResult result2 = executor2.retry(true);
    assertThat(result2.isSuccess()).isTrue();
    
    // 4. 验证：完成后检查点已清理
    assertThat(checkpointService.loadCheckpoint(task)).isNull();
}
```

---

## 8. 风险与应对

| 风险 | 影响 | 应对措施 |
|------|------|---------|
| 破坏现有功能 | 高 | 完整的单元测试和集成测试覆盖 |
| 性能影响 | 低 | 调用栈验证只在开发环境启用 |
| 遗漏状态转换调用点 | 中 | 代码审查 + Grep 搜索 + IDE 引用查找 |
| Checkpoint 清理时机不当 | 中 | 增强测试验证清理逻辑 |

---

## 9. Definition of Done

- [x] 问题分析完成
- [x] 设计原则明确
- [x] 状态流转路径设计完成
- [x] Checkpoint 保存策略设计完成
- [x] 实施计划制定
- [ ] 代码修改完成
- [ ] 单元测试通过
- [ ] 集成测试通过
- [ ] 代码审查通过
- [ ] 文档更新

---

## 10. 后续优化

1. **状态机可视化**：生成运行时状态转换图谱
2. **监控指标**：状态转换次数、停留时间、异常转换
3. **审计日志**：记录所有状态转换及调用栈
4. **分布式锁**：多实例环境的状态转换原子性保障

---

## 附录 A：状态转换权限矩阵

| 方法 | TaskExecutor | TaskDomainService | 应用服务 | PlanDomainService |
|------|-------------|------------------|---------|------------------|
| startTask() | ✅ 是 | ✅ 是（被调用） | ❌ 否 | ❌ 否 |
| completeTask() | ✅ 是 | ✅ 是（被调用） | ❌ 否 | ❌ 否 |
| failTask() | ✅ 是 | ✅ 是（被调用） | ❌ 否 | ❌ 否 |
| pauseTask() | ✅ 是 | ✅ 是（被调用） | ❌ 否 | ❌ 否 |
| resumeTask() | ✅ 是 | ✅ 是（被调用） | ❌ 否 | ❌ 否 |
| retryTask() | ✅ 是 | ✅ 是（被调用） | ❌ 否 | ❌ 否 |
| rollback() | ✅ 是 | ✅ 是（被调用） | ❌ 否 | ❌ 否 |
| getStatus() | ✅ 是 | ✅ 是 | ✅ 是 | ✅ 是 |
| canTransition() | ✅ 是 | ✅ 是 | ✅ 是 | ✅ 是 |

**规则**：
- 状态转换方法（xxxTask）：只有 TaskExecutor 通过 TaskDomainService 调用
- 查询方法（getStatus）：所有组件可调用
- 验证方法（canTransition）：所有组件可调用（只读）

---

## 附录 B：设计确认（2025-11-29）

### ✅ 确认的设计决策

**1. 检查点恢复逻辑**
- **确认**：Stage N-1 完成后记录的检查点相当于 Stage N 前的存档
- **行为**：如果 Stage N 失败，从 Stage N 开始重试（不是 N-1）
- **实现**：`lastCompletedStageIndex = N-1`，恢复时 `startIndex = N-1 + 1 = N`
- **示例**：
  ```
  Stage 0 完成 → Checkpoint(lastCompleted=0)
  Stage 1 完成 → Checkpoint(lastCompleted=1)
  Stage 2 失败 → 重试从 Stage 2 开始（startIndex = 1 + 1 = 2）
  ```

**2. Stage 失败与 Task 失败的顺序**
- **确认**：保持当前顺序，不合并
- **理由**：
  - Stage 和 Task 是上下层关系
  - Fast Fail 模式先记录 Stage 失败，再标记 Task 失败
  - 未来扩展：如果 Stage 并行，可以先完成所有 Stage 再 failTask
- **事件顺序**：`TaskStageFailedEvent` → `TaskFailedEvent`
- **代码**：
  ```java
  // ✅ 正确：两步分离
  taskDomainService.failStage(task, stageName, failureInfo);
  taskDomainService.failTask(task, failureInfo, context);
  ```

**3. TaskAggregate.completeStage() 的自动完成**
- **确认**：采用方案 A，移除自动 `complete()`
- **理由**：所有状态变化必须明确，不能有隐藏的自动转换
- **实现**：TaskExecutor 显式检查并调用 `completeTask()`
- **修改前**：
  ```java
  public void completeStage(String stageName, Duration duration) {
      // ...
      if (stageProgress.isCompleted()) {
          complete();  // ❌ 隐藏的自动转换
      }
  }
  ```
- **修改后**：
  ```java
  public void completeStage(String stageName, Duration duration) {
      // ...
      // ✅ 不自动转换，由 TaskExecutor 显式调用
  }
  ```

**4. 实施顺序**
- **确认**：先完善文档，扫描修改点，确认细节后再重构
- **步骤**：
  1. 完善设计文档 ✅
  2. 扫描所有需要修改的代码点
  3. 确认修改细节
  4. 执行重构
  5. 测试验证

