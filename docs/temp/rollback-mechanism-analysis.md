# Rollback 机制分析报告

**分析时间**: 2025-11-26  
**分析目标**: 验证 Rollback 机制是否符合预期 —— 在新建 task 时保留上一次配置，回滚时重新发送该配置

---

## 1. 预期行为

1. **创建 Task 时**: 保存 `previousConfig`（上一次成功的完整配置）
2. **执行 Rollback 时**: 将 `previousConfig` 重新发送一遍，恢复到上一次可用状态

---

## 2. 当前实现分析

### 2.1 数据模型设计 ✅

#### TenantConfig (DTO 层)
```java
// 位置: application/dto/TenantConfig.java

// 完整的上一次配置（用于回滚时恢复配置内容）
private TenantConfig previousConfig;

// 冗余字段，快速访问上一次的版本号
private Long previousConfigVersion;
```

**设计说明**:
- `previousConfig`: 保存完整的上一版配置对象，包含所有字段
- `previousConfigVersion`: 冗余字段，用于快速版本比较和幂等性保证

#### TenantDeployConfigSnapshot (Domain 层)
```java
// 位置: domain/task/TenantDeployConfigSnapshot.java

public class TenantDeployConfigSnapshot {
    private final String tenantId;
    private final Long deployUnitId;
    private final Long deployUnitVersion;
    private final String deployUnitName;
    private final List<String> networkEndpoints;
}
```

**设计说明**:
- 不持有外部 DTO 引用，避免耦合
- 只保存回滚所需的关键字段（简化版本）

#### TaskAggregate (聚合根)
```java
// 位置: domain/task/TaskAggregate.java

// 上一次可用配置快照
private TenantDeployConfigSnapshot prevConfigSnapshot;

// 上一次成功切换完成的版本号
private Long lastKnownGoodVersion;
```

---

### 2.2 回滚策略实现 ✅

#### RollbackStrategy 接口
```java
// 位置: infrastructure/execution/stage/rollback/RollbackStrategy.java

public interface RollbackStrategy {
    void rollback(TaskAggregate task, TaskRuntimeContext context) throws Exception;
}
```

#### PreviousConfigRollbackStrategy 实现
```java
// 位置: infrastructure/execution/stage/rollback/PreviousConfigRollbackStrategy.java

@Override
public void rollback(TaskAggregate task, TaskRuntimeContext context) throws Exception {
    TenantDeployConfigSnapshot snap = task.getPrevConfigSnapshot();
    
    if (snap == null) {
        log.warn("No previous config snapshot for task={}, skipping rollback", task.getTaskId());
        return;
    }
    
    log.info("Re-sending previous config: task={}, tenant={}, version={}", 
        task.getTaskId(), snap.getTenantId(), snap.getDeployUnitVersion());
    
    // 恢复任务的版本信息
    task.setDeployUnitVersion(snap.getDeployUnitVersion());
    task.setDeployUnitId(snap.getDeployUnitId());
    task.setDeployUnitName(snap.getDeployUnitName());
    
    // 健康确认占位
    log.info("Rollback health confirmation placeholder: task={}, endpoints={}", 
        task.getTaskId(), snap.getNetworkEndpoints());
}
```

**当前状态**: ⚠️ **占位实现**
- ✅ 能够读取 `prevConfigSnapshot`
- ✅ 能够恢复版本信息到 TaskAggregate
- ⚠️ **未实现**: 实际重新发送配置到目标系统
- ⚠️ **未实现**: 健康检查确认

---

### 2.3 Stage 层回滚实现 ⚠️

#### TaskStage 接口定义
```java
// 位置: infrastructure/execution/stage/TaskStage.java

public interface TaskStage {
    void execute(TaskRuntimeContext ctx);
    void rollback(TaskRuntimeContext ctx);  // ✅ 定义了回滚接口
    // ...
}
```

#### ConfigurableServiceStage 实现
```java
// 位置: infrastructure/execution/stage/ConfigurableServiceStage.java

@Override
public void rollback(TaskRuntimeContext ctx) {
    log.info("Stage '{}' 回滚占位（待实现）", name);
    // ⚠️ 回滚逻辑待实现
}
```

**当前状态**: ⚠️ **占位实现**
- ✅ 接口已定义
- ⚠️ 实际逻辑为空（仅打印日志）

#### TaskExecutor 回滚编排
```java
// 位置: infrastructure/execution/TaskExecutor.java

public TaskResult rollback() {
    // 1. 前置检查
    if (!stateTransitionService.canTransition(task, TaskStatus.ROLLING_BACK, context)) {
        return TaskResult.fail(...);
    }
    
    // 2. 开始回滚
    taskDomainService.startRollback(task, context);
    
    // 3. 逆序执行各 Stage 的 rollback
    List<TaskStage> reversedStages = new ArrayList<>(stages);
    Collections.reverse(reversedStages);
    
    for (TaskStage stage : reversedStages) {
        stage.rollback(context);  // ✅ 调用 Stage 回滚
    }
    
    // 4. 完成回滚
    taskDomainService.completeRollback(task, context);
}
```

