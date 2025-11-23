# 执行机详细设计（Execution Engine Design）

> 任务: T-003  
> 状态: 进行中（正式文档，来源 temp/task-003-execution-engine-design.md 整理）  
> 最后更新: 2025-11-23

---
## 1. 设计目标
提供一个可扩展、可恢复、事件驱动的租户级配置切换执行核心，支持：
- 精确的状态转换（前置验证 + 聚合行为）
- 原子阶段执行 + 协作式暂停
- 失败断点保存与重试恢复
- 回滚逆序补偿
- 心跳与进度事件上报

---
## 2. 架构角色与协作
| 组件 | 层 | 职责 | 交互对象 |
|------|----|------|----------|
| TaskExecutionOrchestrator | Application | 任务调度/并发控制/线程池管理 | TaskExecutor, TenantConflictManager |
| TaskExecutor | Infrastructure | 编排 Stage 执行、心跳、Checkpoint、状态事件 | TaskDomainService, StateTransitionService, HeartbeatScheduler, CheckpointService |
| TaskDomainService | Domain | 封装聚合行为 + 事件收集发布 | TaskAggregate, DomainEventPublisher |
| StateTransitionService | Domain | 低成本状态转换合法性校验 | TaskExecutor, TaskAggregate |
| CheckpointService | Application | Checkpoint 的序列化/保存/加载/清理 | RedisCheckpointRepository, InMemoryCheckpointRepository |
| HeartbeatScheduler | Infrastructure | 周期进度事件与活跃信号 | TaskExecutor, MetricsRegistry |
| TenantConflictManager | Infrastructure | 租户并发互斥 | TaskExecutionOrchestrator |

---
## 3. 核心数据结构
### 3.1 TaskCheckpoint
| 字段 | 类型 | 说明 |
|------|------|------|
| lastCompletedStageIndex | int | 最后成功完成的 stage 索引 |
| completedStages | List<Integer> | 已完成 stage 索引集合（顺序）|
| contextData | Map<String,Object> | 恢复所需的附加上下文（可选）|
| savedAt | LocalDateTime | 保存时间戳 |

### 3.2 StageResult
| 字段 | 类型 | 说明 |
|------|------|------|
| stageName | String | 阶段名称 |
| status | StageStatus | 结果状态（COMPLETED/FAILED/...) |
| duration | Duration | 执行耗时 |
| failureInfo | FailureInfo | 失败根因（仅失败时非 null）|

### 3.3 FailureInfo
| 字段 | 类型 | 说明 |
|------|------|------|
| errorType | ErrorType | 错误分类（VALIDATION / EXECUTION / TIMEOUT / ROLLBACK / INFRASTRUCTURE）|
| message | String | 简要描述 |
| details | String | 详细信息（堆栈/上下文）|
| occurredAt | LocalDateTime | 发生时间 |

---
## 4. 执行路径详解
### 4.1 正常执行
流程参考 `execution-engine-internal.puml` 正常执行时序：
1. 前置校验：StateTransitionService.canTransition(PENDING→RUNNING)
2. 启动 Task：TaskDomainService.startTask() → TaskStartedEvent
3. 启动心跳：HeartbeatScheduler.start() 每 10s 发布 TaskProgressEvent
4. 加载断点：CheckpointService.loadCheckpoint()（若存在��计算起始 stage）
5. Stage 循环：对每个 Stage 执行 → 成功则 completeStage() + 保存 Checkpoint
6. 中途控制：在阶段边界检查暂停/取消标志
7. 完成：completeTask() → 清理心跳与断点 → TaskCompletedEvent

### 4.2 失败处理
- Stage 执行返回失败 → failStage() → failTask()，保存断点，发布 TaskStageFailedEvent + TaskFailedEvent
- 未捕获异常 → try/catch 捕获 → 构造 FailureInfo → failTask()

