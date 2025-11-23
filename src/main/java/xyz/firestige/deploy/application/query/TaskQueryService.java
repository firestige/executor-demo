package xyz.firestige.deploy.application.query;

import org.springframework.stereotype.Service;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.facade.TaskStatusInfo;
import xyz.firestige.deploy.infrastructure.persistence.projection.PlanStateProjection;
import xyz.firestige.deploy.infrastructure.persistence.projection.PlanStateProjectionStore;
import xyz.firestige.deploy.infrastructure.persistence.projection.TaskStateProjection;
import xyz.firestige.deploy.infrastructure.persistence.projection.TaskStateProjectionStore;
import xyz.firestige.deploy.infrastructure.persistence.projection.TenantTaskIndexStore;

/**
 * 任务查询服务（最小兜底 API）
 * <p>
 * 职责：
 * - 从投影存储查询 Task/Plan 状态
 * - 组装查询结果 DTO
 * - 不涉及聚合加载，纯查询逻辑
 * <p>
 * 使用场景：
 * - 系统重启后，SRE 手动查询任务状态
 * - 决定是否需要 fromCheckpoint 重试
 * - 不建议常规路径使用（应依赖事件推送）
 *
 * @since T-016 投影型持久化
 */
@Service
public class TaskQueryService {

    private final TaskStateProjectionStore taskProjectionStore;
    private final PlanStateProjectionStore planProjectionStore;
    private final TenantTaskIndexStore tenantTaskIndexStore;

    public TaskQueryService(
            TaskStateProjectionStore taskProjectionStore,
            PlanStateProjectionStore planProjectionStore,
            TenantTaskIndexStore tenantTaskIndexStore) {
        this.taskProjectionStore = taskProjectionStore;
        this.planProjectionStore = planProjectionStore;
        this.tenantTaskIndexStore = tenantTaskIndexStore;
    }

    /**
     * 通过租户 ID 查询 Task 状态
     */
    public TaskStatusInfo queryByTenantId(TenantId tenantId) {
        // 1. 通过索引找到 taskId
        TaskId taskId = tenantTaskIndexStore.get(tenantId);
        if (taskId == null) {
            return TaskStatusInfo.failure("未找到租户对应的任务: " + tenantId);
        }

        // 2. 加载投影
        TaskStateProjection projection = taskProjectionStore.load(taskId);
        if (projection == null) {
            return TaskStatusInfo.failure("未找到任务状态: " + taskId);
        }

        // 3. 组装返回
        TaskStatusInfo info = TaskStatusInfo.success(taskId, projection.getStatus(), "查询成功");
        info.setCurrentStage(projection.getLastCompletedStageIndex() + 1);
        info.setTotalStages(projection.getStageNames().size());
        return info;
    }

    /**
     * 查询 Task 状态（通过 TaskId）
     */
    public TaskStateProjection queryTaskStatus(TaskId taskId) {
        return taskProjectionStore.load(taskId);
    }

    /**
     * 查询 Plan 状态
     */
    public PlanStateProjection queryPlanStatus(PlanId planId) {
        return planProjectionStore.load(planId);
    }

    /**
     * 检查是否有 Checkpoint
     */
    public boolean hasCheckpoint(TenantId tenantId) {
        TaskId taskId = tenantTaskIndexStore.get(tenantId);
        if (taskId == null) {
            return false;
        }

        TaskStateProjection projection = taskProjectionStore.load(taskId);
        return projection != null && projection.getLastCompletedStageIndex() >= 0;
    }
}
