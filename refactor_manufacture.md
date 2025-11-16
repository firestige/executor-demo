# Executor Refactor Manufacture Plan (Plan/Task/Stage — Full Purge & Rebuild)

Status: Draft for approval (no code changes yet)
Owner: executor module maintainers
Scope: Completely purge legacy (deprecated) architecture and rebuild on the final design you approved. Keep only Facade method signatures and external DTOs (TenantDeployConfig, NetworkEndpoint). All return payloads can be redesigned.

Executive summary
- Purge all deprecated classes and any reference to them. No dual systems or transitional adapters.
- Keep Facade method signatures; new return DTOs are allowed.
- Replace ExecutionMode with plan.maxConcurrency and tenant-level conflict locking; maxConcurrency=1 implies FIFO.
- Stage/Step: Step has no rollback; Stage rollback means “re-issue previous good configuration” (old version) + health confirmation.
- Events use Spring ApplicationEventPublisher (no topics); attach per-plan and per-task monotonic sequenceId for ordering/idempotency.
- Health check: sequential polling per stage; fixed interval 3s; max attempts 10; all endpoints must succeed. Heartbeat interval 10s. Retry max 3.
- Keep previous-good config snapshot for fast rollback (prefer TenantDeployConfig.sourceTenantDeployConfig when provided).

External invariants (must not change)
- Facade method names and argument lists remain stable.
- External DTOs: TenantDeployConfig, NetworkEndpoint remain as the inbound contract.
- Everything else may be deleted/recreated.

What will be removed (final state)
- orchestration: ExecutionUnit, ExecutionUnitStatus, ExecutionUnitResult, ExecutionUnitScheduler, TaskOrchestrator (legacy)
- execution: TenantTaskExecutor (legacy)
- service/stage: ServiceNotificationStage (legacy)
- state: xyz.firestige.executor.state.TaskStateMachine (legacy)
- Any tests and configuration wiring that reference the above.

What will replace it (target architecture)
- domain: PlanAggregate, TaskAggregate, TaskStage, StageStep, RollbackStrategy
- domain.state: TaskStateMachine, PlanStateMachine (with Guard/Action)
- state: TaskStateManager (single authority for transitions, per-plan/per-task sequence IDs, publishes Spring events)
- orchestration: PlanOrchestrator (submit/pause/resume/rollback), TaskScheduler (maxConcurrency + FIFO), ConflictRegistry (tenant lock)
- execution: TaskExecutor (sequential stage execution, pause/resume/retry/rollback), HeartbeatScheduler (10s)
- stage steps: ConfigUpdateStep, BroadcastStep, HealthCheckStep (sequential polling; interval 3s; attempts 10)
- checkpoint: CheckpointStore SPI (InMemory default), CheckpointService
- health: HealthCheckClient (Default/Mock)
- facade: DeploymentTaskFacadeImpl (same method signatures; new return DTOs)
- config: ExecutorConfiguration only wires the new components
- factory: PlanFactory converts DTOs to internal aggregates (deep copy), captures prevConfigSnapshot

Decisions (confirmed)
- Concurrency & FIFO: ExecutionMode removed. plan.maxConcurrency governs concurrency; tenant conflict lock prevents same-tenant overlap. maxConcurrency=1 ⇒ strict FIFO; >1 ⇒ concurrent start from FIFO queue.
- Stage vs Step: Step doesn’t rollback; Stage.rollback uses RollbackStrategy to re-send previous good config and (as part of rollback) confirm via health check that old version is effective.
- Events & ordering: Spring events only (no topics). Every Plan event carries planSequenceId; every Task event carries taskSequenceId. If a future MQ bridge is needed, map Plan→plan.events (key=planId), Task→task.events (key=taskId).
- Health checks: sequential polling per stage, interval=3s (configurable), attempts=10 (configurable), all endpoints must succeed; no parallel checks inside a stage. Heartbeat 10s. Retry max 3.
- Fast rollback: maintain prevConfigSnapshot (preferred from TenantDeployConfig.sourceTenantDeployConfig; otherwise capture the last known-good after a successful switch).

Event model (final)
- PlanLifecycle: PlanStarted, PlanPaused, PlanResumed, PlanCompleted, PlanFailed
- PlanProgress: PlanProgress (completedTasks/totalTasks)
- PlanRollback: PlanRollingBack, PlanRolledBack, PlanRollbackFailed
- TaskLifecycle: TaskCreated, TaskValidated, TaskStarted, TaskPaused, TaskResumed, TaskCompleted, TaskFailed, TaskCancelled
- TaskStage: TaskStageStarted, TaskStageSucceeded, TaskStageFailed
- TaskRetry: TaskRetryStarted(fromCheckpoint), TaskRetryCompleted(success/failure)
- TaskRollback: TaskRollingBack, TaskRolledBack, TaskRollbackFailed, TaskStageRollingBack, TaskStageRolledBack, TaskStageRollbackFailed
- TaskHeartbeat: TaskHeartbeat (every 10s)
Event payload common fields: planId, taskId (optional for plan events), sequenceType (PLAN|TASK), sequenceId (long), timestamp, status snapshot, currentStage (optional), completed/total (optional), failureInfo (optional)

