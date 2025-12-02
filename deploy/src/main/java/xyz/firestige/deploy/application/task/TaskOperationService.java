package xyz.firestige.deploy.application.task;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.domain.shared.exception.ErrorType;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskDomainService;
import xyz.firestige.deploy.domain.task.TaskOperationResult;
import xyz.firestige.deploy.domain.task.TaskRepository;
import xyz.firestige.deploy.facade.TaskStatusInfo;
import xyz.firestige.deploy.infrastructure.execution.TaskExecutor;
import xyz.firestige.deploy.infrastructure.execution.TaskWorkerCreationContext;
import xyz.firestige.deploy.infrastructure.execution.TaskWorkerFactory;

/**
 * 任务操作服务（RF-20: DeploymentApplicationService 拆分）
 * <p>
 * 职责：
 * 1. 单个任务的 CRUD 操作
 * 2. 任务状态查询
 * 3. 任务的暂停/恢复/取消/重试/回滚
 * <p>
 * 依赖（4个）：
 * - TaskDomainService：任务领域服务
 * - TaskRepository：任务仓储
 * - TaskRuntimeRepository：任务运行时仓储
 * - TaskWorkerFactory：任务执行器工厂（T-015 新增）
 * <p>
 * 设计说明：
 * - 聚焦于单个任务的操作
 * - 不涉及 Plan 级别的编排
 * - 事务边界在方法级别
 * - 重试/回滚异步执行，通过领域事件通知结果
 *
 * @since RF-20 - 服务拆分
 */
public class TaskOperationService {

    private static final Logger logger = LoggerFactory.getLogger(TaskOperationService.class);

    private final TaskDomainService taskDomainService;
    private final TaskRepository taskRepository;
    private final TaskWorkerFactory taskWorkerFactory;

    public TaskOperationService(
            TaskDomainService taskDomainService,
            TaskRepository taskRepository,
            TaskWorkerFactory taskWorkerFactory) {
        this.taskDomainService = taskDomainService;
        this.taskRepository = taskRepository;
        this.taskWorkerFactory = taskWorkerFactory;

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
     * 回滚任务（T-035: 无状态执行器适配）
     * <p>
     * T-015: 移除 executorCreator 参数，内部创建 TaskExecutor
     * T-028: 使用传入的 version 作为回滚目标版本（planVersion）
     * T-032: 使用标志位驱动，统一通过 execute() 方法
     * T-035: 修改方法名，添加 lastCompletedStageName 参数
     * 通过领域事件通知回滚结果（TaskRollingBackEvent / TaskRolledBackEvent / TaskRollbackFailedEvent）
     * <p>
     * 设计说明：
     * - 回滚不是逆向操作，而是用旧版本配置重新执行 stages
     * - lastCompletedStageName 为 null 时，全部回滚
     *
     * @param oldConfig 旧版本配置（回滚目标）
     * @param lastCompletedStageName 最近完成的 Stage 名称（null 表示全部回滚）
     * @param version 回滚目标版本（planVersion）
     * @return 操作结果（立即返回，实际回滚异步执行）
     */
    @Transactional
    public TaskOperationResult rollbackTask(
        TenantConfig oldConfig,
        String lastCompletedStageName,
        String version
    ) {
        TenantId tenantId = oldConfig.getTenantId();
        logger.info("[TaskOperationService] 回滚租户任务（异步）: {}, version: {}", tenantId, version);

        // Step 1: 调用领域服务准备回滚（传递 planVersion）
        TaskWorkerCreationContext context = taskDomainService.prepareRollback(
            oldConfig,
            lastCompletedStageName,
            version
        );
        if (context == null) {
            return TaskOperationResult.failure(
                null,
                FailureInfo.of(ErrorType.VALIDATION_ERROR, "未找到租户任务"),
                "未找到租户任务"
            );
        }

        // ✅ T-032: 设置回滚标志位
        context.getRuntimeContext().requestRollback(version);

        // Step 2: 创建 TaskExecutor（内部注入）
        TaskExecutor executor = context.hasExistingExecutor()
            ? context.getExistingExecutor()
            : taskWorkerFactory.create(context);

        // Step 3: 异步执行回滚
        CompletableFuture.runAsync(() -> {
            try {
                // ✅ T-032: 统一通过 execute() 方法，根据标志位驱动回滚
                var result = executor.execute();
                logger.info("[TaskOperationService] 租户任务回滚完成: {}, status: {}",
                            tenantId, result.getFinalStatus());
            } catch (Exception e) {
                logger.error("[TaskOperationService] 租户任务回滚异常: {}", tenantId, e);
            }
        });

        logger.info("[TaskOperationService] 租户任务回滚已提交异步执行: {}", tenantId);
        return TaskOperationResult.success(
            context.getTask().getTaskId(),
            context.getTask().getStatus(),
            "回滚任务已提交异步执行，请监听领域事件获取结果"
        );
    }

    /**
     * 重试任务（T-035: 无状态执行器适配）
     * <p>
     * T-015: 移除 executorCreator 参数，内部创建 TaskExecutor
     * T-032: 使用标志位驱动，统一通过 execute() 方法
     * T-035: 修改方法名，移除 taskId 参数
     * 通过领域事件通知重试结果（TaskRetryStartedEvent / TaskCompletedEvent / TaskFailedEvent）
     *
     * @param config 用于重试的配置（和上次执行一样）
     * @param lastCompletedStageName 最近完成的 Stage 名称（null 表示从头重试）
     * @return 操作结果（立即返回，实际重试异步执行）
     */
    @Transactional
    public TaskOperationResult retryTask(TenantConfig config, String lastCompletedStageName) {
        TenantId tenantId = config.getTenantId();
        logger.info("[TaskOperationService] 重试租户任务（异步）: {}, from: {}",
                    tenantId, lastCompletedStageName);

        // TODO T-035: 需要在 TaskDomainService 中添加新方法：
        //  prepareRetry(TenantConfig config, String lastCompletedStageName)
        // Step 1: 调用领域服务准备重试
        TaskWorkerCreationContext context = taskDomainService.prepareRetry(config, lastCompletedStageName);
        if (context == null) {
            return TaskOperationResult.failure(
                null,
                FailureInfo.of(ErrorType.VALIDATION_ERROR, "未找到租户任务"),
                "未找到租户任务"
            );
        }

        // ✅ T-032: 设置重试标志位
        // T-035: lastCompletedStageName 不为 null 表示从检查点继续
        context.getRuntimeContext().requestRetry(lastCompletedStageName != null);

        // Step 2: 创建 TaskExecutor（内部注入）
        TaskExecutor executor = context.hasExistingExecutor()
            ? context.getExistingExecutor()
            : taskWorkerFactory.create(context);

        // Step 3: 异步执行重试
        CompletableFuture.runAsync(() -> {
            try {
                // ✅ T-032: 统一通过 execute() 方法，根据标志位驱动重试
                var result = executor.execute();
                logger.info("[TaskOperationService] 租户任务重试完成: {}, status: {}",
                            tenantId, result.getFinalStatus());
            } catch (Exception e) {
                logger.error("[TaskOperationService] 租户任务重试异常: {}", tenantId, e);
            }
        });

        logger.info("[TaskOperationService] 租户任务重试已提交异步执行: {}", tenantId);
        return TaskOperationResult.success(
            context.getTask().getTaskId(),
            context.getTask().getStatus(),
            "重试任务已提交异步执行，请监听领域事件获取结果"
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
