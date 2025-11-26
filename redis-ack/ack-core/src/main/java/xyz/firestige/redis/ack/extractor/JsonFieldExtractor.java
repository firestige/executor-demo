package xyz.firestige.redis.ack.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.firestige.redis.ack.api.FootprintExtractor;
import xyz.firestige.redis.ack.exception.FootprintExtractionException;

import java.util.Map;

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
 */
public class JsonFieldExtractor implements FootprintExtractor {

    private final String fieldPath;
    private final ObjectMapper objectMapper;

    public JsonFieldExtractor(String fieldPath) {
        this(fieldPath, new ObjectMapper());
    }

    public JsonFieldExtractor(String fieldPath, ObjectMapper objectMapper) {
        this.fieldPath = fieldPath;
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
                return extractFromMap((Map<?, ?>) value);
            }

            // 其他对象，序列化为 JSON 再提取
            JsonNode node = objectMapper.valueToTree(value);
            return extractFromNode(node);

        } catch (Exception e) {
            throw new FootprintExtractionException("Failed to extract field: " + fieldPath, e);
        }
    }

    private String extractFromNode(JsonNode node) throws FootprintExtractionException {
        String[] pathSegments = parseFieldPath(fieldPath);
        JsonNode currentNode = node;

        for (String segment : pathSegments) {
            currentNode = currentNode.get(segment);
            if (currentNode == null || currentNode.isNull()) {
                throw new FootprintExtractionException("Field not found or null at path: " + fieldPath + " (segment: " + segment + ")");
            }
        }

        return currentNode.asText();
    }

    private String extractFromMap(Map<?, ?> map) throws FootprintExtractionException {
        String[] pathSegments = parseFieldPath(fieldPath);
        Object currentValue = map;

        for (String segment : pathSegments) {
            if (!(currentValue instanceof Map)) {
                throw new FootprintExtractionException("Cannot traverse non-map object at path: " + fieldPath + " (segment: " + segment + ")");
            }
            currentValue = ((Map<?, ?>) currentValue).get(segment);
            if (currentValue == null) {
                throw new FootprintExtractionException("Field not found at path: " + fieldPath + " (segment: " + segment + ")");
            }
        }

        return currentValue.toString();
    }

    /**
     * 解析字段路径
     * <p>
     * 支持两种格式：
     * <ul>
     *   <li>"fieldName" -> ["fieldName"]</li>
     *   <li>"$.field1.field2" -> ["field1", "field2"]</li>
     * </ul>
     *
     * @param path 字段路径
     * @return 路径段数组
     */
    private String[] parseFieldPath(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Field path cannot be null or empty");
        }

        // 如果以 $. 开头，去掉前缀并按 . 分割
        if (path.startsWith("$.")) {
            String pathWithoutPrefix = path.substring(2);
            return pathWithoutPrefix.split("\\.");
        }

        // 如果包含 . 但不以 $. 开头，直接按 . 分割（兼容性）
        if (path.contains(".")) {
            return path.split("\\.");
        }

        // 简单字段名
        return new String[]{path};
    }
}

