# DDD状态管理与策略模式：从理论到实践

**作者**: GitHub Copilot  
**日期**: 2025-11-19  
**项目**: Executor Demo - 多租户蓝绿发布配置下发系统

---

## 目录

1. [引言：状态管理的挑战](#引言状态管理的挑战)
2. [DDD视角下的状态管理](#ddd视角下的状态管理)
3. [策略模式：什么是"策略路由"](#策略模式什么是策略路由)
4. [三层职责划分](#三层职责划分)
5. [双重防御机制](#双重防御机制)
6. [策略模式的真正价值](#策略模式的真正价值)
7. [常见误区与澄清](#常见误区与澄清)
8. [最佳实践建议](#最佳实践建议)
9. [总结](#总结)

---

## 引言：状态管理的挑战

在领域驱动设计（DDD）中，状态管理是一个核心话题。一个典型的业务实体（如任务、订单、工单）往往需要在多个状态之间转换，如何优雅地管理这些状态转换，同时保持代码的可维护性和可扩展性，是每个架构师和开发者都需要面对的挑战。

### 传统做法的问题

```java
// ❌ 传统做法：聚合承担了所有状态转换逻辑
public class TaskAggregate {
    private TaskStatus status;
    
    public void changeState(TaskStatus newStatus) {
        // 巨大的switch语句
        switch (newStatus) {
            case RUNNING:
                if (status == PENDING) {
                    this.status = RUNNING;
                    publishEvent(new TaskStartedEvent(...));
                }
                break;
            case PAUSED:
                if (status == RUNNING) {
                    this.status = PAUSED;
                    publishEvent(new TaskPausedEvent(...));
                }
                break;
            case COMPLETED:
                if (status == RUNNING && allStagesCompleted()) {
                    this.status = COMPLETED;
                    publishEvent(new TaskCompletedEvent(...));
                }
                break;
            // ... 还有8种状态转换
        }
    }
}
```

**问题**：
- ❌ 违反单一职责原则（SRP）：聚合承担了路由决策的职责
- ❌ 难以扩展：添加新的状态转换需要修改聚合
- ❌ 测试困难：需要测试所有的switch分支
- ❌ 代码可读性差：业务逻辑被淹没在状态转换判断中

---

## DDD视角下的状态管理

### 核心原则

在DDD中，状态管理应该遵循以下原则：

1. **聚合是不变式的守护者**：聚合必须保护自己的业务规则
2. **聚合暴露业务方法而非状态**：外部通过调用业务方法来触发状态变化
3. **状态转换逻辑应该解耦**：转换规则的变化不应该影响聚合本身

### 理想的聚合设计

```java
// ✅ DDD推荐：聚合只暴露业务方法
public class TaskAggregate {
    private TaskStatus status;
    
    // 业务方法1：启动任务
    public void start() {
        // 保护不变式
        if (status != TaskStatus.PENDING) {
            throw new IllegalStateException("只有PENDING状态可以启动");
        }
        
        // 修改状态
        this.status = TaskStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
        
        // 产生领域事件
        addDomainEvent(new TaskStartedEvent(...));
    }
    
    // 业务方法2：暂停任务
    public void pause() {
        if (status != TaskStatus.RUNNING) {
            throw new IllegalStateException("只有RUNNING状态可以暂停");
        }
        this.status = TaskStatus.PAUSED;
        addDomainEvent(new TaskPausedEvent(...));
    }
    
    // 业务方法3：恢复任务
    public void resume() {
        if (status != TaskStatus.PAUSED) {
            throw new IllegalStateException("只有PAUSED状态可以恢复");
        }
        this.status = TaskStatus.RUNNING;
        addDomainEvent(new TaskResumedEvent(...));
    }
    
    // ... 其他业务方法
}
```

**优势**：
- ✅ 聚合职责单一：只关心业务逻辑
- ✅ 业务意图清晰：方法名表达业务含义
- ✅ 不变式保护：每个方法都检查自己的前置条件
- ✅ 易于理解：没有复杂的状态转换路由逻辑

**但是问题来了**：外部如何知道调用哪个业务方法？

---

## 策略模式：什么是"策略路由"

### 核心概念

**策略（Strategy）**：每一种状态转换的具体实现  
**路由（Routing）**：根据源状态和目标状态，找到对应的策略并执行  
**策略路由器（Router）**：维护策略注册表，执行路由逻辑

### 策略模式的结构

```
请求：将任务从PENDING状态改为RUNNING

┌────────────────────────────────────────────────────────────────┐
│  TaskStateManager（策略路由器）                                  │
│                                                                 │
│  1️⃣ 获取当前状态                                                 │
│     oldStatus = aggregate.getStatus();  // PENDING             │
│                                                                 │
│  2️⃣ 构建路由键                                                   │
│     key = new StateTransitionKey(PENDING, RUNNING);            │
│                           路由键: "PENDING → RUNNING"           │
│                                                                 │
│  3️⃣ 查找策略（路由表查询）                                        │
│     ┌─────────────────────────────────────────────┐           │
│     │ 策略路由表 (strategies Map)                  │           │
│     ├─────────────────────────────────────────────┤           │
│     │ PENDING → RUNNING   → StartTransitionStrategy│ ← 命中！ │
│     │ RUNNING → PAUSED    → PauseTransitionStrategy│           │
│     │ PAUSED  → RUNNING   → ResumeTransitionStrategy          │
│     │ RUNNING → COMPLETED → CompleteTransitionStrategy         │
│     │ ...                                          │           │
│     └─────────────────────────────────────────────┘           │
│                                                                 │
│  4️⃣ 执行策略                                                     │
│     strategy = StartTransitionStrategy                         │
│     strategy.canTransition(agg) → true                         │
│     strategy.execute(agg)                                      │
│         ↓                                                      │
│         调用 agg.start()  // 委托给聚合                         │
│                                                                 │
└────────────────────────────────────────────────────────────────┘
```

### 代码实现

#### 1. 策略接口

```java
/**
 * 状态转换策略接口
 */
public interface StateTransitionStrategy {
    
    /**
     * 判断是否可以执行此状态转换
     */
    boolean canTransition(TaskAggregate agg, 
                         TaskRuntimeContext context, 
                         TaskStatus targetStatus);
    
    /**
     * 执行状态转换（委托给聚合的业务方法）
     */
    void execute(TaskAggregate agg, 
                TaskRuntimeContext context, 
                Object additionalData);
    
    /**
     * 获取此策略支持的源状态
     */
    TaskStatus getFromStatus();
    
    /**
     * 获取此策略支持的目标状态
     */
    TaskStatus getToStatus();
}
```

#### 2. 具体策略实现

```java
/**
 * PENDING -> RUNNING 转换策略（启动任务）
 */
public class StartTransitionStrategy implements StateTransitionStrategy {

    @Override
    public boolean canTransition(TaskAggregate agg, 
                                TaskRuntimeContext context, 
                                TaskStatus targetStatus) {
        return agg.getStatus() == TaskStatus.PENDING;
    }

    @Override
    public void execute(TaskAggregate agg, 
                       TaskRuntimeContext context, 
                       Object additionalData) {
        agg.start();  // 委托给聚合的业务方法
    }

    @Override
    public TaskStatus getFromStatus() {
        return TaskStatus.PENDING;
    }

    @Override
    public TaskStatus getToStatus() {
        return TaskStatus.RUNNING;
    }
}
```

#### 3. 策略路由器

```java
/**
 * 任务状态转换策略路由器
 */
public class TaskStateTransitionRouter {
    
    // 策略注册表（路由表）
    private final Map<StateTransitionKey, StateTransitionStrategy> strategies;
    
    public TaskStateTransitionRouter() {
        this.strategies = new HashMap<>();
        initializeStrategies();
    }
    
    /**
     * 执行状态转换（核心方法）
     */
    public void transition(TaskAggregate aggregate, 
                          TaskStatus targetStatus,
                          TaskRuntimeContext context) {
        
        TaskStatus currentStatus = aggregate.getStatus();
        
        // 1. 路由：查找策略
        StateTransitionKey key = new StateTransitionKey(currentStatus, targetStatus);
        StateTransitionStrategy strategy = strategies.get(key);
        
        if (strategy == null) {
            throw new IllegalStateException(
                String.format("没有找到状态转换策略: %s -> %s", 
                    currentStatus, targetStatus)
            );
        }
        
        // 2. 前置检查
        if (!strategy.canTransition(aggregate, context, targetStatus)) {
            throw new IllegalStateException(
                String.format("不允许的状态转换: %s -> %s", 
                    currentStatus, targetStatus)
            );
        }
        
        // 3. 执行策略（委托给聚合）
        strategy.execute(aggregate, context, null);
    }
    
    /**
     * 注册策略
     */
    public void registerStrategy(StateTransitionStrategy strategy) {
        StateTransitionKey key = new StateTransitionKey(
            strategy.getFromStatus(), 
            strategy.getToStatus()
        );
        strategies.put(key, strategy);
    }
    
    /**
     * 初始化所有策略
     */
    private void initializeStrategies() {
        registerStrategy(new StartTransitionStrategy());
        registerStrategy(new PauseTransitionStrategy());
        registerStrategy(new ResumeTransitionStrategy());
        registerStrategy(new CompleteTransitionStrategy());
        registerStrategy(new FailTransitionStrategy());
        registerStrategy(new RetryTransitionStrategy());
        registerStrategy(new RollbackTransitionStrategy());
        registerStrategy(new CancelTransitionStrategy());
        // ... 其他策略
    }
}
```

#### 4. 路由键（StateTransitionKey）

```java
/**
 * 状态转换键（用于策略映射）
 */
public class StateTransitionKey {
    private final TaskStatus fromStatus;
    private final TaskStatus toStatus;
    
    public StateTransitionKey(TaskStatus fromStatus, TaskStatus toStatus) {
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StateTransitionKey that = (StateTransitionKey) o;
        return fromStatus == that.fromStatus && toStatus == that.toStatus;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(fromStatus, toStatus);
    }
    
    @Override
    public String toString() {
        return fromStatus + " -> " + toStatus;
    }
}
```

---

## 三层职责划分

### 清晰的依赖方向

```
┌──────────────────────┐
│ TaskStateManager     │ ──────┐
│   (策略路由器)        │       │ 依赖
└──────────────────────┘       │
                               ▼
              ┌──────────────────────────────┐
              │ StateTransitionStrategy      │
              │      (策略接口)               │
              └──────────────────────────────┘
                               │ 依赖
                               ▼
              ┌──────────────────────────────┐
              │    TaskAggregate             │
              │     (聚合根)                  │
              └──────────────────────────────┘

依赖方向：Infrastructure → Domain
聚合根在最底层，不依赖任何人！
```

### 第1层：TaskStateManager（路由器）

**职责**：路由决策 - "找到负责这个状态转换的策略"

```java
public class TaskStateManager {
    // 职责1：维护路由表
    private Map<StateTransitionKey, StateTransitionStrategy> strategies;
    
    // 职责2：路由决策
    public void updateState(TaskAggregate agg, TaskStatus newStatus, ...) {
        TaskStatus oldStatus = agg.getStatus();
        
        // 根据 oldStatus → newStatus 找到对应的策略
        StateTransitionKey key = new StateTransitionKey(oldStatus, newStatus);
        StateTransitionStrategy strategy = strategies.get(key);
        
        // 委托给策略
        strategy.execute(agg, context, null);
    }
}
```

**类比**：交通指挥中心 - "去机场的车走这条路"

---

### 第2层：StateTransitionStrategy（策略）

**职责**：转换逻辑 - "如何执行这个状态转换"

```java
public class StartTransitionStrategy implements StateTransitionStrategy {
    
    // 职责1：前置条件检查
    @Override
    public boolean canTransition(TaskAggregate agg, ...) {
        return agg.getStatus() == TaskStatus.PENDING;
    }
    
    // 职责2：执行转换（调用聚合方法）
    @Override
    public void execute(TaskAggregate agg, ...) {
        agg.start();  // 调用聚合的业务方法
    }
}
```

**类比**：司机 - "我负责把你送到机场"

**关键点**：策略知道聚合，但聚合不知道策略

---

### 第3层：TaskAggregate（聚合根）

**职责**：业务逻辑和不变式保护

```java
public class TaskAggregate {
    
    // 业务方法：启动任务
    public void start() {
        // 职责1：保护不变式
        if (status != TaskStatus.PENDING) {
            throw new IllegalStateException("只有PENDING状态可以启动");
        }
        
        // 职责2：修改状态
        this.status = TaskStatus.RUNNING;
        this.timeRange = timeRange.start();
        
        // 职责3：产生领域事件
        addDomainEvent(new TaskStartedEvent(...));
    }
}
```

**类比**：乘客 - "我要去机场（业务需求），但我有自己的规矩（不变式）"

**关键点**：聚合不知道谁调用了它，只关心业务逻辑

---

## 双重防御机制

### 为什么需要两层防御？

一个常见的疑问是：策略的`canTransition`和聚合的业务方法都在检查状态，这不是重复吗？

**答案**：这是**双重防御机制**，各有用途！

### 场景1：策略和聚合都正确（正常情况）✅

```java
// 请求：将状态从 PENDING 改为 RUNNING

// 第1层防御：策略检查（快速失败）
StartTransitionStrategy.canTransition(agg) {
    return agg.getStatus() == PENDING;  // ✅ 通过
}

// 执行策略
StartTransitionStrategy.execute(agg) {
    agg.start();  // 调用聚合
}

// 第2层防御：聚合检查（最终保障）
TaskAggregate.start() {
    if (status != PENDING) {  // ✅ 通过
        throw ...
    }
    status = RUNNING;  // ✅ 成功
}
```

**结果**：成功，两层检查都通过

---

### 场景2：策略写错，聚合正确（聚合兜底）✅

```java
// ❌ 假设策略写错了
BadStrategy.canTransition(agg) {
    return true;  // ⚠️ 错误！没有检查状态
}

BadStrategy.execute(agg) {
    agg.start();  // 调用聚合
}

// ✅ 聚合检查（最终保障）
TaskAggregate.start() {
    if (status != PENDING) {  // ❌ 当前是RUNNING
        throw new IllegalStateException(...);  // ✅ 抛异常！
    }
}
```

**结果**：失败，但被聚合捕获，抛出明确的业务异常

**关键**：聚合是最终的真相守护者！

---

### 场景3：策略正确，但被绕过（直接调用聚合）

```java
// ⚠️ 有人绕过策略，直接调用聚合
TaskAggregate agg = repository.findById(taskId);
agg.start();  // 直接调用，没有经过策略

// ✅ 聚合检查（最终保障）
TaskAggregate.start() {
    if (status != PENDING) {  // 检查不变式
        throw new IllegalStateException(...);  // 抛异常
    }
}
```

**结果**：即使绕过策略，聚合也会保护自己

---

### 两层防御的职责

| 层次 | 职责 | 用途 |
|------|------|------|
| **策略层（canTransition）** | 前置检查 + 快速失败 | • 避免不必要的聚合方法调用<br>• 查询式检查（非破坏性）<br>• 检查运行时上下文 |
| **聚合层（业务方法）** | 不变式保护 + 最终防线 | • 保护领域不变式<br>• 即使策略有bug也能保证数据一致性<br>• 聚合是唯一的真相来源 |

---

### 策略层（canTransition）的价值

#### 价值1：查询式API（非破坏性检查）

```java
// ✅ 查询式：不修改状态，只判断是否可以
if (strategy.canTransition(agg, context, RUNNING)) {
    log.info("任务可以启动");
    
    // 在其他条件满足后再执行
    if (someOtherCondition) {
        strategy.execute(agg, context);  // 真正执行
    }
}

// ❌ 命令式：调用就会修改状态或抛异常
try {
    agg.start();  // 要么成功要么异常，无法"试探"
} catch (IllegalStateException e) {
    // 已经抛异常了，无法提前知道
}
```

#### 价值2：检查额外的运行时条件

```java
// PauseTransitionStrategy.java
@Override
public boolean canTransition(TaskAggregate agg, TaskRuntimeContext context, ...) {
    // ✅ 不仅检查聚合状态，还检查运行时上下文
    return agg.getStatus() == TaskStatus.RUNNING && 
           context != null && 
           context.isPauseRequested();  // 额外条件
    //                    ^^^^^^^^^^^^^^^
    // 这个条件聚合内部不知道，需要外部检查
}
```

**关键区别**：
- 聚合只能检查**自己的状态**
- 策略可以检查**聚合状态 + 运行时上下文**

#### 价值3：避免异常作为控制流

```java
// ✅ 好的做法：用返回值表示是否允许
if (!strategy.canTransition(agg, context, RUNNING)) {
    log.debug("任务状态不允许启动: {}", agg.getStatus());
    return;  // 正常返回
}
strategy.execute(agg, context);

// ❌ 不好的做法：用异常作为控制流
try {
    agg.start();
} catch (IllegalStateException e) {
    log.debug("任务状态不允许启动", e);  // 用异常表示正常的业务逻辑
}
```

---

### 聚合层的价值：不变式保护

```java
public class TaskAggregate {
    
    public void start() {
        // ✅ 最终防线：即使策略错了，聚合也会保护自己
        if (status != TaskStatus.PENDING) {
            throw new IllegalStateException(
                String.format("只有PENDING状态可以启动，当前状态: %s", status)
            );
        }
        
        // ✅ 聚合是唯一可以修改状态的地方
        this.status = TaskStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
        
        // ✅ 产生领域事件
        addDomainEvent(new TaskStartedEvent(...));
    }
}
```

**原则**：
- 聚合是不变式的守护者
- 即使策略有bug，聚合也要保护自己
- 聚合是唯一的真相来源

---

## 策略模式的真正价值

### 常见误区：性能优化？

很多文章会说策略的`canTransition`是为了"性能优化"，但在大多数场景下，**这是不准确的**！

#### 性能分析

```java
// 场景A：策略检查（当前方式）
public void updateState(taskId, RUNNING) {
    TaskAggregate agg = aggregates.get(taskId);          // 1次内存访问
    StateTransitionStrategy strategy = strategies.get(key);  // 1次Map查找
    
    if (!strategy.canTransition(agg, context, RUNNING)) {
        return agg.getStatus() == PENDING;               // 1次字段访问
    }
}

// 场景B：直接调用聚合
public void changeState(taskId, RUNNING) {
    TaskAggregate agg = repository.findById(taskId);     // 1次内存访问
    
    try {
        agg.start();
        // if (status != PENDING) throw ...              // 1次字段访问
    } catch (IllegalStateException e) {
        // 处理异常
    }
}
```

**性能对比**：

| 操作 | 策略检查 | 直接调用 | 差异 |
|------|---------|---------|------|
| 内存访问 | 2次 | 1次 | +1次 |
| Map查找 | 1次 | 0次 | +1次 |
| 字段访问 | 1次 | 1次 | 相同 |
| 异常创建 | 0次 | 1次（失败时） | -1次 |

**结论**：
- ✅ 如果转换**失败**：策略避免了异常创建（有一定优势）
- ⚠️ 如果转换**成功**：策略多了Map查找和策略调用（略慢）
- ❌ 整体性能差异：**微不足道**（纳秒级别）

#### 什么场景下性能优化才有价值？

**只有当状态检查之后有昂贵操作时**：

```java
public class ExpensiveTransitionStrategy {
    @Override
    public boolean canTransition(TaskAggregate agg, ...) {
        // ✅ 便宜的检查（内存访问）
        if (agg.getStatus() != RUNNING) {
            return false;  // 快速失败，不执行昂贵操作
        }
        return true;
    }
    
    @Override
    public void execute(TaskAggregate agg, ...) {
        // ⚠️ 昂贵的操作
        ExpensiveResult result = expensiveService.doHeavyWork();  // 网络调用
        agg.applyResult(result);  // 数据库写入
        agg.complete();
    }
}
```

**但在大多数场景**：

```java
// 聚合的业务方法通常都是轻量操作
public void start() {
    if (status != PENDING) throw ...;  // ✅ 检查在前
    this.status = RUNNING;             // ✅ 内存操作
    addDomainEvent(...);               // ✅ 内存操作
}
```

**所以性能优势通常可以忽略不计！**

---

### 真正的价值（按重要性排序）

#### 价值1：开闭原则（OCP）⭐⭐⭐⭐⭐

```java
// 添加新的状态转换，不需要修改聚合或状态管理器
public class NewTransitionStrategy implements StateTransitionStrategy {
    @Override
    public boolean canTransition(TaskAggregate agg, ...) {
        return agg.getStatus() == TaskStatus.SOME_STATE;
    }
    
    @Override
    public void execute(TaskAggregate agg, ...) {
        agg.doSomething();
    }
    
    @Override
    public TaskStatus getFromStatus() { return SOME_STATE; }
    
    @Override
    public TaskStatus getToStatus() { return OTHER_STATE; }
}

// 注册到路由表
stateManager.registerStrategy(new NewTransitionStrategy());

// ✅ 无需修改任何现有代码
```

**对比传统方式**：

```java
// ❌ 需要修改聚合的changeState方法
public void changeState(TaskStatus newStatus) {
    switch (newStatus) {
        // ... 已有的case
        case OTHER_STATE:  // ⚠️ 新增case，修改聚合
            if (status == SOME_STATE) {
                doSomething();
            }
            break;
    }
}
```

---

#### 价值2：单一职责（SRP）⭐⭐⭐⭐⭐

```java
// 聚合：只关心业务逻辑
public class TaskAggregate {
    public void start() {
        // 纯业务逻辑，不关心路由
    }
}

// 策略：只关心状态转换逻辑
public class StartTransitionStrategy {
    public void execute(TaskAggregate agg) {
        agg.start();  // 只关心调用哪个方法
    }
}

// 路由器：只关心策略选择
public class TaskStateManager {
    public void updateState(...) {
        strategy = findStrategy(key);  // 只关心路由
        strategy.execute(agg);
    }
}
```

**职责清晰**：
- 聚合 = 业务逻辑
- 策略 = 转换逻辑
- 路由器 = 策略选择

---

#### 价值3：可测试性⭐⭐⭐⭐

```java
// 单独测试每个策略
@Test
public void testStartTransition() {
    // Arrange
    TaskAggregate task = createTask(PENDING);
    StartTransitionStrategy strategy = new StartTransitionStrategy();
    TaskRuntimeContext context = createContext();
    
    // Act & Assert
    assertTrue(strategy.canTransition(task, context, RUNNING));
    strategy.execute(task, context, null);
    
    assertEquals(RUNNING, task.getStatus());
    assertNotNull(task.getStartedAt());
}

// 单独测试聚合
@Test
public void testAggregateStart() {
    TaskAggregate task = createTask(PENDING);
    
    task.start();
    
    assertEquals(RUNNING, task.getStatus());
}

// 测试路由器
@Test
public void testRouter() {
    TaskAggregate task = createTask(PENDING);
    TaskStateManager router = new TaskStateManager();
    
    router.transition(task, RUNNING, context);
    
    assertEquals(RUNNING, task.getStatus());
}
```

**测试粒度细**：每个组件都可以独立测试

---

#### 价值4：查询式API⭐⭐⭐

```java
// 批量操作前检查
List<TaskAggregate> tasks = getTasks();
List<TaskAggregate> startableTasks = tasks.stream()
    .filter(task -> startStrategy.canTransition(task, context, RUNNING))
    .collect(toList());

log.info("可启动的任务: {} / {}", startableTasks.size(), tasks.size());

// 再批量执行
startableTasks.forEach(task -> startStrategy.execute(task, context));
```

**如果没有canTransition**：

```java
// ❌ 只能通过异常来判断
List<TaskAggregate> startableTasks = tasks.stream()
    .filter(task -> {
        try {
            task.start();  // ⚠️ 实际修改了状态！
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    })
    .collect(toList());
```

---

#### 价值5：运行时条件检查⭐⭐

```java
// 策略可以访问运行时上下文
public boolean canTransition(TaskAggregate agg, 
                            TaskRuntimeContext ctx, ...) {
    // ✅ 组合多个条件
    return agg.getStatus() == RUNNING &&           // 条件1：聚合状态
           !ctx.isPauseRequested() &&              // 条件2：运行时标志
           !ctx.isCancelRequested() &&             // 条件3：运行时标志
           agg.getStageProgress().isCompleted();   // 条件4：进度
}

// ❌ 聚合无法访问运行时上下文
public void complete() {
    if (status != RUNNING) throw ...;  // 只能检查自己
    // ❌ 无法检查 ctx.isPauseRequested()
    this.status = COMPLETED;
}
```

---

### 价值总结

| 价值 | 重要性 | 说明 |
|------|--------|------|
| 开闭原则（OCP） | ⭐⭐⭐⭐⭐ | 扩展性，添加新转换无需修改现有代码 |
| 单一职责（SRP） | ⭐⭐⭐⭐⭐ | 聚合、策略、路由器职责清晰 |
| 可测试性 | ⭐⭐⭐⭐ | 每个策略独立测试 |
| 查询式API | ⭐⭐⭐ | 非破坏性检查（部分策略有用） |
| 运行时条件检查 | ⭐⭐ | 策略可以检查运行时上下文 |
| ~~性能优化~~ | ❌ | 在大多数场景几乎没有价值 |

---

## 常见误区与澄清

### 误区1：策略模式是为了性能优化

**真相**：性能优化只是副产品，且在大多数场景下微不足道（纳秒级）。

**真正价值**：
- ✅ 架构的清晰性（职责分离）
- ✅ 代码的可扩展性（OCP）
- ✅ 代码的可测试性

---

### 误区2：聚合根调用策略

**错误理解**：
```java
// ❌ 聚合根调用策略
public class TaskAggregate {
    private StateTransitionStrategy strategy;
    
    public void changeState(TaskStatus newStatus) {
        strategy.execute(this, context);  // ❌ 错误
    }
}
```

**正确理解**：
```java
// ✅ 策略调用聚合根
public class StartTransitionStrategy {
    public void execute(TaskAggregate agg) {
        agg.start();  // ✅ 正确
    }
}
```

**关键点**：**策略知道聚合，聚合不知道策略**

---

### 误区3：策略的canTransition和聚合的检查是重复的

**真相**：这是**双重防御机制**，各有用途！

- **策略层**：前置检查、快速失败、查询式API、运行时条件
- **聚合层**：不变式保护、最终防线、数据一致性保障

**原则**：即使策略有bug，聚合也要保护自己！

---

### 误区4：编译期无法检查策略和聚合的规则一致性

**真相**：确实无法在编译期检查，但可以通过以下方式缓解：

#### 方案A：聚合暴露规则查询方法（推荐）

```java
// TaskAggregate.java
public boolean canStart() {
    return status == TaskStatus.PENDING;
}

public void start() {
    if (!canStart()) {  // 使用统一规则
        throw new IllegalStateException(...);
    }
    this.status = RUNNING;
}

// StartTransitionStrategy.java
public boolean canTransition(TaskAggregate agg, ...) {
    return agg.canStart();  // 调用聚合的规则方法
}
```

**优势**：
- ✅ 规则在聚合中统一定义
- ✅ 策略和聚合使用相同的规则
- ✅ 减少重复代码

#### 方案B：状态机验证

```java
// TaskAggregate.java
private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED_TRANSITIONS = Map.of(
    PENDING,    Set.of(RUNNING, CANCELLED),
    RUNNING,    Set.of(PAUSED, COMPLETED, FAILED),
    // ...
);

public boolean canTransitionTo(TaskStatus targetStatus) {
    Set<TaskStatus> allowed = ALLOWED_TRANSITIONS.get(this.status);
    return allowed != null && allowed.contains(targetStatus);
}
```

#### 方案C：测试覆盖

```java
@Test
public void testStrategyAndAggregateConsistency() {
    TaskAggregate task = createTask(RUNNING);  // 非PENDING状态
    StartTransitionStrategy strategy = new StartTransitionStrategy();
    
    // 策略应该返回false
    assertFalse(strategy.canTransition(task, context, RUNNING));
    
    // 聚合应该抛异常
    assertThrows(IllegalStateException.class, () -> {
        task.start();
    });
}
```

---

## 最佳实践建议

### 1. 聚合设计原则

```java
public class TaskAggregate {
    
    // ✅ DO：暴露业务方法
    public void start() { /* ... */ }
    public void pause() { /* ... */ }
    public void resume() { /* ... */ }
    
    // ✅ DO：保护不变式
    public void start() {
        if (status != PENDING) {
            throw new IllegalStateException(...);
        }
        // ...
    }
    
    // ✅ DO：产生领域事件
    public void start() {
        // ...
        addDomainEvent(new TaskStartedEvent(...));
    }
    
    // ✅ DO：暴露规则查询方法（可选）
    public boolean canStart() {
        return status == TaskStatus.PENDING;
    }
    
    // ❌ DON'T：暴露通用的changeState方法
    public void changeState(TaskStatus newStatus) { /* ... */ }
    
    // ❌ DON'T：聚合依赖策略
    private StateTransitionStrategy strategy;
}
```

---

### 2. 策略设计原则

```java
public class StartTransitionStrategy implements StateTransitionStrategy {
    
    // ✅ DO：检查前置条件
    @Override
    public boolean canTransition(TaskAggregate agg, ...) {
        return agg.getStatus() == TaskStatus.PENDING;
    }
    
    // ✅ DO：委托给聚合的业务方法
    @Override
    public void execute(TaskAggregate agg, ...) {
        agg.start();  // 调用业务方法，不直接修改状态
    }
    
    // ✅ DO：复用聚合的规则方法
    @Override
    public boolean canTransition(TaskAggregate agg, ...) {
        return agg.canStart();  // 调用聚合暴露的规则方法
    }
    
    // ❌ DON'T：在策略中直接修改聚合状态
    @Override
    public void execute(TaskAggregate agg, ...) {
        agg.setStatus(RUNNING);  // ❌ 错误！绕过了不变式保护
    }
    
    // ❌ DON'T：在策略中重复聚合的业务逻辑
    @Override
    public void execute(TaskAggregate agg, ...) {
        // ❌ 错误！业务逻辑应该在聚合中
        agg.setStatus(RUNNING);
        agg.setStartedAt(LocalDateTime.now());
        eventPublisher.publish(new TaskStartedEvent(...));
    }
}
```

---

### 3. 路由器设计原则

```java
public class TaskStateManager {
    
    // ✅ DO：只做路由决策
    public void transition(TaskAggregate agg, TaskStatus targetStatus, ...) {
        StateTransitionStrategy strategy = findStrategy(currentStatus, targetStatus);
        if (!strategy.canTransition(agg, context, targetStatus)) {
            throw new IllegalStateException(...);
        }
        strategy.execute(agg, context, null);
    }
    
    // ❌ DON'T：缓存聚合（应该由Repository管理）
    private Map<String, TaskAggregate> aggregates;
    
    // ❌ DON'T：创建事件（应该由聚合产生）
    private TaskStatusEvent createEvent(...) { /* ... */ }
    
    // ❌ DON'T：发布事件（应该由DomainService发布）
    public void publishEvent(...) { /* ... */ }
}
```

---

### 4. 完整的调用流程

```java
// DomainService层（应用层）
public class TaskDomainService {
    private final TaskRepository taskRepository;
    private final TaskStateTransitionRouter stateRouter;
    private final DomainEventPublisher eventPublisher;
    
    @Transactional
    public void startTask(String taskId) {
        // 1. 从Repository获取聚合
        TaskAggregate task = taskRepository.findById(taskId)
            .orElseThrow(() -> new TaskNotFoundException(taskId));
        
        TaskRuntimeContext context = runtimeRepository.getContext(taskId)
            .orElseThrow();
        
        // 2. 通过路由器执行状态转换
        stateRouter.transition(task, TaskStatus.RUNNING, context);
        
        // 3. 保存聚合
        taskRepository.save(task);
        
        // 4. 发布聚合产生的事件
        eventPublisher.publishAll(task.getDomainEvents());
        task.clearDomainEvents();
    }
}
```

**职责清晰**：
1. **DomainService**：协调（获取聚合、调用路由器、保存聚合、发布事件）
2. **Router**：路由（选择策略、执行策略）
3. **Strategy**：转换逻辑（调用聚合方法）
4. **Aggregate**：业务逻辑（保护不变式、修改状态、产生事件）

---

### 5. 测试策略

```java
// 测试1：测试聚合的业务逻辑
@Test
public void testAggregateStart() {
    TaskAggregate task = createTask(PENDING);
    
    task.start();
    
    assertEquals(RUNNING, task.getStatus());
    assertNotNull(task.getStartedAt());
    assertEquals(1, task.getDomainEvents().size());
}

// 测试2：测试聚合的不变式保护
@Test
public void testAggregateStart_shouldFailIfNotPending() {
    TaskAggregate task = createTask(RUNNING);
    
    assertThrows(IllegalStateException.class, () -> {
        task.start();
    });
}

// 测试3：测试策略的canTransition
@Test
public void testStrategyCanTransition() {
    TaskAggregate task = createTask(PENDING);
    StartTransitionStrategy strategy = new StartTransitionStrategy();
    
    assertTrue(strategy.canTransition(task, context, RUNNING));
}

// 测试4：测试策略的execute
@Test
public void testStrategyExecute() {
    TaskAggregate task = createTask(PENDING);
    StartTransitionStrategy strategy = new StartTransitionStrategy();
    
    strategy.execute(task, context, null);
    
    assertEquals(RUNNING, task.getStatus());
}

// 测试5：测试路由器
@Test
public void testRouter() {
    TaskAggregate task = createTask(PENDING);
    TaskStateTransitionRouter router = createRouter();
    
    router.transition(task, RUNNING, context);
    
    assertEquals(RUNNING, task.getStatus());
}

// 测试6：测试策略和聚合的一致性
@Test
public void testStrategyAggregateConsistency() {
    TaskAggregate task = createTask(RUNNING);  // 非PENDING状态
    StartTransitionStrategy strategy = new StartTransitionStrategy();
    
    // 策略应该返回false
    assertFalse(strategy.canTransition(task, context, RUNNING));
    
    // 聚合应该抛异常
    assertThrows(IllegalStateException.class, () -> {
        task.start();
    });
}
```

---

## 总结

### 核心要点

1. **DDD状态管理的原则**
   - 聚合是不变式的守护者
   - 聚合暴露业务方法而非状态
   - 状态转换逻辑应该解耦

2. **策略模式的职责划分**
   - 路由器：策略选择
   - 策略：转换逻辑
   - 聚合：业务逻辑

3. **双重防御机制**
   - 策略层：前置检查、快速失败、查询式API
   - 聚合层：不变式保护、最终防线

4. **策略模式的真正价值**
   - ✅ 开闭原则（OCP）
   - ✅ 单一职责（SRP）
   - ✅ 可测试性
   - ⚠️ 查询式API（部分场景）
   - ❌ 性能优化（微不足道）

5. **依赖方向**
   - Infrastructure → Domain
   - 策略知道聚合，聚合不知道策略
   - 聚合在最底层，不依赖任何人

---

### 关键理解

**策略模式不是为了性能**，而是为了：
- **架构清晰**：职责分离，依赖方向正确
- **易于扩展**：符合开闭原则
- **易于测试**：每个组件独立测试
- **代码质量**：减少复杂度，提高可维护性

**双重防御不是重复**，而是：
- 策略提供便利性（查询式、运行时条件）
- 聚合提供安全性（不变式保护、最终防线）

**聚合是核心**，即使：
- 策略有bug，聚合也会保护自己
- 绕过策略，聚合也会检查不变式
- 编译期无法检查，运行时必须保证

---

### 实践建议

1. **优先考虑架构质量，而非性能优化**
   - 除非有明确的性能瓶颈
   - 否则清晰的架构比微小的性能提升更重要

2. **聚合暴露规则查询方法**
   - 减少策略和聚合的规则重复
   - 提高代码的一致性

3. **完善测试覆盖**
   - 测试聚合的业务逻辑
   - 测试策略的转换逻辑
   - 测试策略和聚合的一致性

4. **遵循依赖方向**
   - Infrastructure依赖Domain
   - Domain不依赖Infrastructure
   - 聚合不知道策略的存在

5. **保持职责清晰**
   - 路由器只做路由
   - 策略只做转换
   - 聚合只做业务

---

## 参考资料

### 书籍推荐

- 《领域驱动设计》（Eric Evans）
- 《实现领域驱动设计》（Vaughn Vernon）
- 《设计模式：可复用面向对象软件的基础》（GoF）

### 模式参考

- Strategy Pattern（策略模式）
- State Pattern（状态模式）
- Template Method Pattern（模板方法模式）

### DDD战术模式

- Aggregate（聚合）
- Value Object（值对象）
- Domain Event（领域事件）
- Repository（仓储）

---

**感谢阅读！希望这篇文章能帮助你更好地理解DDD中的状态管理和策略模式的应用。**

如有疑问或建议，欢迎讨论交流。
