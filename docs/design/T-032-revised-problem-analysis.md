# T-032 修订版：状态机真正问题分析

> 日期: 2025-11-29  
> 状态: 问题重新分析  
> 原因: T-032 只完成了表面工作，未解决核心问题

---

## 🔴 核心问题重述

### 用户的真实诉求

**核心问题**：状态机状态转换不受控，导致重试和回滚都会因为不能通过状态检查而失败

**计划的解决方案**：
1. **第一步**：状态的驱动全部收束到 TaskExecutor 中
2. **第二步**：对状态的修改依赖语义化的请求通过 TaskExecutor 执行
   - 例如暂停：应用层服务通过领域层服务向 Task 设置暂停标志
   - 由 TaskExecutor 检查到暂停标志后驱动 Task 聚合根修改状态
   - 否则按照 TaskExecutor 内部执行逻辑驱动状态变化

---

## ❌ T-032 做了什么（未解决核心问题）

### 已完成的工作

1. ✅ 修复了检查点保存逻辑（最后一个 Stage 不保存）
2. ✅ 移除了 `completeStage()` 的自动 `complete()` 调用
3. ✅ TaskExecutor 显式调用 `completeTask()`

### 为什么没有解决核心问题

**分析**：T-032 只是让状态转换"显式化"了，但并没有解决**状态转换路径混乱**的问题。

**证据**：
- TaskExecutor 中确实在调用 `taskDomainService.xxxTask()`
- 但这些调用是**在执行流程中硬编码的**
- 重试和回滚并不是通过 TaskExecutor 的正常执行流程驱动的

---

## 🔍 真正的问题：重试和回滚的状态转换失败

### 问题场景 1：重试失败

**当前流程**（推测）：
```java
// 某个地方（可能是 Facade 或 Application Service）
task = taskRepository.findById(taskId);

// 直接调用聚合方法
task.retry(fromCheckpoint);  // ❌ 可能失败：状态不是 FAILED/ROLLED_BACK

// 或者通过 TaskDomainService
taskDomainService.retryTask(task, context);  // ❌ 可能失败：状态检查不通过

// 然后创建新的 TaskExecutor 执行
TaskExecutor executor = factory.create(task, stages);
executor.execute();  // ❌ 但 Task 已经是 RUNNING，execute() 中的状态检查可能失败
```

**问题**：
1. `retry()` 把 Task 状态改为 RUNNING
2. 但 TaskExecutor.execute() 期望的是 PENDING 或 PAUSED
3. 状态转换路径不一致

---

### 问题场景 2：回滚失败

**当前流程**（推测）：
```java
// 某个地方调用回滚
task = taskRepository.findById(taskId);

// 直接调用聚合方法
task.rollback();  // ❌ 可能失败：状态检查不通过

// 或者通过 TaskDomainService
taskDomainService.startRollback(task, context);  // ❌ 状态改为 ROLLING_BACK

// 然后执行回滚逻辑
TaskExecutor executor = factory.create(task, rollbackStages);
executor.rollback();  // ❌ 但 rollback() 方法也有自己的状态检查
```

**问题**：
1. 回滚的状态转换路径与正常执行不同
2. `TaskExecutor.rollback()` 和 `TaskAggregate.rollback()` 的状态检查可能冲突

---

## 🎯 真正需要的解决方案

### 第一步：状态驱动收束到 TaskExecutor（✅ 部分完成）

**目标**：TaskExecutor 是唯一驱动状态转换的地方

**当前状态**：
- ✅ 正常执行流程：TaskExecutor.execute() 驱动
- ❌ 重试流程：不清楚入口在哪里
- ❌ 回滚流程：不清楚入口在哪里

### 第二步：语义化请求 + 标志位驱动（❌ 未完成）

**目标**：外部通过设置标志位，TaskExecutor 内部检查标志位后驱动状态转换

**当前状态**：
- ✅ 暂停：已实现（context.isPauseRequested() → taskDomainService.pauseTask()）
- ✅ 取消：已实现（context.isCancelRequested() → taskDomainService.cancelTask()）
- ❌ 重试：未实现标志位机制
- ❌ 回滚：未实现标志位机制

