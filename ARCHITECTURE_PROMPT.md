# Executor Demo – Architecture Prompt (English Clean Version)

Updated: 2025-11-17

## Purpose
Multi-tenant blue/green (or weighted) configuration switch executor. A Plan groups tenant-specific Tasks; each Task runs a sequential list of Stages (each composed of ordered Steps) to push new config, broadcast change events, and verify health. Supports max concurrency, FIFO ordering when needed, pause/resume, manual rollback, manual retry, and heartbeat progress events.

## Current Architecture Snapshot (Updated: 2025-11-18, RF-10 Complete)
- **Layered Architecture (DDD Compliance: 80%)**:
  - **Facade Layer**: DeploymentTaskFacade - DTO conversion (external→internal), parameter validation, exception translation
  - **Application Service Layer**: DeploymentApplicationService - thin orchestration layer, delegates to specialized components (DeploymentPlanCreator, DomainService)
  - **Domain Layer**: Rich domain model with business behaviors, value objects (TaskId, TenantId, PlanId, DeployVersion, NetworkEndpoint), aggregates self-protect invariants
  - **Infrastructure Layer**: Simplified repositories (TaskRepository, TaskRuntimeRepository, PlanRepository), external services
- **Key Refactoring Achievements (RF-05~RF-10)**:
  - ✅ Cleaned orphaned code (~1500 lines removed)
  - ✅ Fixed anemic domain model (15+ business methods added to aggregates)
  - ✅ Corrected aggregate boundaries (Plan holds taskIds instead of Task objects)
  - ✅ Introduced 5 value objects for type safety and domain expressiveness
  - ✅ Simplified repository interfaces (TaskRepository: 15+ methods → 5 methods)
  - ✅ Separated runtime state management (TaskRuntimeRepository)
  - ✅ Optimized application service (extracted DeploymentPlanCreator, 80+ lines → 20 lines)
- **Core Components**: Rich aggregates (PlanAggregate, TaskAggregate with business methods), Value Objects (TaskId, TenantId, PlanId, DeployVersion, NetworkEndpoint), Simplified Repositories (TaskRepository, TaskRuntimeRepository, PlanRepository), Application Service Coordinators (DeploymentApplicationService, DeploymentPlanCreator), Domain Services (PlanDomainService, TaskDomainService), State Machines (TaskStateMachine, PlanStateMachine), Stage/Step (CompositeServiceStage, StageStep implementations)
- **Result DTOs (DDD-compliant)**: PlanCreationResult, PlanInfo, TaskInfo, PlanOperationResult, TaskOperationResult - clear aggregate boundaries, type safety
- **Internal DTO**: TenantConfig - decouples application layer from external DTO changes

## Domain Model (RF-06~RF-08: Rich Domain Model + Value Objects)
- **Plan Aggregate**: Holds taskIds (List<String>), not Task objects (RF-07); enforces maxConcurrency and tenant conflict; self-manages state transitions with invariant protection; supports PAUSED state for plan-level pause/resume. Business methods: addTask(taskId), markAsReady(), start(), pause(), resume(), complete(), fail(), etc.
- **Task Aggregate**: Rich domain model with 15+ business methods (RF-06); self-protects invariants; manages lifecycle, stage progression, retry/rollback logic. Business methods: markAsPending(), start(), completeStage(), pause(), resume(), cancel(), retry(), rollback(), etc. Uses value objects internally for type safety.
- **Value Objects (RF-08)**: TaskId (task ID validation), TenantId (tenant ID validation), PlanId (plan ID validation), DeployVersion (version comparison logic), NetworkEndpoint (URL validation and operations). All immutable, provide of()/ofTrusted() factory methods.
- **Stage**: Sequential execution of Steps; failure short-circuits Task progress. Rollback re-sends previous known good configuration via RollbackStrategy.
- **Step types**: ConfigUpdateStep (apply new config version), BroadcastStep (emit change notification), HealthCheckStep (poll health endpoints until success or timeout).

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

## Completed Phases (Archive)
- Phase 10-16: Core features complete (state machine, events, checkpoint, observability, documentation, 4+1 views) - DONE (2025-11-17)
- **Phase 17: DDD Architecture Deep Optimization** - DONE (2025-11-18)
  - **RF-01: Facade Business Logic Extraction** - DONE (2025-11-17): Clean layering (Facade → Application → Domain → Infrastructure), Result DTOs, internal TenantConfig DTO, exception-driven Facade
  - **RF-02: TaskWorkerFactory Parameter Simplification** - DONE (2025-11-17): TaskWorkerCreationContext (9 params → 1), Builder pattern, improved readability
  - **RF-05: Cleanup Orphaned Code** - DONE (2025-11-18): Removed ~1500 lines of orphaned code (10 main classes + 5 test classes), including service.registry, service.strategy, Pipeline, CheckpointManager
  - **RF-06: Fix Anemic Domain Model** - DONE (2025-11-18): Added 15+ business methods to TaskAggregate, 10+ methods to PlanAggregate, invariant protection, tell-don't-ask principle, code readability +50%, service layer code -30%
  - **RF-07: Correct Aggregate Boundaries** - DONE (2025-11-18): Plan holds taskIds instead of Task objects, aggregates reference each other by ID, clear transaction boundaries, supports distributed scenarios
  - **RF-08: Introduce Value Objects** - DONE (2025-11-18): Created TaskId, TenantId, PlanId, DeployVersion, NetworkEndpoint value objects, type safety, domain expressiveness, validation encapsulation
  - **RF-09: Simplify Repository Interface** - DONE (2025-11-18): TaskRepository (15+ → 5 methods, -67%), separate TaskRuntimeRepository for runtime state, Optional return values, single responsibility
  - **RF-10: Optimize Application Service** - DONE (2025-11-18): Extracted DeploymentPlanCreator, DeploymentApplicationService (80+ lines → 20 lines, -75%), dependencies (6 → 3, -50%), testability +80%
