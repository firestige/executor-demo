# T-028 回滚机制完善 - 设计核实总结

**日期**：2025-11-26  
**核实人**：GitHub Copilot  
**状态**：✅ 设计已核实并对齐现有架构

---

## 📋 核实结论

### ✅ 核心发现

经过对现有代码的深入分析，发现：

1. **应用层已有正确的回滚入口**：
   - `TaskOperationService.rollbackTaskByTenant(tenantId)` 已存在
   - 完全符合预期的调用流程
   - **无需修改应用层**

2. **领域层已有准备方法**：
   - `TaskDomainService.prepareRollbackByTenant(tenantId)` 已存在
   - 与 `prepareRetryByTenant` 模式一致
   - **只需改进实现**（用旧配置重新装配 Stages 和 Context）

3. **现有架构模式清晰**：
   - `TaskWorkerCreationContext` 封装执行所需数据
   - `TaskWorkerFactory` 统一创建 TaskExecutor
   - 异步执行模式成熟（CompletableFuture）

### ❌ 初版设计问题

初版设计中有以下误解：

1. **误解 1**：需要在 `TaskOperationService` 中新增 `rollbackTask()` 方法
   - **实际**：`rollbackTaskByTenant()` 已存在且正确

2. **误解 2**：需要在应用层装配 Stages 和装填 Context
   - **实际**：应该在领域层（TaskDomainService）完成

3. **误解 3**：需要将 `StageFactory` 注入到 `TaskOperationService`
   - **实际**：应该注入到 `TaskDomainService`（符合分层架构）

---

## 🎯 修正后的方案

### 核心改动（仅 1 处）

**TaskDomainService.prepareRollbackByTenant()**

```java
// 现有实现（有问题）
public TaskWorkerCreationContext prepareRollbackByTenant(TenantId tenantId) {
    TaskAggregate target = findTaskByTenantId(tenantId);
    
    // ❌ 问题：复用了原有的 stages 和 context（新配置）
    List<TaskStage> stages = taskRuntimeRepository.getStages(target.getTaskId()).orElseGet(List::of);
    TaskRuntimeContext ctx = taskRuntimeRepository.getContext(target.getTaskId()).orElse(null);
    
    return TaskWorkerCreationContext.builder()
        .task(target)
        .stages(stages)      // ← 问题：使用新配置装配的 Stages
        .runtimeContext(ctx) // ← 问题：装填新配置数据
        .build();
}
```

```java
// 修正后实现（正确）
public TaskWorkerCreationContext prepareRollbackByTenant(TenantId tenantId) {
    TaskAggregate task = findTaskByTenantId(tenantId);
    TenantDeployConfigSnapshot prevSnap = task.getPrevConfigSnapshot();
    
    // ✅ 关键：用旧配置重新装配
    TenantConfig rollbackConfig = convertSnapshotToConfig(prevSnap, task);
    List<TaskStage> rollbackStages = stageFactory.buildStages(rollbackConfig);  // ← 用旧配置装配
    TaskRuntimeContext rollbackCtx = buildRollbackContext(task, prevSnap);     // ← 装填旧配置
    
    return TaskWorkerCreationContext.builder()
        .task(task)
        .stages(rollbackStages)      // ← 正确：使用旧配置装配的 Stages
        .runtimeContext(rollbackCtx) // ← 正确：装填旧配置数据
        .build();
}
```

### 配套改动

1. **TaskDomainService 构造函数**：
   ```java
   public TaskDomainService(
           TaskRepository taskRepository,
           TaskRuntimeRepository taskRuntimeRepository,
           StateTransitionService stateTransitionService,
           DomainEventPublisher domainEventPublisher,
           StageFactory stageFactory  // ← 新增依赖
   )
   ```

2. **TaskDomainService.createTask()**：
   ```java
   public TaskAggregate createTask(PlanId planId, TenantConfig config) {
       TaskAggregate task = new TaskAggregate(taskId, planId, config.getTenantId());
       
       // ✅ 设置 prevConfigSnapshot（当前缺失）
       if (config.getPreviousConfig() != null) {
           TenantDeployConfigSnapshot snapshot = convertToSnapshot(config.getPreviousConfig());
           task.setPrevConfigSnapshot(snapshot);
           task.setLastKnownGoodVersion(config.getPreviousConfigVersion());
       }
       
       task.markAsPending();
       taskRepository.save(task);
       return task;
   }
   ```

---

## 📊 与现有模式对比

### prepareRetryByTenant（现有，正确）

```
领域层准备：
  ├─ 查找 Task
  ├─ 从仓储读取 Stages（复用原配置）
  ├─ 从仓储读取 Context（复用原配置）
  └─ 返回 TaskWorkerCreationContext

应用层执行：
  ├─ 创建 TaskExecutor
  └─ 异步调用 executor.retry()
```

### prepareRollbackByTenant（修正后，正确）

```
领域层准备：
  ├─ 查找 Task
  ├─ 从 prevConfigSnapshot 转换配置
  ├─ 用旧配置重新装配 Stages（stageFactory.buildStages）
  ├─ 用旧配置装填 Context（buildRollbackContext）
  └─ 返回 TaskWorkerCreationContext

应用层执行：
  ├─ 创建 TaskExecutor
  └─ 异步调用 executor.invokeRollback() → execute()
```

