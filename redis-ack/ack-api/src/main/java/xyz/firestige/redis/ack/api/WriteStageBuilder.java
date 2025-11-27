package xyz.firestige.redis.ack.api;

import java.time.Duration;
import java.util.function.Function;

/**
 * Write 阶段构建器
 * <p>
 * 负责配置 Redis 写入操作和 VersionTag 提取
 *
 * <p><b>版本 2.0 新增</b>:
 * <ul>
 *   <li>支持 Hash 多字段模式（{@link #hashKey(String)}）</li>
 *   <li>新增 VersionTag API（替代 Footprint）</li>
 *   <li>保留旧 API 向后兼容</li>
 * </ul>
 *
 * @author AI
 * @since 1.0
 */
public interface WriteStageBuilder {

    // ========== Redis 操作配置 ==========

    /**
     * 设置 Redis Key (String 类型)
     *
     * @param key Redis Key
     * @return this
     */
    WriteStageBuilder key(String key);

    /**
     * 设置 Redis Hash Key + Field (Hash 单字段模式)
     *
     * <p>对于单字段写入场景，使用此方法。如需写入多个 field，
     * 请使用 {@link #hashKey(String)} 进入多字段模式。
     *
     * @param key Redis Hash Key
     * @param field Hash Field
     * @return this
     */
    WriteStageBuilder hashKey(String key, String field);

    /**
     * 使用 Hash 多字段模式
     *
     * <p>进入多字段构建模式，支持一次性原子写入多个 fields。
     *
     * <p>使用示例:
     * <pre>{@code
     * .hashKey("deployment:tenant:123")
     *     .field("config", configJson)
     *     .field("metadata", metadataJson)
     *     .versionTagFromField("metadata", "$.version")
     * .andPublish()
     *     ...
     * }</pre>
     *
     * @param key Redis Hash Key
     * @return HashFieldsBuilder 多字段构建器
     * @since 2.0
     */
    HashFieldsBuilder hashKey(String key);

    /**
     * 设置要写入的值
     *
     * @param value 值对象
     * @return this
     */
    WriteStageBuilder value(Object value);

    /**
     * 设置 TTL (可选)
     *
     * @param ttl 过期时间
     * @return this
     */
    WriteStageBuilder ttl(Duration ttl);

    /**
     * 设置 Redis 操作类型 (默认根据 key/hashKey 自动判断)
     *
     * @param operation 操作类型
     * @return this
     */
    WriteStageBuilder operation(RedisOperation operation);

    /**
     * 设置 ZADD 操作的分数（仅在使用 ZADD 时需要）
     *
     * @param score 分数
     * @return this
     */
    WriteStageBuilder zsetScore(double score);

    // ========== VersionTag 配置（新 API）==========

    /**
     * 从 value 中提取指定字段作为 versionTag (JSON 对象)
     *
     * <p>示例: 从 {"metadata": {"version": "v2.1.0"}} 提取 "version"
     * <pre>{@code
     * .versionTag("version")
     * }</pre>
     *
     * @param fieldName JSON 字段名，例如 "version"
     * @return this
     * @since 2.0
     */
    WriteStageBuilder versionTag(String fieldName);

    /**
     * 使用自定义提取器提取 versionTag
     *
     * @param extractor 提取器
     * @return this
     * @since 2.0
     */
    WriteStageBuilder versionTag(VersionTagExtractor extractor);

    /**
     * 使用自定义函数计算 versionTag
     *
     * @param calculator 计算函数
     * @return this
     * @since 2.0
     */
    WriteStageBuilder versionTag(Function<Object, String> calculator);

    /**
     * 从 value 中的嵌套 JSON 路径提取 versionTag
     *
     * <p>支持深层嵌套提取:
     * <pre>{@code
     * .versionTagFromPath("$.metadata.version")
     * }</pre>
     *
     * @param jsonPath JSON 路径，例如 "$.metadata.version"
     * @return this
     * @since 2.0
     */
    WriteStageBuilder versionTagFromPath(String jsonPath);

    // ========== Footprint 配置（旧 API，已废弃）==========

    /**
     * 从 value 中提取指定字段作为 footprint (JSON 对象)
     *
     * @param fieldName JSON 字段名，例如 "version"
     * @return this
     * @deprecated 使用 {@link #versionTag(String)} 替代
     */
    @Deprecated
    WriteStageBuilder footprint(String fieldName);

    /**
     * 使用自定义提取器提取 footprint
     *
     * @param extractor 提取器
     * @return this
     * @deprecated 使用 {@link #versionTag(VersionTagExtractor)} 替代
     */
    @Deprecated
    WriteStageBuilder footprint(FootprintExtractor extractor);

    /**
     * 使用自定义函数计算 footprint
     *
     * @param calculator 计算函数
     * @return this
     * @deprecated 使用 {@link #versionTag(Function)} 替代
     */
    @Deprecated
    WriteStageBuilder footprint(Function<Object, String> calculator);

    // ========== 流程控制 ==========

    /**
     * 进入 Pub/Sub 阶段（必须调用）
     *
     * @return Pub/Sub 阶段构建器
     */
    PubSubStageBuilder andPublish();

    /**
     * 使用 LPUSH 操作将 value 作为列表元素推入（左侧）
     */
    default WriteStageBuilder listPush(String listKey, Object element) {
        key(listKey);
        value(element);
        operation(RedisOperation.LPUSH);
        return this;
    }

    /**
     * 使用 SADD 操作将 value 作为集合成员添加
     */
    default WriteStageBuilder setAdd(String setKey, Object member) {
        key(setKey);
        value(member);
        operation(RedisOperation.SADD);
        return this;
    }

    /**
     * 使用 ZADD 操作添加排序集合成员，需要指定分数
     */
    default WriteStageBuilder zsetAdd(String zsetKey, double score, Object member) {
        return key(zsetKey)
            .value(member)
            .operation(RedisOperation.ZADD)
            .zsetScore(score);
    }
}
