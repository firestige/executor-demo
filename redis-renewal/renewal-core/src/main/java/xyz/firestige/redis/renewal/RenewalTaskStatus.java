package xyz.firestige.redis.renewal;

/**
 * 续期任务状态
 *
 * @author AI
 * @since 1.0
 */
public enum RenewalTaskStatus {
    /**
     * 等待中（已注册但未开始）
     */
    PENDING,

    /**
     * 运行中
     */
    RUNNING,

    /**
     * 已暂停
     */
    PAUSED,

    /**
     * 已完成（满足停止条件）
     */
    COMPLETED,

    /**
     * 已取消
     */
    CANCELLED,

    /**
     * 失败
     */
    FAILED
}

