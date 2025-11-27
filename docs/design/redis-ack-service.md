# Redis ACK 服务设计文档

**文档版本**: 2.0  
**创建日期**: 2025-11-24  
**最后更新**: 2025-11-28 (T-030 VersionTag 重构)  
**任务编号**: T-019, T-030  
**状态**: 已实现

---

## 1. 概述

### 1.1 背景

在分布式系统中，配置更新通常遵循以下模式：
1. 配置中心将配置写入 Redis
2. 通过 Pub/Sub 通知各个服务实例
3. 服务实例读取新配置并应用
4. **配置中心需要验证配置是否已生效**

当前 `ObServiceStageAssembler` 实现了部分逻辑（Write + Polling），但存在以下问题：
- **缺少 Pub/Sub 通知机制**：服务不知道配置已更新
- **与 deploy 模块耦合**：逻辑嵌入在 Stage Assembler 中，无法复用
- **顺序混乱**：Polling 在 Write 之前（实际是前置检查，不是 ACK）

### 1.2 设计目标

构建一个**通用、独立、可扩展**的 Redis ACK 服务。关键设计点：
- 标准化三阶段：Write → Publish → Verify（顺序固定，全部必经）
- 流式链式 API（易读、语义明确）
- Footprint 抽象（版本号/摘要/外部ID等灵活提取与比对）
- 扩展点体系（提取器、端点、重试策略、消息模板、操作类型）
- 与业务层解耦（不内置业务前置检查如 Agent 就绪）
- 可选指标与健康检查，不侵入核心流程

### 1.3 适用边界

适用于“配置写入后需要确认已经被客户端采纳”的所有跨进程传播场景；不负责：
- 业务端自身的就绪判断（如 Agent 启动检测）
- 配置内容的业务语义合法性校验（由业务端完成）
- 推送失败的二级补偿（可由上层编排策略接管）

---

## 2. 核心概念

### 2.1 标准执行流程

```
┌───────────────────────────────┐
│ Redis ACK Service            │
├───────────────────────────────┤
│ Write → Publish → Verify      │
│                               │
│ Write:  数据写入 + Footprint  │
│ Publish: Pub/Sub 通知         │
│ Verify:  轮询端点比对 Footprint │
└───────────────────────────────┘
```
### 2.2 VersionTag 设计（原 Footprint，T-030 重命名）

- **定义**：能唯一标识一次配置版本/目标状态的最小值。
- **形式**：字符串（如 `v2.1.0`、MD5 摘要、任务ID）。
- **双向提取**：
  - 写入侧：从 value 或指定 field 构造 expectedVersionTag
  - 验证侧：从响应构造 actualVersionTag
- **判定**：`expectedVersionTag.equals(actualVersionTag)`（第一版采用精确匹配，语义扩展留扩展点）
### 2.2 Footprint 设计
**术语演进**（T-030）：
- ✅ `Footprint` → `VersionTag`（语义更明确，不易混淆）
- ✅ `FootprintExtractor` → `VersionTagExtractor`
- ⚠️ 旧术语标记 `@Deprecated`，保持向后兼容
| WriteStage | 组装 Redis 操作（单字段/多字段）与 VersionTag 提取 | 业务前置校验 |
**多字段支持**（T-030 新增）：
| VerifyStage | 轮询端点并比对 VersionTag | 对客户端应用逻辑做诊断 |
| Extractor | 提取/计算 VersionTag（支持 JsonPath 深层提取） | 判定策略（只返回值） |
  - 从指定 field 的值提取 versionTag（如从 `metadata` 字段的 JSON 中提取 `$.version`）
  - 从所有 fields 计算组合签名（如 MD5(field1+field2+field3)）
- 判定：`expectedFootprint.equals(actualFootprint)`（第一版采用精确匹配，语义扩展留扩展点）

### 2.3 角色划分

| 角色 | 职责 | 非职责 |
|------|------|--------|
| WriteStage | 组装 Redis 操作与 Footprint 提取 | 业务前置校验 |
| PubSubStage | 通知客户端有新版本可拉取 | 保证客户端一定接收（允许丢失，由验证兜底） |
| VerifyStage | 轮询端点并比对 Footprint | 对客户端应用逻辑做诊断 |
| Extractor | 提取/计算 Footprint | 判定策略（只返回值） |
| RetryStrategy | 决定重试节奏 | 业务超时策略升级/降级决策 |
| AckEndpoint | 查询外部状态 | 写入 Redis / 发布消息 |

