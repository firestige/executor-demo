# RF-19-01: CompositeServiceStage 事件发布增强 - 详细设计

**创建日期**: 2025-11-21  
**状态**: 🟡 待用户确认  
**优先级**: P0 - 最高  
**预计时间**: 2-3 小时

---

## 🎯 快速总结

### 关键变更
1. **TaskRuntimeContext**: 添加 `task` 和 `eventPublisher` 两个字段
2. **TaskExecutor**: 在执行开始时注入 task 和 eventPublisher 到 context
3. **CompositeServiceStage**: 在 execute() 方法中发布 3 种事件（started/completed/failed）

### 核心设计决策
- ✅ **方案**: 通过 TaskRuntimeContext 传递依赖（最小侵入）
- ✅ **向后兼容**: 完全兼容，只是新增功能
- ✅ **异常处理**: 事件发布失败不影响业务执行
- ✅ **sequenceId**: Stage 事件不维护独立序列号

### 实施复杂度
- **代码量**: ~200 行（含测试）
- **修改文件**: 3 个主代码文件 + 2 个测试文件
- **风险**: 低
- **收益**: 高（显著提升可观测性）

---

## 一、问题分析

### 1.1 现状
当前 `CompositeServiceStage.execute()` 方法执行 Stage 时：
- ✅ 已有完整的执行逻辑（顺序执行 steps）
- ✅ 已有 StageResult 返回值
- ❌ **没有发布任何事件**（TaskStageStartedEvent, TaskStageCompletedEvent, TaskStageFailedEvent）

### 1.2 影响
- 缺少 Stage 级别的可观测性
- 外部系统无法监听 Stage 的执行过程
- 日志中有 Stage 信息，但事件系统中没有

### 1.3 预期目标
在 Stage 执行的关键节点发布事件：
1. **Stage 开始时** → TaskStageStartedEvent
2. **Stage 成功完成时** → TaskStageCompletedEvent
3. **Stage 执行失败时** → TaskStageFailedEvent

---

## 二、技术方案设计

### 2.1 方案选择：通过 TaskRuntimeContext 传递 EventPublisher

#### 方案对比

| 方案 | 优点 | 缺点 | 推荐度 |
|------|------|------|--------|
| **方案 A**: 构造器注入 | 符合依赖注入原则 | 需要修改所有创建 Stage 的地方 | ⭐⭐⭐ |
| **方案 B**: 参数传递（推荐）| 无需修改构造器，侵入性最小 | EventPublisher 需要通过 Context 传递 | ⭐⭐⭐⭐⭐ |
| **方案 C**: 静态单例 | 实现简单 | 不利于测试，违反 DI 原则 | ⭐ |

**最终选择**: **方案 B - 通过 TaskRuntimeContext 传递**

**理由**：
1. TaskRuntimeContext 本身就是执行上下文的容器
2. 无需修改 Stage 构造器签名（保持稳定性）
3. 易于测试（可以注入 Mock EventPublisher）
4. 符合现有架构（TaskExecutor 已经持有 EventPublisher）

---

### 2.2 详细设计

#### 2.2.1 TaskRuntimeContext 增强

**关键发现**: TaskRuntimeContext 当前**没有** `getTask()` 方法，也没有持有 TaskAggregate 引用。

**需要添加两个字段**:

```java
// 在 TaskRuntimeContext 中添加 EventPublisher 和 TaskAggregate
public class TaskRuntimeContext {
    // ...existing fields...
    
    private TaskAggregate task; // 新增：持有 Task 聚合引用
    private ApplicationEventPublisher eventPublisher; // 新增：事件发布器
    
    // Getter & Setter for task
    public TaskAggregate getTask() {
        return task;
    }
    
    public void setTask(TaskAggregate task) {
        this.task = task;
    }
    
    // Getter & Setter for eventPublisher
    public ApplicationEventPublisher getEventPublisher() {
        return eventPublisher;
    }
    
    public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }
}
```

**设计理由**:
1. **Task 引用**: Stage 事件需要 TaskInfo，必须从 TaskAggregate 获取
2. **EventPublisher**: 用于发布事件
3. **保持轻量**: 只添加必要的引用，不影响现有功能

#### 2.2.2 TaskExecutor 注入 TaskAggregate 和 EventPublisher 到 Context

