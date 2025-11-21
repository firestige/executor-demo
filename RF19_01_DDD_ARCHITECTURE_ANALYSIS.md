# RF-19-01: Stage 事件发布的 DDD 架构分析

**创建日期**: 2025-11-21  
**讨论主题**: Stage 事件应该由谁发布？Infrastructure 还是 Domain Service？

---

## 一、问题陈述

### 1.1 核心矛盾

**DDD 原则**:
- ✅ 事件应该由领域服务发布
- ✅ 聚合产生事件，领域服务提取并发布

**当前设计困境**:
- ❌ CompositeServiceStage 位于 Infrastructure 层（不是领域服务）
- ❌ TaskExecutor 也在 Infrastructure 层
- ❓ Stage 不是领域实体（只是 Task 生命周期内的执行步骤）
- ❓ Stage 事件是否应该遵循"聚合产生事件，服务发布"的原则？

### 1.2 用户的关键问题

> "Stage 本身是依赖 Task 的一个步骤，只需要在 Task 生命周期内唯一，不能算是实体。那么 Stage 产生的细粒度的过程事件是否能破坏领域对象创建和管理状态、收集事件，领域服务发布事件的原则呢？"

---

## 二、架构分析

### 2.1 当前架构回顾

```
┌─────────────────────────────────────────────┐
│  Domain Layer (领域层)                       │
│  - TaskAggregate (聚合根)                   │
│    └─ 产生事件: TaskStartedEvent, etc.     │
│  - TaskDomainService (领域服务)             │
│    └─ 发布聚合事件                           │
└─────────────────────────────────────────────┘
              ↓ 依赖
┌─────────────────────────────────────────────┐
│  Infrastructure Layer (基础设施层)           │
│  - TaskExecutor (执行器)                    │
│    └─ 编排 Stage 执行                       │
│  - CompositeServiceStage (Stage 实现)       │
│    └─ 执行 Steps                            │
└─────────────────────────────────────────────┘
```

**关键观察**:
1. TaskDomainService 已经有 `completeStage()` 方法
2. TaskExecutor 调用 `TaskDomainService.completeStage()` 来完成 Stage
3. **但没有发布 Stage 级别的细粒度事件**

---

## 三、Stage 在 DDD 中的定位

### 3.1 Stage 是什么？

从 DDD 视角分析：

| 特征 | Stage | 结论 |
|------|-------|------|
| **是否有唯一标识？** | 否，只有 name（在 Task 内唯一） | ❌ 不是实体 |
| **是否有生命周期？** | 有（started → completed/failed） | ⚠️ 类似实体 |
| **是否可独立存在？** | 否，依赖 Task | ❌ 不是聚合根 |
| **是否有业务逻辑？** | 是（执行 steps，处理失败） | ✅ 有业务含义 |
| **是否有状态变更？** | 是（StageStatus） | ✅ 有状态管理 |

**结论**: Stage 是一个 **值对象** + **执行过程对象**，介于值对象和实体之间

### 3.2 Stage 在领域模型中的角色

```
TaskAggregate (聚合根)
  ├─ taskId: TaskId (实体标识)
  ├─ status: TaskStatus (状态)
  ├─ stageProgress: StageProgress (值对象)
  │   ├─ currentStageIndex: int
  │   ├─ totalStages: int
  │   └─ completedStageNames: List<String>
  └─ stageResults: List<StageResult> (执行记录)
      └─ StageResult (值对象)
          ├─ stageName: String
          ├─ status: StageStatus
          ├─ duration: Duration
          └─ stepResults: List<StepResult>
```

**关键发现**:
- ✅ TaskAggregate 持有 StageProgress（值对象）
- ✅ TaskAggregate 持有 StageResult 列表（执行记录）
- ✅ Stage 的状态变更本质上是 **TaskAggregate 状态的一部分**

---

## 四、解决方案对比

### 方案 A: Infrastructure 层直接发布（原方案）

```java
// CompositeServiceStage.execute()
public StageResult execute(TaskRuntimeContext ctx) {
    publishStageStartedEvent(ctx);  // ❌ Infrastructure 发布领域事件
    
    // execute steps...
    
    publishStageCompletedEvent(ctx);  // ❌ Infrastructure 发布领域事件
}
```

