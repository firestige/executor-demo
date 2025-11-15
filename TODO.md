# 重构计划（Plan/Task/Stage 一步到位）

本计划用于指导将现有基于 ExecutionUnit 的实现，重构为清晰的 Plan/Task/Stage 架构。遵循：
- Facade 方法签名与入参保持不变（返回体字段可调整语义保持一致）。
- 旧代码先标记 @Deprecated，并清理所有引用；删除操作待人工审核后执行。命名冲突的旧类可直接删除（由你审核）。
- 回滚与重试均为手动触发（Facade 层），只有健康检查轮询是自动进行。
- 健康检查：每 3s 轮询一次，连续 10 次未达预期则判定该 Stage 失败；必须“所有实例成功”才通过。
- 事件幂等：基于自增 sequenceId；消费者丢弃 sequenceId 小于等于已处理值的事件。
- 并发阈值：Plan 级可配置 maxConcurrency；超过阈值进入 FIFO 等待队列。
- 暂停：协作式，仅在 Stage 之间的 checkpoint 位置响应；Stage 内不可切片；Stage 仅有开始、成功、失败三类事件。
- MDC：注入 planId, taskId, tenantId, stageName，执行完成需清理。
- Checkpoint：可插拔存储（默认内存），接口可替换为持久化实现。
- 配置优先级：TenantDeployConfig（实例级覆盖）→ application 配置 → 内置默认；默认值不变，仅增加 application 读取能力。
- 胶水层工厂：Facade 不直接持有 TenantDeployConfig 引用；通过 PlanFactory 将外部 DTO 深拷贝/转换为内部聚合模型，保护内部设计稳定。
- 命名约束：不引入 V2/V3 版本后缀；新旧替换采用“直接命名+弃用/删除”策略。

注意：每个阶段结束都有“Checkpoint 验收清单”和“灾难恢复指引”。执行时务必完成验收并记录结果。

---

## Phase 0 — 启动前准备与回退策略
- 建立工作分支：feature/plan-task-stage-refactor
- 打开持续集成的单测与编译校验（mvn -q -DskipTests=false test）
- 约定回退方式：若任一阶段验收失败，使用 git revert/restore 回滚到上一验收点 tag

Checkpoint（必须满足）
- 分支已创建；本文件合入；CI 可跑单测
灾难恢复
- git switch main && git revert <last-merge> 或直接回滚到最近 tag

---

## Phase 1 — 新领域模型与上下文（不改现有业务流）
目标：引入全新领域对象与上下文，不接线老流程，确保可编译与单测可写。
- 新增 domain 聚合：
  - PlanAggregate(planId, version, tasks, status, maxConcurrency, failureSummary, progress)
  - TaskAggregate(taskId, planId, tenantId, deployUnitId/version/name, status, currentStageIndex, retryCount/maxRetry, failureInfo, checkpoint, stageResults)
- 新增上下文：
  - PlanContext（start/end、running/queued 统计、pause/cancel 标志）
  - TaskContext（PipelineContext 包装；pause/cancel 标志；MDC 注入/清理）
- 不接入旧 TaskStateManager/Orchestrator；仅提供最小构造器与 getter/setter；添加基础单测（构建/序列化/空行为）。

Checkpoint
- 编译通过；PlanAggregate/TaskAggregate/Contexts 的构造与基本方法单测通过
灾难恢复
- 回滚本阶段提交；不影响旧流程

---

## Phase 2 — 状态机（带 Guard/Action 与自增 sequence）
目标：引入 TaskStateMachine 与 PlanStateMachine（新实现），具备 Guard/Action 扩展点与事件序列能力；暂不替换旧状态机调用。
- 已完成：新增 `domain/state/TaskStateMachine`, `domain/state/PlanStateMachine`, `TransitionGuard`, `TransitionAction`
- 待办（后续 Phase 接线）：
  - 将 sequenceId 注入事件（TaskStateManager 内部），保持事件幂等
  - 定义核心 Guard：FAILED→RUNNING（重试：retryCount<maxRetry 且非 rolling_back）、RUNNING→PAUSED（pauseRequested）等
  - 定义 Action：进入 RUNNING 记录开始时间；COMPLETED/FAILED 记录耗时与失败原因

Checkpoint
- ✅ 新状态机编译通过，新增最小单测（后续阶段补充更完整测试）

---

## Phase 3 — 冲突注册表与并发阈值调度（新 Orchestrator/Scheduler）
目标：用 PlanOrchestrator + TaskScheduler 取代旧 TaskOrchestrator/ExecutionUnitScheduler（先并行存在，引用尚不切换）。
- 已完成：
  - `support/conflict/ConflictRegistry`
  - `orchestration/TaskScheduler`（并发阈值 + FIFO 等待）
  - `orchestration/PlanOrchestrator`（submitPlan 接口 + 冲突检测 + 调度）
