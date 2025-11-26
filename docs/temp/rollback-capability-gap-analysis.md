# Rollback 能力 GAP 分析与方案讨论

**分析时间**: 2025-11-26  
**基于**: rollback-mechanism-analysis.md  
**目标**: 识别并讨论回滚机制的能力缺口，形成一致的解决方案

---

## 执行摘要

基于之前的分析报告，我们识别出了回滚机制的核心问题：**配置传递链路断裂**。但在深入审视整个回滚流程后，发现还有更多需要讨论的设计和实现细节。

**核心发现**：
1. ❌ **P0 - 配置传递链路断裂**：`previousConfig` 未从 DTO 传递到 Domain
2. ⚠️ **P1 - Stage 回滚逻辑缺失**：各 Stage 的 `rollback()` 方法为空占位
3. ⚠️ **P1 - 回滚策略不完整**：仅更新内存字段，未实际重发配置
4. ❓ **需讨论 - 回滚语义不明确**：配置回滚 vs 操作回滚的边界
5. ❓ **需讨论 - 健康检查策略**：回滚后如何确认系统稳定
6. ❓ **需讨论 - 部分失败处理**：Stage 粒度回滚失败的补偿策略

---

## 1. 配置传递链路断裂（P0 - 必须修复）

### 1.1 问题描述

**当前流程**：
```
外部 API: TenantDeployConfig
  ↓ [Facade 转换]
内部 DTO: TenantConfig
  ├─ previousConfig ✅ 已设置
  └─ previousConfigVersion ✅ 已设置
  ↓ [DeploymentPlanCreator.createAndLinkTask()]
TaskDomainService.createTask(planId, config)
  ↓ [创建聚合]
TaskAggregate
  ├─ prevConfigSnapshot ❌ null（未设置）
  └─ lastKnownGoodVersion ❌ null（未设置）
```

**影响**：
- 回滚时 `PreviousConfigRollbackStrategy` 读取到 `prevConfigSnapshot == null`，直接跳过
- 无法实现"恢复到上一次配置"的核心需求
- TaskAggregate 中的回滚相关字段完全未使用

### 1.2 修复方案

#### 方案 A：在 TaskDomainService.createTask() 中设置

```java
public TaskAggregate createTask(PlanId planId, TenantConfig config) {
    logger.info("[TaskDomainService] 创建 Task: planId={}, tenantId={}", planId, config.getTenantId());

    TaskId taskId = generateTaskId(planId, config.getTenantId());
    TaskAggregate task = new TaskAggregate(taskId, planId, config.getTenantId());
    
    // ✅ 新增：设置上一次配置快照
    if (config.getPreviousConfig() != null) {
        TenantDeployConfigSnapshot snapshot = convertToSnapshot(config.getPreviousConfig());
        task.setPrevConfigSnapshot(snapshot);
        task.setLastKnownGoodVersion(config.getPreviousConfigVersion());
    }
    
    task.markAsPending();
    taskRepository.save(task);
    // ... 发布事件 ...
    
    return task;
}

private TenantDeployConfigSnapshot convertToSnapshot(TenantConfig prevConfig) {
    return new TenantDeployConfigSnapshot(
        prevConfig.getTenantId().getValue(),
        prevConfig.getDeployUnit().id(),           // DeployUnitIdentifier.id()
        prevConfig.getDeployUnit().version(),      // DeployUnitIdentifier.version()
        prevConfig.getDeployUnit().name(),         // DeployUnitIdentifier.name()
        prevConfig.getHealthCheckEndpoints()       // List<String>
    );
}
```

**优点**：
- 修改点单一，集中在 TaskDomainService
- 符合领域服务职责（管理聚合生命周期）
- 转换逻辑内聚

**缺点**：
- TaskDomainService 需要了解 TenantConfig 的结构（DTO → Snapshot 转换）

#### 方案 B：在 TaskAggregate 构造函数中设置

```java
public TaskAggregate(TaskId taskId, PlanId planId, TenantId tenantId, 
                     TenantDeployConfigSnapshot prevConfigSnapshot) {
    this.taskId = taskId;
    this.planId = planId;
    this.tenantId = tenantId;
    this.prevConfigSnapshot = prevConfigSnapshot;  // 构造时传入
    this.status = TaskStatus.CREATED;
    // ...
}
```

**优点**：
- 聚合根构造时就完整（不变式保护）
- 转换逻辑可在应用层（DeploymentPlanCreator）完成

**缺点**：
- 构造函数参数增多
- 现有调用方都需要修改

#### 方案 C：引入专门的转换器

```java
@Component
public class TenantConfigToSnapshotConverter {
    public TenantDeployConfigSnapshot convert(TenantConfig config) {
        if (config == null) return null;
        return new TenantDeployConfigSnapshot(
            config.getTenantId().getValue(),
            config.getDeployUnit().id(),
            config.getDeployUnit().version(),
            config.getDeployUnit().name(),
            config.getHealthCheckEndpoints()
        );
    }
}
```

**优点**：
- 职责分离清晰
- 可复用（多处需要转换时）
- 易于测试

**缺点**：
- 引入新组件，增加复杂度

### 1.3 推荐方案

**推荐方案 A + 小改进**：在 TaskDomainService 中设置，但提取转换方法为 private static，保持简洁。

**理由**：
1. 当前只有一处需要转换（创建 Task 时）
2. 转换逻辑简单，无需独立组件
3. 符合 DDD 原则：领域服务协调聚合生命周期
4. 如果未来有更多转换场景，再重构为 Converter

---

## 2. Stage 回滚逻辑缺失（P1 - 核心功能）

### 2.1 问题描述

当前 `ConfigurableServiceStage.rollback()` 实现：

