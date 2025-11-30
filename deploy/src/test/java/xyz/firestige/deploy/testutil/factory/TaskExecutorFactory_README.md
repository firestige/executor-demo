# TaskExecutorFactory 使用指南

## 概述

`TaskExecutorFactory` 是一个**测试专用**的简化工厂，用于快速创建 `TaskExecutor` 实例。

### 与 `TaskWorkerFactory` 的区别

| 特性 | TaskWorkerFactory (生产) | TaskExecutorFactory (测试) |
|------|-------------------------|---------------------------|
| **职责** | 管理所有基础设施依赖 | 提供简化的测试接口 |
| **参数** | 需要 `TaskWorkerCreationContext` | 只需 `task` + `stages` |
| **依赖注入** | 注入 10+ 个基础设施服务 | 委托给 `TaskWorkerFactory` |
| **使用场景** | 生产环境创建 TaskExecutor | 测试场景快速创建 |
| **设计原则** | 完整、灵活、可配置 | 简单、便捷、测试友好 |

**关键点**：
- ✅ `TaskExecutorFactory` **内部委托**给 `TaskWorkerFactory`
- ✅ 不重复实现创建逻辑，保持代码一致性
- ✅ 仅简化测试场景的参数传递

## 核心优势

- ✅ **简化测试代码**：无需手动注入 10+ 个依赖
- ✅ **零侵入**：通过监听领域事件跟踪执行过程
- ✅ **灵活配置**：支持多种创建方式和自定义参数
- ✅ **类型安全**：编译时检查，避免运行时错误

## 快速开始

### 1. 最简单的用法

```java
@SpringBootTest
public class MyTest {
    @Autowired
    private TaskExecutorFactory taskExecutorFactory;
    
    @Test
    void testBasicExecution() {
        TaskAggregate task = new TaskAggregateTestBuilder().buildPending();
        List<TaskStage> stages = StageListTestFactory.threeSuccessStages();
        
        // ✅ 一行代码创建 TaskExecutor
        TaskExecutor executor = taskExecutorFactory.create(task, stages);
        
        TaskResult result = executor.execute();
        assertThat(result.isSuccess()).isTrue();
    }
}
```

### 2. 自定义运行时上下文

用于测试暂停、取消等场景：

```java
@Test
void testPause() {
    TaskAggregate task = new TaskAggregateTestBuilder().buildPending();
    List<TaskStage> stages = StageListTestFactory.slowStages();
    
    // 创建自定义 Context
    TaskRuntimeContext context = new TaskRuntimeContext(
        task.getPlanId(),
        task.getTaskId(),
        task.getTenantId()
    );
    context.requestPause();
    
    // ✅ 传入自定义 Context
    TaskExecutor executor = taskExecutorFactory.create(task, stages, context);
    
    TaskResult result = executor.execute();
    assertThat(result.getFinalStatus()).isEqualTo(TaskStatus.PAUSED);
}
```

### 3. 使用 Builder 模式

用于复杂配置场景：

```java
@Test
void testWithBuilder() {
    TaskAggregate task = new TaskAggregateTestBuilder().buildPending();
    
    TaskExecutor executor = taskExecutorFactory.builder()
        .task(task)
        .stages(StageListTestFactory.threeSuccessStages())
        .progressInterval(5)  // 自定义进度上报间隔
        .build();
    
    executor.execute();
}
```

## 配合 TestEventTracker 验证执行过程

`TestEventTracker` 通过监听领域事件，自动跟踪任务执行过程：

```java
@SpringBootTest
public class MyTest {
    @Autowired
    private TaskExecutorFactory taskExecutorFactory;
    
    @Autowired
    private TestEventTracker eventTracker;  // ✅ 注入事件跟踪器
    
    @BeforeEach
    void setUp() {
        eventTracker.clear();  // 清理上次测试的事件
    }
    
    @Test
    void testExecutionFlow() {
        TaskAggregate task = new TaskAggregateTestBuilder().buildPending();
        TaskExecutor executor = taskExecutorFactory.create(task, 
            StageListTestFactory.threeSuccessStages());
        
        executor.execute();
        
        // ✅ 验证事件顺序
        List<TrackedEvent> events = eventTracker.getEvents();
        assertThat(events).extracting("type").containsExactly(
            EventType.TASK_STARTED,
            EventType.STAGE_STARTED,
            EventType.STAGE_COMPLETED,
            EventType.STAGE_STARTED,
            EventType.STAGE_COMPLETED,
            EventType.STAGE_STARTED,
            EventType.STAGE_COMPLETED,
            EventType.TASK_COMPLETED
        );
        
        // ✅ 验证状态转换历史
        List<TaskStatus> statusHistory = eventTracker.getTaskStatusHistory(task.getTaskId());
        assertThat(statusHistory).containsExactly(
            TaskStatus.RUNNING,
            TaskStatus.COMPLETED
        );
        
        // ✅ 验证执行的 Stages
        List<String> executedStages = eventTracker.getExecutedStages(task.getTaskId());
        assertThat(executedStages).hasSize(6);  // 3 stages * 2 events
    }
}
```