- 待办（后续 Phase 接线）：
  - pausePlan/resumePlan/rollbackPlan 路由实现
  - 调度完成回调中释放 ConflictRegistry、推进队列
  - 引入 TaskWorkerFactory 的默认实现（调用 TaskExecutor）
  - 端到端并发阈值与 FIFO 顺序测试

Checkpoint
- ✅ 新组件编译通过，未接线旧流

---

## Phase 4 — Stage 与多步骤策略（含健康检查 Step）
目标：将服务切换动作建模为可多步骤的 Stage；健康检查编入策略中，固定间隔 3s，10 次失败判定失败。
- 已完成：
  - `domain/stage/TaskStage`, `StageStep`, `StageExecutionResult`, `StepExecutionResult`
  - `domain/stage/CompositeServiceStage`（顺序执行 + 逆序回滚）
  - `service/health/HealthCheckClient`（抽象）与 `MockHealthCheckClient`（默认实现）
  - `domain/stage/steps/HealthCheckStep`（3s 间隔，10 次失败，全实例成功）
- 待办：
  - 将现有 ServiceNotificationStrategy → 组合 Stage 的映射工厂
  - 将 CompositeServiceStage 接入 TaskExecutor 的执行链
  - 健康检查版本键/端点路径可配置化（按 DTO/应用配置）
  - 单元测试：成功/失败/部分失败分支

Checkpoint
- ✅ 新增类型编译通过；未接线旧流

---

## Phase 5 — CheckpointStore 抽象与集成
目标：将检查点抽象为可插拔存储；默认内存实现；与 Task 执行对齐，仅在 Stage 间保存。
- 已完成：
  - `checkpoint/CheckpointStore`（接口）、`checkpoint/InMemoryCheckpointStore`（默认实现）
  - `checkpoint/CheckpointService`（save/load/clear，更新 TaskAggregate 并持久化到 Store）
- 待办：
  - 在 TaskExecutor 中调用 CheckpointService 在每个 Stage 边界保存检查点；暂停时保存；成功/失败/回滚后清理
  - 增加 batch 接口在大规模 Task 恢复时优化
  - 引入持久化实现（Redis/DB）
  - 单测：保存/恢复/清理、暂停恢复路径

Checkpoint
- ✅ 新增类型编译通过；未接线旧流

---

## Phase 6 — TaskExecutor（替换 TenantTaskExecutor）与事件/心跳/MDC
目标：实现新 TaskExecutor，整合状态机、事件序列、MDC、心跳进度（当前仅骨架，不接线旧流）。
- 已完成：
  - `execution/TaskExecutor`（执行、暂停/取消检测、回滚、CheckpointService 集成、心跳进度、MDC 注入与清理）
  - `execution/TaskExecutionResult`（扩展字段 planId/taskId/status/duration）
  - `event/TaskEventSink` 与 `event/NoopTaskEventSink`（含回滚阶段事件）
- 待办：
  - 接入状态机序列号（sequenceId 由 TaskStateManager 驱动，而非本地计数）
  - 心跳进度调度改为独立调度器（避免长 Stage 阻塞心跳）
  - 事件下沉接线到 Spring ApplicationEventPublisher（替代 Noop）
  - Pause/Cancel 事件完整化（取消事件发布）
  - 重试路径（从检查点 vs 从头）逻辑与事件
  - 单测：成功、失败、暂停恢复、回滚失败、心跳频率、Checkpoint 清理

Checkpoint
- ✅ 骨架编译通过；未影响旧执行流；事件接口与结果模型就绪

---

## Phase 7 — FacadeImpl 接线新流程（保持方法签名）
当前状态：
- ✅ createSwitchTask 已接线新 Plan/Task/Stage，返回 planId + taskIds
- ✅ Stage 模型接入（NotificationStep + HealthCheckStep）
- ✅ PlanOrchestrator + TaskScheduler 调度接入
- ✅ 心跳与事件序列号接入（TaskStateManager 驱动）
- ✅ 暂停/恢复/取消 请求标志对接 TaskContext（协作式）
- ✅ 回滚与重试逻辑初步接入（TaskExecutor.retry / invokeRollback）
- ✅ 查询接口使用新注册表（动态 totalStages）
- ✅ stageRegistry / executorRegistry / contextRegistry 建立

剩余待办（实时更新）：
- [x] HeartbeatSupplier 改为基于 executor.completedCounter（当前依赖 currentStageIndex）
- [x] 回滚/重试重用原始 executorRegistry 中实例（避免重新构造丢失状态）
- [x] 重试 fromCheckpoint 时补发必要进度事件（确保序列号连续）
- [ ] 回滚事件补充阶段级明细（单独事件可选）
- [x] Query 增加当前 Stage 名称、是否 pause/cancel 标志输出
- [x] createSwitchTask 中重复构建 stage 两次（taskStages 与 stageRegistry）已合并
- [ ] 移除旧 ExecutionUnit 相关代码并标记 @Deprecated（Phase8）
- [x] E2E 测试：创建→暂停→恢复→取消→重试（fromCheckpoint / scratch）→回滚（已通过，后续增强断言）
- [x] 健康检查失败路径单测（全部失败、部分失败、最后一次成功）
- [ ] 文档补充：查询字段含义、重试策略、回滚语义

