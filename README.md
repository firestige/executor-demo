# Executor Demo — Plan/Task/Stage 设计与使用

## 🎉 最新更新

### 2025-11-23: T-016 持久化方案完成

**✅ 状态持久化 + 查询API 已完成！**

- 🔐 Redis 分布式租户锁（支持多实例部署）
- 💾 Plan/Task 状态自动持久化（基于事件驱动）
- 🔍 最小兜底查询 API（重启后状态恢复）
- ⚡ AutoConfiguration 自动装配（开箱即用）
- 🛡️ 故障降级（Redis → InMemory）

**📖 详细文档**:
- [T-016 最终实施报告](./docs/temp/task-016-final-implementation-report.md)
- [Phase 2 实施报告](./docs/temp/task-016-phase2-implementation-report.md)
- [Phase 3 查询API](./docs/temp/task-016-phase3-completion-report.md)
- [Phase 4 测试报告](./docs/temp/task-016-phase4-completion-report.md)

### 2025-11-19: Stage Factory 动态编排框架

**✅ Stage Factory 动态编排框架已完成！**

- ✨ 新增配置驱动的动态 Stage/Step 框架
- 🏗️ 实现防腐层工厂模式（TenantConfig → ServiceConfig）
- 🔄 支持 3 种服务类型：蓝绿网关、Portal、ASBC网关
- 📦 4 种可组合 Step：Redis写入、Pub/Sub广播、健康检查、HTTP请求
- 🛡️ 服务降级：Nacos 不可用时自动降级到固定 IP
- ✅ 集成测试：5/5 通过，BUILD SUCCESS

**📖 快速开始**:
- [完整实现报告](./STAGE_FACTORY_IMPLEMENTATION_COMPLETE.md)
- [使用指南](./STAGE_FACTORY_USAGE_GUIDE.md)
- [设计方案](./STAGE_FACTORY_IMPLEMENTATION_PLAN.md)

---

## 概述
- 本项目是一个多租户的蓝绿切换执行器，实现 Plan → Task → Stage 的分层编排。
- 目标：异步执行、租户隔离（FIFO）、跨租户并发、checkpoint 式暂停/恢复、严格状态机、事件驱动可观察性。

核心概念
- Plan：一次切换的计划，包含一组租户的切换任务；负责并发阈值、冲突控制与调度（FIFO）。
- Task：租户维度的切换任务；仅在 Stage 边界可暂停/取消/重试/回滚（均为手动触发）。
- Stage：由若干 Step 组成的服务切换步骤（例如 ServiceNotification、HealthCheck）；Stage 内不可切片。

健康检查（内置语义）
- 固定每 3s 轮询一次；连续 10 次未达预期判定失败。
- 所有实例必须成功（全通过）才算 Stage 成功。
- 可通过 ExecutorProperties 配置（测试中可压低为 0 秒间隔、3 次）。

## 健康检查配置（HC-01）
- 全局配置项（ExecutorProperties）：
  - healthCheckPath：健康检查路径（默认 /health）
  - healthCheckVersionKey：响应体中的版本键（默认 version）
  - healthCheckIntervalSeconds：轮询间隔秒（默认 3）
  - healthCheckMaxAttempts：最大尝试次数（默认 10）
- 优先级：TenantDeployConfig > application 配置 > 默认值。
- URL 解析策略：
  - 如果 NetworkEndpoint.value 以 http/https 开头，直接使用。
  - 否则使用 targetDomain 或 targetIp + healthCheckPath 组装。
- 测试建议：在单测中压低间隔与次数（例如 0s/3 次），通过 stub 的 HealthCheckClient 模拟成功/失败。

配置优先级
- TenantDeployConfig（实例覆盖） → application 配置 → 默认值。
- Facade 不直接持有外部 DTO；通过工厂转换为内部模型，保护内聚与演进。

并发与冲突
- Plan 级 maxConcurrency + FIFO；同一租户不可并发（冲突注册表保障）。
- 幂等：事件携带自增 sequenceId，消费端丢弃已处理序列。

暂停/恢复/取消/回滚/重试
- 协作式暂停：仅在 Stage 边界的 checkpoint 响应；Stage 事件只有开始、成功、失败。
- 回滚与重试均为手动触发；重试支持 fromCheckpoint，会补偿一次进度事件以保证事件序列连续性。

事件与心跳
- 所有事件通过 TaskStateManager 发布，包含序列号；
- 心跳：每 10s 报告一次进度（completedStages/totalStages），同时视为一次心跳。

快速开始
- Spring 环境（推荐）：项目已提供 `ExecutorConfiguration`，装配好以下 Bean：
  - TaskStateManager、ValidationChain、ExecutorProperties、HealthCheckClient(Mock)、DeploymentTaskFacade。
- 直接注入 Facade 使用：

