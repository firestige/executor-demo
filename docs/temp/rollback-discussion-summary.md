# Rollback 机制讨论摘要

**目标**：快速达成关键设计决策共识  
**时间**：2025-11-26

---

## 🎯 核心问题（需立即决策）

### Q1: 回滚的语义 - 配置回滚 vs 操作回滚

```
【选项 A】配置回滚（推荐）✅
─────────────────────────
核心洞察：回滚 = 用旧配置再走一遍正常流程

概念：重新发送 previousConfig，覆盖当前配置
实现：**完全复用 Stage 和 Step**，只替换数据源

正向执行：version=20, unit=blue   →  [FAILED]
回滚执行：version=19, unit=green  →  [走相同流程]
      ├─ 相同的 Stage 编排
      ├─ 相同的 Step 逻辑（ConfigWriteStep、HttpRequestStep、PollingStep）
      └─ 唯一区别：DataPreparer 装配旧配置数据

代码影响：
  Stage.rollback(ctx) {
    // 1. 从 TaskAggregate 提取 previousConfig
    // 2. 构造新的 RuntimeContext（装填旧配置数据）
    // 3. 执行相同的 Steps（无需修改 Step 代码）
  }

✅ 优点：
  - 零 Step 代码改动（完全复用）
  - 零 Stage 重新编排（自动复用）
  - 数据准备集中（只改 DataPreparer）
  - 简单、实用、幂等
  
❌ 缺点：依赖目标系统支持配置覆盖（实际都支持）

【选项 B】操作回滚（补偿事务）
─────────────────────────
概念：逆序执行反操作

正向：Redis HSET, HTTP POST
回滚：HTTP DELETE, Redis DEL

✅ 优点：精确撤销
❌ 缺点：复杂、某些操作不可逆、幂等性差
```

**推荐**：选项 A（配置回滚）

**影响范围**：
- Stage.rollback() 的实现方式
- Step 是否需要实现 rollback() 方法
- 是否需要 previousConfig 完整数据

---

### Q2: RollbackStrategy 的架构定位

```
【当前代码】
────────────
PreviousConfigRollbackStrategy 类存在，但未被使用
TaskExecutor.rollback() 直接调用 stage.rollback(ctx)

【选项 A】移除 RollbackStrategy（推荐）
─────────────────────────────────────
理由：
- YAGNI 原则（当前无多策略需求）
- 职责清晰（Stage 负责完整的执行+回滚）
- 代码简洁（减少抽象层）

实现：
- 删除 PreviousConfigRollbackStrategy 类
- 在 ConfigurableServiceStage.rollback() 中实现完整逻辑

【选项 B】集成 RollbackStrategy
───────────────────────────────
理由：
- 支持多种回滚策略（如：恢复旧配置 vs 重新部署）
- 策略模式提供扩展性

实现：
TaskExecutor.rollback() {
  rollbackStrategy.rollback(task, ctx);  // 高层逻辑
  for (stage : stages) {
    stage.rollback(ctx);  // 细节操作
  }
}

⚠️ 缺点：职责重叠、理解成本高
```

**推荐**：选项 A（移除，集成到 Stage）

**影响范围**：
- 需删除 1 个类（PreviousConfigRollbackStrategy）
- ConfigurableServiceStage.rollback() 需完整实现

---

### Q3: 健康检查策略

```
【问题】回滚后是否必须健康检查？

【选项 A】必须健康检查（推荐）
─────────────────────────────
理由：
- 确保回滚成功（旧版本可能也有问题）
- 与正向执行对称（execute 有健康检查）

实现：
- 复用 PollingStep
- 检查 expect_version = previousConfig.version
- 超时策略：5 次 × 3 秒 = 15 秒（比正向更快失败）

健康检查失败 → ROLLBACK_FAILED → 告警 + 人工介入

【选项 B】不检查（快速回滚）
─────────────────────────
理由：
- 假定旧版本可用（降低耗时）
- 回滚是紧急操作

⚠️ 风险：回滚失败无法及时发现
```

**推荐**：选项 A（必须检查，但超时更短）

**配置项**：
```properties
executor.rollback.health-check.enabled=true
executor.rollback.health-check.max-attempts=5
executor.rollback.health-check.interval-seconds=3
```

---

## 📊 问题优先级总览

