# 持久化与运行态设计（Persistence & Runtime State）

> 任务: T-005  
> 状态: 初稿  
> 最后更新: 2025-11-23

---
## 1. 范围
描述执行模块内部“持久化”与“运行态”数据的存储策略：聚合内存驻留、Checkpoint Redis 持久化、事件暂存与发布、租户冲突管理、无 RDBMS 前提下的一致性与隔离策略。

---
## 2. 数据分类
| 分类 | 数据示例 | 生命周期 | 存储介质 | 访问模式 | 是否跨实例共享 | 一致性级别 |
|------|----------|----------|----------|----------|----------------|------------|
| 聚合状态 | PlanAggregate / TaskAggregate 字段 | 任务执行期 | JVM Heap (InMemoryRepository) | 读多写少 | 否 | 单实例强一致 |
| 阶段结果 | List<StageResult> | 任务执行期 | 聚合内部列表 | 顺序追加 | 否 | 与任务生命周期同步 |
| Checkpoint | TaskCheckpoint(lastCompletedIndex, completedStageNames) | 执行失败/暂停时 | Redis (RedisCheckpointRepository) + 本地缓存 | 覆盖写，单 key | 是 | Redis 最终一致（TTL）|
| 重试策略 | RetryPolicy | 执行期 | 聚合内部值对象 | 不可变替换 | 否 | 单实例强一致 |
| 进度快照 | Heartbeat 发布的 TaskProgressEvent | 周期（10s） | 事件总线（同步调用） | 发布→消费 | 可广播 | 最终一致（消费者自定义）|
| 租户冲突锁 | runningTenants Map | Task RUNNING 范围内 | ConcurrentHashMap | put/remove | 否（当前）| 本地一致；多实例潜在冲突 |

---
## 3. 仓储接口与实现
### 3.1 设计原则
- 聚合不直接耦合底层存储（DDD Repository 模式）
- 简化接口：save/find/remove + 语义化查询，避免泛化 CRUD
- InMemory 实现作为运行期缓存与测试替身

### 3.2 CheckpointRepository
| 方法 | 语义 | Redis 实现 | InMemory 实现 |
|------|------|------------|--------------|
| put(taskId, checkpoint) | 覆盖写入当前检查点 | set(namespace+taskId, JSON, TTL) + 本地缓存 | Map.put |
| get(taskId) | 获取检查点 | 读本地缓存→回源 Redis | Map.get |
| remove(taskId) | 删除检查点 | del(namespace+taskId) + 缓存失效 | Map.remove |

Redis Key 规范：`executor:ckpt:{taskId}`  （可通过 namespace 前缀定制，如 `prod:executor:ckpt:`）。
TTL 策略：默认 7d，可配置。过期后自动清理，避免历史断点堆积。

### 3.3 聚合仓储（示意，未展示全部代码）
| 仓储 | 实现类 | 存储结构 | 线程安全 | 说明 |
|------|--------|----------|----------|------|
| PlanRepository | InMemoryPlanRepository | ConcurrentMap<PlanId, PlanAggregate> | 是 | 单实例内存驻留 |
| TaskRepository | InMemoryTaskRepository | ConcurrentMap<TaskId, TaskAggregate> | 是 | 单实例内存驻留 |
| TaskRuntimeRepository | InMemoryTaskRepository / Composite | 同 TaskRepository 或扩展 | 是 | 运行时属性拆分（未来扩展）|

不引入持久化 DB，实例重启即失去聚合状态（需上层保证可重构或任务重新创建）。

---
## 4. Redis Checkpoint 实现细节
| 关注点 | 说明 |
|--------|------|
| 序列化 | Jackson + JavaTimeModule，禁用时间戳序列化，直接写 ISO 日期 |
| 覆盖写 | put() 始终覆盖旧值，不做增量合并（简化并发）|
| 本地缓存 | ConcurrentHashMap 作为 read-through 缓存，减少重复反序列化 |
| 异常处理 | 序列化/反序列化失败抛 RuntimeException（可后续改为自定义异常）|
| 并发风险 | 多执行线程同时保存会最后写覆盖，无版本控制（风险低：Stage 边界保存）|
| 删除语义 | remove() 用于任务完成/终止后清理断点数据 |

---
## 5. 一致性与隔离分析
| 场景 | 一致性策略 | 风险 | 处理/缓解 |
|------|------------|------|-----------|
| 任务失败后重试 | 从 Redis 读取最新 Checkpoint | 覆盖导致阶段列表回退 | Stage 索引写入前后逻辑规避回退（单调）|
| 暂停后恢复 | 读取 lastCompletedIndex + completedStages | 并发保存覆盖 | 暂停只在 Stage 边界，单写点 |
| 心跳与断点同时发生 | 两者独立（心跳不写 Checkpoint） | 无 | - |
| 多实例并发执行同租户 | 本地租户锁不共享 | 冲突执行 | 未来引入 Redis 锁 |
| Redis 失效 | 读取失败返回 null | 重试时丢失断点 | 回退从头执行（可接受降级）|

---
## 6. 键空间与命名规范
| 类型 | Key 模板 | 示例 | 说明 |
|------|----------|------|------|
| Checkpoint | executor:ckpt:{taskId} | executor:ckpt:task-123 | 断点 JSON 序列化对象 |
| 可扩展（未来） | executor:lock:tenant:{tenantId} | executor:lock:tenant:t-001 | 分布式租户锁占位 |
| 可扩展（未来） | executor:metrics:{counter} | executor:metrics:task_failed | 简易计数器或统计缓存 |

命名策略：`{env?}:{product}:{category}:{entity}:{id}` 可在未来扩展（当前不强制）。

---
## 7. 失败与降级策略
| 场景 | 策略 | 影响范围 |
|------|------|----------|
| Redis 不可用 | 回退 InMemoryCheckpointRepository（标记为非持久） | 重试/暂停恢复可能失效 |
| 反序列化失败 | 抛异常终止当前操作 | 任务失败，触发 TaskFailedEvent |
| 覆盖竞争 | 最后写胜出 | 轻微：阶段边界写入频率低 |
| TTL 过期 | 断点自动清除 | 重试时从头开始执行 |

---
## 8. 演进与改进计划
| 改进项 | 描述 | 优先级 | 方案草案 |
|--------|------|--------|----------|
| Checkpoint 版本化 | 增加 `version` 字段 + CAS 脚本 | P1 | 使用 Redis Lua 校验版本递增 |
| 分布式租户锁 | Redis 锁替代本地 Map | P1 | SET NX + TTL，续租机制 |
| 增量断点 | 仅追加最近阶段，而非全部覆盖 | P2 | 使用 Hash 结构存储阶段集合 |
| 异常分类 | 序列化异常细化为 ERROR_TYPE | P2 | 自定义 PersistenceException + ErrorType 映射 |
| 指标持久化 | 定期汇总指标写入 Redis | P3 | 滑动窗口统计 + 聚合脚本 |

---
## 9. Definition of Done（T-005）
| 条目 | 标准 |
|------|------|
| 数据分类 | 聚合/Checkpoint/锁/事件/进度分类清晰 |
| 仓储接口 | 方法与语义表述完整（put/get/remove）|
| Redis 细节 | Key/TTL/序列化/缓存说明完整 |
| 一致性分析 | 主要场景风险与缓解列出 |
| 演进计划 | 至少 5 项改进建议 + 优先级 |

---
> 后续：在 checkpoint-mechanism.md 中扩展版本化与增量更新策略设计（T-005 派生）。

