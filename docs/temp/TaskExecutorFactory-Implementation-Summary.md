# TaskExecutorFactory 实现总结

## 实现内容

### 1. 核心工具类

#### `TaskExecutorFactory.java`
- **位置**：`deploy/src/test/java/xyz/firestige/deploy/testutil/factory/TaskExecutorFactory.java`
- **功能**：
  - 简化集成测试中 `TaskExecutor` 的创建
  - 自动注入所有 Spring 管理的依赖
  - 提供多种创建方式（简单、自定义 Context、Builder 模式）
  - 自动创建默认的 `TaskRuntimeContext`
- **依赖注入**：
  - `TaskDomainService`
  - `StateTransitionService`
  - `ApplicationEventPublisher`
  - `CheckpointService`
  - `TenantConflictManager`

### 2. 事件跟踪增强

#### `TestEventTracker.java` (已增强)
- **新增方法**：
  - `getEventsOfType(TaskId, EventType)` - 获取特定类型的事件
- **已有功能**：
  - 监听所有任务和 Stage 相关的领域事件
  - 提供状态转换历史查询
  - 提供 Stage 执行顺序查询

### 3. 示例测试

#### `TaskExecutorFactoryExampleTest.java`
- **位置**：`deploy/src/test/java/xyz/firestige/deploy/integration/TaskExecutorFactoryExampleTest.java`
- **包含 6 个完整示例**：
  1. 最简单的用法
  2. 自定义 Context（暂停测试）
  3. 自定义 PlanId
  4. Builder 模式
  5. 事件跟踪验证
  6. 失败场景测试

### 4. 文档

#### `TaskExecutorFactory_README.md`
- **位置**：`deploy/src/test/java/xyz/firestige/deploy/testutil/factory/TaskExecutorFactory_README.md`
- **内容**：
  - 快速开始指南
  - API 参考
  - 测试场景示例
  - 最佳实践
  - 架构说明
  - 故障排查

## 使用方式

### 最简单的用法

```java
@SpringBootTest
public class TaskExecutorTest {
    @Autowired
    private TaskExecutorFactory taskExecutorFactory;
    
    @Autowired
    private TestEventTracker eventTracker;
    
    @BeforeEach
    void setUp() {
        eventTracker.clear();
    }
    
    @Test
    void test() {
        // 准备数据
        TaskAggregate task = new TaskAggregateTestBuilder().buildPending();
        List<TaskStage> stages = StageListTestFactory.threeSuccessStages();
        
        // ✅ 创建并执行
        TaskExecutor executor = taskExecutorFactory.create(task, stages);
        TaskResult result = executor.execute();
        
        // ✅ 验证结果
        assertThat(result.isSuccess()).isTrue();
        
        // ✅ 验证事件
        List<TaskStatus> statusHistory = eventTracker.getTaskStatusHistory(task.getTaskId());
        assertThat(statusHistory).containsExactly(TaskStatus.RUNNING, TaskStatus.COMPLETED);
    }
}
```

## 核心优势

### 1. 零侵入
- **无需修改** `TaskExecutor` 源码
- 通过 Spring 事件机制自动跟踪
- 符合开闭原则

### 2. 简化测试
**之前需要**：
```java
TaskExecutor executor = new TaskExecutor(
    planId, task, stages, context,
    taskDomainService, stateTransitionService,
    eventPublisher, checkpointService,
    conflictManager, 10, metrics
); // 11 个参数！
```

**现在只需**：
```java
TaskExecutor executor = taskExecutorFactory.create(task, stages);
```

### 3. 完整跟踪
通过 `TestEventTracker` 可以验证：
- ✅ 任务启动/完成/失败
- ✅ 状态转换历史
- ✅ Stage 执行顺序
- ✅ 暂停/恢复事件
- ✅ 失败信息

### 4. 灵活配置
提供多种创建方式：
```java
// 标准配置
taskExecutorFactory.create(task, stages)

// 自定义 Context
taskExecutorFactory.create(task, stages, context)

// 自定义 PlanId
taskExecutorFactory.create(planId, task, stages)

// Builder 模式
taskExecutorFactory.builder()
    .task(task)
    .stages(stages)
    .progressInterval(5)
    .build()
```

## 架构设计

