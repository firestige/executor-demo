# 架构设计总纲

> **最后更新**: 2025-11-23  
> **状态**: Active（基于已验证 4+1 视图重写）

---

## 1. 概述
`executor-demo` 是一个多租户配置切换执行模块（蓝绿/渐进/自定义阶段组合），聚焦于“执行机”与任务编排逻辑。它本身不是独立部署系统，而是可嵌入更大平台中的一个业务模块。模块内部遵循 DDD 战术模式 + 事件驱动，对外暴露统一 Facade，内部以 Plan / Task / Stage 为核心分层。

### 1.1 作用域（Scope）
- 不负责：全局部署编排、环境治理、全链路观测平台
- 负责：单次部署计划中的租户级任务编排、执行、恢复、回滚、重试、进度与事件发布
- 非目标：物理部署拓扑（由宿主系统决定）

### 1.2 核心模型
- Plan：部署计划（聚合根），管理任务 ID 列表与生命周期、并发阈值
- Task：租户级执行单元（聚合根），封装状态机、Stage 进度、重试/回滚/暂停语义
- Stage：原子执行阶段（不可切片），由多个 Step 组成
- Step：最小执行指令（Redis 写入 / HTTP 请求 / 健康检查 / Pub/Sub 广播）

---

## 2. 架构原则（Architecture Principles）
| 编号 | 原则 | 说明 |
|------|------|------|
| AP-01 | 聚合最小一致性边界 | Plan 与 Task 为独立聚合根，跨聚合仅通过 ID 引用（RF-07）|
| AP-02 | 充血模型优先 | 业务状态转换与不变式保护封装在聚合方法中（RF-06/13）|
| AP-03 | 分层 + 依赖倒置 | Facade → Application → Domain ← Infrastructure；Domain 不依赖外层 |
| AP-04 | 事件驱动演进 | 状态变化、阶段进度均生成领域事件（RF-11/19）|
| AP-05 | 显式错误与恢复 | FailureInfo + Checkpoint 支持失败诊断与断点续传（RF-19）|
| AP-06 | 协作式控制 | 暂停/取消仅在 Stage 边界响应，避免中间状态污染 |
| AP-07 | 幂等与可重复 | 重试从 Checkpoint 恢复，补偿进度事件保持事件序列连续 |
| AP-08 | 低成本前置验证 | StateTransitionService 先内存校验，再调用高成本领域服务（RF-18）|
| AP-09 | 可组合阶段 | StageFactory 允许通过配置组合不同服务阶段（RF-13/扩展）|
| AP-10 | 简化仓储接口 | Repository 只暴露聚合完整生命周期方法（RF-09）|

---

## 3. 战术设计与演进里程碑
| RF 编号 | 演进摘要 | 对应决策 |
|---------|----------|-----------|
| RF-06 | 贫血模型 → 充血聚合 | 引入业务行为方法、状态守卫 |
| RF-07 | 修正聚合边界 | Plan 仅持有 TaskId 列表，Task 反向引用 PlanId |
| RF-08/13 | 值对象与策略扩展 | 引入 TaskId/PlanId/TenantId/DeployVersion 等 VO，Stage/Step 可扩展 |
| RF-09 | 仓储简化 | CRUD → save/remove/find + 语义化查询 |
| RF-11 | 领域事件内聚 | 聚合收集事件，应用层统一发布 |
| RF-18 | 状态转换优化 | 前置内存校验 + 后置聚合行为 + 事件驱动 |
| RF-19 | Checkpoint / Stage 事件增强 | 精细化 StageStarted/Completed/Failed；恢复补偿进度 |
| RF-20 | 编排层拆分 | 引入 TaskExecutionOrchestrator 分离执行调度与业务逻辑 |

---