```java
// TaskExecutor.execute() 方法开始时
public TaskResult execute() {
    // 注入 Task 和 EventPublisher 到上下文
    context.setTask(task);  // 新增：注入 Task 聚合
    context.setEventPublisher(technicalEventPublisher);  // 注入 EventPublisher
    
    // ...existing code...
}
```

**注意事项**:
1. 必须在执行任何 Stage 之前注入
2. Task 和 EventPublisher 都是必需的
3. 如果 Task 状态变更，Context 中的引用会自动反映最新状态（引用传递）

#### 2.2.3 CompositeServiceStage 发布事件

```java
@Override
public StageResult execute(TaskRuntimeContext ctx) {
    StageResult result = StageResult.start(name);
    
    // 1. 发布 TaskStageStartedEvent
    publishStageStartedEvent(ctx, result);
    
    // 2. 执行所有 steps（保持原有逻辑）
    for (StageStep step : steps) {
        var stepRes = StepResult.start(step.getStepName());
        try {
            ctx.injectMdc(step.getStepName());
            step.execute(ctx);
            stepRes.finishSuccess();
            result.addStepResult(stepRes);
        } catch (Exception ex) {
            log.error("Stage step failed: stage={}, step={}, err={}", 
                name, step.getStepName(), ex.getMessage(), ex);
            FailureInfo failureInfo = FailureInfo.fromException(ex, ErrorType.SYSTEM_ERROR, name);
            stepRes.finishFailure(ex.getMessage());
            result.addStepResult(stepRes);
            result.failure(failureInfo);
            
            // 3. 发布 TaskStageFailedEvent
            publishStageFailedEvent(ctx, result);
            
            return result;
        }
    }
    
    result.success();
    
    // 4. 发布 TaskStageCompletedEvent
    publishStageCompletedEvent(ctx, result);
    
    return result;
}

// 辅助方法：发布 Stage 开始事件
private void publishStageStartedEvent(TaskRuntimeContext ctx, StageResult result) {
    try {
        ApplicationEventPublisher eventPublisher = ctx.getEventPublisher();
        if (eventPublisher == null) {
            log.warn("EventPublisher not available in context, skip publishing TaskStageStartedEvent");
            return;
        }
        
        TaskAggregate task = ctx.getTask();
        if (task == null) {
            log.warn("Task not available in context, skip publishing TaskStageStartedEvent");
            return;
        }
        
        TaskInfo taskInfo = TaskInfo.from(task);
        TaskStageStartedEvent event = new TaskStageStartedEvent(taskInfo, name, steps.size());
        eventPublisher.publishEvent(event);
        
        log.debug("Published TaskStageStartedEvent: stage={}, taskId={}", name, task.getTaskId());
    } catch (Exception ex) {
        // ✅ 设计决策：事件发布失败不应中断 Stage 执行
        log.error("Failed to publish TaskStageStartedEvent: stage={}, error={}", name, ex.getMessage(), ex);
    }
}

// 辅助方法：发布 Stage 完成事件
private void publishStageCompletedEvent(TaskRuntimeContext ctx, StageResult result) {
    try {
        ApplicationEventPublisher eventPublisher = ctx.getEventPublisher();
        if (eventPublisher == null) {
            log.warn("EventPublisher not available in context, skip publishing TaskStageCompletedEvent");
            return;
        }
        
        TaskAggregate task = ctx.getTask();
        if (task == null) {
            log.warn("Task not available in context, skip publishing TaskStageCompletedEvent");
            return;
        }
        
        TaskInfo taskInfo = TaskInfo.from(task);
        TaskStageCompletedEvent event = new TaskStageCompletedEvent(taskInfo, name, result);
        eventPublisher.publishEvent(event);
        
        log.debug("Published TaskStageCompletedEvent: stage={}, taskId={}, duration={}ms", 
            name, task.getTaskId(), result.getDuration().toMillis());
    } catch (Exception ex) {
        log.error("Failed to publish TaskStageCompletedEvent: stage={}, error={}", name, ex.getMessage(), ex);
    }
}

// 辅助方法：发布 Stage 失败事件
private void publishStageFailedEvent(TaskRuntimeContext ctx, StageResult result) {
    try {
        ApplicationEventPublisher eventPublisher = ctx.getEventPublisher();
        if (eventPublisher == null) {
            log.warn("EventPublisher not available in context, skip publishing TaskStageFailedEvent");
            return;
        }
        
        TaskAggregate task = ctx.getTask();
        if (task == null) {
            log.warn("Task not available in context, skip publishing TaskStageFailedEvent");
            return;
        }
        
        TaskInfo taskInfo = TaskInfo.from(task);
        FailureInfo failureInfo = result.getFailureInfo();
        TaskStageFailedEvent event = new TaskStageFailedEvent(taskInfo, name, failureInfo);
        eventPublisher.publishEvent(event);
        
        log.debug("Published TaskStageFailedEvent: stage={}, taskId={}, error={}", 
            name, task.getTaskId(), failureInfo.getErrorMessage());
    } catch (Exception ex) {
        log.error("Failed to publish TaskStageFailedEvent: stage={}, error={}", name, ex.getMessage(), ex);
    }
}
```

