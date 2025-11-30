package xyz.firestige.deploy.domain.task;

/**
 * 任务状态枚举
 * <p>
 * 状态转换说明：
 * - CREATED → PENDING: 创建后准备执行
 * - PENDING → RUNNING: 开始执行
 * - RUNNING → PAUSED: 协作式暂停（Stage边界）
 * - PAUSED → RUNNING: 恢复执行（原子操作，无中间状态）
 * - RUNNING → COMPLETED: 所有Stage完成
 * - RUNNING → FAILED: Stage失败
 * - FAILED → PENDING → RUNNING: 重试
 * <p>
 * 回滚设计说明（T-032 状态机简化）：
 * - 回滚不使用专用状态，而是通过 Task.rollbackIntent 标志位标识
 * - 回滚的状态转换：FAILED → PENDING → RUNNING → COMPLETED（与正常执行相同）
 * - 回滚通过领域事件对外可见：TaskRollbackStarted / TaskRolledBack
 * - 这样简化了状态机，避免状态膨胀和复杂的转换规则
 * <p>
 * 设计说明：
 * - 校验在创建时完成，不需要独立状态
 * - 恢复是原子操作，不需要 RESUMING 中间状态
 */
public enum TaskStatus {

    /**
     * 任务已创建（初始状态）
     */
    CREATED("已创建"),

    /**
     * 待执行（准备就绪）
     */
    PENDING("待执行"),

    /**
     * 执行中
     */
    RUNNING("执行中"),

    /**
     * 已暂停（在Stage边界暂停）
     */
    PAUSED("已暂停"),

    /**
     * 已完成（终态）
     */
    COMPLETED("已完成"),

    /**
     * 执行失败
     */
    FAILED("执行失败"),


    /**
     * 已取消（终态）
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
        return this == COMPLETED || this == CANCELLED;
    }

    /**
     * 是否为失败状态
     */
    public boolean isFailure() {
        return this == FAILED;
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
        return this == FAILED;
    }
}

