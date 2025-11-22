package xyz.firestige.deploy.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 环境变量占位符解析器
 * 语法: {$VAR} 或 {$VAR:default}
 * VAR 使用大写字母/数字/下划线组成。
 * 默认值不再嵌套解析（第一版限制）。
 */
public class EnvironmentPlaceholderResolver {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentPlaceholderResolver.class);
    private static final Pattern PH = Pattern.compile("\\{\\$([A-Z0-9_]+)(?::([^}]*))?}");

    private final boolean allowMissing;
    private final Map<String, String> cache = new HashMap<>();

    public EnvironmentPlaceholderResolver(boolean allowMissing) {
        this.allowMissing = allowMissing;
    }

    /**
     * 入口：解析配置根对象所有字符串字段、列表和 Map。
     */
    public void resolve(Object root) {
        if (root == null) {
            return;
        }
        Set<String> missing = new LinkedHashSet<>();
        int replacedCount = 0;
        int defaultUsedCount = 0;

        Deque<Object> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Object current = stack.pop();
            if (current == null) {
                continue;
            }
            // 处理集合
            if (current instanceof List<?> list) {
                for (int i = 0; i < list.size(); i++) {
                    Object val = list.get(i);
                    if (val instanceof String s) {
                        String replaced = replaceString(s, missing);
                        if (!Objects.equals(s, replaced)) {
                            ((List<Object>) list).set(i, replaced);
                            replacedCount++;
                            if (usedDefault(s)) defaultUsedCount++;
                        }
                    } else if (isProcessable(val)) {
                        stack.push(val);
                    }
                }
                continue;
            }
            if (current instanceof Map<?,?> map) {
                for (Map.Entry<?,?> e : map.entrySet()) {
                    Object val = e.getValue();
                    if (val instanceof String s) {
                        String replaced = replaceString(s, missing);
                        if (!Objects.equals(s, replaced)) {
                            @SuppressWarnings("unchecked") Map<Object,Object> m = (Map<Object,Object>) map;
                            m.put(e.getKey(), replaced);
                            replacedCount++;
                            if (usedDefault(s)) defaultUsedCount++;
                        }
                    } else if (isProcessable(val)) {
                        stack.push(val);
                    }
                }
                continue;
            }
            Class<?> clazz = current.getClass();
            for (Field f : clazz.getDeclaredFields()) {
                f.setAccessible(true);
                try {
                    Object value = f.get(current);
                    if (value instanceof String s) {
                        String replaced = replaceString(s, missing);
                        if (!Objects.equals(s, replaced)) {
                            f.set(current, replaced);
                            replacedCount++;
                            if (usedDefault(s)) defaultUsedCount++;
                        }
                    } else if (isProcessable(value)) {
                        stack.push(value);
                    }
                } catch (IllegalAccessException ex) {
                    log.warn("Failed to access field {} of {}: {}", f.getName(), clazz.getSimpleName(), ex.getMessage());
                }
            }
        }

        if (!missing.isEmpty() && !allowMissing) {
            throw new IllegalStateException("Missing env vars: " + String.join(", ", missing));
        }

        log.info("Environment placeholder resolved: replaced={}, defaultsUsed={}, missingCount={}",
                replacedCount, defaultUsedCount, missing.size());
        if (!missing.isEmpty()) {
            log.warn("Missing env vars (used blank fallback due to allowMissing={}). Vars: {}", allowMissing, missing);
        }
    }

    private boolean usedDefault(String original) {
        Matcher m = PH.matcher(original);
        if (m.find()) {
            String var = m.group(1);
            String envVal = System.getenv(var);
            return (envVal == null || envVal.isBlank());
        }
        return false;
    }

    private boolean isProcessable(Object o) {
        if (o == null) return false;
        Class<?> c = o.getClass();
        return !(c.isEnum() || c.isPrimitive() || Number.class.isAssignableFrom(c) || c == String.class || c == Boolean.class);
    }

    private String replaceString(String input, Set<String> missing) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        Matcher matcher = PH.matcher(input);
        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;
        boolean changed = false;
        while (matcher.find()) {
            String varName = matcher.group(1);
            String defaultVal = matcher.group(2); // may be null
            String whole = matcher.group(0);
            String resolved = cache.get(whole);
            if (resolved == null) {
                String envVal = System.getenv(varName);
                if (envVal != null && !envVal.isBlank()) {
                    resolved = envVal;
                } else if (defaultVal != null) {
                    resolved = defaultVal;
                } else {
                    missing.add(varName);
                    resolved = ""; // 临时置空；后续是否允许由 allowMissing 控制
                }
                cache.put(whole, resolved);
                logResolved(varName, envValOrNull(System.getenv(varName)), defaultVal, resolved);
            }
            sb.append(input, lastEnd, matcher.start());
            sb.append(resolved);
            lastEnd = matcher.end();
            changed = true;
        }
        if (!changed) {
            return input;
        }
        sb.append(input, lastEnd, input.length());
        return sb.toString();
    }

    private String envValOrNull(String envVal) { return envVal; }

    private void logResolved(String varName, String envVal, String defaultVal, String finalVal) {
        boolean secret = varName.endsWith("_SECRET");
        String displayVal = secret ? "***" : finalVal;
        if (envVal != null && !envVal.isBlank()) {
            log.debug("Resolved env placeholder: {} (env) -> {}", varName, displayVal);
        } else if (defaultVal != null) {
            log.debug("Resolved env placeholder: {} (default='{}') -> {}", varName, defaultVal, displayVal);
        } else {
            log.debug("Resolved env placeholder: {} (missing) -> ''", varName);
        }
    }
}
