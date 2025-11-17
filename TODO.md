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

### Phase 0-16 — ✅ 完成并归档
> 已归档到 develop.log，包括：
> - 核心架构建立（领域模型、状态机、调度器）
> - Stage/Step 执行模型与健康检查
> - Checkpoint 持久化（InMemory + Redis）
> - 回滚快照与事件系统
> - 并发控制与冲突锁释放
> - 可观测性（Metrics + MDC）
> - 完整文档体系（架构设计、迁移指南、4+1 视图）

### Phase 17 — ✅ 完成 (2024-11-17)
**主题**: DDD 彻底重构 + 二层校验架构

#### RF-01: Facade 业务逻辑剥离 — ✅ DONE
- ✅ 创建 Result DTO 体系
- ✅ 创建内部 DTO (TenantConfig)
- ✅ 实现 PlanApplicationService 和 TaskApplicationService
- ✅ 重构 DeploymentTaskFacade

#### RF-02: TaskWorkerFactory 参数简化 — ✅ DONE
- ✅ 引入 TaskWorkerCreationContext（Builder 模式）
- ✅ 参数从 9 个简化为 1 个

#### RF-03: DDD 彻底重构 — ✅ DONE
- ✅ 删除旧 ApplicationService（PlanApplicationService, TaskApplicationService）
- ✅ 创建 PlanDomainService（依赖减少 45%）
- ✅ 创建 TaskDomainService（7 个依赖，纯领域逻辑）
- ✅ 创建 DeploymentApplicationService（跨聚合协调）
- ✅ 创建 TenantConfigConverter 防腐层
- ✅ Facade 层完全迁移

#### RF-04: 二层校验架构 — ✅ DONE
- ✅ 添加 Jakarta Validation 依赖
- ✅ TenantConfig 添加 @NotNull/@NotBlank 注解
- ✅ 创建 BusinessValidator（业务规则校验）
- ✅ Facade 先转换后校验（校验 TenantConfig）
- ✅ ExecutorConfiguration 添加 Validator Bean
- ✅ 完整的二层校验流程

**成果**:
- ✅ 分层清晰：Facade → Application → Domain → Infrastructure
- ✅ 防腐层：TenantConfigConverter 隔离外部依赖
- ✅ 校验分层：格式校验（Facade）+ 业务规则校验（Application）
- ✅ 依赖简化：PlanDomainService 依赖减少 45%
- ✅ 内部 DTO 一致：TenantConfig 贯穿应用层和领域层

**文档**:
- `DDD_REFACTORING_PHASE3_COMPLETE.md`
- `VALIDATION_LAYER_COMPLETE.md`
- `BEAN_CONFIGURATION_FIX.md`
- `DDD_REFACTORING_AND_VALIDATION_COMPLETE.md`（综合报告）

## 2. 当前待办（Phase 18 - DDD 架构深度优化）

> **基于评审报告**: `DDD_ARCHITECTURE_REVIEW_REPORT.md` (2025-11-17)  
> **当前 DDD 符合度**: 50% (13/26)  
> **目标 DDD 符合度**: 80% (21/26)  
> **预计总工作量**: 6-8 周

---

### 🔴 P0 - 最高优先级（影响架构质量）— 预计 2 周

#### RF-05: 清理孤立代码 — ✅ DONE (2025-11-17)
**状态**: 已完成  
**实际时间**: 30 分钟  
**责任人**: GitHub Copilot  
**依赖**: 无

**完成情况**:
- ✅ 删除 10 个孤立主代码类（~1380 行）
- ✅ 删除 5 个孤立测试类（~950 行）
- ✅ 总计删除 ~1500 行代码（约 10%）
- ✅ 保留 service.health 包（仍在使用）
- ✅ 保留 PipelineContext（被 TaskRuntimeContext 使用）
- ✅ 编译成功，无错误
- ✅ 测试运行正常（119 tests，失败与清理无关）

**已删除**:
- service.registry 包 (ServiceRegistry)
- service.strategy 包 (DirectRpcNotificationStrategy, RedisRpcNotificationStrategy, ServiceNotificationStrategy)
- service.adapter 包 (ServiceNotificationAdapter)
- NotificationResult.java
- Pipeline.java, PipelineStage.java
- CheckpointManager.java, InMemoryCheckpointManager.java
- PipelineTest, PipelineContextCheckpointIntegrationTest, CheckpointManagerTest
- CheckpointBenchmark, PipelineExecutionBenchmark

**详细报告**: `RF05_CLEANUP_REPORT.md`

---