**优点**:
- ✅ 实现简单
- ✅ 无需修改领域层

**缺点**:
- ❌ **违反 DDD 原则**：Infrastructure 层不应发布领域事件
- ❌ 领域事件由非领域层产生，破坏架构分层
- ❌ 如果后续需要在领域层处理 Stage 事件逻辑，会很困难

---

### 方案 B: TaskAggregate 产生 Stage 事件（推荐）⭐⭐⭐⭐⭐

**核心思想**: Stage 的状态变更本质上是 TaskAggregate 的状态变更

```java
// TaskAggregate.java
public class TaskAggregate {
    private final List<TaskStatusEvent> domainEvents = new ArrayList<>();
    
    /**
     * 开始执行 Stage
     */
    public void startStage(String stageName, int totalSteps) {
        // 业务逻辑：记录当前 Stage
        // ... existing code ...
        
        // 产生领域事件
        TaskStageStartedEvent event = new TaskStageStartedEvent(
            TaskInfo.from(this), 
            stageName, 
            totalSteps
        );
        addDomainEvent(event);
    }
    
    /**
     * 完成 Stage（已存在，但需要产生事件）
     */
    public void completeStage(String stageName, Duration duration) {
        // 业务逻辑
        if (this.status != TaskStatus.RUNNING) {
            throw new IllegalStateException("只有运行中的任务才能完成 Stage");
        }
        
        StageResult result = new StageResult(stageName, duration);
        this.stageResults.add(result);
        this.stageProgress = this.stageProgress.advance();
        
        // 产生领域事件
        TaskStageCompletedEvent event = new TaskStageCompletedEvent(
            TaskInfo.from(this), 
            stageName, 
            result
        );
        addDomainEvent(event);
    }
    
    /**
     * Stage 失败
     */
    public void failStage(String stageName, FailureInfo failureInfo) {
        // 业务逻辑：记录失败信息
        // ...
        
        // 产生领域事件
        TaskStageFailedEvent event = new TaskStageFailedEvent(
            TaskInfo.from(this), 
            stageName, 
            failureInfo
        );
        addDomainEvent(event);
    }
}
```

```java
// TaskDomainService.java
public class TaskDomainService {
    
    /**
     * 开始执行 Stage
     */
    public void startStage(TaskAggregate task, String stageName, int totalSteps) {
        task.startStage(stageName, totalSteps);
        saveAndPublishEvents(task);  // ✅ 领域服务统一发布
    }
    
    /**
     * 完成 Stage
     */
    public void completeStage(TaskAggregate task, String stageName, Duration duration) {
        task.completeStage(stageName, duration);  // ✅ 聚合产生事件
        saveAndPublishEvents(task);  // ✅ 领域服务发布
    }
    
    /**
     * Stage 失败
     */
    public void failStage(TaskAggregate task, String stageName, FailureInfo failureInfo) {
        task.failStage(stageName, failureInfo);
        saveAndPublishEvents(task);
    }
    
    // 统一的事件发布方法（已存在）
    private void saveAndPublishEvents(TaskAggregate task) {
        taskRepository.save(task);
        domainEventPublisher.publishAll(task.getDomainEvents());
        task.clearDomainEvents();
    }
}
```

```java
// TaskExecutor.java (Infrastructure)
public TaskResult execute() {
    // ...
    
    for (int i = startIndex; i < stages.size(); i++) {
        TaskStage stage = stages.get(i);
        String stageName = stage.getName();
        int totalSteps = stage.getSteps().size();
        
        // 1. ✅ 通过领域服务开始 Stage
        taskDomainService.startStage(task, stageName, totalSteps);
        
        // 2. 执行 Stage（Infrastructure 职责）
        StageResult stageResult = stage.execute(context);
        
        // 3. ✅ 根据结果通过领域服务更新状态
        if (stageResult.isSuccess()) {
            taskDomainService.completeStage(task, stageName, stageResult.getDuration());
        } else {
            taskDomainService.failStage(task, stageName, stageResult.getFailureInfo());
        }
    }
}
```

**优点**:
- ✅ **完全符合 DDD 原则**：聚合产生事件，领域服务发布
- ✅ **清晰的职责分离**：
  - TaskAggregate: 管理 Stage 状态，产生事件
  - TaskDomainService: 发布事件
  - TaskExecutor: 编排执行流程
  - CompositeServiceStage: 执行具体步骤（不涉及领域事件）
