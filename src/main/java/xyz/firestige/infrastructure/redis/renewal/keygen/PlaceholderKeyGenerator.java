package xyz.firestige.infrastructure.redis.renewal.keygen;

import xyz.firestige.infrastructure.redis.renewal.api.KeyGenerationStrategy;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 占位符替换 Key 生成器（默认实现）
 * <p>替换模板中的 {var} 占位符
 */
public class PlaceholderKeyGenerator implements KeyGenerationStrategy {
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)\\}");

    @Override
    public String generateKey(String template, Map<String, Object> context) {
        if (template == null) {
            throw new IllegalArgumentException("template cannot be null");
        }
        if (context == null || context.isEmpty()) {
            return template;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            Object value = context.get(placeholder);
            String replacement = value != null ? value.toString() : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    @Override
    public String getName() {
        return "PlaceholderKeyGenerator";
    }
}

