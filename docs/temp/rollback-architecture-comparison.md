# 回滚架构对比：初版 vs 修正版

**目的**：说明为什么修正后的方案更符合现有架构

---

## 1. 初版设计（未对齐现有架构）

```
[应用层] TaskOperationService
  ↓ 新增方法
rollbackTask(taskId)
  ├─ 验证前置条件
  ├─ 从 task 获取 prevConfigSnapshot
  ├─ 转换为 TenantConfig
  ├─ 调用 stageFactory.buildStages(rollbackConfig)  ← 在应用层装配
  ├─ 构造 rollbackCtx                                ← 在应用层装填
  ├─ 创建 TaskExecutor(rollbackStages, rollbackCtx)
  └─ 异步执行 executor.execute()
```

**问题**：
- ❌ 应用层承担了太多职责（装配 Stages、装填 Context）
- ❌ 需要注入 StageFactory 到 TaskOperationService（违反分层）
- ❌ 与现有的 `retryTaskByTenant` 模式不一致
- ❌ 重复了 `prepareRetryByTenant` 的逻辑

---

## 2. 修正版设计（对齐现有架构）

```
[应用层] TaskOperationService
  ↓ 复用现有方法
rollbackTaskByTenant(tenantId)  ← 已存在！
  ├─ 调用 taskDomainService.prepareRollbackByTenant(tenantId)
  ├─ 获取 TaskWorkerCreationContext
  ├─ 使用 taskWorkerFactory.create(context)
  └─ 异步执行 executor.invokeRollback()

[领域层] TaskDomainService
  ↓ 修改现有方法
prepareRollbackByTenant(tenantId)  ← 已存在，需要改进！
  ├─ 查找 task
  ├─ 验证前置条件（状态、prevConfigSnapshot）
  ├─ 从 prevConfigSnapshot 转换为 TenantConfig        ← 在领域层装配
  ├─ 调用 stageFactory.buildStages(rollbackConfig)  ← 领域层有权限
  ├─ 构造 rollbackCtx（装填旧配置数据）             ← 领域层职责
  └─ 返回 TaskWorkerCreationContext                  ← 复用现有模式
```

**优势**：
- ✅ 应用层无需改动（rollbackTaskByTenant 已存在）
- ✅ 领域层负责准备（与 prepareRetryByTenant 一致）
- ✅ StageFactory 注入到 TaskDomainService（合理的依赖关系）
- ✅ 完全复用 TaskWorkerCreationContext 和 TaskWorkerFactory

---

## 3. 与 createTask 流程对比

### 3.1 正向发布：createTask

```
[应用层] DeploymentPlanCreator
  ↓
createAndLinkTask(TenantConfig config)  ← 外部传入配置
  ↓
[领域层] TaskDomainService.createTask(planId, config)
  ├─ 创建 TaskAggregate
  ├─ 设置 prevConfigSnapshot（从 config.previousConfig）
  └─ 保存 Task

[应用层] DeploymentPlanCreator (继续)
  ↓
buildStagesForTask(task, config)
  ├─ 调用 stageFactory.buildStages(config)         ← 使用新配置
  └─ 返回 Stages

  ↓
taskDomainService.attacheStages(task, stages)
  └─ 保存 Stages 到 RuntimeRepository

  ↓
创建 TaskRuntimeContext
  ├─ 装填新配置数据（version=20）                  ← 新配置数据
  └─ 保存 Context 到 RuntimeRepository

[编排层] TaskExecutionOrchestrator
  ↓
orchestrate(planId, tasks, executor::execute)
  └─ 异步执行所有 Task
```

### 3.2 回滚：prepareRollbackByTenant

```
[应用层] TaskOperationService
  ↓
rollbackTaskByTenant(tenantId)
  ↓
[领域层] TaskDomainService.prepareRollbackByTenant(tenantId)
  ├─ 查找 Task
  ├─ 获取 prevConfigSnapshot                        ← 从已有 Task 读取
  ├─ 转换为 TenantConfig (rollbackConfig)
  ├─ 调用 stageFactory.buildStages(rollbackConfig) ← 使用旧配置
  ├─ 创建 TaskRuntimeContext
  │   └─ 装填旧配置数据（version=19）               ← 旧配置数据
  └─ 返回 TaskWorkerCreationContext
      ├─ task
      ├─ stages (使用旧配置装配)
      ├─ runtimeContext (装填旧配置)
      └─ existingExecutor: null

[应用层] TaskOperationService (继续)
  ↓
使用 taskWorkerFactory.create(context) 创建 TaskExecutor
  ↓
异步执行 executor.invokeRollback()
  └─ 内部调用 executor.execute()
```

**对比结论**：

