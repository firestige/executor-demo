package xyz.firestige.infrastructure.redis.ack.api;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * ACK 执行上下文
 * <p>
 * 携带执行过程中的元数据
 *
 * @author AI
 * @since 1.0
 */
public class AckContext {

    private final String taskId;
    private final Instant startTime;
    private final Map<String, Object> attributes;

    private String footprint;

    public AckContext(String taskId) {
        this.taskId = taskId;
        this.startTime = Instant.now();
        this.attributes = new HashMap<>();
    }

    // Getters

    public String getTaskId() {
        return taskId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public String getFootprint() {
        return footprint;
    }

    public void setFootprint(String footprint) {
        this.footprint = footprint;
    }

    /**
     * 获取自定义属性
     */
    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    /**
     * 设置自定义属性
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, Objects.requireNonNull(value));
    }

    /**
     * 获取所有属性
     */
    public Map<String, Object> getAttributes() {
        return new HashMap<>(attributes);
    }
}

