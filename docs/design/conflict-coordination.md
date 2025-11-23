# 冲突协调设计（Conflict Coordination）

> 任务: T-011  
> 状态: 完成  
> 最后更新: 2025-11-23

---
## 1. 背景
同一租户在任意时刻只能存在一个 RUNNING/PAUSED 的任务，需在应用/基础设施层实现互斥。当前版本以“单实例本地锁”为主，未来演进至分布式锁。

---
## 2. 代码角色
| 组件 | 包 | 职责 |
|------|----|------|
| ConflictRegistry | `infrastructure.scheduling` | 本地互斥表（ConcurrentHashMap）|
| TenantConflictManager | 同上 | 锁操作入口（tryAcquire/release）|
| TenantConflictCoordinator | `application.conflict` | 协调编排与锁管理（编排前检查/释放）|
| TaskExecutionOrchestrator | `application.orchestration` | 提交任务前调用协调器，执行结束回收 |

---
## 3. 执行时机
- 提交任务前：`TenantConflictCoordinator.tryAcquire(tenantId)`，失败则跳过提交并记录冲突事件。
- 任务完成/失败/取消后：`release(tenantId)`。
- 计划完成/失败：由 `PlanCompletionListener` 批量释放。

---
## 4. 边界与演进
- 当前：单机可用；多实例可能出现并发冲突（已在风险中说明）。
- 演进：引入 Redis 锁（SET NX + TTL + 续租），并在协调器层屏蔽具体实现。

---
## 5. 风险
- 实例崩溃未释放：增加兜底清理（PlanCompletionListener），并提供定期巡检（未来）。
- 群体雪崩：集中提交导致锁竞争激增；策略层可限流/退避。

---
## 6. Definition of Done（T-011）
- [x] 明确四个组件的职责分工
- [x] 指定调用时机与释放策略
- [x] 标注风险与分布式演进路径

---
> 参见：`xyz.firestige.deploy.infrastructure.scheduling.*`，`xyz.firestige.deploy.application.conflict.*`。

