# RedisAckService Footprint 设计分析与改进方案

> **分析日期**: 2025-11-27  
> **分析人**: GitHub Copilot  
> **任务**: T-019 Redis ACK 服务设计评审

---

## 📋 执行摘要

经过全面分析 RedisAckService 模块的设计与实现，针对提出的两个异议：

1. ✅ **Footprint 命名问题**: 确实容易混淆，建议改名
2. ✅ **HSET 多字段支持**: 当前设计仅支持单字段，需要扩展以支持多字段写入和灵活的字段级 footprint 提取

---

## 🔍 当前设计分析

### 1. Footprint 概念定义

**来自设计文档** (`docs/design/redis-ack-service.md`):

```markdown
### 2.2 Footprint 设计

- 定义：能唯一标识一次配置版本/目标状态的最小值。
- 形式：字符串（如 `v2.1.0`、MD5 摘要、任务ID）。
- 双向提取：
  - 写入侧：从 value 构造 expectedFootprint
  - 验证侧：从响应构造 actualFootprint
- 判定：`expectedFootprint.equals(actualFootprint)`（第一版采用精确匹配）
```

**语义问题**:
- "Footprint" 直译为"脚印"，在业界更多指代"占用空间"（如内存 footprint、碳足迹）
- 在配置同步场景下，该概念实际表示"版本标识符"或"配置指纹"
- 容易与"配置摘要（digest）"或"版本号（version）"混淆

---

### 2. 当前 HSET 实现机制

#### 2.1 API 层面

**WriteStageBuilder 接口**:
```java
WriteStageBuilder hashKey(String key, String field);  // 单字段
WriteStageBuilder value(Object value);                // 整个值对象
WriteStageBuilder footprint(String fieldName);        // 从 value 提取 footprint
```

**实际执行** (`AckExecutor.java`):
```java
private String writeToRedis(AckTask task, AckContext context) {
    // 1. 从整个 value 对象提取 footprint
    String footprint = task.getFootprintExtractor().extract(task.getValue());
    
    // 2. 序列化整个 value 为字符串
    String valueStr = serializeValue(task.getValue());
    
    // 3. 写入单个 field
    if (operation == RedisOperation.HSET) {
        redisClient.hset(task.getKey(), task.getField(), valueStr);
    }
    
    return footprint;
}
```

#### 2.2 实际使用场景

**BlueGreenStageAssembler 的数据准备**:
```java
// 构建完整的 Redis Value
Map<String, Object> redisValue = new HashMap<>();
redisValue.put("tenantId", config.getTenantId().getValue());
redisValue.put("sourceUnit", extractSourceUnit(config));
redisValue.put("targetUnit", extractTargetUnit(config));
redisValue.put("routes", convertRouteRulesToMap(config));

// 构建 metadata 对象（包含 version 作为 footprint）
Map<String, Object> metadata = new HashMap<>();
metadata.put("version", config.getPlanVersion());
redisValue.put("metadata", metadata);

// Footprint（从 PlanVersion）
String footprint = String.valueOf(config.getPlanVersion());

// 调用 RedisAckService
redisAckService.write()
    .hashKey(redisKey, "icc-bg-gateway")  // 单一 field
    .value(redisValue)                     // 整个 Map 作为 value
    .footprint((obj) -> footprint)         // 从 value 提取（但这里直接传入）
```

**最终 Redis 存储结构**:
```
HSET deployment:tenant:tenant123 icc-bg-gateway '{"tenantId":"tenant123","sourceUnit":"BLUE","targetUnit":"GREEN","routes":[...],"metadata":{"version":"v2.1.0"}}'
```

---

## ⚠️ 当前设计的局限性

### 问题 1: 命名混淆

| 术语 | 当前语义 | 易混淆点 |
|------|----------|---------|
| Footprint | 版本标识符/配置指纹 | 业界更多指"占用空间" |
| Extract | 从对象提取标识 | 与"导出"或"抽取数据"混淆 |
| Verify | 比对标识 | 与"验证合法性"混淆 |

**问题根源**: "Footprint" 在配置同步领域不是标准术语。

---

### 问题 2: HSET 单字段限制

#### 2.1 当前架构

```
WriteStageBuilder
    .hashKey(key, field)  // ← 只能指定一个 field
    .value(Object)        // ← 整个对象作为该 field 的值
```

**限制**:
1. 每次只能写入一个 Hash field
2. 如需写入多个 field，需要调用多次 `write()` 流程（低效且不原子）
3. Footprint 只能从整个 value 对象提取，无法指定"从某个 field 的值中提取"