```java
@Override
public void rollback(TaskRuntimeContext ctx) {
    log.info("Stage '{}' 回滚占位（待实现）", name);
    // 可选的回滚逻辑
}
```

**影响**：
- TaskExecutor 逆序调用 `stage.rollback(ctx)`，但什么都不做
- 无法实现真正的配置回滚（重发配置、健康检查等）
- 回滚状态转换为 `ROLLED_BACK`，但实际上没有执行任何操作

### 2.2 核心设计问题：什么是"回滚"？

在讨论具体实现前，我们需要明确回滚的语义：

#### 语义 A：配置回滚（推荐）
**定义**：将系统配置恢复到上一次已知的可用状态

**实现方式**：
1. 重新发送 `previousConfig` 到所有目标系统（Redis、网关、Nacos 等）
2. 健康检查确认旧版本生效
3. 不需要"撤销"已执行的操作，只需"覆盖"配置

**示例**：
```
当前配置：version=20, deploy_unit=blue
上次配置：version=19, deploy_unit=green

回滚操作：
1. Redis HSET tenant:xxx version=19
2. Redis HSET tenant:xxx deploy_unit=green
3. HTTP POST /gateway/config (body: version=19, ...)
4. 健康检查 /health?expect_version=19
```

**优点**：
- 语义清晰：回滚 = 恢复旧配置
- 实现简单：复用 execute 逻辑，替换数据源
- 幂等性好：多次回滚结果一致

**缺点**：
- 需要完整的 previousConfig（已通过 1.3 解决）
- 依赖目标系统支持配置覆盖

#### 语义 B：操作回滚（补偿事务）
**定义**：逆序执行已完成操作的"反操作"

**实现方式**：
1. Redis 写入 → Redis 删除
2. HTTP POST → HTTP DELETE
3. Pub/Sub 广播 → 广播撤销消息

**示例**：
```
正向操作：
1. Redis HSET tenant:xxx version=20
2. HTTP POST /gateway/config

回滚操作：
1. HTTP DELETE /gateway/config/20
2. Redis DEL tenant:xxx version
```

**优点**：
- 精确撤销每个操作
- 不依赖 previousConfig

**缺点**：
- 实现复杂：需要为每个 Step 实现反操作
- 不一定可行：有些操作不可逆（如通知、日志）
- 幂等性差：部分失败时状态难以预测

#### 语义 C：混合模式
- 关键配置使用语义 A（覆盖旧配置）
- 辅助操作使用语义 B（撤销操作）

**复杂度高，不推荐作为初始方案**。

### 2.3 推荐方案：语义 A（配置回滚）

**核心洞察**：回滚 = 用旧配置再走一遍正常流程

**理由**：
1. **业务需求对齐**：分析报告中的预期就是"重新发送上一次配置"
2. **技术可行性高**：目标系统（Redis、网关）都支持配置覆盖
3. **代码复用最大化**：
   - ✅ **零 Step 改动**：ConfigWriteStep、HttpRequestStep、PollingStep 完全复用
   - ✅ **零 Stage 重新编排**：stepConfigs 列表直接复用
   - ✅ **零 DataPreparer 改动**：自动从 RuntimeContext 读取配置数据
   - ✅ **只需修改数据源**：在 Stage.rollback() 中构造新的 RuntimeContext，装填旧配置
4. **幂等性好**：多次回滚不会产生副作用
5. **维护成本低**：新增 Step 自动支持回滚，无需额外开发

**关键实现**：
```java
// 正向执行：应用层构造 RuntimeContext（装填新配置 version=20）
TaskRuntimeContext ctx = new TaskRuntimeContext(...);
ctx.addVariable("deployUnitVersion", 20L);
stage.execute(ctx);  // Steps 从 ctx 读取 version=20

// 回滚执行：Stage 构造 RuntimeContext（装填旧配置 version=19）
TaskRuntimeContext rollbackCtx = new TaskRuntimeContext(...);
rollbackCtx.addVariable("deployUnitVersion", 19L);  // ← 唯一区别
for (StepConfig step : stepConfigs) {
    step.execute(rollbackCtx);  // ← 执行逻辑完全一样
}
```

**详细示例**：参见 [rollback-data-flow-example.md](./rollback-data-flow-example.md)

### 2.4 具体实现方案

#### 方案 A：复用 Execute Steps（推荐）

```java
@Override
public void rollback(TaskRuntimeContext ctx) {
    log.info("Stage '{}' 开始回滚（复用 Steps）", name);
    
    // 1. 从 TaskAggregate 获取 previousConfig
    TaskAggregate task = ctx.getTask();
    TenantDeployConfigSnapshot prevSnap = task.getPrevConfigSnapshot();
    
    if (prevSnap == null) {
        log.warn("Stage '{}' 无上一次配置，跳过回滚", name);
        return;
    }
    
    // 2. 构造回滚 RuntimeContext（使用旧配置数据）
    TaskRuntimeContext rollbackCtx = buildRollbackContext(ctx, prevSnap);
    
    // 3. 执行相同的 Steps（数据源替换为 previousConfig）
    for (StepConfig stepConfig : stepConfigs) {
        try {
            // 数据准备（使用回滚数据）
            if (stepConfig.getDataPreparer() != null) {
                stepConfig.getDataPreparer().prepare(rollbackCtx);
            }
            
            // 执行 Step（与正常执行相同）
            stepConfig.getStep().execute(rollbackCtx);
            
            log.debug("Stage '{}' Step '{}' 回滚成功", name, stepConfig.getStepName());
        } catch (Exception e) {
            log.error("Stage '{}' Step '{}' 回滚失败", name, stepConfig.getStepName(), e);
            throw new RollbackException("回滚失败: " + e.getMessage(), e);
        }
    }
    
    log.info("Stage '{}' 回滚成功", name);
}

private TaskRuntimeContext buildRollbackContext(TaskRuntimeContext originalCtx, 
                                                 TenantDeployConfigSnapshot prevSnap) {
    TaskRuntimeContext rollbackCtx = new TaskRuntimeContext(
        originalCtx.getPlanId(),
        originalCtx.getTaskId(),
        originalCtx.getTenantId()
    );
    
    // 设置回滚数据（覆盖原始变量）
    rollbackCtx.addVariable("deployUnitVersion", prevSnap.getDeployUnitVersion());
    rollbackCtx.addVariable("deployUnitId", prevSnap.getDeployUnitId());
    rollbackCtx.addVariable("deployUnitName", prevSnap.getDeployUnitName());
    rollbackCtx.addVariable("healthCheckEndpoints", prevSnap.getNetworkEndpoints());
    
    // 保留必要的原始上下文（如 planVersion）
    rollbackCtx.addVariable("planVersion", originalCtx.getAdditionalData("planVersion"));
    
    return rollbackCtx;
}
```

