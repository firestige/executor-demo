# T-028: 回滚机制完善 - Task 级回滚设计方案

**任务ID**: T-028  
**优先级**: P1  
**开始日期**: 2025-11-26  
**预计完成**: 2025-11-29  
**状态**: ✅ 设计已核实，已对齐现有架构

**架构对齐**：
- ✅ 复用 `prepareRollbackByTenant` 模式（与 `prepareRetryByTenant` 一致）
- ✅ 复用 `TaskWorkerCreationContext`（与 retry 模式一致）
- ✅ `StageFactory` 注入到 `TaskDomainService`（符合分层架构）
- ✅ 应用层无需改动（`rollbackTaskByTenant` 已存在）

**参考文档**：[架构对比分析](./rollback-architecture-comparison.md)

---

## 1. 核心理念（修正）

### ✅ 正确理解

**回滚 = 用 previousConfig 创建/重置 Task，重新走一遍完整的发布流程**

```
正向发布：
  [Task] config=v20 → 装配 Stages → execute()

回滚：
  [Task] config=v19 (previousConfig) → 装配 Stages → execute()
  
  ↑ 完全相同的流程，只是配置数据不同
```

### ❌ 之前的误解

~~回滚是 Stage 级别的，需要实现 `stage.rollback()` 方法~~

**问题**：
- 增加复杂度（Stage 需要两套逻辑）
- 与正向流程不一致
- 维护成本高

### ✅ 新方案的优势

1. **架构简洁**：
   - 回滚复用正向的 `execute()` 流程
   - 无需修改 Stage、Step、DataPreparer
   - 配置驱动，数据不同即可

2. **逻辑统一**：
   - 回滚 = 重新发布（使用旧配置）
   - 符合业务语义
   - 易于理解和维护

3. **零代码改动**（几乎）：
   - Stage：不需要 `rollback()` 方法
   - Step：不需要修改
   - DataPreparer：不需要修改

---

## 2. 方案设计（修正：复用现有架构）

### 2.1 架构复用分析

**现有设计模式（retry/rollback）**：
```
TaskOperationService.retryTaskByTenant/rollbackTaskByTenant
  ↓ 调用
TaskDomainService.prepareRetryByTenant/prepareRollbackByTenant
  ↓ 返回
TaskWorkerCreationContext (包含 task, stages, runtimeContext, existingExecutor)
  ↓ 使用
TaskWorkerFactory.create(context) → TaskExecutor
  ↓ 异步执行
executor.retry() / executor.invokeRollback()
```

**关键发现**：
1. ✅ **已有 `prepareRollbackByTenant()`**：领域层准备方法
2. ✅ **已有 `TaskWorkerCreationContext`**：封装执行所需的所有数据
3. ✅ **已有 `TaskWorkerFactory`**：统一创建 TaskExecutor
4. ✅ **已有异步执行模式**：CompletableFuture.runAsync()

**问题**：
- ❌ `prepareRollbackByTenant()` 当前复用了原有的 stages（新配置）
- ❌ 没有用 previousConfig 重新装配 stages
- ❌ RuntimeContext 没有装填旧配置数据

### 2.2 修正方案：在 TaskDomainService 中准备回滚

**核心修改点**：`TaskDomainService.prepareRollbackByTenant()`