| 步骤 | 正向发布 | 回滚 | 关键区别 |
|------|---------|------|---------|
| **配置来源** | 外部传入 TenantConfig | 从 prevConfigSnapshot 转换 | 数据来源不同 |
| **装配 Stages** | stageFactory.buildStages(config) | stageFactory.buildStages(rollbackConfig) | 配置参数不同 |
| **装填 Context** | version=20（新） | version=19（旧） | 数据值不同 |
| **执行方式** | executor.execute() | executor.execute() | **完全相同** |

**核心洞察**：回滚 = 用不同的配置数据重走相同的流程

---

## 4. 为什么要复用 prepareRollbackByTenant？

### 4.1 现有模式：retry

```java
// TaskOperationService
public TaskOperationResult retryTaskByTenant(TenantId tenantId, boolean fromCheckpoint) {
    // 1. 领域层准备
    TaskWorkerCreationContext context = taskDomainService.prepareRetryByTenant(tenantId, fromCheckpoint);
    
    // 2. 创建 Executor
    TaskExecutor executor = taskWorkerFactory.create(context);
    
    // 3. 异步执行
    CompletableFuture.runAsync(() -> executor.retry(fromCheckpoint));
}
```

```java
// TaskDomainService
public TaskWorkerCreationContext prepareRetryByTenant(TenantId tenantId, boolean fromCheckpoint) {
    TaskAggregate task = findTaskByTenantId(tenantId);
    
    // 获取现有的运行时数据（复用）
    List<TaskStage> stages = taskRuntimeRepository.getStages(task.getTaskId()).orElseGet(List::of);
    TaskRuntimeContext ctx = taskRuntimeRepository.getContext(task.getTaskId()).orElse(null);
    TaskExecutor executor = taskRuntimeRepository.getExecutor(task.getTaskId()).orElse(null);
    
    // 返回执行上下文
    return TaskWorkerCreationContext.builder()
        .planId(task.getPlanId())
        .task(task)
        .stages(stages)              // ← 复用原有 Stages
        .runtimeContext(ctx)         // ← 复用原有 Context
        .existingExecutor(executor)  // ← 复用原有 Executor
        .build();
}
```

### 4.2 修正后的模式：rollback

```java
// TaskOperationService（无需修改！）
public TaskOperationResult rollbackTaskByTenant(TenantId tenantId) {
    // 1. 领域层准备
    TaskWorkerCreationContext context = taskDomainService.prepareRollbackByTenant(tenantId);
    
    // 2. 创建 Executor
    TaskExecutor executor = taskWorkerFactory.create(context);
    
    // 3. 异步执行
    CompletableFuture.runAsync(() -> executor.invokeRollback());
}
```

```java
// TaskDomainService（需要改进！）
public TaskWorkerCreationContext prepareRollbackByTenant(TenantId tenantId) {
    TaskAggregate task = findTaskByTenantId(tenantId);
    TenantDeployConfigSnapshot prevSnap = task.getPrevConfigSnapshot();
    
    // ✅ 关键改进：用旧配置重新装配（而不是复用原有数据）
    TenantConfig rollbackConfig = convertSnapshotToConfig(prevSnap, task);
    List<TaskStage> rollbackStages = stageFactory.buildStages(rollbackConfig);  // ← 重新装配
    TaskRuntimeContext rollbackCtx = buildRollbackContext(task, prevSnap);     // ← 重新装填
    
    // 返回执行上下文（结构与 retry 一致）
    return TaskWorkerCreationContext.builder()
        .planId(task.getPlanId())
        .task(task)
        .stages(rollbackStages)      // ← 用旧配置装配的 Stages
        .runtimeContext(rollbackCtx) // ← 装填旧配置数据的 Context
        .existingExecutor(null)      // ← 不复用 Executor
        .build();
}
```

**对比总结**：

| 方法 | 配置来源 | Stages | RuntimeContext | Executor | 执行方式 |
|------|---------|--------|---------------|---------|---------|
| **prepareRetryByTenant** | 复用原配置 | 从仓储读取 | 从仓储读取 | 可复用 | retry() |
| **prepareRollbackByTenant** | 从 prevSnapshot 转换 | 重新装配 | 重新装填 | 不复用 | invokeRollback() → execute() |

**关键点**：
- ✅ 两者都返回 `TaskWorkerCreationContext`（结构统一）
- ✅ 两者都在领域层准备数据（职责统一）
- ✅ 两者都复用 `TaskWorkerFactory`（创建方式统一）
- ✅ 应用层代码完全一致（只调用不同的 prepare 方法）

---

## 5. 为什么 StageFactory 应该注入到 TaskDomainService？

### 5.1 依赖关系分析

**当前架构**：
```
[应用层]
  DeploymentPlanCreator
    ├─ 依赖 TaskDomainService
    ├─ 依赖 StageFactory         ← 应用层直接使用
    └─ 职责：编排创建流程

[领域层]
  TaskDomainService
    ├─ 依赖 TaskRepository
    ├─ 依赖 TaskRuntimeRepository
    └─ 职责：Task 生命周期管理
```

