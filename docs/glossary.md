# 术语表（Glossary）

> **最后更新**: 2025-11-22  
> **用途**: 定义项目中的核心概念和术语

---

## 核心领域概念

### Plan（计划/部署计划）
**定义**: 一次完整的多租户部署切换计划，是任务编排的顶层聚合根。

**职责**:
- 管理一组租户的切换任务（Task）
- 控制并发阈值（maxConcurrency）
- 维护租户冲突注册表（同一租户不可并发）
- 调度策略：FIFO（先进先出）

**生命周期**: CREATED → RUNNING → PAUSED → COMPLETED/FAILED

**示例**: "2025-11-22 蓝绿切换部署计划"，包含 tenantA、tenantB、tenantC 的切换任务

---

### Task（任务/租户切换任务）
**定义**: 单个租户维度的切换任务，是 Plan 聚合内的实体。

**职责**:
- 执行单个租户的完整切换流程
- 管理自身状态（CREATED → RUNNING → COMPLETED/FAILED）
- 支持暂停/恢复/重试/回滚（仅在 Stage 边界）

**特性**:
- **协作式暂停**: 只能在 Stage 边界响应暂停请求
- **Checkpoint 续传**: 失败后可从断点重试
- **租户隔离**: 同一租户的 Task 不能并发执行

**生命周期**: CREATED → RUNNING → PAUSED → COMPLETED/FAILED/ROLLED_BACK

---

### Stage（阶段/执行阶段）
**定义**: 由若干 Step 组成的服务切换步骤，是 Task 执行的最小不可切片单位。

**特性**:
- **原子性**: Stage 内不可暂停/取消（内部不可切片）
- **边界**: 暂停/恢复/重试只能在 Stage 边界进行
- **事件**: 只发布开始、成功、失败事件（无内部进度）

**示例**:
- `ServiceNotificationStage`: 服务通知阶段
- `HealthCheckStage`: 健康检查阶段
- `BlueGreenSwitchStage`: 蓝绿切换阶段

---

### Step（步骤）
**定义**: Stage 内的原子操作单元，由 Stage 编排执行。

**当前支持的 Step 类型**:
1. **Redis 写入 Step**: 写入配置到 Redis
2. **Pub/Sub 广播 Step**: 发布消息到消息队列
3. **健康检查 Step**: 轮询服务健康状态
4. **HTTP 请求 Step**: 调用外部 HTTP API

**特性**: Step 由 Stage 内部管理，外部不可见

---

### Checkpoint（检查点/断点）
**定义**: 记录 Task 执行进度的快照，用于支持断点续传。

**保存时机**:
- Task 暂停时
- Task 执行失败时
- Stage 执行完成后（可选）

**包含信息**:
- 当前执行到的 Stage 索引
- 已完成的 Stage 列表
- 执行上下文数据（键值对）

**用途**: 
- 失败后重试时从断点继续（`retryFromCheckpoint=true`）
- 暂停后恢复时继续执行

---

## 执行机制

### Executor（执行器）
**定义**: 负责执行 Task 的策略组件，采用策略模式设计。

**关键特性**:
- 位于 `xyz.firestige.deploy.infrastructure.execution` 包
- **项目最核心的业务逻辑所在**
- 管理 Stage 的编排、执行、状态转换

**核心组件**:
- `TaskExecutionEngine`: 执行引擎，控制整个执行流程
- `StageExecutor`: Stage 执行器，执行单个 Stage
- `StepFactory`: Step 工厂，创建不同类型的 Step

---

### TaskStateManager（任务状态管理器）
**定义**: 管理 Task 状态变更和事件发布的核心组件。

**职责**:
- 发布 Task 状态变更事件
- 维护事件序列号（sequenceId，保证幂等性）
- 心跳机制：每 10 秒报告一次进度

**事件类型**:
- TaskStartedEvent: 任务启动
- StageStartedEvent: Stage 开始
- StageCompletedEvent: Stage 完成
- StageFailedEvent: Stage 失败
- TaskCompletedEvent: 任务完成
- TaskFailedEvent: 任务失败
- TaskProgressEvent: 进度心跳（包含 completedStages/totalStages）

