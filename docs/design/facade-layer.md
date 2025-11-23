# 门面层设计（Facade Layer）

> 任务: T-013  
> 状态: 完成  
> 最后更新: 2025-11-23

---
## 1. 设计目的
统一外部调用入口与内部编排入口，避免外部依赖感知内部实现细节，同时在应用内将领域事件与编排解耦。

---
## 2. 门面家族
| 门面 | 包 | 角色 |
|------|----|------|
| DeploymentTaskFacade | `facade` | 面向外部（上层系统）的统一入口：创建计划/控制任务/查询状态 |
| PlanExecutionFacade | `application.facade` | 应用内部的编排入口：供监听器/服务直接调用以触发编排或恢复 |

---
## 3. 职责边界
- DeploymentTaskFacade：DTO 校验、转换、调用应用服务（PlanLifecycleService/TaskOperationService）。
- PlanExecutionFacade：接收监听器委托，协调 Orchestrator 进行任务下发/恢复。
- 两者均不直接操作聚合内部字段，遵守聚合不变式与服务边界。

---
## 4. 时序示例（缩略）
1. 外部调用：调用 DeploymentTaskFacade.createPlan → PlanLifecycleService → 产生 PlanStartedEvent
2. 自监听：PlanStartedListener → PlanExecutionFacade.executePlan(planId) → Orchestrator → Executor

---
## 5. Definition of Done（T-013）
- [x] 门面列表与包路径清晰
- [x] 职责边界与示例时序明确
- [x] 强调与监听器/编排的协作

---
## 6. 范围界定（与 4+1 Scenarios 的关系）
- 桥接（Plan 事件 → 应用层监听器 → 编排/执行）属于技术设计视图，用于遵循 DDD 的解耦原则，是设计成本，而非业务用例。
- Scenarios 视图专注“产品/业务用例与需求导入”，不承载技术桥接细节；因此桥接视图不会加入 `scenarios`，而保留在技术文档与视图中（如 `plan-to-execution-bridge.puml`）。

---
> 参见：`xyz.firestige.deploy.facade.*`，`xyz.firestige.deploy.application.facade.*`。