**优点**：
- 代码复用度高（Step 逻辑完全复用）
- 维护成本低（新增 Step 自动支持回滚）
- 实现简单（只需替换数据源）

**缺点**：
- 假设所有 Step 都支持配置覆盖（实际大部分都支持）
- 无法处理不可逆操作（如通知 Step）

#### 方案 B：专用回滚 Steps

```java
public ConfigurableServiceStage(String name, 
                                List<StepConfig> stepConfigs,
                                List<StepConfig> rollbackStepConfigs) {
    this.name = name;
    this.stepConfigs = stepConfigs;
    this.rollbackStepConfigs = rollbackStepConfigs;  // 独立的回滚 Steps
}

@Override
public void rollback(TaskRuntimeContext ctx) {
    log.info("Stage '{}' 开始回滚（专用 Steps）", name);
    
    for (StepConfig stepConfig : rollbackStepConfigs) {
        // 执行回滚专用的 Steps
        stepConfig.getStep().execute(ctx);
    }
}
```

**优点**：
- 灵活性高（可以为回滚定制完全不同的 Steps）
- 可以处理补偿事务（如发送撤销通知）

**缺点**：
- 配置复杂（需要为每个 Stage 单独配置回滚 Steps）
- 代码重复（大部分 Step 的回滚逻辑与正向相同）
- 维护成本高（新增功能需要同时维护两套 Steps）

#### 方案 C：Step 层面支持回滚

```java
public interface StageStep {
    String getStepName();
    void execute(TaskRuntimeContext ctx) throws Exception;
    void rollback(TaskRuntimeContext ctx) throws Exception;  // 新增回滚方法
}
```

**优点**：
- 封装性好（每个 Step 知道如何回滚自己）
- 支持复杂场景（如补偿事务）

**缺点**：
- 接口变更影响大（所有 Step 实现都需要修改）
- 增加实现复杂度（6 个 Step × 2 = 12 个方法）
- 大部分 Step 的回滚就是"重新执行"（冗余）

### 2.5 推荐方案：方案 A（复用 Execute Steps）

**理由**：
1. **简单高效**：80% 的场景只需替换数据源
2. **易于维护**：新增 Step 无需额外开发
3. **符合现状**：当前 Step（ConfigWriteStep、HttpRequestStep 等）都是无状态的，天然支持配置覆盖

**特殊情况处理**：
- 对于不需要回滚的 Step（如通知、日志），可以在 DataPreparer 中跳过
- 对于需要特殊处理的 Step，可以通过 RuntimeContext 中的 `isRollback` 标志区分

**实现示例**（处理特殊情况）：

```java
// DataPreparer 中判断是否回滚
public class NotificationDataPreparer implements DataPreparer {
    @Override
    public void prepare(TaskRuntimeContext ctx) {
        // 回滚时跳过通知
        if (ctx.isRollback()) {
            ctx.addVariable("skipNotification", true);
            return;
        }
        // 正常准备通知数据
        // ...
    }
}

// Step 中检查跳过标志
public class NotificationStep implements StageStep {
    @Override
    public void execute(TaskRuntimeContext ctx) {
        if (Boolean.TRUE.equals(ctx.getAdditionalData("skipNotification"))) {
            log.info("回滚模式，跳过通知");
            return;
        }
        // 执行通知
        // ...
    }
}
```

---

## 3. 回滚策略不完整（P1 - 核心功能）

### 3.1 问题描述

当前 `PreviousConfigRollbackStrategy` 实现：

```java
@Override
public void rollback(TaskAggregate task, TaskRuntimeContext context) throws Exception {
    TenantDeployConfigSnapshot snap = task.getPrevConfigSnapshot();
    if (snap == null) {
        log.warn("No previous config snapshot for task={}, skipping rollback", task.getTaskId());
        return;
    }
    log.info("Re-sending previous config: task={}, tenant={}, version={}", 
        task.getTaskId(), snap.getTenantId(), snap.getDeployUnitVersion());
    
    // ⚠️ 仅恢复 TaskAggregate 内的版本字段
    task.setDeployUnitVersion(snap.getDeployUnitVersion());
    task.setDeployUnitId(snap.getDeployUnitId());
    task.setDeployUnitName(snap.getDeployUnitName());
    
    // ⚠️ 健康确认为占位实现
    log.info("Rollback health confirmation placeholder: task={}, endpoints={}", 
        task.getTaskId(), snap.getNetworkEndpoints());
}
```

**影响**：
- 只更新了内存中的聚合字段，没有实际重发配置
- 健康检查未实现，无法确认回滚是否成功

### 3.2 设计问题：RollbackStrategy 的职责

当前架构中有两层回滚：
1. **TaskExecutor.rollback()**：逆序调用 `stage.rollback(ctx)`
2. **PreviousConfigRollbackStrategy**：单独的回滚策略