## API 参考

### TaskExecutorFactory

#### `create(task, stages)`
创建标准配置的 TaskExecutor（最常用）

#### `create(task, stages, context)`
创建带自定义运行时上下文的 TaskExecutor

#### `create(planId, task, stages)`
创建带自定义 PlanId 的 TaskExecutor

#### `builder()`
返回 Builder 实例，支持流式配置

### TestEventTracker

#### `getEvents()`
获取所有跟踪的事件

#### `getTaskStatusHistory(taskId)`
获取任务的状态转换历史

#### `getExecutedStages(taskId)`
获取执行的 Stage 名称列表

#### `getEventsOfType(taskId, eventType)`
获取特定类型的事件

#### `clear()`
清空所有跟踪的事件（建议在 `@BeforeEach` 中调用）

## 测试场景示例

### 测试失败场景

```java
@Test
void testFailure() {
    TaskAggregate task = new TaskAggregateTestBuilder().buildPending();
    // 创建在第3个 Stage 失败的列表
    List<TaskStage> stages = StageListTestFactory.failAtThirdStage();
    
    TaskExecutor executor = taskExecutorFactory.create(task, stages);
    TaskResult result = executor.execute();
    
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getFinalStatus()).isEqualTo(TaskStatus.FAILED);
    
    // 验证失败事件
    List<TrackedEvent> failEvents = eventTracker.getEventsOfType(
        task.getTaskId(),
        EventType.TASK_FAILED
    );
    assertThat(failEvents).isNotEmpty();
}
```

### 测试重试场景

```java
@Test
void testRetry() {
    TaskAggregate task = new TaskAggregateTestBuilder().buildPending();
    List<TaskStage> stages = StageListTestFactory.failOnceAtSecondStage();
    
    // 第一次执行（会失败）
    TaskExecutor executor1 = taskExecutorFactory.create(task, stages);
    TaskResult result1 = executor1.execute();
    assertThat(result1.isSuccess()).isFalse();
    
    eventTracker.clear();
    
    // 第二次执行（重试成功）
    TaskExecutor executor2 = taskExecutorFactory.create(task, stages);
    TaskResult result2 = executor2.execute();
    assertThat(result2.isSuccess()).isTrue();
}
```

## 最佳实践

1. **总是在 `@BeforeEach` 中清理事件**
   ```java
   @BeforeEach
   void setUp() {
       eventTracker.clear();
   }
   ```

2. **使用工厂方法构建测试数据**
   ```java
   TaskAggregate task = new TaskAggregateTestBuilder().buildPending();
   List<TaskStage> stages = StageListTestFactory.threeSuccessStages();
   ```

3. **优先使用最简单的 API**
   - 如果不需要自定义配置，使用 `create(task, stages)`
   - 需要自定义时，使用 `create(task, stages, context)`
   - 复杂配置时，使用 Builder 模式

4. **结合 assertThat 进行断言**
   ```java
   assertThat(events).extracting("type").containsExactly(/*...*/);
   assertThat(statusHistory).contains(TaskStatus.RUNNING, TaskStatus.COMPLETED);
   ```

## 架构说明

```
测试代码
  ↓ 使用
TaskExecutorFactory (测试简化层)
  ↓ 构建 TaskWorkerCreationContext
  ↓ 委托给
TaskWorkerFactory (生产环境工厂)
  ↓ 创建
TaskExecutor
  ↓ 调用
TaskDomainService (触发领域事件)
  ↓ 发布
Spring EventPublisher
  ↓ 监听
TestEventTracker (自动跟踪)
  ↑ 查询
测试代码 (验证执行过程)
```

**关键点**：
- 无需修改生产代码
- 测试工厂委托给生产工厂，保持逻辑一致性
- 通过领域事件实现跟踪（符合 DDD 原则）
- 测试代码与业务代码解耦

## 参考示例

完整示例请参考：
- `TaskExecutorFactoryExampleTest.java` - 6个完整示例
- `TaskExecutorTest.java` - 实际集成测试用例

## 故障排查

### Q: 事件跟踪器没有收到事件？
A: 确保使用 `@SpringBootTest` 并注入真实的 Spring 依赖

### Q: TaskRuntimeContext 构造函数参数错误？
A: 使用工厂方法会自动创建正确的 Context：
```java
taskExecutorFactory.create(task, stages)  // ✅ 自动创建 Context
```

### Q: 需要验证 Stage 内部逻辑？
A: 使用自定义 Stage 实现（如 `testutil.stage` 包下的示例）

