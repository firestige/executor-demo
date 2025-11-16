# Executor Demo – Architecture Prompt (English Clean Version)

Updated: 2025-11-16

## Purpose
Multi-tenant blue/green (or weighted) configuration switch executor. A Plan groups tenant-specific Tasks; each Task runs a sequential list of Stages (each composed of ordered Steps) to push new config, broadcast change events, and verify health. Supports max concurrency, FIFO ordering when needed, pause/resume, manual rollback, manual retry, and heartbeat progress events.

## Current Architecture Snapshot
- Legacy removed: ExecutionUnit, TaskOrchestrator, TenantTaskExecutor, ServiceNotificationStage.
- Core components: PlanAggregate, TaskAggregate, CompositeServiceStage, StageStep, ConfigUpdateStep, BroadcastStep, HealthCheckStep, TaskExecutor, TaskStateMachine, TaskStateManager, PlanOrchestrator, TaskScheduler, CheckpointService (InMemory), PreviousConfigRollbackStrategy (placeholder logic).

## Domain Model
- Plan: Aggregates N tenant Tasks; enforces maxConcurrency and tenant conflict (no concurrent Task for same tenant).
- Task: Tenant-level switch job; only responds to pause/cancel at Stage boundaries (cooperative). Rollback & retry are manually triggered via Facade.
- Stage: Sequential execution of Steps; failure short-circuits Task progress. Rollback resends previous known good configuration via a RollbackStrategy.
- Step types (current): ConfigUpdateStep (apply new config version), BroadcastStep (emit change notification), HealthCheckStep (poll health endpoints until success or timeout).

## Context Separation
- TaskRuntimeContext (current TaskContext): Runtime execution data (MDC, pause/cancel flags, transient map).
- TaskTransitionContext: Built per state transition; contains TaskAggregate reference + runtime context + totalStages; used by Guards and Actions in the state machine; *not* used for business step logic.

## State & Events
- TaskStateMachine: Explicit transition rules; supports registering Guards and Actions (e.g., FAILED→RUNNING retry limit, RUNNING→PAUSED pause flag, RUNNING→COMPLETED all stages finished).
- TaskStateManager: Owns per-task TaskStateMachine, builds TaskTransitionContext, executes transitions, publishes Spring events with monotonically increasing sequenceId (for idempotency).
- Event categories:
  - Lifecycle: Created, Validated, Started, Completed, Failed, Paused, Resumed
  - Progress: Progress, Heartbeat (same structure, heartbeat every 10s)
  - Stage: StageStarted, StageSucceeded, StageFailed
  - Rollback: RollingBack, RolledBack, RollbackFailed (per-stage detail events planned)
  - Validation: ValidationFailed

## Rollback Strategy
- PreviousConfigRollbackStrategy: Re-send previous configuration snapshot (future: restore deployUnitId/version/name, update lastKnownGoodVersion after health reconfirmation).
- Snapshot source: Prefer TenantDeployConfig.sourceTenantDeployConfig; fallback to initial target config.

## Checkpoint
- CheckpointService (InMemory): Save after each successful Stage; clear on terminal states or after rollback. Future SPI: Redis / DB implementations.

## Concurrency & Scheduling
- TaskScheduler: Enforces maxConcurrency; maxConcurrency=1 => strict FIFO; >1 => parallel submission. Uses ConflictRegistry to prevent same-tenant overlap.
- PlanOrchestrator: Submits Plan Tasks to scheduler; future: dedicated pause/resume/rollback routing.

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

## Upcoming Phases (High-Level)
- Phase 10: Strengthen Guards/Actions, add RetryStarted / RetryCompleted, per-stage rollback events, core tests.
- Phase 11: Unify status changes (replace direct task.setStatus), rollback snapshot restore + durationMillis tracking.
- Phase 12: Persistent checkpoint SPI + batch recovery.
- Phase 13: Health check configurability (versionKey/path) + StageFactory.
- Phase 14: Observability metrics + MDC stability tests.
- Phase 15: Performance / concurrency stress tests, lock release safeguards.
- Phase 16: Final documentation & migration guide.

## Key Invariants
- No concurrent active Task for the same tenant.
- Pause/cancel only honored at Stage boundaries.
- lastKnownGoodVersion updated only after successful health confirmation (planned enhancement).
- All events carry increasing sequenceId; consumers discard older/equal sequenceIds for idempotency.
- Legacy classes must not reappear; no V2/V3 suffix naming.

## Testing Guidelines
- Use Facade (DeploymentTaskFacadeImpl) to create & operate Tasks; avoid constructing aggregates directly.
- Simulate failures via stub HealthCheckClient or failing Step.
- Test retry paths (fromCheckpoint vs fresh) ensuring completed stages are not re-executed unless intended.
- Rollback tests will later assert snapshot restore & version reconciliation.

## Adding a New Step / Stage
1. Implement StageStep.execute(TaskRuntimeContext).
2. Register it in a StageFactory that returns ordered List<StageStep>.
3. Facade uses StageFactory to build CompositeServiceStage.

## Glossary
- prevConfigSnapshot: Previously known good config snapshot (for rollback speed & version tracking).
- lastKnownGoodVersion: Version verified via health check; set after successful stage (enhancement pending).
- sequenceId: Monotonic numeric identifier per Task (or Plan) event stream.
- fromCheckpoint retry: Resume execution from saved progress; emits a compensation progress event.

## Maintenance Checklist (New Session)
1. Read `TODO.md` (Upcoming Phases + New Backlog).
2. Decide whether Phase 10 (Guards/Actions tests & events) starts now.
3. Run `mvn -q -DskipTests=false test` baseline before edits.
4. Implement small, cohesive changes; update tests & TODO statuses.

## Do / Don't
- DO use TaskStateManager for transitions (remove remaining direct status mutations in upcoming phases).
- DO keep tests fast (avoid real 3s×10 loops; use stubbed polling).
- DON’T resurrect deleted legacy classes or mixed old naming.

End.
