package xyz.firestige.infrastructure.redis.ack.api;

import java.time.Duration;
import java.util.function.Function;

/**
 * Write 阶段构建器
 * <p>
 * 负责配置 Redis 写入操作和 Footprint 提取
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
     * 设置 Redis Hash Key + Field (Hash 类型)
     *
     * @param key Redis Hash Key
     * @param field Hash Field
     * @return this
     */
    WriteStageBuilder hashKey(String key, String field);

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

    // ========== Footprint 配置 ==========

    /**
     * 从 value 中提取指定字段作为 footprint (JSON 对象)
     *
     * @param fieldName JSON 字段名，例如 "version"
     * @return this
     */
    WriteStageBuilder footprint(String fieldName);

    /**
     * 使用自定义提取器提取 footprint
     *
     * @param extractor 提取器
     * @return this
     */
    WriteStageBuilder footprint(FootprintExtractor extractor);

    /**
     * 使用自定义函数计算 footprint
     *
     * @param calculator 计算函数
     * @return this
     */
    WriteStageBuilder footprint(Function<Object, String> calculator);

    // ========== 流程控制 ==========

    /**
     * 进入 Pub/Sub 阶段（必须调用）
     *
     * @return Pub/Sub 阶段构建器
     */
    PubSubStageBuilder andPublish();
}