```java
@Autowired
private DeploymentTaskFacade facade;

// 1) 创建切换任务（Plan 级）
List<TenantDeployConfig> configs = List.of(cfg("tenantA", 100L, 1L), cfg("tenantB", 100L, 1L));
TaskCreationResult created = facade.createSwitchTask(configs);
String planId = created.getPlanId();
List<String> taskIds = created.getTaskIds();

// 2) 运行期控制
facade.pauseTaskByTenant("tenantA");
facade.resumeTaskByTenant("tenantA");
facade.retryTaskByPlan(100L, true);   // fromCheckpoint 重试
facade.rollbackTaskByPlan(100L);      // 回滚

// 3) 查询任务
TaskStatusInfo info = facade.queryTaskStatus(taskIds.get(0));
System.out.println(info.getMessage());
```

- 非 Spring 场景（手工装配）：
```java
ValidationChain chain = new ValidationChain();
TaskStateManager stateManager = new TaskStateManager(event -> {});
ExecutorProperties props = new ExecutorProperties();
HealthCheckClient client = url -> Map.of("version", "1");
DeploymentTaskFacade facade = new DeploymentTaskFacadeImpl(chain, stateManager, props, client);
```

查询字段与语义
- message：包含进度、currentStage、paused、cancelled 标志。
- status：TaskStatus（RUNNING/PAUSED/CANCELLED/ROLLED_BACK/...）。
- completedStages/totalStages：用于进度计算（事件中也有明细）。

## 查询 API（仅兜底使用）

> ⚠️ **重要**：查询 API 仅用于系统重启后的手动状态确认，不建议常规调用。

### 使用场景

**典型场景**：
1. 系统意外重启后，SRE 手动查询任务状态
2. 决定是否需要 fromCheckpoint 重试
3. 外部监控系统确认任务执行进度

### API 列表

#### 1. 查询任务状态（通过租户ID）

```java
TenantId tenantId = TenantId.of("tenant-001");
TaskStatusInfo status = facade.queryTaskStatusByTenant(tenantId);

System.out.println("状态: " + status.getStatus());
System.out.println("进度: " + status.getCurrentStage() + "/" + status.getTotalStages());
```

**返回字段**：
- `taskId`: 任务ID
- `status`: 任务状态（RUNNING/PAUSED/FAILED/COMPLETED等）
- `currentStage`: 当前执行到第几阶段
- `totalStages`: 总阶段数
- `message`: 附加信息

#### 2. 查询计划状态

```java
PlanId planId = PlanId.of("plan-123");
PlanStatusInfo plan = facade.queryPlanStatus(planId);

System.out.println("计划状态: " + plan.getStatus());
System.out.println("任务数: " + plan.getTaskCount());
System.out.println("并发度: " + plan.getMaxConcurrency());
```

**返回字段**：
- `planId`: 计划ID
- `status`: 计划状态
- `taskCount`: 任务数量
- `taskIds`: 任务ID列表
- `maxConcurrency`: 最大并发度

#### 3. 检查是否有Checkpoint

```java
TenantId tenantId = TenantId.of("tenant-001");
boolean hasCheckpoint = facade.hasCheckpoint(tenantId);

if (hasCheckpoint) {
    // 从checkpoint重试（跳过已完成的阶段）
    facade.retryTaskByTenant(tenantId, true);
} else {
    // 从头重试
    facade.retryTaskByTenant(tenantId, false);
}
```

### 完整示例：重启后恢复流程

```java
// 1. 外部系统检测到服务重启
// 2. 查询失败租户列表（从外部数据库）
List<String> failedTenants = externalSystem.getFailedTenants();

// 3. 逐一查询状态并决定重试策略
for (String tenantId : failedTenants) {
    TenantId tid = TenantId.of(tenantId);
    TaskStatusInfo status = facade.queryTaskStatusByTenant(tid);
    
    // 4. 判断是否可重试
    if (status.getStatus() == TaskStatus.FAILED) {
        boolean hasCheckpoint = facade.hasCheckpoint(tid);
        
        // 5. SRE 确认后重试
        if (hasCheckpoint) {
            logger.info("租户 {} 从 Checkpoint 重试 (阶段 {}/{})", 
                tenantId, status.getCurrentStage(), status.getTotalStages());
            facade.retryTaskByTenant(tid, true);
        } else {
            logger.info("租户 {} 从头重试", tenantId);
            facade.retryTaskByTenant(tid, false);
        }
    }
}
```

### 注意事项

- ❌ **不要高频轮询**：查询API设计用于低频手动查询（SRE介入场景）
- ❌ **不要用于监控**：监控指标应通过事件推送到独立监控系统
- ❌ **不要用于业务逻辑**：正常业务流程应依赖事件通知机制
- ✅ **仅兜底使用**：系统重启后状态恢复的保险绳

### 配置说明

#### 开发环境（内存存储）

