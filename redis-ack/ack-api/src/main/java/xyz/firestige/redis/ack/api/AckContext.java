package xyz.firestige.redis.ack.api;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * ACK 执行上下文
 * <p>
 * 携带执行过程中的元数据
 *
 * <p><b>版本 2.0 更新</b>:
 * <ul>
 *   <li>新增 versionTag 字段（替代 footprint）</li>
 *   <li>保留 footprint 字段向后兼容</li>
 * </ul>
 *
 * @author AI
 * @since 1.0
 */
public class AckContext {

    private final String taskId;
    private final Instant startTime;
    private final Map<String, Object> attributes;

    // 新字段（2.0+）
    private String versionTag;

    // 旧字段（保留兼容）
    @Deprecated
    private String footprint;

    public AckContext(String taskId) {
        this.taskId = taskId;
        this.startTime = Instant.now();
        this.attributes = new HashMap<>();
    }

    // ========== Basic Getters ==========

    public String getTaskId() {
        return taskId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    // ========== VersionTag（新 API）==========

    /**
     * 获取版本标签
     *
     * @return versionTag 值
     * @since 2.0
     */
    public String getVersionTag() {
        return versionTag;
    }

    /**
     * 设置版本标签
     *
     * @param versionTag 版本标签
     * @since 2.0
     */
    public void setVersionTag(String versionTag) {
        this.versionTag = versionTag;
        this.footprint = versionTag; // 同步到旧字段
    }

    // ========== Footprint（旧 API，已废弃）==========

    /**
     * 获取 footprint
     *
     * @return footprint 值
     * @deprecated 使用 {@link #getVersionTag()} 替代
     */
    @Deprecated
    public String getFootprint() {
        return footprint;
    }

    /**
     * 设置 footprint
     *
     * @param footprint footprint 值
     * @deprecated 使用 {@link #setVersionTag(String)} 替代
     */
    @Deprecated
    public void setFootprint(String footprint) {
        this.footprint = footprint;
        this.versionTag = footprint; // 同步到新字段
    }

    // ========== Attributes ==========

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

