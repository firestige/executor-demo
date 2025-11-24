package xyz.firestige.infrastructure.redis.ack.api;

import xyz.firestige.infrastructure.redis.ack.exception.FootprintExtractionException;

/**
 * Footprint 提取器接口
 * <p>
 * 用于从写入的值或响应中提取唯一标识（版本号、摘要、ID等）
 *
 * @author AI
 * @since 1.0
 */
@FunctionalInterface
public interface FootprintExtractor {

    /**
     * 从值中提取 footprint
     *
     * @param value 值对象（可能是 JSON、Map、POJO 等）
     * @return footprint 字符串
     * @throws FootprintExtractionException 提取失败
     */
    String extract(Object value) throws FootprintExtractionException;
}

