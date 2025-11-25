package xyz.firestige.redis.ack.extractor;

import xyz.firestige.redis.ack.api.FootprintExtractor;
import xyz.firestige.redis.ack.exception.FootprintExtractionException;

import java.util.function.Function;

/**
 * 函数式 Footprint 提取器
 * <p>
 * 使用用户提供的函数提取 footprint
 *
 * @author AI
 * @since 1.0
 */
public class FunctionFootprintExtractor implements FootprintExtractor {

    private final Function<Object, String> extractorFunction;

    public FunctionFootprintExtractor(Function<Object, String> extractorFunction) {
        this.extractorFunction = extractorFunction;
    }

    @Override
    public String extract(Object value) throws FootprintExtractionException {
        try {
            String result = extractorFunction.apply(value);
            if (result == null) {
                throw new FootprintExtractionException("Extractor function returned null");
            }
            return result;
        } catch (Exception e) {
            throw new FootprintExtractionException("Function extraction failed", e);
        }
    }
}

