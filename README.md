# Executor Demo — Plan/Task/Stage 设计与使用

概述
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

测试策略（不侵入生产）
- 通过构造器注入 ExecutorProperties（压低间隔/次数）与 HealthCheckClient stub 实现快速验证。
- 失败路径单测用 stub 返回错误来模拟，无需真实等待 10×3s。

遗留清理
- 旧的 ExecutionUnit/TaskOrchestrator/TenantTaskExecutor/ServiceNotificationStage 已移除，主线已完全切换到 Plan/Task/Stage 新架构（见提交记录）。

常用命令
```bash
# 运行测试
mvn -q -DskipTests=false test

# 运行单个测试类（示例）
mvn -q -Dtest=xyz.firestige.executor.integration.FacadeE2ERefactorTest test
```
