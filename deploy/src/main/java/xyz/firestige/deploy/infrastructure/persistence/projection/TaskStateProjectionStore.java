package xyz.firestige.deploy.infrastructure.persistence.projection;

import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;

import java.util.List;

/**
 * Task 状态投影存储接口（技术无关）
 * <p>
 * 职责：
 * - 持久化 Task 状态投影供查询
 * - 不返回聚合根，仅返回投影 DTO
 * - 不参与业务不变式，仅供查询使用
 * <p>
 * 实现可以是：
 * - Redis Hash（默认）
 * - JDBC Table
 * - InMemory Map（测试）
 * <p>
 * 设计原则：
 * - 接口在 Infrastructure 层（技术关注点）
 * - 命名通用，不绑定具体技术
 * - 支持实现替换（通过 Spring 条件注入）
 *
 * @since T-016 投影型持久化
 */
public interface TaskStateProjectionStore {

    /**
     * 保存 Task 状态投影
     *
     * @param projection Task 状态投影
     */
    void save(TaskStateProjection projection);

    /**
     * 加载 Task 状态投影
     *
     * @param taskId 任务 ID
     * @return Task 状态投影，不存在返回 null
     */
    TaskStateProjection load(TaskId taskId);

    /**
     * 通过租户 ID 查询 Task 状态（需要索引支持）
     *
     * @param tenantId 租户 ID
     * @return Task 状态投影，不存在返回 null
     */
    TaskStateProjection findByTenantId(TenantId tenantId);

    /**
     * 删除投影（Task 完成后清理）
     *
     * @param taskId 任务 ID
     */
    void remove(TaskId taskId);

    /**
     * 批量保存（性能优化，可选）
     *
     * @param projections 投影列表
     */
    default void saveAll(List<TaskStateProjection> projections) {
        if (projections != null) {
            projections.forEach(this::save);
        }
    }

    /**
     * 检查投影是否存在
     *
     * @param taskId 任务 ID
     * @return true=存在，false=不存在
     */
    default boolean exists(TaskId taskId) {
        return load(taskId) != null;
    }
}