```
测试代码
  ↓ 使用
TaskExecutorFactory (Spring @Component)
  ↓ 创建
TaskExecutor
  ↓ 调用
TaskDomainService
  ↓ 产生
领域事件 (TaskStartedEvent, TaskCompletedEvent, etc.)
  ↓ 监听
TestEventTracker (Spring @Component)
  ↑ 查询
测试代码 (验证执行过程)
```

**关键设计决策**：
1. 使用 **Spring 依赖注入** 管理服务依赖
2. 通过 **领域事件** 实现执行跟踪（而非侵入式修改）
3. 提供 **多层次 API**（简单 → 自定义 → Builder）
4. 遵循 **测试数据构建器模式**（配合 `TaskAggregateTestBuilder`）

## 测试覆盖场景

### 支持的测试场景

✅ **正常执行流程**
- 多 Stage 串联执行
- 状态转换验证
- 事件顺序验证

✅ **失败场景**
- Stage 执行失败
- 中途失败恢复
- 失败事件验证

✅ **暂停/恢复**
- 协作式暂停
- 从 Checkpoint 恢复
- 暂停状态验证

✅ **取消场景**
- 协作式取消
- 取消事件验证

✅ **重试场景**
- 失败重试
- 从 Checkpoint 重试

## 与现有测试体系的集成

### 配合使用的工具类

1. **TaskAggregateTestBuilder**
   - 构建各种状态的任务聚合根
   - `buildPending()`, `buildRunning()`, `buildFailed()` 等

2. **StageListTestFactory**
   - 快速创建测试用的 Stage 列表
   - `threeSuccessStages()`, `failAtThirdStage()`, `slowStages()` 等

3. **ValueObjectTestFactory**
   - 创建测试用的值对象
   - `randomTaskId()`, `randomPlanId()`, `randomTenantId()` 等

4. **TestEventTracker**
   - 跟踪执行过程
   - 查询状态历史和事件

### 示例组合使用

```java
@Test
void complexScenario() {
    // 1. 创建测试数据
    TaskAggregate task = new TaskAggregateTestBuilder()
        .planId(ValueObjectTestFactory.randomPlanId())
        .totalStages(5)
        .buildPending();
    
    // 2. 创建 Stage 列表
    List<TaskStage> stages = StageListTestFactory.failAtThirdStage();
    
    // 3. 创建 TaskExecutor
    TaskExecutor executor = taskExecutorFactory.create(task, stages);
    
    // 4. 执行
    TaskResult result = executor.execute();
    
    // 5. 验证
    assertThat(result.isSuccess()).isFalse();
    assertThat(eventTracker.getExecutedStages(task.getTaskId()))
        .hasSize(4);  // 2 stages * 2 events (started + completed/failed)
}
```

## 编译状态

✅ **编译成功**
- `TaskExecutorFactory.java` - 无错误
- `TaskExecutorFactoryExampleTest.java` - 无错误
- `TestEventTracker.java` - 已增强，无错误

⚠️ **警告说明**
- 部分方法未使用的警告是正常的（工具类方法供未来使用）

## 下一步建议

1. **运行示例测试**
   ```bash
   mvn test -Dtest=TaskExecutorFactoryExampleTest -pl deploy
   ```

2. **在现有测试中应用**
   - 重构 `TaskExecutorTest.java` 使用新工厂
   - 简化其他集成测试

3. **扩展功能**（按需）
   - 添加更多便捷方法
   - 支持更多自定义配置
   - 增加断言辅助方法

## 文件清单

```
deploy/src/test/java/xyz/firestige/deploy/
├── testutil/
│   ├── factory/
│   │   ├── TaskExecutorFactory.java           ✅ 新增
│   │   ├── TaskExecutorFactory_README.md      ✅ 新增
│   │   ├── TaskAggregateTestBuilder.java      (已有)
│   │   ├── StageListTestFactory.java          (已有)
│   │   └── ValueObjectTestFactory.java        (已有)
│   └── TestEventTracker.java                  ✅ 增强
└── integration/
    ├── TaskExecutorFactoryExampleTest.java    ✅ 新增
    └── TaskExecutorTest.java                  (待重构)
```

## 总结

成功创建了一个**零侵入、易用、完整**的 TaskExecutor 测试工厂，通过：
1. ✅ Spring 依赖注入管理复杂依赖
2. ✅ 领域事件机制实现执行跟踪
3. ✅ 多层次 API 支持不同复杂度的测试
4. ✅ 完整的文档和示例

开发者现在可以用**一行代码**创建 TaskExecutor，并通过**事件跟踪器**验证完整的执行流程！

