package xyz.firestige.deploy.domain.task;

import java.util.Objects;

/**
 * 重试策略值对象（Value Object）
 * <p>
 * 职责：
 * 1. 封装 retryCount 和 maxRetry
 * 2. 提供重试判断和计数增加方法
 * 3. 不可变对象，线程安全
 * <p>
 * 规则：maxRetry = null 表示使用全局配置
 *
 * @since Phase 18 - RF-13
 */
public final class RetryPolicy {

    private final int retryCount;
    private final Integer maxRetry; // null = 使用全局配置

    private RetryPolicy(int retryCount, Integer maxRetry) {
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount 不能为负数");
        }
        if (maxRetry != null && maxRetry < 0) {
            throw new IllegalArgumentException("maxRetry 不能为负数");
        }
        this.retryCount = retryCount;
        this.maxRetry = maxRetry;
    }

    // ============================================
    // 工厂方法
    // ============================================

    /**
     * 创建初始重试策略（未重试）
     *
     * @param maxRetry 最大重试次数（null = 使用全局配置）
     * @return RetryPolicy 实例
     */
    public static RetryPolicy initial(Integer maxRetry) {
        return new RetryPolicy(0, maxRetry);
    }

    /**
     * 创建指定重试次数的策略
     *
     * @param retryCount 当前重试次数
     * @param maxRetry 最大重试次数
     * @return RetryPolicy 实例
     */
    public static RetryPolicy of(int retryCount, Integer maxRetry) {
        return new RetryPolicy(retryCount, maxRetry);
    }

    // ============================================
    // 业务方法
    // ============================================

    /**
     * 判断是否可以重试
     *
     * @param globalMaxRetry 全局最大重试次数（当 maxRetry = null 时使用）
     * @return true = 可以重试，false = 已达上限
     */
    public boolean canRetry(Integer globalMaxRetry) {
        int effectiveMaxRetry = getEffectiveMaxRetry(globalMaxRetry);
        return retryCount < effectiveMaxRetry;
    }

    /**
     * 增加重试次数（返回新对象，不可变）
     *
     * @return 新的 RetryPolicy（retryCount + 1）
     */
    public RetryPolicy incrementRetryCount() {
        return new RetryPolicy(retryCount + 1, maxRetry);
    }

    /**
     * 重置重试次数（用于某些场景）
     *
     * @return 新的 RetryPolicy（retryCount = 0）
     */
    public RetryPolicy reset() {
        return new RetryPolicy(0, maxRetry);
    }

    /**
     * 获取生效的最大重试次数
     * <p>
     * 优先级：Task.maxRetry > globalMaxRetry > Integer.MAX_VALUE
     *
     * @param globalMaxRetry 全局最大重试次数
     * @return 生效的最大重试次数
     */
    public int getEffectiveMaxRetry(Integer globalMaxRetry) {
        if (maxRetry != null) {
            return maxRetry;
        }
        if (globalMaxRetry != null) {
            return globalMaxRetry;
        }
        return Integer.MAX_VALUE; // 无限制
    }

    /**
     * 判断是否已达最大重试次数
     *
     * @param globalMaxRetry 全局最大重试次数
     * @return true = 已达上限，false = 未达上限
     */
    public boolean isMaxRetryReached(Integer globalMaxRetry) {
        return !canRetry(globalMaxRetry);
    }

    // ============================================
    // Getter 方法
    // ============================================

    public int getRetryCount() {
        return retryCount;
    }

    public Integer getMaxRetry() {
        return maxRetry;
    }

    // ============================================
    // equals / hashCode / toString
    // ============================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RetryPolicy that = (RetryPolicy) o;
        return retryCount == that.retryCount &&
               Objects.equals(maxRetry, that.maxRetry);
    }

    @Override
    public int hashCode() {
        return Objects.hash(retryCount, maxRetry);
    }

    @Override
    public String toString() {
        String maxRetryStr = maxRetry != null ? String.valueOf(maxRetry) : "global";
        return String.format("RetryPolicy[%d/%s]", retryCount, maxRetryStr);
    }
}