---

## 📋 需要明确的问题

### 问题 1：重试的当前实现是什么？

**需要查找**：
- 重试的入口在哪里？（Facade? Application Service?）
- 重试如何修改 Task 状态？
- 重试如何触发 TaskExecutor 执行？

**推测的可能实现**：
```java
// 可能方案 A：直接调用 TaskExecutor.retry()
TaskExecutor executor = factory.create(task, stages);
executor.retry(fromCheckpoint);  // TaskExecutor 有 retry() 方法

// 可能方案 B：先改状态，再execute()
task.retry(fromCheckpoint);  // Task 状态 → RUNNING
taskRepository.save(task);
executor.execute();  // 从 RUNNING 开始执行
```

### 问题 2：回滚的当前实现是什么？

**需要查找**：
- 回滚的入口在哪里？
- 回滚如何修改 Task 状态？
- 回滚如何触发 TaskExecutor 执行？

**推测的可能实现**：
```java
// 可能方案 A：调用 TaskExecutor.rollback()
TaskExecutor executor = factory.create(task, rollbackStages);
executor.rollback();  // TaskExecutor 有 rollback() 方法

// 可能方案 B：使用 prevConfig 重新走 execute
task.rollback();  // Task 状态 → ROLLING_BACK
List<TaskStage> rollbackStages = stageFactory.create(task.getPrevConfig());
executor = factory.create(task, rollbackStages);
executor.execute();  // 但 execute() 期望 PENDING/PAUSED，不是 ROLLING_BACK
```

### 问题 3：TaskExecutor 的职责边界是什么？

**当前困惑**：
- TaskExecutor 有 `execute()`, `retry()`, `rollback()` 三个方法
- 这三个方法是否应该统一为一个 `execute()` 方法？
- 重试和回滚是否应该通过标志位驱动，而不是独立方法？

---

## 🚀 建议的调查步骤

### Step 1：定位重试和回滚的入口

**任务**：找到重试和回滚是从哪里触发的

**搜索关键词**：
- `retry` 方法调用
- `rollback` 方法调用
- `TaskExecutor.retry`
- `TaskExecutor.rollback`

### Step 2：分析状态转换路径

**任务**：画出当前的状态转换流程图

**需要分析**：
- 正常执行：PENDING → RUNNING → COMPLETED
- 失败：RUNNING → FAILED
- 重试：FAILED → ??? → RUNNING
- 回滚：FAILED → ROLLING_BACK → ROLLED_BACK

### Step 3：设计统一的状态转换机制

**目标**：所有状态转换都通过 TaskExecutor 驱动

**方案选项**：

**选项 A：统一的 execute() 方法**
```java
// TaskExecutor 只有一个 execute() 方法
public TaskResult execute() {
    // 根据 Task 当前状态决定执行逻辑
    TaskStatus status = task.getStatus();
    
    if (status == PENDING || status == PAUSED) {
        // 正常执行
    } else if (status == FAILED && context.isRetryRequested()) {
        // 重试逻辑
    } else if (status == FAILED && context.isRollbackRequested()) {
        // 回滚逻辑
    }
}
```

**选项 B：独立方法 + 标志位控制**
```java
// TaskExecutor 有多个方法，但都检查标志位
public TaskResult execute() { /* 正常执行 */ }
public TaskResult retry(boolean fromCheckpoint) { /* 重试执行 */ }
public TaskResult rollback() { /* 回滚执行 */ }

// 外部通过标志位请求
context.requestRetry();
context.requestRollback();
```

**选项 C：命令模式**
```java
// TaskExecutor 接收命令对象
public TaskResult execute(TaskCommand command) {
    if (command instanceof StartCommand) { /* ... */ }
    else if (command instanceof RetryCommand) { /* ... */ }
    else if (command instanceof RollbackCommand) { /* ... */ }
}
```

---

## 📌 下一步行动

1. **立即调查**：找到重试和回滚的当前实现
2. **分析问题**：确认状态转换失败的具体原因
3. **设计方案**：统一状态转换机制
4. **实施重构**：真正完成状态驱动收束

---

**T-032 需要重新规划和实施！** 🔴

