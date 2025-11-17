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
> Phase 0-16 已完成并归档到 develop.log，包括：
> - 核心架构建立（领域模型、状态机、调度器）
> - Stage/Step 执行模型与健康检查
> - Checkpoint 持久化（InMemory + Redis）
> - 回滚快照与事件系统
> - 并发控制与冲突锁释放
> - 可观测性（Metrics + MDC）
> - 完整文档体系（架构设计、迁移指南、4+1 视图）

## 2. 当前待办（Phase 17 & 18）

### 2.1. 架构重构（Phase 17）

#### RF-01: Facade 业务逻辑剥离 — TODO
- **问题**：DeploymentTaskFacadeImpl.createSwitchTask 方法包含大量业务逻辑
- **目标**：Facade 作为防腐层，仅负责数据结构转换和协调调用
- **方案**：
  - 提取业务逻辑到专门的应用服务层（Application Service）
  - 引入 PlanApplicationService / TaskApplicationService
  - Facade 仅负责：DTO 转换 + 调用应用服务 + 结果封装
- **优先级**：高

#### RF-02: TaskWorkerFactory 参数简化 — TODO
- **问题**：DefaultTaskWorkerFactory.create() 方法参数过多（8+ 个参数）
- **目标**：提升可读性和可维护性
- **方案**：
  - 引入 TaskWorkerCreationContext 参数对象（Builder 模式）
  - 或使用 TaskWorkerConfig 配置对象封装相关参数
  - 保持扩展性：便于后续增加新参数
- **优先级**：中

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
- **T-01**: Facade 业务逻辑剥离后的单测更新 — TODO（依赖 RF-01）
- **T-02**: TaskWorkerFactory 重构后的单测更新 — TODO（依赖 RF-02）
- **T-04**: 测试基础设施搭建 — TODO（RF-04 子任务）
- **T-05**: 核心场景集成测试实现 — TODO（RF-04 子任务）
- **T-06**: 事件流验证测试 — TODO（RF-04 子任务）
- **T-07**: Checkpoint 持久化集成测试 — TODO（RF-04 子任务）

### 2.3. 文档更新
- **D-01**: 更新 ARCHITECTURE_PROMPT.md 反映重构后的架构 — TODO（依赖 RF-01/02）
- **D-02**: 更新类图和组件图 — TODO（依赖 RF-01/02）

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
