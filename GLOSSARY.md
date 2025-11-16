# 术语表（Glossary）

- Plan：包含一组租户级 Task 的计划；管理并发阈值与调度；可进入 PAUSED 状态。
- Task：租户级切换任务；仅在 Stage 边界响应暂停/取消；回滚/重试为手动。
- Stage：一组顺序 Step；失败短路；不可切片。
- Step：最小执行单元；示例：ConfigUpdate/Broadcast/HealthCheck。
- Checkpoint：阶段边界的进度快照；支持批量恢复与可插拔存储。
- Rollback：重发上一次可用配置（prevConfigSnapshot）；健康验证通过后更新 lastKnownGoodVersion。
- HealthCheck：固定轮询间隔（3s），尝试上限（10），要求全通过。
- Heartbeat：每 10s 的任务进度心跳；携带 completed/total。
- MetricsRegistry：计量抽象；默认 Noop；可用 Micrometer 适配器替换。
- MDC：planId/taskId/tenantId/stageName；执行结束必须清理。
- ConflictRegistry：租户级互斥锁，防止同租户并发任务。
- sequenceId：事件幂等自增序号；消费者丢弃小于等于已处理的序列。

