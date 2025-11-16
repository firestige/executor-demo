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
- **Phase 14**：可观测性与测试完善 — 已完成
> 截至当前 Phase 16 全部完成。所有状态机、事件、回滚、Checkpoint、健康检查、并发调度、测试覆盖、可观测性、文档均已完成。
  - C-03（执行后 MDC 清理）
- **Phase 15**：文档与治理 — 已完成
  - **问题**：DeploymentTaskFacadeImpl.createSwitchTask 方法包含大量业务逻辑
### 2.1. 架构重构（Phase 17）
- **RF-01** Facade 业务逻辑剥离 — TODO
  - **问题**：DeploymentTaskFacadeImpl.createSwitchTask 方法包含大量业务逻辑
  - **目标**：Facade 作为防腐层，仅负责数据结构转换和协调调用
  - **方案**：
    - 提取业务逻辑到专门的应用服务层（Application Service）
    - 考虑引入 PlanApplicationService / TaskApplicationService
    - Facade 仅负责：DTO 转换 + 调用应用服务 + 结果封装
  - **优先级**：高
    - Facade 仅负责：DTO 转换 + 调用应用服务 + 结果封装
- **RF-02** TaskWorkerFactory 参数简化 — TODO
  - **问题**：DefaultTaskWorkerFactory.create() 方法参数过多（8+ 个参数）
  - **目标**：提升可读性和可维护性
  - **方案**：
    - 引入 TaskWorkerCreationContext 参数对象（Builder 模式）
    - 或使用 TaskWorkerConfig 配置对象封装相关参数
    - 保持扩展性：便于后续增加新参数
  - **优先级**：中
    - 引入 TaskWorkerCreationContext 参数对象（Builder 模式）
- **RF-03** StageFactory 策略模式与自动装配 — TODO
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
    - 自定义注解 @StageComponent(order=100, name="config-update")
    - 自动装配机制：使用 ApplicationContext.getBeansOfType() 或 @Autowired List<StageProvider>
    - 排序策略：Order 值越小越先执行
  - **优先级**：低（需详细设计）
- **RF-03** StageFactory 策略模式与自动装配 — TODO
### 2.2. 测试增强
- **T-01** Facade 业务逻辑剥离后的单测更新 — TODO（依赖 RF-01）
- **T-02** TaskWorkerFactory 重构后的单测更新 — TODO（依赖 RF-02）
- **T-03** StageFactory 策略模式的集成测试 — TODO（依赖 RF-03）
    - 按 @Order 排序后组装为 List<TaskStage>
### 2.3. 文档更新
- **D-01** 更新 ARCHITECTURE_PROMPT.md 反映重构后的架构 — TODO（依赖 RF-01/02/03）
- **D-02** 更新类图和组件图 — TODO（依赖 RF-01/02）

### Phase 17（计划中）：架构重构与集成测试

### Phase 17（计划中）：架构重构与优化
- 目标：优化 Facade 层、工厂参数、Stage 装配机制
- 任务：RF-01（Facade 重构）、RF-02（参数简化）
- 预计完成时间：待定
  - RF-01（Facade 重构）
### Phase 18（计划中）：Stage 策略模式（低优先级）
- 目标：实现声明式 Stage 装配
- 任务：RF-03（详细设计 + 实现）、T-03（集成测试）
- 预计完成时间：待定

## 4. 当前工作集（Phase 17）
- RF-01：Facade 业务逻辑剥离（优先级：高）
- RF-02：TaskWorkerFactory 参数简化（优先级：中）
- RF-04：集成测试方案准备（优先级：中高）
  - T-04：测试基础设施搭建
  - T-05：核心场景集成测试实现
  - T-04（测试基础设施）
  - T-05（核心场景测试）
- 预计完成时间：待定
  - 多租户并发测试
### Phase 18（计划中）：Stage 策略模式（低优先级）
- 目标：实现声明式 Stage 装配
- 任务：RF-03（详细设计 + 实现）、T-03（集成测试）
- 预计完成时间：待定

## 4. 当前工作集（Phase 17）
- RF-01：Facade 业务逻辑剥离（优先）
- RF-02：TaskWorkerFactory 参数简化
- **T-07** Checkpoint 持久化集成测试 — TODO（依赖 T-04）
  - Redis 模式测试
  - 故障恢复测试
  - 批量恢复测试
    - 按 @Order 排序后组装为 List<TaskStage>
### 2.3. 文档更新
- **D-01** 更新 ARCHITECTURE_PROMPT.md 反映重构后的架构 — TODO（依赖 RF-01/02/03）
- **D-02** 更新类图和组件图 — TODO（依赖 RF-01/02）
      - 异常场景：失败 → 重试 → 成功
      - 控制流程：暂停 → 恢复 → 完成

### Phase 17（计划中）：架构重构与优化
- 目标：优化 Facade 层、工厂参数、Stage 装配机制
- 任务：RF-01（Facade 重构）、RF-02（参数简化）
- 预计完成时间：待定
    - 事件流验证
### Phase 18（计划中）：Stage 策略模式（低优先级）
- 目标：实现声明式 Stage 装配
- 任务：RF-03（详细设计 + 实现）、T-03（集成测试）
- 预计完成时间：待定

## 4. 当前工作集（Phase 17）
- RF-01：Facade 业务逻辑剥离（优先）
- RF-02：TaskWorkerFactory 参数简化
      - 事件幂等性（重复处理）
    - Checkpoint 持久化验证
      - 内存模式基础测试
      - Redis 模式集成测试（使用 Testcontainers）
      - 故障恢复测试
  - **技术栈**：
    - Spring Boot Test + @SpringBootTest
    - Testcontainers (Redis)
    - Awaitility（异步等待断言）
    - MockMvc（如果需要 REST API 测试）
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
- **T-01** Facade 业务逻辑剥离后的单测更新 — TODO（依赖 RF-01）
- **T-02** TaskWorkerFactory 重构后的单测更新 — TODO（依赖 RF-02）
- **T-03** StageFactory 策略模式的集成测试 — TODO（依赖 RF-03）

### 2.3. 文档更新
- **D-01** 更新 ARCHITECTURE_PROMPT.md 反映重构后的架构 — TODO（依赖 RF-01/02/03）
- **D-02** 更新类图和组件图 — TODO（依赖 RF-01/02）

## 3. 阶段路线图（待办编号映射）

### Phase 17（计划中）：架构重构与优化
- 目标：优化 Facade 层、工厂参数、Stage 装配机制
- 任务：RF-01（Facade 重构）、RF-02（参数简化）
- 预计完成时间：待定

### Phase 18（计划中）：Stage 策略模式（低优先级）
- 目标：实现声明式 Stage 装配
- 任务：RF-03（详细设计 + 实现）、T-03（集成测试）
- 预计完成时间：待定

## 4. 当前工作集（Phase 17）
- RF-01：Facade 业务逻辑剥离（优先）
- RF-02：TaskWorkerFactory 参数简化

## 5. 说明
- 冲突锁释放：仅终止态（COMPLETED/FAILED/CANCELLED/ROLLED_BACK/ROLLBACK_FAILED）释放；执行路径释放 + 事件兜底释放双层保障；回滚中（ROLLING_BACK）与暂停（PAUSED）不释放。
