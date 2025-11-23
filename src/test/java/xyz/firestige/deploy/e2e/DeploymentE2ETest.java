package xyz.firestige.deploy.e2e;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.EventListener;
import xyz.firestige.deploy.TestApplication;
import xyz.firestige.deploy.domain.plan.event.PlanStatusEvent;
import xyz.firestige.deploy.facade.DeploymentTaskFacade;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.entity.deploy.NetworkEndpoint;

import java.util.List;
import java.util.concurrent.CountDownLatch;

@SpringBootTest(classes = {TestApplication.class})
@ComponentScan(basePackages = {"xyz.firestige.deploy"})
@EnabledIfSystemProperty(named = "runE2E", matches = "true")
public class DeploymentE2ETest {
    private static final Logger logger = LoggerFactory.getLogger(DeploymentE2ETest.class);

    @Autowired
    private DeploymentTaskFacade facade;

    private CountDownLatch countDownLatch = new CountDownLatch(1);

    @Test
    public void test() throws InterruptedException {
        logger.info("test");
        logger.info("facade: {}", facade);
        TenantDeployConfig cfg = new TenantDeployConfig();
        cfg.setPlanId(1L);
        cfg.setPlanVersion(0L);
        cfg.setTenantId("tenant-e2e-1");
        NetworkEndpoint endpoint = new NetworkEndpoint();
        endpoint.setKey("endpoint-key");
        endpoint.setValue("endpoint-value");
        endpoint.setSourceIp("192.168.1.1");
        endpoint.setSourceDomain("192.168.1.2");
        endpoint.setTargetIp("192.168.1.2");
        endpoint.setTargetDomain("192.168.1.2");
        cfg.setNetworkEndpoints(List.of(endpoint));
        cfg.setNacosNameSpace("blue");
        cfg.setDeployUnitId(2L);
        cfg.setDeployUnitName("test");
        cfg.setCalledNumberRules("95598");
        cfg.setTrunkGroup("t1");
        cfg.setDefaultFlag(true);
        cfg.setDeployUnitVersion(11L);
        cfg.setSourceTenantDeployConfig(null);
        facade.createSwitchTask(List.of(cfg));

        countDownLatch.await();
    }

    @EventListener(PlanStatusEvent.class)
    public void listen(PlanStatusEvent event) {
        logger.info("planStatusEvent: {}", event);
    }
}