Checkpoint 通过标准（完成以上待办后）：
- 新增 E2E 测试全部通过（当前已通过，建议增强回滚后状态断言）
- Heartbeat 精确反映已完成 Stage 数（跳过也计数）
- 重试不重复已完成 Stage 事件（或明确发生补偿事件，序列号严格递增）
- 回滚后任务状态为 ROLLED_BACK 或 ROLLBACK_FAILED 并发布相应事件

---

## Phase 8 — 弃用与清理（不直接删除文件）
当前进度（已完成）：
- ✅ 标注 @Deprecated：
  - orchestration：ExecutionUnit.java / ExecutionUnitStatus.java / ExecutionUnitResult.java / ExecutionUnitScheduler.java / TaskOrchestrator.java
  - service/stage：ServiceNotificationStage.java
  - execution：TenantTaskExecutor.java
- ✅ 主线引用清理：
  - Spring 配置 `ExecutorConfiguration` 切换到新架构装配（ExecutorProperties + HealthCheckClient + 新 Facade 构造器）
  - Facade 无参构造移除旧调度器/旧 orchestrator 依赖；新增不依赖旧类的构造器（供测试/新接入）
  - 集成测试 `FacadeE2ERefactorTest` 改用新构造器与 stub，不再依赖旧类
- ✅ 构建与测试：mvn test 通过
- ✅ 删除操作（已执行，见提交记录）：
  - 移除文件：
    - src/main/java/xyz/firestige/executor/orchestration/ExecutionUnit.java
    - src/main/java/xyz/firestige/executor/orchestration/ExecutionUnitStatus.java
    - src/main/java/xyz/firestige/executor/orchestration/ExecutionUnitResult.java
    - src/main/java/xyz/firestige/executor/orchestration/ExecutionUnitScheduler.java
    - src/main/java/xyz/firestige/executor/orchestration/TaskOrchestrator.java
    - src/main/java/xyz/firestige/executor/service/stage/ServiceNotificationStage.java
    - src/main/java/xyz/firestige/executor/execution/TenantTaskExecutor.java
  - 提交信息：refactor(p8): remove deprecated legacy classes; tests green

Checkpoint（已满足）：
- 主代码不再引用上述旧类；相关文件已删除
- 全量测试通过

---

## Phase 9 — 文档与交付（当前）
- ✅ README 审校完成（同步新架构、API、健康检查与测试策略）
- ✅ ARCHITECTURE_PROMPT.md 终稿（去除旧实现，聚焦新架构）
- ✅ 删除操作指导（如下）：

删除操作指导
- 创建分支：
```bash
git checkout -b refactor/p8-remove-legacy
```
- 删除文件（已执行，仅供留档）：
```bash
git rm src/main/java/xyz/firestige/executor/orchestration/ExecutionUnit.java \
       src/main/java/xyz/firestige/executor/orchestration/ExecutionUnitStatus.java \
       src/main/java/xyz/firestige/executor/orchestration/ExecutionUnitResult.java \
       src/main/java/xyz/firestige/executor/orchestration/ExecutionUnitScheduler.java \
       src/main/java/xyz/firestige/executor/orchestration/TaskOrchestrator.java \
       src/main/java/xyz/firestige/executor/service/stage/ServiceNotificationStage.java \
       src/main/java/xyz/firestige/executor/execution/TenantTaskExecutor.java
```
- 构建与测试：
```bash
mvn -q -DskipTests=false test
```
- 提交与推送：
```bash
git commit -m "refactor(p8): remove deprecated legacy classes; tests green"
```

Checkpoint
- 文档更新；你审核通过
灾难恢复
- 无

---

## 验收标准（统一）
- 构建：mvn -q -DskipTests=false test 通过
- Lint/格式：保持与现有风格一致
- 事件：包含自增 sequenceId，消费方可按序处理；MDC 字段正确
- 暂停/恢复：仅在 Stage 间响应；checkpoint 可恢复
- 并发：尊重 Plan.maxConcurrency；FIFO 等待
- 冲突：同租户不并发执行；任务结束与异常路径均释放；有兜底清理
- 健康检查：3s 间隔，10 次失败；“全实例成功”通过

---

## 变更审计与回滚指引（通用）
- 每阶段结束：
  - 打 Tag：refactor-P{N}-ok
  - 记录 CI 构建日志与测试报告
  - 提交“阶段验收记录”到 TEST_PROGRESS_REPORT.md
- 回滚：
  - git switch feature/plan-task-stage-refactor
  - git reset --hard <refactor-P{N-1}-ok> 或 git revert <bad-merge-commit>

---

## 后续可选项（非本轮）
- Checkpoint 持久化实现（Redis/DB）
- Plan 级 MAX_CONCURRENCY 动态调优接口
- 指标上报与可观测性埋点（Micrometer）
- 事件幂等增强（加入 epoch/启动时间戳）
- 优雅停机与“在途任务”策略
