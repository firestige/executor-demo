# Executor Demo - 4+1 架构视图说明

本目录包含 Executor Demo 项目的完整 4+1 架构视图（使用 PlantUML 绘制）。

---

## 📁 文件列表

### 1. 用例视图 (Use Case View)
**文件:** `01_usecase_view.puml`

**说明:** 展示系统的功能用例及参与者
- **主要用例:**
  - 创建切换任务
  - 暂停/恢复/取消任务
  - 重试/回滚任务
  - 查询任务状态
  - 批量租户切换
  
- **内部机制:**
  - 租户冲突检测
  - 并发控制
  - 状态机管理
  - Checkpoint 保存恢复
  - 健康检查验证
  - 心跳监控
  - 事件发布
  - MDC 日志上下文

---

### 2. 时序图 (Sequence Diagrams)
展示主要用例的交互时序

#### 2.1 创建切换任务
**文件:** `02_sequence_create_task.puml`

**流程:**
1. 运维人员调用 Facade 创建任务
2. PlanFactory 创建 Plan 和 Task 聚合
3. ValidationChain 校验配置
4. PlanOrchestrator 提交 Plan
5. TaskScheduler 调度任务（并发控制）
6. ConflictRegistry 检测租户冲突
7. TaskExecutor 执行 Stage 列表
8. CheckpointService 保存进度
9. TaskEventSink 发布事件

#### 2.2 暂停/恢复任务
**文件:** `03_sequence_pause_resume.puml`

**关键点:**
- 暂停是协作式的，仅在 Stage 边界响应
- 暂停时保存 checkpoint
- 恢复时从 checkpoint 继续执行
- 发布补偿性进度事件保证序列连续性

#### 2.3 回滚任务
**文件:** `04_sequence_rollback.puml`

**关键点:**
- 使用 PreviousConfigRollbackStrategy 重发已知良好配置
- 按 Stage 列表逆序执行回滚
- 恢复 prevConfigSnapshot 到聚合
- RollbackHealthVerifier 验证回滚后健康状态
- 验证通过后更新 lastKnownGoodVersion
- 发布 Stage 级回滚事件

#### 2.4 重试任务
**文件:** `05_sequence_retry.puml`

**关键点:**
- 支持 fromCheckpoint 和完全重新执行两种模式
- fromCheckpoint=true: 从断点继续，跳过已完成 Stage
- fromCheckpoint=false: 清空 checkpoint，从头开始
- 重试受 maxRetry 限制
- HeartbeatScheduler 支持重复启动

---

### 3. 状态图 (State Diagrams)

#### 3.1 Task 状态机
**文件:** `06_state_task.puml`

**状态流转:**
```
CREATED → VALIDATING → PENDING → RUNNING ⇄ PAUSED
                ↓                    ↓
         VALIDATION_FAILED    COMPLETED/FAILED
                                     ↓
                              ROLLING_BACK
                                     ↓
                          ROLLED_BACK/ROLLBACK_FAILED
```

**关键状态:**
- **RUNNING:** 执行 Stage 列表，仅在边界响应控制
- **PAUSED:** 协作式暂停，checkpoint 已保存
- **FAILED:** 可重试（受 maxRetry 限制）或回滚
- **ROLLING_BACK:** 按 Stage 逆序执行回滚
- **ROLLED_BACK:** 快照恢复完成，终态

#### 3.2 Plan 状态机
**文件:** `07_state_plan.puml`

**状态流转:**
```
CREATED → VALIDATING → READY → RUNNING ⇄ PAUSED
                ↓                  ↓
            FAILED         PARTIAL_FAILED/COMPLETED
                                  ↓
                           ROLLING_BACK
                                  ↓
                        ROLLED_BACK/FAILED
```

**关键状态:**
- **READY:** 验证通过，等待提交
- **RUNNING:** 遵循 maxConcurrency 限制调度任务
- **PARTIAL_FAILED:** 部分任务失败，可重试或回滚
- **PAUSED:** Plan 级暂停，所有任务暂停

#### 3.3 Stage 执行流程
**文件:** `08_state_stage.puml`

