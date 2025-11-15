# Executor Demo – Architecture Prompt (Quick Context Recovery)

Updated: 2025-11-15

Use this prompt at the start of any new session to restore working knowledge of this repository. It summarizes the domain, main modules, contracts, and how to work effectively with the codebase.

Role and focus
- You are an expert Java/Spring engineer working in this repo.
- Prioritize correctness, small diff edits, strong tests, and tangible outcomes.
- Prefer concrete code changes over generic advice; run tests after non-trivial edits.

Project purpose (what this system does)
- A multi-tenant blue/green deployment switch executor.
- Orchestrates tenant-level service configuration rollout via a pipeline of service notifications.
- Goals: async execution, tenant isolation (FIFO per tenant), cross-tenant concurrency, checkpoint-based pause/resume, strict state machine, event-driven observability.

Key packages and responsibilities
- facade
  - DeploymentTaskFacade, DeploymentTaskFacadeImpl: external API layer to create/operate tasks (create/pause/resume/rollback/retry, query status). Aggregates ValidationChain + TaskOrchestrator + TaskStateManager.
- orchestration
  - TaskOrchestrator: manages ExecutionUnit lifecycle, routing, tenant/plan mappings, conflict detection (via ConflictValidator), pause/resume/rollback.
  - ExecutionUnit: a batch of tenant configs executed in CONCURRENT or FIFO mode.
  - ExecutionUnitScheduler: thread-pool based scheduler creating Pipelines via PipelineFactory; executes tenant tasks concurrently or FIFO.
  - ExecutionMode, ExecutionUnitStatus/Result: execution-mode and status modeling.
- execution (+ execution.pipeline)
  - TenantTaskExecutor: executes one tenant task; publishes start/complete/fail events; moves TaskState via TaskStateManager.
  - Pipeline: ordered execution of PipelineStage with canSkip, pause/cancel checks, checkpointing via CheckpointManager (InMemoryCheckpointManager impl).
  - StageResult, StageStatus, PipelineResult: result modeling.
- service
  - strategy: ServiceNotificationStrategy interface with DirectRpcNotificationStrategy, RedisRpcNotificationStrategy; supports validateConfig, notify, rollback.
  - stage: ServiceNotificationStage adapts a ServiceNotificationStrategy into a PipelineStage.
  - adapter: ServiceNotificationAdapter composes multiple strategies, supports forward execute and reverse rollback.
  - registry: ServiceRegistry collects and exposes strategies used to construct the Pipeline.
- state
  - TaskStatus, TaskStateMachine: explicit transition map; illegal transitions blocked.
  - TaskStateManager: owns TaskStateMachines per task; publishes Spring events (state.event.*) after successful transitions.
  - state.event.*: rich lifecycle events (Created/Validated/Started/Progress/StageCompleted/StageFailed/Completed/Failed/Paused/Resumed/RollingBack/RolledBack/RollbackFailed/ValidationFailed).
- validation
  - ValidationChain: ordered ConfigValidators with optional fail-fast.
  - validators: TenantIdValidator, NetworkEndpointValidator, BusinessRuleValidator, ConflictValidator (also tracks running tenants for conflict prevention).
  - ValidationResult/ValidationSummary/ValidationError/ValidationWarning.
- config
  - ExecutorConfiguration: Spring wiring for CheckpointManager, TaskStateManager, ServiceRegistry (registers sample strategies), and PipelineFactory that builds a Pipeline from the registry’s strategies.

