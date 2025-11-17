# 术语表（Glossary）

## 核心概念
- **Plan**：包含一组租户级 Task 的计划；管理并发阈值与调度；可进入 PAUSED 状态。
- **Task**：租户级切换任务；仅在 Stage 边界响应暂停/取消；回滚/重试为手动。
- **Stage**：一组顺序 Step；失败短路；不可切片。
- **Step**：最小执行单元；示例：ConfigUpdate/Broadcast/HealthCheck。

## 持久化与恢复
- **Checkpoint**：阶段边界的进度快照；支持批量恢复与可插拔存储（InMemory/Redis）。
- **Rollback**：重发上一次可用配置（prevConfigSnapshot）；健康验证通过后更新 lastKnownGoodVersion。
- **prevConfigSnapshot**：上一次已知良好配置快照，包含 deployUnitId/Version/Name 和网络端点信息。
- **lastKnownGoodVersion**：经过健康检查确认的版本号，仅在验证通过后更新。

## 健康检查与监控
- **HealthCheck**：固定轮询间隔（3s），尝试上限（10），要求全通过。
- **Heartbeat**：每 10s 的任务进度心跳；携带 completed/total。
- **MetricsRegistry**：计量抽象；默认 Noop；可用 Micrometer 适配器替换。
- **MDC**：Mapped Diagnostic Context，注入 planId/taskId/tenantId/stageName；执行结束必须清理。

## 并发控制
- **ConflictRegistry**：租户级互斥锁，防止同租户并发任务。
- **maxConcurrency**：Plan 级并发阈值；=1 时严格 FIFO；>1 时并行执行，超出阈值进入等待队列。
- **TaskScheduler**：任务调度器，执行并发控制和 FIFO 排队。

## 事件与幂等
- **sequenceId**：事件幂等自增序号；消费者丢弃小于等于已处理的序列。
- **TaskEventSink**：事件发布抽象；支持 Spring ApplicationEvent 和 Noop 实现。

## 扩展点
- **StageFactory**：Stage 工厂，负责组装 Stage 步骤序列；支持声明式装配（计划中）。
- **TaskWorkerFactory**：TaskExecutor 工厂，封装执行器创建逻辑，便于注入依赖（如 MetricsRegistry）。
- **RollbackHealthVerifier**：回滚健康确认器；默认 AlwaysTrue，可替换为实际版本验证。
- **RollbackStrategy**：回滚策略接口；当前实现 PreviousConfigRollbackStrategy（重发上次配置）。

## 状态机
- **TaskStateMachine**：任务状态机，管理 13 种状态转换，支持 Guard（前置条件）和 Action（副作用）。
- **PlanStateMachine**：计划状态机，管理 Plan 级状态转换。
- **TaskStateManager**：状态管理器，统一管理状态机实例，执行状态转换并发布事件。
- **Guard**：状态转换守卫，前置条件检查（如 retryCount < maxRetry）。
- **Action**：状态转换动作，记录时间戳、清理资源等副作用。

## 上下文
- **TaskRuntimeContext**：运行时上下文，包含 MDC、暂停/取消标志、临时属性。
- **TaskTransitionContext**：状态转换上下文，传递给 Guard 和 Action，包含聚合、运行时上下文和总阶段数。

## 测试相关（Phase 17）
- **Testcontainers**：容器化测试框架，用于 Redis Checkpoint 集成测试。
- **Awaitility**：异步等待断言库，用于验证异步事件。
- **E2E Test**：端到端集成测试，覆盖完整业务场景。

