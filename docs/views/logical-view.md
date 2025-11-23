# 逻辑视图 - 补充说明

> **最后更新**: 2025-11-22  
> **PlantUML 图**: [logical-view.puml](logical-view.puml)

---

## 聚合设计原则

### Plan 聚合边界
Plan 是聚合根，负责管理其内部的 Task 实体集合。聚合边界的设计遵循以下原则：

1. **事务一致性边界**: 一个 Plan 及其所有 Task 的状态变更在同一事务内完成
2. **不变式保证**:
   - Plan 必须至少包含一个 Task
   - 所有 Task 完成后，Plan 才能标记为 COMPLETED
   - Plan 暂停时，所有 RUNNING 状态的 Task 必须暂停
3. **访问控制**: 外部不能直接修改 Task，必须通过 Plan 的方法

### Task 实体设计
Task 是 Plan 聚合内的实体，具有以下特点：

- 通过 `taskId` 标识唯一性
- 持有对 `planId` 的引用（外键关联）
- 状态转换独立但受 Plan 约束
- 支持 Checkpoint 机制实现断点续传

---

## 状态转换规则

### Plan 状态机
| 当前状态 | 允许操作 | 目标状态 | 前置条件 |
|---------|---------|---------|---------|
| CREATED | start() | RUNNING | 至少有一个 Task |
| RUNNING | pause() | PAUSED | - |
| RUNNING | complete() | COMPLETED | 所有 Task 都是 COMPLETED |
| RUNNING | fail() | FAILED | 存在无法恢复的 Task 失败 |
| PAUSED | resume() | RUNNING | - |

### Task 状态机
| 当前状态 | 允许操作 | 目标状态 | 前置条件 |
|---------|---------|---------|---------|
| CREATED | execute() | RUNNING | Executor 可用 |
| RUNNING | pause() | PAUSED | - |
| RUNNING | - | COMPLETED | 执行成功 |
| RUNNING | - | FAILED | 执行失败 |
| PAUSED | resume() | RUNNING | - |
| PAUSED | retry() | RUNNING | 可指定 fromCheckpoint |
| FAILED | retry() | RUNNING | - |
| FAILED | rollback() | ROLLED_BACK | Executor 支持回滚 |

---

## 领域服务职责

### PlanLifecycleService
负责 Plan 的完整生命周期管理，协调 Plan 与 Task 的状态同步。

**实现位置**: `xyz.firestige.deploy.domain.service.PlanLifecycleService`

**关键职责**:
- 创建 Plan 时验证业务规则
- 启动 Plan 时批量启动 Task
- 暂停/恢复 Plan 时同步所有 Task 状态
- 检查 Plan 完成条件

### TaskOperationService
负责单个 Task 的执行、重试、回滚操作，与 TaskExecutor 策略交互。

**实现位置**: `xyz.firestige.deploy.domain.service.TaskOperationService`

**关键职责**:
- 选择合适的 TaskExecutor
- 执行 Task 并处理结果
- 管理 Checkpoint 的保存与恢复
- 处理失败重试逻辑

---

## 仓储实现说明

### PlanRepository
聚合根仓储，负责整个 Plan 聚合的持久化。

**实现方式**: JPA，通过 `CascadeType.ALL` 自动管理 Task 的持久化。

**关键方法**:
```java
// 加载聚合及其所有实体
Optional<Plan> findById(PlanId planId);

// 保存聚合（包括内部 Task）
Plan save(Plan plan);
```

### TaskRepository
提供 Task 的独立查询能力（仅查询，不做独立修改）。

**使用场景**:
- 按状态查询 Task（如找出所有 FAILED 的 Task）
- 按 PlanId 查询 Task 列表（用于展示）

---

## 执行器策略模式

### 策略接口: TaskExecutor
定义执行器的标准行为，支持不同部署策略的扩展。

**当前实现**:
- `BlueGreenSwitchExecutor`: 蓝绿切换部署
- `CanaryReleaseExecutor`: 金丝雀发布（规划中）

**扩展方式**: 
实现 `TaskExecutor` 接口，并通过 `ExecutorType` 枚举注册新策略。

详细设计见：[执行策略设计](../design/execution-strategy.md)

---

## 相关文档

- [架构总纲](../architecture-overview.md)
- [进程视图](process-view.puml) - 执行流程与时序
- [领域模型详细设计](../design/domain-model.md)
- [状态管理设计](../design/state-management.md)

