# 重构路线图（Plan / Task / Stage）— 结构化中文版

## 0. 指导原则
- Facade 方法签名与入参 DTO 保持稳定（返回内容允许演进但语义一致）。
- 不保留双系统：旧代码经审核后直接移除；禁止使用 V2/V3 命名后缀。
- 回滚与重试均为手动（通过 Facade），自动行为仅包含：健康检查轮询 + 心跳/进度事件。
- 健康检查：固定 3 秒间隔，最多 10 次尝试，所有端点成功才通过。
- 事件幂等：task/plan 级 sequenceId 单调递增；消费者丢弃小于等于已处理 sequenceId 的事件。
- 并发：Plan.maxConcurrency 控制任务并行度；=1 时严格 FIFO；租户级冲突锁防止同租户并发任务。
- 暂停/取消：协作式，仅在 Stage 边界被响应。
- MDC：注入 planId / taskId / tenantId / stageName；执行完成或异常必须清理。
- 配置优先级：TenantDeployConfig > application 配置 > 默认值。
- 胶水层：PlanFactory 深拷贝外部 DTO 为内部聚合，内部不直接引用外部对象。
- Checkpoint：存储可插拔（当前 InMemory，后续 Redis/DB）。
- 不同 Plan 之间无依赖，不需要跨 Plan 协作编排或依赖处理。

## 1. 已完成历史（归档）
- **Phase 0**：初始化与分支建立
- **Phase 1**：领域聚合与运行时上下文
- **Phase 2**：初版 TaskStateMachine（Guard/Action 接口骨架）
- **Phase 3**：ConflictRegistry 与 TaskScheduler 骨架
- **Phase 4**：Stage/Step 模型 + HealthCheckStep
- **Phase 5**：Checkpoint 抽象与内存实现
- **Phase 6**：TaskExecutor 接线 + 心跳 + MDC + 基础回滚/重试
- **Phase 7**：Facade 接入新架构（创建/暂停/恢复/重试/回滚/查询）
- **Phase 8**：旧体系清理（ExecutionUnit / TaskOrchestrator 等删除）
- **Phase 9**：初步文档更新（Architecture Prompt / README）
- **Phase 10**：状态与事件接线（SM-04/05/07，EV-01/02/04，TC-05）
  - 进入 RUNNING/终态写 startedAt/endedAt/durationMillis
  - cancel/retry 改为状态机迁移
  - 重试事件（Started/Completed）+ 每阶段回滚事件 + 聚合回滚失败事件
  - 心跳调度器支持重试（可重复启动）
  - 新单测：Guard/Retry/回滚阶段/持续时间
- **Phase 11**：回滚快照与取消事件增强 + 计划状态机接线
  - RB-01/RB-03 快照恢复与事件携带
  - RB-02 健康确认门控 lastKnownGoodVersion（VersionRollbackHealthVerifier + 单测）
  - EV-03 取消事件 enriched (cancelledBy + lastStage)
  - SM-06 计划状态机 READY→RUNNING→PAUSED/RUNNING + PAUSED 状态引入
- **Phase 12**：Checkpoint/释放兜底/重试差异 — 已完成
  - CP-03（完成）、CP-04（完成：自动配置 + 客户端隔离，可通过属性切换 memory/redis）、CP-05（完成：Pause→Resume 测试通过，checkpoint 连续）
  - SC-02（完成：执行路径释放 + 事件兜底释放）、C-02（完成）
- **Phase 13**：健康检查与工厂抽象 — 已完成
  - HC-01（ExecutorProperties 支持 healthCheckPath / healthCheckVersionKey）
  - HC-03（StageFactory 抽象与接线）
  - SC-05（TaskWorkerFactory 抽象与接线）

> 截至当前 Phase 13 全部完成。

## 2. 待办目录（编号可追踪）
### 2.1. 状态机
- **SM-01** FAILED→RUNNING Guard（retryCount < maxRetry） — DONE
- **SM-02** RUNNING→PAUSED Guard（pauseRequested = true） — DONE
- **SM-03** RUNNING→COMPLETED Guard（currentStageIndex == totalStages） — DONE
- **SM-04** RUNNING 进入 Action：记录 startedAt — DONE
- **SM-05** COMPLETED / FAILED / ROLLED_BACK / ROLLBACK_FAILED Action：记录 endedAt + durationMillis — DONE
- **SM-06** PlanStateMachine 基本迁移（READY→RUNNING→PAUSED/ RUNNING） — DONE
- **SM-07** 替换 retry / cancel 直接 task.setStatus 为 stateManager.updateState — DONE

