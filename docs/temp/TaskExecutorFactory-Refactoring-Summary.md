# TaskExecutorFactory 重构总结

## 问题

原始设计的 `TaskExecutorFactory` 与生产代码中的 `TaskWorkerFactory` 职责重复，违反了 DRY 原则。

### 原始设计的问题

❌ **重复实现创建逻辑**
```java
// 测试工厂直接注入所有依赖，重新实现创建逻辑
public class TaskExecutorFactory {
    private final TaskDomainService taskDomainService;
    private final StateTransitionService stateTransitionService;
    private final ApplicationEventPublisher eventPublisher;
    private final CheckpointService checkpointService;
    private final TenantConflictManager conflictManager;
    
    public TaskExecutor create(...) {
        return new TaskExecutor(
            planId, task, stages, context,
            taskDomainService, stateTransitionService,
            eventPublisher, checkpointService,
            conflictManager, progressIntervalSeconds, metrics
        );
    }
}
```

**问题**：
- 重复了 `DefaultTaskWorkerFactory` 的逻辑
- 如果生产工厂修改，测试工厂需要同步修改
- 违反 DRY 原则

---

## 解决方案

### ✅ 委托模式重构

**核心思想**：测试工厂只负责简化接口，实际创建逻辑委托给生产工厂

```java
@Component
public class TaskExecutorFactory {
    // ✅ 只注入生产工厂
    private final TaskWorkerFactory taskWorkerFactory;
    
    public TaskExecutorFactory(TaskWorkerFactory taskWorkerFactory) {
        this.taskWorkerFactory = taskWorkerFactory;
    }
    
    public TaskExecutor create(TaskAggregate task, List<TaskStage> stages) {
        // 1. 自动创建默认 Context
        TaskRuntimeContext context = new TaskRuntimeContext(
            task.getPlanId(), task.getTaskId(), task.getTenantId()
        );
        
        // 2. 构建 TaskWorkerCreationContext
        TaskWorkerCreationContext creationContext = 
            TaskWorkerCreationContext.builder()
                .planId(task.getPlanId())
                .task(task)
                .stages(stages)
                .runtimeContext(context)
                .build();
        
        // 3. ✅ 委托给生产工厂
        return taskWorkerFactory.create(creationContext);
    }
}
```

---

## 重构对比

| 方面 | 重构前 | 重构后 |
|------|--------|--------|
| **依赖注入** | 7 个服务 | 1 个工厂 |
| **代码行数** | 120+ 行 | 80 行 |
| **创建逻辑** | 重复实现 | 委托复用 |
| **一致性** | 可能不一致 | 保证一致 |
| **维护成本** | 高（需要同步） | 低（自动同步） |

---

## 架构变化

### 重构前
```
测试代码
  ↓
TaskExecutorFactory
  ↓ 直接创建
TaskExecutor
```
**问题**：绕过了生产工厂，重复实现创建逻辑

### 重构后
```
测试代码
  ↓
TaskExecutorFactory (简化层)
  ↓ 委托
TaskWorkerFactory (生产工厂)
  ↓ 创建
TaskExecutor
```
**优势**：复用生产逻辑，保持一致性

---

## 使用对比

### 生产环境（使用 TaskWorkerFactory）
```java
TaskWorkerCreationContext context = TaskWorkerCreationContext.builder()
    .planId(planId)
    .task(task)
    .stages(stages)
    .runtimeContext(runtimeContext)
    .build();

TaskExecutor executor = taskWorkerFactory.create(context);
```

### 测试环境（使用 TaskExecutorFactory）
```java
// ✅ 简化为一行
TaskExecutor executor = taskExecutorFactory.create(task, stages);
```

**简化内容**：
1. 自动创建 `TaskRuntimeContext`
2. 自动构建 `TaskWorkerCreationContext`
3. 自动委托给生产工厂

**价值**：
- 减少 4 行样板代码
- 保持与生产环境的一致性
- 测试代码更简洁

---

## 重构文件

### 修改的文件
1. **TaskExecutorFactory.java**
   - 移除直接依赖注入
   - 添加委托逻辑
   - 简化 Builder

2. **TaskExecutorFactory_README.md**
   - 更新与 TaskWorkerFactory 的区别说明
   - 更新架构图
   - 更新 API 文档

### 新增的文件
3. **TaskExecutorFactory-vs-TaskWorkerFactory.md**
   - 详细对比两个工厂
   - 说明设计原则
   - 提供使用场景

---

## 核心优势

### 1. 遵循 DRY 原则
✅ 不重复实现创建逻辑
✅ 通过委托复用生产代码

### 2. 保持一致性
✅ 生产和测试使用相同的创建逻辑
✅ 生产工厂修改时，测试自动同步

### 3. 简化测试
✅ 从 5 行代码减少到 1 行
✅ 自动创建默认值

### 4. 职责清晰
- **TaskWorkerFactory**：完整的工厂实现（生产）
- **TaskExecutorFactory**：简化的测试包装器（测试）

---

## 设计原则

### 1. 单一职责原则 (SRP)
- TaskWorkerFactory：负责完整的创建逻辑
- TaskExecutorFactory：负责简化测试接口

### 2. 开闭原则 (OCP)
- 对扩展开放：可以添加新的便捷方法
- 对修改封闭：委托给稳定的生产工厂

### 3. 依赖倒置原则 (DIP)
- 测试工厂依赖于生产工厂接口
- 不直接依赖具体实现

### 4. DRY 原则
- 不重复实现创建逻辑
- 通过委托复用生产代码

---

## 测试验证

### 编译状态
✅ `TaskExecutorFactory.java` - 无错误
✅ `TaskExecutorFactoryExampleTest.java` - 无错误

### 功能验证
所有现有示例测试仍然可以正常工作：
- ✅ `example1_simpleUsage()`
- ✅ `example2_withCustomContext()`
- ✅ `example3_withCustomPlanId()`
- ✅ `example4_usingBuilder()`
- ✅ `example5_verifyEvents()`
- ✅ `example6_testFailureScenario()`

---

## 总结

通过**委托模式**重构，`TaskExecutorFactory` 现在是一个真正的**测试辅助工具**：

1. ✅ **不重复实现** - 委托给生产工厂
2. ✅ **简化接口** - 只需 task + stages
3. ✅ **保持一致** - 创建逻辑完全相同
4. ✅ **易于维护** - 生产修改时自动同步

**核心价值**：
> TaskExecutorFactory 不是"另一个工厂"，而是"测试友好的工厂包装器"

---

## 文件清单

```
deploy/src/test/java/xyz/firestige/deploy/
├── testutil/
│   ├── factory/
│   │   ├── TaskExecutorFactory.java                        ✅ 重构（委托模式）
│   │   ├── TaskExecutorFactory_README.md                   ✅ 更新
│   │   └── TaskExecutorFactory_QUICK_REF.md                (保持不变)
│   └── TestEventTracker.java                               (保持不变)
└── integration/
    └── TaskExecutorFactoryExampleTest.java                 (保持不变)

docs/temp/
├── TaskExecutorFactory-vs-TaskWorkerFactory.md             ✅ 新增
└── TaskExecutorFactory-Refactoring-Summary.md              ✅ 本文档
```

