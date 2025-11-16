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

## Completed History (归档已完成内容，不再作为当前执行 Phase)
- Phase 0: 启动准备与分支建立 (已完成)
- Phase 1: 新领域模型与上下文引入 (已完成)
- Phase 2: 初版状态机 (TaskStateMachine + 基础 Guard/Action 接口) (已完成基础, 等待增强单测)
- Phase 3: 冲突注册表与调度骨架 (已完成初版)
- Phase 4: Stage/Step 机制与 HealthCheckStep (已完成)
- Phase 5: Checkpoint 抽象与内存实现 (已完成)
- Phase 6: 新 TaskExecutor 骨架 + 心跳 + MDC + 回滚/重试基础 (已完成接线, 等待状态机统一化 retry/cancel)
- Phase 7: Facade 接线新架构 (已完成)
- Phase 8: 旧体系文件清理 (已完成, legacy 删除)
- Phase 9: 文档初步更新 (README / ARCHITECTURE_PROMPT) (已完成)

> 以上阶段不再参与后续排期；需要回溯时参考提交 Tag 与此列表。

---

## Upcoming Phases (现行与未来执行计划)

### Phase 10 – 状态机强化与关键事件补齐
目标: 完善 Guards/Actions, 引入 RetryStarted/RetryCompleted, per-stage rollback events, 补核心测试。
任务:
- Guards 单测 (FAILED→RUNNING, RUNNING→COMPLETED, RUNNING→PAUSED)
- Actions: 记录 startedAt / endedAt / durationMillis
- 事件: RetryStarted / RetryCompleted (fromCheckpoint 标志), StageRollingBack / StageRolledBack / StageRollbackFailed
- Heartbeat 长耗时 Step 测试
- FIFO & 并发单测 (maxConcurrency=1 & >1)
验收: 测试全部通过, 事件日志包含新增事件, 序列号连续。

### Phase 11 – 状态机统一化 + 回滚快照恢复
目标: 去除直接 task.setStatus, 回滚使用 prevConfigSnapshot 恢复版本, 查询补充新字段。
任务:
- 替换 retry/cancel 直接赋值 → stateManager.updateState
- PreviousConfigRollbackStrategy: 恢复 deployUnitId/version/name + lastKnownGoodVersion
- TaskAggregate.durationMillis 字段 + 查询输出
验收: 重试/取消路径仅通过状态机, 回滚后版本/lastKnownGoodVersion 正确, 查询显示 durationMillis。

### Phase 12 – Checkpoint 增强与持久化 SPI
目标: 完善 checkpoint 行为与可插拔存储。
任务:
- 每 Stage 结束保存, 终态/回滚后清理统一方法
- Batch 恢复 API
- RedisCheckpointStore 占位实现 + SPI 选择 (memory|redis|db)
验收: 单测覆盖暂停→恢复、回滚后清理、批量恢复。

### Phase 13 – 健康检查可配置化与 Step 工厂
目标: 解耦硬编码 versionKey/path, 统一 Stage 构建工厂。
任务:
- ExecutorProperties 支持 healthCheck.versionKey / healthCheck.path
- HealthCheckStep 使用配置值
- 通用 StageFactory (将 buildStageForTask 逻辑抽离)
验收: 配置项变更反映到健康检查请求; 单测覆盖自定义键/路径。

### Phase 14 – 可观测性与指标
目标: 引入基础 Micrometer 指标与 MDC 稳定性验证。
任务:
- 指标: task_active, task_completed, task_failed, rollback_count, heartbeat_lag
- MDC 清理测试 & 日志结构化
验收: 指标注册成功, MDC 无残留, 日志包含标准字段。

### Phase 15 – 性能与批量恢复/并发压测
目标: 验证在高并发与大批量任务下的性能与稳定性。
任务:
- 压测脚本/基准测试 (大规模计划, 并发租户)
- 锁释放兜底策略测试
- Batch 恢复性能评估
验收: 基准结果记录, 无死锁/泄漏, 锁释放彻底。

### Phase 16 – 文档与治理终稿
目标: 完成最终架构文档、事件 payload 示例与扩展指南。
任务:
- README: 事件示例/扩展策略章节
- ARCHITECTURE_PROMPT: 上下文分离 (Runtime vs Transition), RollbackStrategy, 扩展点列表
- 升级迁移指南 (旧→新)
验收: 文档经审核无遗留旧术语, 与实现同步。

---

## Remaining Backlog (主题分类参考, 与 Phases 对应)
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
