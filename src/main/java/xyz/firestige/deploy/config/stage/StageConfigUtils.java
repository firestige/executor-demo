package xyz.firestige.deploy.config.stage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 阶段配置工具类
 *
 * @since T-017
 */
public class StageConfigUtils {

    private static final Pattern CAMEL_CASE_PATTERN =
        Pattern.compile("([a-z])([A-Z])");

    /**
     * 驼峰命名转烤串命名
     *
     * @param camelCase 驼峰命名字符串，如 "blueGreenGateway"
     * @return 烤串命名字符串，如 "blue-green-gateway"
     */
    public static String toKebabCase(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return camelCase;
        }

        Matcher matcher = CAMEL_CASE_PATTERN.matcher(camelCase);
        return matcher.replaceAll("$1-$2").toLowerCase();
    }

    /**
     * 烤串命名转驼峰命名
     *
     * @param kebabCase 烤串命名字符串，如 "blue-green-gateway"
     * @return 驼峰命名字符串，如 "blueGreenGateway"
     */
    public static String toCamelCase(String kebabCase) {
        if (kebabCase == null || kebabCase.isEmpty()) {
            return kebabCase;
        }

        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = false;

        for (char c : kebabCase.toCharArray()) {
            if (c == '-') {
                capitalizeNext = true;
            } else {
                if (capitalizeNext) {
                    result.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    result.append(c);
                }
            }
        }

        return result.toString();
    }

    private StageConfigUtils() {
        throw new UnsupportedOperationException("Utility class");
    }
}

