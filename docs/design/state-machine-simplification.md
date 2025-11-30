# 状态机简化设计（T-033）

> **关联任务**：T-033  
> **完成日期**：2025-11-29  
> **设计原则**：聚合根是状态的守门员，用标志位替代状态枚举

---

## 设计背景

### 原有问题

在 T-032 状态机重构后，发现系统存在以下问题：

1. **回滚专用状态冗余**：`ROLLING_BACK` / `ROLLED_BACK` / `ROLLBACK_FAILED` 三个状态，但回滚本质是"用旧配置重新执行"
2. **状态机复杂**：10 个状态 + 19 个策略类 + 预检验层
3. **职责模糊**：StateManager 做预检验，聚合根也做检查，维护两套逻辑
4. **多处检查**：各处都在调用 `canTransition()`，但聚合根内部也有完整的状态保护

### 核心洞察

**回滚 = 用旧配置重新执行正常流程**
- Stage 顺序相同
- 执行逻辑相同
- 唯一区别：配置来源（旧配置 vs 新配置）
- 不需要专用状态，只需要标识"这是一次回滚"

## 解决方案

### 架构简化

#### Before (T-032)
```
┌─────────────────────────────────────────────────────────┐
│                  StateTransitionService                  │
│  - TaskStateManager (状态管理器)                         │
│  - 19 个策略类 (StartTransitionStrategy, ...)           │
│  - canTransition() 预检验                                │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│                    TaskDomainService                     │
│  - 包装聚合根调用                                         │
│  - 每个方法都先调用 canTransition()                       │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│                     TaskAggregate                        │
│  - 状态检查 (IllegalStateException)                      │
│  - 状态转换                                              │
│  - 领域事件发布                                          │
└─────────────────────────────────────────────────────────┘
```

#### After (T-033)
```
┌─────────────────────────────────────────────────────────┐
│                    TaskDomainService                     │
│  - 直接调用聚合根方法                                     │
│  - 通过 try-catch 处理状态转换失败                        │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│                     TaskAggregate                        │
│  - status: TaskStatus (7个核心状态)                      │
│  - rollbackIntent: boolean (回滚意图标志)                │
│                                                          │
│  - 状态检查 (IllegalStateException)                      │
│  - 状态转换                                              │
│  - 领域事件发布 (根据标志位选择事件类型)                   │
└─────────────────────────────────────────────────────────┘
```

### 核心变更

#### 1. 状态枚举简化

**移除**：
- `ROLLING_BACK` - 回滚中
- `ROLLED_BACK` - 回滚完成
- `ROLLBACK_FAILED` - 回滚失败

**保留**：
- `CREATED` - 已创建
- `PENDING` - 待执行
- `RUNNING` - 执行中
- `PAUSED` - 已暂停
- `COMPLETED` - 已完成（终态）
- `FAILED` - 执行失败
- `CANCELLED` - 已取消（终态）

**状态数量**：10 → 7（**-30%**）

#### 2. 回滚意图标志位

```java
public class TaskAggregate {
    // 状态
    private TaskStatus status;
    
    // 回滚意图标志
    private boolean rollbackIntent = false;
    
    // 标记为回滚意图
    public void markAsRollbackIntent() {
        this.rollbackIntent = true;
    }
    
    // 清除回滚意图
    public void clearRollbackIntent() {
        this.rollbackIntent = false;
    }
    
    // 启动任务（根据标志位发布不同事件）
    public void start() {
        if (status != TaskStatus.PENDING) {
            throw new IllegalStateException("只有 PENDING 状态可以启动");
        }
        this.status = TaskStatus.RUNNING;
        
        if (rollbackIntent) {
            addDomainEvent(new TaskRollbackStarted(...));
        } else {
            addDomainEvent(new TaskStarted(...));
        }
    }
    
    // 完成任务（根据标志位发布不同事件）
    public void complete() {
        if (status != TaskStatus.RUNNING) {
            throw new IllegalStateException("只有 RUNNING 状态才能完成");
        }
        this.status = TaskStatus.COMPLETED;
        
        if (rollbackIntent) {
            addDomainEvent(new TaskRolledBack(...));
            this.rollbackIntent = false;  // 清除标志
        } else {
            addDomainEvent(new TaskCompleted(...));
        }
    }
}
```

#### 3. 移除预检验层

**删除**：
- `StateTransitionService` 接口
- `TaskStateManager` 实现
- 所有策略类（19 个文件）
- 所有 `canTransition()` 调用

**调用方式变更**：

```java
// Before (T-032)
if (stateTransitionService.canTransition(task, TaskStatus.RUNNING, context)) {
    taskDomainService.startTask(task, context);
} else {
    log.warn("状态转换失败");
}

// After (T-033)
try {
    taskDomainService.startTask(task, context);
} catch (IllegalStateException e) {
    log.warn("状态转换失败: {}", e.getMessage());
}
```

## 状态转换流程

### 正常执行

```
CREATED → PENDING → RUNNING → COMPLETED
         (rollbackIntent = false)
         
事件流：
  TaskStarted → StageStarted → ... → StageCompleted → ... → TaskCompleted
```

### 回滚执行

```
FAILED → PENDING → RUNNING → COMPLETED
        (rollbackIntent = true)
        
事件流：
  TaskRollbackStarted → StageStarted → ... → StageCompleted → ... → TaskRolledBack
```

**关键点**：
1. **内部状态相同**：回滚和正常执行都是 PENDING → RUNNING → COMPLETED
2. **外部可区分**：通过不同的领域事件（TaskRollbackStarted vs TaskStarted）
3. **配置不同**：回滚使用 `prevConfigSnapshot`，正常使用 `currentConfig`