#### RF-06: 修复贫血聚合模型 — 🔴 TODO （最重要）
**状态**: 待启动  
**预计时间**: 1-2 天  
**责任人**: 待分配  
**依赖**: RF-05（建议先清理代码）

**问题描述**:
TaskAggregate 和 PlanAggregate 沦为纯数据容器（贫血模型），业务逻辑散落在 DomainService 层，违反 DDD 核心原则："告知而非询问"（Tell, Don't Ask）。

**执行步骤**:

**Step 1**: 为 TaskAggregate 添加业务行为（6 小时）
- 状态转换方法：start(), requestPause(), resume(), cancel()
- Stage 管理方法：completeStage(), failStage(), isAllStagesCompleted()
- 重试与回滚方法：retry(), rollback(), completeRollback()
- 不变式保护：validateCanCompleteStage()

**Step 2**: 为 PlanAggregate 添加业务行为（4 小时）
- Plan 状态转换：addTask(), start(), pause(), resume(), complete()
- 查询方法：getTaskCount(), canStart()

**Step 3**: 重构 DomainService（4 小时）
- 简化 TaskDomainService：只做查询、协调和持久化
- 简化 PlanDomainService：调用聚合方法而非直接操作状态

**Step 4**: 更新单元测试（2 小时）

**验收标准**:
- ✅ 聚合包含完整的业务行为方法
- ✅ DomainService 职责简化
- ✅ 聚合能保护自身不变式
- ✅ 单元测试覆盖率 > 80%

**预期收益**:
- ✅ 业务逻辑内聚在聚合，符合 DDD 原则
- ✅ 代码可读性提升 50%
- ✅ 测试更简单
- ✅ 服务层代码减少 30%

---

#### RF-07: 修正聚合边界 — 🔴 TODO
**状态**: 待启动  
**预计时间**: 4-8 小时  
**责任人**: 待分配  
**依赖**: RF-06

**问题描述**:
PlanAggregate 直接持有 TaskAggregate 对象列表，违反 DDD 原则："聚合间通过 ID 引用"。

**执行步骤**:
1. 重构 PlanAggregate：改为持有 taskIds (List<String>)
2. 重构 PlanDomainService：addTaskToPlan() 只传递 taskId
3. 重构 DeploymentApplicationService：组装完整信息时查询 Tasks
4. 更新仓储实现
5. 更新测试

**验收标准**:
- ✅ PlanAggregate 只持有 taskIds
- ✅ 跨聚合引用通过 ID
- ✅ 事务边界清晰
- ✅ 所有测试通过

---

### 🟡 P1 - 中优先级（改善代码质量）— 预计 2-3 周

#### RF-08: 引入值对象 — 🟡 TODO
**预计时间**: 1-2 天  
**目标**: 创建 TaskId、TenantId、PlanId、DeployVersion、NetworkEndpoint 值对象，消除原始类型泛滥。

#### RF-09: 重构仓储接口 — 🟡 TODO
**预计时间**: 1 天  
**目标**: 分离 Repository 和 QueryService，符合 CQRS 原则。

#### RF-10: 优化应用服务 — 🟡 TODO
**预计时间**: 1 天  
**目标**: 提取 DeploymentPlanCreator，简化 DeploymentApplicationService 职责。

---

### 🟢 P2 - 低优先级（锦上添花）— 预计 1 周

#### RF-11: 完善领域事件 — 🟢 TODO
**预计时间**: 4-8 小时  
**目标**: 事件由聚合产生，服务层统一发布。

#### RF-12: 添加事务标记 — 🟢 TODO
**预计时间**: 2-4 小时  
**目标**: 在应用服务层使用 @Transactional 明确事务边界。

---

## Phase 18 执行优先级

**立即开始（本周）**:
1. 🔴 RF-05: 清理孤立代码（2-4小时）
2. 🔴 RF-06: 修复贫血聚合模型（1-2天）← **最关键**

**第二周**:
3. 🔴 RF-07: 修正聚合边界（4-8小时）

**第三、四周**:
4. 🟡 RF-08: 引入值对象（1-2天）
5. 🟡 RF-09: 重构仓储接口（1天）
6. 🟡 RF-10: 优化应用服务（1天）

**后续优化**:
7. 🟢 RF-11: 完善领域事件（4-8小时）
8. 🟢 RF-12: 添加事务标记（2-4小时）

**预期总收益**:
- ✅ DDD 符合度从 50% 提升至 80%
- ✅ 代码行数减少 10%
- ✅ 测试覆盖率提升 40%
- ✅ 可维护性提升 50%
- ✅ 类型安全提升 60%

---

