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

---

## Remaining Backlog (未完成待办梳理)
> 以下为尚未完成或部分完成的任务，已按主题重新分解，供后续迭代：

### 1. 状态机与语义强化 (State Machine Enhancements)
- Implement TransitionGuard: FAILED→RUNNING 需满足 retryCount < maxRetry 且非 rolling_back
- Implement TransitionGuard: RUNNING→PAUSED 仅在 pauseRequested=true
- Implement TransitionAction: RUNNING 设置 startTime, COMPLETED/FAILED 计算 duration & failureInfo 注入
- Add PlanStateMachine transitions (e.g., PENDING→RUNNING, RUNNING→PAUSED, RUNNING→COMPLETED/FAILED)

### 2. 编排与并发控制 (Orchestrator & Concurrency)
- Plan-level pause/resume/rollback 通过 PlanOrchestrator 路由（当前直接遍历 tasks）
- 调度完成回调：释放 ConflictRegistry 租户锁并 FIFO 队列推进
- Introduce TaskWorkerFactory 封装 TaskExecutor 构建逻辑
- End-to-end 测试：maxConcurrency=1 时 FIFO 顺序保证；>1 时并发正确性

### 3. Stage/Step 工厂化与配置 (Stage Factory & Config)
- ServiceNotificationStrategy → CompositeServiceStage 的通用映射工厂（替换硬编码 buildStageForTask）
- ExecutorProperties 增加 healthCheckVersionKey / healthCheckPath 配置项
- HealthCheckStep 使用上述配置项而非硬编码 "version" / 默认 path
- 单测：部分实例失败场景（非全部成功）导致 Stage 失败

### 4. Checkpoint 能力扩展 (Checkpoint & Persistence)
- 在每个 Stage 结束自动保存 checkpoint（核实现状，不足则补）
- 成功/失败/回滚后集中清理 checkpoint（统一方法 + 单测）
- Batch 恢复接口：loadMultiple(List<taskId>) 提升批量恢复效率
- 可插拔持久化实现（Redis/DB）与 SPI（占位实现 + 配置切换）
- 单测：暂停恢复后继续正确接续；回滚后 checkpoint 清理验证

### 5. 事件模型增强 (Event Model)
- Per-stage rollback events: publishTaskStageRollingBack / publishTaskStageRollbackFailed / publishTaskStageRolledBack
- RetryStarted / RetryCompleted 事件（含 fromCheckpoint 标志）
- Cancel 事件 enrichment：增加 cancelledBy（system/manual）+ lastStage
- ProgressDetail 事件：统一使用 completedStages/totalStages（核实序列）
- 序列号一致性测试：事件 sequenceId 严格递增，无跳号/回退

### 6. 测试覆盖补强 (Testing Coverage)
- 并发与 FIFO 行为测试（含冲突租户第二任务等待）
- 心跳频率测试：模拟长 Stage 确认 HeartbeatScheduler 独立运行
- Checkpoint 保存/恢复/清理全路径测试
- 回滚失败路径：模拟某个 Stage rollback 抛异常 → 全局失败事件
- MDC 清理：验证执行后线程上下文无残留（使用 ThreadLocal 检查）
- Retry from scratch vs fromCheckpoint 差异测试（事件补偿、已完成 Stage 不重复执行）

### 7. 持久化与扩展 (Persistence & Extensibility)
- RedisCheckpointStore 占位实现（内存接口复制 + 注释待实现）
- DBCheckpointStore 设计草稿（暂不实现细节）
- SPI 选择机制：properties 中 checkpoint.storeType=memory|redis|db

### 8. 可观测性与指标 (Observability)
- Micrometer 指标：task_active、task_completed、task_failed、rollback_count、heartbeat_lag
- 日志 MDC 字段稳定性测试（planId/taskId/tenantId/stageName）

### 9. 文档与治理 (Docs & Governance)
- README 补充：事件列表与示例 payload
- ARCHITECTURE_PROMPT 增加“扩展策略/新增 Step 流程”小节
- 升级说明：从旧架构迁移步骤（已删除旧类，补迁移指引）

## Phase 10 — 迭代计划（第一批执行）
> 聚焦最小可行增强：状态机守卫 + 事件完善 + 基础测试补强

### 10.1 目标
在不破坏现有通过测试的前提下，补齐核心领域约束与关键事件，为后续性能与持久化扩展奠定基础。