```java
/**
 * 准备回滚任务（修正：用 previousConfig 重新装配）
 * 
 * 关键改进：
 * 1. 从 prevConfigSnapshot 转换为 TenantConfig
 * 2. 用旧配置重新装配 Stages（通过 StageFactory）
 * 3. 用旧配置装填 RuntimeContext
 * 4. 返回 TaskWorkerCreationContext（与 retry 模式一致）
 *
 * @param tenantId 租户 ID
 * @return TaskWorkerCreationContext 包含回滚所需的数据，null 表示未找到任务
 */
public TaskWorkerCreationContext prepareRollbackByTenant(TenantId tenantId) {
    logger.info("[TaskDomainService] 准备回滚租户任务: {}", tenantId);

    // 1. 查找 Task
    TaskAggregate task = findTaskByTenantId(tenantId);
    if (task == null) {
        logger.warn("[TaskDomainService] 未找到租户任务: {}", tenantId);
        return null;
    }

    // 2. 验证前置条件
    if (!canRollback(task)) {
        logger.warn("[TaskDomainService] Task 状态不允许回滚: {}, status: {}", 
            task.getTaskId(), task.getStatus());
        return null;
    }

    // 3. 获取 previousConfig 快照
    TenantDeployConfigSnapshot prevSnap = task.getPrevConfigSnapshot();
    if (prevSnap == null) {
        logger.warn("[TaskDomainService] 无上一次配置快照，无法回滚: {}", task.getTaskId());
        return null;
    }

    // 4. 转换 snapshot → TenantConfig
    TenantConfig rollbackConfig = convertSnapshotToConfig(prevSnap, task);

    // 5. ✅ 用旧配置重新装配 Stages（关键点！）
    List<TaskStage> rollbackStages = stageFactory.buildStages(rollbackConfig);
    logger.info("[TaskDomainService] 用旧配置重新装配 Stages: {}, version: {}", 
        task.getTaskId(), prevSnap.getDeployUnitVersion());

    // 6. ✅ 用旧配置装填 RuntimeContext（关键点！）
    TaskRuntimeContext rollbackCtx = buildRollbackContext(task, prevSnap);
    logger.info("[TaskDomainService] 用旧配置装填 RuntimeContext: {}, version: {}", 
        task.getTaskId(), prevSnap.getDeployUnitVersion());

    // 7. 发布回滚开始事件（移到这里，因为状态还未改变）
    // 注意：实际状态转换在 TaskExecutor.invokeRollback() 中通过 startRollback() 完成

    // 8. 返回执行上下文（与 prepareRetryByTenant 一致）
    return TaskWorkerCreationContext.builder()
        .planId(task.getPlanId())
        .task(task)
        .stages(rollbackStages)         // ← 使用旧配置的 stages
        .runtimeContext(rollbackCtx)    // ← 使用旧配置的 context
        .existingExecutor(null)         // ← 回滚不复用 executor
        .build();
}

/**
 * 检查是否可以回滚
 */
private boolean canRollback(TaskAggregate task) {
    TaskStatus status = task.getStatus();
    return status == TaskStatus.FAILED 
        || status == TaskStatus.PAUSED
        || status == TaskStatus.ROLLBACK_FAILED;  // 允许重新回滚
}

/**
 * 转换 snapshot → TenantConfig（用于装配 Stages）
 */
private TenantConfig convertSnapshotToConfig(TenantDeployConfigSnapshot snapshot, TaskAggregate task) {
    TenantConfig config = new TenantConfig();
    config.setPlanId(task.getPlanId());
    config.setTenantId(task.getTenantId());
    
    // ✅ 设置旧版本的部署单元
    config.setDeployUnit(new DeployUnitIdentifier(
        snapshot.getDeployUnitId(),
        snapshot.getDeployUnitVersion(),
        snapshot.getDeployUnitName()
    ));
    
    // ✅ 设置健康检查端点
    config.setHealthCheckEndpoints(snapshot.getNetworkEndpoints());
    
    // 可选：设置其他必要字段（根据 StageFactory 的需求）
    // config.setPlanVersion(...);  // 如果需要
    
    logger.debug("[TaskDomainService] 转换 snapshot → TenantConfig: version={}, unit={}", 
        snapshot.getDeployUnitVersion(), snapshot.getDeployUnitName());
    
    return config;
}

/**
 * 构建回滚 RuntimeContext（装填旧配置数据）
 */
private TaskRuntimeContext buildRollbackContext(TaskAggregate task, TenantDeployConfigSnapshot snapshot) {
    TaskRuntimeContext ctx = new TaskRuntimeContext(
        task.getPlanId(),
        task.getTaskId(),
        task.getTenantId()
    );
    
    // ✅ 装填旧配置数据（DataPreparer 会从这里读取）
    ctx.addVariable("deployUnitVersion", snapshot.getDeployUnitVersion());
    ctx.addVariable("deployUnitId", snapshot.getDeployUnitId());
    ctx.addVariable("deployUnitName", snapshot.getDeployUnitName());
    ctx.addVariable("healthCheckEndpoints", snapshot.getNetworkEndpoints());
    
    // 可选：标记为回滚模式
    ctx.addVariable("isRollback", true);
    
    // 保留 planVersion（如果原 context 有的话）
    TaskRuntimeContext originalCtx = taskRuntimeRepository.getContext(task.getTaskId()).orElse(null);
    if (originalCtx != null && originalCtx.getAdditionalData("planVersion") != null) {
        ctx.addVariable("planVersion", originalCtx.getAdditionalData("planVersion"));
    }
    
    logger.debug("[TaskDomainService] 构建回滚 RuntimeContext: version={}", 
        snapshot.getDeployUnitVersion());
    
    return ctx;
}
```

