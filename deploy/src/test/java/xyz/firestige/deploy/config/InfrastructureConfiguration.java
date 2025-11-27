package xyz.firestige.deploy.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import xyz.firestige.deploy.config.properties.InfrastructureProperties;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.domain.task.CheckpointRepository;
import xyz.firestige.deploy.infrastructure.execution.stage.factory.OrchestratedStageFactory;
import xyz.firestige.deploy.infrastructure.lock.TenantLockManager;
import xyz.firestige.deploy.infrastructure.persistence.checkpoint.InMemoryCheckpointRepository;
import xyz.firestige.deploy.infrastructure.persistence.projection.PlanStateProjectionStore;
import xyz.firestige.deploy.infrastructure.persistence.projection.TaskStateProjectionStore;
import xyz.firestige.deploy.infrastructure.persistence.projection.TenantTaskIndexStore;
import xyz.firestige.deploy.infrastructure.persistence.projection.memory.InMemoryPlanStateProjectionStore;
import xyz.firestige.deploy.infrastructure.persistence.projection.memory.InMemoryTaskStateProjectionStore;
import xyz.firestige.deploy.infrastructure.persistence.projection.memory.InMemoryTenantTaskIndexStore;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@TestConfiguration
@ComponentScan(
        basePackages = "xyz.firestige.deploy",
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {OrchestratedStageFactory.class})
        }
)
public class InfrastructureConfiguration {

    @Configuration
    static class PersistenceConfiguration {

        @Bean
        public CheckpointRepository checkpointStore() {
            return new InMemoryCheckpointRepository();
        }

        @Bean
        public TaskStateProjectionStore redisTaskStateProjectionStore() {
            return new InMemoryTaskStateProjectionStore();
        }

        @Bean
        public PlanStateProjectionStore redisPlanStateProjectionStore() {
            return new InMemoryPlanStateProjectionStore();
        }

        /**
         * Redis 租户任务索引存储
         */
        @Bean
        public TenantTaskIndexStore redisTenantTaskIndexStore() {
            return new InMemoryTenantTaskIndexStore();
        }

        @Bean
        public TenantLockManager redisTenantLockManager() {
            return new InMemoryTenantLockManager();
        }
    }

    /**
     * 内存租户锁管理器实现（用于测试和单实例场景）
     */
    private static class InMemoryTenantLockManager implements TenantLockManager {
        private final ConcurrentHashMap<String, String> locks = new ConcurrentHashMap<>();

        @Override
        public boolean tryAcquire(TenantId tenantId, TaskId taskId, Duration ttl) {
            if (tenantId == null || taskId == null) {
                return false;
            }
            String key = tenantId.getValue();
            String value = taskId.getValue();
            return locks.putIfAbsent(key, value) == null;
        }

        @Override
        public void release(TenantId tenantId) {
            if (tenantId != null) {
                locks.remove(tenantId.getValue());
            }
        }

        @Override
        public boolean exists(TenantId tenantId) {
            return tenantId != null && locks.containsKey(tenantId.getValue());
        }
    }
}