**疑问**：
- 这两者的关系是什么？
- 谁负责实际重发配置？
- 谁负责健康检查？

### 3.3 架构澄清建议

#### 当前架构（代码实际情况）

```
TaskExecutor.rollback()
  ├─ 状态检查（canTransition）
  ├─ taskDomainService.startRollback()
  ├─ 逆序执行 stage.rollback(ctx)  ← 主流程
  │   └─ ConfigurableServiceStage.rollback()
  │       └─ 执行 Steps（重发配置、健康检查）
  ├─ taskDomainService.completeRollback()
  └─ 释放租户锁
```

#### PreviousConfigRollbackStrategy 的位置？

**发现**：`PreviousConfigRollbackStrategy` 在代码中定义了，但没有被 TaskExecutor 调用！

```bash
# 搜索 PreviousConfigRollbackStrategy 的使用
grep -r "PreviousConfigRollbackStrategy" --include="*.java"
```

**结论**：这是一个**未集成的策略**，可能是早期设计遗留。

### 3.4 方案选择

#### 方案 A：移除 PreviousConfigRollbackStrategy（推荐）

**理由**：
1. Stage.rollback() 已经足够承担回滚职责
2. 增加策略层会导致职责重复
3. 简化架构，减少理解成本

**实现**：
- 删除 `PreviousConfigRollbackStrategy` 类
- 在 `ConfigurableServiceStage.rollback()` 中实现完整逻辑（如 2.4 方案 A）

#### 方案 B：集成 PreviousConfigRollbackStrategy

**理由**：
1. 支持多种回滚策略（如：恢复旧配置 vs 重新部署）
2. 策略模式提供扩展性

**实现**：
```java
public TaskResult rollback() {
    // ...
    taskDomainService.startRollback(task, context);
    
    // 1. 执行回滚策略（高层逻辑）
    rollbackStrategy.rollback(task, context);
    
    // 2. 逆序执行 Stage 回滚（细节操作）
    for (TaskStage stage : reversedStages) {
        stage.rollback(context);
    }
    
    taskDomainService.completeRollback(task, context);
    // ...
}
```

**问题**：
- 策略和 Stage 的职责界限模糊
- 增加理解成本
- 当前没有多种回滚策略的需求

### 3.5 推荐方案：方案 A（移除策略，集成到 Stage）

**理由**：
1. YAGNI 原则：当前不需要多种回滚策略
2. 职责清晰：Stage 负责完整的执行和回滚
3. 代码简洁：减少抽象层

**如果未来需要多种策略**：
- 通过不同的 Stage 组合实现（如：RollbackStage、RedeployStage）
- 而不是在 Stage 上层再加一个策略

---

## 4. 健康检查策略（需讨论）

### 4.1 问题描述

回滚后如何确认系统已恢复到稳定状态？

**当前实现**：
- 正向执行有 PollingStep（健康检查，轮询直到版本匹配）
- 回滚时未实现健康检查

### 4.2 设计问题

#### 问题 1：回滚是否需要健康检查？

**观点 A：必须健康检查**
- 理由：回滚是为了恢复稳定，必须确认成功
- 实现：复用 PollingStep，检查旧版本

**观点 B：不需要健康检查**
- 理由：回滚本身就是降级操作，旧版本假定可用
- 实现：只重发配置，不轮询

**推荐观点 A**：必须健康检查，确保回滚成功。

#### 问题 2：健康检查失败怎么办？

**场景**：回滚后，健康检查仍然失败（旧版本也不可用）

**选项 A：回滚失败，状态 = ROLLBACK_FAILED**
- 需要人工介入
- 记录详细错误信息

**选项 B：尝试再次回滚**
- 可能陷入循环
- 不推荐

**选项 C：尝试重新部署当前版本**
- 复杂度高
- 超出回滚职责

**推荐选项 A**：标记 ROLLBACK_FAILED，触发告警，人工介入。

#### 问题 3：健康检查的超时和重试策略？

**参考正向执行**：
```java
// ExecutorProperties
healthCheckIntervalSeconds = 3   // 轮询间隔
healthCheckMaxAttempts = 10      // 最大尝试次数（30秒）
```

**回滚场景建议**：
- 间隔：3 秒（与正向一致）
- 最大尝试：5 次（15 秒，比正向更快失败）
- 理由：回滚是紧急操作，不宜等太久

**可配置性**：
```java
// ExecutorProperties
healthCheckIntervalSecondsRollback = 3
healthCheckMaxAttemptsRollback = 5
```

### 4.3 实现方案

#### 在 ConfigurableServiceStage.rollback() 中复用 PollingStep

```java
@Override
public void rollback(TaskRuntimeContext ctx) {
    log.info("Stage '{}' 开始回滚", name);
    
    // 1. 从 TaskAggregate 获取 previousConfig
    TenantDeployConfigSnapshot prevSnap = ctx.getTask().getPrevConfigSnapshot();
    if (prevSnap == null) {
        log.warn("Stage '{}' 无上一次配置，跳过回滚", name);
        return;
    }
    
    // 2. 构造回滚 RuntimeContext
    TaskRuntimeContext rollbackCtx = buildRollbackContext(ctx, prevSnap);
    rollbackCtx.addVariable("isRollback", true);  // 标记回滚模式
    
    // 3. 执行相同的 Steps（数据源替换为 previousConfig）
    for (StepConfig stepConfig : stepConfigs) {
        try {
            // 数据准备
            if (stepConfig.getDataPreparer() != null) {
                stepConfig.getDataPreparer().prepare(rollbackCtx);
            }
            
            // 执行 Step
            stepConfig.getStep().execute(rollbackCtx);
            
            // ✅ 如果是 PollingStep，会自动检查旧版本
            log.debug("Stage '{}' Step '{}' 回滚成功", name, stepConfig.getStepName());
        } catch (Exception e) {
            log.error("Stage '{}' Step '{}' 回滚失败", name, stepConfig.getStepName(), e);
            throw new RollbackException("回滚失败: " + e.getMessage(), e);
        }
    }
    
    log.info("Stage '{}' 回滚成功（包含健康检查）", name);
}
```

