# 状态管理设计（State Management Design）

> 任务: T-007  
> 状态: 初稿  
> 最后更新: 2025-11-23

---
## 1. 设计目标
统一描述 PlanStatus 与 TaskStatus 的状态集合、合法转换、失败与恢复策略、暂停/重试/回滚的交互边界，为后续分布式扩展（锁与事件可靠性）提供基础。

---
## 2. 状态集合对比
| 聚合 | 状态枚举 | 终态集合 | 失败相关 | 控制相关 |
|------|----------|----------|----------|----------|
| Plan | CREATED, VALIDATING, READY, RUNNING, PAUSED, PARTIAL_FAILED, COMPLETED, ROLLING_BACK, ROLLED_BACK, FAILED, CANCELLED | COMPLETED, FAILED, ROLLED_BACK, CANCELLED | PARTIAL_FAILED, FAILED | PAUSED, ROLLING_BACK |
| Task | CREATED, VALIDATING, VALIDATION_FAILED, PENDING, RUNNING, PAUSED, RESUMING, COMPLETED, FAILED, ROLLING_BACK, ROLLBACK_FAILED, ROLLED_BACK, CANCELLED | COMPLETED, VALIDATION_FAILED, ROLLED_BACK, CANCELLED | VALIDATION_FAILED, FAILED, ROLLBACK_FAILED | PAUSED, RESUMING, ROLLING_BACK |

差异要点：
- Plan 有 PARTIAL_FAILED 表示部分 Task 失败仍可继续；Task 无对应状态（直接 FAILED）。
- Task 有 VALIDATION_FAILED 与 ROLLBACK_FAILED（更细粒度的失败分类）。
- Plan 不包含 RESUMING（恢复过程由应用层编排），Task 包含 RESUMING（可选过渡，用于未来更细心跳信号）。

---
## 3. Plan 状态转换矩阵
| 当前 | 目标 | 触发方法 | 前置条件 | 失败事件/行为 | 生成事件 |
|------|------|----------|----------|--------------|----------|
| CREATED | VALIDATING | validate() | 新建后首次校验 | - | (可选) PlanValidatingEvent* |
| VALIDATING | READY | validationPassed() | 校验通过 & 有 ≥1 Task | - | PlanReadyEvent |
| VALIDATING | FAILED | validationFailed() | 校验失败 | PlanFailedEvent | PlanFailedEvent |
| READY | RUNNING | start() | 有 ≥1 Task | - | PlanStartedEvent |
| RUNNING | PAUSED | pause() | 运维触发 | - | PlanPausedEvent |
| PAUSED | RUNNING | resume() | 暂停中 | - | PlanResumedEvent |
| RUNNING | PARTIAL_FAILED | taskFailed(partial=true) | 至少 1 任务失败，仍有可继续任务 | 不会终止计划 | PlanPartialFailedEvent* |
| PARTIAL_FAILED | RUNNING | recoverPartial() | 剩余任务可继续 | - | PlanResumedEvent* |
| PARTIAL_FAILED | FAILED | escalate() | 运维或策略判定不可继续 | 失败终止 | PlanFailedEvent |
| RUNNING | COMPLETED | allTasksCompleted() | 所有 Task 终态成功 | - | PlanCompletedEvent |
| RUNNING | FAILED | taskFailed(partial=false) | 严重失败不可继续 | 失败终止 | PlanFailedEvent |
| RUNNING | ROLLING_BACK | rollbackStart() | 运维触发 & 有回滚语义 | - | PlanRollingBackEvent* |
| ROLLING_BACK | ROLLED_BACK | rollbackComplete() | 回滚成功 | - | PlanRolledBackEvent* |
| ROLLING_BACK | FAILED | rollbackFailed() | 回滚失败 | 失败终止 | PlanFailedEvent |
| RUNNING | CANCELLED | cancel() | 运维触发 | 终止 | PlanCancelledEvent* |

(* 标记事件当前可能未实现，预留扩展命名。)

---
## 4. Task 状态转换矩阵
| 当前 | 目标 | 触发方法 | 前置条件 | 失败事件/行为 | 生成事件 |
|------|------|----------|----------|--------------|----------|
| CREATED | VALIDATING | validate() | 新建 | - | TaskValidatingEvent* |
| VALIDATING | VALIDATION_FAILED | validationFailed() | 校验失败 | 终态 | TaskFailedEvent (type=VALIDATION) |
| VALIDATING | PENDING | validationPassed() | 校验通过 | - | TaskPendingEvent* |
| PENDING | RUNNING | start() | 准备执行 | - | TaskStartedEvent |
| RUNNING | PAUSED | requestPause()+applyPauseAtStageBoundary() | pauseRequested=true & Stage 边界 | - | TaskPausedEvent |
| PAUSED | RUNNING | resume() | 暂停中 | - | TaskResumedEvent |
| RUNNING | COMPLETED | complete() | 所有 Stage 完成 | - | TaskCompletedEvent |
| RUNNING | FAILED | fail()/failStage(result->FAILED) | 致命错误/不可继续 | 终态失败 | TaskFailedEvent |
| RUNNING | CANCELLED | cancel() | 运维触发 | 终态取消 | TaskCancelledEvent |
| FAILED | RUNNING | retry(fromCheckpoint?) | 可重试 & 未达最大重试 | - | TaskRetryStartedEvent |
| ROLLED_BACK | RUNNING | retry(fromCheckpoint?) | 可重试 & 未达最大重试 | - | TaskRetryStartedEvent |
| FAILED | ROLLING_BACK | startRollback() | 有可用快照 | - | TaskRollingBackEvent |
| ROLLING_BACK | ROLLED_BACK | rollbackComplete() | 成功回滚 | - | TaskRolledBackEvent |
| ROLLING_BACK | ROLLBACK_FAILED | rollbackFailed() | 回滚失败 | 终态失败 | TaskRollbackFailedEvent |

