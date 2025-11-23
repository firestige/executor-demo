# 架构提示词（Architecture Prompt）

> 版本: 0.1  
> 最后更新: 2025-11-23  
> 用途: 快速让 AI/新人在最少上下文下掌握本模块的核心架构，并生成后续分析 / 代码 / 文档辅助内容。

---
## 1. 使用说明
本文件包含多段可复制的 Prompt 模板。根据使用场景选择对应段落，将其中的 <变量> 替换为你的实际值，然后发送给 AI。

注意：
- 本模块不包含“物理视图”部署拓扑（参见说明：`./views/physical-view-not-applicable.md`）。
- 真实架构信息均以已验证的 4 个视图为准：逻辑 / 进程 / 开发 / 场景。
- 所有聚合之间只通过 ID 引用，不直接持有对方对象。
- 不要再引用 MySQL / JPA 等旧技术栈（当前为 Redis + InMemory）。

---
## 2. 基础快速上下文（复制即可）
```
你现在要理解一个多租户配置切换执行模块（executor-demo）。它包含两个核心聚合：Plan 与 Task，二者独立；Plan 通过 List<TaskId> 引用 Task。Task 内含 Stage 进度与 Checkpoint 机制，用于暂停/重试/恢复。执行机由 TaskExecutionOrchestrator + TaskExecutor 组成：前者负责并发调度与线程池，后者负责阶段编排、心跳、断点续传、状态事件生成。无物理部署视图，技术栈：Java17 + Spring Boot + Redis (Checkpoint/广播) + InMemory（运行态）。请后续回答严格基于这些约束，不允许引入 RDBMS/JPA/自定义 MQ。领域状态枚举：PlanStatus(11)、TaskStatus(13)。所有状态转换先通过 StateTransitionService 内存校验，再调用 TaskDomainService 产生事件并持久化聚合。
```

---
## 3. 深度分析 Prompt 模板
### 3.1 架构解读
```
目标：对 executor-demo 架构进行解读。
请基于以下文件（如需更多上下文，可提示我补充）：
- 领域视图: logical-view.puml
- 进程视图: process-view.puml
- 开发视图: development-view.puml
- 场景视图: scenarios.puml
输出结构：
1. 聚合边界与不变式 (列出关键业务守卫)
2. 状态机核心路径（正常、失败、暂停、重试、回滚）
3. 执行机内部协作（Orchestrator → Executor → Domain）
4. 事件列表与消费建议（不假设具体事件总线实现）
5. 易错点与改进建议（仅限当前范围，不扩展到分布式/微服务）
限制：不得引入物理部署描述；不得添加未出现的技术栈；不得假设外部数据库。
```

### 3.2 代码走查 / 影响评估
```
请基于 executor-demo 的架构，评估以下修改的影响：
<描述你的拟修改，例如："在 TaskExecutor 中为心跳调度器增加动态频率" >
输出：
1. 受影响类（分类：Domain / Application / Infrastructure / DTO）
2. 受影响事件（新增 / 变更 / 废弃）
3. 与状态机的兼容性风险
4. 对 Checkpoint 机制的潜在影响
5. 回滚方案（若修改失败如何恢复）
不得假设新增外部系统。
```

### 3.3 新增 Stage 类型设计
```
我要新增一个 StageStep：<StepName>，功能：<功能描述>。
请输出：
1. 该 Step 放入哪个 Stage 组合中（或需新建 Stage）
2. execute(context) 前置条件与需要的上下文字段
3. 失败分类建议（ErrorType）与 FailureInfo 字段
4. 是否需要 Checkpoint（原则：只在跨阶段暂停点持久化）
5. 对事件模型是否需要新增或复用现有事件
要求：不修改聚合结构；不引入事务管理层；不编造物理部署。
```

### 3.4 风险扫描 Prompt
```
请对当前执行机的设计进行风险扫描，范围限定：
- 高并发下心跳调度成本
- Redis 断点写入竞争
- 分布式租户锁缺失
- 重试风暴与退避策略
输出：
1. 风险成因
2. 当前已有缓解手段（若无明确说明）
3. 可在不引入新外部组件的前提下的轻量优化建议
4. 中长期改进方向（可引入组件，但标记为未来）
```

