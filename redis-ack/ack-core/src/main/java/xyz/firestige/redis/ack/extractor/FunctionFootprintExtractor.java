package xyz.firestige.redis.ack.extractor;

import xyz.firestige.redis.ack.api.FootprintExtractor;
import xyz.firestige.redis.ack.exception.FootprintExtractionException;
import xyz.firestige.redis.ack.exception.VersionTagExtractionException;

import java.util.function.Function;

/**
 * 函数式 Footprint 提取器
 * <p>
 * 使用用户提供的函数提取 footprint
 *
 * @author AI
 * @since 1.0
 * @deprecated 使用 {@link FunctionVersionTagExtractor} 替代。
 *             此类将在 3.0 版本移除。
 */
@Deprecated
public class FunctionFootprintExtractor implements FootprintExtractor {

    private final FunctionVersionTagExtractor delegate;

    public FunctionFootprintExtractor(Function<Object, String> extractorFunction) {
        this.delegate = new FunctionVersionTagExtractor(extractorFunction);
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