## 4. 分层结构（High-Level Layering）
- Facade：统一入口（DeploymentTaskFacade），参数校验 + DTO 转换
- Application：PlanLifecycleService、TaskOperationService、TaskExecutionOrchestrator（线程池与并发控制）
- Domain：PlanAggregate、TaskAggregate、领域服务、值对象、状态枚举、事件层次
- Infrastructure：执行引擎（TaskExecutor / TaskStage / StageStep）、仓储实现（InMemory/Redis）、冲突管理、心跳与指标

> 详见：分层与包结构视图：[development-view.puml](./views/development-view.puml) | 补充说明文档：[development-view.md](./views/development-view.md)

---

## 5. 领域模型摘要
- 双聚合：PlanAggregate / TaskAggregate 独立生命周期
- 事件层次：DomainEvent → PlanStatusEvent / TaskStatusEvent → TaskStageStatusEvent → 具体事件
- 值对象族：PlanId、TaskId、TenantId、DeployVersion、TimeRange、TaskCheckpoint、StageProgress、TaskDuration、FailureInfo、RetryPolicy 等
- 状态机：PlanStatus(11)、TaskStatus(13)；转换由 StateTransitionService 预验证 + 聚合方法执行

> 详见：领域模型视图：[logical-view.puml](./views/logical-view.puml) | 补充说明文档：[logical-view.md](./views/logical-view.md)

---

## 6. 生命周期与执行流程
- 执行主线：创建 Plan → 创建 Tasks → READY → RUNNING（任务并发受 maxConcurrency + 租户冲突约束） → 完成 / 部分失败 / 失败 / 回滚 / 取消
- Task 执行：TaskExecutionOrchestrator 分配执行 → TaskExecutor 依序执行 Stage → 保存阶段性 Checkpoint → 心跳与进度事件上报
- 重试：FAILED / PAUSED 状态 → 从 Checkpoint 恢复 → 跳过已完成 Stage
- 回滚：逆序执行已完成 Stage 的 rollback 操作（若实现）

> 详见：进程与执行视图：[process-view.puml](./views/process-view.puml) | 补充说明文档：[process-view.md](./views/process-view.md)

---

## 7. 并发与隔离策略
| 策略 | 说明 |
|------|------|
| 租户锁 (TenantConflictManager) | 同一租户在任意时刻仅一个 RUNNING/PAUSED 任务 |
| 全局并发 (maxConcurrency) | Plan 级并发额度控制任务提交速率 |
| 协作式暂停 | 仅在完成当前 Stage 后检查暂停标志 |
| 心跳与进度 | HeartbeatScheduler 每 10s 上报 TaskProgressEvent |

---

## 8. Checkpoint 与恢复机制
| 要素 | 描述 |
|------|------|
| 存储 | RedisCheckpointRepository（JSON 序列化 + TTL 可配置），InMemoryCheckpointRepository（测试/回退）|
| 内容 | lastCompletedStageIndex、completedStages、contextData、savedAt |
| 保存时机 | Stage 成功、失败、暂停、异常中断 |
| 恢复策略 | 从 (lastCompletedStageIndex + 1) 开始执行；补偿一次进度事件 |
| 回滚交互 | 回滚不依赖 Checkpoint（按已完成列表逆序）|

---

## 9. 事件模型（Event Model）
| 事件类别 | 示例 | 触发点 |
|----------|------|--------|
| Plan 状态 | PlanStartedEvent / PlanCompletedEvent | 聚合状态变更 |
| Task 状态 | TaskStartedEvent / TaskPausedEvent / TaskCompletedEvent | 聚合方法调用 |
| Task Stage 状态 | TaskStageStartedEvent / TaskStageCompletedEvent / TaskStageFailedEvent | Stage 执行前后 |
| 进度/心跳 | TaskProgressEvent | 心跳调度器周期触发 |
| 重试/回滚 | TaskRetryStartedEvent / TaskRolledBackEvent | 操作入口调用 |

> 发布流程详解：将在执行机设计文档中说明（待补充：[execution-engine.md](./design/execution-engine.md)）

