# T-032 优化方案：执行策略模式

> 日期: 2025-11-29  
> 状态: 设计优化  
> 目标: 避免 execute() 方法膨胀，采用策略模式分离执行逻辑

---

## 🎯 设计目标

1. **统一入口**：所有执行都通过 `execute()` 方法
2. **避免膨胀**：执行逻辑拆分到独立的策略类
3. **语义化请求**：通过 Context 标志位驱动
4. **易于扩展**：新增执行模式只需添加新策略

---

## 🏗️ 架构设计

### 类图

```
┌─────────────────────────────────────────────────────────────┐
│                      TaskExecutor                            │
│                                                              │
│  - task: TaskAggregate                                       │
│  - context: TaskRuntimeContext                               │
│  - executionStrategyChain: ExecutionStrategyChain            │
│                                                              │
│  + execute(): TaskResult                                     │
│      → executionStrategyChain.selectAndExecute()             │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│              ExecutionStrategyChain                          │
│                                                              │
│  - strategies: List<ExecutionStrategy>                       │
│                                                              │
│  + selectAndExecute(): TaskResult                            │
│      → 遍历策略，找到第一个匹配的执行                          │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│           ExecutionStrategy (Interface)                      │
│                                                              │
│  + canHandle(task, context): boolean                         │
│  + execute(task, context, stages): TaskResult                │
└─────────────────────────────────────────────────────────────┘
                            △
        ┌───────────────────┼───────────────────┬──────────────┐
        │                   │                   │              │
┌───────────────┐  ┌────────────────┐  ┌───────────────┐  ┌──────────────┐
│ StartStrategy │  │ ResumeStrategy │  │ RetryStrategy │  │RollbackStrategy│
│               │  │                │  │               │  │              │
│ PENDING →     │  │ PAUSED →       │  │ FAILED →      │  │ FAILED →     │
│ RUNNING       │  │ RUNNING        │  │ RUNNING       │  │ ROLLING_BACK │
└───────────────┘  └────────────────┘  └───────────────┘  └──────────────┘
```

---

## 📝 接口设计

### 1. ExecutionStrategy 接口

```java
/**
 * 执行策略接口
 * <p>
 * 职责：
 * 1. 判断是否可以处理当前状态和请求
 * 2. 执行对应的状态转换和业务逻辑
 */
public interface ExecutionStrategy {
    
    /**
     * 判断是否可以处理当前的 Task 状态和 Context 请求
     *
     * @param task Task 聚合
     * @param context 运行时上下文
     * @return true = 可以处理，false = 不能处理
     */
    boolean canHandle(TaskAggregate task, TaskRuntimeContext context);
    
    /**
     * 执行策略
     *
     * @param task Task 聚合
     * @param context 运行时上下文
     * @param stages Stage 列表
     * @param dependencies 依赖服务（TaskDomainService, CheckpointService 等）
     * @return 执行结果
     */
    TaskResult execute(
        TaskAggregate task, 
        TaskRuntimeContext context, 
        List<TaskStage> stages,
        ExecutionDependencies dependencies
    );
    
    /**
     * 策略优先级（数字越小优先级越高）
     * 用于排序，确保特定策略优先匹配
     */
    default int priority() {
        return 100;
    }
}
```

### 2. ExecutionDependencies 依赖注入对象

```java
/**
 * 执行策略依赖
 * <p>
 * 将所有依赖封装到一个对象中，避免策略构造函数参数过多
 */
public class ExecutionDependencies {
    private final TaskDomainService taskDomainService;
    private final StateTransitionService stateTransitionService;
    private final CheckpointService checkpointService;
    private final TenantConflictManager conflictManager;
    private final MetricsRegistry metrics;
    
    // Constructor and getters
}
```

### 3. ExecutionStrategyChain 责任链