---

## 三、关键设计决策

### 3.1 事件发布器的注入方式 ✅

**决策**: 通过 TaskRuntimeContext 传递

**原因**:
1. 无需修改 CompositeServiceStage 构造器（保持接口稳定）
2. TaskRuntimeContext 是执行上下文的自然容器
3. TaskExecutor 已经持有 ApplicationEventPublisher
4. 易于在测试中注入 Mock 实现

---

### 3.2 事件中包含的字段 ✅

**TaskStageStartedEvent**:
```java
- TaskInfo (taskId, tenantId, planId, status, deployUnitName, deployUnitVersion)
- String stageName
- int totalSteps (Stage 中的 step 数量)
- StageStatus: RUNNING
- LocalDateTime timestamp (继承自 DomainEvent)
```

**TaskStageCompletedEvent**:
```java
- TaskInfo
- String stageName
- StageResult stageResult (包含执行结果和耗时)
- StageStatus: COMPLETED
- LocalDateTime timestamp
```

**TaskStageFailedEvent**:
```java
- TaskInfo
- String stageName
- FailureInfo failureInfo (错误详情)
- StageStatus: FAILED
- LocalDateTime timestamp
```

**sequenceId 管理**:
- ❌ **不在 Stage 事件中管理 sequenceId**
- ✅ **理由**: Stage 事件是 Task 事件的细粒度补充，不需要独立的幂等性控制
- ✅ **sequenceId 仅在 Task 级别事件中维护**（TaskStartedEvent, TaskCompletedEvent 等）

---

### 3.3 异常处理策略 ✅

**决策**: 事件发布失败不影响 Stage 执行

**原因**:
1. **业务优先**: Stage 执行是核心业务逻辑，事件是辅助性的可观测性功能
2. **降级优雅**: 事件系统故障不应导致业务中断
3. **日志兜底**: 所有 Stage 执行信息都有日志记录，事件只是额外的观测手段

**实现**:
```java
try {
    // 发布事件逻辑
} catch (Exception ex) {
    log.error("Failed to publish event, but continue execution: {}", ex.getMessage(), ex);
    // 不抛出异常，继续执行
}
```

---

### 3.4 与现有事件系统的集成方式 ✅

**集成点**:
1. **复用现有事件基类**: TaskStageStatusEvent（已存在）
2. **复用现有事件发布机制**: ApplicationEventPublisher
3. **保持事件风格一致**: 与 TaskStatusEvent 保持相同的结构

**事件流**:
```
TaskExecutor.execute()
  │
  ├─ [初始化] context.setTask(task)
  ├─ [初始化] context.setEventPublisher(eventPublisher)
  │
  ├─ TaskStartedEvent (Task 级别) ← 已存在
  │
  ├─ Stage 1: CompositeServiceStage.execute(context)
  │   │
  │   ├─ TaskStageStartedEvent ← ✨ 新增
  │   │  └─ 包含: taskInfo, stageName="Stage1", totalSteps=3
  │   │
  │   ├─ Step 1 执行
  │   ├─ Step 2 执行
  │   ├─ Step 3 执行
  │   │
  │   └─ TaskStageCompletedEvent ← ✨ 新增
  │      └─ 包含: taskInfo, stageName, StageResult(duration, stepResults)
  │
  ├─ Stage 2: CompositeServiceStage.execute(context)
  │   │
  │   ├─ TaskStageStartedEvent ← ✨ 新增
  │   │
  │   ├─ Step 1 执行
  │   ├─ Step 2 执行 ❌ 失败
  │   │
  │   └─ TaskStageFailedEvent ← ✨ 新增
  │      └─ 包含: taskInfo, stageName, FailureInfo(errorMessage, errorType)
  │
  └─ TaskFailedEvent (Task 级别) ← 已存在
```