- ✅ **易于测试**：聚合的事件产生逻辑可以独立测试
- ✅ **易于扩展**：如果需要在领域层处理 Stage 事件逻辑，非常自然

**缺点**:
- ⚠️ 需要修改 TaskAggregate（添加 3 个方法）
- ⚠️ 需要修改 TaskDomainService（添加 2 个方法：startStage, failStage）
- ⚠️ TaskExecutor 需要调用领域服务（但这是正确的架构）

---

### 方案 C: 混合方案（不推荐）

让 TaskDomainService 在 completeStage() 时主动创建和发布 Stage 事件。

**缺点**:
- ❌ 违反"聚合产生事件"原则
- ❌ 领域服务不应该直接创建领域事件（应由聚合创建）

---

## 五、架构正确性分析

### 5.1 Stage 事件的本质

**关键认知**: Stage 事件本质上是 **TaskAggregate 状态变更的细粒度表达**

| 事件类型 | 本质 | 是否应该由聚合产生 |
|---------|------|------------------|
| TaskStartedEvent | Task 整体状态变更 | ✅ 是 |
| TaskStageStartedEvent | Task 的 Stage 开始（状态的一部分） | ✅ 是 |
| TaskStageCompletedEvent | Task 的 Stage 完成（stageProgress 变更） | ✅ 是 |
| TaskStageFailedEvent | Task 的 Stage 失败（状态的一部分） | ✅ 是 |
| TaskCompletedEvent | Task 整体状态变更 | ✅ 是 |

**结论**: Stage 事件应该由 TaskAggregate 产生，因为：
1. Stage 状态是 TaskAggregate 状态的一部分
2. stageProgress 是 TaskAggregate 的值对象
3. Stage 的变更影响 TaskAggregate 的状态

### 5.2 是否破坏 DDD 原则？

**答案**: ❌ **不破坏**，反而是 **更符合** DDD 原则

**理由**:
1. **聚合完整性**: Stage 的状态变更是 TaskAggregate 不变式的一部分
   ```java
   // 不变式：currentStageIndex 不能超过 totalStages
   public void completeStage(String stageName, Duration duration) {
       if (stageProgress.isCompleted()) {
           throw new IllegalStateException("所有 Stage 已完成");
       }
       this.stageProgress = stageProgress.advance();  // ✅ 保护不变式
   }
   ```

2. **事件溯源**: Stage 事件是 TaskAggregate 生命周期的一部分
   - 如果需要事件溯源，Stage 事件是重建 TaskAggregate 状态的必要信息

3. **领域语言**: "Task 开始执行 Stage X" 是领域概念，不是技术实现细节
   - 应该由领域层表达，而不是基础设施层

---

## 六、推荐方案实施细节

### 6.1 修改清单

| 文件 | 修改内容 | 难度 |
|------|---------|------|
| `TaskAggregate.java` | 添加 3 个业务方法：startStage(), completeStage()已有但需产生事件, failStage() | 中 |
| `TaskDomainService.java` | 添加 startStage(), failStage()，修改 completeStage() | 简单 |
| `TaskExecutor.java` | 调用领域服务的 startStage() 和 failStage() | 简单 |
| `CompositeServiceStage.java` | 无需修改（不发布事件） | 无 |

### 6.2 代码变更示例

#### 6.2.1 TaskAggregate 增强

