package xyz.firestige.infrastructure.redis.ack.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.firestige.infrastructure.redis.ack.api.FootprintExtractor;
import xyz.firestige.infrastructure.redis.ack.exception.FootprintExtractionException;

import java.util.Map;

/**
 * JSON 字段提取器
 * <p>
 * 从 JSON 对象中提取指定字段作为 footprint
 *
 * @author AI
 * @since 1.0
 */
public class JsonFieldExtractor implements FootprintExtractor {

    private final String fieldName;
    private final ObjectMapper objectMapper;

    public JsonFieldExtractor(String fieldName) {
        this(fieldName, new ObjectMapper());
    }

    public JsonFieldExtractor(String fieldName, ObjectMapper objectMapper) {
        this.fieldName = fieldName;
        this.objectMapper = objectMapper;
    }

    @Override
    public String extract(Object value) throws FootprintExtractionException {
        try {
            // 如果已经是字符串，尝试解析为 JSON
            if (value instanceof String) {
                JsonNode node = objectMapper.readTree((String) value);
                return extractFromNode(node);
            }

            // 如果是 Map，直接提取
            if (value instanceof Map) {
                Object fieldValue = ((Map<?, ?>) value).get(fieldName);
                if (fieldValue == null) {
                    throw new FootprintExtractionException("Field not found: " + fieldName);
                }
                return fieldValue.toString();
            }

            // 其他对象，序列化为 JSON 再提取
            JsonNode node = objectMapper.valueToTree(value);
            return extractFromNode(node);

        } catch (Exception e) {
            throw new FootprintExtractionException("Failed to extract field: " + fieldName, e);
        }
    }

    private String extractFromNode(JsonNode node) throws FootprintExtractionException {
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            throw new FootprintExtractionException("Field not found or null: " + fieldName);
        }
        return fieldNode.asText();
    }
}

