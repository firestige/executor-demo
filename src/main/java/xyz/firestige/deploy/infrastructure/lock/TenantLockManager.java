package xyz.firestige.deploy.infrastructure.lock;

import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;

import java.time.Duration;

/**
 * 租户锁管理接口（技术无关）
 * <p>
 * 职责：
 * - 确保同一租户在任意时刻只有一个任务在执行
 * - 支持分布式场景（多实例部署）
 * - 防止锁泄漏（TTL 自动释放）
 * <p>
 * 实现可以是：
 * - Redis SET NX（默认，支持多实例）
 * - JDBC Table（可选）
 * - Zookeeper（可选）
 * - InMemory ConcurrentHashMap（单实例测试）
 * <p>
 * 设计原则：
 * - 接口在 Infrastructure 层（技术关注点）
 * - 命名通用，不绑定具体技术
 * - 支持实现替换（通过 Spring 条件注入）
 * <p>
 * 替换关系：
 * - 本接口替换现有的 TenantConflictManager（内存锁）
 *
 * @since T-016 投影型持久化
 */
public interface TenantLockManager {

    /**
     * 尝试获取租户锁
     * <p>
     * 原子操作，多实例安全
     *
     * @param tenantId 租户 ID
     * @param taskId   任务 ID（标识锁持有者）
     * @param ttl      锁过期时间（防止崩溃后泄漏）
     * @return true=成功获取，false=已被占用
     */
    boolean tryAcquire(TenantId tenantId, TaskId taskId, Duration ttl);

    /**
     * 释放租户锁
     * <p>
     * 只有锁持有者可以释放（通过 taskId 校验）
     *
     * @param tenantId 租户 ID
     */
    void release(TenantId tenantId);

    /**
     * 续租（可选，长任务场景）
     * <p>
     * 延长锁的过期时间，防止长任务执行中锁过期
     *
     * @param tenantId      租户 ID
     * @param additionalTtl 延长的时间
     * @return true=续租成功，false=锁已不存在或被他人持有
     */
    default boolean renew(TenantId tenantId, Duration additionalTtl) {
        // 默认实现：不支持续租
        return false;
    }

    /**
     * 检查锁是否存在
     *
     * @param tenantId 租户 ID
     * @return true=锁存在，false=锁不存在
     */
    boolean exists(TenantId tenantId);

    /**
     * 强制释放锁（管理接口，慎用）
     * <p>
     * 用于异常情况下清理僵尸锁
     *
     * @param tenantId 租户 ID
     */
    default void forceRelease(TenantId tenantId) {
        release(tenantId);
    }
}