```java
/**
 * 执行策略链
 * <p>
 * 职责：
 * 1. 按优先级维护策略列表
 * 2. 选择第一个匹配的策略执行
 */
public class ExecutionStrategyChain {
    
    private final List<ExecutionStrategy> strategies;
    
    public ExecutionStrategyChain(List<ExecutionStrategy> strategies) {
        // 按优先级排序
        this.strategies = strategies.stream()
            .sorted(Comparator.comparingInt(ExecutionStrategy::priority))
            .toList();
    }
    
    /**
     * 选择并执行策略
     */
    public TaskResult selectAndExecute(
        TaskAggregate task,
        TaskRuntimeContext context,
        List<TaskStage> stages,
        ExecutionDependencies dependencies
    ) {
        for (ExecutionStrategy strategy : strategies) {
            if (strategy.canHandle(task, context)) {
                log.info("选择执行策略: {}, taskId: {}", 
                    strategy.getClass().getSimpleName(), task.getTaskId());
                return strategy.execute(task, context, stages, dependencies);
            }
        }
        
        // 没有匹配的策略
        throw new IllegalStateException(
            String.format("无法处理 Task 状态: %s, taskId: %s", 
                task.getStatus(), task.getTaskId())
        );
    }
}
```

---

## 🎨 具体策略实现

### 策略 1: StartStrategy（首次执行）

```java
/**
 * 首次启动策略
 * <p>
 * 处理：PENDING → RUNNING → 正常执行
 */
public class StartStrategy implements ExecutionStrategy {
    
    @Override
    public boolean canHandle(TaskAggregate task, TaskRuntimeContext context) {
        return task.getStatus() == TaskStatus.PENDING;
    }
    
    @Override
    public TaskResult execute(
        TaskAggregate task, 
        TaskRuntimeContext context, 
        List<TaskStage> stages,
        ExecutionDependencies deps
    ) {
        // 1. 状态转换：PENDING → RUNNING
        deps.getTaskDomainService().startTask(task, context);
        
        // 2. 执行正常流程
        return executeNormalStages(task, context, stages, deps);
    }
    
    @Override
    public int priority() {
        return 10;  // 高优先级
    }
    
    // 正常执行逻辑（提取为公共方法）
    private TaskResult executeNormalStages(...) {
        // Stage 循环执行
        // 检查点保存
        // 暂停/取消检查
        // ...
    }
}
```

### 策略 2: ResumeStrategy（恢复执行）

```java
/**
 * 恢复执行策略
 * <p>
 * 处理：PAUSED → RUNNING → 从检查点继续执行
 */
public class ResumeStrategy implements ExecutionStrategy {
    
    @Override
    public boolean canHandle(TaskAggregate task, TaskRuntimeContext context) {
        return task.getStatus() == TaskStatus.PAUSED;
    }
    
    @Override
    public TaskResult execute(
        TaskAggregate task, 
        TaskRuntimeContext context, 
        List<TaskStage> stages,
        ExecutionDependencies deps
    ) {
        // 1. 状态转换：PAUSED → RUNNING
        deps.getTaskDomainService().resumeTask(task, context);
        
        // 2. 清除暂停标志
        context.clearPause();
        
        // 3. 从检查点恢复执行
        TaskCheckpoint checkpoint = deps.getCheckpointService().loadCheckpoint(task);
        int startIndex = (checkpoint != null) ? checkpoint.getLastCompletedStageIndex() + 1 : 0;
        
        // 4. 执行正常流程（从 startIndex 开始）
        return executeNormalStages(task, context, stages, deps, startIndex);
    }
    
    @Override
    public int priority() {
        return 20;
    }
}
```

### 策略 3: RetryStrategy（重试执行）

