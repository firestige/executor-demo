package xyz.firestige.executor.integration;

import org.junit.jupiter.api.Test;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.entity.deploy.NetworkEndpoint;
import xyz.firestige.executor.exception.FailureInfo;
import xyz.firestige.executor.facade.*;
import xyz.firestige.executor.state.TaskStatus;
import xyz.firestige.executor.state.TaskStateManager;
import xyz.firestige.executor.validation.ValidationChain;
import xyz.firestige.executor.config.ExecutorProperties;
import xyz.firestige.executor.service.health.HealthCheckClient;
import xyz.firestige.executor.facade.DeploymentTaskFacadeImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class FacadeE2ERefactorTest {

    private DeploymentTaskFacade newFacade() {
        ValidationChain chain = new ValidationChain();
        TaskStateManager stateManager = new TaskStateManager(event -> {});
        ExecutorProperties props = new ExecutorProperties();
        props.setHealthCheckMaxAttempts(3);
        props.setHealthCheckIntervalSeconds(0); // speed up to near-zero
        HealthCheckClient stub = url -> java.util.Map.of("version", "1");
        return new DeploymentTaskFacadeImpl(chain, stateManager, props, stub);
    }

    private TenantDeployConfig cfg(String tenant, long planId, long unitVersion) {
        TenantDeployConfig c = new TenantDeployConfig();
        c.setTenantId(tenant);
        c.setPlanId(planId);
        c.setDeployUnitVersion(unitVersion);
        c.setDeployUnitId(planId * 10 + unitVersion);
        c.setDeployUnitName("unit-"+unitVersion);
        List<NetworkEndpoint> eps = new ArrayList<>();
        NetworkEndpoint ep = new NetworkEndpoint();
        ep.setTargetDomain("localhost");
        eps.add(ep);
        c.setNetworkEndpoints(eps);
        return c;
    }

    @Test
    void testFullLifecycle() throws Exception {
        DeploymentTaskFacade facade = newFacade();
        List<TenantDeployConfig> configs = List.of(cfg("tenantA", 100L, 1L), cfg("tenantB", 100L, 1L));
        TaskCreationResult create = facade.createSwitchTask(configs);
        assertTrue(create.isSuccess());
        assertEquals("100", create.getPlanId());
        assertEquals(2, create.getTaskIds().size());

        String taskA = create.getTaskIds().get(0);
        TaskStatusInfo statusA1 = facade.queryTaskStatus(taskA);
        assertNotNull(statusA1.getMessage());

        TaskOperationResult pause = facade.pauseTaskByTenant("tenantA");
        assertTrue(pause.isSuccess());
        TaskStatusInfo pausedInfo = facade.queryTaskStatus(taskA);
        assertTrue(pausedInfo.getMessage().contains("paused=true"));

        TaskOperationResult resume = facade.resumeTaskByTenant("tenantA");
        assertTrue(resume.isSuccess());
        TaskStatusInfo resumedInfo = facade.queryTaskStatus(taskA);
        assertTrue(resumedInfo.getMessage().contains("paused=false"));

        // cancel then retry from checkpoint
        TaskOperationResult cancel = facade.cancelTask(taskA);
        assertTrue(cancel.isSuccess());
        TaskStatusInfo cancelledInfo = facade.queryTaskStatus(taskA);
        assertTrue(cancelledInfo.getMessage().contains("cancelled=true"));

        TaskOperationResult retryFromCheckpoint = facade.retryTaskByTenant("tenantA", true);
        assertTrue(retryFromCheckpoint.isSuccess());

        TaskOperationResult rollbackPlan = facade.rollbackTaskByPlan(100L);
        assertTrue(rollbackPlan.isSuccess());
        assertEquals(TaskStatus.ROLLED_BACK, rollbackPlan.getStatus());
        // 验证至少一个租户回滚后可查询（不抛异常）
        TaskStatusInfo afterRb = facade.queryTaskStatus(taskA);
        assertNotNull(afterRb);
    }
}
