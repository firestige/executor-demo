package xyz.firestige.executor.state;

/**
 * 任务状态枚举
 */
public enum TaskStatus {

    /**
     * 任务已创建（校验前）
     */
    CREATED("已创建"),

    /**
     * 正在校验
     */
    VALIDATING("校验中"),

    /**
     * 校验失败
     */
    VALIDATION_FAILED("校验失败"),

    /**
     * 待执行（校验通过）
     */
    PENDING("待执行"),

    /**
     * 执行中
     */
    RUNNING("执行中"),

    /**
     * 已暂停
     */
    PAUSED("已暂停"),

    /**
     * 恢复中
     */
    RESUMING("恢复中"),

    /**
     * 已完成
     */
    COMPLETED("已完成"),

    /**
     * 执行失败
     */
    FAILED("执行失败"),

    /**
     * 回滚中
     */
    ROLLING_BACK("回滚中"),

    /**
     * 回滚失败
     */
    ROLLBACK_FAILED("回滚失败"),

    /**
     * 已回滚
     */
    ROLLED_BACK("已回滚"),

    /**
     * 已取消
     */
    CANCELLED("已取消");

    private final String description;

    TaskStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 是否为终态
     */
    public boolean isTerminal() {
        return this == COMPLETED ||
               this == VALIDATION_FAILED ||
               this == ROLLED_BACK ||
               this == CANCELLED;
    }

    /**
     * 是否为失败状态
     */
    public boolean isFailure() {
        return this == VALIDATION_FAILED ||
               this == FAILED ||
               this == ROLLBACK_FAILED;
    }

    /**
     * 是否为成功状态
     */
    public boolean isSuccess() {
        return this == COMPLETED;
    }

    /**
     * 是否可以暂停
     */
    public boolean canPause() {
        return this == RUNNING;
    }

    /**
     * 是否可以恢复
     */
    public boolean canResume() {
        return this == PAUSED;
    }

    /**
     * 是否可以回滚
     */
    public boolean canRollback() {
        return this == FAILED || this == PAUSED || this == COMPLETED;
    }

    /**
     * 是否可以重试
     */
    public boolean canRetry() {
        return this == FAILED || this == ROLLBACK_FAILED;
    }
}