### 4.3 重试执行
1. 状态前置：FAILED/PAUSED → RUNNING 校验
2. resumeTask() → TaskResumedEvent（fromCheckpoint = true）
3. 加载断点 → startIndex = lastCompletedStageIndex + 1
4. 执行剩余 Stage，逻辑与正常执行相同
5. 如果再次失败 → 覆盖断点并返回 FAILED；成功则 completeTask()

### 4.4 暂停与恢复
- 暂停：在 Stage 完成后检测到 pauseRequested → pauseTask() → 保存断点 → TaskPausedEvent
- 恢复：resumeTask() + 加载断点 → 从下一个 Stage 继续执行

### 4.5 回滚
适用于已失败需要逆序补偿：
1. FAILED → ROLLING_BACK 校验
2. startRollback() → TaskRollingBackEvent
3. 逆序遍历 completedStages 调用 rollback()
4. 全部成功 → completeRollback() → TaskRolledBackEvent；否则 failRollback() → TaskRollbackFailedEvent

---
## 5. 状态转换与守卫
| 当前状态 | 目标状态 | 触发操作 | 前置条件 | 失败行为 |
|----------|----------|----------|----------|----------|
| PENDING | RUNNING | startTask | canTransition(PENDING→RUNNING)=true | 返回失败结果，不写入聚合 |
| RUNNING | PAUSED | pauseTask | 标志已设置 + canTransition(RUNNING→PAUSED) | 忽略并继续执行 |
| PAUSED | RUNNING | resumeTask | canTransition(PAUSED→RUNNING) | 返回失败结果 |
| FAILED | RUNNING | resumeTask(retry) | canTransition(FAILED→RUNNING) | 返回失败结果 |
| FAILED | ROLLING_BACK | startRollback | canTransition(FAILED→ROLLING_BACK) | 返回失败结果 |
| ROLLING_BACK | ROLLED_BACK | completeRollback | 所有 rollback 成功 | failRollback |
| RUNNING | COMPLETED | completeTask | 所有 Stage 已成功 | 返回失败结果 |

---
## 6. Checkpoint 策略
| 时机 | 行为 | 目的 |
|------|------|------|
| Stage 成功 | 保存 | 支持断点续传与重试跳过 |
| Stage 失败 | 保存 | 记录失败位置与上下文 |
| 暂停 | 保存 | 恢复继续执行 |
| 取消 | 保存 | 支持故障分析（可选）|
| 完成/回滚完成 | 清理 | 避免脏数据与空间占用 |

序列化：JSON（Jackson），TTL 默认 7 天（可配置）。
并发保护：当前无版本号 → 风险：高频写覆盖；后续引入 CAS 或版本字段。

---
## 7. 心跳与进度事件
| 要素 | 说明 |
|------|------|
| 周期 | 默认 10 秒，可配置（下限建议 5 秒）|
| 内容 | completedStages, totalStages, progressPercent |
| 风险 | 大量并发导致调度线程膨胀 → 未来合并调度器或批量上报 |
| 失败处理 | 心跳线程异常自动重启；记录指标 alarm_counter++ |

---
## 8. 扩展点一览
| 扩展点 | 接口 | 扩展方式 | 示例 |
|--------|------|----------|------|
| 阶段 | StageFactory | 新增组合或动态策略 | CompositeServiceStage |
| 步骤 | StageStep | 实现 execute() | RedisKeyValueWriteStep, HttpRequestStep |
| Checkpoint 存储 | CheckpointRepository | 新增实现 | Redis / InMemory |
| Metrics | MetricsRegistry | 新增具体记录实现 | Noop → Micrometer |
| 租户锁 | TenantConflictManager | 新增分布式实现 | 内存（InMemory）→ Redis 锁（RedisTenantLockManager）|
| 回滚策略 | rollback() | 新增补偿逻辑 | 自定义 Stage 回滚步骤 |

**说明**：
- `TenantConflictManager` 可扩展为分布式锁实现（已在 T-016 中实现 Redis 锁）
- `TenantConflictCoordinator` 封装冲突检测逻辑，屏蔽底层锁实现细节