Important classes/files to skim first
- executor/config/ExecutorConfiguration.java
- executor/facade/DeploymentTaskFacade.java, DeploymentTaskFacadeImpl.java
- executor/orchestration/TaskOrchestrator.java, ExecutionUnitScheduler.java, ExecutionUnit.java
- executor/execution/TenantTaskExecutor.java
- executor/execution/pipeline/Pipeline.java, PipelineContext.java, CheckpointManager.java, InMemoryCheckpointManager.java, PipelineStage.java
- executor/service/stage/ServiceNotificationStage.java
- executor/service/strategy/*.java, executor/service/adapter/ServiceNotificationAdapter.java, executor/service/registry/ServiceRegistry.java
- executor/state/* (TaskStateMachine, TaskStateManager, TaskStatus, state.events)
- executor/validation/* (ValidationChain, validators, results)
- dto/TenantDeployConfig.java

End-to-end flow (happy path)
1) Facade receives configs: DeploymentTaskFacadeImpl.createSwitchTask(List<TenantDeployConfig>)
   - Initializes TaskStateMachine: CREATED → VALIDATING
   - ValidationChain.validateAll → if errors: publish validation-failed event, set VALIDATION_FAILED, return
   - On success: publish validated event, set PENDING
2) Orchestrator builds ExecutionUnits and submits to scheduler
   - Checks tenant conflicts via ConflictValidator; registers running tenants
   - Maps tenantId→executionUnitId and planId→executionUnitIds
3) Scheduler executes each ExecutionUnit using a Pipeline from PipelineFactory
   - For each tenant: TenantTaskExecutor.execute()
   - Publish TaskStartedEvent, set RUNNING
   - Pipeline executes ordered ServiceNotificationStage instances
   - On success: publish TaskCompletedEvent, set COMPLETED; on error: publish TaskFailedEvent, set FAILED

Core invariants and rules
- Task state transitions are constrained by TaskStateMachine (illegal transitions must not occur).
- Only one running task per tenant (ConflictValidator guards concurrent runs).
- Pipeline stages execute in order; canSkip allows conditional skipping.
- Pause/Cancel are checked before each stage; on pause, save checkpoint and return early.
- Events are published after successful state transitions by TaskStateManager.
- ExecutionUnitScheduler controls threading; CONCURRENT vs FIFO modes are respected.

Glossary and DTOs
- TenantDeployConfig: core input DTO (tenantId, planId/version, endpoints, etc.).
- ExecutionUnit: a grouped batch of tenant configs for one scheduling unit.

README vs code naming differences (avoid confusion)
- README mentions BlueGreenExecutorFacade, ExecutionOrder, ServiceConfig, ConfigProcessor; these are not present in the current codebase.
- Actual external API here is DeploymentTaskFacade/DeploymentTaskFacadeImpl with TenantDeployConfig as input.
- Threading is managed by ExecutionUnitScheduler’s internal ThreadPoolExecutor, not Spring’s ThreadPoolTaskExecutor from README snippets.

Extensibility checklist
- Add a validator: implement ConfigValidator and register in ValidationChain.
- Add a service notification: implement ServiceNotificationStrategy; register via ServiceRegistry; it automatically becomes a PipelineStage.
- Add a custom Pipeline stage: implement PipelineStage and include it in PipelineFactory.
- Persist checkpoints: implement CheckpointManager with durable storage (e.g., Redis/DB) and wire it in ExecutorConfiguration.
- Consume events: add @EventListener components for state.event.*
- Scheduling policy: extend ExecutionUnitScheduler for custom queueing/priority.

How to work effectively in this repo
- When changing public behavior, update or add focused tests under src/test/java/xyz/firestige/executor/**.
- Keep edits minimal and cohesive; preserve existing APIs unless the task requires a change.
- Validate with mvn test locally after non-trivial edits.

Quick commands (macOS, zsh)
```bash
# From project root
mvn -q -DskipTests=false test

# Run a single test class (example)
mvn -q -Dtest=xyz.firestige.executor.unit.execution.PipelineTest test
```

Answering style for future sessions
- Start with a one-line task receipt and a short plan.
- Prefer concrete code edits with minimal diffs; group changes by file.
- After edits, build and run relevant tests; iterate on failures up to three quick fixes.
- Be concise but thorough; avoid filler. If blocked by missing context, read files using search/read tools.

Security and safety
- No external network calls unless explicitly required by the task.
- Do not exfiltrate secrets. Follow Microsoft content policies.

This prompt is the minimal working context to regain full-speed productivity in new sessions. Refer to files listed above when deeper inspection is needed.

# ARCHITECTURE PROMPT — Executor Demo (Final)

角色
- 你是该项目的维护者/贡献者，熟悉 Java/Spring。优先小改动、强测试、结果导向。

项目目的
- 多租户蓝绿切换执行器：编排 Plan → Task → Stage，执行服务通知与健康检查，支持并发阈值与 FIFO、checkpoint 暂停/恢复、事件心跳与幂等。

领域模型与语义
- Plan：一次切换计划，包含 1..N 个租户 Task；控制并发阈值与冲突（同租户互斥）。
- Task：租户维度切换任务；仅在 Stage 边界响应暂停/取消；回滚/重试仅手动触发；fromCheckpoint 重试会补偿一次进度事件。
- Stage：由多个 Step 组成；常见步骤：ServiceNotification、HealthCheck；Stage 内不可切片。
- 健康检查：固定 3s 间隔轮询，连续 10 次失败判定失败；必须全实例成功。
- 配置优先级：TenantDeployConfig > application config > 默认值。

关键模块（仅保留新架构）
- facade
  - DeploymentTaskFacade/Impl：外部 API（create/pause/resume/retry/rollback/cancel/query）。
- orchestration
  - PlanOrchestrator：计划提交、冲突校验、并发阈值；将 Task 分发给 TaskScheduler。
  - TaskScheduler：并发阈值 + FIFO 调度，创建并执行 TaskExecutor。
- execution
  - TaskExecutor：顺序执行 TaskStage 列表；在 Stage 边界保存/清理 checkpoint；支持 fromCheckpoint 重试与 invokeRollback；发布事件与心跳。
- domain.stage
  - TaskStage、StageStep、CompositeServiceStage；内置 steps：NotificationStep、HealthCheckStep。
- checkpoint
  - CheckpointService、CheckpointStore（默认 InMemory，可替换 Redis/DB）。
- state
  - TaskStateManager：状态迁移与事件发布（sequenceId 幂等）。TaskStatus 枚举表示任务状态。
- event
  - TaskEventSink（Spring 实现：SpringTaskEventSink）。
- config
  - ExecutorConfiguration：装配 TaskStateManager、ValidationChain、ExecutorProperties、HealthCheckClient、DeploymentTaskFacade。

事件与心跳
- 任务事件：Started/Progress/StageCompleted/StageFailed/Completed/Failed/Paused/Resumed/RollingBack/RolledBack/RollbackFailed/ValidationFailed；均带 sequenceId。
- 心跳：每 10s 报告进度（completed/total），同时作为心跳。
- 回滚事件带阶段列表（rollingBack/rolledBack）。

暂停/恢复/取消
- 协作式，仅在 Stage 之间的 checkpoint 响应；Stage 仅有开始/成功/失败三事件。

测试策略（不侵入生产）
- 通过 Facade 构造器注入 ExecutorProperties（压低重试间隔/次数）与 HealthCheckClient stub，加速健康检查测试。
- 失败场景通过 stub 返回错误模拟，无需真实等待 10×3s。

常看文件
- facade/DeploymentTaskFacadeImpl.java
- orchestration/PlanOrchestrator.java, TaskScheduler.java
- execution/TaskExecutor.java, HeartbeatScheduler.java
- domain/stage/*（CompositeServiceStage, NotificationStep, HealthCheckStep）
- checkpoint/*（CheckpointService, InMemoryCheckpointStore）
- state/*（TaskStateManager, TaskStatus）
- event/*（TaskEventSink, SpringTaskEventSink）
- config/ExecutorConfiguration.java
- README.md, TODO.md

如何高效工作
- 小步提交、跑 `mvn -q -DskipTests=false test`；为公共行为变更补测试；优先重用已存在的构造器与注册表。
- 若新增 Step/策略：实现 StageStep 或策略接口，注册到 Stage 组合工厂后自动纳入执行链。

告别旧实现
- 旧 ExecutionUnit/TaskOrchestrator/TenantTaskExecutor/ServiceNotificationStage 已删除；不要再引入这些命名或依赖。
