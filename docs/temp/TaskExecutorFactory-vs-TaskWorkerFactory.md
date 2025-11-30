# TaskExecutorFactory vs TaskWorkerFactory 对比

## 核心区别

### TaskWorkerFactory (生产环境)
**位置**：`xyz.firestige.deploy.infrastructure.execution.TaskWorkerFactory`

**职责**：
- 生产环境的标准工厂接口
- 管理所有基础设施依赖（10+ 个服务）
- 创建完整配置的 TaskExecutor
- 支持心跳调度器等高级功能

**使用方式**：
```java
// 1. 构建 Context
TaskWorkerCreationContext context = TaskWorkerCreationContext.builder()
    .planId(planId)
    .task(task)
    .stages(stages)
    .runtimeContext(runtimeContext)
    .build();

// 2. 使用工厂创建
TaskExecutor executor = taskWorkerFactory.create(context);
```

**依赖注入**：
```java
@Component
public class DefaultTaskWorkerFactory implements TaskWorkerFactory {
    private final TaskDomainService taskDomainService;
    private final StateTransitionService stateTransitionService;
    private final ApplicationEventPublisher technicalEventPublisher;
    private final CheckpointService checkpointService;
    private final TenantConflictManager conflictManager;
    private final int progressIntervalSeconds;
    private final MetricsRegistry metrics;
    // ... 构造函数注入
}
```

---

### TaskExecutorFactory (测试环境)
**位置**：`xyz.firestige.deploy.testutil.factory.TaskExecutorFactory`

**职责**：
- 测试专用的简化接口
- 委托给 TaskWorkerFactory
- 自动创建默认的 TaskRuntimeContext
- 减少测试代码的样板代码

**使用方式**：
```java
// ✅ 一行代码搞定
TaskExecutor executor = taskExecutorFactory.create(task, stages);
```

**依赖注入**：
```java
@Component
public class TaskExecutorFactory {
    // ✅ 只需要注入生产环境的工厂
    private final TaskWorkerFactory taskWorkerFactory;
    
    public TaskExecutorFactory(TaskWorkerFactory taskWorkerFactory) {
        this.taskWorkerFactory = taskWorkerFactory;
    }
}
```

**内部实现**：
```java
public TaskExecutor create(TaskAggregate task, List<TaskStage> stages) {
    // 1. 自动创建默认 Context
    TaskRuntimeContext context = new TaskRuntimeContext(
        task.getPlanId(),
        task.getTaskId(),
        task.getTenantId()
    );
    
    // 2. 构建 TaskWorkerCreationContext
    TaskWorkerCreationContext creationContext = TaskWorkerCreationContext.builder()
        .planId(task.getPlanId())
        .task(task)
        .stages(stages)
        .runtimeContext(context)
        .build();
    
    // 3. 委托给生产工厂
    return taskWorkerFactory.create(creationContext);
}
```

---

## 对比表格

| 特性 | TaskWorkerFactory | TaskExecutorFactory |
|------|------------------|---------------------|
| **环境** | 生产 | 测试 |
| **职责** | 完整的工厂实现 | 简化的测试包装器 |
| **参数** | `TaskWorkerCreationContext` | `task` + `stages` |
| **依赖数量** | 7+ 个服务 | 1 个（工厂本身） |
| **代码行数** | 需要 5 行 | 只需 1 行 |
| **Context 创建** | 手动创建 | 自动创建 |
| **实现方式** | 直接创建 TaskExecutor | 委托给 TaskWorkerFactory |
| **可配置性** | 完整配置 | 简化配置 + Builder |
| **心跳调度器** | 自动创建 | 自动（通过委托） |

---

## 架构关系

```
测试代码
    ↓
TaskExecutorFactory (测试简化层)
    ↓ 委托
TaskWorkerFactory (生产工厂)
    ↓ 创建
TaskExecutor
```

**关键设计决策**：
1. ✅ **不重复实现** - 测试工厂委托给生产工厂
2. ✅ **保持一致性** - 创建逻辑完全相同
3. ✅ **简化接口** - 测试只需要关心核心参数
4. ✅ **自动默认值** - 自动创建 TaskRuntimeContext

---

## 使用场景

### 生产环境（使用 TaskWorkerFactory）
```java
@Service
public class TaskExecutionService {
    @Autowired
    private TaskWorkerFactory taskWorkerFactory;
    
    public void executeTask(TaskAggregate task, List<TaskStage> stages) {
        TaskWorkerCreationContext context = TaskWorkerCreationContext.builder()
            .planId(task.getPlanId())
            .task(task)
            .stages(stages)
            .runtimeContext(createContext(task))
            .build();
        
        TaskExecutor executor = taskWorkerFactory.create(context);
        executor.execute();
    }
}
```

### 测试环境（使用 TaskExecutorFactory）
```java
@SpringBootTest
public class TaskExecutorTest {
    @Autowired
    private TaskExecutorFactory taskExecutorFactory;
    
    @Test
    void test() {
        TaskAggregate task = new TaskAggregateTestBuilder().buildPending();
        List<TaskStage> stages = StageListTestFactory.threeSuccessStages();
        
        // ✅ 简化的创建方式
        TaskExecutor executor = taskExecutorFactory.create(task, stages);
        executor.execute();
    }
}
```

---

## 优势总结

### TaskExecutorFactory 的优势

1. **简化测试代码**
   - 从 5 行减少到 1 行
   - 无需手动创建 Context

2. **保持一致性**
   - 委托给生产工厂
   - 创建逻辑完全相同
   - 避免测试和生产的差异

3. **易于维护**
   - 生产逻辑修改时，测试自动同步
   - 无需维护两套创建逻辑

4. **测试友好**
   - 提供多种便捷方法
   - 支持 Builder 模式
   - 自动创建默认值

---

## 设计原则

1. **单一职责原则**
   - TaskWorkerFactory：负责完整的创建逻辑
   - TaskExecutorFactory：负责简化测试接口

2. **开闭原则**
   - 对扩展开放（可以添加新的便捷方法）
   - 对修改封闭（委托给稳定的生产工厂）

3. **依赖倒置原则**
   - 测试工厂依赖于生产工厂接口
   - 不直接依赖具体实现

4. **DRY 原则**
   - 不重复实现创建逻辑
   - 通过委托复用生产代码

---

## 总结

**TaskExecutorFactory 的核心价值**：
- ✅ **简化**：减少测试样板代码
- ✅ **委托**：复用生产环境逻辑
- ✅ **一致**：保证测试和生产的行为一致
- ✅ **便捷**：提供测试友好的 API

**不是**：
- ❌ 不是重新实现 TaskWorkerFactory
- ❌ 不是绕过生产工厂
- ❌ 不是创建不同的 TaskExecutor