### 9.1 事件监听与消费（Plan）
- 自监听机制：DomainEventPublisher(Spring) → @EventListener 监听器（进程内自消费）
- 监听器与职责：
  - PlanStartedListener：接收 PlanStartedEvent → 委托 PlanExecutionFacade.executePlan(planId)
  - PlanResumedListener：接收 PlanResumedEvent → PlanExecutionFacade.resumePlanExecution(planId)
  - PlanPausedListener：接收 PlanPausedEvent → 记录审计/通知；暂停在 TaskExecutor Stage 边界协作式生效
  - PlanCompletionListener：接收 PlanCompleted/Failed → 调用 PlanSchedulingStrategy.onPlanCompleted 清理冲突标记
- 与编排/执行的桥接：监听器仅做“事件到应用门面/策略”的委托，业务逻辑仍在 Facade/Orchestrator/Executor 中实现

---

## 10. 非功能特性概览
| 维度 | 当前策略 |
|------|----------|
| 可扩展性 | 新增 StageStep 实现 + 配置驱动 StageFactory 组合 |
| 可观察性 | 领域事件 + 心跳进度；可挂载指标（MetricsRegistry）|
| 异常处理 | FailureInfo 分类（ErrorType：VALIDATION/EXECUTION/TIMEOUT/ROLLBACK/INFRASTRUCTURE）|
| 幂等 | 事件序列（sequenceId 逻辑在事件总线层，可扩展）|
| 性能 | 内存优先读写；Redis 仅用于持久化断点与广播 |
| 安全 | 依赖宿主系统的认证/鉴权（模块内部不处理）|

---

## 11. 技术栈（修正）
| 类别 | 使用 | 说明 |
|------|------|------|
| 语言 / 运行时 | Java 17 | 主开发语言 |
| 框架 | Spring Boot 3.2.x | 应用框架与容器 |
| 持久化 | Redis + InMemory | Checkpoint 与运行态存储；无 RDBMS/JPA |
| 序列化 | Jackson | JSON/YAML/日期类型支持 |
| 验证 | Jakarta Validation + Hibernate Validator | 参数与模型校验 |
| 依赖发现（可选） | Nacos Client | 服务发现（可降级固定 IP）|
| 指标（可选） | Micrometer | 自定义指标挂载 |
| 测试 | JUnit5 / Awaitility / Testcontainers / JMH | 单测、异步、集成、性能 |

---

## 12. 不适用的视图说明
**物理视图 (Physical View)**：本模块不直接定义物理部署拓扑。部署架构（节点、网络、HA、存储规划）由集成该模块的上层系统决定。参见说明文档：[physical-view-not-applicable.md](./views/physical-view-not-applicable.md)。

---

## 13. 文档索引（更新）
| 分类 | 文件 | 描述 |
|------|------|------|
| 逻辑视图 | [logical-view.puml](./views/logical-view.puml) / [logical-view.md](./views/logical-view.md) | 聚合、值对象、事件、仓储、领域服务 |
| 进程视图 | [process-view.puml](./views/process-view.puml) / [process-view.md](./views/process-view.md) | 状态机、执行/重试/暂停时序、Stage 内部 |
| 开发视图 | [development-view.puml](./views/development-view.puml) / [development-view.md](./views/development-view.md) | 分层架构、包结构、接口实现、依赖倒置 |
| 桥接视图 | [plan-to-execution-bridge.puml](./views/plan-to-execution-bridge.puml) | Plan 事件 → 应用层桥接 → 编排/执行 |
| 场景视图 | [scenarios.puml](./views/scenarios.puml) / [scenarios.md](./views/scenarios.md) | 用例图、关系、流程（部署/重试/暂停/回滚）|
| 不适用视图 | [physical-view-not-applicable.md](./views/physical-view-not-applicable.md) | 说明物理视图不在模块范围 |
| 技术栈 | [tech-stack.md](./tech-stack.md) | 已验证技术栈清单 |
| 术语表 | [glossary.md](./glossary.md) | 领域与技术术语定义 |
| 工作流 | [documentation-workflow.md](./workflow/documentation-workflow.md) | 文档更新流程 |
| 开发规范 | [development-workflow.md](./workflow/development-workflow.md) | Git / 测试 / 提交规范 |
| 执行机设计 | [execution-engine.md](./design/execution-engine.md) | 核心执行机与扩展点 |
| 领域模型设计 | [domain-model.md](./design/domain-model.md) | 聚合内部结构与不变式 |
| 持久化设计 | [persistence.md](./design/persistence.md) | InMemory 与 Redis 策略 |
| Checkpoint 机制 | [checkpoint-mechanism.md](./design/checkpoint-mechanism.md) | 序列化与 TTL |
| 状态管理设计 | [state-management.md](./design/state-management.md) | 状态转换矩阵 |
| 防腐层工厂 | [anti-corruption-layer.md](./design/anti-corruption-layer.md) | ServiceConfigFactory 家族设计 |
| 冲突协调 | [conflict-coordination.md](./design/conflict-coordination.md) | 租户互斥协调与演进 |
| 调度策略 | [scheduling-strategy.md](./design/scheduling-strategy.md) | PlanSchedulingStrategy 对比 |
| 门面层 | [facade-layer.md](./design/facade-layer.md) | 对外与应用内门面职责边界 |