### 重试执行（从检查点）

```
FAILED → PENDING → RUNNING → COMPLETED
        (rollbackIntent = false, fromCheckpoint = true)
        
事件流：
  TaskRetryStarted → TaskStarted → StageStarted(stage-2) → ... → TaskCompleted
```

## 代码统计

### 删除的代码

| 类别 | 文件数 | 代码行数 |
|------|--------|---------|
| 策略类 | 11 | ~800 |
| StateTransitionService | 1 | ~50 |
| TaskStateManager | 1 | ~300 |
| 策略基础类 | 2 | ~100 |
| 状态转换 Key | 1 | ~50 |
| 回滚相关方法 | - | ~200 |
| **总计** | **16+** | **~1500** |

### 修改的代码

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| TaskStatus.java | 简化 | 移除 3 个状态 |
| TaskAggregate.java | 增强 | 添加标志位，修改事件发布 |
| TaskDomainService.java | 简化 | 移除所有预检验 |
| TaskExecutor.java | 简化 | 移除所有 canTransition |
| ExecutionPreparer.java | 增强 | 设置 rollbackIntent |
| ExecutionDependencies.java | 简化 | 移除 StateTransitionService |
| ExecutorConfiguration.java | 简化 | 移除 Bean 定义 |
| DefaultTaskWorkerFactory.java | 简化 | 移除依赖 |

## 设计原则

### 1. 聚合根是状态的守门员

> 所有状态转换都由聚合根内部的不变式保护

- 聚合根方法检查状态并抛出 `IllegalStateException`
- 调用方不需要预检验，直接调用并处理异常
- 单一职责：状态保护逻辑只在一处维护

#### 状态检查的分类

**核心不变式检查（必须保留）**：
- 涉及状态转换的方法必须检查状态
  - `start()`: 必须是 PENDING
  - `complete()`: 必须是 RUNNING
  - `retry()`: 必须是 FAILED
  - `applyPauseAtStageBoundary()`: 必须是 RUNNING
  - `resume()`: 必须是 PAUSED

**辅助操作（移除状态检查）**：
- 只设置字段、不改变状态的方法
  - `restoreFromCheckpoint()`: 只设置 checkpoint 字段
  - `recordCheckpoint()`: 只记录检查点，不改变 status
  - `setPrevConfig()`: 只设置配置快照
- 这些方法由调用方（TaskExecutor/ExecutionPreparer）保证在正确时机调用

**设计原则**：
```java
// ✅ 好的设计：状态转换方法检查前置条件
public void start() {
    if (status != TaskStatus.PENDING) {
        throw new IllegalStateException("只有 PENDING 状态可以启动");
    }
    this.status = TaskStatus.RUNNING;  // 改变状态
    // ...
}

// ✅ 好的设计：辅助方法不检查状态
public void restoreFromCheckpoint(TaskCheckpoint checkpoint) {
    if (checkpoint == null) {
        throw new IllegalArgumentException("检查点不能为空");
    }
    this.checkpoint = checkpoint;  // 只设置字段，不改变状态
}

// ❌ 过度保护：辅助方法检查状态（已移除）
public void restoreFromCheckpoint(TaskCheckpoint checkpoint) {
    if (status != TaskStatus.FAILED) {  // ❌ 过度保护
        throw new IllegalStateException("只有 FAILED 状态才能恢复检查点");
    }
    this.checkpoint = checkpoint;
}
```

**好处**：
1. 修改执行流程时，不会被旧的状态检查卡住
2. 状态检查集中在状态转换方法中，易于维护
3. 辅助方法更灵活，可在不同状态下复用

### 2. 用标志位替代状态枚举

> 临时性的执行模式用标志位，不膨胀状态机

- `rollbackIntent` 标识回滚执行
- 内部状态保持简单（PENDING/RUNNING/COMPLETED）
- 外部通过领域事件感知��异

### 3. 事件驱动可观测性

> 外部系统通过领域事件感知业务变化，不依赖状态轮询

- TaskRollbackStarted：回滚开始
- TaskRolledBack：回滚完成
- TaskStarted：正常开始
- TaskCompleted：正常完成

### 4. 简化优于复杂

> 能用简单方案解决的，不要引入复杂模式

- 移除策略模式：减少 1500+ 行代码
- 移除预检验层：简化调用链
- 异常处理流程控制：符合语义（状态转换失败就是异常）

## 影响分析

### 优势

1. **代码量减少**：删除 ~1500 行策略模式代码
2. **状态机简化**：状态数量减少 30%
3. **职责清晰**：聚合根是状态的唯一守门员
4. **维护成本降低**：只维护一处状态检查逻辑
5. **可扩展性好**：未来可以扩展更多"意图"标志

### 劣势/风险

1. **测试更新成本**：需要更新所有期望回滚状态的测试
2. **异常处理**：调用方需要处理 IllegalStateException
3. **可观测性依赖事件**：如果事件丢失，外部无法感知回滚

### 迁移建议

1. **逐步迁移**：先完成编译，再更新测试
2. **保留事件**：TaskRollbackStarted/TaskRolledBack 事件保持不变
3. **文档更新**：更新状态机设计文档和 API 文档

## 后续优化

1. **补充集成测试**：验证回滚流程的事件顺序
2. **更新监控**：基于领域事件的回滚监控
3. **文档完善**：更新架构文档和开发指南

---

**参考文档**：
- [T-033 详细设计](../temp/T-033-rollback-intent-flag-design.md)
- [T-032 状态机重构](state-management.md)
- [回滚机制设计](../temp/rollback-task-level-design.md)