---
## 4. 精准提问模版（避免含糊）
| 场景 | 问题示例 | 不该问的模糊写法 |
|------|----------|------------------|
| 查询聚合行为 | “TaskAggregate.pause() 的不变式有哪些？” | “暂停怎么做？” |
| 查询状态机 | “FAILED → ROLLING_BACK 的前置校验是什么？” | “回滚需要什么条件？” |
| 执行机内部 | “TaskExecutor 在捕获 Stage 异常后调用的域服务顺序？” | “异常怎么处理？” |
| Checkpoint | “重试时如何计算 startIndex？” | “怎么恢复？” |
| 事件模型 | “TaskStageFailedEvent 与 TaskFailedEvent 触发点差异？” | “失败事件有什么？” |

---
## 5. 常见误区与纠正语句
| 误区 | 纠正语句 |
|------|----------|
| 认为 Plan 包含 Task 对象 | “Plan 仅维护 TaskId 列表，不直接持有 TaskAggregate。” |
| 认为使用了关系数据库 | “无 RDBMS；运行态与断点分别由内存与 Redis 支撑。” |
| 认为可以中途打断一个 Stage | “Stage 是原子执行单元，只能在 Stage 边界暂停。” |
| 认为事件自动可靠投递 | “当前事件发布为直接调用发布器，缺少持久化与重试机制。” |
| 混淆重试与回滚 | “重试继续未完成阶段；回滚逆序处理已完成阶段。” |

---
## 6. 关键文件索引引用（辅助 AI 继续深挖）
| 分类 | 目标文件 | 用途 |
|------|----------|------|
| 聚合实现 | `domain/task/TaskAggregate.java` | 任务生命周期、Stage 行为与事件收集 |
| 聚合实现 | `domain/plan/PlanAggregate.java` | 计划生命周期与任务 ID 管理 |
| 状态枚举 | `domain/task/TaskStatus.java` / `domain/plan/PlanStatus.java` | 状态机枚举定义 |
| 执行入口 | `infrastructure/execution/TaskExecutor.java` | 核心编排逻辑（执行/暂停/重试）|
| 编排层 | `application/orchestration/TaskExecutionOrchestrator.java` | 并发与线程池调度 |
| Checkpoint | `application/checkpoint/CheckpointService.java` | 断点保存/加载/清理接口 |
| 冲突协调 | `application/conflict/TenantConflictCoordinator.java` | 租户冲突协调（应用层） |
| 冲突管理 | `infrastructure/scheduling/TenantConflictManager.java` | 租户并发互斥（底层锁：内存/Redis） |
| 视图文件 | `docs/views/process-view.puml` | 状态机 / 执行 / 重试 / 暂停 / Stage 内部 |
| 架构总纲 | `docs/architecture-overview.md` | 全局原则与索引 |

---
## 7. 单轮回答格式建议（供 AI 遵循）
```
请按以下结构回答：
[背景确认]：复述核心输入关键字段（不超过 3 行）
[直接结论]：是否可行 / 是否存在风险
[分解步骤]：编号列出 3~7 步逻辑
[边界与限制]：指出操作中不可突破的架构边界
[后续建议]：列出 1~3 个可选改进，不写废话
```

---
## 8. 后续扩展占位（未来追加）
| 主题 | 说明 | 状态 |
|------|------|------|
| 指标与可观测性 Prompt | 统一规范指标查询与报警分析问法 | ✅ 已补充 (T-008) |
| 分布式一致性 Prompt | 引入事件总线与锁后更新此章节 | 待补充 |
| 回滚策略优化 Prompt | 细化回滚失败补偿与二阶段确认 | 待补充 |

---
## 9. 深度分析场景（T-008 新增）

### 9.1 性能瓶颈分析
```
场景：Task 执行耗时明显超出预期（如单租户耗时从 30s 上升到 2min）。
分析步骤：
1. 列出可能的瓶颈点（Stage 内 Step 耗时 / 心跳调度器线程竞争 / Checkpoint 序列化 / Redis 网络延迟）
2. 指出需要收集的指标（stage_duration / task_duration / checkpoint_save_time）
3. 提供排查优先级（先检查 Stage 内 Step 是否阻塞 → Redis 连接池 → 心跳频率）
4. 给出典型优化建议（减少 Checkpoint 保存频率 / 增加心跳间隔 / 异步化非关键 Step）
要求：基于现有架构，不引入新组件。
```

### 9.2 失败链路追踪
```
场景：Task 进入 FAILED 状态，需要追踪从 Stage 失败到事件发布的完整路径。
需要输出：
1. 失败触发点（哪个 Stage/Step 抛出异常）
2. 异常传播路径（TaskExecutor.execute → Stage.execute → failStage → failTask）
3. 涉及的状态转换（RUNNING → FAILED）
4. 生成的事件序列（TaskStageFailedEvent → TaskFailedEvent）
5. Checkpoint 保存时机与内容
6. FailureInfo 分类（ErrorType 与 details）
格式：按时间轴展开，标注关键方法与数据变化。
```

