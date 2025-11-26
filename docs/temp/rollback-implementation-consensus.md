# Rollback 方案确认

**日期**：2025-11-26  
**状态**：✅ 已达成共识

---

## 核心共识

### ✅ 回滚 = 用旧配置再走一遍正常流程（仅针对 Task）

**本质**：
- 不是"撤销操作"，而是"重新执行"
- 唯一区别：数据源从新配置替换为旧配置
- 流程、逻辑、编排完全复用（Stage/Step 不变）
- ❗ 范围澄清：回滚是 Task 级操作，Plan 不存在回滚概念（Plan 状态不受回滚直接影响）

**优势**：
- ✅ **零 Step 代码改动**
- ✅ **零 Stage 重新编排**
- ✅ **零 DataPreparer 改动**
- ✅ **新增功能自动支持回滚**

---

## 范围澄清（重要）

| 项 | 是否支持回滚 | 说明 |
|----|--------------|------|
| Plan | ❌ 不支持 | 回滚不改变 Plan 生命周期；Plan 继续维持其聚合内 Task 的终态统计 |
| Task | ✅ 支持 | FAILED / PAUSED 触发回滚；状态机含 ROLLING_BACK / ROLLED_BACK / ROLLBACK_FAILED |
| Task 内 Stage | ✅ 间接 | 通过复用执行顺序重新应用旧配置，不做逆序补偿 |

> 回滚后是否影响 Plan 的最终状态（例如 Plan 原来已 COMPLETED，但某个 Task 回滚）：保持现状（设计：Plan 不感知回滚）。后续若需要“Plan 可感知回滚”作为增强再讨论。

---

## 设计决策

### Q1: 回滚语义（Task 级）
**决策**：✅ 配置回滚（完全复用正向流程）

**实现（在 TaskOperationService 中编排）**：
```java
// TaskOperationService 伪代码（单任务回滚）
public TaskOperationResult rollbackTask(TaskId taskId) {
  TaskAggregate task = taskRepository.findRequired(taskId);
  TenantDeployConfigSnapshot prev = task.getPrevConfigSnapshot();
  assertRollbackPreconditions(task, prev); // 状态与快照校验

  taskDomainService.startRollback(task, domainContext); // 进入 ROLLING_BACK

  TaskRuntimeContext originalCtx = runtimeRepo.getContext(taskId)
      .orElse(createAndSaveDefaultContext(task));
  TaskRuntimeContext rollbackCtx = buildRollbackContext(task, originalCtx, prev);
  runtimeRepo.saveContext(taskId, rollbackCtx);

  // 异步提交给执行层（可直接委托 TaskExecutor）
  submitAsync(() -> {
     TaskExecutor executor = ensureExecutor(task);
     TaskResult result = executor.executeStages(rollbackCtx, ExecutionMode.ROLLBACK);
     // 根据 result 内聚合失败情况调用 completeRollback 或 failRollback
  });

  return TaskOperationResult.accepted(task.getTaskId(), task.getStatus(), "回滚已提交");
}
```

> 不存在“Plan 级批量回滚 orchestrateRollback()”的需求；当前不提供 Plan → 全部 Task 自动回滚的统一入口。

### Q2: RollbackStrategy
**决策**：✅ 移除独立策略类；逻辑集中在 TaskOperationService（每次按需构造旧配置上下文）。

### Q3: 健康检查
**决策**：✅ 必须执行（自动复用 PollingStep）

**配置建议**：
- 回滚健康检查最大次数：5
- 间隔：3 秒
- 超时失败：标记 ROLLBACK_FAILED + 事件通知 + 告警

---

## 编排方法签名建议（最终版）

### TaskOperationService（新增/强化）
```java
public TaskOperationResult rollbackTask(TaskId taskId);
public TaskOperationResult rollbackTaskByTenant(TenantId tenantId); // 委托 taskId

// 辅助内部方法
private void assertRollbackPreconditions(TaskAggregate task, TenantDeployConfigSnapshot prev);
private TaskRuntimeContext buildRollbackContext(TaskAggregate task, TaskRuntimeContext originalCtx, TenantDeployConfigSnapshot prev);
private void submitAsyncRollback(TaskAggregate task, TaskRuntimeContext rollbackCtx);
private TaskExecutor ensureExecutor(TaskAggregate task);
```

### TaskExecutor（执行阶段复用）
```java
public TaskResult executeStages(TaskRuntimeContext ctx, ExecutionMode mode); // mode=ROLLBACK 时顺序执行旧配置
```

