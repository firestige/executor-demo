package xyz.firestige.deploy.domain.stage.rollback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.domain.task.TenantDeployConfigSnapshot;
import xyz.firestige.deploy.service.health.HealthCheckClient;
import xyz.firestige.deploy.config.ExecutorProperties;

/**
 * 使用上一版可用配置进行回滚：重发旧配置 + 健康确认旧版本号。
 * 当前暂为占位逻辑（日志 + 版本号校验），后续可扩展实际下发实现。
 */
public class PreviousConfigRollbackStrategy implements RollbackStrategy {

    private static final Logger log = LoggerFactory.getLogger(PreviousConfigRollbackStrategy.class);

    private final HealthCheckClient healthCheckClient;
    private final ExecutorProperties props;

    public PreviousConfigRollbackStrategy(HealthCheckClient healthCheckClient, ExecutorProperties props) {
        this.healthCheckClient = healthCheckClient;
        this.props = props;
    }

    @Override
    public void rollback(TaskAggregate task, TaskRuntimeContext context) throws Exception {
        TenantDeployConfigSnapshot snap = task.getPrevConfigSnapshot();
        if (snap == null) {
            log.warn("No previous config snapshot for task={}, skipping rollback", task.getTaskId());
            return;
        }
        log.info("Re-sending previous config: task={}, tenant={}, version={}", task.getTaskId(), snap.getTenantId(), snap.getDeployUnitVersion());
        // 占位：模拟健康确认旧版本（真实实现：发送旧配置；循环 polling endpoints）
        // 这里只做一次性检查，不重试，后续阶段补轮询逻辑一致性。
        task.setDeployUnitVersion(snap.getDeployUnitVersion());
        task.setDeployUnitId(snap.getDeployUnitId());
        task.setDeployUnitName(snap.getDeployUnitName());
        // 健康确认可在后续整合：当前仅打印
        log.info("Rollback health confirmation placeholder: task={}, endpoints={}", task.getTaskId(), snap.getNetworkEndpoints());
    }
}
