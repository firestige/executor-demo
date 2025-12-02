# T-035 Facade 集成设计：DeploymentTaskFacade → TaskExecutor

> **设计日期**: 2025-12-02  
> **设计状态**: 讨论中  
> **相关任务**: T-035 无状态执行器重构  
> **更新记录**: 
> - 2025-12-02 14:30: 初稿完成，提交问题 1-5
> - 2025-12-02 15:00: 用户明确 - caller 监听事件，保证传参，无需 TaskStateProjection
> - 2025-12-02 15:15: 简化设计 - 查询走内存（TaskRuntimeRepository），不破坏现有数据流

---

## 📋 设计目标

在 T-035 无状态执行器改造完成后，设计从 DeploymentTaskFacade 到 TaskExecutor 的完整调用链路，确保符合 DDD 战略和战术设计原则。

### 核心改造点

1. **Facade API 不变**：DeploymentTaskFacade 的公共 API 保持稳定（对外契约）
2. **内部流程重构**：调用链路适配 TaskRecoveryService（无状态恢复）
3. **事件驱动状态管理**：Caller 通过监听领域事件来持久化任务状态
4. **分层职责清晰**：严格遵循 Facade → Application → Domain → Infrastructure 分层
5. **最小改动原则**：不引入新组件（TaskStateManager/TaskStateProjection），保持现有数据流

---

## 🎯 核心问题分析

### 1. T-035 前后对比

| 维度 | T-035 前（Checkpoint 模式） | T-035 后（无状态模式） |
|------|---------------------------|----------------------|
| **状态持久化** | 执行器内部通过 CheckpointService 自动保存 | 执行器不保存，Caller 监听事件自行持久化 |
| **恢复机制** | ExecutionPreparer 从 Checkpoint 恢复 | TaskRecoveryService 从 Caller 提供的数据重建 Task |
| **Retry/Rollback** | TaskOperationService 调用 prepareRetry/prepareRollback | TaskRecoveryService 调用 recoverForRetry/recoverForRollback |
| **状态查询** | TaskQueryService 查询 Projection | **TaskDomainService 查询 TaskRepository + TaskRuntimeRepository（内存）** |
| **Caller 职责** | 无（被动接收事件） | **主动监听事件，持久化状态，传递恢复参数** |

### 2. Facade 当前 API（保持不变）

```java
// 创建切换任务（正常执行）
void createSwitchTask(List<TenantDeployConfig> configs)

// 重试任务（从失败点继续）
void retryTask(TenantDeployConfig retryConfig, String taskId, String lastCompletedStageName)

// 回滚任务（用旧配置回退）
void rollbackTask(TenantDeployConfig rollbackConfig, String taskId, String lastCompletedStageName, Long version)

// 查询任务状态
TaskStatusInfo queryTaskStatus(String taskId)
TaskStatusInfo queryTaskStatusByTenant(String tenantId)
```

### 3. 需要解决的核心问题

| 问题 | 说明 | 设计方案 |
|------|------|---------|
| **Q1: Caller 是谁？** | 谁负责监听事件并持久化状态？ | **外部调用方**（REST API 层或上层系统），保证传参正确 |
| **Q2: 状态存储在哪？** | lastCompletedStageName 保存在什么地方？ | **Caller 自行管理**，执行器不关心 |
| **Q3: 如何触发恢复？** | retryTask/rollbackTask 如何找到恢复数据？ | **Caller 提供完整参数**（lastCompletedStageName），不需要查询 |
| **Q4: 首次执行路径？** | createSwitchTask 是否需要状态管理？ | **不涉及改造**，保持原有流程 |
| **Q5: 查询接口实现？** | queryTaskStatus 查询什么？ | **查询内存状态**（TaskRepository + TaskRuntimeRepository） |

---

## 🏗️ 架构设计（简化版）

### 1. 分层视图（DDD Layering）