```java
public class TaskAggregate {
    // ...existing fields...
    
    /**
     * 开始执行 Stage（新增）
     */
    public void startStage(String stageName, int totalSteps) {
        if (this.status != TaskStatus.RUNNING) {
            throw new IllegalStateException("只有运行中的任务才能开始 Stage");
        }
        
        // 产生领域事件
        TaskStageStartedEvent event = new TaskStageStartedEvent(
            TaskInfo.from(this), 
            stageName, 
            totalSteps
        );
        addDomainEvent(event);
        
        logger.debug("Task {} 开始执行 Stage: {}", taskId, stageName);
    }
    
    /**
     * 完成 Stage（修改：添加事件产生）
     */
    public void completeStage(String stageName, Duration duration) {
        if (this.status != TaskStatus.RUNNING) {
            throw new IllegalStateException("只有运行中的任务才能完成 Stage");
        }
        
        // 业务逻辑（已存在）
        StageResult result = StageResult.success(stageName, duration);
        this.stageResults.add(result);
        this.stageProgress = this.stageProgress.advance();
        
        // 产生领域事件（新增）
        TaskStageCompletedEvent event = new TaskStageCompletedEvent(
            TaskInfo.from(this), 
            stageName, 
            result
        );
        addDomainEvent(event);
        
        logger.debug("Task {} 完成 Stage: {}, 耗时: {}ms", taskId, stageName, duration.toMillis());
    }
    
    /**
     * Stage 失败（新增）
     */
    public void failStage(String stageName, FailureInfo failureInfo) {
        if (this.status != TaskStatus.RUNNING) {
            throw new IllegalStateException("只有运行中的任务才能记录 Stage 失败");
        }
        
        // 业务逻辑：记录失败的 Stage
        StageResult result = StageResult.failure(stageName, failureInfo);
        this.stageResults.add(result);
        
        // 产生领域事件
        TaskStageFailedEvent event = new TaskStageFailedEvent(
            TaskInfo.from(this), 
            stageName, 
            failureInfo
        );
        addDomainEvent(event);
        
        logger.warn("Task {} Stage 失败: {}, 原因: {}", taskId, stageName, failureInfo.getErrorMessage());
    }
}
```

#### 6.2.2 TaskDomainService 增强

```java
public class TaskDomainService {
    
    /**
     * 开始执行 Stage（新增）
     */
    public void startStage(TaskAggregate task, String stageName, int totalSteps) {
        logger.debug("[TaskDomainService] 开始执行 Stage: {}, stage: {}", task.getTaskId(), stageName);
        
        if (task.getStatus() != TaskStatus.RUNNING) {
            throw new IllegalStateException("只有运行中的任务才能开始 Stage，当前状态: " + task.getStatus());
        }
        
        task.startStage(stageName, totalSteps);  // ✅ 聚合产生事件
        saveAndPublishEvents(task);  // ✅ 领域服务发布事件
    }
    
    /**
     * 完成 Stage（修改：completeStage 内部会产生事件）
     */
    public void completeStage(TaskAggregate task, String stageName, Duration duration, TaskRuntimeContext context) {
        logger.debug("[TaskDomainService] 完成 Stage: {}, stage: {}", task.getTaskId(), stageName);
        
        if (task.getStatus() != TaskStatus.RUNNING) {
            throw new IllegalStateException("只有运行中的任务才能完成 Stage，当前状态: " + task.getStatus());
        }
        
        task.completeStage(stageName, duration);  // ✅ 聚合产生事件
        saveAndPublishEvents(task);  // ✅ 领域服务发布事件
    }
    
    /**
     * Stage 失败（新增）
     */
    public void failStage(TaskAggregate task, String stageName, FailureInfo failureInfo) {
        logger.warn("[TaskDomainService] Stage 失败: {}, stage: {}, reason: {}", 
            task.getTaskId(), stageName, failureInfo.getErrorMessage());
        
        if (task.getStatus() != TaskStatus.RUNNING) {
            throw new IllegalStateException("只有运行中的任务才能记录 Stage 失败，当前状态: " + task.getStatus());
        }
        
        task.failStage(stageName, failureInfo);  // ✅ 聚合产生事件
        saveAndPublishEvents(task);  // ✅ 领域服务发布事件
    }
}
```

#### 6.2.3 TaskExecutor 调用领域服务

