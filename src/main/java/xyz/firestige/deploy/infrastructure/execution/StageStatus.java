package xyz.firestige.deploy.infrastructure.execution;

/**
 * Stage 执行状态枚举
 */
public enum StageStatus {

    /**
     * 待执行
     */
    PENDING("待执行"),

    /**
     * 执行中
     */
    RUNNING("执行中"),

    /**
     * 已完成
     */
    COMPLETED("已完成"),

    /**
     * 失败
     */
    FAILED("失败"),

    /**
     * 已跳过
     */
    SKIPPED("已跳过"),

    /**
     * 已回滚
     */
    ROLLED_BACK("已回滚");

    private final String description;

    StageStatus(String description) {
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
               this == FAILED ||
               this == SKIPPED ||
               this == ROLLED_BACK;
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
        return this == FAILED;
    }
}

