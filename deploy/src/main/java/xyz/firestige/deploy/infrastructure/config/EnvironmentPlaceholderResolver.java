package xyz.firestige.deploy.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;

/**
 * 环境变量占位符解析器
 * 语法:
 *  - {$VAR}
 *  - {$VAR:defaultValue}
 * 支持 defaultValue 中嵌套普通模板占位符，如: {$HEALTH_CHECK_PATH:/actuator/bg-sdk/{tenantId}}
 * 其中最后两个连续的 '}}' 分别关闭内部模板和外层环境占位符。
 */
public class EnvironmentPlaceholderResolver {
    private static final Logger log = LoggerFactory.getLogger(EnvironmentPlaceholderResolver.class);
    private final boolean allowMissing;
    private final Map<String, String> cache = new HashMap<>();

    public EnvironmentPlaceholderResolver(boolean allowMissing) {
        this.allowMissing = allowMissing;
    }

    public void resolve(Object root) {
        if (root == null) return;
        Set<String> missing = new LinkedHashSet<>();
        int replacedCount = 0;
        int defaultUsedCount = 0;
        Deque<Object> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Object current = stack.pop();
            if (current == null) continue;
            if (current instanceof List<?> list) {
                for (int i = 0; i < list.size(); i++) {
                    Object val = list.get(i);
                    if (val instanceof String s) {
                        String replaced = replacePlaceholders(s, missing);
                        if (!Objects.equals(s, replaced)) {
                            ((List<Object>) list).set(i, replaced);
                            replacedCount++;
                            if (usedDefault(s)) defaultUsedCount++;
                        }
                    } else if (isProcessable(val)) stack.push(val);
                }
                continue;
            }
            if (current instanceof Map<?,?> map) {
                for (Map.Entry<?,?> e : map.entrySet()) {
                    Object val = e.getValue();
                    if (val instanceof String s) {
                        String replaced = replacePlaceholders(s, missing);
                        if (!Objects.equals(s, replaced)) {
                            @SuppressWarnings("unchecked") Map<Object,Object> m = (Map<Object,Object>) map;
                            m.put(e.getKey(), replaced);
                            replacedCount++;
                            if (usedDefault(s)) defaultUsedCount++;
                        }
                    } else if (isProcessable(val)) stack.push(val);
                }
                continue;
            }
            Class<?> clazz = current.getClass();
            for (Field f : clazz.getDeclaredFields()) {
                f.setAccessible(true);
                try {
                    Object value = f.get(current);
                    if (value instanceof String s) {
                        String replaced = replacePlaceholders(s, missing);
                        if (!Objects.equals(s, replaced)) {
                            f.set(current, replaced);
                            replacedCount++;
                            if (usedDefault(s)) defaultUsedCount++;
                        }
                    } else if (isProcessable(value)) stack.push(value);
                } catch (IllegalAccessException ex) {
                    log.warn("Field access failure {}.{}: {}", clazz.getSimpleName(), f.getName(), ex.getMessage());
                }
            }
        }
        if (!missing.isEmpty() && !allowMissing) {
            throw new IllegalStateException("Missing env vars: " + String.join(", ", missing));
        }
        log.info("Env placeholders resolved: replaced={}, defaultsUsed={}, missingCount={}",
                replacedCount, defaultUsedCount, missing.size());
        if (!missing.isEmpty()) {
            log.warn("Missing env vars (allowMissing={}) -> {}", allowMissing, missing);
        }
    }

    private boolean isProcessable(Object o) {
        if (o == null) return false;
        Class<?> c = o.getClass();
        return !(c.isEnum() || c.isPrimitive() || Number.class.isAssignableFrom(c) || c == String.class || c == Boolean.class);
    }

    /**
     * 是否使用了默认值（用于统计）: 原串包含 {$VAR:...} 且对应 env 缺失。
     */
    private boolean usedDefault(String original) {
        int idx = original.indexOf("{$");
        if (idx < 0) return false;
        // 简单检测 env 是否存在
        int colon = original.indexOf(':', idx);
        if (colon < 0) return false; // 无默认值
        // 解析变量名
        int varStart = idx + 2;
        int varEnd = colon;
        String varName = original.substring(varStart, varEnd);
        String envVal = System.getenv(varName);
        return envVal == null || envVal.isBlank();
    }

    /**
     * 支持 nested {tenantId} 场景的占位符解析。
     */
    private String replacePlaceholders(String input, Set<String> missing) {
        if (input == null || input.indexOf("{$") < 0) return input;
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < input.length()) {
            int start = input.indexOf("{$", i);
            if (start < 0) {
                out.append(input, i, input.length());
                break;
            }
            // append leading untouched
            out.append(input, i, start);
            int varNameStart = start + 2;
            int cursor = varNameStart;
            // read var name until ':' or '}'
            while (cursor < input.length() && input.charAt(cursor) != ':' && input.charAt(cursor) != '}') {
                cursor++;
            }
            if (cursor >= input.length()) { // malformed, append rest and break
                out.append(input.substring(start));
                break;
            }
            String varName = input.substring(varNameStart, cursor);
            String defaultVal = null;
            int endPos;
            if (cursor < input.length() && input.charAt(cursor) == ':') {
                // parse default with nested braces
                cursor++; // skip ':'
                int defStart = cursor;
                int depth = 0;
                while (cursor < input.length()) {
                    char c = input.charAt(cursor);
                    if (c == '{') depth++;
                    else if (c == '}') {
                        if (depth == 0) { // end of env placeholder
                            // previous char may close inner placeholder
                            break;
                        } else depth--;
                    }
                    cursor++;
                }
                if (cursor >= input.length()) { // malformed
                    out.append(input.substring(start));
                    break;
                }
                defaultVal = input.substring(defStart, cursor); // includes inner placeholders fully
                endPos = cursor; // at closing '}' of env placeholder
            } else { // immediate closing
                endPos = cursor; // points to '}'
            }
            // resolve value
            String whole = defaultVal == null ? "{$" + varName + "}" : "{$" + varName + ":" + defaultVal + "}";
            String resolved = cache.get(whole);
            if (resolved == null) {
                String envVal = System.getenv(varName);
                if (envVal != null && !envVal.isBlank()) {
                    resolved = envVal;
                } else if (defaultVal != null) {
                    resolved = defaultVal;
                } else {
                    missing.add(varName);
                    resolved = ""; // placeholder removed
                }
                cache.put(whole, resolved);
                logResolved(varName, System.getenv(varName), defaultVal, resolved);
            }
            out.append(resolved);
            i = endPos + 1; // skip closing '}'
        }
        return out.toString();
    }

    private void logResolved(String varName, String envVal, String defaultVal, String finalVal) {
        boolean secret = varName.endsWith("_SECRET");
        String displayVal = secret ? "***" : finalVal;
        if (envVal != null && !envVal.isBlank()) {
            log.debug("Env placeholder {} resolved via env -> {}", varName, displayVal);
        } else if (defaultVal != null) {
            log.debug("Env placeholder {} resolved via default='{}' -> {}", varName, defaultVal, displayVal);
        } else {
            log.debug("Env placeholder {} missing (no default) -> ''", varName);
        }
    }
}