```java
public class TaskExecutor {
    
    public TaskResult execute() {
        // ...existing code...
        
        for (int i = startIndex; i < stages.size(); i++) {
            TaskStage stage = stages.get(i);
            String stageName = stage.getName();
            int totalSteps = stage.getSteps().size();
            
            // 1. ✅ 通过领域服务开始 Stage
            taskDomainService.startStage(task, stageName, totalSteps);
            
            // 2. 执行 Stage（Infrastructure 职责）
            log.info("开始执行 Stage: {}, taskId: {}", stageName, taskId);
            context.injectMdc(stageName);
            
            StageResult stageResult = stage.execute(context);
            
            // 3. ✅ 根据结果通过领域服务更新状态
            if (stageResult.isSuccess()) {
                Duration duration = stageResult.getDuration();
                taskDomainService.completeStage(task, stageName, duration, context);
                
                completedStages.add(stageResult);
                checkpointService.saveCheckpoint(task, extractStageNames(completedStages), i);
                
                log.info("Stage 执行成功: {}, 耗时: {}ms, taskId: {}", 
                    stageName, duration.toMillis(), taskId);
            } else {
                log.error("Stage 执行失败: {}, 原因: {}, taskId: {}", 
                    stageName, stageResult.getFailureInfo().getErrorMessage(), taskId);
                
                // ✅ 先记录 Stage 失败
                taskDomainService.failStage(task, stageName, stageResult.getFailureInfo());
                
                // ✅ 再标记 Task 失败
                if (stateTransitionService.canTransition(task, TaskStatus.FAILED, context)) {
                    taskDomainService.failTask(task, stageResult.getFailureInfo(), context);
                }
                
                stopHeartbeat();
                releaseTenantLock();
                
                return TaskResult.fail(...);
            }
        }
        
        // ...existing code...
    }
}
```

---

## 七、架构优势总结

### 7.1 符合 DDD 原则

| DDD 原则 | 方案 A (Infrastructure 发布) | 方案 B (聚合产生) |
|---------|---------------------------|-----------------|
| 聚合产生事件 | ❌ 不符合 | ✅ 符合 |
| 领域服务发布事件 | ❌ 不符合 | ✅ 符合 |
| 分层清晰 | ❌ Infrastructure 侵入领域 | ✅ 清晰分层 |
| 聚合完整性 | ⚠️ Stage 状态未完全封装 | ✅ 完全封装 |

### 7.2 可维护性

**方案 B 的优势**:
1. ✅ **测试性**: 聚合的事件产生逻辑可以独立测试
2. ✅ **可读性**: 领域逻辑集中在领域层
3. ✅ **扩展性**: 如果需要在领域层处理 Stage 事件，无需修改架构
4. ✅ **一致性**: 所有事件的产生和发布逻辑保持一致

### 7.3 事件流完整性

```
事件流程（方案 B）:

TaskAggregate.startStage()
  └─ 产生 TaskStageStartedEvent
      └─ TaskDomainService.startStage()
          └─ 发布 TaskStageStartedEvent

TaskAggregate.completeStage()
  └─ 产生 TaskStageCompletedEvent
      └─ TaskDomainService.completeStage()
          └─ 发布 TaskStageCompletedEvent

TaskAggregate.failStage()
  └─ 产生 TaskStageFailedEvent
      └─ TaskDomainService.failStage()
          └─ 发布 TaskStageFailedEvent
```

**一致性**: 与现有的 Task 级别事件流程完全一致

---

## 八、最终推荐

### ✅ 推荐方案：方案 B - TaskAggregate 产生 Stage 事件

**核心理由**:
1. **Stage 状态是 TaskAggregate 状态的一部分**，应该由聚合管理
2. **完全符合 DDD 原则**："聚合产生事件，领域服务发布事件"
3. **不破坏现有架构**，反而使架构更加清晰和一致
4. **易于测试和维护**

### 变更影响评估

| 影响维度 | 评估 |
|---------|------|
| 架构变更 | 低（增强现有架构，不破坏） |
| 代码量 | 中（约 150 行新增代码） |
| 测试工作 | 中（需要补充聚合单元测试） |
| 风险 | 低（符合现有模式） |
| 收益 | 高（架构更清晰，可维护性提升） |

---

## 九、待确认问题

### 请用户确认

1. **架构方向**: 是否同意 Stage 事件应该由 TaskAggregate 产生？
2. **实施方案**: 是否同意实施方案 B？
3. **优先级**: 是否接受增加的修改工作量（约 150 行代码）？

如果同意，我将：
1. 修改 TaskAggregate（添加 startStage(), 修改 completeStage(), 添加 failStage()）
2. 修改 TaskDomainService（添加 startStage(), failStage()）
3. 修改 TaskExecutor（调用新的领域服务方法）
4. 补充单元测试

---

**总结**: 方案 B 不仅不会破坏 DDD 原则，反而是更严格地遵循 DDD 原则的正确做法。

