package xyz.firestige.deploy.application.conflict;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.domain.plan.PlanCreationResult;
import xyz.firestige.deploy.domain.shared.exception.ErrorType;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.infrastructure.scheduling.TenantConflictManager;

/**
 * 租户冲突协调器（RF-20: DeploymentApplicationService 拆分）
 * <p>
 * 职责：
 * 1. 检查租户冲突（创建 Plan 前）
 * 2. 注册租户锁（执行 Task 前）
 * 3. 释放租户锁（Task 完成后）
 * <p>
 * 依赖（1个）：
 * - TenantConflictManager：租户冲突管理器
 * <p>
 * 设计说明：
 * - 聚焦于租户级别的冲突检测和协调
 * - 为上层服务提供统一的冲突管理接口
 * - 纯内存操作，性能高效
 *
 * @since RF-20 - 服务拆分
 */
public class TenantConflictCoordinator {

    private static final Logger logger = LoggerFactory.getLogger(TenantConflictCoordinator.class);

    private final TenantConflictManager conflictManager;

    public TenantConflictCoordinator(TenantConflictManager conflictManager) {
        this.conflictManager = conflictManager;
        
        logger.info("[TenantConflictCoordinator] 初始化完成");
    }

    /**
     * 检查创建 Plan 是否允许（给 PlanLifecycleService 使用）
     *
     * @param configs 租户配置列表
     * @return 冲突检查结果，失败时返回 PlanCreationResult.failure
     */
    public PlanCreationResult checkPlanCreation(List<TenantConfig> configs) {
        // 提取租户 ID
        List<TenantId> tenantIds = configs.stream()
            .map(TenantConfig::getTenantId)
            .collect(Collectors.toList());

        // 冲突检测（统一接口，纯内存操作，< 1ms）
        TenantConflictManager.ConflictCheckResult conflictCheck = conflictManager.canCreatePlan(tenantIds);
        if (!conflictCheck.isAllowed()) {
            logger.warn("[TenantConflictCoordinator] 创建 Plan 被拒绝，冲突租户: {}", 
                conflictCheck.getConflictingTenants());
            return PlanCreationResult.failure(
                FailureInfo.of(ErrorType.CONFLICT, conflictCheck.getMessage()),
                "请等待相关 Plan 完成，或移除冲突租户后重试"
            );
        }

        logger.debug("[TenantConflictCoordinator] Plan 创建冲突检查通过，租户数量: {}", tenantIds.size());
        return null; // null 表示无冲突
    }

    /**
     * 检查并注册租户锁（给 PlanExecutionOrchestrator 使用）
     *
     * @param task 任务聚合
     * @return true 表示注册成功，false 表示冲突
     */
    public boolean checkAndRegisterTask(TaskAggregate task) {
        TenantId tenantId = task.getTenantId();
        TaskId taskId = task.getTaskId();

        // 注册租户锁
        if (!conflictManager.registerTask(tenantId, taskId)) {
            TaskId conflictingTaskId = conflictManager.getConflictingTaskId(tenantId);
            logger.warn("[TenantConflictCoordinator] 租户冲突，注册失败: taskId={}, tenantId={}, conflictingTask={}",
                taskId, tenantId, conflictingTaskId);
            return false;
        }

        logger.debug("[TenantConflictCoordinator] Task {} 已注册租户锁: {}", taskId, tenantId);
        return true;
    }

    /**
     * 释放租户锁（给 PlanExecutionOrchestrator 使用）
     *
     * @param tenantId 租户 ID
     */
    public void releaseTenant(TenantId tenantId) {
        conflictManager.releaseTask(tenantId);
        logger.debug("[TenantConflictCoordinator] 释放租户锁: {}", tenantId);
    }

    /**
     * 获取冲突的 Task ID
     *
     * @param tenantId 租户 ID
     * @return 冲突的 Task ID
     */
    public TaskId getConflictingTaskId(TenantId tenantId) {
        return conflictManager.getConflictingTaskId(tenantId);
    }
}