### 10.2 待办列表 (Actionable Tasks)
1. Implement TransitionGuard & TransitionAction（FAILED→RUNNING、RUNNING→PAUSED、RUNNING/COMPLETED/FAILED Actions）
2. Add RetryStarted / RetryCompleted events（含 fromCheckpoint 字段）并接入 Facade 重试逻辑
3. Add per-stage rollback events（遍历阶段调用 publishTaskStageRollingBack / publishTaskStageRolledBack / publishTaskStageRollbackFailed）
4. HeartbeatScheduler 独立线程池验证（单测：模拟长耗时 Step）
5. 并发与 FIFO 单测：maxConcurrency=1 + 冲突租户 second task 排队
6. Checkpoint 保存/清理核实：补单测（暂停→恢复→完成；回滚后清理）
7. 序列号递增一致性测试（抓取事件序列）
8. README 补充事件示例（Started/StageSucceeded/RetryStarted/RolledBack）

### 10.3 验收标准
- 新增事件（RetryStarted/RetryCompleted、StageRollingBack）出现于测试日志并通过断言
- 状态机非法转换（例如 PAUSED→COMPLETED 不直接跳）触发 Guard 拦截测试
- FIFO 测试中第二任务在前一任务完成前不进入 RUNNING
- 心跳在长耗时 Step 期间仍按间隔触发（>=2 次）
- 回滚后 checkpoint 全部清理（查询接口无残留 checkpoint 信息）

### 10.4 回退策略
- 引入状态机文件时单独提交；若失败可 git revert 该提交
- 事件模型扩展独立提交；出现消费者兼容问题可单独回滚

## Phase 11+ 展望（后续批次）
- HealthCheck 可配置化（versionKey/path）
- RedisCheckpointStore & SPI 切换
- Metrics + MDC 自动化测试
- Batch 恢复与性能压测

## 状态同步
- “文档补充：查询字段含义、重试策略、回滚语义” 已在 README 与 ARCHITECTURE_PROMPT 完成，标记为 ✅（原 TODO 未勾选）
- “回滚事件补充阶段级明细” 部分完成（总事件含阶段列表），需补 per-stage 事件 → 保持为进行中

---

## 新增待办（本次交互补充）
### 状态机与行为一致性增强
- [ ] Guard/Action 单测：
  - FAILED→RUNNING 重试上限拦截（retryCount == maxRetry 禁止迁移）
  - RUNNING→COMPLETED 仅在 currentStageIndex == totalStages 时允许
  - RUNNING→PAUSED 在 pauseRequested=true 时允许，其他拒绝
  - 验证非法迁移保持原状态（no-op）并不发布事件
- [ ] 回滚时恢复 prevConfigSnapshot：
  - PreviousConfigRollbackStrategy 在成功回滚后重置 deployUnitVersion/deployUnitId/deployUnitName 到快照值
  - 设置 lastKnownGoodVersion=快照版本，并发布 TaskStageRolledBack/TaskRolledBack 事件
- [ ] 用状态机替换 retry/cancel 的直接赋值：
  - retry(fromCheckpoint=false) 将 PENDING→RUNNING 通过 stateManager.updateState 而非 task.setStatus
  - cancelTask 使用 stateManager.updateState(taskId, CANCELLED) 替代直接赋值
  - 单测覆盖：取消后不再允许 RUNNING→COMPLETED
- [ ] 记录任务持续时间字段：
  - TaskAggregate 增加 durationMillis 字段（COMPLETED/FAILED/RolledBack 设置）
  - Action 在 COMPLETED/FAILED/ROLLED_BACK/ROLLBACK_FAILED 时写入 durationMillis = endedAt-startedAt
  - 查询接口 TaskStatusInfo 输出 durationMillis（若存在）

### 后续执行顺序建议
1. 增加 TaskAggregate.durationMillis 字段及状态机 Action 设置逻辑
2. 更新 PreviousConfigRollbackStrategy 以恢复快照并设置 lastKnownGoodVersion
3. 改造 cancel & retry 流程调用 stateManager.updateState
4. 编写 Guard/Action 单测（分文件：RetryGuardTest, PauseGuardTest, CompletionGuardTest）
5. 扩展查询 DTO 输出 durationMillis 与 lastKnownGoodVersion