**执行逻辑:**
- 按顺序执行 Step 列表（ConfigUpdate → Broadcast → HealthCheck）
- 任一 Step 失败则短路
- 成功后保存 checkpoint
- 支持条件跳过（canSkip）

**HealthCheckStep 细节:**
- 固定 3 秒轮询间隔
- 最多 10 次尝试
- 要求所有实例版本匹配

---

### 4. 组件图 (Component View)
**文件:** `09_component_view.puml`

**分层结构:**

```
Facade Layer (RF-01 重构)
  ├─ DeploymentTaskFacade (异常驱动，返回 void)
  └─ Facade Exceptions (4个异常类)
  
Application Service Layer (RF-01 新增)
  ├─ PlanApplicationService (业务编排)
  ├─ TaskApplicationService (任务操作)
  └─ Application DTOs
      ├─ Result DTOs (PlanCreationResult, PlanOperationResult, TaskOperationResult)
      ├─ Value Objects (PlanInfo, TaskInfo)
      └─ Internal DTO (TenantConfig)
  
Orchestration Layer
  ├─ PlanOrchestrator (计划编排)
  ├─ TaskScheduler (并发调度)
  └─ ConflictRegistry (冲突检测)
  
Domain Layer
  ├─ Aggregate (PlanAggregate, TaskAggregate)
  ├─ State Machine (TaskStateMachine, PlanStateMachine)
  ├─ Stage (CompositeServiceStage, Steps)
  └─ Validation (ValidationChain)
  
Execution Layer (RF-02 优化)
  ├─ TaskExecutor (执行引擎)
  ├─ TaskWorkerFactory (工厂)
  ├─ TaskWorkerCreationContext (参数对象 + Builder)
  └─ HeartbeatScheduler (心跳)
  
Infrastructure Layer
  ├─ CheckpointService (Checkpoint 服务)
  ├─ TaskEventSink (事件发布)
  ├─ HealthCheckClient (健康检查)
  ├─ MetricsRegistry (指标收集)
  └─ RollbackStrategy (回滚策略)
```

**扩展点:**
- StageFactory: 声明式组装 Stage
- TaskWorkerFactory: 封装 TaskExecutor 创建（RF-02：参数对象模式）
- CheckpointStore: 可插拔存储（Memory/Redis）
- MetricsRegistry: 指标收集（Noop/Micrometer）
- RollbackHealthVerifier: 回滚健康确认

**RF-01 重构亮点:**
- 清晰的分层架构：Facade → Application Service → Domain
- DDD 原则：Result DTOs 明确聚合边界，值对象不可变
- 异常驱动：Facade 层返回 void，通过异常处理错误
- 内部 DTO：TenantConfig 解耦应用层与外部 DTO

**RF-02 重构亮点:**
- 参数简化：TaskWorkerFactory.create() 从 9 个参数减少到 1 个
- Builder 模式：提供命名参数风格，提升可读性
- 参数验证：7 个必需参数在构建时验证
- 向后兼容：旧方法标记 @Deprecated

---

### 5. 类图 (Class Diagram)
**文件:** `10_class_diagram.puml`

**核心类:**

**Facade 层 (RF-01):**
- `DeploymentTaskFacade`: 新 Facade（异常驱动，返回 void）
- `TaskCreationException / TaskOperationException / TaskNotFoundException / PlanNotFoundException`: Facade 异常

**Application Service 层 (RF-01):**
- `PlanApplicationService`: Plan 业务编排服务
- `TaskApplicationService`: Task 操作服务
- `PlanCreationResult / PlanOperationResult / TaskOperationResult`: Result DTOs
- `PlanInfo / TaskInfo`: 值对象（不可变）
- `TenantConfig`: 内部 DTO（解耦外部 DTO）

**领域模型:**
- `PlanAggregate`: 计划聚合，包含多个 Task
- `TaskAggregate`: 任务聚合，包含状态和配置快照
- `TenantDeployConfigSnapshot`: 租户配置快照（用于回滚）
- `TaskCheckpoint`: Checkpoint 数据结构

**状态机:**
- `TaskStateMachine`: 任务状态机（Guard/Action 扩展）
- `PlanStateMachine`: 计划状态机
- `TaskStateManager`: 状态管理器，统一管理状态机实例
- `TransitionGuard<T>`: 状态转换守卫接口
- `TransitionAction<T>`: 状态转换副作用接口