**关键区别**：
- **Retry**：复用原有的 Stages 和 Context（从仓储读取）
- **Rollback**：重新装配 Stages 和 Context（使用旧配置）

---

## 📝 实施清单

### Phase 1: 核心修复（P0）

**Day 1**（8h）：
- [ ] 1. 修复 `TaskDomainService.createTask()`：设置 `prevConfigSnapshot`（2h）
- [ ] 2. 注入 `StageFactory` 到 `TaskDomainService`（1h）
- [ ] 3. 重写 `prepareRollbackByTenant()` 实现（5h）
  - [ ] 3.1 新增 `canRollback()` 前置条件检查
  - [ ] 3.2 新增 `convertSnapshotToConfig()` 转换方法
  - [ ] 3.3 新增 `buildRollbackContext()` 上下文构建
  - [ ] 3.4 调用 `stageFactory.buildStages(rollbackConfig)`
  - [ ] 3.5 返回 `TaskWorkerCreationContext`

**Day 2**（6h）：
- [ ] 4. 检查 `TaskExecutor.invokeRollback()`：确认调用 `execute()`（2h）
- [ ] 5. 删除 `TaskStage.rollback()` 接口（如果存在）（2h）
- [ ] 6. 删除 `PreviousConfigRollbackStrategy` 类（如果不使用）（1h）
- [ ] 7. 编译并清理引用（1h）

**Day 3**（4h）：
- [ ] 8. 单元测试（4h）
  - [ ] 8.1 配置传递测试
  - [ ] 8.2 prepareRollbackByTenant 逻辑测试
  - [ ] 8.3 Stages 重新装配测试
  - [ ] 8.4 Context 装填测试

**Day 4**（4h）：
- [ ] 9. 集成测试（3h）
  - [ ] 9.1 成功回滚场景（版本恢复）
  - [ ] 9.2 健康检查失败场景
  - [ ] 9.3 无 previousConfig 场景
- [ ] 10. 文档更新（1h）

**总工时**：22h ≈ 3 天

---

## 🎨 数据流示例

### 正向发布（version=20）

```
外部输入：TenantDeployConfig {version: 20, previousConfig: {version: 19}}
  ↓
应用层：DeploymentPlanCreator
  ├─ taskDomainService.createTask(planId, config)
  │   └─ task.setPrevConfigSnapshot({version: 19})  ← 设置旧配置
  ├─ stageFactory.buildStages(config)  ← version=20
  └─ runtimeContext.addVariable("deployUnitVersion", 20L)
  
执行：TaskExecutor.execute()
  └─ Stages 使用 version=20 配置
```

### 回滚（version=19）

```
触发：TaskOperationService.rollbackTaskByTenant(tenantId)
  ↓
领域层：taskDomainService.prepareRollbackByTenant(tenantId)
  ├─ prevSnap = task.getPrevConfigSnapshot()  ← 读取旧配置 {version: 19}
  ├─ rollbackConfig = convertSnapshotToConfig(prevSnap)
  ├─ rollbackStages = stageFactory.buildStages(rollbackConfig)  ← version=19
  └─ rollbackCtx.addVariable("deployUnitVersion", 19L)  ← 装填旧数据
  
执行：TaskExecutor.invokeRollback() → execute()
  └─ Stages 使用 version=19 配置  ← 与正向流程完全相同的代码
```

**关键点**：
- 正向和回滚使用**相同的执行逻辑**（execute）
- 唯一区别是**配置数据**（version=20 vs version=19）
- Stages、Steps、DataPreparers **完全复用**

---

## ✅ 验收标准

### 功能验收
- [ ] 创建 Task 时 `prevConfigSnapshot` 正确设置
- [ ] 回滚时使用 previousConfig 重新装配 Stages
- [ ] 回滚时 RuntimeContext 装填旧配置数据
- [ ] Redis/Gateway 配置恢复为旧版本
- [ ] 健康检查验证旧版本生效

### 架构验收
- [ ] 应用层无需修改（复用 rollbackTaskByTenant）
- [ ] 领域层负责准备（与 prepareRetryByTenant 一致）
- [ ] StageFactory 注入到 TaskDomainService（符合分层）
- [ ] 完全复用 TaskWorkerCreationContext 模式

### 质量验收
- [ ] 单元测试覆盖率 ≥ 80%
- [ ] 集成测试 3 个场景通过
- [ ] 无编译错误和警告
- [ ] 代码审查通过

---

## 📚 参考文档

1. **设计方案**：[rollback-task-level-design.md](./rollback-task-level-design.md)
2. **架构对比**：[rollback-architecture-comparison.md](./rollback-architecture-comparison.md)
3. **原始分析**：[rollback-mechanism-analysis.md](./rollback-mechanism-analysis.md)

---

## 🚀 下一步

**立即可以开始实施**：
1. 方案已充分验证
2. 架构已完全对齐
3. 改动点清晰明确
4. 风险低、收益高

**建议**：
- 优先完成 Phase 1 - Day 1（配置传递 + prepareRollbackByTenant）
- 这是核心能力，完成后即可进行端到端测试

---

**状态**：✅ 设计核实完成，待实施  
**风险**：🟢 低（改动集中，复用现有模式）  
**信心**：⭐⭐⭐⭐⭐ 非常高