```yaml
executor:
  persistence:
    store-type: memory  # 使用内存，重启后丢失
  checkpoint:
    store-type: memory
```

#### 生产环境（Redis存储）

```yaml
spring:
  data:
    redis:
      host: redis.prod.example.com
      port: 6379
      password: ${REDIS_PASSWORD}

executor:
  persistence:
    store-type: redis   # 使用Redis持久化
    namespace: prod-executor
    projection-ttl: 7d  # 投影数据保留7天
    lock-ttl: 2h30m     # 租户锁TTL
  checkpoint:
    store-type: redis
    namespace: prod-executor
    ttl: 7d
```

### 架构说明

查询 API 基于 **CQRS + Event Sourcing** 架构：

```
领域聚合 (TaskAggregate/PlanAggregate)
    ↓ 发布领域事件
事件监听器 (TaskStateProjectionUpdater)
    ↓ 自动更新投影
投影存储 (Redis/InMemory)
    ↓ 查询
查询服务 (TaskQueryService)
    ↓ 封装
Facade API (queryTaskStatusByTenant)
```

**优势**：
- ✅ 无代码侵入（DomainService 无需修改）
- ✅ 自动同步（事件驱动）
- ✅ 最终一致（可接受短暂不一致）
- ✅ 易扩展（添加新监听器即可）

---
- 通过构造器注入 ExecutorProperties（压低间隔/次数）与 HealthCheckClient stub 实现快速验证。
- 失败路径单测用 stub 返回错误来模拟，无需真实等待 10×3s。

遗留清理
- 旧的 ExecutionUnit/TaskOrchestrator/TenantTaskExecutor/ServiceNotificationStage 已移除，主线已完全切换到 Plan/Task/Stage 新架构（见提交记录）。

## 工厂扩展点（HC-03 / SC-05）
- StageFactory：根据 TenantDeployConfig 生成 FIFO 阶段（默认组合：ConfigUpdate -> Broadcast -> HealthCheck）。
- TaskWorkerFactory：集中创建 TaskExecutor 并注入 HeartbeatScheduler，方便替换或增加装配逻辑（如指标/限流等）。

## 指标与可观测性（OB）
- 抽象：MetricsRegistry（默认 Noop）。
- 事件：
  - TaskExecutor 会在开始/终止路径累加计数：task_active、task_completed、task_failed、task_paused、task_cancelled。
  - HeartbeatScheduler 周期上报 Gauge：heartbeat_lag（= totalStages - completed，非负）。
- 对接 Micrometer（可选）：
  - 依赖：pom.xml 已加入 io.micrometer:micrometer-core。
  - 适配器：MicrometerMetricsRegistry（一次注册、多次更新 Gauge）。
  - 在 Spring 环境中可通过注入 MeterRegistry 构建 DefaultTaskWorkerFactory(new MicrometerMetricsRegistry(meterRegistry)) 完成替换。
- 示例（手工装配）
```java
MeterRegistry reg = new SimpleMeterRegistry();
TaskWorkerFactory wf = new DefaultTaskWorkerFactory(new MicrometerMetricsRegistry(reg));
```

## 事件示例（EV-05）
以下为部分关键事件的示例字段（实际以事件对象为准）：
- TaskStartedEvent
```json
{"taskId":"t1","totalStages":3,"sequenceId":12}
```
- TaskProgressEvent / Heartbeat
```json
{"taskId":"t1","currentStage":"switch-service","completedStages":1,"totalStages":3,"sequenceId":13}
```
- TaskStageCompletedEvent
```json
{"taskId":"t1","stageName":"switch-service","stageResult":{"stageName":"switch-service","success":true},"sequenceId":14}
```
- TaskStageFailedEvent
```json
{"taskId":"t1","stageName":"switch-service","failureInfo":{"type":"SYSTEM_ERROR","message":"timeout"},"sequenceId":15}
```
- TaskFailedEvent
```json
{"taskId":"t1","failureInfo":{"type":"SYSTEM_ERROR","message":"timeout"},"completedStages":["switch-service"],"failedStage":"health-check","sequenceId":16}
```
- TaskCompletedEvent
```json
{"taskId":"t1","durationMillis":1200,"completedStages":["switch-service"],"sequenceId":17}
```
- TaskRetryStartedEvent / TaskRetryCompletedEvent
```json
{"taskId":"t1","fromCheckpoint":true,"sequenceId":18}
```
- TaskRollingBackEvent / TaskRolledBackEvent
```json
{"taskId":"t1","reason":"manual","stagesToRollback":["switch-service"],"sequenceId":19}
```
- TaskCancelledEvent
```json
{"taskId":"t1","cancelledBy":"facade","lastStage":"broadcast-change","sequenceId":20}
```

## 常用命令
```bash
mvn -q -DskipTests=false test
mvn -q -Dtest=xyz.firestige.executor.integration.FacadeE2ERefactorTest test
```