---
## 5. 失败与恢复路径（Task）
| 失败类型 | 进入方式 | 恢复策略 | 依赖数据 | 失败事件 |
|----------|----------|----------|----------|----------|
| VALIDATION_FAILED | 校验逻辑发现问题 | 重新校验（外部修复后）或终止 | 外部配置修复 | TaskFailedEvent(type=VALIDATION) |
| FAILED | 执行阶段失败 / fatalError | 重试（Checkpoint 或重置）/ 回滚 | Checkpoint, StageProgress | TaskFailedEvent |
| ROLLBACK_FAILED | 回滚步骤失败 | 再次回滚尝试 / 手工干预 | prevConfigSnapshot | TaskRollbackFailedEvent |
| ROLLED_BACK | 回滚成功（终态） | 可选择重试新执行 | prevConfigSnapshot, RetryPolicy | TaskRolledBackEvent |

恢复优先级：Checkpoint 重试 > 重置重试 > 回滚（根据失败影响面）。

---
## 6. 协作式暂停机制
| 阶段 | 行为 | 原因 |
|------|------|------|
| requestPause() | 标记 pauseRequested=true | 不打断当前 Stage 保持原子性 |
| applyPauseAtStageBoundary() | 检查标记并转换为 PAUSED | Stage 成功/失败后唯一安全窗口 |
| resume() | 状态 PAUSED → RUNNING | 继续执行剩余 Stage |

优点：避免半完成阶段中断产生不可重现状态；缺点：暂停可能延迟（需文档/监控解释）。

---
## 7. 重试与回滚交互边界
| 操作 | 可触发状态 | 前置 | 结果 |
|------|-------------|------|------|
| retry(fromCheckpoint) | FAILED / ROLLED_BACK | Checkpoint 可用 & 未超重试次数 | 跳过已完成阶段继续 |
| retry(reset) | FAILED / ROLLED_BACK | 不从断点；策略允许 | 清空 stageProgress 重头执行 |
| startRollback() | FAILED | 有快照 | 进入 ROLLING_BACK |
| rollback() | 非终态（执行中/暂停）| 运维强制 + 快照 | 进入 ROLLING_BACK |
| completeRollback() | ROLLING_BACK | 回滚全部成功 | ROLLED_BACK |
| failRollback() | ROLLING_BACK | 回滚出现不可恢复错误 | ROLLBACK_FAILED |

---
## 8. 取消操作语义
| 操作 | 适用状态 | 行为 | 事件 |
|------|----------|------|------|
| cancel(plan) | RUNNING/PAUSED | 标记 CANCELLED（Plan）| PlanCancelledEvent* |
| cancel(task) | RUNNING/PAUSED | 标记 CANCELLED（Task）| TaskCancelledEvent |

取消不清理 Checkpoint（保留诊断），后续不允许重试（终态）。

---
## 9. UML 视图索引
- 总览与子图：`views/state-management.puml`
  - 总览：Plan / Task 对比
  - 子图：Plan 细节、Task 细节、Task 失败与恢复、暂停/重试/回滚交互

---
## 10. 风险与改进
| 风险 | 描述 | 当前处理 | 改进建议 |
|------|------|----------|----------|
| 暂停延迟 | Stage 耗时长导致暂停响应慢 | 协作式边界设计 | 增加长时 Stage 进度子事件 |
| 回滚失败无法自动恢复 | 回滚逻辑缺少补偿重试 | 手工重试 | 引入多阶段补偿与超时重试 |
| 重试风暴 | 连续失败快速重试 | RetryPolicy 限制次数 | 指数退避 + 分类限流 |
| 状态漂移（多实例） | 无分布式锁 | 单实例运行 | 引入 Redis 锁 + 状态版本号 |
| 事件同步发布失败丢失 | 无缓冲重试 | 直接抛出 | 引入事件总线与持久化队列 |

---
## 11. Definition of Done（T-007）
| 条目 | 标准 |
|------|------|
| 枚举覆盖 | 所有 PlanStatus / TaskStatus 出现在矩阵与图中 |
| 转换矩阵 | 列出触发方法与前置条件/事件 |
| 失败路径 | VALIDATION_FAILED / FAILED / ROLLBACK_FAILED / ROLLED_BACK 均有策略 |
| UML | state-management.puml 包含 4 个子图 |
| 暂停/重试/回滚 | 交互边界明确（第7节 + UML 子图）|
| 风险与演进 | 至少 5 项风险与改进建议 |

---
## 12. 后续演进（Roadmap）
| 项目 | 描述 | 优先级 |
|------|------|--------|
| 分布式锁 | 基于 Redis/Redisson 的租户锁与状态原子检查 | P1 |
| 事件持久化 | 引入持久化事件总线（Kafka/Redis Stream） | P1 |
| 状态版本化 | 聚合状态更新增加版本号 + CAS | P2 |
| 暂停快速响应 | 长时 Stage 内引入心跳子事件 | P2 |
| 退避策略 | RetryPolicy 增加指数退避与分类维度 | P2 |
| 回滚增强 | 引入多步骤补偿与资源清单 | P3 |

---
> 审阅后：若无结构调整需求，将合入总纲索引并删除临时方案文档。

