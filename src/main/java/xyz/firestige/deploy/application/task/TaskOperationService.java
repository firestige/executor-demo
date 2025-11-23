package xyz.firestige.deploy.application.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import xyz.firestige.deploy.domain.shared.exception.ErrorType;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskDomainService;
import xyz.firestige.deploy.domain.task.TaskOperationResult;
import xyz.firestige.deploy.domain.task.TaskRepository;
import xyz.firestige.deploy.domain.task.TaskRuntimeRepository;
import xyz.firestige.deploy.domain.task.TaskStatus;
import xyz.firestige.deploy.facade.TaskStatusInfo;
import xyz.firestige.deploy.infrastructure.execution.TaskExecutor;
import xyz.firestige.deploy.infrastructure.execution.TaskWorkerCreationContext;

import java.util.List;
import java.util.function.Function;

/**
 * 任务操作服务（RF-20: DeploymentApplicationService 拆分）
 * <p>
 * 职责：
 * 1. 单个任务的 CRUD 操作
 * 2. 任务状态查询
 * 3. 任务的暂停/恢复/取消/重试/回滚
 * <p>
 * 依赖（3个）：
 * - TaskDomainService：任务领域服务
 * - TaskRepository：任务仓储
 * - TaskRuntimeRepository：任务运行时仓储
 * <p>
 * 设计说明：
 * - 聚焦于单个任务的操作
 * - 不涉及 Plan 级别的编排
 * - 事务边界在方法级别
 *
 * @since RF-20 - 服务拆分
 */
public class TaskOperationService {

    private static final Logger logger = LoggerFactory.getLogger(TaskOperationService.class);

    private final TaskDomainService taskDomainService;
    private final TaskRepository taskRepository;
    private final TaskRuntimeRepository taskRuntimeRepository;

    public TaskOperationService(
            TaskDomainService taskDomainService,
            TaskRepository taskRepository,
            TaskRuntimeRepository taskRuntimeRepository) {
        this.taskDomainService = taskDomainService;
        this.taskRepository = taskRepository;
        this.taskRuntimeRepository = taskRuntimeRepository;
        
        logger.info("[TaskOperationService] 初始化完成");
    }

    // ========== 任务级别操作 ==========

    /**
     * 根据租户 ID 暂停任务
     *
     * @param tenantId 租户 ID
     * @return 操作结果
     */
    @Transactional
    public TaskOperationResult pauseTaskByTenant(TenantId tenantId) {
        logger.info("[TaskOperationService] 暂停租户任务: {}", tenantId);
        return taskDomainService.pauseTaskByTenant(tenantId);
    }

    /**
     * 根据租户 ID 恢复任务
     *
     * @param tenantId 租户 ID
     * @return 操作结果
     */
    @Transactional
    public TaskOperationResult resumeTaskByTenant(TenantId tenantId) {
        logger.info("[TaskOperationService] 恢复租户任务: {}", tenantId);
        return taskDomainService.resumeTaskByTenant(tenantId);
    }

    /**
     * 根据租户 ID 回滚任务
     *
     * @param tenantId 租户 ID
     * @param executorCreator TaskExecutor 创建器（由外部提供）
     * @return 操作结果
     */
    @Transactional
    public TaskOperationResult rollbackTaskByTenant(
            TenantId tenantId,
            Function<TaskWorkerCreationContext, TaskExecutor> executorCreator) {
        logger.info("[TaskOperationService] 回滚租户任务: {}", tenantId);

        // Step 1: 调用领域服务准备回滚
        TaskWorkerCreationContext context = taskDomainService.prepareRollbackByTenant(tenantId);
        if (context == null) {
            return TaskOperationResult.failure(
                null,
                FailureInfo.of(ErrorType.VALIDATION_ERROR, "未找到租户任务"),
                "未找到租户任务"
            );
        }

        // Step 2: 创建或复用 TaskExecutor
        TaskExecutor executor = context.hasExistingExecutor()
            ? context.getExistingExecutor()
            : executorCreator.apply(context);

        // Step 3: 执行回滚
        var result = executor.invokeRollback();

        // Step 4: 发布回滚结果事件
        TaskStatus finalStatus = context.getTask().getStatus();
        if (finalStatus == TaskStatus.ROLLED_BACK) {
            // 聚合产生的事件已由 TaskDomainService 发布
        } else if (finalStatus == TaskStatus.ROLLBACK_FAILED) {
            // 记录失败日志
        }

        logger.info("[TaskOperationService] 租户任务回滚结束: {}, status: {}",
                    tenantId, result.getFinalStatus());
        return TaskOperationResult.success(
            context.getTask().getTaskId(),
            finalStatus,
            "租户任务回滚结束: " + result.getFinalStatus()
        );
    }