```
┌─────────────────────────────────────────────────────────────────┐
│ Facade Layer                                                    │
│  DeploymentTaskFacade                                           │
│   ├─ createSwitchTask()    → PlanLifecycleService (不改)       │
│   ├─ retryTask()           → TaskOperationService (改)         │
│   ├─ rollbackTask()        → TaskOperationService (改)         │
│   └─ queryTaskStatus()     → TaskOperationService (不改)       │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ Application Layer                                               │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ TaskOperationService (需要重构)                             │ │
│  │  ├─ retryTask(config, taskId, lastCompletedStageName)      │ │
│  │  │   → 调用 TaskRecoveryService.recoverForRetry()         │ │
│  │  │   → 保存 Task → 创建 Executor → 异步执行                │ │
│  │  ├─ rollbackTask(oldConfig, taskId, lastCompleted, ver)   │ │
│  │  │   → 调用 TaskRecoveryService.recoverForRollback()      │ │
│  │  └─ queryTaskStatus(taskId)                                │ │
│  │       → 调用 TaskDomainService.queryTaskStatus() (不改)   │ │
│  └────────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ TaskRecoveryService (已实现 - T-035)                        │ │
│  │  ├─ recoverForRetry(taskId, config, lastCompletedStage)   │ │
│  │  └─ recoverForRollback(taskId, oldConfig, lastCompleted)  │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ Domain Layer                                                    │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ TaskDomainService                                          │ │
│  │  ├─ queryTaskStatus(taskId) → 查询内存状态 (不改)          │ │
│  │  ├─ startTask()                                            │ │
│  │  └─ completeStage() → 发布 TaskStageCompletedEvent        │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ Infrastructure Layer                                            │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ TaskExecutor (已重构 - T-035)                               │ │
│  │  └─ execute() → 无状态执行，发布事件                       │ │
│  └────────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ StageFactory (已实现 - T-035)                               │ │
│  │  ├─ buildStages(config)                                    │ │
│  │  └─ calculateStartIndex(config, lastCompletedStageName)   │ │
│  └────────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ TaskRepository / TaskRuntimeRepository (不改)              │ │
│  │  └─ 查询内存中的 Task 和运行时状态                         │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

**核心变化**：
- ❌ **不引入** TaskStateManager / TaskStateProjection
- ✅ **保留** 现有的 TaskDomainService.queryTaskStatus()（查询内存）
- ✅ **重构** TaskOperationService.retryTask() / rollbackTask()
- ✅ **集成** TaskRecoveryService（已实现）

### 2. 核心组件职责（简化版）

#### 2.1 TaskOperationService（需要重构）

**当前问题**：
```java
// Line 163: 方法签名错误
public TaskOperationResult retryTaskByTenant(TenantConfig config, String takId, String lastCompletedStageName)
//                                                                      ^^^^^ 拼写错误：takId → taskId

// Line 167-170: 调用了已删除的方法
TaskWorkerCreationContext context = taskDomainService.prepareRetryByTenant(tenantId, fromCheckpoint);
// prepareRetryByTenant() 和 prepareRollbackByTenant() 已在 T-035 中删除
```

**重构方案**（保持现有数据流）：
```java
/**
 * 重试任务（重构）
 * T-035: 使用 TaskRecoveryService 重建 Task
 */
@Transactional
public TaskOperationResult retryTask(
    TenantConfig config,
    String taskId,  // 修正拼写
    String lastCompletedStageName  // 由 Caller 提供
) {
    log.info("[TaskOperationService] 重试任务: taskId={}, from={}", taskId, lastCompletedStageName);
    
    TaskId tid = TaskId.of(taskId);
    
    // Step 1: 使用 TaskRecoveryService 重建 Task
    TaskAggregate recoveredTask = taskRecoveryService.recoverForRetry(
        tid,
        config,
        lastCompletedStageName
    );
    
    // Step 2: 保存重建的 Task
    taskRepository.save(recoveredTask);
    
    // Step 3: 构建 RuntimeContext 和 Stages
    TaskRuntimeContext runtimeContext = new TaskRuntimeContext(tid, config.getTenantId());
    List<TaskStage> stages = stageFactory.buildStages(config);
    
    // Step 4: 创建 TaskWorkerCreationContext
    TaskWorkerCreationContext context = TaskWorkerCreationContext.builder()
        .planId(config.getPlanId())
        .task(recoveredTask)
        .stages(stages)
        .runtimeContext(runtimeContext)
        .build();
    
    // Step 5: 创建 TaskExecutor 并异步执行
    TaskExecutor executor = taskWorkerFactory.create(context);
    
    CompletableFuture.runAsync(() -> {
        try {
            executor.execute();
        } catch (Exception e) {
            log.error("[TaskOperationService] 重试任务异常: {}", taskId, e);
        }
    });
    
    return TaskOperationResult.success(tid, recoveredTask.getStatus(), "重试任务已提交");
}

