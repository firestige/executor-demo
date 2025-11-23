# Onboarding Prompt（快速入门指引）

> 版本: 0.2  
> 最后更新: 2025-11-23  
> 适用对象: 新加入开发者 / AI 助手  
> 目标：5 分钟内建立正确的“心智模型”，避免引用过时设计（如 MySQL/JPA、物理部署视图）。

---
## 1. 一句话概括
多租户配置切换执行模块：按计划（Plan）调度租户级任务（Task），由执行机分阶段（Stage）顺序执行，可暂停、重试、回滚，失败位置通过 Redis Checkpoint 精确恢复。所有领域状态靠充血聚合 + 事件驱动模型实现，无关系型数据库持久化。

---
## 2. 核心特性速览
| 维度 | 说明 |
|------|------|
| 架构风格 | DDD + 事件驱动 + 协作式控制（暂停/重试在 Stage 边界）|
| 聚合根 | PlanAggregate / TaskAggregate（相互仅通过 ID 引用）|
| 状态机 | PlanStatus(11) / TaskStatus(13) 严格前置验证 + 聚合行为执行 |
| 执行机 | TaskExecutionOrchestrator（调度） + TaskExecutor（阶段编排）|
| 恢复机制 | Redis Checkpoint 保存 lastCompletedStageIndex；重试跳过已完成阶段 |
| 并发控制 | maxConcurrency + TenantConflictManager（单实例租户锁）|
| 可观测性 | 领域事件 + 心跳进度（每 10s TaskProgressEvent）|
| 持久化 | 运行态 InMemory，Checkpoint Redis；无 MySQL/JPA |

---
## 3. 你需要忘掉的旧信息（DON'T）
| 误区 | 正确做法 |
|------|----------|
| 使用 MySQL/JPA 维护聚合 | 当前无 RDBMS，聚合驻留内存，断点在 Redis 保存 |
| Plan 持有 Task 对象引用 | 只保存 TaskId 列表（聚合边界清晰）|
| 可以在任意时刻暂停 | 仅在 Stage 完成后协作式暂停（原子阶段内部不停顿）|
| 重试会重新执行全部阶段 | 从 Checkpoint 的 lastCompletedStageIndex+1 开始继续 |
| 回滚与重试相同 | 回滚逆序执行已完成阶段的补偿逻辑；重试继续未完成阶段 |
| 领域事件自动可靠投递 | 当前同步发布，未来才扩展重试/缓冲 |
| 多实例已实现租户互斥 | 现阶段仅本地锁，分布式锁是演进项 |

---
## 4. 阅读路径（建议照此顺序）
| 步骤 | 文件 | 目的 |
|------|------|------|
| 1 | `docs/architecture-overview.md` | 总体原则与索引定位 |
| 2 | `docs/views/logical-view.puml` | 聚合结构 / 值对象 / 领域事件全貌 |
| 3 | `docs/views/process-view.puml` | 状态机 / 执行 / 重试 / 暂停 / 回滚流程 |
| 4 | `docs/views/execution-engine-internal.puml` | 执行机内部协作与错误分支 |
| 5 | `docs/design/execution-engine.md` | 执行路径 + 扩展点 + 风险策略 |
| 6 | `docs/design/domain-model.md` | 不变式 + 值对象协作 + 事件触发点 |
| 7 | `docs/design/persistence.md` | Redis Checkpoint 与内存运行态策略 |
| 8 | `docs/design/checkpoint-mechanism.md` | 保存/恢复/版本化未来增强 |
| 9 | `docs/views/scenarios.puml` | 用例图（部署/重试/暂停/回滚）|

