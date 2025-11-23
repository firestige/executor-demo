package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.task.TaskInfo;

import java.util.Collections;
import java.util.List;

/**
 * Task 创建事件
 * <p>
 * 触发时机：
 * - TaskDomainService.createTask() 成功后
 * <p>
 * 用途：
 * - 驱动投影更新（TaskStateProjectionUpdater）
 * - 建立 TenantId → TaskId 索引
 * - 初始化 Task 状态投影
 * <p>原始语义：携带该 Task 的全部 Stage 名称列表，用于
 * 1. 构建状态投影（初始阶段列表）
 * 2. 外部或查询服务在重启后还原执行拓扑
 *
 * @since T-016 投影型持久化
 */
public class TaskCreatedEvent extends TaskStatusEvent {

    private final List<String> stageNames; // 按执行顺序的全量阶段名称

    public TaskCreatedEvent(TaskInfo info) {
        super(info, "Task created");
        this.stageNames = List.of();
    }

    public TaskCreatedEvent(TaskInfo info, String message) {
        super(info, message);
        this.stageNames = List.of();
    }

    public TaskCreatedEvent(TaskInfo info, List<String> stageNames) {
        super(info, "Task created");
        this.stageNames = stageNames != null ? List.copyOf(stageNames) : List.of();
    }

    // 可选：自定义描述
    public TaskCreatedEvent(TaskInfo info, List<String> stageNames, String message) {
        super(info, message);
        this.stageNames = stageNames != null ? List.copyOf(stageNames) : List.of();
    }

    public List<String> getStageNames() {
        return Collections.unmodifiableList(stageNames);
    }

    @Override
    public String toString() {
        return "TaskCreatedEvent{" +
                "taskId=" + getTaskId() +
                ", tenantId=" + getTenantId() +
                ", planId=" + getPlanId() +
                ", stageCount=" + stageNames.size() +
                '}';
    }
}
