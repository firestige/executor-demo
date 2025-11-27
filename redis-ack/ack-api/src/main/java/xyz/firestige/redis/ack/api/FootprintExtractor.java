package xyz.firestige.redis.ack.api;

import xyz.firestige.redis.ack.exception.FootprintExtractionException;
import xyz.firestige.redis.ack.exception.VersionTagExtractionException;

/**
 * Footprint 提取器接口
 * <p>
 * 用于从写入的值或响应中提取唯一标识（版本号、摘要、ID等）
 *
 * @author AI
 * @since 1.0
 * @deprecated 使用 {@link VersionTagExtractor} 替代。Footprint 术语容易混淆，
 *             VersionTag 语义更清晰。此接口将在 3.0 版本移除。
 */
@Deprecated
@FunctionalInterface
public interface FootprintExtractor extends VersionTagExtractor {

    /**
     * 从值中提取 footprint
     *
     * @param value 值对象（可能是 JSON、Map、POJO 等）
     * @return footprint 字符串
     * @throws FootprintExtractionException 提取失败
     * @deprecated 使用 {@link VersionTagExtractor#extractTag(Object)} 替代
     */
    @Deprecated
    String extract(Object value) throws FootprintExtractionException;

    /**
     * 桥接方法：将新接口调用委托给旧方法
     * <p>
     * 这样旧的实现类无需修改即可兼容新接口
     */
    @Override
    default String extractTag(Object value) throws VersionTagExtractionException {
        try {
            return extract(value);
        } catch (FootprintExtractionException e) {
            throw new VersionTagExtractionException(e.getMessage(), e);
        }
    }
}

