package xyz.firestige.redis.ack.api;

import java.util.Map;
import java.util.function.Function;

/**
 * Hash 多字段构建器
 * <p>
 * 用于构建 Redis HSET 的多字段写入，支持原子操作和灵活的 versionTag 提取策略
 *
 * <p><b>使用场景</b>:
 * <ul>
 *   <li>一次性写入多个 Hash field（原子操作）</li>
 *   <li>从指定 field 的值中提取 versionTag</li>
 *   <li>从所有 fields 计算组合签名</li>
 * </ul>
 *
 * <p><b>使用示例</b>:
 * <pre>{@code
 * redisAckService.write()
 *     .hashKey("deployment:tenant:123")
 *         .field("config", configJson)
 *         .field("metadata", metadataJson)
 *         .field("status", "ACTIVE")
 *         .versionTagFromField("metadata", "$.version")
 *     .andPublish()
 *         ...
 * }</pre>
 *
 * @author AI
 * @since 2.0
 */
public interface HashFieldsBuilder {

    /**
     * 添加一个 field
     *
     * @param field Hash field 名称
     * @param value field 值（可以是 String、Map、POJO 等，将自动序列化）
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
     * @return WriteStageBuilder 以继续配置后续阶段
     */
    WriteStageBuilder versionTagFromField(String fieldName, VersionTagExtractor extractor);

    /**
     * 便捷方法：从指定 field 的 JSON 路径提取 versionTag
     *
     * <p>示例:
     * <pre>{@code
     * .versionTagFromField("metadata", "$.version")
     * // 从 metadata field 的 JSON 中提取 version 字段
     * }</pre>
     *
     * @param fieldName 目标 field 名称
     * @param jsonPath JSON 路径，例如 "$.version" 或 "$.metadata.version"
     * @return WriteStageBuilder 以继续配置后续阶段
     */
    WriteStageBuilder versionTagFromField(String fieldName, String jsonPath);

    /**
     * 从整个 fields Map 提取 versionTag（高级用法）
     *
     * <p>使用场景：
     * <ul>
     *   <li>计算所有 fields 的组合签名</li>
     *   <li>根据多个 field 的值生成复合标识</li>
     * </ul>
     *
     * <p>示例:
     * <pre>{@code
     * .versionTagFromFields(fields -> {
     *     String combined = fields.values().stream()
     *         .map(v -> extractVersion(v))
     *         .collect(Collectors.joining(","));
     *     return DigestUtils.md5Hex(combined);
     * })
     * }</pre>
     *
     * @param extractor 提取器（接收完整的 field-value Map）
     * @return WriteStageBuilder 以继续配置后续阶段
     */
    WriteStageBuilder versionTagFromFields(Function<Map<String, Object>, String> extractor);

    /**
     * 便捷方法：从指定 field 的 JSON 字段提取 versionTag（旧 API 兼容）
     *
     * @param fieldName 目标 field 名称
     * @param jsonFieldName JSON 对象中的字段名，例如 "version"
     * @return WriteStageBuilder 以继续配置后续阶段
     * @deprecated 使用 {@link #versionTagFromField(String, String)} 替代，
     *             后者支持更灵活的 JsonPath 语法
     */
    @Deprecated
    default WriteStageBuilder versionTagFromFieldJson(String fieldName, String jsonFieldName) {
        return versionTagFromField(fieldName, "$." + jsonFieldName);
    }
}

