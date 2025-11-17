package xyz.firestige.executor.integration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.entity.deploy.NetworkEndpoint;
import xyz.firestige.executor.facade.DeploymentTaskFacade;

import java.util.ArrayList;
import java.util.List;

@Disabled("Legacy DeploymentTaskFacadeImpl tests removed; will be re-written after new facade wiring is complete.")
public class FacadeE2ERefactorTest {

    private DeploymentTaskFacade newFacade() {
        throw new UnsupportedOperationException("Legacy DeploymentTaskFacadeImpl tests have been removed. Use updated facade tests.");
    }

    private TenantDeployConfig cfg(String tenant, long planId, long unitVersion) {
        // simplified helper retained for future rewrite
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
        throw new UnsupportedOperationException("Legacy DeploymentTaskFacadeImpl tests have been removed. Use updated facade tests.");
    }
}