**关键点**：
- PollingStep 会从 `rollbackCtx` 中读取 `expectVersion`（即 `prevSnap.getDeployUnitVersion()`）
- DataPreparer 负责准备健康检查的端点和预期版本
- 健康检查失败会抛异常，导致回滚失败

---

## 5. 部分失败处理（需讨论）

### 5.1 问题描述

**场景**：回滚过程中，某个 Stage 失败了

```
正向执行：Stage A → Stage B → Stage C（失败）
  └─ 开始回滚...
回滚执行（逆序）：Stage C rollback（跳过） → Stage B rollback（成功） → Stage A rollback（失败❌）
```

**当前实现**：
```java
// TaskExecutor.rollback()
boolean anyFailed = false;
for (TaskStage stage : reversedStages) {
    try {
        stage.rollback(context);
    } catch (Exception ex) {
        anyFailed = true;
        log.error("Stage 回滚失败: {}", stageName, ex);
    }
}

if (anyFailed) {
    taskDomainService.failRollback(task, failure, context);
    // 状态 = ROLLBACK_FAILED
}
```

### 5.2 设计问题

#### 问题 1：部分 Stage 回滚失败，是否继续回滚其他 Stage？

**当前实现**：继续执行，最后统一标记失败

**观点 A：立即停止（Fail-Fast）**
- 理由：避免部分回滚导致状态不一致
- 实现：第一个失败就 `break`

**观点 B：全部尝试（Best-Effort）**
- 理由：尽可能多地恢复系统
- 实现：记录失败，继续执行（当前实现）

**推荐观点 B**：全部尝试回滚，记录所有失败的 Stage。

**理由**：
- 回滚是降级操作，尽可能恢复比放弃更好
- 即使部分失败，其他 Stage 回滚成功也有价值
- 最终状态 = ROLLBACK_FAILED，会触发人工介入

#### 问题 2：ROLLBACK_FAILED 后的恢复策略？

**当前状态机**：
```
FAILED → ROLLING_BACK → ROLLBACK_FAILED（终态）
```

**恢复选项**：
1. **重新回滚**：`ROLLBACK_FAILED → ROLLING_BACK`
   - 需要：状态机支持
   - 场景：临时故障（网络抖动、服务重启）

2. **手工修复**：人工介入，修复失败的 Stage，然后标记为已恢复
   - 需要：详细的失败记录（哪些 Stage 失败、原因）
   - 场景：配置错误、权限问题

3. **放弃回滚，重试正向**：`ROLLBACK_FAILED → RUNNING`
   - 需要：状态机支持
   - 场景：旧版本确实有问题，回滚不可行

**推荐策略**：
- **短期（MVP）**：ROLLBACK_FAILED 为终态，人工介入修复
- **长期**：支持重新回滚（选项 1），记录详细失败信息

### 5.3 失败信息记录

**当前实现**：
```java
FailureInfo failure = FailureInfo.of(ErrorType.BUSINESS_ERROR, "部分 Stage 回滚失败");
```

**问题**：丢失了具体是哪些 Stage 失败、失败原因

**改进方案**：
```java
// 收集所有失败信息
List<String> failedStages = new ArrayList<>();
StringBuilder failureDetails = new StringBuilder();

for (TaskStage stage : reversedStages) {
    try {
        stage.rollback(context);
    } catch (Exception ex) {
        anyFailed = true;
        failedStages.add(stage.getName());
        failureDetails.append(String.format("[%s]: %s; ", stage.getName(), ex.getMessage()));
        log.error("Stage 回滚失败: {}", stage.getName(), ex);
    }
}

if (anyFailed) {
    FailureInfo failure = FailureInfo.of(
        ErrorType.ROLLBACK_PARTIAL_FAILED,  // 新增错误类型
        String.format("部分 Stage 回滚失败（%d/%d）: %s", 
            failedStages.size(), reversedStages.size(), String.join(", ", failedStages)),
        failureDetails.toString()  // 详细信息
    );
    taskDomainService.failRollback(task, failure, context);
}
```

**需要新增**：
```java
// ErrorType.java
ROLLBACK_PARTIAL_FAILED("ROLLBACK_PARTIAL_FAILED", "部分 Stage 回滚失败");
```

---

## 6. 数据一致性与事务边界（需讨论）

### 6.1 问题描述

**场景**：回滚涉及多个外部系统

```
回滚操作：
1. Redis: HSET tenant:xxx version=19  ← 成功
2. Gateway: POST /config (version=19)  ← 失败
3. Nacos: updateConfig(version=19)    ← 未执行
```

**问题**：
- Redis 已更新，但 Gateway 和 Nacos 未更新
- 系统处于不一致状态

### 6.2 设计问题

#### 问题 1：回滚是否需要事务保证？

**当前架构**：无事务保证（每个 Step 独立执行）

**观点 A：需要事务（分布式事务 / Saga）**
- 理由：保证强一致性
- 实现：TCC / Saga 模式
- 成本：高（需引入事务框架）

**观点 B：不需要事务（最终一致性）**
- 理由：回滚本身就是补偿操作，追求最终一致即可
- 实现：失败重试 + 幂等性
- 成本：低

**推荐观点 B**：最终一致性，通过重试保证。

**理由**：
1. 外部系统（Redis、Gateway、Nacos）本身不支持分布式事务
2. 回滚是降级操作，允许短暂不一致
3. 重试 + 幂等性 = 最终一致