### 2.2. 事件
- **EV-01** RetryStarted / RetryCompleted（包含 fromCheckpoint 标志） — DONE
- **EV-02** 每阶段回滚事件（StageRollingBack / StageRolledBack / StageRollbackFailed） — DONE
- **EV-03** Cancel 事件增强（cancelledBy + lastStage） — DONE（通过注册 stage 名称解析 lastStage）
- **EV-04** sequenceId 连续性测试（无跳号/回退） — DONE（已有通用与重试覆盖）
- **EV-05** README 中关键事件示例章节 — DONE（README.md 已提供事件 payload 示例）

### 2.3. 回滚/快照
- **RB-01** 回滚成功恢复 prevConfigSnapshot（deployUnitId/version/name） — DONE
- **RB-02** 回滚健康确认后更新 lastKnownGoodVersion — DONE（VersionRollbackHealthVerifier 已实现并有成功/失败单测）
- **RB-03** 在 RolledBack 事件中发布快照恢复详情 — DONE

### 2.4. Checkpoint
- **CP-01** 每个成功 Stage 后保存 checkpoint — DONE
- **CP-02** 终态/回滚后统一清理 checkpoint — DONE
- **CP-03** 批量恢复 API（loadMultiple） — DONE（CheckpointService.loadMultiple）
- **CP-04** RedisCheckpointStore 占位 + SPI 选择（memory|redis|db） — DONE（自动配置 + 客户端隔离，可通过属性切换 memory/redis）
- **CP-05** Pause→Resume 正确性测试（checkpoint 连续性） — DONE（Pause→Resume 测试通过，checkpoint 连续）

### 2.5. 健康检查
- **HC-01** ExecutorProperties 支持 versionKey / path 可配置 — DONE
- **HC-02** 部分实例失败用例（部分端点滞后导致 Stage 失败） — DONE
- **HC-03** StageFactory 抽象（声明式组合步骤） — DONE

### 2.6. 并发/调度
- **SC-01** PlanOrchestrator 支持 plan 级 pause/resume/rollback 路由 — DONE
- **SC-02** 任务结束各路径释放 ConflictRegistry 锁（成功/失败/取消/回滚） — DONE（执行路径释放 + 事件兜底释放）
- **SC-03** FIFO 测试（maxConcurrency=1）保证启动顺序 — DONE
- **SC-04** 并发启动测试（maxConcurrency>1）正确性 — DONE
- **SC-05** TaskWorkerFactory 抽象（封装 TaskExecutor 创建） — DONE

### 2.7. 测试覆盖
- **C-01** 长耗时 Step 心跳测试（≥2 次心跳） — DONE（HeartbeatSchedulerTest）
- **C-02** fromCheckpoint 与 fresh 重试差异（不重复已完成 Stage） — DONE（RetryFromCheckpointTest）
- **C-03** 执行后 MDC 清理（线程局部检查） — DONE（MdcCleanupTest / MdcCleanupCancelFailTest）
- **C-04** 回滚失败路径（某 Stage rollback 抛异常）触发 RollbackFailed — DONE
- **C-05** durationMillis 字段正确性测试 — DONE
- **C-06** RollbackHealthVerifier 成功/失败分支测试 — DONE

### 2.8. 可观测性/指标
- **OB-01** Micrometer 计数器（task_active / task_completed / task_failed / rollback_count） — DONE（MetricsRegistry 适配 + 可选自动配置示例）
- **OB-02** heartbeat_lag Gauge — DONE（HeartbeatScheduler + MetricsRegistry#setGauge）
- **OB-03** 结构化日志 + MDC 稳定性测试 — DONE（StructuredMdcLoggingTest + 失败/取消路径覆盖）

### 2.9. 文档/治理
- **DG-01** 最终架构文档扩展点矩阵 — DONE（ARCHITECTURE_PROMPT.md 已更新）
- **DG-02** 迁移指南（旧 → 新）最终版 — DONE（MIGRATION_GUIDE.md）
- **DG-03** README 事件 payload 示例章节 — DONE（README.md 事件示例）
- **DG-04** Glossary 扩展（policies / instrumentation） — DONE（GLOSSARY.md）

## 3. 阶段路线图（待办编号映射）
- Phase 15（已完成）：OB-01..03、C-03
- Phase 16（已完成）：DG-01..04、EV-05

## 4. 当前工作集（Phase 16）
- （空）

## 5. 说明
- 冲突锁释放：仅终止态（COMPLETED/FAILED/CANCELLED/ROLLED_BACK/ROLLBACK_FAILED）释放；执行路径释放 + 事件兜底释放双层保障；回滚中（ROLLING_BACK）与暂停（PAUSED）不释放。
