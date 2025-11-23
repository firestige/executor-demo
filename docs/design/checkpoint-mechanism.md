# Checkpoint 机制设计（Checkpoint Mechanism）

> 任务: T-005 派生  
> 状态: 初稿  
> 最后更新: 2025-11-23

---
## 1. 目标
细化任务执行断点（Checkpoint）的数据结构、保存/恢复流程、并发与版本化策略可选方案，为后续可靠重试与暂停恢复增强奠定基础。

---
## 2. 数据结构规范
当前 TaskCheckpoint Java 类：
```
class TaskCheckpoint {
  int lastCompletedStageIndex;
  List<String> completedStageNames;  // 顺序执行完成的 Stage 名称
  Map<String,Object> customData;     // 预留上下文扩展，如租户配置快照片段
  LocalDateTime timestamp;           // 保存时间
}
```
扩展建议字段（未来）：
| 字段 | 类型 | 说明 | 状态 |
|------|------|------|------|
| version | long | 覆盖写版本号（CAS） | 计划中 |
| executorInstance | String | 保存该断点的实例标识 | 计划中 |
| stageDurations | Map<String,Long> | 单阶段耗时累计 | 可选 |
| failureHistory | List<FailureInfo> | 历史失败记录链 | 可选 |

---
## 3. 保存流程（Stage 边界）
| 步骤 | 说明 |
|------|------|
| 1. 收集数据 | 从 TaskAggregate 读取 stageProgress、stageResults（已完成阶段名称）|
| 2. 构造对象 | new TaskCheckpoint() 设置 lastCompletedStageIndex + completedStageNames + timestamp |
| 3. 序列化 | Jackson (JavaTimeModule) → JSON bytes |
| 4. 写入 | Redis: SET key value PX=ttl （覆盖旧值）|
| 5. 本地缓存 | 写入 ConcurrentHashMap 加快后续读取 |

Checkpoint 保存触发时机：Stage 成功、Stage 失败、暂停应用、取消、重试前（可选覆盖）、回滚前（可选）。

---
## 4. 恢复流程（重试/恢复）
| 步骤 | 说明 |
|------|------|
| 1. 读取 | RedisCheckpointRepository.get(taskId)（缓存命中则直接返回）|
| 2. 校验 | lastCompletedStageIndex ∈ [0, totalStages-1]；completedStageNames 长度与 lastCompletedStageIndex +1 一致 |
| 3. 计算起点 | startIndex = lastCompletedStageIndex + 1 |
| 4. 跳过阶段 | Executor 跳过已完成阶段列表，继续后续阶段 |
| 5. 补偿进度事件 | 重试场景首次心跳前发布一次 TaskProgressEvent（未来可统一）|

---
## 5. 版本化与并发控制（未来增强）
| 问题 | 现状 | 风险 | 增强策略 |
|------|------|------|----------|
| 覆盖写无版本 | 最后写胜出 | 理论上可能回退（低概率） | 添加 version 字段 + CAS 校验（Lua 脚本）|
| 多执行实例争抢 | 单实例设计 | 多实例未来引入分布式锁 | Redis 锁 + version 双重保护 |
| 阶段结果增量 | 全量写入 | 大型阶段列表写放大 | Hash 存储阶段集合（HSET append）|

CAS 脚本示例（草案）：
```
if redis.call('EXISTS', KEYS[1]) == 0 then
  redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
  redis.call('HSET', KEYS[2], 'version', ARGV[1])
  return 1
end
local current = redis.call('HGET', KEYS[2], 'version')
if tonumber(current) < tonumber(ARGV[1]) then
  redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
  redis.call('HSET', KEYS[2], 'version', ARGV[1])
  return 1
end
return 0
```

---
## 6. 键空��设计（详细）
| Key | 类型 | 示例 | 说明 |
|-----|------|------|------|
| executor:ckpt:{taskId} | String | executor:ckpt:task-123 | 断点 JSON 主体 |
| executor:ckpt:meta:{taskId} | Hash | executor:ckpt:meta:task-123 | 元信息：version、instanceId 等 |

命名扩展模式：`{env}:{product}:{category}:{entity}:{id}` → `prod:executor:ckpt:meta:task-123`。

---
## 7. 断点有效性校验规则
| 规则 | 条件 | 失败处理 |
|------|------|----------|
| 索引范围 | 0 ≤ lastCompletedIndex < totalStages | 丢弃断点 + 从头执行 |
| 阶段列表长度 | completedStageNames.size == lastCompletedIndex + 1 | 丢弃断点 + 从头执行 |
| 阶段名称一致性 | completedStageNames 中名称均在当前 Plan/Task 配置阶段集合中 | 丢弃断点 + 从头执行 |
| 时间戳新鲜度 | timestamp 距现在 > TTL 则应已过期 | 视为无效 |
| 版本递增（未来） | newVersion > storedVersion | 否则忽略写入 |

---
## 8. 异常与降级处理
| 场景 | 异常 | 策略 |
|------|------|------|
| 序列化失败 | RuntimeException | 标记任务失败（可细分 ErrorType.PERSISTENCE）|
| Redis 连接失败 | ConnectionException | 降级为 InMemoryCheckpointRepository（记录非持久）|
| 版本 CAS 失败 | 返回 0 | 重试或丢弃（视场景）|
| 校验失败 | 校验条件不满足 | 丢弃断点并记录一次 WARN |

---
## 9. 与执行机交互钩子
| 时机 | 执行机动作 | Checkpoint 行为 |
|------|------------|----------------|
| Stage 成功后 | completeStage() | recordCheckpoint() + put() |
| Stage 失败后 | failStage(result) | recordCheckpoint() + put() |
| 暂停应用 | applyPauseAtStageBoundary() | recordCheckpoint() + put() |
| 重试启动 | resumeTask()/retry() 前 | get() + restoreFromCheckpoint() |
| 任务完成 | complete() | clearCheckpoint() + remove() |
| 回滚完成 | completeRollback() | clearCheckpoint() + remove() |

---
## 10. Definition of Done（Checkpoint 设计）
| 项目 | 标准 |
|------|------|
| 数据结构 | 当前与扩展字段定义清晰 |
| 保存/恢复流程 | 步骤化拆解 + 起点计算规则 |
| 版本化策略 | 提供未来 CAS 方案草案 |
| 键空间 | 主键与元信息键模板明确 |
| 校验规则 | 全部列出 + 失败处理策略 |
| 交互钩子 | 与执行机每个关键时机对齐 |

---
> 后续：版本化落地后更新 Redis Lua 脚本与异常分类，并调整 persistence.md 对应章节。

