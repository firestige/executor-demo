package xyz.firestige.redis.ack.extractor;

import xyz.firestige.redis.ack.api.FootprintExtractor;
import xyz.firestige.redis.ack.exception.FootprintExtractionException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 正则表达式 Footprint 提取器
 */
public class RegexFootprintExtractor implements FootprintExtractor {
    private final Pattern pattern;
    private final int group;

    public RegexFootprintExtractor(String regex) { this(regex, 1); }
    public RegexFootprintExtractor(String regex, int group) {
        this.pattern = Pattern.compile(regex);
        this.group = group;
    }

    @Override
    public String extract(Object value) throws FootprintExtractionException {
        if (value == null) throw new FootprintExtractionException("value is null");
        String str = value.toString();
        Matcher matcher = pattern.matcher(str);
        if (!matcher.find()) {
            throw new FootprintExtractionException("pattern not matched: " + pattern);
        }
        if (group > matcher.groupCount()) {
            throw new FootprintExtractionException("group index out of range: " + group);
        }
        String g = matcher.group(group);
        if (g == null) throw new FootprintExtractionException("matched group is null");
        return g;
    }
}