**事件层级**:
```
Task 级别事件 (TaskStatusEvent)
  ├─ TaskStartedEvent
  ├─ TaskCompletedEvent
  ├─ TaskFailedEvent
  └─ ... (其他 Task 事件)

Stage 级别事件 (TaskStageStatusEvent) ← 细粒度补充
  ├─ TaskStageStartedEvent ✨
  ├─ TaskStageCompletedEvent ✨
  └─ TaskStageFailedEvent ✨
```

---

## 四、需要修改的文件

### 4.1 主代码修改

| 文件 | 修改内容 | 复杂度 |
|------|---------|--------|
| `TaskRuntimeContext.java` | 添加 task 和 eventPublisher 两个字段 + getter/setter | 简单 |
| `TaskExecutor.java` | 在 execute() 开始时注入 task 和 eventPublisher 到 context | 简单 |
| `CompositeServiceStage.java` | 添加 3 个事件发布辅助方法 + 在 execute() 中调用 | 中等 |

**总代码量**: 约 150-200 行（含注释和日志）

### 4.2 测试修改

| 测试文件 | 修改内容 |
|---------|---------|
| `CompositeServiceStageTest.java` | 验证事件发布逻辑（使用 Mock EventPublisher）|
| `TaskExecutorIntegrationTest.java` | 验证端到端事件流 |

---

## 五、测试策略

### 5.1 单元测试

**CompositeServiceStageTest**:
```java
@Test
void shouldPublishStageStartedEvent() {
    // Given
    ApplicationEventPublisher mockPublisher = mock(ApplicationEventPublisher.class);
    TaskRuntimeContext context = createContext(mockPublisher);
    CompositeServiceStage stage = new CompositeServiceStage("test-stage", steps);
    
    // When
    stage.execute(context);
    
    // Then
    ArgumentCaptor<TaskStageStartedEvent> captor = 
        ArgumentCaptor.forClass(TaskStageStartedEvent.class);
    verify(mockPublisher).publishEvent(captor.capture());
    
    TaskStageStartedEvent event = captor.getValue();
    assertEquals("test-stage", event.getStageName());
    assertEquals(steps.size(), event.getTotalSteps());
}

@Test
void shouldPublishStageCompletedEventWhenSuccess() {
    // 验证成功时发布 TaskStageCompletedEvent
}

@Test
void shouldPublishStageFailedEventWhenStepFails() {
    // 验证失败时发布 TaskStageFailedEvent
}

@Test
void shouldContinueExecutionWhenEventPublishingFails() {
    // 验证事件发布失败不影响 Stage 执行
    ApplicationEventPublisher faultyPublisher = mock(ApplicationEventPublisher.class);
    doThrow(new RuntimeException("Event publishing failed"))
        .when(faultyPublisher).publishEvent(any());
    
    // Stage 应该继续执行并成功
}
```

### 5.2 集成测试

**TaskExecutorIntegrationTest**:
```java
@Test
void shouldPublishCompleteEventFlow() {
    // Given: 完整的 TaskExecutor 配置
    // When: 执行任务
    // Then: 验证事件顺序
    //   1. TaskStartedEvent
    //   2. TaskStageStartedEvent (Stage 1)
    //   3. TaskStageCompletedEvent (Stage 1)
    //   4. TaskStageStartedEvent (Stage 2)
    //   5. TaskStageCompletedEvent (Stage 2)
    //   6. TaskCompletedEvent
}
```

---

## 六、影响范围评估

### 6.1 向后兼容性

✅ **完全向后兼容**

**原因**:
1. 不修改任何现有接口签名
2. 只在 TaskRuntimeContext 中添加可选字段
3. 事件发布是新增功能，不影响现有逻辑
4. 如果 EventPublisher 为 null，只记录警告日志，不抛异常

