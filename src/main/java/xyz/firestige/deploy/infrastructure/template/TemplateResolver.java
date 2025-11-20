package xyz.firestige.deploy.infrastructure.template;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板变量解析器
 *
 * 职责：
 * 1. 解析配置中的占位符并替换为实际值
 * 2. 支持递归处理嵌套配置（Map、List）
 * 3. 支持占位符格式: {variableName}
 *
 * 示例：
 * - "/actuator/bg-sdk/{tenantId}" + {tenantId: "tenant-001"} → "/actuator/bg-sdk/tenant-001"
 * - '{"tenantId":"{tenantId}","appName":"gateway"}' + {tenantId: "tenant-001"}
 *   → '{"tenantId":"tenant-001","appName":"gateway"}'
 */
@Component
public class TemplateResolver {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_-]+)\\}");

    /**
     * 解析对象中的所有模板变量（递归处理）
     *
     * @param obj 待解析对象（Map, String, List等）
     * @param variables 变量上下文
     * @return 解析后的对象
     */
    public Object resolve(Object obj, Map<String, String> variables) {
        if (obj == null) {
            return null;
        }

        if (obj instanceof String) {
            return resolveString((String) obj, variables);
        }

        if (obj instanceof Map) {
            return resolveMap((Map<?, ?>) obj, variables);
        }

        if (obj instanceof List) {
            return resolveList((List<?>) obj, variables);
        }

        // 其他类型直接返回（Integer, Boolean等）
        return obj;
    }

    /**
     * 解析字符串中的占位符
     *
     * @param template 模板字符串，如 "/actuator/bg-sdk/{tenantId}"
     * @param variables 变量 Map
     * @return 替换后的字符串
     */
    private String resolveString(String template, Map<String, String> variables) {
        if (!StringUtils.hasText(template)) {
            return template;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String value = variables.get(varName);

            if (value == null) {
                throw new IllegalArgumentException(
                    "Missing variable value for placeholder: {" + varName + "} in template: " + template
                );
            }

            // 使用 Matcher.quoteReplacement 避免特殊字符问题
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * 递归解析 Map 中的所有值
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveMap(Map<?, ?> map, Map<String, String> variables) {
        Map<String, Object> resolved = new HashMap<>();

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = entry.getKey().toString();
            Object value = entry.getValue();
            resolved.put(key, resolve(value, variables));
        }

        return resolved;
    }

    /**
     * 递归解析 List 中的所有元素
     */
    private List<Object> resolveList(List<?> list, Map<String, String> variables) {
        List<Object> resolved = new ArrayList<>();

        for (Object item : list) {
            resolved.add(resolve(item, variables));
        }

        return resolved;
    }
}