**设计亮点**：
1. ✅ **完全复用现有模式**：与 `prepareRetryByTenant()` 结构一致
2. ✅ **职责清晰**：TaskDomainService 负责准备，TaskOperationService 负责执行
3. ✅ **依赖注入 StageFactory**：需要在 TaskDomainService 构造函数中注入
4. ✅ **零应用层改动**：`rollbackTaskByTenant()` 已经存在，只需修改领域层

### 2.3 TaskOperationService（无需修改）

**现有代码已经符合要求**：

```java
@Transactional
public TaskOperationResult rollbackTaskByTenant(TenantId tenantId) {
    logger.info("[TaskOperationService] 回滚租户任务（异步）: {}", tenantId);

    // ✅ Step 1: 调用领域服务准备回滚（已有）
    TaskWorkerCreationContext context = taskDomainService.prepareRollbackByTenant(tenantId);
    if (context == null) {
        return TaskOperationResult.failure(...);
    }

    // ✅ Step 2: 创建 TaskExecutor（已有）
    TaskExecutor executor = context.hasExistingExecutor()
        ? context.getExistingExecutor()
        : taskWorkerFactory.create(context);

    // ✅ Step 3: 异步执行回滚（已有）
    CompletableFuture.runAsync(() -> {
        try {
            var result = executor.invokeRollback();  // ← 调用现有方法
            logger.info("[TaskOperationService] 租户任务回滚完成: {}, status: {}",
                        tenantId, result.getFinalStatus());
        } catch (Exception e) {
            logger.error("[TaskOperationService] 租户任务回滚异常: {}", tenantId, e);
        }
    });

    return TaskOperationResult.success(...);
}
```

**无需修改！** 只需要改进 `prepareRollbackByTenant()` 的实现
```

### 2.4 领域层支持（已存在，需要增强）

**TaskDomainService 方法状态**：

✅ **已存在的方法**（只需确保实现正确）：
- `startRollback(TaskAggregate, TaskRuntimeContext)` - 状态转换为 ROLLING_BACK
- `completeRollback(TaskAggregate, TaskRuntimeContext)` - 状态转换为 ROLLED_BACK
- `failRollback(TaskAggregate, FailureInfo, TaskRuntimeContext)` - 状态转换为 ROLLBACK_FAILED

**需要注入的依赖**：
```java
public class TaskDomainService {
    // ...existing fields...
    private final StageFactory stageFactory;  // ← 新增依赖