```java
/**
 * 重试执行策略
 * <p>
 * 处理：FAILED/ROLLED_BACK + retryRequested → RUNNING → 重新执行
 */
public class RetryStrategy implements ExecutionStrategy {
    
    @Override
    public boolean canHandle(TaskAggregate task, TaskRuntimeContext context) {
        TaskStatus status = task.getStatus();
        return (status == TaskStatus.FAILED || status == TaskStatus.ROLLED_BACK)
            && context.isRetryRequested();
    }
    
    @Override
    public TaskResult execute(
        TaskAggregate task, 
        TaskRuntimeContext context, 
        List<TaskStage> stages,
        ExecutionDependencies deps
    ) {
        // 1. 状态转换：FAILED/ROLLED_BACK → RUNNING
        deps.getTaskDomainService().retryTask(task, context);
        
        // 2. 处理检查点
        boolean fromCheckpoint = context.isFromCheckpoint();
        int startIndex = 0;
        
        if (fromCheckpoint) {
            TaskCheckpoint checkpoint = deps.getCheckpointService().loadCheckpoint(task);
            startIndex = (checkpoint != null) ? checkpoint.getLastCompletedStageIndex() + 1 : 0;
        } else {
            // 不从检查点重试，清空检查点
            deps.getCheckpointService().clearCheckpoint(task);
        }
        
        // 3. 执行正常流程
        return executeNormalStages(task, context, stages, deps, startIndex);
    }
    
    @Override
    public int priority() {
        return 30;  // 优先于回滚
    }
}
```

### 策略 4: RollbackStrategy（回滚执行）

```java
/**
 * 回滚执行策略
 * <p>
 * 处理：FAILED + rollbackRequested → ROLLING_BACK → 回滚执行
 */
public class RollbackStrategy implements ExecutionStrategy {
    
    @Override
    public boolean canHandle(TaskAggregate task, TaskRuntimeContext context) {
        return task.getStatus() == TaskStatus.FAILED
            && context.isRollbackRequested();
    }
    
    @Override
    public TaskResult execute(
        TaskAggregate task, 
        TaskRuntimeContext context, 
        List<TaskStage> stages,
        ExecutionDependencies deps
    ) {
        // 1. 状态转换：FAILED → ROLLING_BACK
        deps.getTaskDomainService().startRollback(task, context);
        
        // 2. 执行回滚逻辑
        return executeRollback(task, context, stages, deps);
    }
    
    @Override
    public int priority() {
        return 40;  // 低于重试优先级
    }
    
    // 回滚执行逻辑
    private TaskResult executeRollback(...) {
        // 逆序执行 Stage.rollback()
        // ...
    }
}
```

### 策略 5: ContinueStrategy（继续执行）

```java
/**
 * 继续执行策略（兜底）
 * <p>
 * 处理：RUNNING → 继续执行（适用于内部状态转换后的继续执行）
 */
public class ContinueStrategy implements ExecutionStrategy {
    
    @Override
    public boolean canHandle(TaskAggregate task, TaskRuntimeContext context) {
        return task.getStatus() == TaskStatus.RUNNING;
    }
    
    @Override
    public TaskResult execute(
        TaskAggregate task, 
        TaskRuntimeContext context, 
        List<TaskStage> stages,
        ExecutionDependencies deps
    ) {
        // 已经是 RUNNING，直接执行
        log.debug("Task 已处于 RUNNING 状态，继续执行, taskId: {}", task.getTaskId());
        
        // 从检查点恢复
        TaskCheckpoint checkpoint = deps.getCheckpointService().loadCheckpoint(task);
        int startIndex = (checkpoint != null) ? checkpoint.getLastCompletedStageIndex() + 1 : 0;
        
        return executeNormalStages(task, context, stages, deps, startIndex);
    }
    
    @Override
    public int priority() {
        return 999;  // 最低优先级（兜底）
    }
}
```

---

## 🔄 重构后的 TaskExecutor

### 简化的 TaskExecutor