/**
 * 回滚任务（重构）
 * T-035: 使用 TaskRecoveryService 重建 Task
 */
@Transactional
public TaskOperationResult rollbackTask(
    TenantConfig oldConfig,  // 旧配置
    String taskId,
    String lastCompletedStageName,  // 由 Caller 提供
    Long version
) {
    log.info("[TaskOperationService] 回滚任务: taskId={}, version={}", taskId, version);
    
    TaskId tid = TaskId.of(taskId);
    
    // Step 1: 使用 TaskRecoveryService 重建 Task（回滚模式）
    TaskAggregate recoveredTask = taskRecoveryService.recoverForRollback(
        tid,
        oldConfig,
        lastCompletedStageName
    );
    
    // Step 2-5: 同 retryTask 逻辑
    taskRepository.save(recoveredTask);
    
    TaskRuntimeContext runtimeContext = new TaskRuntimeContext(tid, oldConfig.getTenantId());
    runtimeContext.requestRollback(version.toString());  // 设置回滚标志
    
    List<TaskStage> stages = stageFactory.buildStages(oldConfig);
    
    TaskWorkerCreationContext context = TaskWorkerCreationContext.builder()
        .planId(oldConfig.getPlanId())
        .task(recoveredTask)
        .stages(stages)
        .runtimeContext(runtimeContext)
        .build();
    
    TaskExecutor executor = taskWorkerFactory.create(context);
    
    CompletableFuture.runAsync(() -> {
        try {
            executor.execute();
        } catch (Exception e) {
            log.error("[TaskOperationService] 回滚任务异常: {}", taskId, e);
        }
    });
    
    return TaskOperationResult.success(tid, recoveredTask.getStatus(), "回滚任务已提交");
}
```

**关键变化**：
- 移除对 `prepareRetryByTenant()` / `prepareRollbackByTenant()` 的调用
- 直接调用 `TaskRecoveryService.recoverForRetry()` / `recoverForRollback()`
- 自行构建 `TaskWorkerCreationContext`（原本由 prepare 方法返回）
- 需要注入 `TaskRecoveryService` 和 `StageFactory` 依赖

#### 2.2 DeploymentTaskFacade（不需要改动）

**当前实现**（已经正确）：
```java
// retryTask - 参数已包含 lastCompletedStageName
public void retryTask(
    TenantDeployConfig retryConfig,
    String taskId,
    String lastCompletedStageName  // ✅ 已经由 Caller 提供
) {
    TenantConfig config = tenantConfigConverter.convert(retryConfig);
    TaskOperationResult result = taskOperationService.retryTask(config, taskId, lastCompletedStageName);
    handleTaskOperationResult(result, "重试任务");
}

// rollbackTask - 参数已包含 lastCompletedStageName
public void rollbackTask(
    TenantDeployConfig rollbackConfig,
    String taskId,
    String lastCompletedStageName,  // ✅ 已经由 Caller 提供
    Long version
) {
    TenantConfig config = tenantConfigConverter.convert(rollbackConfig);
    TaskOperationResult result = taskOperationService.rollbackTask(config, taskId, lastCompletedStageName, version);
    handleTaskOperationResult(result, "回滚任务");
}

