package xyz.firestige.redis.ack.extractor;

import xyz.firestige.redis.ack.api.VersionTagExtractor;
import xyz.firestige.redis.ack.exception.VersionTagExtractionException;

import java.util.function.Function;

/**
 * 函数式版本标签提取器
 * <p>
 * 使用自定义函数提取 versionTag
 *
 * <p>使用示例:
 * <pre>{@code
 * // 简单提取
 * new FunctionVersionTagExtractor(value -> {
 *     Map<String, Object> map = (Map<String, Object>) value;
 *     return map.get("version").toString();
 * });
 *
 * // 复杂计算
 * new FunctionVersionTagExtractor(value -> {
 *     Map<String, Object> map = (Map<String, Object>) value;
 *     String v1 = map.get("majorVersion").toString();
 *     String v2 = map.get("minorVersion").toString();
 *     return v1 + "." + v2;
 * });
 * }</pre>
 *
 * @author AI
 * @since 2.0
 */
public class FunctionVersionTagExtractor implements VersionTagExtractor {

    private final Function<Object, String> extractor;

    public FunctionVersionTagExtractor(Function<Object, String> extractor) {
        this.extractor = extractor;
    }

    @Override
    public String extractTag(Object value) throws VersionTagExtractionException {
        try {
            String result = extractor.apply(value);
            if (result == null) {
                throw new VersionTagExtractionException("Extractor function returned null");
            }
            return result;
        } catch (VersionTagExtractionException e) {
            throw e;
        } catch (Exception e) {
            throw new VersionTagExtractionException("Function extraction failed", e);
        }
    }
}