**编排逻辑**: ✅ **完整**
- ✅ 状态检查
- ✅ 逆序执行
- ✅ 异常处理
- ✅ 状态更新

---

### 2.4 配置传递链路 ❌ **断链**

#### 问题: previousConfig 未传递到 TaskAggregate

```
外部 API (TenantDeployConfig)
  ↓ [Facade 转换]
TenantConfig (previousConfig ✅ 已设置)
  ↓ [DeploymentPlanCreator.createAndLinkTask]
TaskDomainService.createTask(planId, config)
  ↓ [TaskAggregate 创建]
new TaskAggregate(taskId, planId, tenantId)
  ↓
TaskAggregate.prevConfigSnapshot = null  ❌ **未设置**
```

**根本原因**: `TaskDomainService.createTask()` 方法中未将 `TenantConfig.previousConfig` 转换并设置到 `TaskAggregate.prevConfigSnapshot`

---

## 3. 实际使用场景追踪

### 3.1 BlueGreenStageAssembler 中的使用

```java
// 位置: infrastructure/execution/stage/factory/assembler/BlueGreenStageAssembler.java

private String extractSourceUnit(TenantConfig config) {
    // ✅ 使用 previousConfig 获取源单元
    if (config.getPreviousConfig() != null 
        && config.getPreviousConfig().getDeployUnit() != null) {
        return config.getPreviousConfig().getDeployUnit().name();
    }
    return extractTargetUnit(config);  // Fallback
}
```

**用途**: 蓝绿切换时，从 `previousConfig` 获取源单元名称

### 3.2 ObServiceStageAssembler 中的使用

```java
// 位置: infrastructure/execution/stage/factory/assembler/ObServiceStageAssembler.java

private String extractSourceUnit(TenantConfig config) {
    if (config.getPreviousConfig() != null 
        && config.getPreviousConfig().getDeployUnit() != null) {
        return config.getPreviousConfig().getDeployUnit().name();
    }
    return extractTargetUnit(config);
}
```

**用途**: OB 服务切换时获取源单元

---

## 4. 问题总结

### 4.1 核心问题 ❌

**配置传递链路断裂**:
```
TenantConfig.previousConfig (有值)
   ↓ ❌ 未传递
TaskAggregate.prevConfigSnapshot (null)
```

**影响**:
1. 回滚时 `PreviousConfigRollbackStrategy` 读取到 null，跳过回滚
2. 无法恢复到上一次配置
3. TaskAggregate 中的 `lastKnownGoodVersion` 也未设置

### 4.2 实现不完整 ⚠️

1. **PreviousConfigRollbackStrategy**:
   - 仅恢复 TaskAggregate 内的版本字段
   - 未实际重发配置到外部系统（网关、Nacos 等）
   - 健康检查为占位实现

2. **ConfigurableServiceStage.rollback()**:
   - 完全是空实现（仅日志）
   - 未执行任何实际回滚操作

### 4.3 架构优势 ✅

尽管有问题，但架构设计是合理的：

1. **职责分离清晰**:
   - `TenantConfig`: 外部输入，携带完整配置
   - `TenantDeployConfigSnapshot`: 领域快照，解耦外部 DTO
   - `RollbackStrategy`: 可插拔策略

2. **扩展性好**:
   - Stage 回滚接口已定义
   - 策略模式支持多种回滚策略

3. **状态管理完善**:
   - 回滚状态转换完整（ROLLING_BACK → ROLLED_BACK / ROLLBACK_FAILED）
   - 领域事件支持

---

## 5. 修复建议

### 5.1 修复配置传递（高优先级）

**位置**: `TaskDomainService.createTask()`

```java
public TaskAggregate createTask(PlanId planId, TenantConfig config) {
    // ...创建聚合...
    TaskAggregate task = new TaskAggregate(taskId, planId, config.getTenantId());
    
    // ✅ 新增：设置上一次配置快照
    if (config.getPreviousConfig() != null) {
        TenantDeployConfigSnapshot snapshot = convertToSnapshot(config.getPreviousConfig());
        task.setPrevConfigSnapshot(snapshot);
        task.setLastKnownGoodVersion(config.getPreviousConfigVersion());
    }
    
    // ...保存和发布事件...
}

private TenantDeployConfigSnapshot convertToSnapshot(TenantConfig prevConfig) {
    return new TenantDeployConfigSnapshot(
        prevConfig.getTenantId().getValue(),
        prevConfig.getDeployUnitId(),
        prevConfig.getDeployUnitVersion(),
        prevConfig.getDeployUnitName(),
        prevConfig.getHealthCheckEndpoints()  // 或 networkEndpoints
    );
}
```

### 5.2 实现 Stage 回滚逻辑（中优先级）

**方案 A: 复用 Execute Steps（推荐）**