| 问题 | 优先级 | 工时 | 风险 | 阻塞 |
|------|--------|------|------|------|
| **配置传递链路断裂** | P0 | 2h | 低 | 阻塞所有回滚功能 |
| **Stage 回滚逻辑缺失** | P1 | 8h | 中 | 核心功能 |
| 移除 RollbackStrategy | P1 | 1h | 低 | 架构清理 |
| 健康检查集成 | P1 | 4h | 低 | 可靠性保证 |
| 部分失败信息记录 | P2 | 2h | 低 | 可观测性 |
| 重试策略 | P2 | 4h | 中 | 健壮性 |
| 测试补充 | P1 | 6h | 低 | 质量保证 |

**Phase 1 总工时**：21h（P0 + P1）

---

## 🔧 技术实现速览

### 1. 配置传递修复（P0, 2h）

```java
// TaskDomainService.createTask()
public TaskAggregate createTask(PlanId planId, TenantConfig config) {
    TaskAggregate task = new TaskAggregate(taskId, planId, config.getTenantId());
    
    // ✅ 新增：设置 previousConfig 快照
    if (config.getPreviousConfig() != null) {
        TenantDeployConfigSnapshot snapshot = convertToSnapshot(config.getPreviousConfig());
        task.setPrevConfigSnapshot(snapshot);
        task.setLastKnownGoodVersion(config.getPreviousConfigVersion());
    }
    
    task.markAsPending();
    return task;
}

private TenantDeployConfigSnapshot convertToSnapshot(TenantConfig cfg) {
    return new TenantDeployConfigSnapshot(
        cfg.getTenantId().getValue(),
        cfg.getDeployUnit().id(),
        cfg.getDeployUnit().version(),
        cfg.getDeployUnit().name(),
        cfg.getHealthCheckEndpoints()
    );
}
```

**验证**：
```java
@Test
void should_set_previous_config_snapshot() {
    TenantConfig config = buildConfig(version=20);
    config.setPreviousConfig(buildConfig(version=19));
    
    TaskAggregate task = service.createTask(planId, config);
    
    assertNotNull(task.getPrevConfigSnapshot());
    assertEquals(19L, task.getPrevConfigSnapshot().getDeployUnitVersion());
}
```

---

### 2. Stage 回滚逻辑（P1, 8h）

```java
// ConfigurableServiceStage.rollback()
@Override
public void rollback(TaskRuntimeContext ctx) {
    log.info("Stage '{}' 开始回滚", name);
    
    // 1. 获取 previousConfig
    TenantDeployConfigSnapshot prevSnap = ctx.getTask().getPrevConfigSnapshot();
    if (prevSnap == null) {
        log.warn("无上一次配置，跳过回滚");
        return;
    }
    
    // 2. 构造回滚上下文（装填旧配置数据）
    //    ✅ 关键：这里是唯一需要改的地方
    TaskRuntimeContext rollbackCtx = buildRollbackContext(ctx, prevSnap);
    rollbackCtx.addVariable("isRollback", true);  // 可选标记
    
    // 3. ✅ 完全复用 execute 的 Steps（零代码改动）
    for (StepConfig stepConfig : stepConfigs) {
        // DataPreparer 从 rollbackCtx 中提取旧配置数据
        if (stepConfig.getDataPreparer() != null) {
            stepConfig.getDataPreparer().prepare(rollbackCtx);
        }
        
        // Step 执行逻辑完全一样（不知道也不关心是回滚）
        stepConfig.getStep().execute(rollbackCtx);
    }
    
    log.info("Stage '{}' 回滚成功（包含健康检查）", name);
}

private TaskRuntimeContext buildRollbackContext(TaskRuntimeContext ctx, 
                                                 TenantDeployConfigSnapshot snap) {
    TaskRuntimeContext rollbackCtx = new TaskRuntimeContext(
        ctx.getPlanId(), ctx.getTaskId(), ctx.getTenantId()
    );
    
    // ✅ 核心：装填旧配置数据（previousConfig）
    rollbackCtx.addVariable("deployUnitVersion", snap.getDeployUnitVersion());
    rollbackCtx.addVariable("deployUnitId", snap.getDeployUnitId());
    rollbackCtx.addVariable("deployUnitName", snap.getDeployUnitName());
    rollbackCtx.addVariable("healthCheckEndpoints", snap.getNetworkEndpoints());
    
    // 保留必要的原始上下文
    rollbackCtx.addVariable("planVersion", ctx.getAdditionalData("planVersion"));
    
    return rollbackCtx;
}
```