New DTOs (for Facade returns)
- TaskCreationResultNew { planId, taskIds, validatedCount, failureInfo?, status, message }
- TaskOperationResultNew { targetType: PLAN|TASK, targetId, success, finalStatus, failureInfo?, message }
- TaskStatusInfoNew { taskId, status, currentStage, completedStages, totalStages, progressPercent, paused, cancelled, retryCount, timestamps }

Quality gates for every phase
- Build compiles; tests (if present) pass
- grep for legacy symbols (ExecutionUnit|TaskOrchestrator|TenantTaskExecutor|ServiceNotificationStage|xyz.firestige.executor.state.TaskStateMachine) returns zero when the purge phase completes
- No @Deprecated classes remain after purge
- README/ARCHITECTURE_PROMPT aligned; no legacy terms left

Phased plan (reviewable steps, each with acceptance + rollback)

Phase 0 — Preparation (no code changes)
- Create working branch: feature/new-arch-purge
- Confirm external invariants (Facade signatures, DTOs) and approved defaults:
  - healthCheckIntervalSeconds=3, healthCheckMaxAttempts=10, taskProgressIntervalSeconds=10, maxRetry=3
- Acceptance: branch created; this document added; defaults listed
- Rollback: delete the branch

Phase 1 — Inventory & reference map (read-only)
- List all @Deprecated classes and all references to legacy symbols (code + tests + configuration)
- Produce a reference matrix to guide deletions
- Acceptance: inventory document (append to TODO.md) and zero ambiguity on where references exist
- Rollback: N/A (read-only)

Phase 2 — Introduce new core domain/state (not wired yet)
- Add domain: PlanAggregate, TaskAggregate, TaskStage, StageStep, RollbackStrategy
- Add domain.state: TaskStateMachine, PlanStateMachine (with Guard/Action per spec)
- Add state: TaskStateManager (transitionTask→Guard/Action→sequenceId→publish Spring event; queryTask)
- Add factory: PlanFactory (DTO→Aggregates; capture prevConfigSnapshot via TenantDeployConfig.sourceTenantDeployConfig)
- Acceptance: compiles; unit tests for state machine transitions pass (happy/guard/illegal)
- Rollback: remove newly added domain/state files

Phase 3 — Stage/Steps & Health check
- Implement steps: ConfigUpdateStep, BroadcastStep, HealthCheckStep (sequential polling; interval 3s, attempts 10; all endpoints must pass)
- Implement stage: CompositeServiceStage (execute forward steps; rollback via RollbackStrategy re-sending previous good config + confirm)
- Acceptance: unit tests covering success/failure/partial-failure (partial ⇒ stage failure)
- Rollback: revert Phase 3 commits

Phase 4 — Checkpoint & persistence SPI
- Implement CheckpointStore SPI (InMemory default) + CheckpointService (save after stage, load on resume, clear on terminal states/rollback)
- Acceptance: unit tests for save/load/clear; pause/resume path; rollback clears
- Rollback: revert Phase 4 commits

Phase 5 — Orchestration & scheduling
- Implement ConflictRegistry (tenant lock), TaskScheduler (maxConcurrency + FIFO), PlanOrchestrator (submit/pause/resume/rollback flags)
- Acceptance: unit tests for FIFO @ maxConcurrency=1; concurrent starts when >1; tenant conflict mutual exclusion
- Rollback: revert Phase 5 commits

Phase 6 — Executor & heartbeat
- Implement TaskExecutor (sequential stage exec; stage-boundary checks for pause/cancel; retry(fromCheckpoint|scratch); rollback) and HeartbeatScheduler (10s)
- Acceptance: unit tests for success/failure/pause/resume/retry/rollback success+failure; heartbeat during long step
- Rollback: revert Phase 6 commits

Phase 7 — Facade rewrite (signatures stable; new DTOs)
- Re-implement DeploymentTaskFacadeImpl using new components; return new DTOs
- ExecutorConfiguration: wire only new components (TaskStateManager, ValidationChain, ExecutorProperties, HealthCheckClient, Facade)
- Acceptance: compiles; basic integration tests (create→pause→resume→retry→rollback) pass using Spring events
- Rollback: revert Phase 7 commits (Facade and configuration)

Phase 8 — Purge legacy (single atomic commit)
- Delete legacy classes & their tests:
  - ExecutionUnit*, ExecutionUnitScheduler, TaskOrchestrator
  - TenantTaskExecutor
  - ServiceNotificationStage
  - xyz.firestige.executor.state.TaskStateMachine