```java
@Override
public void rollback(TaskRuntimeContext ctx) {
    // 从 context 或 task 获取 previousConfig
    TenantDeployConfigSnapshot prevSnap = ctx.getTask().getPrevConfigSnapshot();
    
    if (prevSnap == null) {
        log.warn("No previous config, skip rollback for stage: {}", name);
        return;
    }
    
    // 构造回滚 TenantConfig（基于 previousConfig）
    TenantConfig rollbackConfig = buildRollbackConfig(prevSnap);
    
    // 逆序执行 Steps（或只执行关键 Steps）
    for (StepConfig stepCfg : stepConfigs) {
        stepCfg.getStep().execute(ctx);  // 使用回滚配置执行
    }
}
```

**方案 B: 专用回滚 Steps**

```java
// 为每个 Stage 配置独立的 rollback steps
private final List<StepConfig> rollbackSteps;

@Override
public void rollback(TaskRuntimeContext ctx) {
    for (StepConfig stepCfg : rollbackSteps) {
        stepCfg.getStep().execute(ctx);
    }
}
```

### 5.3 完善 PreviousConfigRollbackStrategy（低优先级）

```java
@Override
public void rollback(TaskAggregate task, TaskRuntimeContext context) throws Exception {
    TenantDeployConfigSnapshot snap = task.getPrevConfigSnapshot();
    // ...检查...
    
    // ✅ 实际重发配置（调用外部服务）
    sendConfigToGateway(snap);
    updateNacosConfig(snap);
    
    // ✅ 健康检查确认
    boolean healthy = pollHealthCheck(snap.getNetworkEndpoints(), maxRetries);
    if (!healthy) {
        throw new RollbackException("Health check failed after rollback");
    }
    
    // 更新 TaskAggregate
    task.setDeployUnitVersion(snap.getDeployUnitVersion());
    // ...
}
```

---

## 6. 测试验证建议

### 6.1 单元测试

```java
@Test
void should_preserve_previous_config_when_creating_task() {
    // Given
    TenantConfig config = buildTenantConfig();
    TenantConfig previousConfig = buildPreviousConfig();
    config.setPreviousConfig(previousConfig);
    
    // When
    TaskAggregate task = taskDomainService.createTask(planId, config);
    
    // Then
    assertNotNull(task.getPrevConfigSnapshot());
    assertEquals(previousConfig.getDeployUnitVersion(), 
                 task.getPrevConfigSnapshot().getDeployUnitVersion());
}

@Test
void should_rollback_to_previous_config() {
    // Given
    TaskAggregate task = createTaskWithPreviousConfig();
    
    // When
    TaskResult result = taskExecutor.rollback();
    
    // Then
    assertEquals(TaskStatus.ROLLED_BACK, task.getStatus());
    assertEquals(previousVersion, task.getDeployUnitVersion());
}
```

### 6.2 集成测试

```java
@SpringBootTest
class RollbackIntegrationTest {
    
    @Test
    void should_rollback_and_restore_gateway_config() {
        // 1. 部署新版本
        deployNewVersion(tenantId, newVersion);
        
        // 2. 触发回滚
        rollbackService.rollback(taskId);
        
        // 3. 验证网关配置已恢复
        GatewayConfig config = gatewayClient.getConfig(tenantId);
        assertEquals(previousVersion, config.getVersion());
        
        // 4. 验证健康检查通过
        assertTrue(healthCheckPassed(tenantId));
    }
}
```

---

## 7. 总结

### 7.1 当前状态

| 模块 | 状态 | 说明 |
|------|------|------|
| 数据模型 | ✅ 完整 | TenantConfig、TenantDeployConfigSnapshot、TaskAggregate |
| 回滚策略接口 | ✅ 完整 | RollbackStrategy 定义清晰 |
| TaskExecutor 编排 | ✅ 完整 | 逆序执行、状态管理 |
| **配置传递** | ❌ **缺失** | **未将 previousConfig 设置到 TaskAggregate** |
| Stage 回滚实现 | ⚠️ 占位 | 仅日志，无实际逻辑 |
| 策略回滚实现 | ⚠️ 占位 | 仅恢复字段，未重发配置 |

### 7.2 对齐预期

**预期**: 新建 task 时保留上一次配置，回滚时重新发送

**实际**:
- ❌ 新建 task 时 **未** 保留上一次配置（链路断裂）
- ⚠️ 回滚时 **无法** 重新发送（因为没有数据 + 逻辑未实现）

### 7.3 修复优先级

1. **P0 (必须修复)**: 修复配置传递链路 → TaskDomainService.createTask()
2. **P1 (核心功能)**: 实现 Stage 回滚逻辑 → ConfigurableServiceStage.rollback()
3. **P2 (增强)**: 完善回滚策略 → PreviousConfigRollbackStrategy

---

**结论**: 
- 架构设计 ✅ 合理
- 核心链路 ❌ 断裂（配置未传递）
- 实现进度 ⚠️ 占位阶段
- 修复难度 🟢 低（主要是数据传递和逻辑补充）

