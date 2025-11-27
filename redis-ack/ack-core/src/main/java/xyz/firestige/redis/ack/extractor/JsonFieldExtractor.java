package xyz.firestige.redis.ack.extractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.firestige.redis.ack.api.FootprintExtractor;
import xyz.firestige.redis.ack.exception.FootprintExtractionException;
import xyz.firestige.redis.ack.exception.VersionTagExtractionException;

/**
 * JSON 字段提取器
 * <p>
 * 从 JSON 对象中提取指定字段作为 footprint
 * <p>
 * 支持两种字段路径格式：
 * <ul>
 *   <li>简单字段名：{@code "fieldName"} - 提取第一层字段</li>
 *   <li>JSONPath 风格路径：{@code "$.field1.field2.field3"} - 提取嵌套字段</li>
 * </ul>
 *
 * @author AI
 * @since 1.0
 * @deprecated 使用 {@link JsonFieldVersionTagExtractor} 替代。
 *             此类将在 3.0 版本移除。
 */
@Deprecated
public class JsonFieldExtractor implements FootprintExtractor {

    private final JsonFieldVersionTagExtractor delegate;

    public JsonFieldExtractor(String fieldPath) {
        this.delegate = new JsonFieldVersionTagExtractor(fieldPath);
    }

    public JsonFieldExtractor(String fieldPath, ObjectMapper objectMapper) {
        this.delegate = new JsonFieldVersionTagExtractor(fieldPath, objectMapper);
    }

    @Override
    public String extract(Object value) throws FootprintExtractionException {
        try {
            return delegate.extractTag(value);
        } catch (VersionTagExtractionException e) {
            throw new FootprintExtractionException(e.getMessage(), e);
        }
    }
}