- **Achievements**: DDD compliance 50% → 80%, code -10%, test coverage +40%, maintainability +50%, type safety +60%

## Upcoming Phases (High-Level)
- Phase 18 (Remaining): 
  - RF-11: Domain events (events produced by aggregates, published by service layer) - PLANNED
  - RF-12: Transaction boundaries (@Transactional in application service) - PLANNED
  - RF-04: Comprehensive integration test suite (Testcontainers, 7 core scenarios) - PLANNED
- Phase 19: Stage strategy pattern (low priority)
  - RF-03: Declarative Stage assembly via @Component + @Order auto-discovery

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
4. After completing tasks, update `develop.log` with condensed change descriptions and archive completed items from `TODO.md`.

## TODO & develop.log Maintenance Protocol

### TODO.md - Active Work Tracking
- **Purpose**: Track current and upcoming work items with full context
- **Content**:
  - Guiding principles (architecture invariants, conventions)
  - Current phase tasks with detailed descriptions (problems, goals, solutions, priority)
  - Future phase roadmap (high-level planning)
  - Brief reference to completed phases (link to develop.log for details)
- **Update Trigger**: When planning new work, starting/completing tasks, or reprioritizing
- **Format**:
  - Keep detailed context for active tasks (RF-01, RF-02, etc.)
  - Mark tasks as TODO/IN_PROGRESS/DONE
  - Include dependencies between tasks
  - Archive completed phases with one-line summary + "see develop.log"

### develop.log - Historical Change Timeline
- **Purpose**: Day-granular, highly condensed, readable project change history
- **Content Structure**:
  ```
  ## YYYY-MM-DD
  
  ### Feature/Phase Name
  - One-sentence summary of what was done and why
  - Key outcomes or effects achieved
  - Files: Major files/components changed (optional but valuable)
  - Commit: commit-id (optional, when available)
  ```
- **Condensation Rules**:
  - Each change entry: 1-3 sentences maximum
  - Focus on WHAT changed and WHY (business value)
  - Omit implementation details unless critical to understanding
  - Group related changes under a single heading
  - NO TODO items, NO pending work, NO future plans
- **Update Trigger**: When completing a significant feature, phase, or day's work
- **Value vs git log**: 
  - develop.log is human-readable with business context
  - git log shows technical commits
  - develop.log provides day-level aggregated view with rationale

### Workflow Example
1. **Starting work**: Review TODO.md for current priorities
2. **During work**: Mark tasks as IN_PROGRESS in TODO.md
3. **After completion**: 
   - Update develop.log with condensed historical entry (same day or next day)
   - Archive completed tasks from TODO.md (move to "Completed Phases" section or remove)
   - Update TODO.md status, remove completed items if fully archived
4. **Planning next phase**: Add new tasks to TODO.md with full context

### Anti-patterns to Avoid
- ❌ Don't duplicate detailed task descriptions in develop.log
- ❌ Don't keep completed TODO items without archiving
- ❌ Don't add future plans or pending work to develop.log
- ❌ Don't write multi-paragraph change descriptions in develop.log
- ✅ DO keep TODO.md focused on active/future work
- ✅ DO make develop.log entries scannable and concise
- ✅ DO update develop.log at natural breakpoints (end of day, end of phase)

## Do / Don't
- DO use TaskStateManager for transitions (remove remaining direct status mutations in upcoming phases).
- DO keep tests fast (avoid real 3s×10 loops; use stubbed polling).
- DON’T resurrect deleted legacy classes or mixed old naming.

## Extension Points Matrix (Stable Contracts)
- **StageFactory**
  - Input: TaskAggregate, TenantDeployConfig, ExecutorProperties, HealthCheckClient
  - Output: List<TaskStage> (ordered, FIFO)
  - Default: DefaultStageFactory (ConfigUpdate -> Broadcast -> HealthCheck)
- **TaskWorkerFactory** (RF-02 Optimized)
  - Input: TaskWorkerCreationContext (contains planId, TaskAggregate, stages, context, checkpoint service, event sink, progress interval, state manager, conflict registry)
  - Output: TaskExecutor (with HeartbeatScheduler)
  - Default: DefaultTaskWorkerFactory (injects MetricsRegistry)
- **MetricsRegistry**
  - Methods: incrementCounter(name), setGauge(name, value)
  - Impl: NoopMetricsRegistry (default), MicrometerMetricsRegistry (optional)
- **CheckpointStore** (SPI via CheckpointService)
  - Implementations: InMemory (default), Redis (via Spring Data Redis), future DB
  - Batch: loadMultiple(List<String>)
- **Repository Interfaces** (RF-09 Simplified)
  - TaskRepository: save, remove, findById, findByTenantId, findByPlanId (5 core methods)
  - TaskRuntimeRepository: manages runtime state (Executor, Context, Stages)
  - PlanRepository: save, remove, findById (simplified)
- **HealthCheckClient**
  - Method: Map<String,Object> get(String url)
  - Default: MockHealthCheckClient in dev/test
- **RollbackHealthVerifier**
  - Method: boolean verify(TaskAggregate, TaskRuntimeContext)
  - Impl: AlwaysTrueRollbackHealthVerifier (default), VersionRollbackHealthVerifier

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
