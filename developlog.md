# 开发日志

> **格式说明**：日期为二级标题 (## YYYY-MM-DD)，任务标签为三级标题 (### [任务ID或分类])，同任务下使用无序列表记录事件。最新日期块置于最上方。
> **记录范围**：只保留已有信息的结构调整，不删减原始内容。

---

## 2025-11-24

### [文档更新 M-01 ~ M-08]
- **完成 Minor 文档更新任务**：实施全部 8 个文档更新建议
- **M-01 & M-02**: 更新 `architecture-overview.md` §4 应用服务列表
  - 补充 T-016 投影更新器（TaskStateProjectionUpdater、PlanStateProjectionUpdater）
  - 补充查询服务（TaskQueryService）
  - 添加 T-016 新增组件清单表格
- **M-03**: 补充 `architecture-overview.md` §9.2 事件监听器章节
  - 详细说明投影更新机制（CQRS + Event Sourcing）
  - 说明一致性模型（命令侧 vs 查询侧）
  - 说明故障降级机制
  - 明确设计理念（仅兜底使用）
- **M-04**: 更新 `architecture-overview.md` §8 Checkpoint 机制
  - 拆分为 3 个子章节：Checkpoint、投影持久化、租户锁、查询 API
  - 详细说明 T-016 扩展的持久化能力
  - 补充 TTL 策略和使用场景
- **M-05**: 补充 `README.md` 查询 API 使用约束
  - 添加设计理念章节（CQRS + Event Sourcing）
  - 说明技术实现（投影更新器、查询服务、AutoConfiguration）
  - 添加相关文档链接
- **M-06**: 更新 `state-management.md` 状态转换矩阵
  - 更新 §3 Plan 状态转换矩阵，标注已移除的 4 个状态
  - 更新 §4 Task 状态转换矩阵，标注已移除的 3 个状态
  - 更新 §5 失败与恢复路径，移除 VALIDATION_FAILED，补充校验失败处理说明
  - 添加设计理念和移除理由说明
- **M-07**: 补充 `persistence.md` §6 Redis Key 规范
  - 扩展为 6 个子章节：Key 设计总览、数据结构详解、命名空间配置、索引设计、TTL 策略、监控告警
  - 补充完整的数据结构示例（JSON、Hash、String）
  - 添加多环境隔离配置示例
  - 补充索引设计和未来扩展方向
  - 添加监控指标和告警规则
- **M-08**: 添加 `architecture-overview.md` §10 AutoConfiguration 使用指南
  - 详细说明 ExecutorPersistenceAutoConfiguration 装配逻辑
  - 提供开发/生产/测试环境配置示例
  - 完整列出所有配置属性
  - 说明条件装配逻辑和优先级
  - 说明故障降级流程和监控建议
  - 提供自定义配置示例
- **影响文档**（3 个）：
  - `docs/architecture-overview.md`（M-01, M-02, M-03, M-04, M-08）
  - `docs/design/state-management.md`（M-06）
  - `docs/design/persistence.md`（M-07）
  - `README.md`（M-05）
- **文档质量提升**：
  - 补充 T-016 最新实现细节
  - 明确设计理念和使用约束
  - 提供完整配置示例和监控建议
  - 标注已移除状态，避免误导

### [架构对照与命名澄清]
- **完成架构设计与实现对照检查**：生成完整对照分析报告（architecture-implementation-comparison-report.md）
  - 一致性评分：85%（核心架构 95%，文档同步 75%）
  - 核心发现：DDD 战术模式完整，T-016 持久化方案完整落地，文档存在滞后
- **完成状态枚举精简（I-01, I-02）**：
  - 移除 PlanStatus 4个未使用状态、TaskStatus 3个未使用状态
  - 更新 PlantUML 图和代码注释
  - 详见状态枚举分析报告（status-enum-analysis-report.md）
- **完成命名一致性澄清（I-03）**：
  - **架构澄清**：实际代码有两个不同职责的类
    - `TenantConflictManager` (Infrastructure层)：底层锁管理（内存/Redis）
    - `TenantConflictCoordinator` (Application层)：应用层冲突协调
  - **文档更新**（4个文件）：
    - `execution-engine.md` §2：更新架构角色表，添加两层架构说明
    - `architecture-overview.md` §7：更新并发策略表，详细描述两层架构
    - `architecture-prompt.md`：更新关键文件索引和诊断模板
    - `onboarding-prompt.md`：更新核心概念、误区说明、代码入口
  - **结论**：不是命名不一致，而是两个不同职责的类组成的两层架构
- **对照报告更新**：
  - I-01, I-02, I-03 全部标记为已解决 ✅
  - Important 差异：0 个（全部已解决）
  - Minor 差异：8 个（文档更新建议，非阻塞）

### [T-016]
- **任务完成**：崩溃恢复能力增强 - 状态持久化设计与实施（4个Phase全部完成）
- **Phase 1**：租户锁迁移到 Redis 分布式锁（RedisTenantLockManager）
  - 实现 Redis SET NX 原子操作，TTL 2.5小时
  - 支持 tryAcquire / release / renew / exists
  - InMemory fallback 用于测试和单实例场景
- **Phase 2**：状态投影持久化（CQRS + Event Sourcing）
  - 实现 RedisTaskStateProjectionStore / RedisPlanStateProjectionStore
  - 实现 RedisTenantTaskIndexStore（TenantId → TaskId 索引）
  - 事件监听器：TaskStateProjectionUpdater / PlanStateProjectionUpdater
  - AutoConfiguration：ExecutorPersistenceAutoConfiguration（条件装配，故障降级）
  - Redis Key 设计：executor:task:{id}, executor:plan:{id}, executor:index:tenant:{id}, executor:lock:tenant:{id}
- **Phase 3**：查询 API（最小兜底）
  - 新增 TaskQueryService：queryByTenantId / queryPlanStatus / hasCheckpoint
  - 新增 PlanStatusInfo DTO 封装 Plan 投影
  - DeploymentTaskFacade 暴露查询方法
  - 明确"仅兜底使用"原则，不建议常规调用
  - 移除冗余批量查询方法，保持最小化设计
- **Phase 4**：测试验证
  - 单元测试：TaskQueryServiceTest（10个用例）
  - DTO测试：PlanStatusInfoTest（4个用例���
  - 集成测试：Phase4QueryApiIntegrationTest（7个用例）
  - 总计 21个测试用例，覆盖所有核心场景
- **文档更新**：
  - 更新 persistence.md：添加投影存储、租户锁、查询API章节
  - 更新 architecture-overview.md：标记 T-016 完成，更新 Checkpoint 机制、风险表
  - 更新 README.md：新增"查询API（仅兜底使用）"完整章节
  - 创建完整报告：task-016-final-implementation-report.md（含架构说明、使用指南、测试覆盖）
  - 创建阶段报告：phase2/phase3/phase4-completion-report.md
  - 创建快速总结：task-016-completion-summary.md
- **影响模块**：
  - Infrastructure：persistence（projection stores、lock manager、redis client）
  - Application：query（TaskQueryService）、projection（updaters）
  - AutoConfiguration：ExecutorPersistenceAutoConfiguration、ExecutorPersistenceProperties
  - Facade：DeploymentTaskFacade（新增查询方法）
  - Config：ExecutorConfiguration（移除硬编码 Bean）
  - Resources：application.yml（新增 executor.persistence 配置段）
- **关键决策**：
  - 采用 CQRS + Event Sourcing 架构，而非 Repository 双写（更低侵入性，更好扩展性）
  - 查询API保持最小化，避免演变为查询平台（3个核心方法）
  - 事件驱动投影更新，最终一致性（可接受毫秒级延迟）
  - Redis 不可用时自动降级为 InMemory（重启后状态丢失）
- 从 TODO 移除 T-016（已完成）
- 归档临时设计文档（保留在 docs/temp/ 供参考）

---

## 2025-11-23
### [文档复核]
- 完成文档与代码一致性复核：从 README 出发检查所有可达文档
- 发现 6 个文档不一致或缺失问题：
  - P1: 应用服务类命名错误（文档写 xxApplicationService，代码实际为 xxService）
  - P2: ServiceConfigFactory 防腐层工厂设计未记录
  - P2: ConflictRegistry 与 TenantConflictCoordinator 协作未说明
  - P2: PlanSchedulingStrategy 调度策略模式未记录
  - P2: 多 Facade 设计（PlanExecutionFacade）未说明
  - P3: Plan 相关事件监听器未在事件驱动章节体现
- 创建待办任务 T-009 至 T-014 跟踪修复

### [T-008]
- 完成架构提示词增强：architecture-prompt.md 新增深度分析场景（性能瓶颈/失败链路/状态漂移）、分模块诊断模板（执行机/领域模型/持久化/状态管理）、影响评估工作流（修改评估/新增功能）、多粒度查询示例、综合应用示例
- 启动架构提示词增强任务：创建临时方案 task-008-architecture-prompt-enhancement.md

### [T-007]
- 完成状态管理设计文档：state-management.md（状态集合对比、Plan/Task 转换矩阵、失败与恢复路径、协作式暂停、重试/回滚交互边界）
- 完成状态管理 UML 图：state-management.puml（总览 + 4个子图：Plan细节、Task细节、失败恢复、暂停重试回滚交互）
- 合入总纲索引：architecture-overview.md 第14节标记已完成设计文档
- 清理临时方案文档：task-003/task-007-xxx-design.md
- 启动状态管理设计任务：创建临时方案 task-007-state-management-design.md，生成 UML state-management.puml，撰写设计初稿 state-management.md

### [T-006]
- 重写 Onboarding Prompt（onboarding-prompt.md）去除过时技术栈、补充不变式/差异/调试模板

### [T-004]
- 初稿领域模型详细设计文档完成，新增 domain-model.md（聚合/状态机/不变式/事件/值对象）

### [T-003]
- 微调：补充错误分支总览时序图、事件触发点速查表、DoD 增强；更新 execution-engine-internal.puml / execution-engine.md
- 创建执行机内部 UML 视图（类图 + 正常/重试/暂停/回滚时序）；新增 execution-engine-internal.puml
- 初版执行机详细设计文档完成（execution-engine.md），涵盖核心数据结构/执行路径/扩展点/风险
- 启动执行机详细设计任务，创建临时设计文档；新增 task-003-execution-engine-design.md

### [T-002]
- 架构总纲重写完成，更新 architecture-overview.md（移除错误技术栈与物理视图引用，加入原则与索引）

### [T-001]
- 修正场景视图：添加 usecase 图（用例图、用例关系图）；更新 scenarios.puml
- 删除不适用的物理视图，创建说明文档；删除 physical-view.puml，创建 physical-view-not-applicable.md
- 删除旧的 process-view 单独文件（plan-state, task-state, plan-execution, task-retry）
- 完成场景视图绘制：1个概览+4个子视图（完整部署、失败重试、暂停恢复、回滚）；创建 scenarios.puml
- 完成物理视图绘制：1个概览+5个子视图（应用实例、Redis存储、网络拓扑、部署模式、资源规划）；创建 physical-view.puml [后删除]
- 完成开发视图绘制并修正语法错误：1个概览+6个子视图（Facade层、Application层、Domain层、Infrastructure层、依赖关系、包结构树）；创建 development-view.puml
- 完成进程视图绘制：1个概览+6个子视图（Plan状态机、Task状态机、执行时序、重试流程、协作式暂停、Stage执行）；创建 process-view.puml
- 完成逻辑视图拆分：1个概览+5个子视图（Plan聚合、Task聚合、领域事件、校验值对象、共享值对象）；重新生成 logical-view.puml
- 修正逻辑视图：补充 TaskStageStatusEvent 父类、完善值对象和依赖关系
- 开始逻辑视图重绘（基于真实代码）
- 在 TODO 中创建文档重组任务跟踪（T-001 至 T-006）

## 2025-11-22
### [文档重组]
- 完成步骤 1-4：创建文档状态清单、技术栈清单、术语表、开发工作流规范；更新 documentation-status.md, tech-stack.md, glossary.md, development-workflow.md
- 初始化新文档结构；创建 TODO.md、developlog.md、docs/ 框架

---

## 2025-11-23
### [T-014]
- 文档补齐：在 architecture-overview.md 增加“9.1 事件监听与消费（Plan）”，在 execution-engine.md 增加“18. 事件消费端（Plan）”，清晰说明 PlanStarted/Resumed/Paused/Completion 四类监听器及与编排/策略的桥接
- 从 TODO 移除 T-014（已完成）

### [T-009]
- 修正文档类命名：architecture-overview.md、onboarding-prompt.md 中的 PlanLifecycleApplicationService/TaskOperationApplicationService 更正为 PlanLifecycleService/TaskOperationService

### [T-010]
- 新增防腐层工厂设计文档：anti-corruption-layer.md（ServiceConfigFactory 家族与 Composite 设计、边界与使用）

### [T-011]
- 新增冲突协调设计文档：conflict-coordination.md（ConflictRegistry/TenantConflictManager/TenantConflictCoordinator/Orchestrator 时机）

### [T-012]
- 新增调度策略设计文档：scheduling-strategy.md（FineGrained vs CoarseGrained，对 Orchestrator 集成点说明）

### [T-013]
- 新增门面层设计文档：facade-layer.md（DeploymentTaskFacade vs PlanExecutionFacade 边界与时序）

### [T-015]
- 完成 executorCreator 补完：TaskOperationService 注入 TaskWorkerFactory，内部创建 TaskExecutor
- rollbackTaskByTenant 和 retryTaskByTenant 改为异步执行（CompletableFuture），通过领域事件通知结果
- 移除 DeploymentTaskFacade 中的 null 参数传递
- 更新 ExecutorConfiguration 注入 TaskWorkerFactory
- 设计检查：调用链路干净，无泄露，符合分层原则（Facade 不依赖 Infrastructure）

### [里程碑]
- 完成技术文档清理：删除 `docs/backup` 与 `docs/temp` 目录（所有过程性文档已迁移或合并至正式文档与视图中）
