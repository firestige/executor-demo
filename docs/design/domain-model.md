# 领域模型详细设计（Domain Model Design）

> 任务: T-004  
> 状态: 进行中（初稿）  
> 最后更新: 2025-11-23

---
## 1. 目标
明确本模块核心领域模型（PlanAggregate / TaskAggregate）内部结构、状态机、不变式、领域事件触发点和值对象协作方式，为后续代码演进与审计提供精确基线。

---
## 2. 聚合总览
| 聚合 | 领域定位 | 聚合根标识 | 关键组成 | 外部交互 | 是否引用其他聚合 | 说明 |
|------|----------|------------|----------|----------|------------------|------|
| PlanAggregate | 部署计划生命周期与任务集合 | PlanId | status, taskIds, maxConcurrency, progress, domainEvents | 应用层启动/暂停/恢复/完成 | 仅持有 TaskId 列表（ID 引用） | 不直接操纵 Task 状态 |
| TaskAggregate | 租户级执行单元生命周期与阶段进度 | TaskId | status, stageProgress, retryPolicy, stageResults, checkpoint, timeRange, domainEvents | 执行机编排、暂停/恢复/回滚/重试 | 引用 PlanId（ID） | 核心执行逻辑集中此处 |

聚合间耦合策略：只通过 ID（PlanId, TaskId）引用，避免跨聚合事务与深层次锁定，提高独立演进性（AP-01）。

---
## 3. 状态机矩阵
### 3.1 PlanStatus
| 当前 | 允许目标 | 触发操作 | 不变式 | 非法触发处理 |
|------|----------|----------|--------|--------------|
| CREATED | READY | markAsReady | taskIds 非空 | IllegalStateException |
| READY | RUNNING | start | taskIds 非空 | IllegalStateException |
| RUNNING | PAUSED | pause | - | IllegalStateException |
| PAUSED | RUNNING | resume | - | IllegalStateException |
| RUNNING | COMPLETED | complete | 所有 Task 完成（应用层保证） | IllegalStateException |
| RUNNING | FAILED | markAsFailed | 可直接失败 | - |
| ANY (非 COMPLETED) | FAILED | markAsFailed | COMPLETED 后不可再失败 | 忽略 |

终态：COMPLETED / FAILED。完成后不再发生其他状态转换。

### 3.2 TaskStatus
| 当前 | 允许目标 | 触发操作 | 不变式 | 非法触发处理 |
|------|----------|----------|--------|--------------|
| CREATED | PENDING | markAsPending | 初始创建后才能进入待执行 | IllegalStateException |
| PENDING | RUNNING | start | 仅 PENDING 可启动 | IllegalStateException |
| RUNNING | PAUSED | applyPauseAtStageBoundary | 仅在 pauseRequested=true 时 | 忽略或抛出 |
| PAUSED | RUNNING | resume | - | IllegalStateException |
| RUNNING | FAILED | fail / failStage(result->FAILED) | 终态不可再次失败 | 忽略 |
| RUNNING | CANCELLED | cancel | 终态不可取消 | IllegalStateException |
| FAILED | RUNNING | retry(fromCheckpoint/重置) | 仅 FAILED/ROLLED_BACK 可重试 | IllegalStateException |
| ROLLED_BACK | RUNNING | retry | 同上 | IllegalStateException |
| FAILED | ROLLING_BACK | startRollback / rollback | 有可用 prevConfigSnapshot 或直接 rollback() | IllegalStateException |
| ROLLING_BACK | ROLLED_BACK | completeRollback | - | IllegalStateException |
| ROLLING_BACK | ROLLBACK_FAILED | failRollback | - | IllegalStateException |

终态：COMPLETED / FAILED / CANCELLED / ROLLED_BACK / ROLLBACK_FAILED。