    public TaskDomainService(
            TaskRepository taskRepository,
            TaskRuntimeRepository taskRuntimeRepository,
            StateTransitionService stateTransitionService,
            DomainEventPublisher domainEventPublisher,
            StageFactory stageFactory  // ← 新增参数
    ) {
        // ...
        this.stageFactory = stageFactory;
    }
}
```

**TaskAggregate 方法状态**：

✅ **已存在的方法**（需确认）：
- `startRollback()` - 状态转换验证和更新
- `completeRollback()` - 标记回滚完成
- `failRollback(FailureInfo)` - 标记回滚失败

**检查清单**：
- [ ] TaskAggregate 是否有这三个方法？
- [ ] 状态机是否支持这些转换？
- [ ] 领域事件是否正确发布？

### 2.5 状态机（无需修改）

**当前状态机**：
```
FAILED / PAUSED / ROLLBACK_FAILED
  ↓ (rollback)
ROLLING_BACK
  ├─ (success) → ROLLED_BACK
  └─ (failed) → ROLLBACK_FAILED
```

**手动重试回滚**：
```
ROLLBACK_FAILED
  ↓ (manual retry rollback)
ROLLING_BACK → ROLLED_BACK / ROLLBACK_FAILED
```

**实现**：复用现有的 `TaskOperationService.rollbackTaskByTenant(tenantId)`

### 2.6 TaskExecutor 改动

**需要确认的点**：
1. `executor.invokeRollback()` 是否调用 `execute()` 方法？
2. 如果不是，需要修改为调用 `execute()`

**预期实现**：
```java
public class TaskExecutor {
    public TaskResult invokeRollback() {
        // 状态检查和转换
        if (!stateTransitionService.canTransition(task, TaskStatus.ROLLING_BACK, context)) {
            return TaskResult.fail(...);
        }
        
        taskDomainService.startRollback(task, context);
        
        // ✅ 关键：调用 execute()（因为 stages 已经是用旧配置装配的）
        TaskResult result = execute();  // ← 复用正常执行流程
        
        if (result.isSuccess()) {
            taskDomainService.completeRollback(task, context);
        } else {
            taskDomainService.failRollback(task, result.getFailure(), context);
        }
        
        return result;
    }
    