// queryTaskStatus - 查询内存状态
public TaskStatusInfo queryTaskStatus(String taskId) {
    TaskStatusInfo result = taskOperationService.queryTaskStatus(TaskId.of(taskId));
    if (result.getStatus() == null) {
        throw new TaskNotFoundException("任务不存在: " + taskId);
    }
    return result;
}
```

**结论**：Facade 层**不需要改动**，因为：
- API 签名已包含 `lastCompletedStageName` 参数
- 查询方法已经通过 `TaskOperationService` → `TaskDomainService` 查询内存
- 只需修正 `TaskOperationService` 的实现

#### 2.3 TaskDomainService（不需要改动）

**当前实现**（已经正确）：
```java
public TaskStatusInfo queryTaskStatus(TaskId taskId) {
    // Step 1: 查询 TaskRepository（内存中的 Task 聚合）
    TaskAggregate task = taskRepository.findById(taskId).orElse(null);
    if (task == null) {
        return TaskStatusInfo.failure("任务不存在: " + taskId);
    }

    // Step 2: 计算进度（从 TaskRuntimeRepository 获取运行时信息）
    int completed = task.getCurrentStageIndex();
    List<TaskStage> stages = taskRuntimeRepository.getStages(taskId).orElse(null);
    int total = (stages != null) ? stages.size() : 0;
    double progress = total == 0 ? 0 : (completed * 100.0 / total);

    // Step 3: 获取当前阶段（从 TaskRuntimeRepository 获取 Executor）
    TaskExecutor exec = taskRuntimeRepository.getExecutor(taskId).orElse(null);
    String currentStage = exec != null ? exec.getCurrentStageName() : null;

    // Step 4: 获取运行时状态（从 TaskRuntimeRepository 获取 Context）
    TaskRuntimeContext ctx = taskRuntimeRepository.getContext(taskId).orElse(null);
    boolean paused = ctx != null && ctx.isPauseRequested();
    boolean cancelled = ctx != null && ctx.isCancelRequested();

    // Step 5: 构造状态信息
    TaskStatusInfo info = new TaskStatusInfo(taskId, task.getStatus());
    info.setMessage(String.format(
        "进度 %.2f%% (%d/%d), currentStage=%s, paused=%s, cancelled=%s",
        progress, completed, total, currentStage, paused, cancelled
    ));

    return info;
}
```

**结论**：TaskDomainService**不需要改动**，因为：
- 已经查询内存（TaskRepository + TaskRuntimeRepository）
- 返回的 TaskStatusInfo 包含完整的运行时状态
- 符合"查询内存"的设计要求

---

## 🔄 执行流程设计（简化版）

### 1. 首次执行流程（createSwitchTask - 不涉及改造）

```
┌─────────────┐
│ REST API    │ POST /deploy/switch
└──────┬──────┘
       │ List<TenantDeployConfig>
       ↓
┌─────────────────────────────────────────────┐
│ DeploymentTaskFacade.createSwitchTask()     │ (不改)
└──────┬──────────────────────────────────────┘
       │
       ↓
┌─────────────────────────────────────────────┐
│ PlanLifecycleService.createDeploymentPlan() │ (不改)
└──────┬──────────────────────────────────────┘
       │
       ↓
     正常执行流程（不涉及改造）
```

**说明**：首次执行路径完全不需要改动，保持原有流程。

### 2. 重试流程（retryTask）

```
┌─────────────┐
│ REST API    │ POST /deploy/retry
└──────┬──────┘
       │ {taskId, retryConfig, lastCompletedStageName}
       ↓
┌─────────────────────────────────────────────┐
│ DeploymentTaskFacade                        │
│  .retryTask(retryConfig, taskId, lastStage) │
└──────┬──────────────────────────────────────┘
       │
       ↓ [可选] 验证恢复数据
┌─────────────────────────────────────────────┐
│ TaskStateManager                            │
│  .getRecoveryData(taskId)                   │
│   └─ 返回 lastCompletedStageName            │
└─────────────────────────────────────────────┘
       │
       ↓
┌─────────────────────────────────────────────┐
│ TaskOperationService                        │
│  .retryTask(config, taskId, lastStage)      │
└──────┬──────────────────────────────────────┘
       │
       ↓
┌─────────────────────────────────────────────┐
│ TaskRecoveryService                         │
│  .recoverForRetry(taskId, config, lastStage)│
│   ├─ StageFactory.buildStages(config)       │
│   ├─ StageFactory.calculateStartIndex()     │
│   │    → 返回 lastCompletedIndex            │
│   ├─ TaskAggregate.createForRecovery()      │
│   │    → 创建新 Task（复用 taskId）         │
│   ├─ task.setStageProgress(index+1)         │
│   └─ task.setExecutionRange([index+1, end)) │
└──────┬──────────────────────────────────────┘
       │ recoveredTask
       ↓
