# 迁移指南（旧体系 → 新架构）

本文帮助你从旧的 ExecutionUnit/TaskOrchestrator/TaskContext 等体系迁移到 Plan/Task/Stage + StateMachine + 工厂/指标 的新架构。

## 1. 名词与对象替换
- TaskContext → TaskRuntimeContext（运行时上下文：MDC、暂停/取消标志、临时数据）
- ExecutionUnit/TenantTaskExecutor → TaskExecutor（不可切片的 Stage 边界）
- 旧状态流 → TaskStateMachine + TaskStateManager（统一迁移、事件发布、序列号）
- 内联构造 → StageFactory/TaskWorkerFactory（统一构建阶段与执行器）

## 2. Facade 契约
- 方法签名与入参 DTO（TenantDeployConfig/NetworkEndpoint）保持不变；返回体可演进。
- 创建计划：Facade 接受租户维度的配置信息，内部经 PlanFactory 深拷贝为 Plan/Task 聚合。

## 3. 执行语义
- 暂停/取消：协作式，仅在 Stage 边界检查，单个 Stage 不可切分。
- 回滚/重试：手动触发；重试支持 fromCheckpoint。
- 健康检查：固定间隔/最大次数，全部实例成功为过。

## 4. Checkpoint
- 每个成功 Stage 后保存；终态与回滚后清理。
- 支持批量恢复 API；Redis/DB 实现通过 SPI 挂接。

## 5. 并发/调度
- Plan.maxConcurrency 控制并发；=1 则严格 FIFO。
- 冲突锁：同租户不可并发，终态释放 + 事件兜底释放。

## 6. 工厂与扩展点
- StageFactory：声明式组合步骤（默认：ConfigUpdate → Broadcast → HealthCheck）。
- TaskWorkerFactory：集中创建 TaskExecutor 并注入 HeartbeatScheduler。
- MetricsRegistry：无侵入计量抽象；默认 Noop，可选 Micrometer。

## 7. 事件与观测
- 统一由 TaskStateManager 发布，携带单调递增 sequenceId（幂等基于序列）；
- 心跳：每 10s 报告进度，同时视为心跳。
- 指标：task_active/completed/failed/paused/cancelled/rollback_count + heartbeat_lag。

## 8. 测试策略
- 不在生产代码注入测试开关；测试通过 props/stub 降低间隔/次数模拟行为。
- 覆盖：重试差异（fromCheckpoint vs fresh）、回滚阶段事件、MDC 清理、心跳 Gauge。

## 9. 清退要求
- 旧类以 @Deprecated 标注后清理引用；确认无引用后删除文件。
- 不保留 V2/V3 命名；统一命名与目录结构。

## 10. 示例
- README 中提供了 Facade 使用、事件 payload 与配置示例；
- ARCHITECTURE_PROMPT.md 提供扩展点矩阵与英文快照。

