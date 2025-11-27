package xyz.firestige.redis.ack.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.firestige.redis.ack.api.VersionTagExtractor;
import xyz.firestige.redis.ack.exception.VersionTagExtractionException;

import java.util.Map;

/**
 * JSON 字段版本标签提取器
 * <p>
 * 从 JSON 对象中提取指定字段作为 versionTag
 * <p>
 * 支持两种字段路径格式：
 * <ul>
 *   <li>简单字段名：{@code "fieldName"} - 提取第一层字段</li>
 *   <li>JSONPath 风格路径：{@code "$.field1.field2.field3"} - 提取嵌套字段</li>
 * </ul>
 *
 * @author AI
 * @since 2.0
 */
public class JsonFieldVersionTagExtractor implements VersionTagExtractor {

    private final String fieldPath;
    private final ObjectMapper objectMapper;

    public JsonFieldVersionTagExtractor(String fieldPath) {
        this(fieldPath, new ObjectMapper());
    }

    public JsonFieldVersionTagExtractor(String fieldPath, ObjectMapper objectMapper) {
        this.fieldPath = fieldPath;
        this.objectMapper = objectMapper;
    }

    @Override
    public String extractTag(Object value) throws VersionTagExtractionException {
        try {
            // 如果已经是字符串，尝试解析为 JSON
            if (value instanceof String) {
                JsonNode node = objectMapper.readTree((String) value);
                return extractFromNode(node);
            }

            // 如果是 Map，直接提取
            if (value instanceof Map) {
                return extractFromMap((Map<?, ?>) value);
            }

            // 其他对象，序列化为 JSON 再提取
            JsonNode node = objectMapper.valueToTree(value);
            return extractFromNode(node);

        } catch (VersionTagExtractionException e) {
            throw e;
        } catch (Exception e) {
            throw new VersionTagExtractionException("Failed to extract field: " + fieldPath, e);
        }
    }

    private String extractFromNode(JsonNode node) throws VersionTagExtractionException {
        String[] pathSegments = parseFieldPath(fieldPath);
        JsonNode currentNode = node;

        for (String segment : pathSegments) {
            currentNode = currentNode.get(segment);
            if (currentNode == null || currentNode.isNull()) {
                throw new VersionTagExtractionException(
                    "Field not found or null at path: " + fieldPath + " (segment: " + segment + ")"
                );
            }
        }

        return currentNode.asText();
    }

    private String extractFromMap(Map<?, ?> map) throws VersionTagExtractionException {
        String[] pathSegments = parseFieldPath(fieldPath);
        Object currentValue = map;

        for (String segment : pathSegments) {
            if (!(currentValue instanceof Map)) {
                throw new VersionTagExtractionException(
                    "Cannot traverse non-map object at path: " + fieldPath + " (segment: " + segment + ")"
                );
            }

            currentValue = ((Map<?, ?>) currentValue).get(segment);
            if (currentValue == null) {
                throw new VersionTagExtractionException(
                    "Field not found or null at path: " + fieldPath + " (segment: " + segment + ")"
                );
            }
        }

        return currentValue.toString();
    }

    private String[] parseFieldPath(String path) {
        // 移除 JSONPath 前缀 "$."
        String normalizedPath = path.startsWith("$.") ? path.substring(2) : path;

        // 按 "." 分割路径
        return normalizedPath.split("\\.");
    }
}

