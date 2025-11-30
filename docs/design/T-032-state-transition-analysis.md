# Task 状态转换流程分析报告

> 分析时间: 2025-11-29  
> 分析范围: TaskAggregate 状态转换实际实现 vs 设计文档  
> 状态: ⚠️ 发现重大差异

---

## 📋 执行摘要

**核心发现**: 实际实现与设计文档存在**重大差异**，主要体现在：

1. ✅ **重试流程** - 实际实现已按 T-032 修正：FAILED → PENDING → RUNNING
2. ❌ **回滚流程** - 实际实现与设计不符：FAILED → ROLLING_BACK（错误）
3. ❌ **回滚后状态** - 完成回滚应该进入什么状态不明确

---

## 📊 状态转换对比表

### 1. 正常执行流程

| 转换 | 设计文档 | 实际实现 | 状态 | 触发方法 |
|------|----------|---------|------|---------|
| CREATED → PENDING | ✅ 有 | ✅ 有 | ✅ **一致** | `markAsPending()` |
| PENDING → RUNNING | ✅ 有 | ✅ 有 | ✅ **一致** | `start()` |
| RUNNING → PAUSED | ✅ 有 | ✅ 有 | ✅ **一致** | `requestPause()` + `applyPauseAtStageBoundary()` |
| PAUSED → RUNNING | ✅ 有 | ✅ 有 | ✅ **一致** | `resume()` |
| RUNNING → COMPLETED | ✅ 有 | ✅ 有 | ✅ **一致** | `complete()` |
| RUNNING → FAILED | ✅ 有 | ✅ 有 | ✅ **一致** | `fail()` / `failStage()` |
| RUNNING → CANCELLED | ✅ 有 | ✅ 有 | ✅ **一致** | `cancel()` |

**结论**: 正常执行流程 ✅ **完全一致**

---

### 2. 重试流程（T-032 修正后）

| 转换 | 设计文档 | T-032 修正前 | T-032 修正后 | 状态 |
|------|----------|-------------|-------------|------|
| FAILED → RUNNING | ✅ 设计 | ✅ 实现 | ❌ 删除 | ⚠️ **已修正** |
| FAILED → PENDING | ❌ 无 | ❌ 无 | ✅ 新增 | ✅ **已修正** |
| PENDING → RUNNING | ✅ 有 | ✅ 有 | ✅ 有 | ✅ **一致** |

**实际流程（T-032 后）**:
```
FAILED → retry() → PENDING → startTask() → RUNNING
```

**分析**:
- ✅ T-032 修正了重试流程
- ✅ 现在重试先转到 PENDING，然后调用 startTask()
- ✅ 符合状态机设计理念（重新进入待执行状态）
- ⚠️ **但设计文档未更新**，仍然写的是 `FAILED → RUNNING`

**建议**: 更新设计文档 `state-management.md`，将重试流程改为：
```
FAILED → PENDING → RUNNING
```

---

### 3. 回滚流程（⚠️ 重大问题）

| 转换 | 设计文档 | 实际实现 | 状态 | 问题 |
|------|----------|---------|------|------|
| FAILED → ROLLING_BACK | ✅ 有 | ✅ 有 | ⚠️ **错误** | 回滚不应该是独立状态 |
| ROLLING_BACK → ROLLED_BACK | ✅ 有 | ✅ 有 | ⚠️ **错误** | 回滚成功后应该是什么状态？ |
| ROLLING_BACK → ROLLBACK_FAILED | ✅ 有 | ✅ 有 | ⚠️ **错误** | 回滚失败后如何处理？ |

**核心问题**:

根据你的澄清，**回滚 = 使用旧配置重新执行正常流程**，因此：

❌ **错误的理解（当前实现）**:
```
回滚是独立的操作流程：
FAILED → startRollback() → ROLLING_BACK
ROLLING_BACK → completeRollback() → ROLLED_BACK
```

✅ **正确的理解（应该实现）**:
```
回滚 = 特殊的重试（使用旧配置）:
FAILED → retry() → PENDING → startTask() → RUNNING
（差异只在配置来源：prevConfigSnapshot）
```

**当前代码的问题**:

1. **TaskAggregate 中存在回滚状态**:
   ```java
   public void startRollback(String reason) {
       this.status = TaskStatus.ROLLING_BACK;  // ❌ 错误
   }
   
   public void completeRollback() {
       this.status = TaskStatus.ROLLED_BACK;  // ❌ 错误
   }
   ```

2. **ExecutionPreparer 已部分修正**:
   ```java
   // ✅ T-032 已修正：回滚使用正常流程
   if (context.isRollbackRequested()) {
       deps.getTaskDomainService().retryTask(task, context);
       deps.getTaskDomainService().startTask(task, context);
       context.setExecutionMode(NORMAL);  // ✅ 正确
   }
   ```

3. **TaskDomainService 仍有回滚方法**:
   ```java
   public void startRollback(TaskAggregate task, TaskRuntimeContext context) {
       task.startRollback(context.getRollbackTargetVersion());  // ❌ 调用错误方法
       saveAndPublishEvents(task);
   }
   
   public void completeRollback(TaskAggregate task, TaskRuntimeContext context) {
       task.completeRollback();  // ❌ 调用错误方法
       saveAndPublishEvents(task);
   }
   ```