┌─────────────────────────────────────────────┐
│ TaskRepository.save(recoveredTask)          │
└──────┬──────────────────────────────────────┘
       │
       ↓
┌─────────────────────────────────────────────┐
│ TaskWorkerFactory.create(context)           │
│  └─ 创建 TaskExecutor                       │
└──────┬──────────────────────────────────────┘
       │
       ↓
┌─────────────────────────────────────────────┐
│ TaskExecutor.execute() (异步)               │
│   └─ 从 index+1 开始执行 Stages              │
│       └─ 发布 TaskStageCompletedEvent       │
└─────────────────────────────────────────────┘
```

**关键点**：
- ✅ lastCompletedStageName 由 Caller（REST API）提供，不查询
- ✅ TaskRecoveryService 负责计算 startIndex 并重建 Task
- ✅ 重建的 Task 复用原 taskId（保持引用一致性）
- ✅ 需要注入 `StageFactory` 到 `TaskOperationService`

### 3. 回滚流程（rollbackTask - 类似 retry）

回滚流程与重试流程几乎相同，只有以下差异：
- 使用 `oldConfig`（旧配置）而不是新配置
- 调用 `TaskRecoveryService.recoverForRollback()` 而不是 `recoverForRetry()`
- 回滚范围：`[0, lastCompletedIndex+1]`（重新执行已完成的步骤）
- 设置 `runtimeContext.requestRollback(version)` 标志

### 4. 查询流程（queryTaskStatus - 不涉及改造）

```
┌─────────────┐
│ REST API    │ GET /deploy/task/{taskId}/status
└──────┬──────┘
       │
       ↓
┌─────────────────────────────────────────────┐
│ DeploymentTaskFacade.queryTaskStatus()      │ (不改)
└──────┬──────────────────────────────────────┘
       │
       ↓
┌─────────────────────────────────────────────┐
│ TaskOperationService.queryTaskStatus()      │ (不改)
└──────┬──────────────────────────────────────┘
       │
       ↓
┌─────────────────────────────────────────────┐
│ TaskDomainService.queryTaskStatus()         │ (不改)
│  ├─ TaskRepository.findById(taskId)         │
│  ├─ TaskRuntimeRepository.getStages()       │
│  ├─ TaskRuntimeRepository.getExecutor()     │
│  └─ TaskRuntimeRepository.getContext()      │
└──────┬──────────────────────────────────────┘
       │ TaskStatusInfo (内存状态)
       ↓
      返回