---
## 14. 后续设计待补充
| 设计文档 | 说明 | 状态 |
|----------|------|------|
| [design/execution-engine.md](./design/execution-engine.md) | 执行机核心结构与扩展点 | ✅ 已完成 (T-003) |
| [design/domain-model.md](./design/domain-model.md) | 聚合内部结构与不变式 | ✅ 已完成 (T-004) |
| [design/persistence.md](./design/persistence.md) | InMemory 与 Redis 仓储策略 | ✅ 已完成 (T-005) |
| [design/checkpoint-mechanism.md](./design/checkpoint-mechanism.md) | Checkpoint 序列化与 TTL 策略 | ✅ 已完成 (T-005 派生) |
| [design/state-management.md](./design/state-management.md) | 状态机转换矩阵 + 失败路径 | ✅ 已完成 (T-007) |
| [design/anti-corruption-layer.md](./design/anti-corruption-layer.md) | 防腐层工厂设计 | ✅ 已完成 (T-010) |
| [design/conflict-coordination.md](./design/conflict-coordination.md) | 冲突协调设计 | ✅ 已完成 (T-011) |
| [design/scheduling-strategy.md](./design/scheduling-strategy.md) | 计划调度策略 | ✅ 已完成 (T-012) |
| [design/facade-layer.md](./design/facade-layer.md) | 门面层设计 | ✅ 已完成 (T-013) |
| [prompts/onboarding-prompt.md](./prompts/onboarding-prompt.md) | AI/新人入门指引 | ✅ 已完成 (T-006) |
| [prompts/architecture-prompt.md](./prompts/architecture-prompt.md) | 架构深度提示词 | ✅ 已完成 (T-006) |

---

## 15. 风险与待改进项
| 风险 | 描述 | 计划 |
|------|------|------|
| 事件发布耦合 | 事件发布集中在应用服务，缺少异步落地保障 | 引入事件总线适配层，实现重试/缓冲 |
| Checkpoint 竞争 | 并发保存存在覆盖风险 | 引入版本号或乐观锁（未来）|
| 心跳调度资源占用 | 大量并发任务时调度器线程增加 | 合并心跳调度器，批量上报 |
| 多实例租户锁 | TenantConflictManager 为本地内存 | 引入分布式锁（Redis / Redisson）|

---

## 16. 总结
本架构以“精确、内聚、可恢复”为核心：通过独立聚合加值对象确保领域语义清晰；通过协作式暂停与 Checkpoint 提供健壮的运行时控制；通过事件与心跳机制提升可观测性。后续将围绕执行机扩展点、分布式一致性与事件可靠投递继续演进。

> 下一任务：T-002 完成后将进入执行机详细设计（T-003）。