**结论**: 回滚流程 ❌ **实现错误**

---

### 4. 回滚后重试流程

| 转换 | 设计文档 | 实际实现 | 状态 | 问题 |
|------|----------|---------|------|------|
| ROLLED_BACK → RUNNING | ✅ 有 | ✅ 有 | ⚠️ **错误** | ROLLED_BACK 不应该存在 |

**分析**:
- 如果回滚不使用 ROLLING_BACK/ROLLED_BACK 状态
- 则不需要 ROLLED_BACK → RUNNING 转换
- 回滚成功后，Task 应该处于 COMPLETED 状态

---

## 🔍 详细问题分析

### Problem 1: 回滚状态的设计冲突

**设计文档说**:
```markdown
| FAILED | ROLLING_BACK | startRollback() | 有快照 | - | TaskRollingBackEvent |
| ROLLING_BACK | ROLLED_BACK | completeRollback() | 回滚全部成功 | ROLLED_BACK | TaskRolledBackEvent |
```

**你的澄清说**:
```
回滚 = 使用旧配置重新执行正常流程
- 配置来源：prevConfigSnapshot
- Stage 顺序：正常顺序
- 执行逻辑：与正常执行完全相同
```

**矛盾点**:
1. 设计文档假设回滚是独立的状态转换
2. 实际需求是回滚复用正常执行流程
3. 这导致 ROLLING_BACK/ROLLED_BACK 状态没有存在的必要

---

### Problem 2: 回滚成功后的状态不明确

**当前实现**:
```java
// 回滚成功
public void completeRollback() {
    this.status = TaskStatus.ROLLED_BACK;  // ❌ 终态
}
```

**问题**:
- ROLLED_BACK 是终态（`isTerminal() = true`）
- 但回滚成功后，应该是 COMPLETED 状态（因为是重新执行成功）
- 如果回滚失败，应该是 FAILED 状态

**正确的流程应该是**:
```
回滚请求 → 使用旧配置执行 → 成功 → COMPLETED
                            → 失败 → FAILED
```

---

### Problem 3: TaskStatus 枚举中的冗余状态

**TaskStatus.java 中定义**:
```java
ROLLING_BACK("回滚中"),      // ❌ 不需要
ROLLBACK_FAILED("回滚失败"),  // ❌ 不需要
ROLLED_BACK("已回滚"),        // ❌ 不需要
```

**分析**:
- 如果回滚使用正常执行流程，这些状态都不需要
- 回滚成功 = COMPLETED
- 回滚失败 = FAILED

---

## 📈 状态转换图对比

### 设计文档的状态转换

```
┌─────────┐
│ CREATED │
└────┬────┘
     ↓ markAsPending()
┌─────────┐
│ PENDING │
└────┬────┘
     ↓ start()
┌─────────┐  ←─────────────┐
│ RUNNING │                │ resume()
└─┬─┬─┬─┬─┘                │
  │ │ │ │                  │
  │ │ │ └→ PAUSED ─────────┘
  │ │ │
  │ │ └→ COMPLETED (终态)
  │ │
  │ └→ CANCELLED (终态)
  │
  └→ FAILED
     ├→ retry() → RUNNING
     └→ startRollback() → ROLLING_BACK
                           ├→ ROLLED_BACK (终态)
                           └→ ROLLBACK_FAILED (终态)
```

### 实际应该的状态转换（根据你的澄清）

```
┌─────────┐
│ CREATED │
└────┬────┘
     ↓ markAsPending()
┌─────────┐  ←─────────────────┐
│ PENDING │                    │ retry() (包括回滚重试)
└────┬────┘                    │
     ↓ start()                 │
┌─────────┐  ←─────────────┐   │
│ RUNNING │                │   │
└─┬─┬─┬─┬─┘                │   │
  │ │ │ │                  │   │
  │ │ │ └→ PAUSED ─────────┘   │
  │ │ │      │ resume()        │
  │ │ │                        │
  │ │ └→ COMPLETED (终态)      │
  │ │                          │
  │ └→ CANCELLED (终态)        │
  │                            │
  └→ FAILED ──────────────────┘
        ↓ retry(fromCheckpoint)
     或 retry() with prevConfig (回滚)
```

**关键差异**:
- ❌ 删除 ROLLING_BACK 状态
- ❌ 删除 ROLLED_BACK 状态
- ❌ 删除 ROLLBACK_FAILED 状态
- ✅ 回滚使用 retry() 方法
- ✅ 回滚成功 → COMPLETED
- ✅ 回滚失败 → FAILED

---

## 🎯 需要修正的代码

### 1. TaskAggregate.java

**需要删除的方法**:
```java
❌ public void startRollback(String reason)
❌ public void rollback()
❌ public void completeRollback()
❌ public void failRollback(String reason)
```

**保留的方法**:
```java
✅ public void retry()  // 用于正常重试和回滚重试
```

---

### 2. TaskDomainService.java

