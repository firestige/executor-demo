# Executor Demo – Architecture Prompt (Updated)

Updated: 2025-11-16

Purpose
多租户蓝绿/灰度配置切换执行器：Plan 管理一组租户 Task；每个 Task 通过顺序 Stage 完成配置下发、广播与健康检查；支持并发阈值、FIFO、暂停/恢复、回滚、重试与心跳事件。

Current Architecture Snapshot
- 已完成基础重构：旧 ExecutionUnit / TaskOrchestrator / TenantTaskExecutor / ServiceNotificationStage 全部移除。
- 新核心：PlanAggregate / TaskAggregate / CompositeServiceStage / StageStep / HealthCheckStep / ConfigUpdateStep / BroadcastStep / TaskExecutor / TaskStateMachine / TaskStateManager / PlanOrchestrator / TaskScheduler / CheckpointService。

Domain Model
- Plan: 聚合多个租户 Task，控制 maxConcurrency 与租户冲突（同租户不能并发）。
- Task: 单租户切换任务；阶段边界支持暂停/取消；回滚与重试仅 Facade 手动触发；fromCheckpoint 重试会补偿一次进度事件。
- Stage: 顺序执行多个 Step；失败短路；回滚通过 RollbackStrategy 重新下发上一版可用配置。
- Step 类型：ConfigUpdateStep(下发新版本)、BroadcastStep(广播通知)、HealthCheckStep(轮询健康检查)。

Context Separation
- TaskRuntimeContext (现 TaskContext): 执行期上下文（MDC、pause/cancel 标志、临时数据）。
- TaskTransitionContext: 状态迁移评估上下文（TaskAggregate + RuntimeContext 引用 + totalStages）。用于 Guard/Action 决策，不直接暴露全部内部结构给执行层。

State & Events
- TaskStateMachine: 维护状态迁移规则；支持��册 Guard/Action（FAILED→RUNNING 重试限制、RUNNING→PAUSED、RUNNING→COMPLETED 等）。
- TaskStateManager: 持有状态机实例；构建 TaskTransitionContext；执行迁移后发布 Spring 事件（含 sequenceId 幂等）。
- 事件分类：Lifecycle(Started/Completed/Failed/Paused/Resumed)、Progress(Progress/Heartbeat)、Stage(StageStarted/StageSucceeded/StageFailed)、Rollback(RollingBack/RolledBack/RollbackFailed)、Validation(ValidationFailed/Validated)。

Rollback Strategy
- PreviousConfigRollbackStrategy: 使用 prevConfigSnapshot 重发旧配置（后续将恢复 deployUnitId/version/name 并更新 lastKnownGoodVersion + 健康确认）。
- 快照来源：TenantDeployConfig.sourceTenantDeployConfig 优先；否则初始切换目标作为基准。

Checkpoint
- CheckpointService(InMemory) 在 Stage 成功后保存；终态/回滚清理；后续扩展 Redis/DB SPI。

Concurrency & Scheduling
- TaskScheduler: 控制 maxConcurrency；maxConcurrency=1 时 FIFO；>1 时并发提交；结合 ConflictRegistry 防止同租���并发。
- PlanOrchestrator: 接收 Plan 提交，分发 Task 到调度器；未来扩展 pause/resume/rollback 路由。

Health Check
- 固定间隔 3s；最多 10 次；全部实例成功才通过；测试环境通过 stub 压缩尝试次数与间隔（不侵入生产逻辑）。

Heartbeat
- 每 10s 发布 TaskProgressEvent（completedStages/totalStages + 心跳作用）。

Configuration Priority
1) TenantDeployConfig（租户配置） 2) application 配置 3) 默认值；默认值未修改，仅增加 application 加载能力。

Extensibility Points (Reserved / Upcoming)
- Policies: RetryPolicy / PausePolicy / CompletionPolicy / RollbackPolicy / HealthCheckPolicy / CheckpointPolicy.
- Instrumentation: TransitionInstrumentation 钩子 beforeGuard/afterTransition。
- StageFactory: 抽象 buildStageForTask → 由配置驱动步骤组合。
- Persisted Checkpoints: RedisCheckpointStore / DBCheckpointStore + SPI selector。
- Metrics: Micrometer 指标 (task_active, task_completed, task_failed, rollback_count, heartbeat_lag)。

Upcoming Phases (High-Level)
- Phase 10: 状态机守卫与关键事件补齐 + 单测。
- Phase 11: 状态机统一化 retry/cancel + 回滚快照恢复 + durationMillis。
- Phase 12: Checkpoint SPI 持久化与批量恢复。
- Phase 13: 健康检查可配置化 + StageFactory。
- Phase 14: 指标与可观测性。
- Phase 15: 并发/性能压测与锁释放兜底。
- Phase 16: 文档终稿与升级迁移指南。

Key Invariants
- 同租户任务互斥执行。
- 作业暂停/取消仅在 Stage 边界响应。
- 完整健康检查通过后才更新 lastKnownGoodVersion（计划完善）。
- 所有事件携带 sequenceId；消费者按序幂等处理。
- 无旧体系类引用；新增不使用 V2/V3 命名。

Testing Guidelines
- 使用 Facade 创建任务；不直接调用内部聚合构造器。
- 模拟失败：通过 MockHealthCheckClient 或 stub Step。
- 重试覆盖 fromCheckpoint 与全量重跑；检查已完成 Stage 不重复执行。
- 回滚测试：验证状态变更与快照恢复（后续补充）。

How To Add A New Step
1) 实现 StageStep.execute(TaskContext)
2) 在 StageFactory 中注册步骤组合（例如 [ConfigUpdateStep, BroadcastStep, HealthCheckStep]）
3) Facade 在构建 Plan 时调用 StageFactory 返回 TaskStage 列表。

Quick Commands
```bash
mvn -q -DskipTests=false test
mvn -q -Dtest=xyz.firestige.executor.integration.FacadeE2ERefactorTest test
```

Do / Don’t
- DO: 通过 TaskStateManager 迁移状态；不要直接写 task.setStatus（后续 Phase 会完全移除）。
- DO: 保持单测快速稳定；避免睡眠依赖生产间隔。
- DON’T: 重新引入已删除的旧类或 ExecutionUnit 语义。

Glossary
- prevConfigSnapshot: 上一次可用配置快照，供快速回滚与 lastKnownGoodVersion 更新。
- lastKnownGoodVersion: 成功健康确认后的版本号（尚未在 rollback 完整实现中赋值）。
- sequenceId: 任务或计划事件内自增标识，用于消费幂等。

Maintenance Checklist (New Session)
1) 阅读 `TODO.md` Upcoming Phases 与 New Backlog。
2) 确认是否需要先执行 Phase 10（Guard/Action 单测与事件）。
3) 修改前运行 `mvn -q -DskipTests=false test` 作为基���。
4) 小步提交，更新 TODO 完成状态。

End.