### Domain 扩展（如未存在）
```java
public void startRollback(TaskAggregate task, TaskRuntimeContext ctx);
public void completeRollback(TaskAggregate task, TaskRuntimeContext ctx);
public void failRollback(TaskAggregate task, FailureInfo failure, TaskRuntimeContext ctx);
```

### 枚举（可选）
```java
enum ExecutionMode { NORMAL, ROLLBACK };
```

---

## 实施计划（调整：仅 Task 级）

### Phase 1（21h）
- 修复 Task 创建时 previousConfigSnapshot 设置（2h）
- 新增 TaskOperationService.rollbackTask / rollbackTaskByTenant（6h）
- TaskExecutor.executeStages 支持 ExecutionMode=ROLLBACK（3h 重构现有 rollback）
- 移除 PreviousConfigRollbackStrategy（1h）
- 失败信息与 FailureInfo 细化（2h）
- 单元测试（6h）
- 集成测试（3 场景：成功 / 健康检查失败 / 部分Stage失败）（4h）
- 文档与审查（2h）

---

## 修改清单（更新）

1. **TaskDomainService.java**：设置 prevConfigSnapshot（保持不变）
2. **TaskOperationService.java**：新增 rollbackTask / rollbackTaskByTenant + 内部辅助方法（替代原“prepareRollbackByTenant”若语义不符需合并）
3. **TaskExecutor.java**：重构 rollback → executeStages(ctx, ROLLBACK)
4. **PreviousConfigRollbackStrategy.java**：删除
5. **FailureInfo / ErrorType**：补充 ROLLBACK_PARTIAL_FAILED / ROLLBACK_HEALTH_CHECK_FAILED / ROLLBACK_PRECONDITION_FAILED

> 不再修改 TaskExecutionOrchestrator（仅用于 Plan 级创建/启动，不承载回滚）。

---

## 验收标准（保持不变但强调 Task 范围）
- 创建 Task 时 `prevConfigSnapshot` 正确
- rollbackTask(TaskId) 能使用旧配置重跑全部 Stage（顺序与正向一致）
- Redis/Gateway/Nacos 等目标系统收到旧版本数据覆盖
- PollingStep 验证恢复到旧版本
- 失败场景：健康检查失败 → ROLLBACK_FAILED；部分 Stage 异常 → FailureInfo 包含明细
- Plan 状态不发生“回滚”语义变化（不新增 Plan 回滚状态）

---

## 风险与缓解（Task 级不变）

### 风险 1：健康检查失败率高
**缓解**：
- 回滚前确认 previousConfig 是历史成功的配置
- 设置合理的超时时间（15 秒）
- 失败后触发告警，人工介入

### 风险 2：Redis 或 Gateway 不可用
**缓解**：
- 自动重试 3 次（指数退避）
- 失败标记 ROLLBACK_FAILED
- 详细记录失败原因（ErrorType.ROLLBACK_PARTIAL_FAILED）

### 风险 3：previousConfig 数据不完整
**缓解**：
- 在 Facade 层校验 previousConfig 的完整性
- 创建 Task 时检查 prevConfigSnapshot 是否为 null
- 回滚前再次检查 prevConfigSnapshot

---

## 后续优化（不变）

### 可选优化（不阻塞 Phase 1）

1. **Stage 级别重试**（P2, 4h）
   - 回滚失败时自动重试 3 次
   - 配置化：`executor.rollback.max-retries=3`

2. **回滚可观测性**（P2, 3h）
   - 补充回滚指标（rollback_success, rollback_failed）
   - 补充 Stage 回滚事件（TaskStageRollbackCompletedEvent）

3. **重新回滚支持**（P3, 未来）
   - 支持 ROLLBACK_FAILED → ROLLING_BACK
   - 需修改状态机

---

## 相关文档

- [详细分析报告](./rollback-capability-gap-analysis.md) - 11 章完整分析
- [数据流示例](./rollback-data-flow-example.md) - 可视化数据流
- [讨论摘要](./rollback-discussion-summary.md) - 快速决策指南
- [流程对比图](./rollback-flow-comparison.puml) - 当前 vs 修复后

---

**状态**：✅ 已根据“回滚仅针对任务”澄清并收敛范围  
**下一步**：进入任务拆分与实现阶段（仍保持不修改代码，等待最终批准）