- grep verify zero references
- Acceptance: build/test green; grep legacy=0; no @Deprecated remains
- Rollback: revert purge commit (as one atomic change)

Phase 9 — Documentation & audit
- Update README and ARCHITECTURE_PROMPT to remove legacy terms and document new events/DTOs
- Update TODO.md with completed phases and next backlog
- Acceptance: docs align with implementation, reviewer sign-off
- Rollback: revert doc commit

## Context & State Machine Extension Design (Added)

### Runtime vs Transition Context
- TaskRuntimeContext (current TaskContext) carries execution-time data: MDC keys, pause/cancel flags, transient step data.
- TaskTransitionContext is constructed per state change and provides immutable decision inputs for guards/actions: TaskAggregate snapshot, TaskRuntimeContext reference (read-only), totalStages, (future) plan concurrency and conflict lock info.
- Separation avoids leaking execution concerns into state machine logic and enables future policy injection.

### Builder Pattern
A TaskTransitionContextBuilder will assemble TaskTransitionContext from registered maps: taskId -> TaskAggregate, taskId -> TaskRuntimeContext, taskId -> totalStages. Missing components short-circuit transitions (no-op) to keep system robust during partial initialization.

### Policies (Extension Points)
Interfaces reserved (stub or to be added gradually):
- RetryPolicy: allowRetry(agg), nextDelayMs(agg)
- PausePolicy: shouldPause(runtimeCtx)
- CompletionPolicy: isCompleted(agg, totalStages)
- RollbackPolicy: canRollback(agg)
- StageSkipEvaluator: shouldSkip(agg, runtimeCtx, stage)
- HealthCheckPolicy: intervalSeconds(), maxAttempts(), successPredicate(resp)
- CheckpointPolicy: shouldCheckpointAfterStage(agg, stage), restoreOnRetry()
- TransitionInstrumentation: beforeGuard(from,to,ctx), afterTransition(from,to,success,ctx)

Initial implementation uses inline Guards/Actions for: FAILED→RUNNING (retry limit), RUNNING→PAUSED (pause flag), RUNNING→COMPLETED (all stages done). Actions set start/end timestamps.

### Encapsulation Future
Introduce TaskView / PlanView wrappers to restrict direct field access in guards and actions; current phase uses TaskAggregate directly but isolates via TransitionContext for future swap.

### Sequence & Events
Sequence assignment remains in TaskStateManager after successful transition; instrumentation hook will later allow unified metrics & tracing.

### Phase Adjustments (Remaining Work)
- Phase 2 Add: TransitionContextBuilder, registerTaskAggregate, simplified TaskStateMachine (remove TaskContext overloads).
- Phase 3 Add: ConfigUpdateStep, BroadcastStep replacing NotificationStep; integrate RollbackStrategy invocation solely in TaskExecutor.
- Phase 4+: Introduce stub policy interfaces (as empty defaults) without changing behavior; wire metrics instrumentation.

### Guard & Action Registration Flow
1. initializeTask(taskId, initialStatus) creates bare TaskStateMachine.
2. registerTaskAggregate(taskId, aggregate, runtimeCtx, totalStages) registers guards/actions *after* aggregate available.
3. updateState builds TaskTransitionContext via builder; if context incomplete, skips transition safely.

### Backward Compatibility Handling
None retained. Direct field mutations (task.setStatus) will be phased out by routing through TaskStateManager.updateState in later phases.

Backlog after refactor (post-approval)
- Observability: Micrometer metrics (task_active, task_completed, task_failed, rollback_count, heartbeat_lag)
- Persistence: Redis/DB CheckpointStore implementations and SPI switch
- Advanced strategies: additional Stage/Step types, per-step retry control if needed

Verification & reviewer checklist
- Build: mvn -q -DskipTests=false test
- Legacy grep:
  - ExecutionUnit
  - TaskOrchestrator
  - TenantTaskExecutor
  - ServiceNotificationStage
  - xyz.firestige.executor.state.TaskStateMachine
- No @Deprecated classes left
- Events include sequenceId (plan/task) and meet ordering/heartbeat requirements
- Health check sequential per stage; interval=3s; attempts=10; all endpoints success required
- Heartbeat every 10s; retry max 3; stage-only pause points
- Plan concurrency by maxConcurrency; maxConcurrency=1 ⇒ FIFO; tenant conflict mutual exclusion

Notes for future MQ bridge (out of scope now)
- If integrating a message queue later: map Plan events→plan.events partitioned by planId; Task events→task.events partitioned by taskId; keep same sequenceId discipline.

Appendix — Data contracts at a glance
- TenantDeployConfig: keep as-is; prefer sourceTenantDeployConfig for prevConfigSnapshot when present
- NetworkEndpoint: used by HealthCheckStep sequential polling
- Facade returns: switch to new DTO types; callers do not rely on return body (per product constraint)