### 9.3 状态漂移诊断
```
场景：多实例部署后，发现同一租户任务在不同实例上重复执行（租户锁失效）。
诊断方向：
1. 确认当前租户锁实现（TenantConflictManager 是内存 InMemory 还是分布式 Redis 锁）
2. 检查 TenantConflictCoordinator 的冲突检测逻辑（Plan 创建前、Task 执行前）
3. 检查租户锁的释放时机（Task 完成、失败、取消后）
4. 排查是否有锁泄漏（未释放）或死锁（锁超时未续租）
5. 提出快速止血方案（回退单实例 / 确认 Redis 锁配置）
6. 长期方案（锁版本化 + 心跳续租 + 自动过期清理）
```

---
## 10. 分模块深度诊断模板（T-008 新增）

### 10.1 执行机诊断
```
问题：TaskExecutor 卡住不前进 / 心跳停止 / Checkpoint 未保存。
检查清单：
□ TaskStatus 是否仍为 RUNNING
□ stageProgress.getCurrentStageIndex() 是否递增
□ HeartbeatScheduler.isRunning() 是否为 true
□ 最后一次 TaskProgressEvent 时间戳
□ 当前 Stage 是否超时（对比 stage_duration 历史数据）
□ Redis 连接是否正常（尝试手动 get checkpoint key）
□ 线程池状态（active threads / queue size）
排查步骤：
1. 日志过滤 "[TaskExecutor]" 关键字，定位最后执行的 Stage
2. 检查该 Stage 的 StageStep 列表，逐个确认是否完成
3. 若卡在某个 Step，检查该 Step 的外部依赖（HTTP 超时 / Redis 阻塞）
```

### 10.2 领域模型诊断
```
问题：聚合方法抛出 IllegalStateException / 状态转换被拒绝。
检查清单：
□ 当前状态与目标状态是否在合法转换矩阵中（参考 state-management.md 第3/4节）
□ 前置条件是否满足（如 complete() 要求 stageProgress.isCompleted()）
□ 是否跳过了 StateTransitionService 前置校验
□ 领域事件列表是否正常（getDomainEvents() 不为空则未发布）
□ 值对象是否正确构造（RetryPolicy / StageProgress / TimeRange）
排查步骤：
1. 定位抛出异常的聚合方法（如 TaskAggregate.complete()）
2. 检查该方法的不变式守卫代码（validateCanCompleteStage 等）
3. 打印当前状态字段（status / stageProgress / retryPolicy）
4. 对照 domain-model.md 不变式列表，确认哪个条件未满足
```

### 10.3 持久化诊断
```
问题：Checkpoint 读取为 null / 写入失败 / 重试未跳过已完成阶段。
检查清单：
□ Redis key 是否存在（redis-cli: EXISTS executor:ckpt:{taskId}）
□ TTL 是否过期（redis-cli: TTL executor:ckpt:{taskId}）
□ JSON 反序列化是否成功（检查 Jackson 异常日志）
□ lastCompletedStageIndex 是否符合预期（>= 0 且 < totalStages）
□ completedStageNames 列表是否与实际匹配
□ 本地缓存是否污染（RedisCheckpointRepository.cache）
排查步骤：
1. 手动读取 Redis key（redis-cli: GET executor:ckpt:{taskId}）
2. 校验 JSON 结构完整性（timestamp / lastCompletedStageIndex / completedStageNames）
3. 对比聚合内 stageProgress 与 Checkpoint 数据是否一致
4. 检查保存时机日志（"Checkpoint saved" 关键字）
```

### 10.4 状态管理诊断
```
问题：状态转换逻辑混乱 / 终态任务仍可操作 / 重试未生效。
检查清单：
□ 当前状态是否为终态（isTerminal() 返回 true）
□ 操作前是否调用 canTransition（StateTransitionService）
□ 重试时 RetryPolicy.canRetry() 返回值
□ 回滚时是否有 prevConfigSnapshot
□ 暂停时 pauseRequested 是否设置且在 Stage 边界检测
排查步骤：
1. 打印当前 TaskStatus / PlanStatus
2. 查阅 state-management.md 转换矩阵，确认目标状态是否合法
3. 检查前置条件代码（如 retry() 检查 status == FAILED || status == ROLLED_BACK）
4. 查看最近一次状态转换事件（TaskStartedEvent / TaskPausedEvent）
```