```java
/**
 * TaskExecutor（优化版：策略模式）
 * <p>
 * 职责：
 * 1. 统一的执行入口
 * 2. 委托给策略链选择执行策略
 * 3. 管理心跳和资源释放
 */
public class TaskExecutor {
    
    private final PlanId planId;
    private final TaskAggregate task;
    private final List<TaskStage> stages;
    private final TaskRuntimeContext context;
    
    // 策略链
    private final ExecutionStrategyChain strategyChain;
    
    // 依赖
    private final ExecutionDependencies dependencies;
    
    // 心跳调度器
    private volatile HeartbeatScheduler heartbeatScheduler;
    
    public TaskExecutor(
        PlanId planId,
        TaskAggregate task,
        List<TaskStage> stages,
        TaskRuntimeContext context,
        ExecutionStrategyChain strategyChain,
        ExecutionDependencies dependencies
    ) {
        this.planId = planId;
        this.task = task;
        this.stages = stages != null ? stages : new ArrayList<>();
        this.context = context;
        this.strategyChain = strategyChain;
        this.dependencies = dependencies;
    }
    
    /**
     * 统一的执行入口
     * <p>
     * ✅ 简化版：所有逻辑委托给策略链
     */
    public TaskResult execute() {
        TaskId taskId = task.getTaskId();
        LocalDateTime startTime = LocalDateTime.now();
        
        try {
            // 注入 MDC
            context.injectMdc(null);
            dependencies.getMetrics().incrementCounter("task_active");
            
            // 启动心跳
            startHeartbeat();
            
            // ✅ 核心：委托给策略链执行
            TaskResult result = strategyChain.selectAndExecute(
                task, context, stages, dependencies
            );
            
            // 清理资源
            cleanup(result);
            
            return result;
            
        } catch (Exception e) {
            log.error("任务执行异常, taskId: {}, error: {}", taskId, e.getMessage(), e);
            
            // 异常处理
            handleException(e);
            
            return TaskResult.fail(
                planId, taskId, task.getStatus(), e.getMessage(),
                Duration.between(startTime, LocalDateTime.now()),
                new ArrayList<>()
            );
        } finally {
            context.clearMdc();
        }
    }
    
    // ========== 辅助方法 ==========
    
    private void startHeartbeat() {
        if (heartbeatScheduler == null) {
            heartbeatScheduler = new HeartbeatScheduler(
                task, dependencies.getTechnicalEventPublisher(), 
                progressIntervalSeconds, dependencies.getMetrics()
            );
        }
        if (!heartbeatScheduler.isRunning()) {
            heartbeatScheduler.start();
        }
    }
    
    private void cleanup(TaskResult result) {
        stopHeartbeat();
        
        if (result.getFinalStatus().isTerminal()) {
            releaseTenantLock();
            dependencies.getCheckpointService().clearCheckpoint(task);
        }
    }
    
    private void stopHeartbeat() { /* ... */ }
    private void releaseTenantLock() { /* ... */ }
    private void handleException(Exception e) { /* ... */ }
}
```

---

## 🔧 策略的公共逻辑提取

### AbstractExecutionStrategy 抽象基类

