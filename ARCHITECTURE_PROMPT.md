# Executor Demo – Architecture Prompt (English Clean Version)

Updated: 2025-11-16

## Purpose
Multi-tenant blue/green (or weighted) configuration switch executor. A Plan groups tenant-specific Tasks; each Task runs a sequential list of Stages (each composed of ordered Steps) to push new config, broadcast change events, and verify health. Supports max concurrency, FIFO ordering when needed, pause/resume, manual rollback, manual retry, and heartbeat progress events.

## Current Architecture Snapshot
- Legacy removed: ExecutionUnit, TaskOrchestrator, TenantTaskExecutor, ServiceNotificationStage.
- Core components: PlanAggregate, TaskAggregate, CompositeServiceStage, StageStep, ConfigUpdateStep, BroadcastStep, HealthCheckStep, TaskExecutor, TaskStateMachine, TaskStateManager, PlanStateMachine, PlanOrchestrator, TaskScheduler, CheckpointService (InMemory), PreviousConfigRollbackStrategy (placeholder logic).

## Domain Model
- Plan: Aggregates N tenant Tasks; enforces maxConcurrency and tenant conflict (no concurrent Task for same tenant); supports PAUSED state for plan-level pause/resume.
- Task: Tenant-level switch job; only responds to pause/cancel at Stage boundaries (cooperative). Rollback & retry are manually triggered via Facade.
- Stage: Sequential execution of Steps; failure short-circuits Task progress. Rollback re-sends previous known good configuration via a RollbackStrategy.
- Step types (current): ConfigUpdateStep (apply new config version), BroadcastStep (emit change notification), HealthCheckStep (poll health endpoints until success or timeout).

## Context Separation
- TaskRuntimeContext (current TaskContext): Runtime execution data (MDC, pause/cancel flags, transient map).
- TaskTransitionContext: Built per state transition; contains TaskAggregate reference + runtime context + totalStages; used by Guards and Actions in the state machine; not used for business step logic.

## State & Events
- TaskStateMachine: Explicit transition rules; supports registering Guards and Actions (e.g., FAILED→RUNNING retry limit, RUNNING→PAUSED pause flag, RUNNING→COMPLETED all stages finished).
- TaskStateManager: Owns per-task TaskStateMachine, builds TaskTransitionContext, executes transitions, publishes Spring events with monotonically increasing sequenceId (for idempotency). Cancellation event carries cancelledBy and lastStage (lastStage resolved from registered stage names).
- PlanStateMachine: READY→RUNNING→PAUSED/RUNNING minimal wiring; future guards/actions can be added.
- Event categories:
  - Lifecycle: Created, Validated, Started, Completed, Failed, Paused, Resumed, Cancelled (cancelledBy, lastStage)
  - Progress: Progress, Heartbeat (same structure, heartbeat every 10s)
  - Stage: StageStarted, StageSucceeded, StageFailed
  - Rollback: RollingBack, RolledBack (includes prev snapshot fields), RollbackFailed
  - Validation: ValidationFailed

## Rollback Strategy
- PreviousConfigRollbackStrategy: Re-send previous configuration snapshot. On ROLLED_BACK transition, snapshot fields are restored into the aggregate; lastKnownGoodVersion is updated via a pluggable RollbackHealthVerifier (default AlwaysTrue, future: real health reconfirmation).

## Checkpoint
- CheckpointService (InMemory): Save after each successful Stage; clear on terminal states or after rollback. Future SPI: Redis / DB implementations.
- Batch recovery (CP-03): `CheckpointService.loadMultiple(List<String>)` to fetch multiple checkpoints at once without mutating aggregates.
- RedisCheckpointStore (CP-04): Implemented behind a RedisClient abstraction and Spring Data Redis-based client; namespace + TTL supported; enabled via auto-configuration (property `executor.checkpoint.store-type=redis`).

## Concurrency & Scheduling
- TaskScheduler: Enforces maxConcurrency; maxConcurrency=1 => strict FIFO; >1 => parallel submission. Uses ConflictRegistry to prevent same-tenant overlap.
- PlanOrchestrator: Submits Plan Tasks to scheduler; plan-level pause/resume/rollback routed via Facade.

## Health Check
- Fixed polling interval: 3 seconds.
- Max attempts: 10.
- Success criteria: ALL endpoints report expected version.
- Tests use stub client with reduced interval/attempts (non-intrusive to production code).

## Heartbeat
- Every 10 seconds emit TaskProgressEvent (completedStages/totalStages); acts as a heartbeat/health signal.

## Configuration Priority
1. TenantDeployConfig (tenant-specific override)
2. application configuration
3. internal defaults

## Extensibility Points (Reserved / Planned)
- Policies: RetryPolicy, PausePolicy, CompletionPolicy, RollbackPolicy, HealthCheckPolicy, CheckpointPolicy.
- Instrumentation: TransitionInstrumentation (beforeGuard / afterTransition hooks).
- StageFactory: Declarative assembly of Stage step sequences.
- Checkpoint SPI: RedisCheckpointStore, DBCheckpointStore, selectable via property.
- Metrics: Micrometer counters/gauges (task_active, task_completed, task_failed, rollback_count, heartbeat_lag).
- RollbackHealthVerifier: Pluggable health reconfirmation for rollback success to gate lastKnownGoodVersion update.