#### 2.2 实际需求场景

**场景 A: 蓝绿切换配置**
```
HSET deployment:tenant:tenant123
    config         '{"sourceUnit":"BLUE","targetUnit":"GREEN","routes":[...]}'
    metadata       '{"version":"v2.1.0","timestamp":1732665600}'
    status         'ACTIVE'
```

**需求**:
- 一次性写入 3 个 field（原子操作）
- 从 `metadata` field 的值中提取 `version` 作为 footprint
- Verify 时也从端点响应的 `metadata.version` 提取

**场景 B: 多租户配置聚合**
```
HSET config:multi-tenant
    tenant1:routes  '{"routes":[...],"version":"v1.0"}'
    tenant2:routes  '{"routes":[...],"version":"v2.0"}'
    tenant3:routes  '{"routes":[...],"version":"v3.0"}'
```

**需求**:
- 批量写入多个租户配置
- 每个 field 的值都包含独立的 version
- 需要验证所有租户的 version 是否一致

---

## 💡 改进方案

### 方案 A: 保守改进（仅改名 + 轻量扩展）

#### A.1 命名改进

| 旧术语 | 新术语 | 语义 |
|--------|--------|------|
| Footprint | **VersionTag** / **ConfigSignature** | 版本标签/配置签名 |
| FootprintExtractor | **VersionTagExtractor** / **SignatureExtractor** | 标签提取器 |
| extract() | **extractTag()** / **computeSignature()** | 提取标签/计算签名 |

**推荐**: `VersionTag` + `VersionTagExtractor`
- 语义清晰：明确表示"版本标识"
- 通用性强：可以是版本号、摘要、时间戳等
- 避免混淆：与"footprint"常见含义不冲突

#### A.2 API 改进（向后兼容）

```java
public interface WriteStageBuilder {
    // 保留旧 API（标记 @Deprecated）
    @Deprecated
    WriteStageBuilder footprint(String fieldName);
    
    // 新 API
    WriteStageBuilder versionTag(String fieldName);
    WriteStageBuilder versionTag(VersionTagExtractor extractor);
    WriteStageBuilder versionTag(Function<Object, String> calculator);
    
    // 保留 HSET 单字段 API
    WriteStageBuilder hashKey(String key, String field);
}
```

**影响**: 最小，现有代码仅需简单替换 `footprint()` → `versionTag()`

---

### 方案 B: 激进重构（支持 HSET 多字段）

#### B.1 新增多字段 HSET API

```java
public interface WriteStageBuilder {
    // ===== 新增：多字段 HSET 支持 =====
    
    /**
     * 使用 Hash 多字段模式
     * 
     * @param key Redis Hash Key
     * @return HashFieldsBuilder
     */
    HashFieldsBuilder hashKey(String key);
    
    // ===== 保留：单字段兼容 API =====
    WriteStageBuilder hashKey(String key, String field);
    WriteStageBuilder value(Object value);
}

/**
 * Hash 多字段构建器（新接口）
 */
public interface HashFieldsBuilder {
    /**
     * 添加一个 field
     * 
     * @param field Hash field 名称
     * @param value field 值
     * @return this
     */
    HashFieldsBuilder field(String field, Object value);
    
    /**
     * 批量添加 fields
     * 
     * @param fields field-value 映射
     * @return this
     */
    HashFieldsBuilder fields(Map<String, Object> fields);
    
    /**
     * 指定从哪个 field 的值中提取 versionTag
     * 
     * @param fieldName 目标 field 名称
     * @param extractor 提取器（作用于该 field 的值）
     * @return WriteStageBuilder
     */
    WriteStageBuilder versionTagFromField(String fieldName, VersionTagExtractor extractor);
    
    /**
     * 便捷方法：从指定 field 的 JSON 路径提取 versionTag
     * 
     * @param fieldName 目标 field 名称
     * @param jsonPath JSON 路径，例如 "$.version"
     * @return WriteStageBuilder
     */
    WriteStageBuilder versionTagFromField(String fieldName, String jsonPath);
    
    /**
     * 从整个 fields Map 提取 versionTag（高级用法）
     * 
     * @param extractor 提取器（接收完整的 field-value Map）
     * @return WriteStageBuilder
     */
    WriteStageBuilder versionTagFromFields(Function<Map<String, Object>, String> extractor);
}
```

#### B.2 使用示例

