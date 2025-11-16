# 重构路线图（Plan / Task / Stage）— 结构化中文版

## 0. 指导原则
- Facade 方法签名与入参 DTO 保持稳定（返回内容允许演进但语义一致）。
- 不保留双系统：旧代码经审核后直接移除；禁止使用 V2/V3 命名后缀。
- 回滚与重试均为手动（通过 Facade），自动行为仅包含：健康检查轮询 + 心跳/进度事件。
- 健康检查：固定 3 秒间隔，最多 10 次尝试，所有端点成功才通过。
- 事件幂等：task/plan 级 sequenceId 单调递增；消费者丢弃小于等于已处理 sequenceId 的事件。
- 并发：Plan.maxConcurrency 控制任务并行度；=1 时严格 FIFO；租户级冲突锁防止同租户并发任务。
- 暂停/取消：协作式，仅在 Stage 边界被响应。
- MDC：注入 planId / taskId / tenantId / stageName；执行完成或异常必须清理。
- 配置优先级：TenantDeployConfig > application 配置 > 默认值。
- 胶水层：PlanFactory 深拷贝外部 DTO 为内部聚合，内部不直接引用外部对象。
- Checkpoint：存储可插拔（当前 InMemory，后续 Redis/DB）。

## 1. 已完成历史（归档）
- **Phase 0**：初始化与分支建立
- **Phase 1**：领域聚合与运行时上下文
Phase 2：初版 TaskStateMachine（Guard/Action 接口骨架）
Phase 3：ConflictRegistry 与 TaskScheduler 骨架
Phase 4：Stage/Step 模型 + HealthCheckStep
Phase 5：Checkpoint 抽象与内存实现
Phase 6：TaskExecutor 接线 + 心跳 + MDC + 基础回滚/重试
Phase 7：Facade 接入新架构（创建/暂停/恢复/重试/回滚/查询）
Phase 8：旧体系清理（ExecutionUnit / TaskOrchestrator 等删除）
Phase 9：初步文档更新（Architecture Prompt / README）

> 以上阶段已完成，不再重新规划；回溯请参考对应提交或标签。

## 2. 待办目录（编号可追踪）
（状态机）
SM-01 FAILED→RUNNING Guard（retryCount < maxRetry）
SM-02 RUNNING→PAUSED Guard（pauseRequested = true）
SM-03 RUNNING→COMPLETED Guard（currentStageIndex == totalStages）
SM-04 RUNNING 进入 Action：记录 startedAt
SM-05 COMPLETED / FAILED / ROLLED_BACK / ROLLBACK_FAILED Action：记录 endedAt + durationMillis
SM-06 PlanStateMachine 基本迁移（PENDING→RUNNING→PAUSED/COMPLETED/FAILED）
SM-07 替换 retry / cancel 直接 task.setStatus 为 stateManager.updateState

（事件）
EV-01 RetryStarted / RetryCompleted（包含 fromCheckpoint 标志）
EV-02 每阶段回滚事件（StageRollingBack / StageRolledBack / StageRollbackFailed）
EV-03 Cancel 事件增强（cancelledBy + lastStage）
EV-04 sequenceId 连续性测试（无跳号/回退）
EV-05 README 中关键事件示例

（回滚/快照）
RB-01 回滚成功恢复 prevConfigSnapshot（deployUnitId/version/name）
RB-02 回滚健康确认后更新 lastKnownGoodVersion
RB-03 在 RolledBack 事件中发布快照恢复详情

（Checkpoint）
CP-01 每个成功 Stage 后保存 checkpoint
CP-02 终态/回滚后统一清理 checkpoint
CP-03 批量恢复 API（loadMultiple）
CP-04 RedisCheckpointStore 占位 + SPI 选择（memory|redis|db）
CP-05 Pause→Resume 正确性测试（checkpoint 连续性）

（健康检查）
HC-01 ExecutorProperties 支持 versionKey / path 可配置
HC-02 部分实例失败用例（部分端点滞后导致 Stage 失败）
HC-03 StageFactory 抽象（声明式组合步骤）

（并发/调度）
SC-01 PlanOrchestrator 支持 plan 级 pause/resume/rollback 路由
SC-02 任务结束各路径释放 ConflictRegistry 锁（成功/失败/取消/回滚）
SC-03 FIFO 测试（maxConcurrency=1）保证启动顺序
SC-04 并发启动测试（maxConcurrency>1）正确性
SC-05 TaskWorkerFactory 抽象（封装 TaskExecutor 创建）

