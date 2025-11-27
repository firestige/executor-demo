package xyz.firestige.redis.ack.api;

import java.time.Duration;

/**
 * ACK 执行结果
 *
 * <p><b>版本 2.0 更新</b>:
 * <ul>
 *   <li>新增 versionTag 字段（替代 footprint）</li>
 *   <li>保留 footprint 字段向后兼容（已标记 @Deprecated）</li>
 *   <li>新增 isVersionTagMismatch() 方法</li>
 * </ul>
 *
 * @author AI
 * @since 1.0
 */
public class AckResult {

    private final boolean success;

    // 新字段（2.0+）
    private final String expectedVersionTag;
    private final String actualVersionTag;

    // 旧字段（保留兼容）
    @Deprecated
    private final String expectedFootprint;
    @Deprecated
    private final String actualFootprint;

    private final int attempts;
    private final Duration elapsed;
    private final String reason;
    private final Throwable error;

    private AckResult(boolean success, String expectedVersionTag, String actualVersionTag,
                     int attempts, Duration elapsed, String reason, Throwable error) {
        this.success = success;
        // 新字段
        this.expectedVersionTag = expectedVersionTag;
        this.actualVersionTag = actualVersionTag;
        // 旧字段（指向相同值）
        this.expectedFootprint = expectedVersionTag;
        this.actualFootprint = actualVersionTag;
        this.attempts = attempts;
        this.elapsed = elapsed;
        this.reason = reason;
        this.error = error;
    }

    // ========== Status Getters ==========

    public boolean isSuccess() {
        return success;
    }

    public boolean isTimeout() {
        return !success && "TIMEOUT".equals(reason);
    }

    /**
     * 是否因版本标签不匹配而失败
     *
     * @return true 如果失败原因是 MISMATCH
     * @since 2.0
     */
    public boolean isVersionTagMismatch() {
        return !success && "MISMATCH".equals(reason);
    }

    /**
     * 是否因 footprint 不匹配而失败
     *
     * @return true 如果失败原因是 MISMATCH
     * @deprecated 使用 {@link #isVersionTagMismatch()} 替代
     */
    @Deprecated
    public boolean isFootprintMismatch() {
        return isVersionTagMismatch();
    }

    public boolean isError() {
        return !success && error != null;
    }

    // ========== VersionTag Getters（新 API）==========

    /**
     * 获取预期的版本标签
     *
     * @return 写入时提取的 versionTag
     * @since 2.0
     */
    public String getExpectedVersionTag() {
        return expectedVersionTag;
    }

    /**
     * 获取实际的版本标签
     *
     * @return 验证时查询到的 versionTag
     * @since 2.0
     */
    public String getActualVersionTag() {
        return actualVersionTag;
    }

    // ========== Footprint Getters（旧 API，已废弃）==========

    /**
     * 获取预期的 footprint
     *
     * @return footprint 值
     * @deprecated 使用 {@link #getExpectedVersionTag()} 替代
     */
    @Deprecated
    public String getExpectedFootprint() {
        return expectedFootprint;
    }

    /**
     * 获取实际的 footprint
     *
     * @return footprint 值
     * @deprecated 使用 {@link #getActualVersionTag()} 替代
     */
    @Deprecated
    public String getActualFootprint() {
        return actualFootprint;
    }

    // ========== Other Getters ==========

    public int getAttempts() {
        return attempts;
    }

    public Duration getElapsed() {
        return elapsed;
    }

    public String getReason() {
        return reason;
    }

    public Throwable getError() {
        return error;
    }

    // ========== Factory Methods（新 API）==========

    /**
     * 创建成功结果
     *
     * @param expectedVersionTag 预期版本标签
     * @param actualVersionTag 实际版本标签
     * @param attempts 尝试次数
     * @param elapsed 耗时
     * @return 成功结果
     * @since 2.0
     */
    public static AckResult success(String expectedVersionTag, String actualVersionTag,
                                   int attempts, Duration elapsed) {
        return new AckResult(true, expectedVersionTag, actualVersionTag, attempts, elapsed, null, null);
    }

    /**
     * 创建版本标签不匹配结果
     *
     * @param expectedVersionTag 预期版本标签
     * @param actualVersionTag 实际版本标签
     * @param attempts 尝试次数
     * @param elapsed 耗时
     * @return 不匹配结果
     * @since 2.0
     */
    public static AckResult mismatch(String expectedVersionTag, String actualVersionTag,
                                    int attempts, Duration elapsed) {
        return new AckResult(false, expectedVersionTag, actualVersionTag, attempts, elapsed, "MISMATCH", null);
    }

    /**
     * 创建超时结果
     *
     * @param expectedVersionTag 预期版本标签
     * @param attempts 尝试次数
     * @param elapsed 耗时
     * @return 超时结果
     * @since 2.0
     */
    public static AckResult timeout(String expectedVersionTag, int attempts, Duration elapsed) {
        return new AckResult(false, expectedVersionTag, null, attempts, elapsed, "TIMEOUT", null);
    }

    /**
     * 创建错误结果
     *
     * @param expectedVersionTag 预期版本标签
     * @param attempts 尝试次数
     * @param elapsed 耗时
     * @param error 异常
     * @return 错误结果
     * @since 2.0
     */
    public static AckResult error(String expectedVersionTag, int attempts, Duration elapsed, Throwable error) {
        return new AckResult(false, expectedVersionTag, null, attempts, elapsed, "ERROR", error);
    }

    @Override
    public String toString() {
        return "AckResult{" +
                "success=" + success +
                ", expectedVersionTag='" + expectedVersionTag + '\'' +
                ", actualVersionTag='" + actualVersionTag + '\'' +
                ", attempts=" + attempts +
                ", elapsed=" + elapsed +
                ", reason='" + reason + '\'' +
                '}';
    }
}

