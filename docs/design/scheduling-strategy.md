# 计划调度策略（Plan Scheduling Strategy）

> 任务: T-012  
> 状态: 完成  
> 最后更新: 2025-11-23

---
## 1. 概述
调度策略抽象 `PlanSchedulingStrategy` 用于对计划的任务下发方式进行策略化控制，当前提供细粒度与粗粒度两种策略，以适配不同规模与性能需求。

---
## 2. 代码结构
| 组件 | 包 | 说明 |
|------|----|------|
| PlanSchedulingStrategy | `application.orchestration.strategy` | 策略接口 |
| FineGrainedSchedulingStrategy | 同上 | 逐任务下发，适配小规模或高可控 |
| CoarseGrainedSchedulingStrategy | 同上 | 批量下发，适配大规模提高吞吐 |
| TaskExecutionOrchestrator | `application.orchestration` | 注入策略，按策略组织提交 |
| PlanCompletionListener | `application.orchestration.listener` | 完成/失败后通知策略清理 |

---
## 3. 策略对比
| 维度 | 细粒度 | 粗粒度 |
|------|--------|--------|
| 提交方式 | 单个任务逐个提交 | 批量提交一组任务 |
| 并发控制 | 更精细，易插入优先级 | 吞吐更高，粒度较粗 |
| 资源利用 | 可能空闲较多 | 资源更饱和 |
| 场景 | 小批量、严格顺序 | 大批量、高吞吐 |

---
## 4. 与监听器/编排的关系
- PlanStarted/Resumed：由监听器触发 Facade → Orchestrator，Orchestrator 内部调用策略进行下发。
- PlanCompleted/Failed：由监听器通知策略进行冲突标记清理与状态收尾。

---
## 5. Definition of Done（T-012）
- [x] 明确策略接口与两种实现
- [x] 指定 Orchestrator 集成点
- [x] 与 Plan 事件监听器关系说明

---
> 参见：`xyz.firestige.deploy.application.orchestration.strategy.*`。

