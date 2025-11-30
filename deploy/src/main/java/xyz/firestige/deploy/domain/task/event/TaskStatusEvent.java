package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.shared.event.DomainEvent;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskInfo;
import xyz.firestige.deploy.domain.task.TaskInfoView;
import xyz.firestige.deploy.domain.task.TaskStatus;

/**
 * 任务状态事件基类
 * <p>
 * 所有任务状态相关事件的基类
 * <p>
 * T-036: 重构为使用 TaskInfoView（宽表设计）
 * - 支持新构造器：TaskStatusEvent(TaskAggregate task)
 * - 保留旧构造器：TaskStatusEvent(TaskInfo info) - 向后兼容
 */
public abstract class TaskStatusEvent extends DomainEvent {

    // T-036: 使用 TaskInfoView 替代 TaskInfo
    private final TaskInfoView taskInfo;

    // ============================================
    // 新构造器（T-036）
    // ============================================

    /**
     * 新构造器：接受 TaskAggregate
     * <p>
     * 事件内部调用 TaskInfoView.from(task) 创建视图
     *
     * @param task Task 聚合根
     * @since T-036
     */
    protected TaskStatusEvent(TaskAggregate task) {
        super();
        this.taskInfo = TaskInfoView.from(task);
    }

    /**
     * 新构造器：接受 TaskAggregate + message
     *
     * @param task Task 聚合根
     * @param message 事件消息
     * @since T-036
     */
    protected TaskStatusEvent(TaskAggregate task, String message) {
        this(task);
        setMessage(message);
    }

    // ============================================
    // 旧构造器（向后兼容，标记为 Deprecated）
    // ============================================

    /**
     * 旧构造器：接受 TaskInfo
     * <p>
     * 为了向后兼容保留，将 TaskInfo 转换为 TaskInfoView
     *
     * @param info TaskInfo 对象
     * @deprecated 使用 TaskStatusEvent(TaskAggregate task) 代替
     */
    @Deprecated
    protected TaskStatusEvent(TaskInfo info) {
        super();
        // 将 TaskInfo 转换为 TaskInfoView
        this.taskInfo = convertToView(info);
    }

    /**
     * 旧构造器：接受 TaskInfo + message
     *
     * @param info TaskInfo 对象
     * @param message 事件消息
     * @deprecated 使用 TaskStatusEvent(TaskAggregate task, String message) 代替
     */
    @Deprecated
    protected TaskStatusEvent(TaskInfo info, String message) {
        this(info);
        setMessage(message);
    }

    // ============================================
    // 辅助方法
    // ============================================

    /**
     * 将 TaskInfo 转换为 TaskInfoView（向后兼容）
     */
    private static TaskInfoView convertToView(TaskInfo info) {
        if (info == null) {
            return null;
        }
        return TaskInfoView.builder()
            .taskId(info.getTaskId().getValue())
            .tenantId(info.getTenantId().getValue())
            .planId(info.getPlanId().getValue())
            .deployUnitName(info.getDeployUnitName())
            .deployUnitVersion(info.getDeployUnitVersion())
            .status(info.getStatus())
            .build();
    }

    // ============================================
    // Getter 方法（保持向后兼容）
    // ============================================

    public TaskId getTaskId() {
        return TaskId.of(taskInfo.getTaskId());
    }

    public String getTaskIdAsString() {
        return taskInfo.getTaskId();
    }

    public TenantId getTenantId() {
        return TenantId.of(taskInfo.getTenantId());
    }

    public String getTenantIdAsString() {
        return taskInfo.getTenantId();
    }

    public PlanId getPlanId() {
        return PlanId.of(taskInfo.getPlanId());
    }

    public String getPlanIdAsString() {
        return taskInfo.getPlanId();
    }

    public TaskStatus getStatus() {
        return taskInfo.getStatus();
    }

    public String getStatusAsString() {
        return taskInfo.getStatus().toString();
    }

    /**
     * 获取 TaskInfoView（新增）
     * <p>
     * 子类可以访问完整的视图数据
     *
     * @return TaskInfoView 实例
     * @since T-036
     */
    protected TaskInfoView getTaskInfo() {
        return taskInfo;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" +
                "eventId='" + this.getEventId() + '\'' +
                ", taskId='" + this.getTaskIdAsString() + '\'' +
                ", tenantId='" + this.getTenantIdAsString() + '\'' +
                ", planId='" + this.getPlanIdAsString() + '\'' +
                ", status=" + this.getStatusAsString() +
                ", timestamp=" + this.getFormattedTimestamp() +
                '}';
    }
}
