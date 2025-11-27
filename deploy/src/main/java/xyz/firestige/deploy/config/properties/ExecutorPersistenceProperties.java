package xyz.firestige.deploy.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 执行器持久化配置属性
 * <p>
 * 支持配置：
 * - 存储类型（redis/memory）
 * - Redis Key 前缀
 * - TTL 时间
 *
 * @since T-016 投影型持久化
 */
@ConfigurationProperties(prefix = "executor.persistence")
public class ExecutorPersistenceProperties {

    /**
     * 存储类型
     */
    private StoreType storeType = StoreType.memory;

    /**
     * Redis Key 命名空间前缀
     */
    private String namespace = "executor";

    /**
     * 投影数据 TTL（默认 7 天）
     */
    private Duration projectionTtl = Duration.ofDays(7);

    /**
     * 租户锁 TTL（默认 2.5 小时）
     */
    private Duration lockTtl = Duration.ofSeconds(9000);

    public enum StoreType {
        /**
         * Redis 存储（生产环境推荐）
         */
        redis,

        /**
         * 内存存储（测试环境，重启后丢失）
         */
        memory
    }

    // Getters and Setters

    public StoreType getStoreType() {
        return storeType;
    }

    public void setStoreType(StoreType storeType) {
        this.storeType = storeType;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public Duration getProjectionTtl() {
        return projectionTtl;
    }

    public void setProjectionTtl(Duration projectionTtl) {
        this.projectionTtl = projectionTtl;
    }

    public Duration getLockTtl() {
        return lockTtl;
    }

    public void setLockTtl(Duration lockTtl) {
        this.lockTtl = lockTtl;
    }
}