## Upcoming Phases (High-Level)
- Phase 10: Strengthen Guards/Actions, add RetryStarted / RetryCompleted, per-stage rollback events, core tests.
- Phase 11: Unify status changes, rollback snapshot restore + durationMillis tracking, cancellation enrichment, plan PAUSED wiring, rollback verifier hook.
- Phase 12: Persistent checkpoint SPI + batch recovery.
- Phase 13: Health check configurability (versionKey/path) + StageFactory.
- Phase 14: Observability metrics + MDC stability tests.
- Phase 15: Performance / concurrency stress tests, lock release safeguards.
- Phase 16: Final documentation & migration guide.

## Key Invariants
- No concurrent active Task for the same tenant.
- Pause/cancel only honored at Stage boundaries.
- lastKnownGoodVersion updated only after successful health confirmation (via RollbackHealthVerifier).
- All events carry increasing sequenceId; consumers discard older/equal sequenceIds for idempotency.
- Legacy classes must not reappear; no V2/V3 suffix naming.

## Testing Guidelines
- Use Facade (DeploymentTaskFacadeImpl) to create & operate Tasks; avoid constructing aggregates directly.
- Simulate failures via stub HealthCheckClient or failing Step.
- Test retry paths (fromCheckpoint vs fresh) ensuring completed stages are not re-executed unless intended.
- Rollback tests assert snapshot restore and (with real verifier) lastKnownGoodVersion gating.

## Adding a New Step / Stage
1. Implement StageStep.execute(TaskRuntimeContext).
2. Register it in a StageFactory that returns ordered List<StageStep>.
3. Facade uses StageFactory to build CompositeServiceStage.

## Glossary
- prevConfigSnapshot: Previously known good config snapshot (for rollback speed & version tracking).
- lastKnownGoodVersion: Version verified via health check; set after successful stage or rollback health confirmation.
- sequenceId: Monotonic numeric identifier per Task (or Plan) event stream.
- fromCheckpoint retry: Resume execution from saved progress; emits a compensation progress event.

## Maintenance Checklist (New Session)
1. Read `TODO.md` (Upcoming Phases + New Backlog).
2. Run `mvn -q -DskipTests=false test` baseline before edits.
3. Implement small, cohesive changes; update tests & TODO statuses.

## Do / Don't
- DO use TaskStateManager for transitions (remove remaining direct status mutations in upcoming phases).
- DO keep tests fast (avoid real 3s×10 loops; use stubbed polling).
- DON’T resurrect deleted legacy classes or mixed old naming.

## Extension Points Matrix (Stable Contracts)
- StageFactory
  - Input: TaskAggregate, TenantDeployConfig, ExecutorProperties, HealthCheckClient
  - Output: List<TaskStage> (ordered, FIFO)
  - Default: DefaultStageFactory (ConfigUpdate -> Broadcast -> HealthCheck)
- TaskWorkerFactory
  - Input: planId, TaskAggregate, List<TaskStage>, TaskRuntimeContext, CheckpointService, TaskEventSink, progressIntervalSeconds, TaskStateManager, ConflictRegistry
  - Output: TaskExecutor (with HeartbeatScheduler)
  - Default: DefaultTaskWorkerFactory (injects MetricsRegistry into TaskExecutor & HeartbeatScheduler)
- MetricsRegistry
  - Methods: incrementCounter(name), setGauge(name, value)
  - Impl: NoopMetricsRegistry (default), MicrometerMetricsRegistry (optional via Spring auto-config)
- CheckpointStore (SPI via CheckpointService)
  - Implementations: InMemory (default), Redis (via Spring Data Redis), future DB
  - Batch: loadMultiple(List<String>)
- HealthCheckClient
  - Method: Map<String,Object> get(String url)
  - Default: MockHealthCheckClient in dev/test; real impl can be provided by glue layer
- RollbackHealthVerifier
  - Method: boolean verify(TaskAggregate, TaskRuntimeContext)
  - Impl: AlwaysTrueRollbackHealthVerifier (default), VersionRollbackHealthVerifier (compares version field)

## Event Payloads (Examples)
- TaskStartedEvent: { taskId, totalStages, sequenceId }
- TaskProgressEvent: { taskId, currentStage, completedStages, totalStages, sequenceId }
- TaskStageCompletedEvent: { taskId, stageName, stageResult, sequenceId }
- TaskStageFailedEvent: { taskId, stageName, failureInfo{type,code,message}, sequenceId }
- TaskFailedEvent: { taskId, failureInfo{...}, completedStages[], failedStage, sequenceId }
- TaskCompletedEvent: { taskId, durationMillis, completedStages[], sequenceId }
- TaskRetryStartedEvent: { taskId, fromCheckpoint, sequenceId }
- TaskRetryCompletedEvent: { taskId, fromCheckpoint, sequenceId }
- TaskRollingBackEvent: { taskId, reason, stagesToRollback[], sequenceId }
- TaskRolledBackEvent: { taskId, prevDeployUnitId, prevDeployUnitVersion, prevDeployUnitName, sequenceId }
- TaskRollbackFailedEvent: { taskId, failureInfo{...}, partiallyRolledBackStages[], sequenceId }
- TaskCancelledEvent: { taskId, cancelledBy, lastStage, sequenceId }

## Configuration Priority (Concrete)
1) TenantDeployConfig (highest)
2) application configuration (ExecutorProperties)
3) defaults (ExecutorProperties defaults)

## MDC Fields
- During execution: planId, taskId, tenantId, stageName
- Cleared at end of TaskExecutor.execute() and on early exits (pause/cancel/fail)

## Observability
- Counters: task_active, task_completed, task_failed, task_paused, task_cancelled, rollback_count
- Gauge: heartbeat_lag (totalStages - completed, non-negative)
- Micrometer integration via MicrometerMetricsRegistry; Spring auto-config provided (ExecutorAutoConfiguration)

End.