**问题**：
- DeploymentPlanCreator 既依赖领域层（TaskDomainService），又依赖基础设施层（StageFactory）
- 职责混乱：应用层需要知道如何装配 Stages

**修正后架构**：
```
[应用层]
  DeploymentPlanCreator
    ├─ 依赖 TaskDomainService     ← 只依赖领域层
    └─ 职责：编排创建流程

  TaskOperationService
    ├─ 依赖 TaskDomainService     ← 只依赖领域层
    └─ 职责：执行单个 Task 操作

[领域层]
  TaskDomainService
    ├─ 依赖 TaskRepository
    ├─ 依赖 TaskRuntimeRepository
    ├─ 依赖 StageFactory           ← 领域服务依赖基础设施
    └─ 职责：Task 生命周期管理 + 运行时数据准备
```

**优势**：
- ✅ 应用层只依赖领域层（符合分层架构）
- ✅ 领域服务统一负责运行时数据准备（Stages + Context）
- ✅ 基础设施依赖集中在领域层（便于测试和替换）

### 5.2 与 DeploymentPlanCreator 对比

**DeploymentPlanCreator（正向发布）**：
```java
@Component
public class DeploymentPlanCreator {
    private final TaskDomainService taskDomainService;
    private final StageFactory stageFactory;  // ← 应用层依赖 StageFactory
    
    private TaskAggregate createAndLinkTask(TenantConfig config) {
        // 1. 创建 Task（领域层）
        TaskAggregate task = taskDomainService.createTask(planId, config);
        
        // 2. 装配 Stages（应用层）← 应用层承担基础设施职责
        List<TaskStage> stages = stageFactory.buildStages(config);
        taskDomainService.attacheStages(task, stages);
        
        // 3. 装填 Context（应用层）← 应用层承担基础设施职责
        TaskRuntimeContext runtimeContext = new TaskRuntimeContext(...);
        runtimeContext.addVariable("planVersion", config.getPlanVersion());
        taskRuntimeRepository.saveContext(task.getTaskId(), runtimeContext);
    }
}
```

**问题**：应用层需要知道如何装配 Stages 和装填 Context

**TaskOperationService（修正后回滚）**：
```java
@Component
public class TaskOperationService {
    private final TaskDomainService taskDomainService;
    // ❌ 不需要 StageFactory（由领域层负责）
    
    public TaskOperationResult rollbackTaskByTenant(TenantId tenantId) {
        // 1. 领域层准备所有运行时数据（Stages + Context）
        TaskWorkerCreationContext context = taskDomainService.prepareRollbackByTenant(tenantId);
        
        // 2. 创建和执行（应用层只负责编排）
        TaskExecutor executor = taskWorkerFactory.create(context);
        CompletableFuture.runAsync(() -> executor.invokeRollback());
    }
}
```

**优势**：应用层只负责编排，不关心如何准备运行时数据

**建议**：未来可以重构 `DeploymentPlanCreator`，将 Stage 装配和 Context 装填移到领域层

---

## 6. 总结

### 修正前 vs 修正后

| 维度 | 修正前（初版设计） | 修正后（对齐架构） |
|------|------------------|------------------|
| **应用层** | 新增 rollbackTask() | 复用 rollbackTaskByTenant() |
| **领域层** | 新增多个方法 | 只改进 prepareRollbackByTenant() |
| **依赖注入** | StageFactory → TaskOperationService | StageFactory → TaskDomainService |
| **职责划分** | 应用层装配 Stages + Context | 领域层装配 Stages + Context |
| **一致性** | 与 retry 模式不一致 | 与 retry 模式完全一致 |
| **改动量** | 大（多个新方法） | 小（只改一个方法） |

### 核心价值

1. ✅ **复用现有模式**：prepareRollbackByTenant 已存在，只需改进实现
2. ✅ **应用层无改动**：rollbackTaskByTenant 已存在且正确
3. ✅ **职责更清晰**：领域层负责准备，应用层负责编排
4. ✅ **架构更一致**：与 retry 模式保持一致
5. ✅ **改动最小化**：只需修改 TaskDomainService

### 实施建议

**优先级 P0（必须）**：
1. 修复配置传递（createTask 设置 prevConfigSnapshot）
2. 注入 StageFactory 到 TaskDomainService
3. 改进 prepareRollbackByTenant 实现

**优先级 P1（建议）**：
4. 确认 TaskExecutor.invokeRollback() 调用 execute()
5. 删除 Stage.rollback() 接口（如果存在）

**优先级 P2（可选）**：
6. 重构 DeploymentPlanCreator（将 Stage 装配移到领域层）
7. 统一"准备模式"（create/retry/rollback 都使用相同的模式）

---

**结论**：修正后的设计完全对齐现有架构，改动最小，价值最大！

