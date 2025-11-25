package xyz.firestige.deploy.infrastructure.template;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板解析器 - 支持占位符替换
 *
 * <p>支持的占位符格式：{变量名}
 *
 * <p>示例：
 * <pre>
 * String template = "/actuator/bg-sdk/{tenantId}/version/{version}";
 * Map<String, String> variables = Map.of(
 *     "tenantId", "tenant001",
 *     "version", "v1.0.0"
 * );
 * String result = templateResolver.resolve(template, variables);
 * // 结果: /actuator/bg-sdk/tenant001/version/v1.0.0
 * </pre>
 *
 * @since RF-19 三层抽象架构
 * @updated T-027 重新启用，用于 StageAssembler 动态路径构建
 */
@Component
public class TemplateResolver {

    // 匹配 {变量名} 格式的占位符
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)}");

    /**
     * 解析模板字符串，替换所有占位符
     *
     * @param template 模板字符串（如 "/api/{tenantId}/config"）
     * @param variables 变量映射（key=变量名, value=变量值）
     * @return 解析后的字符串
     * @throws IllegalArgumentException 如果模板中的占位符在 variables 中找不到
     */
    public String resolve(String template, Map<String, String> variables) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        if (variables == null || variables.isEmpty()) {
            // 如果没有变量，检查模板中是否还有未解析的占位符
            if (template.contains("{")) {
                throw new IllegalArgumentException("模板中包含占位符，但未提供变量映射: " + template);
            }
            return template;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String variableName = matcher.group(1);
            String value = variables.get(variableName);

            if (value == null) {
                throw new IllegalArgumentException(
                    String.format("模板占位符 '%s' 在变量映射中找不到。模板: %s, 可用变量: %s",
                        variableName, template, variables.keySet())
                );
            }

            // 替换占位符，注意需要转义特殊字符
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 快捷方法：只替换单个变量
     *
     * @param template 模板字符串
     * @param variableName 变量名
     * @param value 变量值
     * @return 解析后的字符串
     */
    public String resolve(String template, String variableName, String value) {
        return resolve(template, Map.of(variableName, value));
    }

    /**
     * 检查模板是否包含占位符
     *
     * @param template 模板字符串
     * @return true 如果包含占位符
     */
    public boolean hasPlaceholders(String template) {
        return template != null && PLACEHOLDER_PATTERN.matcher(template).find();
    }
}

