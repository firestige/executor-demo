package xyz.firestige.executor.facade;

import xyz.firestige.dto.deploy.TenantDeployConfig;

import java.util.List;

/**
 * 部署任务 Facade 接口
 * 对外提供统一的蓝绿发布配置下发接口
 */
public interface DeploymentTaskFacade {

    /**
     * 创建切换任务
     *
     * @param configs 租户部署配置列表
     * @return 任务创建结果
     */
    TaskCreationResult createSwitchTask(List<TenantDeployConfig> configs);

    /**
     * 根据租户 ID 暂停任务
     *
     * @param tenantId 租户 ID
     * @return 任务操作结果
     */
    TaskOperationResult pauseTaskByTenant(String tenantId);

    /**
     * 根据计划 ID 暂停任务
     *
     * @param planId 计划 ID
     * @return 任务操作结果
     */
    TaskOperationResult pauseTaskByPlan(Long planId);

    /**
     * 根据租户 ID 恢复任务
     *
     * @param tenantId 租户 ID
     * @return 任务操作结果
     */
    TaskOperationResult resumeTaskByTenant(String tenantId);

    /**
     * 根据计划 ID 恢复任务
     *
     * @param planId 计划 ID
     * @return 任务操作结果
     */
    TaskOperationResult resumeTaskByPlan(Long planId);

    /**
     * 根据租户 ID 回滚任务
     *
     * @param tenantId 租户 ID
     * @return 任务操作结果
     */
    TaskOperationResult rollbackTaskByTenant(String tenantId);

    /**
     * 根据计划 ID 回滚任务
     *
     * @param planId 计划 ID
     * @return 任务操作结果
     */
    TaskOperationResult rollbackTaskByPlan(Long planId);

    /**
     * 根据租户 ID 重试任务
     *
     * @param tenantId 租户 ID
     * @param fromCheckpoint 是否从检查点重试
     * @return 任务操作结果
     */
    TaskOperationResult retryTaskByTenant(String tenantId, boolean fromCheckpoint);

    /**
     * 根据计划 ID 重试任务
     *
     * @param planId 计划 ID
     * @param fromCheckpoint 是否从检查点重试
     * @return 任务操作结果
     */
    TaskOperationResult retryTaskByPlan(Long planId, boolean fromCheckpoint);

    /**
     * 查询任务状态
     *
     * @param executionUnitId 执行单 ID
     * @return 任务状态信息
     */
    TaskStatusInfo queryTaskStatus(String executionUnitId);

    /**
     * 根据租户 ID 查询任务状态
     *
     * @param tenantId 租户 ID
     * @return 任务状态信息
     */
    TaskStatusInfo queryTaskStatusByTenant(String tenantId);

    /**
     * 取消任务
     *
     * @param executionUnitId 执行单 ID
     * @return 任务操作结果
     */
    TaskOperationResult cancelTask(String executionUnitId);

    /**
     * 根据租户 ID取消任务
     *
     * @param tenantId 租户 ID
     * @return 任务操作结果
     */
    TaskOperationResult cancelTaskByTenant(String tenantId);
}