### 6.2 性能影响

✅ **影响极小**

**分析**:
1. 每个 Stage 增加 3 次事件发布（started/completed/failed）
2. 事件发布是异步操作（Spring 默认行为）
3. 事件发布失败有快速失败机制（try-catch）

**预估性能开销**: < 1ms per Stage

### 6.3 依赖变更

✅ **无新增依赖**

**理由**:
- ApplicationEventPublisher: 已存在
- TaskStageStatusEvent 及其子类: 已存在
- 无需引入新的第三方库

---

## 七、实施计划

### Phase 1: 核心实现（1 小时）
1. ✅ 修改 TaskRuntimeContext（添加 eventPublisher 字段）
2. ✅ 修改 TaskExecutor（注入 eventPublisher 到 context）
3. ✅ 修改 CompositeServiceStage（添加事件发布逻辑）

### Phase 2: 单元测试（30 分钟）
4. ✅ CompositeServiceStageTest（3 个测试用例）

### Phase 3: 集成测试（30 分钟）
5. ✅ TaskExecutorIntegrationTest（事件流验证）

### Phase 4: 验证与文档（30 分钟）
6. ✅ 手动测试验证
7. ✅ 更新 ARCHITECTURE_DESIGN_REPORT.md
8. ✅ 更新 GLOSSARY.md（补充 Stage 事件说明）

**总计**: 2.5 小时

---

## 八、风险与缓解

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| TaskRuntimeContext 没有 task 引用 | 已解决 | - | 在设计中添加 task 字段和 getter/setter |
| EventPublisher 为 null | 低 | 低 | 添加空值检查，降级到日志 |
| 事件发布性能问题 | 极低 | 低 | Spring 事件默认异步，快速失败 |
| 测试环境事件监听器冲突 | 低 | 低 | 使用 Mock EventPublisher |
| Task 状态在 Context 中不是最新 | 极低 | 低 | 使用引用传递，自动反映最新状态 |

---

## 九、验收标准

### 9.1 功能验收

- [ ] Stage 开始时发布 TaskStageStartedEvent
- [ ] Stage 成功完成时发布 TaskStageCompletedEvent
- [ ] Stage 失败时发布 TaskStageFailedEvent
- [ ] 事件包含正确的 taskId, stageName, timestamp
- [ ] 事件发布失败不影响 Stage 执行

### 9.2 测试验收

- [ ] 单元测试覆盖率 > 90%
- [ ] 集成测试验证事件流完整性
- [ ] 所有现有测试通过

### 9.3 文档验收

- [ ] 架构设计文档更新
- [ ] 术语表补充 Stage 事件说明
- [ ] Code Review 通过

---

## 十、待确认问题

### ✅ 需要用户确认的设计点

1. **事件发布方式**: 通过 TaskRuntimeContext 传递 EventPublisher（方案 B） ← **请确认**
2. **TaskRuntimeContext 增强**: 添加 `task` 和 `eventPublisher` 两个字段 ← **请确认**
3. **sequenceId 管理**: Stage 事件不维护独立 sequenceId ← **请确认**
4. **异常处理**: 事件发布失败不中断 Stage 执行 ← **请确认**

### ✅ 已澄清的问题

1. ✅ **TaskRuntimeContext 中是否已有 `getTask()` 方法？**
   - **答案**: 没有，需要添加 `task` 字段和 `getTask()/setTask()` 方法
   
2. ✅ **是否需要在事件中包含 sequenceId？**
   - **答案**: 不需要，Stage 事件是 Task 事件的细粒度补充，不需要独立的幂等性控制
   - **理由**: sequenceId 仅在 Task 级别事件中维护

---

## 十一、总结

**推荐方案**: ✅ 方案 B - 通过 TaskRuntimeContext 传递 EventPublisher

**关键优势**:
1. ✅ 最小侵入性（无需修改构造器）
2. ✅ 保持接口稳定性
3. ✅ 易于测试
4. ✅ 符合现有架构风格
5. ✅ 完全向后兼容

**实施复杂度**: ⭐⭐ (简单)  
**风险等级**: ⭐ (低)  
**预期收益**: ⭐⭐⭐⭐⭐ (高 - 显著提升可观测性)

---

**请确认以上设计方案，确认后我将开始实施。**