**示例 1: 多字段写入 + 指定 field 提取**
```java
AckResult result = redisAckService.write()
    .hashKey("deployment:tenant:tenant123")
        .field("config", configJson)
        .field("metadata", metadataJson)
        .field("status", "ACTIVE")
        .versionTagFromField("metadata", "$.version")  // ← 从 metadata field 提取
    
    .andPublish()
        .topic("config:updates")
        .message("{\"tenant\":\"tenant123\"}")
    
    .andVerify()
        .httpGet("http://service:8080/actuator/config")
        .extractJson("$.metadata.version")  // ← 从响应提取
        .retryFixedDelay(10, Duration.ofSeconds(3))
        .timeout(Duration.ofSeconds(60))
    
    .executeAndWait();
```

**示例 2: 从多个 field 计算签名**
```java
AckResult result = redisAckService.write()
    .hashKey("config:aggregated")
        .field("tenant1", tenant1Config)
        .field("tenant2", tenant2Config)
        .field("tenant3", tenant3Config)
        .versionTagFromFields(fields -> {
            // 计算所有 tenant 版本的组合签名
            String combined = fields.values().stream()
                .map(v -> extractVersion(v))
                .collect(Collectors.joining(","));
            return DigestUtils.md5Hex(combined);
        })
    
    .andPublish()
        .topic("config:batch-update")
        .message("multi-tenant update")
    
    .andVerify()
        .httpPost("http://validator/check-batch", footprint -> {
            return Map.of("expectedSignature", footprint);
        })
        .extractJson("$.actualSignature")
        .retryFixedDelay(5, Duration.ofSeconds(5))
        .timeout(Duration.ofSeconds(30))
    
    .executeAndWait();
```

#### B.3 实现要点

**WriteStageBuilderImpl 改造**:
```java
public class WriteStageBuilderImpl implements WriteStageBuilder {
    // 保留原有单字段模式
    private String key;
    private String field;
    private Object value;
    
    // 新增多字段模式
    private Map<String, Object> fields; // 当 fields != null 时，使用多字段模式
    private String versionTagSourceField; // 指定从哪个 field 提取
    private VersionTagExtractor fieldLevelExtractor; // field 级别提取器
    
    @Override
    public HashFieldsBuilder hashKey(String key) {
        this.key = key;
        this.fields = new LinkedHashMap<>(); // 初始化多字段模式
        return new HashFieldsBuilderImpl(this);
    }
    
    // 内部类：HashFieldsBuilderImpl
    private static class HashFieldsBuilderImpl implements HashFieldsBuilder {
        private final WriteStageBuilderImpl parent;
        
        @Override
        public HashFieldsBuilder field(String field, Object value) {
            parent.fields.put(field, value);
            return this;
        }
        
        @Override
        public WriteStageBuilder versionTagFromField(String fieldName, String jsonPath) {
            parent.versionTagSourceField = fieldName;
            parent.fieldLevelExtractor = new JsonFieldExtractor(jsonPath, objectMapper);
            return parent;
        }
        
        @Override
        public WriteStageBuilder versionTagFromFields(Function<Map<String, Object>, String> extractor) {
            parent.versionTagExtractor = new FunctionVersionTagExtractor(extractor);
            return parent;
        }
    }
}
```

**AckExecutor 改造**:
```java
private String writeToRedis(AckTask task, AckContext context) {
    // 提取 versionTag
    String versionTag;
    
    if (task.isMultiFieldMode()) {
        // 多字段模式
        if (task.getVersionTagSourceField() != null) {
            // 从指定 field 的值提取
            Object fieldValue = task.getFields().get(task.getVersionTagSourceField());
            versionTag = task.getFieldLevelExtractor().extract(fieldValue);
        } else {
            // 从整个 fields Map 提取
            versionTag = task.getVersionTagExtractor().extract(task.getFields());
        }
        
        // 序列化所有 fields
        Map<String, String> serializedFields = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : task.getFields().entrySet()) {
            serializedFields.put(entry.getKey(), serializeValue(entry.getValue()));
        }
        
        // 原子批量写入
        redisClient.hmset(task.getKey(), serializedFields);
        
    } else {
        // 单字段模式（保留原逻辑）
        versionTag = task.getVersionTagExtractor().extract(task.getValue());
        String valueStr = serializeValue(task.getValue());
        redisClient.hset(task.getKey(), task.getField(), valueStr);
    }
    
    // 设置 TTL（如果有）
    if (task.getTtl() != null) {
        redisClient.expire(task.getKey(), task.getTtl());
    }
    
    return versionTag;
}
```