    public TaskResult execute() {
        // 顺序执行 stages（正向发布 & 回滚共用）
        for (TaskStage stage : stages) {
            stage.execute(context);
        }
        // 回滚时，stages 是用 previousConfig 装配的
        // execute() 逻辑完全一样
    }
}
```

### 2.7 Stage 层改动

**检查清单**：
- [ ] `TaskStage` 接口是否有 `rollback()` 方法？
- [ ] 如果有，需要删除（因为回滚通过 `execute()` 实现）
- [ ] `ConfigurableServiceStage` 是否实现了 `rollback()`？
- [ ] 如果有，需要删除实现

**理由**：
- 回滚通过重新 `execute()` 实现
- 不需要单独的回滚逻辑
- 简化接口

---

## 3. 实施计划（修正版）

### Phase 1: 核心链路修复（P0, 18h）

**Day 1: 配置传递与回滚准备** (8h)

1. **修复配置传递** (2h)
   - 文件：`TaskDomainService.java`
   - 修改：在 `createTask()` 中设置 `prevConfigSnapshot`
   - 新增：`convertToSnapshot()` 私有方法

2. **注入 StageFactory 依赖** (1h)
   - 文件：`TaskDomainService.java`
   - 修改：构造函数增加 `StageFactory` 参数
   - 文件：相关配置类（Spring Bean 注册）

3. **实现 prepareRollbackByTenant()** (5h)
   - 文件：`TaskDomainService.java`
   - 修改：重写 `prepareRollbackByTenant()` 方法
   - 新增：`canRollback()` 前置条件检查
   - 新增：`convertSnapshotToConfig()` 转换方法
   - 新增：`buildRollbackContext()` 上下文构建
   - 验证：用 previousConfig 重新装配 Stages 和 RuntimeContext

**Day 2: TaskExecutor 和 Stage 清理** (6h)

4. **检查并修改 TaskExecutor** (2h)
   - 文件：`TaskExecutor.java`
   - 检查：`invokeRollback()` 是否调用 `execute()`
   - 修改：如果不是，改为调用 `execute()`
   - 删除：如果有独立的 `rollback()` 方法，考虑删除

5. **检查并删除 Stage.rollback()** (2h)
   - 文件：`TaskStage.java`
   - 检查：接口是否定义了 `rollback()` 方法
   - 删除：如果有，删除接口定义
   - 文件：`ConfigurableServiceStage.java` 及其他实现类
   - 删除：所有 `rollback()` 方法实现

6. **删除 RollbackStrategy** (1h)
   - 文件：`PreviousConfigRollbackStrategy.java`
   - 操作：删除文件
   - 清理：搜索并清理相关引用

7. **代码审查与编译** (1h)
   - 确保所有改动编译通过
   - 确保无遗漏的 rollback() 引用

**Day 3: 测试与文档** (4h)

8. **单元测试** (4h)
   - 测试 1：配置传递（createTask 设置 prevConfigSnapshot）
   - 测试 2：prepareRollbackByTenant 转换逻辑
   - 测试 3：Stages 重新装配（使用旧配置）
   - 测试 4：RuntimeContext 装填（旧配置数据）
   - 测试 5：前置条件验证（状态检查、快照存在性）
   - 目标覆盖率：≥ 80%

**Day 4: 集成测试与交付** (4h)

9. **集成测试** (3h)
   - 场景 1：成功回滚（版本从 v20 恢复到 v19）
   - 场景 2：健康检查失败（标记 ROLLBACK_FAILED）
   - 场景 3：无 previousConfig（返回失败）

10. **文档更新** (1h)
    - 更新：`docs/design/execution-engine.md`
    - 更新：`docs/design/state-management.md`
    - 添加：回滚流程说明
    - 更新：`developlog.md`

---

## 4. 修改清单（修正版）

| 文件 | 操作 | 工作量 | 说明 |
|------|------|--------|------|
| **TaskDomainService.java** | 修改 + 新增 | 8h | createTask() 设置快照 + prepareRollbackByTenant() 重写 + 注入 StageFactory |
| **Spring 配置类** | 修改 | 0.5h | TaskDomainService Bean 注册增加 StageFactory |
| **TaskExecutor.java** | 检查 + 修改 | 2h | 确认 invokeRollback() 调用 execute() |
| **TaskStage.java** | 删除 | 0.5h | 删除 rollback() 接口（如果有） |
| **ConfigurableServiceStage.java** | 删除 | 0.5h | 删除 rollback() 实现（如果有） |
| **PreviousConfigRollbackStrategy.java** | 删除 | 0.5h | 删除文件 + 清理引用 |
| **单元测试** | 新增 | 4h | ≥ 80% 覆盖率 |
| **集成测试** | 新增 | 3h | 3 个核心场景 |
| **文档** | 更新 | 1h | 设计文档 + developlog |

**总计**：20h ≈ 2.5 天

---

## 4. 修改清单

| 文件 | 操作 | 工作量 | 说明 |
|------|------|--------|------|
| **TaskDomainService.java** | 修改 | 2h | createTask() 设置 prevConfigSnapshot |
| **TaskOperationService.java** | 新增 | 4h | rollbackTask() + 辅助方法 |
| **TaskDomainService.java** | 新增 | 3h | startRollback/completeRollback/failRollback |
| **TaskAggregate.java** | 新增 | 1h | 回滚相关状态转换方法 |
| **TaskExecutor.java** | 删除 | 1h | 移除 rollback() 方法 |
| **TaskStage.java** | 删除 | 0.5h | 移除 rollback() 接口 |
| **ConfigurableServiceStage.java** | 删除 | 0.5h | 移除 rollback() 实现 |
| **PreviousConfigRollbackStrategy.java** | 删除 | 0.5h | 删除文件 |
| **单元测试** | 新增 | 4h | ≥ 80% 覆盖率 |
| **集成测试** | 新增 | 3h | 3 个核心场景 |
| **文档** | 更新 | 2h | 设计文档 + developlog |

**总计**：21.5 工时 ≈ 3 天

---

## 5. 关键设计决策

### 5.1 方案选择

| 决策点 | 选项 A | 选项 B | 选择 | 理由 |
|--------|--------|--------|------|------|
| **回滚级别** | Task 级（重新 execute） | Stage 级（stage.rollback） | ✅ A | 架构简洁，复用性好 |
| **回滚失败处理** | 自动重试 | 手动重试 | ✅ B | 避免循环失败，保持与正向一致 |
| **Stage 接口** | 保留 rollback() | 删除 rollback() | ✅ B | 不需要，简化接口 |
| **RollbackStrategy** | 保留 | 删除 | ✅ B | 不需要，逻辑在应用层 |

### 5.2 与之前方案的对比

| 维度 | 之前方案（Stage 级回滚） | 新方案（Task 级回滚） |
|------|-------------------------|---------------------|
| **复杂度** | 高（每个 Stage 两套逻辑） | 低（完全复用） |
| **代码改动** | 大（修改 Stage/Step） | 小（仅应用层） |
| **维护成本** | 高（双倍维护） | 低（单一流程） |
| **理解难度** | 中（需理解回滚语义） | 低（就是重新发布） |
| **扩展性** | 中 | 高（新 Stage 自动支持） |

### 5.3 为什么不需要 Stage.rollback()

**误区**：回滚需要"逆序执行"，所以要有 `stage.rollback()`

**真相**：
- 回滚 = 用旧配置重新发布
- 发布流程是**顺序的**（不是逆序）
- Stage 只需要 `execute()`，不关心配置新旧

**举例**：
```
正向：v20 → [通知] → [配置写入] → [健康检查]
回滚：v19 → [通知] → [配置写入] → [健康检查]  ← 顺序相同！
```

---

## 6. 数据流示例

### 6.1 正向发布

```
用户输入：
  TenantDeployConfig {
    version: 20,
    deployUnit: "blue",
    previousConfig: { version: 19, deployUnit: "green" }
  }