#### 问题 2：如何保证幂等性？

**关键**：所有回滚操作必须幂等（多次执行结果一致）

**当前 Steps 的幂等性分析**：

| Step | 操作 | 幂等性 | 说明 |
|------|------|--------|------|
| ConfigWriteStep | Redis HSET | ✅ 幂等 | 覆盖写入，多次执行结果一致 |
| HttpRequestStep | HTTP POST/PUT | ⚠️ 取决于 API | 需要目标 API 支持幂等 |
| MessageBroadcastStep | Pub/Sub | ❌ 不幂等 | 每次广播都会发送消息 |
| PollingStep | 健康检查 | ✅ 幂等 | 只读操作 |
| RedisAckStep | Redis 确认 | ✅ 幂等 | SETNX 或 HSET，多次执行结果一致 |

**风险点**：
1. **HttpRequestStep**：需要确保目标 API 支持幂等（如通过版本号、唯一 ID）
2. **MessageBroadcastStep**：回滚时可能需要跳过（避免重复通知）

**改进方案**：
```java
// 在 TaskRuntimeContext 中添加幂等性标识
rollbackCtx.addVariable("idempotencyKey", task.getTaskId() + "-rollback-" + System.currentTimeMillis());

// HttpRequestStep 使用幂等性 Key
HttpHeaders headers = new HttpHeaders();
headers.set("Idempotency-Key", ctx.getAdditionalData("idempotencyKey"));
```

### 6.3 重试策略

**当前实现**：无自动重试（回滚失败直接标记 ROLLBACK_FAILED）

**改进方案**：
```java
// TaskExecutor.rollback()
for (TaskStage stage : reversedStages) {
    int retryCount = 0;
    int maxRetries = 3;  // 可配置
    boolean success = false;
    
    while (retryCount < maxRetries && !success) {
        try {
            stage.rollback(context);
            success = true;
        } catch (Exception ex) {
            retryCount++;
            if (retryCount < maxRetries) {
                log.warn("Stage {} 回滚失败，重试 {}/{}", stage.getName(), retryCount, maxRetries);
                Thread.sleep(1000 * retryCount);  // 指数退避
            } else {
                log.error("Stage {} 回滚失败，已达最大重试次数", stage.getName(), ex);
                anyFailed = true;
            }
        }
    }
}
```

**配置化**：
```java
// ExecutorProperties
rollbackMaxRetries = 3          // 最大重试次数
rollbackRetryIntervalMs = 1000  // 重试间隔（毫秒）
rollbackRetryBackoff = true     // 是否使用指数退避
```

---

## 7. 可观测性与监控（需讨论）

### 7.1 问题描述

当前回滚流程的可观测性不足：
- 无法查看回滚进度（回滚了哪些 Stage）
- 无法查看回滚耗时
- 无法统计回滚成功率

### 7.2 改进方案

#### 方案 A：复用现有事件体系

**领域事件**：
```java
// 已有事件
TaskRollingBackEvent      // 开始回滚
TaskRolledBackEvent       // 回滚成功
TaskRollbackFailedEvent   // 回滚失败

// 新增细粒度事件（可选）
TaskStageRollbackStartedEvent    // Stage 回滚开始
TaskStageRollbackCompletedEvent  // Stage 回滚成功
TaskStageRollbackFailedEvent     // Stage 回滚失败
```

**实现**：
```java
// TaskExecutor.rollback()
for (TaskStage stage : reversedStages) {
    // 发布 Stage 回滚开始事件
    taskDomainService.startStageRollback(task, stage.getName(), context);
    
    try {
        stage.rollback(context);
        
        // 发布 Stage 回滚成功事件
        taskDomainService.completeStageRollback(task, stage.getName(), context);
    } catch (Exception ex) {
        // 发布 Stage 回滚失败事件
        taskDomainService.failStageRollback(task, stage.getName(), ex, context);
    }
}
```

**优点**：
- 与正向执行一致（已有 TaskStageStartedEvent 等）
- 支持外部监听（如发送告警、更新监控面板）

**缺点**：
- 增加事件数量（可能导致事件风暴）

#### 方案 B：使用指标（Metrics）

```java
// TaskExecutor.rollback()
metrics.incrementCounter("rollback_count");  // 已有

// 新增指标
metrics.recordTimer("rollback_duration", duration);
metrics.recordTimer("rollback_stage_duration", stageName, stageDuration);
metrics.incrementCounter("rollback_stage_success", stageName);
metrics.incrementCounter("rollback_stage_failed", stageName);
```

**优点**：
- 轻量级（不产生领域事件）
- 易于聚合和可视化

**缺点**：
- 无法追踪单个 Task 的回滚详情

#### 方案 C：结合事件 + 指标（推荐）

- **事件**：用于追踪单个 Task 的回滚流程（调试、审计）
- **指标**：用于聚合统计（成功率、耗时分布）

**实现优先级**：
1. **P0**：补充基本指标（`rollback_success`, `rollback_failed`, `rollback_duration`）
2. **P1**：补充 Stage 级别事件（`TaskStageRollbackCompletedEvent`）
3. **P2**：补充详细指标（每个 Stage 的耗时、成功率）

---

## 8. 测试策略（需讨论）

### 8.1 问题描述

回滚是关键路径，需要充分测试

### 8.2 测试场景

#### 单元测试

1. **配置传递测试**
   ```java
   @Test
   void should_set_previous_config_snapshot_when_creating_task() {
       // Given: previousConfig 已设置
       TenantConfig config = buildConfig();
       config.setPreviousConfig(buildPreviousConfig());
       
       // When: 创建 Task
       TaskAggregate task = taskDomainService.createTask(planId, config);
       
       // Then: prevConfigSnapshot 已设置
       assertNotNull(task.getPrevConfigSnapshot());
       assertEquals(19L, task.getPrevConfigSnapshot().getDeployUnitVersion());
   }
   ```