**Stage & Step:**
- `TaskStage`: Stage 接口
- `CompositeServiceStage`: 组合 Stage 实现
- `StageStep`: Step 接口
- `ConfigUpdateStep / BroadcastStep / HealthCheckStep`: 具体 Step

**执行层 (RF-02 优化):**
- `TaskExecutor`: 任务执行引擎
- `TaskWorkerFactory`: 工厂接口
- `DefaultTaskWorkerFactory`: 默认工厂实现
- `TaskWorkerCreationContext`: 参数对象（Builder 模式，9参数→1参数）
- `HeartbeatScheduler`: 心跳调度器
- `TaskRuntimeContext`: 运行时上下文（MDC、暂停标志）

**编排层:**
- `PlanOrchestrator`: 计划编排器
- `TaskScheduler`: 任务调度器（并发控制 + FIFO）
- `ConflictRegistry`: 租户冲突注册表

**基础设施:**
- `CheckpointService / CheckpointStore`: Checkpoint 服务与存储
- `TaskEventSink`: 事件发布接口
- `MetricsRegistry`: 指标收集接口
- `RollbackHealthVerifier`: 回滚健康确认接口

---

### 6. 部署视图 (Deployment View)
**文件:** `11_deployment_view.puml`

**节点:**
- **应用服务器:** 运行 Executor 应用，包含线程池和心跳调度器
- **Redis 集群:** 可选的 Checkpoint 持久化存储
- **监控系统:** Micrometer + Prometheus + Grafana
- **Spring Event Bus:** 事件总线
- **租户服务集群:** 多实例部署，提供健康检查端点

**配置:**
- maxConcurrency: 并发阈值
- checkpoint.store-type: memory|redis
- healthCheckIntervalSeconds: 3
- progressIntervalSeconds: 10

---

## 🎨 如何查看

### 方式 1: IDE 插件（推荐）
- **IntelliJ IDEA:** 安装 PlantUML Integration 插件
- **VS Code:** 安装 PlantUML 扩展
- 直接在编辑器中预览和导出