```java
/**
 * 执行策略抽象基类
 * <p>
 * 提供公共的执行逻辑方法，避免代码重复
 */
public abstract class AbstractExecutionStrategy implements ExecutionStrategy {
    
    /**
     * 执行正常 Stage 流程（公共方法）
     */
    protected TaskResult executeNormalStages(
        TaskAggregate task,
        TaskRuntimeContext context,
        List<TaskStage> stages,
        ExecutionDependencies deps,
        int startIndex
    ) {
        TaskId taskId = task.getTaskId();
        LocalDateTime startTime = LocalDateTime.now();
        List<StageResult> completedStages = new ArrayList<>();
        
        // Stage 循环执行
        for (int i = startIndex; i < stages.size(); i++) {
            TaskStage stage = stages.get(i);
            String stageName = stage.getName();
            boolean isLastStage = (i == stages.size() - 1);
            
            // 开始 Stage
            deps.getTaskDomainService().startStage(task, stageName, stage.getSteps().size());
            
            // 执行 Stage
            StageResult result = stage.execute(context);
            
            if (result.isSuccess()) {
                // Stage 成功
                handleStageSuccess(task, context, stageName, result, i, isLastStage, deps, completedStages);
            } else {
                // Stage 失败
                return handleStageFailure(task, context, result, deps, completedStages, startTime);
            }
            
            // 检查暂停/取消请求
            TaskResult pauseOrCancelResult = checkPauseOrCancel(task, context, deps, completedStages, startTime);
            if (pauseOrCancelResult != null) {
                return pauseOrCancelResult;
            }
        }
        
        // 所有 Stage 完成，完成任务
        return completeTask(task, context, deps, completedStages, startTime);
    }
    
    /**
     * 处理 Stage 成功
     */
    protected void handleStageSuccess(
        TaskAggregate task,
        TaskRuntimeContext context,
        String stageName,
        StageResult result,
        int stageIndex,
        boolean isLastStage,
        ExecutionDependencies deps,
        List<StageResult> completedStages
    ) {
        // 完成 Stage
        deps.getTaskDomainService().completeStage(task, stageName, result.getDuration(), context);
        completedStages.add(result);
        
        // ✅ 只有非最后一个 Stage 才保存检查点
        if (!isLastStage) {
            List<String> stageNames = completedStages.stream()
                .map(StageResult::getStageName)
                .toList();
            deps.getCheckpointService().saveCheckpoint(task, stageNames, stageIndex);
        }
    }
    
    /**
     * 处理 Stage 失败
     */
    protected TaskResult handleStageFailure(
        TaskAggregate task,
        TaskRuntimeContext context,
        StageResult result,
        ExecutionDependencies deps,
        List<StageResult> completedStages,
        LocalDateTime startTime
    ) {
        String stageName = result.getStageName();
        
        // 记录 Stage 失败
        deps.getTaskDomainService().failStage(task, stageName, result.getFailureInfo());
        
        // 标记 Task 失败
        if (deps.getStateTransitionService().canTransition(task, TaskStatus.FAILED, context)) {
            deps.getTaskDomainService().failTask(task, result.getFailureInfo(), context);
        }
        
        deps.getMetrics().incrementCounter("task_failed");
        
        return TaskResult.fail(
            task.getPlanId(), task.getTaskId(), task.getStatus(),
            result.getFailureInfo().getErrorMessage(),
            Duration.between(startTime, LocalDateTime.now()),
            completedStages
        );
    }
    
    /**
     * 检查暂停/取消请求
     */
    protected TaskResult checkPauseOrCancel(
        TaskAggregate task,
        TaskRuntimeContext context,
        ExecutionDependencies deps,
        List<StageResult> completedStages,
        LocalDateTime startTime
    ) {
        // 检查暂停
        if (context.isPauseRequested()) {
            if (deps.getStateTransitionService().canTransition(task, TaskStatus.PAUSED, context)) {
                deps.getTaskDomainService().pauseTask(task, context);
                deps.getMetrics().incrementCounter("task_paused");
                
                return TaskResult.ok(
                    task.getPlanId(), task.getTaskId(), task.getStatus(),
                    Duration.between(startTime, LocalDateTime.now()),
                    completedStages
                );
            }
        }
        
        // 检查取消
        if (context.isCancelRequested()) {
            if (deps.getStateTransitionService().canTransition(task, TaskStatus.CANCELLED, context)) {
                deps.getTaskDomainService().cancelTask(task, "用户取消", context);
                deps.getMetrics().incrementCounter("task_cancelled");
                
                return TaskResult.ok(
                    task.getPlanId(), task.getTaskId(), task.getStatus(),
                    Duration.between(startTime, LocalDateTime.now()),
                    completedStages
                );
            }
        }
        
        return null;  // 无暂停/取消
    }
    
    /**
     * 完成任务
     */
    protected TaskResult completeTask(
        TaskAggregate task,
        TaskRuntimeContext context,
        ExecutionDependencies deps,
        List<StageResult> completedStages,
        LocalDateTime startTime
    ) {
        // ✅ 显式完成任务
        if (deps.getStateTransitionService().canTransition(task, TaskStatus.COMPLETED, context)) {
            deps.getTaskDomainService().completeTask(task, context);
        }
        
        deps.getMetrics().incrementCounter("task_completed");
        
        return TaskResult.ok(
            task.getPlanId(), task.getTaskId(), task.getStatus(),
            Duration.between(startTime, LocalDateTime.now()),
            completedStages
        );
    }
}
```