---
## 5. 关键代码入口对照
| 能力 | 入口类/包 | 说明 |
|------|-----------|------|
| 创建/启动计划 | `application.lifecycle.PlanLifecycleService` | 创建 Plan + 任务 ID 收集 + 状态推进 |
| 任务编排 | `application.orchestration.TaskExecutionOrchestrator` | 线程池与并发调度、提交 TaskExecutor |
| 执行阶段 | `infrastructure.execution.TaskExecutor` | 循环 Stage → 状态转换 → Checkpoint 保存 |
| 状态前置验证 | `domain.task.StateTransitionService` | 低成本内存校验（RF-18）|
| 聚合行为与事件 | `domain.task.TaskDomainService` / `domain.plan.PlanDomainService` | 调用聚合方法 + 收集领域事件 |
| Checkpoint 持久化 | `application.checkpoint.CheckpointService` + `infrastructure.persistence.checkpoint.RedisCheckpointRepository` | 保存/读取/清理断点 |
| 暂停/恢复 | TaskAggregate: `requestPause()` + `applyPauseAtStageBoundary()` + `resume()` | 协作式暂停在 Stage 边界应用 |
| 回滚 | TaskAggregate: `startRollback()/completeRollback()/failRollback()` | 逆序执行补偿逻辑 |
| 租户冲突协调 | `application.conflict.TenantConflictCoordinator` | 应用层冲突检测（Plan 创建前、Task 执行前） |
| 租户冲突管理 | `infrastructure.scheduling.TenantConflictManager` | 底层锁管理（内存/Redis），支持分布式部署 |

---
## 6. 常用领域不变式（快速核对）
| 不变式 | 位置 | 失败后行为 |
|--------|------|------------|
| Plan READY 必须 ≥1 Task | `PlanAggregate.markAsReady` | IllegalStateException |
| Plan start 仅在 READY | `PlanAggregate.start` | IllegalStateException |
| Task start 仅在 PENDING | `TaskAggregate.start` | IllegalStateException |
| Task complete 必须所有 Stage 完成 | `TaskAggregate.complete` + `stageProgress.isCompleted()` | IllegalStateException |
| Stage 完成/失败仅在 RUNNING | `TaskAggregate.validateCanCompleteStage` | IllegalStateException |
| Checkpoint 记录仅在 RUNNING | `TaskAggregate.recordCheckpoint` | IllegalStateException |
| 重试仅在 FAILED/ROLLED_BACK | `TaskAggregate.retry` | IllegalStateException |
| 回滚开始必须有快照或显式调用 | `TaskAggregate.startRollback` | IllegalStateException |

---
## 7. 领域事件分层（理解事件语义）
| 层级 | 示例 | 说明 |
|------|------|------|
| Plan 状态事件 | PlanStartedEvent / PlanCompletedEvent | 计划生命周期节点 |
| Task 状态事件 | TaskStartedEvent / TaskPausedEvent / TaskCompletedEvent | 任务级状态与控制 |
| Task Stage 事件 | TaskStageStartedEvent / TaskStageCompletedEvent / TaskStageFailedEvent | 原子阶段执行结果 |
| 进度/心跳事件 | TaskProgressEvent | 周期性活跃与进度广播 |
| 回滚/重试事件 | TaskRollingBackEvent / TaskRetryStartedEvent | 补偿与恢复动作入口 |

事件发布机制：聚合方法内 addDomainEvent → 应用层统一发布（当前同步）。

---
## 8. 重试与回滚差异（易混点）
| 场景 | 重试 | 回滚 |
|------|------|------|
| 触发条件 | FAILED / ROLLED_BACK | FAILED（且需补偿）|
| 起始阶段 | lastCompleted + 1 | 已完成阶段逆序 |
| 状态流转 | FAILED → RUNNING → COMPLETED | FAILED → ROLLING_BACK → ROLLED_BACK / ROLLBACK_FAILED |
| 是否使用 Checkpoint | 是（fromCheckpoint=true）| 可选（逻辑不依赖 Checkpoint）|
| 目标 | 完成剩余阶段 | 恢复上一次良好配置状态 |