---
## 4. 不变式列表与守卫
| 聚合 | 不变式描述 | 代码守卫位置 | 违反后处理 |
|------|------------|--------------|--------------|
| Plan | READY 必须有 ≥1 Task | markAsReady() 检查 taskIds.isEmpty | IllegalStateException |
| Plan | START 仅在 READY | start() status == READY | IllegalStateException |
| Plan | COMPLETE 仅在 RUNNING | complete() status == RUNNING | IllegalStateException |
| Task | START 仅在 PENDING | start() status == PENDING | IllegalStateException |
| Task | 暂停请求仅在 RUNNING | requestPause() status == RUNNING | IllegalStateException |
| Task | 应用暂停仅在 RUNNING+pauseRequested | applyPauseAtStageBoundary() | 检查并转换/忽略 |
| Task | COMPLETE 必须 RUNNING 且所有 Stage 完成 | complete() 验证 stageProgress.isCompleted | IllegalStateException |
| Task | FAIL/FAIL_STAGE 仅在 RUNNING | validateCanCompleteStage() | IllegalStateException |
| Task | CANCEL 不允许终态 | cancel() status.isTerminal | IllegalStateException |
| Task | RETRY 仅在 FAILED/ROLLED_BACK | retry() / retry(boolean) | IllegalStateException |
| Task | ROLLBACK 仅有快照或显式触发 | startRollback() prevConfigSnapshot != null | IllegalStateException |
| Task | COMPLETE_ROLLBACK 仅在 ROLLING_BACK | completeRollback() | IllegalStateException |
| Task | FAIL_ROLLBACK 仅在 ROLLING_BACK | failRollback() | IllegalStateException |
| Task | 记录检查点仅在 RUNNING | recordCheckpoint() status == RUNNING | IllegalStateException |
| Task | 恢复检查点仅在 FAILED/ROLLED_BACK | restoreFromCheckpoint() | IllegalStateException |

---
## 5. 领域事件触发点（补充聚合视角）
| 事件 | 聚合 | 触发方法 | 事件载荷核心 | 备注 |
|------|------|----------|----------------|------|
| PlanReadyEvent | Plan | markAsReady | PlanInfo + taskCount | 首次进入 READY |
| PlanStartedEvent | Plan | start | PlanInfo + taskCount | 记录开始时间 |
| PlanPausedEvent | Plan | pause | PlanInfo | 简单状态转换 |
| PlanResumedEvent | Plan | resume | PlanInfo | 从 PAUSED → RUNNING |
| PlanCompletedEvent | Plan | complete | PlanInfo + taskCount | 记录结束时间 |
| PlanFailedEvent | Plan | markAsFailed | PlanInfo + failureSummary | 允许覆盖之前的非终态 |
| TaskStartedEvent | Task | start | TaskInfo + totalStages | 记录开始时间 |
| TaskStageStartedEvent | Task | startStage | TaskInfo + stageName + totalSteps | 在阶段执行前 |
| TaskStageCompletedEvent | Task | completeStage(String,Duration) | TaskInfo + stageName + result | 包含进度推进 |
| TaskStageFailedEvent | Task | failStage(stageName, failureInfo) | TaskInfo + stageName + failureInfo | 不直接终止任务状态 |
| TaskFailedEvent | Task | fail()/failStage(result->FAILED) | TaskInfo + failureInfo + completedStages | 任务终止失败 |
| TaskPausedEvent | Task | applyPauseAtStageBoundary | TaskInfo + currentStageName | 在阶段边界应用 |
| TaskResumedEvent | Task | resume | TaskInfo + currentStageName | 从暂停恢复 |
| TaskRetryStartedEvent | Task | retry(boolean) | TaskInfo + fromCheckpoint | 重试入口 |
| TaskRollingBackEvent | Task | startRollback()/rollback() | TaskInfo + reason | 回滚开始 |
| TaskRolledBackEvent | Task | completeRollback | TaskInfo | 回滚完成终态 |
| TaskRollbackFailedEvent | Task | failRollback | TaskInfo + FailureInfo | 回滚失败终态 |
| TaskCancelledEvent | Task | cancel | TaskInfo + cancelledBy | 用户/系统取消 |
| TaskCompletedEvent | Task | complete | TaskInfo + duration + completedStages | 成功完成终态 |
| TaskProgressEvent | Task | HeartbeatScheduler.tick | TaskInfo + progress | 非聚合方法内部触发 |

---
## 6. 值对象协作与使用场景
| 值对象 | 用途 | 典型方法 | 聚合内访问点 | 设计要点 |
|--------|------|----------|--------------|----------|
| PlanId / TaskId / TenantId | 标识封装 | getValue() | 聚合构造 / 事件封装 | 不暴露原始字符串/数字 |
| DeployVersion | 部署版本组合 | of(id,version) | 版本设置/查询 | 避免散落两个字段更新不一致 |
| StageProgress | 阶段进度 | next()/reset()/isCompleted() | completeStage()/retry()/start() | 不可变，替换式更新 |
| RetryPolicy | 重试策略 | canRetry()/incrementRetryCount() | retry(boolean)/retry() | 不可变，外部传入 globalMaxRetry |
| TaskCheckpoint | 断点存储结构 | lastCompletedStageIndex | recordCheckpoint()/restoreFromCheckpoint() | 覆盖式更新，外部序列化持久化 |
| TimeRange | 时间范围 | start()/end() | start()/complete()/fail()/cancel() | 保证开始/结束时间成对出现 |
| TaskDuration | 执行耗时 | between()/notStarted() | calculateDuration()/complete() | 从 TimeRange 派生，统一来源 |
| FailureInfo | 失败描述 | of(type,message) | fail()/failStage()/failRollback() | 分类错误，供事件和诊断使用 |