---

## 📊 优化效果对比

### 重构前：execute() 膨胀

```java
public TaskResult execute() {
    // 1. 状态检查和转换（50+ 行）
    if (status == PENDING) { ... }
    else if (status == PAUSED) { ... }
    else if (status == FAILED && context.isRetryRequested()) { ... }
    else if (status == FAILED && context.isRollbackRequested()) { ... }
    
    // 2. 执行逻辑（200+ 行）
    for (Stage stage : stages) { ... }
    
    // 3. 暂停/取消检查（50+ 行）
    if (context.isPauseRequested()) { ... }
    
    // 4. 完成逻辑（30+ 行）
    ...
    
    // 总计：300+ 行，难以维护
}
```

### 重构后：简洁的 execute()

```java
public TaskResult execute() {
    // 30 行，简洁明了
    try {
        startHeartbeat();
        
        // ✅ 委托给策略链
        TaskResult result = strategyChain.selectAndExecute(
            task, context, stages, dependencies
        );
        
        cleanup(result);
        return result;
        
    } catch (Exception e) {
        handleException(e);
        return TaskResult.fail(...);
    }
}
```

### 策略类：职责单一

每个策略类只处理一种场景，代码量：
- `StartStrategy`：~80 行
- `ResumeStrategy`：~90 行
- `RetryStrategy`：~100 行
- `RollbackStrategy`：~120 行
- `ContinueStrategy`：~70 行

**总计**：460 行，但分散在 5 个独立文件中，易于理解和维护

---

## ✅ 优势总结

### 1. 职责分离
- ✅ TaskExecutor：统一入口 + 资源管理
- ✅ ExecutionStrategy：具体执行逻辑
- ✅ AbstractExecutionStrategy：公共逻辑复用

### 2. 易于扩展
新增执行模式只需：
1. 实现 `ExecutionStrategy` 接口
2. 注册到策略链
3. 无需修改 TaskExecutor

### 3. 易于测试
- 每个策略可以独立测试
- Mock 依赖注入对象即可

### 4. 语义清晰
- 策略名称直接表达意图
- 代码即文档

---

## 🚀 实施步骤

### 第一步：创建接口和基础类
1. `ExecutionStrategy` 接口
2. `AbstractExecutionStrategy` 抽象基类
3. `ExecutionDependencies` 依赖对象
4. `ExecutionStrategyChain` 责任链

### 第二步：实现具体策略
1. `StartStrategy`
2. `ResumeStrategy`
3. `RetryStrategy`
4. `RollbackStrategy`
5. `ContinueStrategy`

### 第三步：重构 TaskExecutor
1. 简化 `execute()` 方法
2. 移除 `retry()` 和 `rollback()` 方法
3. 注入策略链

### 第四步：修改 TaskWorkerFactory
1. 创建策略链
2. 注入到 TaskExecutor

### 第五步：更新测试
1. 测试策略选择逻辑
2. 测试每个策略的执行逻辑
3. 集成测试

---

## 🎯 最终效果

### 语义化请求驱动

```java
// 重试
context.requestRetry(fromCheckpoint);
executor.execute();  // → RetryStrategy 匹配 → 执行重试

// 回滚
context.requestRollback(version);
executor.execute();  // → RollbackStrategy 匹配 → 执行回滚

// 暂停
context.requestPause();
executor.execute();  // → 在 executeNormalStages() 中检查
```

### 统一的状态转换

所有状态转换都在策略的 `execute()` 方法中处理：
- StartStrategy → startTask()
- ResumeStrategy → resumeTask()
- RetryStrategy → retryTask()
- RollbackStrategy → startRollback()

### 避免 execute() 膨胀

- TaskExecutor.execute()：30 行（入口）
- 策略类：平均 90 行（具体逻辑）
- 公共基类：200 行（复用逻辑）

**总计**：代码量增加，但结构清晰，易于维护和扩展

---

**完美的策略模式设计！** 🎨

