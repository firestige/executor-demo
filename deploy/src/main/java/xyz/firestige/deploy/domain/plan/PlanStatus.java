package xyz.firestige.deploy.domain.plan;

/**
 * Plan 聚合的状态
 * <p>
 * 状态说明：
 * - CREATED: 初始状态，Plan 创建后
 * - READY: 准备就绪，至少包含1个Task
 * - RUNNING: 运行中，Task 正在执行
 * - PAUSED: 已暂停，所有运行中的Task已暂停
 * - COMPLETED: 已完成，所有Task成功
 * - FAILED: 失败，Plan 执行失败
 * - CANCELLED: 已取消，用户主动取消
 * <p>
 * 设计说明：
 * - Plan 不感知 Task 的内部状态（如校验、回滚）
 * - Task 的回滚状态封装在 Task 聚合内部
 * - 部分失败的判断在应用层处理，Plan 保持 RUNNING 状态
 */
public enum PlanStatus {
    /**
     * 初始状态
     */
    CREATED,

    /**
     * 准备就绪（至少包含1个Task）
     */
    READY,

    /**
     * 运行中
     */
    RUNNING,

    /**
     * 已暂停
     */
    PAUSED,

    /**
     * 已完成（终态）
     */
    COMPLETED,

    /**
     * 失败（终态）
     */
    FAILED,

    /**
     * 已取消（终态）
     */
    CANCELLED;

    /**
     * 是否为终态
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