```

**说明**：查询路径完全不需要改动，已经是查询内存状态。

---

## 📦 实现清单（简化版）

### Phase 1: TaskOperationService 重构（核心改动）

#### 1.1 修改方法签名和实现
- [ ] 修改 `retryTask()` 方法
  - 修正参数：`takId` → `taskId`
  - 移除对 `prepareRetryByTenant()` 的调用
  - 改为调用 `taskRecoveryService.recoverForRetry()`
  - 自行构建 `TaskWorkerCreationContext`
  - 保存 Task → 创建 Executor → 异步执行

- [ ] 修改 `rollbackTask()` 方法
  - 移除对 `prepareRollbackByTenant()` 的调用
  - 改为调用 `taskRecoveryService.recoverForRollback()`
  - 设置 `runtimeContext.requestRollback(version)` 标志
  - 自行构建 `TaskWorkerCreationContext`
  - 保存 Task → 创建 Executor → 异步执行

#### 1.2 依赖注入更新
- [ ] 添加 `TaskRecoveryService` 依赖（已实现）
- [ ] 添加 `StageFactory` 依赖（用于 buildStages）
- [ ] 确认已有依赖：`TaskRepository`, `TaskRuntimeRepository`, `TaskWorkerFactory`

**文件位置**：
- `deploy/src/main/java/xyz/firestige/deploy/application/task/TaskOperationService.java`

**预期结果**：
- `retryTask()` 和 `rollbackTask()` 方法能够正确调用 TaskRecoveryService
- 编译通过，不再依赖已删除的 `prepareRetryByTenant()` / `prepareRollbackByTenant()`

---

### Phase 2: 配置更新

#### 2.1 Spring Bean 配置
- [ ] 检查 `ExecutorConfiguration.java`
  - 确认 `TaskRecoveryService` Bean 已注册
  - 确认 `StageFactory` Bean 已注册
  - 确认 `TaskOperationService` 的依赖注入正确

**文件位置**：
- `deploy/src/main/java/xyz/firestige/deploy/infrastructure/config/ExecutorConfiguration.java`

---

### Phase 3: 验证与测试

#### 3.1 编译验证
- [ ] 编译验证：`mvn clean compile -DskipTests`
- [ ] 检查是否有编译错误
- [ ] 确认所有依赖都能正确注入

#### 3.2 集成测试（可选）
- [ ] 编写 `TaskOperationServiceTest.java`
  - 测试 `retryTask()` 方法
  - 测试 `rollbackTask()` 方法
  - Mock TaskRecoveryService, StageFactory, TaskWorkerFactory
  - 验证方法调用链路

**文件位置**：
- `deploy/src/test/java/xyz/firestige/deploy/application/task/TaskOperationServiceTest.java`

---

### 改动汇总

| 文件 | 改动类型 | 说明 |
|------|---------|------|
| TaskOperationService.java | **重构** | 修改 retryTask/rollbackTask 实现 |
| ExecutorConfiguration.java | 检查 | 确认依赖注入配置 |
| DeploymentTaskFacade.java | **不改** | API 已符合要求 |
| TaskDomainService.java | **不改** | queryTaskStatus 已查询内存 |
| TaskRecoveryService.java | **不改** | 已实现，直接使用 |
| StageFactory.java | **不改** | 已实现 calculateStartIndex |

**总结**：
- ✅ **只需改动 1 个文件**：TaskOperationService.java
- ✅ **不引入新组件**：TaskStateManager / TaskStateProjection
- ✅ **不破坏现有流程**：保持调用链路和数据流
- ✅ **最小化改动**：符合用户要求

---

## 🔍 关键设计决策（简化版）

### 决策 1: 不引入 TaskStateManager

**原因**：
- Caller 外部监听事件并管理状态
- Caller 保证每次调用 Facade 时传递正确的参数（lastCompletedStageName）
- 执行器内部不需要维护状态投影

**结论**：不需要 TaskStateManager / TaskStateProjection

---

### 决策 2: 查询接口走内存

**原因**：
- TaskDomainService.queryTaskStatus() 已经查询内存（TaskRepository + TaskRuntimeRepository）
- 提供 Caller 主动拉取状态的机制
- 不需要额外的持久化查询

**结论**：保持现有实现，不需要改动

---

### 决策 3: StageFactory 注入到 TaskOperationService

**问题**：Application Layer 依赖 Infrastructure Layer 的 StageFactory

**解决方案**：依赖倒置（DIP）
- StageFactory 是接口（Infrastructure Layer 定义）
- Application Layer 依赖接口（符合 DIP 原则）
- 具体实现（OrchestratedStageFactory）由 Spring 注入

**结论**：合理的跨层依赖，符合 DDD 分层原则

---

### 决策 4: 保留 @Transactional

**原因**：
- `taskRepository.save(recoveredTask)` 需要事务保证
- 异步执行 `executor.execute()` 在事务外（符合预期）
- 失败回滚不影响 Task 创建

**结论**：保留 @Transactional 注解

---

## ❓ 待确认问题（简化版）

### ✅ 已确认

1. **Caller 职责**：外部调用方监听事件，保证传参正确 ✅
2. **TaskStateProjection**：不需要 ✅
3. **查询实现**：走内存（TaskRepository + TaskRuntimeRepository）✅
4. **createSwitchTask**：不涉及改造 ✅
5. **数据流**：不破坏现有调用链路 ✅

### ⚠️ 需要讨论

#### 问题 1: StageFactory 依赖注入方式

**当前情况**：
- StageFactory 接口在 Infrastructure Layer
- TaskOperationService 需要调用 `stageFactory.buildStages(config)`

**方案对比**：
| 方案 | 说明 | 优点 | 缺点 |
|------|------|------|------|
| A | 直接注入 StageFactory 接口 | 简单直接，符合 DIP | Application Layer 依赖 Infrastructure 接口 |
| B | 通过 TaskDomainService 封装 | 避免跨层依赖 | 增加一层间接调用 |
| C | 将 StageFactory 提升到 Domain Layer | 完全符合分层 | 大改动，StageFactory 依赖基础设施 |

**建议**：方案 A（直接注入接口）

**需要确认**：是否接受方案 A？

---

#### 问题 2: TaskRuntimeContext 创建方式

**当前代码**（TaskOperationService 需要创建）：
```java
TaskRuntimeContext runtimeContext = new TaskRuntimeContext(tid, config.getTenantId());
```

**问题**：
- TaskRuntimeContext 是 Domain Layer 的类
- Application Layer 直接 new 是否合适？

**方案对比**：
| 方案 | 说明 | 优点 | 缺点 |
|------|------|------|------|
| A | Application Layer 直接 new | 简单直接 | Application Layer 依赖 Domain 实现细节 |
| B | 通过 Factory 方法创建 | 封装创建逻辑 | 增加复杂度 |
| C | 通过 TaskDomainService 创建 | 符合分层 | 需要新增方法 |

**建议**：方案 A（直接 new）

**需要确认**：是否接受方案 A？

---

#### 问题 3: rollbackTask 的 version 参数处理

**当前代码**（TaskOperationService 需要设置）：
```java
runtimeContext.requestRollback(version.toString());  // version 是 Long 类型
```

**问题**：
- `requestRollback()` 方法期望什么类型？String 还是 Long？
- version 的语义是什么？是否需要验证？

**需要确认**：
1. `requestRollback()` 的参数类型是什么？
2. 是否需要在 Application Layer 验证 version 的有效性？

---

#### 问题 4: 异步执行的错误处理

**当前代码**：
```java
CompletableFuture.runAsync(() -> {
    try {
        executor.execute();
    } catch (Exception e) {
        log.error("[TaskOperationService] 重试任务异常: {}", taskId, e);
        // 异常被吞掉了，没有后续处理
    }
});
```

**问题**：
- 异步执行失败后，Task 状态可能不正确
- Caller 如何知道执行失败？（只能通过监听 TaskFailedEvent）

**方案对比**：
| 方案 | 说明 | 优点 | 缺点 |
|------|------|------|------|
| A | 只记录日志，依赖事件通知 | 简单，符合事件驱动 | 异常信息可能丢失 |
| B | 在 catch 中手动发布 TaskFailedEvent | 保证事件发布 | Application Layer 发布 Domain Event？ |
| C | 在 TaskExecutor 内部处理异常并发布事件 | 职责清晰 | 需要确认 TaskExecutor 是否已处理 |

**建议**：方案 C（确认 TaskExecutor.execute() 是否已处理异常）

**需要确认**：TaskExecutor.execute() 是否在内部 catch 异常并发布 TaskFailedEvent？

---

## ✅ 总结

### 核心变更
1. **重构 TaskOperationService**：修改 retryTask/rollbackTask 实现
2. **不引入新组件**：不需要 TaskStateManager / TaskStateProjection
3. **保持现有流程**：DeploymentTaskFacade 和 TaskDomainService 不改
4. **最小化改动**：只改动 1 个文件（TaskOperationService.java）

### 设计原则遵循
- ✅ **DDD 分层**：保持 Facade → Application → Domain → Infrastructure
- ✅ **无状态执行器**：TaskExecutor 不持久化，Caller 负责状态管理
- ✅ **事件驱动**：状态变化通过领域事件传播
- ✅ **最小改动**：不破坏现有数据流和调用链路

### 下一步行动
1. **确认 4 个待讨论问题**（StageFactory 注入、RuntimeContext 创建、version 参数、异步错误处理）
2. **确认后开始实现**
3. **编译验证**

---

**设计更新完成，等待您的反馈！** 🎉