---
## 11. 影响评估工作流（T-008 新增）

### 11.1 修改评估模板
```
拟修改：{描述修改，如"将 Stage 边界检测从完成后改为开始前"}
评估维度：
1. 受影响的聚合方法：{列出方法名}
2. 受影响的状态转换：{列出转换路径}
3. 受影响的事件：{新增/变更/废弃}
4. 不变式冲突风险：{是否破坏现有不变式}
5. 向后兼容性：{已有 Checkpoint 是否仍可用}
6. 测试覆盖要求：{需要新增哪些测试用例}
7. 文档更新范围：{列出需要同步更新的文档}
8. 回滚方案：{若修改失败如何恢复}
```

### 11.2 新增功能评估模板
```
拟新增功能：{如"支持 Stage 内部进度子事件"}
评估维度：
1. 扩展点选择：{新增接口 / 继承现有 / 修改核心}
2. 依赖注入方式：{构造函数 / Spring Bean / 工厂}
3. 事件模型扩展：{新增事件类型与层次}
4. 状态机影响：{是否引入新状态}
5. Checkpoint 扩展：{需要持久化哪些新字段}
6. 演进路径：{MVP → 完整版分几步}
7. 风险点：{性能 / 复杂度 / 一致性}
8. 退化方案：{功能开关 / 降级逻辑}
```

---
## 12. 多粒度查询示例（T-008 新增）

### 12.1 概览级查询
```
"列出当前架构的所有聚合根、状态枚举数量、核心值对象清单。"
"总结 Plan 与 Task 的生命周期差异（3 句话以内）。"
"哪些设计文档已完成，哪些待补充？"
```

### 12.2 细节级查询
```
"TaskAggregate.retry(fromCheckpoint) 的完整执行路径，包含状态校验、Checkpoint 加载、Stage 跳过逻辑、事件触发顺序。"
"协作式暂停为何必须在 Stage 边界应用？列出技术原因与业务约束。"
"Redis Checkpoint 的 TTL 策略与并发写保护机制当前状态与改进计划。"
```

### 12.3 代码级查询
```
"TaskExecutor.execute() 方法中如何判断是否进入错误分支？给出关键 if 判断与异常捕获代码位置。"
"StateTransitionService.canTransition() 内部如何校验 FAILED → RUNNING 转换？列出判断逻辑伪代码。"
"RedisCheckpointRepository.put() 的序列化失败处理流程与异常类型。"
```

---
## 13. 使用示例（综合应用）

### 示例 A：性能优化全流程
```
当前问题：单租户任务平均耗时从 30s 上升到 90s。
Step 1: 使用"性能瓶颈分析"场景 Prompt 生成排查清单
Step 2: 使用"执行机诊断"模板定位卡顿 Stage
Step 3: 使用"持久化诊断"检查 Checkpoint 保存耗时
Step 4: 使用"影响评估"模板评估优化方案（如减少 Checkpoint 频率）
Step 5: 参考 execution-engine.md 第11节风险路线图确认长期改进
```

### 示例 B：状态转换异常排查
```
错误信息：IllegalStateException: 只有 RUNNING 状态才能完成
Step 1: 使用"状态管理诊断"模板检查当前状态与目标状态
Step 2: 查阅 state-management.md 第4节 Task 转换矩阵
Step 3: 使用"领域模型诊断"模板确认不变式守卫逻辑
Step 4: 使用"失败链路追踪"场景重现异常传播路径
Step 5: 提出修复方案（如补充前置状态检查）
```

---
## 14. 维护指引（更新）
更新本文件时：
1. 不新增"物理部署"相关内容。
2. 新增 Prompt 模板必须基于现有架构能力（不预支未来实现）。
3. 影响评估模板需与 DoD 标准对齐（domain-model / execution-engine / state-management）。
4. 多粒度查询示例应覆盖新增的设计文档。
5. T-008 新增章节（9~13节）与原有章节（1~8节）形成互补：原有侧重基础与通用，新增侧重深度与诊断。

---
## 15. Changelog（更新）
| 日期 | 版本 | 说明 |
|------|------|------|
| 2025-11-23 | 0.2 | T-008: 新增深度分析场景、分模块诊断、影响评估工作流、多粒度查询示例 |
| 2025-11-23 | 0.1 | 初始占位扩展为多段 Prompt 模版 |

---
> T-008 增强完成后，architecture-prompt 与 onboarding-prompt 形成完整入门+深度体系。