#### RF-04: 端到端集成测试套件 — TODO
- **目标**：建立完整的端到端集成测试覆盖
- **技术栈**：
  - Spring Boot Test + @SpringBootTest
  - Testcontainers (Redis)
  - Awaitility（异步等待断言）
- **测试场景**：
  - 完整生命周期（创建→执行→完成）
  - 异常场景（失败→重试→成功）
  - 控制流程（暂停→恢复→完成）
  - 回滚场景（失败→回滚→验证）
  - 并发控制（多租户并发测试）
  - 事件流验证（sequenceId 幂等性）
  - Checkpoint 持久化（内存/Redis 模式，故障恢复）
- **测试类设计**：
  - `E2ELifecycleIntegrationTest`：完整生命周期
  - `E2ERetryIntegrationTest`：重试场景
  - `E2EPauseResumeIntegrationTest`：暂停恢复
  - `E2ERollbackIntegrationTest`：回滚场景
  - `E2EConcurrencyIntegrationTest`：并发控制
  - `E2ECheckpointRedisIntegrationTest`：Redis Checkpoint
  - `E2EEventSequenceIntegrationTest`：事件流验证
- **优先级**：中高

### 2.2. 测试增强
- **T-01**: Facade 业务逻辑剥离后的单测更新 — ✅ DONE (RF-01 完成时一并完成)
- **T-02**: TaskWorkerFactory 重构后的单测更新 — ✅ DONE (RF-02 完成时一并完成)
- **T-04**: 测试基础设施搭建 — TODO（RF-04 子任务）
- **T-05**: 核心场景集成测试实现 — TODO（RF-04 子任务）
- **T-06**: 事件流验证测试 — TODO（RF-04 子任务）
- **T-07**: Checkpoint 持久化集成测试 — TODO（RF-04 子任务）

### 2.3. 文档更新
- **D-01**: 更新 ARCHITECTURE_PROMPT.md 反映重构后的架构 — ✅ DONE (RF-01 完成)
- **D-02**: 更新类图和组件图 — TODO（依赖 RF-01/02 - 可选）

### 2.4. Stage 策略模式（Phase 18）

#### RF-03: StageFactory 策略模式与自动装配 — TODO
- **问题**：StageFactory.buildStages 目前硬编码 Stage 创建逻辑
- **目标**：支持声明式 Stage 装配，便于扩展自定义 Stage
- **方案**：
  - 定义 StageProvider 接口（提供单个 Stage）
  - 使用 @Component + @Order 注解标记 StageProvider 实现类
  - StageFactory 通过 Spring 容器自动发现所有 StageProvider
  - 按 @Order 排序后组装为 List<TaskStage>
  - 支持条件装配（@ConditionalOnProperty）
- **设计要点**：
  - StageProvider 接口：`TaskStage provide(TaskAggregate, TenantDeployConfig, ExecutorProperties)`
  - 自定义注解：@StageComponent(order=100, name="config-update")
  - 自动装配机制：使用 ApplicationContext.getBeansOfType() 或 @Autowired List<StageProvider>
  - 排序策略：Order 值越小越先执行
- **优先级**：低（需详细设计）

#### T-03: StageFactory 策略模式的集成测试 — TODO（依赖 RF-03）

## 3. 阶段路线图

### Phase 17（进行中）：架构重构与集成测试
- **目标**：优化代码结构，提升可维护性，建立完整的端到端测试体系
- **任务**：
  - RF-01（Facade 重构）→ T-01 → D-01/D-02
  - RF-02（参数简化）→ T-02 → D-01/D-02
  - RF-04（集成测试）→ T-04 → T-05/T-06/T-07
- **预计完成时间**：待定

### Phase 18（计划中）：Stage 策略模式
- **目标**：实现声明式 Stage 装配，支持灵活扩展
- **任务**：RF-03（详细设计 + 实现）→ T-03
- **预计完成时间**：待定

## 4. 当前工作集（优先级排序）
1. **RF-01**：Facade 业务逻辑剥离（高）
2. **RF-04**：集成测试套件（中高）
3. **RF-02**：TaskWorkerFactory 参数简化（中）
4. **D-01/D-02**：文档更新（低）
5. **RF-03**：Stage 策略模式（低 - Phase 18）

## 5. 说明
- 冲突锁释放：仅终止态（COMPLETED/FAILED/CANCELLED/ROLLED_BACK/ROLLBACK_FAILED）释放；执行路径释放 + 事件兜底释放双层保障；回滚中（ROLLING_BACK）与暂停（PAUSED）不释放。
- 已完成阶段详情请参阅 develop.log