约束：扩展不能破坏聚合不变式；不得跳过状态校验；不得在 Stage 内部进行暂停。

---
## 9. 错误与降级策略
| 场景 | 策略 |
|------|------|
| Redis 不可用 | 回退到 InMemoryCheckpointRepository（标记非持久）|
| 心跳线程异常 | 自动重启；记录指标 alarm_counter++ |
| 阶段长时间阻塞 | 当前仅可观测（进度无变化）；未来引入超时处理 |
| 高并发冲突 | 租户锁失败时跳过该 Task 提交并记录冲突事件 |
| 重试风暴 | RetryPolicy 限制次数 + 未来加入退避 |

---
## 10. 指标建议（待实现）
| 指标 | 类型 | 说明 |
|------|------|------|
| task_active | Counter | 当前活跃任务数（执行开始+1，结束-1）|
| task_failed | Counter | 失败任务次数 |
| task_completed | Counter | 成功任务次数 |
| heartbeat_sent | Counter | 心跳事件发送次数 |
| stage_duration | Timer | 单 Stage 耗时 |
| task_duration | Timer | Task 总耗时 |

---
## 11. 风险与改进路线
| 风险 | 当前状态 | 短期优化 | 长期演进 |
|------|----------|----------|----------|
| 心跳调度线程数膨胀 | 每 Task 一个调度器 | 合并为共享调度池 | 批量事件 + 可配置频率自适应 |
| Checkpoint 覆盖 | 无版本控制 | 增加乐观锁字段 version | 使用原子脚本或 Redis 事务 |
| 分布式租户锁缺失 | 本地内存 | 引入 Redis 锁实现 tryAcquire | 引入 Redisson + 超时续租 |
| 事件可靠性不足 | 同步发布 | 提供事件缓冲队列（内存） | 引入持久化事件总线（Kafka/Redis Stream）|

---
## 12. 与其他文档的关系
| 文档 | 关联内容 |
|------|----------|
| architecture-overview.md | 总体原则、演进里程碑、索引引用执行机设计 |
| process-view.puml | 状态机 + 执行/暂停/重试/回滚时序图初版本 |
| logical-view.puml | 聚合根与事件类型定义 |
| execution-engine-internal.puml | 本设计的补充 UML（类图 + 多条内部时序）|

---
## 13. 事件触发点速查表
| 事件 | 触发方法 | 所属聚合 | 说明 |
|------|----------|----------|------|
| TaskStartedEvent | TaskDomainService.startTask | TaskAggregate | 任务从 PENDING 进入 RUNNING |
| TaskStageStartedEvent | TaskDomainService.startStage | TaskAggregate | 某 Stage 开始执行前触发 |
| TaskStageCompletedEvent | TaskDomainService.completeStage | TaskAggregate | Stage 成功结束 |
| TaskStageFailedEvent | TaskDomainService.failStage | TaskAggregate | Stage 失败（含 FailureInfo）|
| TaskProgressEvent | HeartbeatScheduler.tick | TaskAggregate | 心跳周期触发（每10s）|
| TaskPausedEvent | TaskDomainService.pauseTask | TaskAggregate | 协作式暂停在 Stage 边界 |
| TaskResumedEvent | TaskDomainService.resumeTask | TaskAggregate | 从 PAUSED 或 FAILED（重试）恢复 |
| TaskFailedEvent | TaskDomainService.failTask | TaskAggregate | 任务终止失败（含 CANCELLED、ROLLBACK_FAILED）|
| TaskRetryStartedEvent | TaskDomainService.resumeTask (FAILED→RUNNING) | TaskAggregate | 重试入口（fromCheckpoint=true）|
| TaskCompletedEvent | TaskDomainService.completeTask | TaskAggregate | 全部 Stage 完成 |
| TaskRollingBackEvent | TaskDomainService.startRollback | TaskAggregate | 回滚流程开始 |
| TaskRolledBackEvent | TaskDomainService.completeRollback | TaskAggregate | 回滚完成 |
| TaskRollbackFailedEvent | TaskDomainService.failRollback | TaskAggregate | 回滚失败终止 |