**需要删除的方法**:
```java
❌ public void startRollback(TaskAggregate task, TaskRuntimeContext context)
❌ public void completeRollback(TaskAggregate task, TaskRuntimeContext context)
❌ public void failRollback(TaskAggregate task, FailureInfo failure, TaskRuntimeContext context)
```

**保留的方法**:
```java
✅ public void retryTask(TaskAggregate task, TaskRuntimeContext context)
   // 用于正常重试和回滚重试
```

---

### 3. TaskStatus.java

**需要删除的状态**:
```java
❌ ROLLING_BACK("回滚中"),
❌ ROLLBACK_FAILED("回滚失败"),
❌ ROLLED_BACK("已回滚"),
```

**需要修改的方法**:
```java
// isTerminal() 中删除 ROLLED_BACK
public boolean isTerminal() {
    return this == COMPLETED
        || this == ROLLBACK_FAILED  // ❌ 删除
        || this == ROLLED_BACK      // ❌ 删除
        || this == CANCELLED;
}

// canRollback() 需要重新定义
public boolean canRollback() {
    // 回滚 = 使用旧配置重试
    return this == FAILED;  // 只有失败的任务可以回滚
}

// canRetry() 不变
public boolean canRetry() {
    return this == FAILED || this == ROLLBACK_FAILED;  // ❌ 删除 ROLLBACK_FAILED
}
```

---

### 4. ExecutionPreparer.java（已部分修正）

**当前代码**（T-032 已修正）:
```java
✅ if (context.isRollbackRequested()) {
    deps.getTaskDomainService().retryTask(task, context);  // ✅ 正确
    deps.getTaskDomainService().startTask(task, context);  // ✅ 正确
    context.setStartIndex(0);
    context.setExecutionMode(NORMAL);  // ✅ 正确
}
```

**问题**:
- ❌ 调用了 `retryTask()`，但没有设置配置来源为 prevConfigSnapshot
- ❌ 需要在 Context 或 TaskAggregate 中标记"使用旧配置"

---

## 💡 修正建议

### 方案 A: 完全删除回滚状态（推荐）

**理由**:
- ✅ 符合"回滚 = 使用旧配置重试"的定义
- ✅ 简化状态机
- ✅ 减少代码复杂度

**修改步骤**:
1. 删除 TaskStatus 中的 ROLLING_BACK、ROLLED_BACK、ROLLBACK_FAILED
2. 删除 TaskAggregate 中的回滚方法
3. 删除 TaskDomainService 中的回滚方法
4. 回滚统一使用 `retry()` 方法
5. 在 Context 或 TaskAggregate 中标记配置来源

**优点**:
- ✅ 状态机简单清晰
- ✅ 代码量减少
- ✅ 符合实际需求

**缺点**:
- ❌ 无法区分是正常重试还是回滚重试（需要通过事件或日志区分）

---

### 方案 B: 保留回滚状态，但改变语义

**理由**:
- 保持可观测性（可以看到任务正在回滚）
- 保持与设计文档一致

**修改步骤**:
1. 保留 ROLLING_BACK、ROLLED_BACK 状态
2. 修改状态转换流程：
   ```
   FAILED → retry() → PENDING → startTask() → ROLLING_BACK (如果是回滚)
   ROLLING_BACK → executeNormalStages() → COMPLETED/FAILED
   ```
3. 回滚仍然使用正常执行流程，只是多一个中间状态用于标识

**优点**:
- ✅ 可观测性更好
- ✅ 与设计文档一致

**缺点**:
- ❌ 增加状态复杂度
- ❌ ROLLING_BACK 状态与 RUNNING 状态语义重复

---

## 📋 推荐行动计划

### 立即行动（T-032 续）

1. **确定方案**：选择方案 A 或方案 B
2. **更新设计文档**：修正重试流程 FAILED → PENDING → RUNNING
3. **修正回滚流程**：根据选定方案修改代码

### 如果选择方案 A（推荐）

```
优先级: P0 (高优先级)
工作量: 4-6小时

Step 1: 删除 TaskStatus 中的回滚状态
Step 2: 删除 TaskAggregate 中的回滚方法
Step 3: 删除 TaskDomainService 中的回滚方法
Step 4: 修改 ExecutionPreparer 的回滚准备逻辑
Step 5: 修改测试用例
Step 6: 更新文档
```

---

## 🎯 总结

| 维度 | 状态 | 问题 |
|------|------|------|
| **正常执行流程** | ✅ 一致 | 无 |
| **重试流程** | ✅ 已修正 | 设计文档未更新 |
| **回滚流程** | ❌ 错误 | 状态转换不符合实际需求 |
| **回滚状态** | ❌ 冗余 | ROLLING_BACK/ROLLED_BACK 不应该存在 |
| **代码一致性** | ⚠️ 部分 | ExecutionPreparer 已修正，但聚合根未修正 |

**核心问题**: 回滚流程的实现与"回滚 = 使用旧配置重新执行"的定义不符，存在冗余的状态和方法。

**建议**: 采用方案 A，完全删除回滚状态，统一使用重试流程。

---

**分析完成！需要我立即开始修正吗？** 🚀