↓ [Facade]

TenantConfig {
  version: 20,
  previousConfig: { version: 19 }
}

↓ [DeploymentPlanCreator]

TaskAggregate {
  deployUnitVersion: 20,
  prevConfigSnapshot: { version: 19 }  ← 修复点：设置此字段
}

↓ [TaskExecutor]

RuntimeContext {
  deployUnitVersion: 20  ← 新配置
}

↓ [Stages]

执行流程：通知(v20) → 配置(v20) → 健康检查(v20)
```

### 6.2 回滚发布

```
用户触发回滚：
  rollbackTask(taskId)

↓ [TaskOperationService]

TaskAggregate {
  prevConfigSnapshot: { version: 19 }  ← 读取
}

↓ [convertSnapshotToConfig]

TenantConfig {
  version: 19  ← 用旧配置构造
}

↓ [stageFactory.assembleStages]

重新装配 Stages（使用 v19 配置）

↓ [TaskExecutor]

RuntimeContext {
  deployUnitVersion: 19  ← 旧配置
}

↓ [Stages]

执行流程：通知(v19) → 配置(v19) → 健康检查(v19)
  ↑ 完全相同的代码，只是数据不同
```

---

## 7. 验收标准

### 7.1 功能验收

- [ ] **前置条件验证**：
  - 只有 FAILED/PAUSED 状态可以回滚
  - 必须有 prevConfigSnapshot
  
- [ ] **回滚执行**：
  - 使用 previousConfig 重新装配 Stages
  - 调用 execute() 完成发布
  
- [ ] **配置恢复**：
  - Redis 中版本号恢复为旧版本
  - Gateway 配置恢复为旧版本
  - 健康检查通过（验证旧版本）
  
- [ ] **状态转换**：
  - FAILED → ROLLING_BACK → ROLLED_BACK（成功）
  - FAILED → ROLLING_BACK → ROLLBACK_FAILED（失败）
  
- [ ] **手动重试**：
  - ROLLBACK_FAILED 后可以调用 retryTask()

### 7.2 质量验收

- [ ] 单元测试覆盖率 ≥ 80%
- [ ] 集成测试 3 个场景通过
- [ ] 代码审查通过
- [ ] 无编译错误和警告

### 7.3 文档验收

- [ ] `docs/design/execution-engine.md` 更新
- [ ] `docs/design/state-management.md` 更新
- [ ] `developlog.md` 添加完成记录

---

## 8. 风险与缓解

### 8.1 风险清单

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| prevConfigSnapshot 未设置 | 无法回滚 | 高 | P0 优先修复配置传递 |
| 回滚时 Stage 装配失败 | 回滚失败 | 中 | 完善 convertSnapshotToConfig() |
| 健康检查失败率高 | 回滚失败 | 中 | 设置合理超时，人工介入 |
| previousConfig 数据不完整 | 回滚配置不正确 | 低 | Facade 层校验完整性 |

### 8.2 回退方案

如果新方案有问题，可以：
1. 暂时保留 `TaskExecutor.rollback()` 方法
2. 逐步迁移到新方案
3. 但优先级低（新方案更简洁）

---

## 9. 后续优化（Phase 2）

### 可选增强（不阻塞 Phase 1）

1. **回滚可观测性** (P2, 3h)
   - 补充指标：rollback_count, rollback_success, rollback_failed
   - 事件：TaskRollbackStartedEvent, TaskRollbackCompletedEvent

2. **回滚前确认机制** (P2, 2h)
   - 验证 previousConfig 是否历史成功
   - 可选：回滚前二次确认

3. **批量回滚** (P3, 4h)
   - 支持 Plan 级别批量回滚所有 Task
   - 需求不明确，暂不实施

---

## 10. 总结

### 核心改变

**之前理解**：回滚 = Stage 级别的逆序补偿操作

**正确理解**：回滚 = Task 级别的重新发布（使用旧配置）

**架构修正**：回滚完全复用现有的 retry/rollback 准备模式

### 关键优势

1. ✅ **完全复用现有架构**：prepareRollbackByTenant 模式已存在
2. ✅ **最小化改动**：只需修改 TaskDomainService，应用层无需改动
3. ✅ **零重复代码**：回滚 = execute()，Stages 用旧配置装配
4. ✅ **易于维护**：新增功能自动支持回滚
5. ✅ **符合直觉**：回滚就是"重新发布旧版本"

### 与 createTask 的对比

| 阶段 | createTask（正向发布） | prepareRollbackByTenant（回滚） |
|------|----------------------|-------------------------------|
| **配置来源** | 外部传入的 TenantConfig | 从 prevConfigSnapshot 转换 |
| **装配 Stages** | stageFactory.buildStages(config) | stageFactory.buildStages(rollbackConfig) |
| **装填 Context** | 新配置数据（version=20） | 旧配置数据（version=19） |
| **执行方式** | TaskExecutor.execute() | TaskExecutor.execute() ← 完全相同 |

**结论**：回滚与正向发布的唯一区别是**数据来源**，流程完全一致！

### 工作量

- **总工时**：20h ≈ 2.5 天（比之前少 1.5h）
- **优先级**：P1（核心功能）
- **风险**：低（复用现有模式，改动集中在 TaskDomainService）

### 实施优先级

**必须实施（P0）**：
1. ✅ 修复配置传递（createTask 设置 prevConfigSnapshot）
2. ✅ 重写 prepareRollbackByTenant（用旧配置装配 Stages + Context）
3. ✅ 注入 StageFactory 到 TaskDomainService

**可选清理（P1）**：
4. 删除 Stage.rollback() 接口（如果存在）
5. 删除 PreviousConfigRollbackStrategy（如果不使用）

**验证测试（P0）**：
6. 单元测试（配置传递、Stages 装配、Context 装填）
7. 集成测试（端到端回滚流程）

---

**状态**：✅ 设计确认，已对齐现有架构  
**下一步**：开始 Phase 1 - Day 1 实施