---
## 9. 调试建议（快速定位问题）
| 问题类型 | 优先查看 | 说明 |
|----------|----------|------|
| 任务不前进 | `TaskStatus` & `stageProgress` | 检查是否卡在 Stage 内部耗时操作 |
| 重试未跳过已完成阶段 | Redis Checkpoint 是否存在 / lastCompletedStageIndex | 断点过期或索引未更新 |
| 暂停无效 | 是否在 Stage 内部设置 pauseRequested | 只能在边界应用 |
| 回滚失败 | FailureInfo + StageResult 列表 | 检查是否所有已完成阶段有补偿逻辑 |
| 冲突并发 | TenantConflictManager 状态 | 多实例下为已知风险（演进项）|

---
## 10. 后续演进（你可能会遇到的 TODO）
| 方向 | 计划 | 意义 |
|------|------|------|
| 分布式租户锁 | Redis / Redisson 锁 | 解决多实例并发冲突 |
| Checkpoint 版本化 | version + CAS | 避免覆盖竞态与回退风险 |
| 事件可靠投递 | 缓冲 + 重试 + 死信 | 提升可观测与外部集成稳定性 |
| 动态心跳 | 自适应频率 | 降低大规模任务调度开销 |
| 指标体系 | Micrometer + Prometheus | 分析性能与失败模式 |

---
## 11. 快速提问模板（复制即可）
```
请帮我分析 Task 在 FAILED 状态调用 retry(fromCheckpoint=true) 时的执行路径：
需要：状态检查 → Checkpoint 加载 → startIndex 计算 → 跳过阶段逻辑 → 事件触发顺序。
```
```
列出 pause 与 applyPauseAtStageBoundary 的区别，以及为何必须在 Stage 边界应用暂停。
```
```
评估将心跳周期从 10s 调整为 5s 的风险：线程数、事件风暴、暂停检测频率、改进建议。
```

---
## 12. 立即确认你是否理解（自测清单）
| 问题 | 你是否能回答？ |
|------|----------------|
| 为什么不能在 Stage 中途暂停？ | ✔/✘ |
| 重试与回滚在事件层面有什么不同？ | ✔/✘ |
| Checkpoint 写入时机有哪些？ | ✔/✘ |
| Plan 为什么不直接持有 Task 对象？ | ✔/✘ |
| 心跳事件的作用是什么？ | ✔/✘ |

如果存在“✘”，回到对应章节补课。

---
## 13. 禁区与约束总结（红线）
| 红线 | 说明 |
|------|------|
| 不新增物理视图 | 本模块不负责部署拓扑 |
| 不引入关系型持久化假设 | 保持内存 + Redis 组合简单性 |
| 不直接修改聚合状态字段 | 通过聚合方法保证不变式 |
| 不在 Stage 内主动中断线程 | 保证阶段原子性 |
| 不跳过 StateTransitionService 前置校验 | 保障状态机合法性 |

---
## 14. 链接索引（快速跳转）
| 类别 | 链接 |
|------|------|
| 架构总纲 | `docs/architecture-overview.md` |
| 逻辑视图 | `docs/views/logical-view.puml` |
| 进程视图 | `docs/views/process-view.puml` |
| 开发视图 | `docs/views/development-view.puml` |
| 场景视图 | `docs/views/scenarios.puml` |
| 执行机内部视图 | `docs/views/execution-engine-internal.puml` |
| 执行机设计文档 | `docs/design/execution-engine.md` |
| 领域模型设计 | `docs/design/domain-model.md` |
| 持久化设计 | `docs/design/persistence.md` |
| Checkpoint 机制 | `docs/design/checkpoint-mechanism.md` |
| 开发日志 | `developlog.md` |
| TODO | `TODO.md` |
| 架构提示词 | `docs/prompts/architecture-prompt.md` |

---
> 现在：如果你已经可以正确描述“重试 vs 回滚 vs 暂停恢复”的差异，就可以进入下一步代码走查；否则回到第 8 节再看一遍。