2. **Stage 回滚逻辑测试**
   ```java
   @Test
   void should_reuse_steps_when_rollback() {
       // Given: 构造 Stage 和 RuntimeContext
       ConfigurableServiceStage stage = buildStage();
       TaskRuntimeContext ctx = buildContext();
       ctx.getTask().setPrevConfigSnapshot(buildSnapshot());
       
       // When: 执行回滚
       stage.rollback(ctx);
       
       // Then: Steps 被执行，数据源为 previousConfig
       verify(configWriteStep).execute(any());
       verify(httpRequestStep).execute(any());
       assertEquals(19L, ctx.getAdditionalData("deployUnitVersion"));
   }
   ```

3. **部分失败处理测试**
   ```java
   @Test
   void should_continue_rollback_when_one_stage_failed() {
       // Given: 3 个 Stage，第 2 个会失败
       List<TaskStage> stages = List.of(stage1, failingStage, stage3);
       
       // When: 执行回滚
       TaskResult result = taskExecutor.rollback();
       
       // Then: 所有 Stage 都被尝试，状态 = ROLLBACK_FAILED
       verify(stage1).rollback(any());
       verify(failingStage).rollback(any());
       verify(stage3).rollback(any());
       assertEquals(TaskStatus.ROLLBACK_FAILED, task.getStatus());
   }
   ```

#### 集成测试

1. **端到端回滚测试**
   ```java
   @SpringBootTest
   @Test
   void should_rollback_successfully() {
       // Given: 部署新版本
       PlanCreationContext plan = deploymentService.createPlan(buildConfigs(version=20));
       TaskId taskId = plan.getPlanInfo().getTasks().get(0).getTaskId();
       
       // 等待执行完成
       await().until(() -> taskQueryService.getTaskStatus(taskId) == COMPLETED);
       
       // When: 触发回滚
       taskOperationService.rollback(taskId);
       
       // Then: 回滚成功，配置恢复到 version=19
       await().until(() -> taskQueryService.getTaskStatus(taskId) == ROLLED_BACK);
       String version = redisTemplate.opsForHash().get("tenant:xxx", "version");
       assertEquals("19", version);
   }
   ```

2. **健康检查失败测试**
   ```java
   @Test
   void should_fail_rollback_when_health_check_failed() {
       // Given: Mock 健康检查失败
       when(healthCheckClient.check(any())).thenReturn(false);
       
       // When: 执行回滚
       TaskResult result = taskOperationService.rollback(taskId);
       
       // Then: 回滚失败
       assertEquals(TaskStatus.ROLLBACK_FAILED, result.getStatus());
       assertTrue(result.getFailureInfo().getMessage().contains("健康检查失败"));
   }
   ```

### 8.3 测试覆盖率目标

| 层次 | 目标覆盖率 | 说明 |
|------|-----------|------|
| 领域服务 | ≥ 80% | TaskDomainService 回滚方法 |
| 执行引擎 | ≥ 80% | TaskExecutor.rollback() |
| Stage 回滚 | ≥ 70% | ConfigurableServiceStage.rollback() |
| 集成测试 | ≥ 3 个场景 | 成功、失败、部分失败 |

---

## 9. 实施计划与优先级

### 9.1 优先级矩阵

| 问题 | 优先级 | 复杂度 | 估算工时 | 风险 |
|------|--------|--------|----------|------|
| 1. 配置传递链路断裂 | **P0** | 低 | 2h | 低 |
| 2. Stage 回滚逻辑缺失 | **P1** | 中 | 8h | 中 |
| 3. 移除未集成的 RollbackStrategy | **P1** | 低 | 1h | 低 |
| 4. 健康检查集成 | **P1** | 低 | 4h | 低 |
| 5. 部分失败信息记录 | P2 | 低 | 2h | 低 |
| 6. 重试策略 | P2 | 中 | 4h | 中 |
| 7. 可观测性增强 | P2 | 低 | 3h | 低 |
| 8. 测试补充 | **P1** | 中 | 6h | 低 |

### 9.2 分阶段实施

#### Phase 1: 基础能力补全（P0 + P1, 21h）

**目标**：实现完整的回滚流程（配置恢复 + 健康检查）

**任务**：
1. ✅ 修复配置传递链路（TaskDomainService.createTask）
2. ✅ 实现 Stage 回滚逻辑（ConfigurableServiceStage.rollback）
3. ✅ 移除 PreviousConfigRollbackStrategy
4. ✅ 集成健康检查（复用 PollingStep）
5. ✅ 补充单元测试和集成测试

**交付物**：
- 代码修改（5 个文件）
- 单元测试（≥ 5 个用例）
- 集成测试（≥ 2 个场景）
- 技术文档（回滚流程说明）

#### Phase 2: 健壮性增强（P2, 9h）

**目标**：提升回滚的可靠性和可观测性

**任务**：
1. ✅ 部分失败详细信息记录
2. ✅ Stage 级别重试策略
3. ✅ 补充回滚指标（Metrics）
4. ✅ 补充 Stage 回滚事件

**交付物**：
- 代码修改（3 个文件）
- 配置项文档（ExecutorProperties）
- 监控面板示例

#### Phase 3: 长期优化（未来）

**目标**：支持更复杂的回滚场景

**任务**：
1. 支持重新回滚（ROLLBACK_FAILED → ROLLING_BACK）
2. 支持部分回滚（只回滚特定 Stage）
3. 支持回滚预检查（Dry-Run）
4. 支持回滚审批流程

---

## 10. 待讨论问题清单

### 10.1 高优先级（需立即讨论）

#### Q1: 回滚语义选择
**问题**：采用"配置回滚"还是"操作回滚"？
- 配置回滚：重新发送旧配置（推荐）
- 操作回滚：逆序执行反操作（复杂）