---

### 方案 C: 中庸方案（改名 + 部分扩展）

#### C.1 命名改进（同方案 A）
- `Footprint` → `VersionTag`
- `FootprintExtractor` → `VersionTagExtractor`

#### C.2 仅扩展提取器灵活性（不改 HSET API）

```java
public interface WriteStageBuilder {
    // 保留单字段 API
    WriteStageBuilder hashKey(String key, String field);
    WriteStageBuilder value(Object value);
    
    // 改进提取器 API
    WriteStageBuilder versionTag(String fieldName);  // 从 value 的 JSON 字段提取
    WriteStageBuilder versionTag(VersionTagExtractor extractor);
    
    /**
     * 新增：从 value 中的嵌套字段提取 versionTag
     * 
     * @param jsonPath JSON 路径，例如 "$.metadata.version"
     * @return this
     */
    WriteStageBuilder versionTagFromPath(String jsonPath);
    
    /**
     * 新增：使用自定义计算函数
     * 
     * @param calculator 计算函数，接收完整 value 对象
     * @return this
     */
    WriteStageBuilder versionTagWith(Function<Object, String> calculator);
}
```

**优势**:
- 命名清晰（解决问题 1）
- 提取器更灵活（部分解决问题 2）
- API 变更最小（兼容性好）

**不足**:
- 仍无法一次性写入多个 field
- 多租户批量场景需要循环调用

---

## 📊 方案对比

| 维度 | 方案 A (保守) | 方案 B (激进) | 方案 C (中庸) |
|------|--------------|--------------|--------------|
| **命名改进** | ✅ 完全 | ✅ 完全 | ✅ 完全 |
| **HSET 多字段** | ❌ 不支持 | ✅ 完全支持 | ❌ 不支持 |
| **字段级提取** | ❌ 不支持 | ✅ 完全支持 | ⚠️ 部分支持（路径提取） |
| **API 兼容性** | ✅ 完全兼容 | ⚠️ 需要适配器 | ✅ 完全兼容 |
| **实现复杂度** | 🟢 低 | 🔴 高 | 🟡 中 |
| **开发工作量** | 1-2天 | 5-7天 | 2-3天 |
| **测试工作量** | 1天 | 3-4天 | 1-2天 |
| **适用场景** | 单字段场景足够 | 需要复杂批量操作 | 当前场景 + 小扩展 |

---

## 🎯 推荐决策路径

### 立即行动 (本周)

1. **确认需求范围**
   - ❓ 是否有实际的"一次写入多个 Hash field"的业务需求？
   - ❓ 当前 BlueGreenStageAssembler 能否通过"单字段 + 嵌套 JSON"满足？
   - ❓ 未来是否有批量租户配置的场景？

2. **选择方案**

#### 如果答案是 "当前单字段足够" → **方案 C（推荐）**
- ✅ 快速解决命名问题
- ✅ 增强提取器灵活性（支持深层路径）
- ✅ 最小化风险和工作量

#### 如果答案是 "未来需要多字段" → **方案 B**
- ✅ 一次性解决所有问题
- ✅ API 更符合 Redis HSET 语义
- ⚠️ 需要完整的测试覆盖

#### 如果答案是 "仅改名即可" → **方案 A**
- ✅ 零风险
- ✅ 1-2 天完成
- ❌ 不解决多字段问题

---

## 🔧 方案 C 实施清单（推荐）

### Phase 1: 命名重构（1天）

**API 层**:
```java
// 新增接口（保留旧接口兼容）
public interface VersionTagExtractor {
    String extractTag(Object value) throws VersionTagExtractionException;
}

// 标记旧接口废弃
@Deprecated
public interface FootprintExtractor extends VersionTagExtractor {
    @Override
    default String extractTag(Object value) throws VersionTagExtractionException {
        return extract(value); // 桥接到旧方法
    }
    
    @Deprecated
    String extract(Object value) throws FootprintExtractionException;
}
```

**Builder API**:
```java
public interface WriteStageBuilder {
    // 新方法
    WriteStageBuilder versionTag(String fieldName);
    WriteStageBuilder versionTag(VersionTagExtractor extractor);
    WriteStageBuilder versionTagFromPath(String jsonPath);
    
    // 旧方法（标记废弃）
    @Deprecated
    WriteStageBuilder footprint(String fieldName);
    @Deprecated
    WriteStageBuilder footprint(FootprintExtractor extractor);
}
```