### 方式 2: 在线渲染
访问 [PlantUML Online](http://www.plantuml.com/plantuml/uml/) 并粘贴文件内容

### 方式 3: 命令行生成
```bash
# 安装 PlantUML
brew install plantuml  # macOS
apt-get install plantuml  # Linux

# 生成 PNG
plantuml diagrams/*.puml

# 生成 SVG
plantuml -tsvg diagrams/*.puml
```

---

## 📊 视图关系

```
用例视图 (Use Case)
  ├─ 定义了系统的功能边界
  └─ 驱动 → 时序图（具体交互）
  
时序图 (Sequence)
  ├─ 展示用例的实现细节
  └─ 涉及 → 组件/类（参与者）
  
状态图 (State)
  ├─ 描述核心实体的生命周期
  └─ 由 → 状态机类实现
  
组件图 (Component)
  ├─ 展示系统的模块化结构
  └─ 细化 → 类图（内部实现）
  
类图 (Class)
  ├─ 展示核心类的结构和关系
  └─ 实现 → 用例和时序
  
部署图 (Deployment)
  ├─ 展示运行时的物理部署
  └─ 包含 → 所有组件
```

---

## 🔍 关键设计决策

### 1. 严格状态机
- 通过 Guard 和 Action 扩展点，确保状态转换的合法性
- 所有状态变更通过 TaskStateManager 统一管理

### 2. 协作式暂停/取消
- 仅在 Stage 边界响应，避免中断不可切片的操作
- 通过 TaskRuntimeContext 传递控制信号

### 3. Checkpoint 机制
- 每个 Stage 成功后保存，支持从断点恢复
- 可插拔存储（Memory/Redis），通过配置切换

### 4. 租户隔离与并发控制
- ConflictRegistry 防止同租户并发
- TaskScheduler 实现 Plan 级并发阈值 + FIFO 队列

### 5. 事件驱动可观测性
- 所有状态变更发布事件（带 sequenceId 实现幂等）
- 支持 Spring ApplicationEvent 和自定义 EventBus

### 6. 回滚快照恢复
- 保存 prevConfigSnapshot 用于快速回滚
- 回滚后通过 RollbackHealthVerifier 确认健康状态
- 仅在确认通过后更新 lastKnownGoodVersion

---

## 📚 相关文档

- [ARCHITECTURE_DESIGN_REPORT.md](../ARCHITECTURE_DESIGN_REPORT.md) - 架构设计报告
- [ARCHITECTURE_PROMPT.md](../ARCHITECTURE_PROMPT.md) - 架构提示文档
- [README.md](../README.md) - 项目使用指南
- [TODO.md](../TODO.md) - 开发路线图

---

## 📝 更新历史

- **2025-11-17 (RF-02):** 更新执行层
  - 组件图：新增 TaskWorkerCreationContext（参数对象 + Builder）
  - 类图：新增 TaskWorkerFactory 参数简化设计
  - README：更新核心类列表和设计亮点
  
- **2025-11-17 (RF-01):** 重大架构重构
  - 组件图：新增 Application Service Layer（PlanApplicationService, TaskApplicationService）
  - 组件图：更新 Facade Layer（异常驱动设计）
  - 组件图：新增 Application DTOs（Result DTOs, Value Objects, Internal DTO）
  - 类图：新增完整的 Application Service 和 DTO 类
  - 类图：新增 Facade 层异常类
  - README：更新分层结构和核心类说明
  - README：新增 RF-01 和 RF-02 重构亮点说明
  
- **2025-11-16:** 初始版本，完整 4+1 视图
  - 用例图（包含内部机制）
  - 时序图（创建、暂停/恢复、回滚、重试）
  - 状态图（Task、Plan、Stage）
  - 组件图（分层架构）
  - 类图（核心类关系）
  - 部署图（物理部署）
@startuml 部署视图
!theme plain

node "应用服务器" {
  component "Executor Application" as App {
    [Facade Layer]
    [Orchestration Layer]
    [Domain Layer]
    [Execution Layer]
  }
  
  component "Thread Pool" as Pool
  component "Heartbeat Scheduler" as Heartbeat
  component "MDC Context" as MDC
}

node "Redis Cluster" as Redis {
  database "Checkpoint Store" as CPStore {
    folder "namespace:executor" {
      [task:123:checkpoint]
      [task:456:checkpoint]
    }
  }
}

node "监控系统" as Monitor {
  component "Micrometer Registry" as Metrics
  component "Prometheus" as Prom
  component "Grafana" as Graf
}

node "Spring Event Bus" as EventBus {
  queue "Task Events" as Events
}

node "租户服务集群" as TenantCluster {
  node "Tenant A Instances" {
    [Instance 1]
    [Instance 2]
    [Instance N]
  }
  
  component "Health Check Endpoint" as Health {
    [/health API]
  }
}

' 依赖关系
App --> Pool : 异步任务调度
App --> Heartbeat : 每10秒心跳
App --> MDC : 日志上下文
App --> Redis : Checkpoint 存储\n(可选，默认内存)
App --> EventBus : 发布状态事件
App --> Monitor : 指标上报\n(可选)
App --> TenantCluster : 健康检查\n配置推送

EventBus --> Monitor : 事件订阅

Monitor --> Prom : 指标采集
Prom --> Graf : 可视化

note right of App
  **核心配置**
  - maxConcurrency: 并发阈值
  - healthCheckIntervalSeconds: 3
  - healthCheckMaxAttempts: 10
  - progressIntervalSeconds: 10
  - checkpoint.store-type: memory|redis
end note

note right of Redis
  **Redis 配置**
  - 命名空间隔离
  - TTL 过期策略
  - 支持批量加载
  - 可切换为内存模式
end note

note right of Monitor
  **监控指标**
  - task_active: 活跃任务数
  - task_completed: 完成计数
  - task_failed: 失败计数
  - rollback_count: 回滚计数
  - heartbeat_lag: 心跳延迟
end note

note right of TenantCluster
  **租户集群**
  - 多实例部署
  - 统一健康检查接口
  - 版本信息上报
  - 蓝绿环境隔离
end note

@enduml

