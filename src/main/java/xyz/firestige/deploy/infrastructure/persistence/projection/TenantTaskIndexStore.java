package xyz.firestige.deploy.infrastructure.persistence.projection;

import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;

/**
 * TenantId → TaskId 索引存储接口（技术无关）
 * <p>
 * 职责：
 * - 维护租户 ID 到任务 ID 的映射关系
 * - 支持外部系统通过租户 ID 查询任务状态
 * <p>
 * 实现可以是：
 * - Redis String（默认）
 * - JDBC Table
 * - InMemory Map（测试）
 * <p>
 * 使用场景：
 * 外部系统传入 tenantId → 查询 taskId → 加载 TaskStateProjection
 *
 * @since T-016 投影型持久化
 */
public interface TenantTaskIndexStore {

    /**
     * 建立租户 ID 到任务 ID 的映射
     *
     * @param tenantId 租户 ID
     * @param taskId   任务 ID
     */
    void put(TenantId tenantId, TaskId taskId);

    /**
     * 通过租户 ID 查询任务 ID
     *
     * @param tenantId 租户 ID
     * @return 任务 ID，不存在返回 null
     */
    TaskId get(TenantId tenantId);

    /**
     * 删除索引（Task 完成后清理）
     *
     * @param tenantId 租户 ID
     */
    void remove(TenantId tenantId);

    /**
     * 检查索引是否存在
     *
     * @param tenantId 租户 ID
     * @return true=存在，false=不存在
     */
    default boolean exists(TenantId tenantId) {
        return get(tenantId) != null;
    }
}