### 2.4 失败类型

| 类型 | 说明 | 处理方式 |
|------|------|----------|
| TIMEOUT | 在总超时时间内未匹配到 Footprint | 返回 AckResult 标记失败，由上层判定重试/回滚 |
| MISMATCH | 得到响应但 Footprint 不一致 | 提前失败，提高反馈速度 |
| ERROR | 网络/序列化/端点异常 | 重试策略决定是否继续，最终汇总为 ERROR |

---

## 3. API 设计概要
| WriteStageBuilder | key/hashKey/value/ttl/versionTag/operation | 构建写入与 VersionTag 提取策略 |
| HashFieldsBuilder | field/fields/versionTagFromField/versionTagFromFields | 多字段写入构建器（T-030 新增） |
### 3.1 入口

```java
**多字段 API 示例**（T-030 新增）：
```java
// 单字段模式（向后兼容）
redisAckService.write()
    .hashKey("deployment:tenant:123", "config")
    .value(configJson)
    .versionTag("v2.1.0")
    .executeAndWait();

// 多字段模式（新功能）
### 3.4 VersionTag 提取接口
    .hashKey("deployment:tenant:123")
        .field("config", configJson)
        .field("metadata", metadataJson)
public interface VersionTagExtractor {
    String extractTag(Object value) throws VersionTagExtractionException;
    .andPublish()
        .topic("updates")
        .message("配置已更新")
预置实现：
- **JsonFieldVersionTagExtractor**：从 JSON 字符串提取字段（支持 JsonPath，如 `$.metadata.version`）
### 3.5 AckResult 数据结构
- **RegexVersionTagExtractor**：正则表达式匹配提取
字段：
- `success`: 是否成功
- `expectedVersionTag` / `actualVersionTag`: 版本标签对比
- `attempts`: 重试次数
- `elapsed`: 耗时
- `reason`: 失败原因枚举
- `error`: 异常信息

| VersionTag | 提取/计算 | JsonFieldVersionTag / Function / Regex / Digest | 支持 JsonPath 深层提取（T-030） |
| 多字段 | 字段级提取 | versionTagFromField / versionTagFromFields | 支持 HMSET 原子写入（T-030） |
- **ExternalIdVersionTagExtractor**：从外部系统获取 ID

| Redis 操作 | 数据结构 | SET / HSET / HMSET / LPUSH / SADD / ZADD | HMSET 支持多字段（T-030） |
- `FootprintExtractor` 接口继承 `VersionTagExtractor`，标记 `@Deprecated`
- 旧代码无需修改，自动桥接到新接口
        .httpGet("http://service/actuator/config")
        .extractJson("$.metadata.version")
        .retryFixedDelay(10, Duration.ofSeconds(3))
    .executeAndWait();
```

RedisAckService service = ...;
service.write() ...
```

### 3.2 分阶段 Builder 接口要点
  → WriteStageBuilder(key,value,versionTag)
    → Redis 写入 (SET/HSET/HMSET/…)
    → 生成 expectedVersionTag
| WriteStageBuilder | key/hashKey/value/ttl/footprint/operation | 构建写入与 Footprint 提取策略 |
| PubSubStageBuilder | topic/message/messageTemplate | 定义通知载荷与发布目标 |
| VerifyStageBuilder | httpGet/httpPost/endpoint/extract*/retry*/timeout/execute* | 定义端点、提取、重试与执行方式 |

### 3.3 重试策略接口

```java
@FunctionalInterface
public interface RetryStrategy {
    Duration nextDelay(int attempt, Throwable lastError, AckContext context); // null = 停止
}
```

预置：FixedDelay / ExponentialBackoff / Custom（函数式包装）

### 3.4 Footprint 提取接口

```java
@FunctionalInterface
public interface FootprintExtractor {
    String extract(Object value) throws FootprintExtractionException;
}
```

预置：JsonField / Function / Regex / Digest / ExternalId（Digest/ExternalId 当前留接口，后续扩展）

### 3.5 AckResult 数据结构（精简）

字段：success / expectedFootprint / actualFootprint / attempts / elapsed / reason / error
| VersionTag 比较语义 | 精确匹配 | 保持确定性，语义扩展（≥、前缀）可后续增加 |
---

| Redis 操作支持范围 | 五类常用结构 + HMSET | 覆盖 80% 场景，HMSET 支持多字段原子写入（T-030） |
| 术语命名 | VersionTag 替代 Footprint | 语义更明确，降低理解成本（T-030） |

| 类别 | 扩展点 | 预置实现 | 备注 |
|------|--------|----------|------|
| Footprint | 提取/计算 | JsonField / Function / Regex | Digest/ExternalId 可后续补充 |
| Endpoint | 查询来源 | HttpGet / HttpPost / Custom | Pub/Sub ACK 留未来版本 |
| Retry | 时序策略 | FixedDelay / ExponentialBackoff / Custom | 支持最大延迟封顶 |
| Redis 操作 | 数据结构 | SET / HSET / LPUSH / SADD / ZADD | ZADD 需 score 参数 |
| 消息模板 | 模板替换 | 基于 {field} 占位符 | Map 值驱动替换 |
| 指标 | 执行统计 | Executions / Success / Timeout / Mismatch / Error / Duration | 可选，非强制 |
| 健康检查 | 存活探测 | HealthIndicator | 仅报告可用性，不做深度自检 |

设计原则：所有扩展点均基于接口与最小职责；调用路径不出现反射强耦合（除自定义 Endpoint 场景由业务决定）。

---

## 5. 行为与时序模型

redis-ack/
├── ack-api/              // 接口与数据模型
│   ├── VersionTagExtractor.java (新)
│   ├── FootprintExtractor.java (@Deprecated, 桥接)
│   ├── HashFieldsBuilder.java (新, T-030)
│   └── ...
├── ack-core/             // 内部执行与 Builder 实现
│   ├── JsonFieldVersionTagExtractor.java (重命名)
│   ├── HashFieldsBuilderImpl.java (新, T-030)
│   └── ...
├── ack-spring/           // Spring Boot 集成
│   └── autoconfigure/
└── ack-examples/         // 使用示例
  → VerifyStageBuilder(endpoint,extract,retry)
    → loop {
         response = endpoint.query()
         actual = extractor(response)
         if actual == expected → success
| VersionTag 语义扩展 | 支持语义版本比较 / 前缀匹配 | 更灵活的兼容升级策略 |
| JsonPath 深层提取 | 支持 $.field1.field2 语法（T-031 计划中） | 嵌套 JSON 字段提取 |
         else if 重试耗尽 → mismatch/error
       }
  → 返回 AckResult
| 自定义判定策略 | VersionTag + 业务谓词组合 | 支持更复杂一致性定义 |

### 5.2 重试决策时序（抽象）

```
Attempt N → nextDelay = strategy.nextDelay(N,error,context)
         → null => stop
| VersionTag | 用于判定同步是否完成的版本/标识值（原 Footprint，T-030 重命名） |
```
| Verify | 轮询端点并比对 VersionTag 的动作 |
---

| HMSET | Redis 多字段原子写入操作（T-030 新增支持） |

---
## 12. 设计边界与不做事项
## 11. T-030 重构总结

### 术语重命名
- ✅ `Footprint` → `VersionTag`（更准确的语义）
- ✅ `FootprintExtractor` → `VersionTagExtractor`
- ✅ 保持完美向后兼容（旧接口标记 @Deprecated 并桥接）

### 多字段支持
- ✅ **HMSET 原子写入**：一次写入多个 fields
- ✅ **字段级提取**：从指定 field 的值中提取 versionTag
## 13. 设计完整性自检清单
- ✅ **HashFieldsBuilder**：流式 API 构建多字段写入

### 技术亮点
- **零破坏性变更**：现有代码无需修改即可编译
| VersionTag 提取与比对路径单一且可替换 | ✅ |
| 多字段 HMSET 支持（T-030） | ✅ |
- **惰性迁移**：旧 API 仍可用，逐步迁移到新 API
## 6. 关键设计取舍

| 议题 | 方案 | 取舍理由 |
## 12. 设计边界与不做事项
| 向后兼容性保证（T-030） | ✅ |
| Pub/Sub 是否阻塞确认 | 非阻塞（发送后即进入验证） | 避免客户端消费延迟拖长整体 ACK 周期 |
| 验证方式 | 轮询端点 | 简化首版，实现确定性判定；后续可补订阅式 ACK |
| Footprint 比较语义 | 精确匹配 | 保持确定性，语义扩展（≥、前缀）可后续增加 |
| 错误与超时区分 | reason 字段枚举化 | 便于指标分桶与上层策略判定 |
| 指标记录位置 | Verify 执行结束统一记录 | 避免多阶段重复统计，保证一次执行一次指标 |
| Redis 操作支持范围 | 五类常用结构 | 覆盖 80% 场景，其余由外部转换后写入 |

---

## 7. 非功能设计要点

| 维度 | 目标 | 机制 |
|------|------|------|
| 可扩展性 | 新增操作/提取/重试不修改核心类 | 基于接口 + Builder 分发 |
| 可靠性 | Pub/Sub 可能丢失仍可确认 | Verify 独立轮询兜底 |
| 性能 | 单次 ACK < 100ms（网络不阻塞） | 轻量对象 + 无阻塞队列 |
| 观测性 | 基本可见性与统计 | 可选 Micrometer 集成 |
| 解耦 | 不依赖业务模型类型 | value 作为 Object + 外部提取器 |

---

## 8. 模块结构（最终）

```
xyz.firestige.infrastructure.redis.ack/
  api/              // 接口与数据模型
  core/             // 内部执行与 Builder 实现
  extractor/        // Footprint 提取实现
  endpoint/         // 端点访问实现
  retry/            // 重试策略实现
  exception/        // 异常层次
  autoconfigure/    // Spring Boot 集成
  metrics/          // 可选指标封装
```

---

## 9. 未来演进方向（仅设计展望）

| 演进 | 描述 | 价值 |
|------|------|------|
| Pub/Sub ACK 回流 | 订阅客户端确认消息替代轮询 | 降低轮询开销，实时性更高 |
| Footprint 语义扩展 | 支持语义版本比较 / 前缀匹配 | 更灵活的兼容升级策略 |
| 批量 ACK | 一次请求校验多个 key | 降低高频配置场景的网络成本 |
| 多租户隔离指标 | 按租户分桶统计成功/失败 | 精细运维与定位异常租户 |
| SPI 端点扩展 | 支持异构客户端（Jedis/Lettuce） | 降低实现绑定，增强接入面 |
| 自定义判定策略 | Footprint + 业务谓词组合 | 支持更复杂一致性定义 |

---

## 10. 术语摘要

| 术语 | 定义 |
|------|------|
| Footprint | 用于判定同步是否完成的版本/标识值 |
| Publish | Redis Pub/Sub 消息广播（通知可拉取） |
| Verify | 轮询端点并比对 Footprint 的动作 |
| RetryStrategy | 控制下一次验证延迟及终止时机的策略 |
| Endpoint | 暴露当前实际配置状态的外部服务接口 |

---

## 11. 设计边界与不做事项

| 项目 | 不做原因 |
|------|----------|
| 业务前置健康检查 | 属于业务编排职责，放在 deploy 层保持解耦 |
| 客户端强制推送/重试补偿 | ACK 只关心确认结果，补偿策略由上层决定 |
| 持久化执行记录 | 首版不做审计，减少复杂度；可通过指标侧间接统计 |
| 多阶段状态机 | 单线性三阶段足够表达语义，避免过度建模 |

---

## 12. 设计完整性自检清单

| 检查项 | 状态 |
|--------|------|
| 三阶段职责边界清晰 | ✅ |
| Footprint 提取与比对路径单一且可替换 | ✅ |
| 重试策略与业务无耦合（纯时间逻辑） | ✅ |
| 端点抽象不依赖具体协议（HTTP 只是实现之一） | ✅ |
| Redis 操作枚举不侵入外部值结构 | ✅ |
| 指标集成可选且不影响主链路 | ✅ |
| 设计与现有 deploy 模块耦合点已剥离 | ✅ |

---

**结束**
