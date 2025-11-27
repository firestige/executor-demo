package xyz.firestige.redis.ack.api;

import xyz.firestige.redis.ack.exception.VersionTagExtractionException;

/**
 * VersionTag 提取器接口
 * <p>
 * 用于从写入的值或响应中提取唯一标识（版本号、摘要、ID等）
 *
 * <p><b>术语说明</b>:
 * <ul>
 *   <li>VersionTag: 能唯一标识一次配置版本的字符串标签</li>
 *   <li>替代原 "Footprint" 概念，语义更清晰</li>
 * </ul>
 *
 * @author AI
 * @since 2.0
 * @see FootprintExtractor 已废弃的旧接口
 */
@FunctionalInterface
public interface VersionTagExtractor {

    /**
     * 从值中提取 versionTag
     *
     * @param value 值对象（可能是 JSON、Map、POJO 等）
     * @return versionTag 字符串
     * @throws VersionTagExtractionException 提取失败
     */
    String extractTag(Object value) throws VersionTagExtractionException;
}