> 回滚与重试不产生 StageStartedEvent/StageCompletedEvent 对已跳过的阶段，重试从下一个未完成阶段开始。

## 14. Definition of Done（执行机完善标准）
| 目标 | 标准 |
|------|------|
| 行为可视化 | 正常/失败/重试/暂停/回滚/错误分支均有时序图（execution-engine-internal.puml）|
| 不变式明确 | 各状态转换在 StateTransitionService + 聚合方法双重验证 |
| 扩展点规范 | 扩展点表列出接口+示例+约束（第8节）|
| 错误分类清晰 | FailureInfo 与 ErrorType 列出并在错误分支图体现 |
| 事件完整性 | 事件触发点速查表覆盖所有领域事件（第13节）|
| Checkpoint 安全 | 保存策略与清理策略在执行路径中明确（第6节）|
| 风险控制 | 风险与短/长期路线列出（第11节）|
| 文档关联 | 总纲、视图、内部 UML 与本设计交叉引用完善 |

## 15. 下一步工作（T-003 后续）
| 事项 | 描述 | 优先级 |
|------|------|------|
| 优化心跳调度 | 合并调度器并批量上报进度 | P2 |
| 增加版本化 Checkpoint | 引入 version 字段并在保存时校验 | P1 |
| 引入分布式租户锁 | 提供 Redis 锁实现 | P2 |
| 增加 RetryPolicy 退避 | 指数退避 + 分级错误分类 | P3 |
| 指标实现 | Micrometer 接入并定义仪表盘 | P3 |

---
## 16. 附录：关键方法摘要
| 方法 | 所属 | 说明 |
|------|------|------|
| execute() | TaskExecutor | 主入口，编排整条执行链路 |
| startTask() | TaskDomainService | 状态 PENDING→RUNNING，产生 TaskStartedEvent |
| completeStage() | TaskDomainService | 写入结果 + 进度推进 + 事件 |
| failStage() | TaskDomainService | 标记阶段失败并记录 FailureInfo |
| failTask() | TaskDomainService | 标记任务失败并触发 TaskFailedEvent |
| resumeTask() | TaskDomainService | PAUSED/FAILED → RUNNING（fromCheckpoint 标记）|
| pauseTask() | TaskDomainService | RUNNING→PAUSED（协作式）|
| startRollback() | TaskDomainService | FAILED→ROLLING_BACK |
| completeRollback() | TaskDomainService | ROLLING_BACK→ROLLED_BACK |
| saveCheckpoint() | CheckpointService | JSON 序列化并写入后端存储 |
| loadCheckpoint() | CheckpointService | 获取并反序列化断点数据 |

---
## 17. 错误分支图引用
参见：`execution-engine-internal.puml` 中 “执行机内部-错误分支总览” 时序用于统一查看失败/暂停/取消/继续决策逻辑。

> 本文档会随 T-003 迭代更新，完成后再回填至总纲索引最终状态。

## 18. 事件消费端（Plan）
| 监听器 | 监听事件 | 动作 | 说明 |
|--------|----------|------|------|
| PlanStartedListener | PlanStartedEvent | PlanExecutionFacade.executePlan(planId) | 启动计划后触发编排提交 |
| PlanResumedListener | PlanResumedEvent | PlanExecutionFacade.resumePlanExecution(planId) | 恢复计划后继续编排 |
| PlanPausedListener | PlanPausedEvent | 审计/通知（暂停在 Stage 边界生效） | 协作式暂停，无需显式编排 |
| PlanCompletionListener | PlanCompletedEvent / PlanFailedEvent | PlanSchedulingStrategy.onPlanCompleted(planId, tenantIds) | 清理租户冲突标记 |

发布链路：PlanAggregate → PlanDomainService.publishEvent → DomainEventPublisher(Spring) → @EventListener（上表）。

桥接可视化：见 `views/plan-to-execution-bridge.puml`（Plan 事件 → 应用层桥接 → 编排/执行）。