---

## 状态管理

### 状态机（State Machine）
**定义**: 严格定义状态转换规则的模式，防止非法状态跳转。

**Plan 状态转换**:
```
CREATED → RUNNING → PAUSED → RUNNING
                  ↓         ↓
                COMPLETED  FAILED
```

**Task 状态转换**:
```
CREATED → RUNNING → PAUSED → RUNNING
                  ↓         ↓
                COMPLETED  FAILED → ROLLED_BACK
                           ↓
                         RUNNING (retry)
```

---

### 协作式暂停（Cooperative Pause）
**定义**: Task 不会立即暂停，而是在下一个 Stage 边界检查暂停标志后响应。

**原因**: Stage 是原子执行单元，中途暂停会导致状态不一致

**实现**: 
1. 调用 `pauseTask()` 设置暂停标志
2. 当前 Stage 执行完成后检查标志
3. 如果标志为 true，保存 Checkpoint 并暂停

---

## 并发与冲突

### 并发阈值（maxConcurrency）
**定义**: Plan 级别的最大并发 Task 数量限制。

**用途**: 控制同时执行的 Task 数量，避免资源耗尽

**示例**: maxConcurrency=3，最多 3 个 Task 同时执行

---

### 租户冲突注册表
**定义**: 防止同一租户的多个 Task 并发执行的机制。

**规则**: 同一租户在同一时刻只能有一个 Task 在 RUNNING 状态

**实现**: Plan 内部维护 `Map<TenantId, TaskId>`，执行前检查冲突

---

### FIFO 调度
**定义**: 先进先出的任务调度策略。

**规则**: Task 按创建顺序排队，满足并发条件后按顺序执行

---

## 健康检查

### 健康检查轮询（Health Check Polling）
**定义**: 定期检查服务实例健康状态，直到所有实例达到预期版本。

**默认配置**:
- 轮询间隔: 3 秒
- 最大尝试次数: 10 次
- 健康检查路径: `/health`
- 版本键: `version`

**成功条件**: 所有实例的健康检查都返回预期版本

**失败条件**: 达到最大尝试次数后仍有实例未达预期

---

## 配置与集成

### TenantDeployConfig（租户部署配置）
**定义**: 单个租户的部署配置信息，包含租户 ID、目标版本、服务端点等。

**优先级**: TenantDeployConfig > application 配置 > 默认值

---

### Facade（防腐层）
**定义**: 系统边界，对外提供统一接口，隔离外部 DTO 与内��领域模型。

**职责**:
- DTO 转换（外部 DTO → 内部领域对象）
- 参数校验
- 异常转换

**核心 Facade**: `DeploymentTaskFacade`

---

### Result 模式
**定义**: 显式表达成功/失败的返回值模式，替代异常抛出。

**结构**:
```java
Result<T> {
  boolean success;
  T data;           // 成功时有值
  FailureInfo error; // 失败时有值
}
```

**优势**: 
- 避免异常穿透层次
- 显式处理错误
- 符合函数式编程风格

---

## 外部依赖（Mock）

### OB Service
**定义**: OceanBase 租户管理服务（外部依赖，项目中为 Mock）

**用途**: 查询租户信息、更新租户状态

---

### Gateway
**定义**: 网关管理服务（外部依赖，项目中为 Mock）

**用途**: 执行蓝绿切换、流量路由配置

---

### Portal
**定义**: Portal 服务（外部依赖，项目中为 Mock）

**用途**: 业务系统集成

---

## 事件与幂等

### sequenceId（序列号）
**定义**: 每个事件的自增序列号，用于保证事件幂等性。

**规则**: 消费端丢弃已处理过的序列号，避免重复处理

---

### 进度事件补偿
**定义**: 当重试时，如果从 Checkpoint 恢复，会补发一次进度事件以保持事件序列连续性。

---

## 相关文档

- [架构总纲](architecture-overview.md) - 架构设计概览
- [逻辑视图](views/logical-view.puml) - 领域模型类图
- [进程视图](views/process-view.puml) - 状态机与执行流程
- [执行机设计](design/execution-engine.md) - 执行引擎详细设计