**影响**：决定 Stage.rollback() 的实现方式

**建议**：配置回滚（方案 2.3）

---

#### Q2: RollbackStrategy 的去留
**问题**：是否保留 `PreviousConfigRollbackStrategy`？
- 保留：支持多种回滚策略（扩展性）
- 移除：简化架构，职责归 Stage（推荐）

**影响**：架构复杂度和未来扩展性

**建议**：移除（方案 3.5）

---

#### Q3: 健康检查是否必需
**问题**：回滚后是否必须健康检查？
- 必需：确保回滚成功（推荐）
- 可选：快速回滚，假定旧版本可用

**影响**：回滚耗时和可靠性

**建议**：必需，但超时时间缩短（方案 4.2）

---

### 10.2 中优先级（Phase 1 完成后讨论）

#### Q4: 部分失败处理策略
**问题**：Stage 回滚失败后，是否继续回滚其他 Stage？
- Fail-Fast：立即停止（保守）
- Best-Effort：全部尝试（推荐）

**影响**：回滚的完整性

**建议**：Best-Effort（方案 5.2）

---

#### Q5: ROLLBACK_FAILED 后的恢复路径
**问题**：回滚失败后，是否支持重新回滚？
- 支持：需修改状态机
- 不支持：人工介入（短期推荐）

**影响**：状态机复杂度和恢复能力

**建议**：短期不支持，Phase 3 再考虑

---

#### Q6: 回滚重试策略
**问题**：Stage 回滚失败时，是否自动重试？
- 自动重试：提高成功率（推荐）
- 不重试：失败即停止

**影响**：回滚的可靠性和耗时

**建议**：支持（方案 6.3），可配置次数（默认 3 次）

---

### 10.3 低优先级（未来讨论）

#### Q7: 回滚预检查
是否支持 Dry-Run 模式（检查回滚可行性，但不实际执行）？

#### Q8: 分布式事务
是否需要引入分布式事务框架（如 Seata）保证强一致性？

#### Q9: 回滚审批流程
是否需要人工审批后才能执行回滚？

---

## 11. 总结与下一步

### 11.1 核心发现总结

1. ❌ **配置传递链路断裂**：必须修复（P0）
2. ⚠️ **回滚逻辑缺失**：Stage 和 Strategy 层都是占位实现（P1）
3. ❓ **设计决策未明确**：回滚语义、健康检查策略、失败处理等需讨论
4. ✅ **架构基础良好**：状态机、事件、Checkpoint 等基础设施完善

### 11.2 推荐技术方案（待确认）

| 问题 | 推荐方案 | 理由 |
|------|----------|------|
| 配置传递 | 方案 A（TaskDomainService） | 简单、集中 |
| 回滚语义 | 配置回滚 | 实用、易实现 |
| Stage 实现 | 复用 Execute Steps | 代码复用、维护简单 |
| RollbackStrategy | 移除 | 简化架构 |
| 健康检查 | 必需（复用 PollingStep） | 确保可靠性 |
| 部分失败 | Best-Effort | 尽可能恢复 |
| 重试策略 | 支持（3 次） | 提高成功率 |

### 11.3 下一步行动

#### 立即行动
1. **讨论确认**：与团队确认上述推荐方案（Q1-Q3）
2. **制定详细设计**：基于确认的方案，编写详细设计文档
3. **创建任务**：拆分为可执行的开发任务（JIRA / GitHub Issue）

#### Phase 1 实施（估算 21h）
1. 修复配置传递链路
2. 实现 Stage 回滚逻辑
3. 移除未集成的 RollbackStrategy
4. 集成健康检查
5. 补充测试

#### 验收标准
- [ ] 创建 Task 时 `prevConfigSnapshot` 正确设置
- [ ] 回滚时能重新发送 previousConfig 到 Redis、Gateway
- [ ] 回滚后健康检查通过（检查旧版本）
- [ ] 部分 Stage 失败时，状态 = ROLLBACK_FAILED，记录详细错误
- [ ] 单元测试覆盖率 ≥ 80%
- [ ] 集成测试通过（成功、失败、部分失败场景）

---

## 附录

### A. 相关代码文件清单

| 文件 | 路径 | 修改说明 |
|------|------|----------|
| TaskDomainService | domain/task/TaskDomainService.java | 添加 previousConfig 转换逻辑 |
| ConfigurableServiceStage | infrastructure/execution/stage/ConfigurableServiceStage.java | 实现 rollback() 方法 |
| PreviousConfigRollbackStrategy | infrastructure/execution/stage/rollback/PreviousConfigRollbackStrategy.java | 删除（或标记 @Deprecated） |
| TaskExecutor | infrastructure/execution/TaskExecutor.java | 优化失败信息记录 |
| ExecutorProperties | config/ExecutorProperties.java | 新增回滚相关配置项 |

### B. 测试文件清单

| 测试类 | 类型 | 测试场景 |
|--------|------|----------|
| TaskDomainServiceTest | 单元测试 | 配置传递 |
| ConfigurableServiceStageTest | 单元测试 | Stage 回滚逻辑 |
| TaskExecutorTest | 单元测试 | 部分失败处理 |
| RollbackIntegrationTest | 集成测试 | 端到端回滚 |

### C. 参考文档

- [rollback-mechanism-analysis.md](./rollback-mechanism-analysis.md) - 初始分析报告
- [architecture-overview.md](../architecture-overview.md) - 架构总览
- [execution-engine.md](../design/execution-engine.md) - 执行引擎设计
- [state-management.md](../design/state-management.md) - 状态管理设计

---

**文档状态**：待讨论  
**期待输出**：就 Q1-Q6 达成一致，启动 Phase 1 实施