（测试覆盖）
TC-01 长耗时 Step 心跳测试（≥2 次心跳）
TC-02 fromCheckpoint 与 fresh 重试差异（不重复已完成 Stage）
TC-03 执行后 MDC 清理（线程局部检查）
TC-04 回滚失败路径（某 Stage rollback 抛异常）触发 RollbackFailed
TC-05 durationMillis 字段正确性测试

（可观测性/指标）
OB-01 Micrometer 计数器（task_active / task_completed / task_failed / rollback_count）
OB-02 heartbeat_lag Gauge
OB-03 结构化日志 + MDC 稳定性测试

（文档/治理）
DG-01 最终架构文档扩展点矩阵
DG-02 迁移指南（旧 → 新）最终版
DG-03 README 事件 payload 示例章节
DG-04 Glossary 扩展（policies / instrumentation）

## 3. 阶段路线图（待办编号映射）
Phase 10（状态机 & 核心事件）
包含：SM-01..05, SM-07, EV-01, EV-02, EV-04, TC-05
验收：
- Guard / Action 单测全部通过
- RetryStarted / RetryCompleted & 每阶段回滚事件产出
- retry/cancel 不再直接修改状态
- durationMillis 写入并测试校验

Phase 11（回滚快照 & 取消增强）
包含：RB-01..03, EV-03, SM-06, TC-04
验收：快照恢复事件详情正确；取消事件包含附加字段；PlanStateMachine 基本迁移通过

Phase 12（Checkpoint SPI & 批量恢复）
包含：CP-01..05, SC-02, TC-02
验收：批量 API 可用；Redis 占位可选择；暂停→恢复 checkpoint 连续

Phase 13（健康检查配置 & StageFactory）
包含：HC-01..03, SC-05
验收：versionKey/path 配置生效；StageFactory 生成声明式组合

Phase 14（并发与调度稳健性）
包含：SC-01, SC-03, SC-04, TC-01
验收：FIFO 与并发测试通过；路由 pause/resume/rollback 生效

Phase 15（可观测性与指标）
包含：OB-01..03, TC-03
验收：指标按预期暴露；MDC 清理与结构化日志测试通过

Phase 16（文档终稿）
包含：DG-01..04, EV-05
验收：README / Architecture Prompt 最终版；迁移指南审核通过

## 4. 执行模式（各 Phase 通用步骤）
1. 制定实现清单（引用待办编号）
2. 垂直切片交付（Guard + Action + 事件 + 测试一起）避免半成品
3. 每完成关键逻辑运行 `mvn -q -DskipTests=false test`
4. 在待办条目后标记状态：DONE / IN-PROGRESS / BLOCKED
5. 验收通过打标签：`refactor-phase10-ok` 等
6. 准备回滚说明（按逻辑单元分提交）

## 5. 当前工作集（Phase 10 草案）
[ ] SM-01 FAILED→RUNNING Guard
[ ] SM-02 RUNNING→PAUSED Guard
[ ] SM-03 RUNNING→COMPLETED Guard
[ ] SM-04 RUNNING 进入写 startedAt
[ ] SM-05 终态写 endedAt + durationMillis
[ ] SM-07 替换 retry / cancel 直接赋值
[ ] EV-01 RetryStarted / RetryCompleted 事件
[ ] EV-02 每阶段回滚事件
[ ] EV-04 sequenceId 连续性测试
[ ] TC-05 durationMillis 单测

## 6. 风险与缓解
- 状态机扩展导致不一致：单独为每个 Guard/Action 增量单测
- 事件序列缺口：早期实现 EV-04 保证连续性
- 回滚并发请求：租户冲突锁 + 单任务内单线程执行回滚

## 7. 回滚策略模板
若 Phase 验收失败：
```bash
# 回退到上一个成功标签
git checkout feature/plan-task-stage-refactor
git reset --hard refactor-phase{N-1}-ok
```
或回滚部分提交：
```bash
git revert <commit_sha_1> <commit_sha_2>
```

## 8. 状态更新约定
在待办 ID 后追加：
- SM-01 DONE
- EV-02 IN-PROGRESS
- RB-01 BLOCKED（等待健康确认机制）

## 9. 范围外（暂不评估）
- 多 Plan 依赖编排
- 调度优先级 / 抢占
- 自适应健康检查间隔（动态退避）

## 10. 下一步
待你审核通过后，启动 Phase 10：先实现 SM-01..03 Guard + SM-04..05 Action + SM-07 状态替换，再补 EV-01/EV-02 与测试。