**关键点**：
- ✅ **零 Step 改动**：ConfigWriteStep、HttpRequestStep、PollingStep 完全复用
- ✅ **零 Stage 重新编排**：stepConfigs 列表直接复用
- ✅ **数据准备集中**：只需要 buildRollbackContext() 装填旧配置
- ✅ **健康检查自动执行**：PollingStep 自动检查旧版本（从 rollbackCtx 读取 expectVersion）

**为什么这样设计？**
1. 正向执行和回滚执行的**流程是一样的**（通知 → 配置 → 健康检查）
2. 唯一区别是**数据不同**（新配置 vs 旧配置）
3. 所以只需要在**数据层面**替换，**逻辑层面**完全复用

---

### 3. 部分失败处理增强（P2, 2h）

```java
// TaskExecutor.rollback()
List<String> failedStages = new ArrayList<>();
StringBuilder failureDetails = new StringBuilder();

for (TaskStage stage : reversedStages) {
    try {
        stage.rollback(context);
    } catch (Exception ex) {
        failedStages.add(stage.getName());
        failureDetails.append(String.format("[%s]: %s; ", 
            stage.getName(), ex.getMessage()));
        log.error("Stage 回滚失败: {}", stage.getName(), ex);
    }
}

if (!failedStages.isEmpty()) {
    FailureInfo failure = FailureInfo.of(
        ErrorType.ROLLBACK_PARTIAL_FAILED,
        String.format("部分 Stage 回滚失败（%d/%d）: %s", 
            failedStages.size(), reversedStages.size(), 
            String.join(", ", failedStages)),
        failureDetails.toString()
    );
    taskDomainService.failRollback(task, failure, context);
}
```

---

## 🧪 测试策略

### 单元测试（≥ 80% 覆盖）

```java
// 1. 配置传递
@Test void should_set_previous_config_snapshot()

// 2. Stage 回滚逻辑
@Test void should_reuse_steps_when_rollback()
@Test void should_use_previous_config_data()

// 3. 部分失败处理
@Test void should_continue_when_one_stage_failed()
@Test void should_record_all_failed_stages()
```

### 集成测试（≥ 3 场景）

```java
// 1. 成功场景
@Test void should_rollback_successfully()

// 2. 失败场景
@Test void should_fail_rollback_when_health_check_failed()

// 3. 部分失败场景
@Test void should_mark_rollback_failed_when_partial_failed()
```

---

## 🚦 决策清单（需确认）

### 立即决策（Phase 1 启动前）

- [ ] **Q1**：回滚语义 → 推荐**配置回滚**（选项 A）
- [ ] **Q2**：RollbackStrategy → 推荐**移除**（选项 A）
- [ ] **Q3**：健康检查 → 推荐**必须检查**（选项 A，超时 15s）

### 后续讨论（Phase 1 期间）

- [ ] **Q4**：部分失败 → 推荐 **Best-Effort**（全部尝试）
- [ ] **Q5**：ROLLBACK_FAILED 后 → 推荐**不支持重新回滚**（短期）
- [ ] **Q6**：重试策略 → 推荐**支持**（3 次，指数退避）

---

## 📅 实施计划

### Phase 1: 基础能力补全（21h, 3 天）

**Day 1**：
- [ ] 修复配置传递（2h）
- [ ] 实现 Stage 回滚逻辑（8h）

**Day 2**：
- [ ] 移除 RollbackStrategy（1h）
- [ ] 集成健康检查（4h）
- [ ] 补充单元测试（6h）

**Day 3**：
- [ ] 集成测试（3 场景）
- [ ] 代码审查
- [ ] 文档更新

### 验收标准

- [ ] `prevConfigSnapshot` 正确设置
- [ ] 回滚能重发 previousConfig 到 Redis、Gateway
- [ ] 健康检查通过（检查旧版本）
- [ ] 部分失败时记录详细错误
- [ ] 单元测试覆盖率 ≥ 80%
- [ ] 集成测试 3 场景通过

---

## 🔗 相关文档

- [详细分析报告](./rollback-capability-gap-analysis.md) - 完整技术方案
- [初始分析](./rollback-mechanism-analysis.md) - 问题发现报告
- [执行引擎设计](../design/execution-engine.md) - 架构参考
- [状态管理](../design/state-management.md) - 状态转换规则

---

**状态**：⏳ 待讨论  
**下一步**：确认 Q1-Q3 → 启动 Phase 1 实施