**数据模型**:
```java
public class AckResult {
    private final String expectedVersionTag;  // 新字段
    private final String actualVersionTag;    // 新字段
    
    @Deprecated
    public String getExpectedFootprint() { return expectedVersionTag; }
    @Deprecated
    public String getActualFootprint() { return actualVersionTag; }
}
```

### Phase 2: 提取器增强（1天）

**新增 JsonPath 提取器**:
```java
public class JsonPathVersionTagExtractor implements VersionTagExtractor {
    private final String jsonPath;
    private final ObjectMapper objectMapper;
    
    @Override
    public String extractTag(Object value) {
        // 使用 JsonPath 从深层嵌套中提取
        // 例如: "$.metadata.version" → "v2.1.0"
        return JsonPath.parse(value).read(jsonPath, String.class);
    }
}
```

**Builder 集成**:
```java
@Override
public WriteStageBuilder versionTagFromPath(String jsonPath) {
    this.versionTagExtractor = new JsonPathVersionTagExtractor(jsonPath, objectMapper);
    return this;
}
```

### Phase 3: 文档更新（0.5天）

1. 更新 `docs/design/redis-ack-service.md`
   - 术语表替换 Footprint → VersionTag
   - 增加 JsonPath 提取示例

2. 更新 `README.md` 示例代码

3. 添加迁移指南 `docs/migration/footprint-to-versiontag.md`

### Phase 4: 业务代码迁移（0.5天）

**BlueGreenStageAssembler 改造**:
```java
// 旧代码（保持功能不变，仅改 API）
redisAckService.write()
    .hashKey(redisKey, redisField)
    .value(redisValue)
    .versionTagFromPath("$.metadata.version")  // ← 新 API，更语义化
    
    .andPublish()
    .topic(topic)
    .message(message)
    
    .andVerify()
    .httpGetMultiple(verifyUrls)
    .extractJson("$.metadata.version")
    .retryFixedDelay(maxAttempts, retryDelay)
    .timeout(timeout)
    
    .executeAndWait();
```

### Phase 5: 测试验证（1天）

1. 单元测试：新提取器 + 新 API
2. 集成测试：BlueGreen + Portal + ASBC 三个场景
3. 回归测试：确保旧 API 仍可用

---

## 📝 术语表（改进后）

| 旧术语 | 新术语 | 定义 |
|--------|--------|------|
| Footprint | **VersionTag** | 能唯一标识一次配置版本的字符串标签 |
| FootprintExtractor | **VersionTagExtractor** | 从值对象中提取 VersionTag 的策略接口 |
| FootprintExtractionException | **VersionTagExtractionException** | 提取失败时的异常 |
| expectedFootprint | **expectedVersionTag** | 写入时提取的预期标签 |
| actualFootprint | **actualVersionTag** | 验证时查询到的实际标签 |

---

## 🏁 结论与建议

### 核心问题确认

1. ✅ **命名问题严重**: "Footprint" 确实容易混淆，强烈建议改为 `VersionTag`
2. ✅ **HSET 限制存在**: 当前仅支持单字段，但可通过"单字段 + 嵌套 JSON"规避大部分场景
3. ⚠️ **需求待确认**: 是否有真实的"多字段批量写入"需求

### 最终推荐

**采用方案 C（中庸方案）**:
1. ✅ 解决命名问题（VersionTag）
2. ✅ 增强提取器灵活性（JsonPath）
3. ✅ 保持 API 兼容性（废弃旧 API）
4. ✅ 最小化风险（2-3 天完成）

**如果未来确认需要多字段**, 可以在方案 C 基础上渐进升级到方案 B，此时：
- 术语已统一（VersionTag）
- 提取器体系已完善
- 仅需扩展 Builder API（增加 `HashFieldsBuilder`）

### 后续跟进

1. 本周内确认业务需求范围
2. 根据需求选择方案 C 或 B
3. 创建 TODO 任务（T-030: Redis ACK VersionTag 重构）
4. 预估 2-3 天完成方案 C，5-7 天完成方案 B

---

**参考文档**:
- 设计文档: `docs/design/redis-ack-service.md`
- API 源码: `redis-ack/ack-api/src/main/java/xyz/firestige/redis/ack/api/`
- 使用示例: `deploy/src/main/java/xyz/firestige/deploy/infrastructure/execution/stage/factory/assembler/BlueGreenStageAssembler.java`