    /**
     * 根据租户 ID 重试任务
     *
     * @param tenantId 租户 ID
     * @param fromCheckpoint 是否从 checkpoint 恢复
     * @param executorCreator TaskExecutor 创建器（由外部提供）
     * @return 操作结果
     */
    @Transactional
    public TaskOperationResult retryTaskByTenant(
            TenantId tenantId,
            boolean fromCheckpoint,
            Function<TaskWorkerCreationContext, TaskExecutor> executorCreator) {
        logger.info("[TaskOperationService] 重试租户任务: {}, fromCheckpoint: {}",
                    tenantId, fromCheckpoint);

        // Step 1: 调用领域服务准备重试
        TaskWorkerCreationContext context = taskDomainService.prepareRetryByTenant(tenantId, fromCheckpoint);
        if (context == null) {
            return TaskOperationResult.failure(
                null,
                FailureInfo.of(ErrorType.VALIDATION_ERROR, "未找到租户任务"),
                "未找到租户任务"
            );
        }

        // Step 2: 创建或复用 TaskExecutor
        TaskExecutor executor = context.hasExistingExecutor()
            ? context.getExistingExecutor()
            : executorCreator.apply(context);

        // Step 3: 执行重试
        var result = executor.retry(fromCheckpoint);

        logger.info("[TaskOperationService] 租户任务重试启动: {}, status: {}",
                    tenantId, result.getFinalStatus());
        return TaskOperationResult.success(
            context.getTask().getTaskId(),
            result.getFinalStatus(),
            "租户任务重试启动"
        );
    }

    /**
     * 根据租户 ID 取消任务
     *
     * @param tenantId 租户 ID
     * @return 操作结果
     */
    @Transactional
    public TaskOperationResult cancelTaskByTenant(TenantId tenantId) {
        logger.info("[TaskOperationService] 取消租户任务: {}", tenantId);
        return taskDomainService.cancelTaskByTenant(tenantId);
    }

    /**
     * 查询任务状态
     *
     * @param taskId 任务 ID
     * @return 任务状态信息
     */
    public TaskStatusInfo queryTaskStatus(TaskId taskId) {
        logger.debug("[TaskOperationService] 查询任务状态: {}", taskId);
        return taskDomainService.queryTaskStatus(taskId);
    }

    /**
     * 根据租户 ID 查询任务状态
     *
     * @param tenantId 租户 ID
     * @return 任务状态信息
     */
    public TaskStatusInfo queryTaskStatusByTenant(TenantId tenantId) {
        logger.debug("[TaskOperationService] 查询租户任务状态: {}", tenantId);
        return taskDomainService.queryTaskStatusByTenant(tenantId);
    }

    /**
     * 根据 Plan ID 查询所有任务
     *
     * @param planId Plan ID
     * @return 任务列表
     */
    public List<TaskAggregate> getTasksByPlanId(PlanId planId) {
        logger.debug("[TaskOperationService] 查询 Plan 任务: {}", planId);
        return taskRepository.findByPlanId(planId);
    }
}
