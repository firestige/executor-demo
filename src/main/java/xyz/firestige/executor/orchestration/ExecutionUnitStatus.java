package xyz.firestige.executor.orchestration;

/**
 * 执行单状态枚举
 */
public enum ExecutionUnitStatus {

    /**
     * 已创建
     */
    CREATED("已创建"),

    /**
     * 校验失败
     */
    VALIDATION_FAILED("校验失败"),

    /**
     * 已调度
     */
    SCHEDULED("已调度"),

    /**
     * 运行中
     */
    RUNNING("运行中"),

    /**
     * 已暂停
     */
    PAUSED("已暂停"),

    /**
     * 已完成
     */
    COMPLETED("已完成"),

    /**
     * 失败
     */
    FAILED("失败"),

    /**
     * 已取消
     */
    CANCELLED("已取消");

    private final String description;

    ExecutionUnitStatus(String description) {
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
               this == FAILED ||
               this == CANCELLED;
    }

    /**
     * 是否为成功状态
     */
    public boolean isSuccess() {
        return this == COMPLETED;
    }

    /**
     * 是否为失败状态
     */
    public boolean isFailure() {
        return this == VALIDATION_FAILED || this == FAILED;
    }
}