---
## 7. 聚合行为分类
| 类别 | Plan 行为 | Task 行为 |
|------|-----------|-----------|
| 生命周期启动 | markAsReady(), start() | markAsPending(), start() |
| 暂停/恢复 | pause(), resume() | requestPause(), applyPauseAtStageBoundary(), resume() |
| 阶段推进 | - | startStage(), completeStage(), failStage() |
| 状态终止 | complete(), markAsFailed() | complete(), fail(), cancel() |
| 回滚与重试 | - | startRollback(), rollback(), completeRollback(), failRollback(), retry(), retry(boolean) |
| 断点管理 | - | recordCheckpoint(), restoreFromCheckpoint(), clearCheckpoint() |
| 诊断辅助 | getTaskCount(), getStatus() | getStageResults(), getRetryCount(), getCheckpoint() |

---
## 8. 一致性与事务性说明
- 聚合内部方法原子性：单次方法调用完成状态与事件添加（事件列表作为暂存，不立即发布）。
- 跨聚合一致性：Plan 与多个 Task 的协同由应用服务编排，不在聚合内部承担分布式事务；失败时依赖补偿（回滚或重试）。
- 暂停/重试/回滚一致性：均在 Stage 边界处理，避免中间阶段部分状态写入导致不一致。

---
## 9. 领域建模决策摘要（与 RF 关联）
| 决策 | 原因 | RF 来源 | 风险 | 缓解 |
|------|------|---------|------|------|
| 任务使用值对象封装标识与版本 | 避免原始类型分散 | RF-13 | 需要序列化处理 | 统一 Jackson 配置 |
| StageProgress 不可变 | 并发下安全读写 | RF-18 | 频繁创建对象 | JVM 小对象可接受 |
| Checkpoint 覆盖式更新 | 简化并发控制 | RF-19 | 覆盖竞态 | 未来加版本号 |
| 领域事件集中在聚合 | 降低应用层耦合 | RF-11 | 事件发布失败处理缺失 | 引入事件总线重试机制（未来） |
| 协作式暂停 | 保证 Stage 原子性 | RF-19 | 暂停延迟 | 用户预期文档说明 |

---
## 10. 反模式避免清单
| 潜在反模式 | 当前避免措施 |
|------------|--------------|
| 贫血模型 | 行为下沉至聚合方法（start/pause/complete 等）|
| 跨聚合强引用 | 仅存 TaskId 列表，Task 不直接注入 Plan 对象 |
| 事务脚本 | 高阶业务逻辑由方法组合，不在应用层拼接原始字段 |
| 原始类型散落 | 标识与版本统一使用值对象（PlanId, TaskId, DeployVersion）|
| 无进度抽象 | StageProgress 封装阶段索引与总数 |
| 重试计数分散 | RetryPolicy 统一管理重试次数与上限 |

---
## 11. 审计与演进建议
| 演进点 | 当前状态 | 建议优先级 | 建议内容 |
|--------|----------|-----------|----------|
| Checkpoint 版本控制 | 无 | P1 | 增加 version 字段 + compareAndSet 保存 |
| 事件可靠投递 | 同步发布（无重试） | P1 | 引入事件发布缓冲与重试策略 |
| RetryPolicy 退避策略 | 固定 + 计数 | P2 | 增加指数退避 / 分级错误分类（TIMEOUT vs BUSINESS_ERROR）|
| 回滚信息增强 | 原样记录 | P2 | 增加受影响资源清单（config keys）|
| 多阶段复合统计 | 无聚合统计 | P3 | 聚合 StageResult 提供平均耗时/失败率 |

---
## 12. Definition of Done（领域模型文档）
| 条目 | 标准 |
|------|------|
| 聚合结构 | 字段/行为/事件触发点完整列出 |
| 状态机 | Plan/Task 状态转换矩阵覆盖所有路径 |
| 不变式 | 列表与代码守卫对应清晰 |
| 值对象 | 作用/访问点/不变式说明 |
| 反模式 | 明确已避免项与措施 |
| 演进建议 | 风险 + 优先级 + 建议动作 |

---
> 后续：完成审阅后将把与执行机相关的聚合扩展点放入单独的 state-management 文档（T-007）。

