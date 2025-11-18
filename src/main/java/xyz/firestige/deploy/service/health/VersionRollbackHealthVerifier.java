package xyz.firestige.deploy.service.health;

import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;

import java.util.List;
import java.util.Map;

/**
 * Realistic rollback health verifier: re-check endpoints for prevConfigSnapshot version.
 * If all endpoints report the previous version, we treat rollback as healthy.
 * Version key is injected (default 'version').
 */
public class VersionRollbackHealthVerifier implements RollbackHealthVerifier {

    private final HealthCheckClient client;
    private final String versionKey;

    public VersionRollbackHealthVerifier(HealthCheckClient client, String versionKey) {
        this.client = client;
        this.versionKey = versionKey == null ? "version" : versionKey;
    }

    @Override
    public boolean verify(TaskAggregate aggregate, TaskRuntimeContext context) {
        if (aggregate == null || aggregate.getPrevConfigSnapshot() == null) return false;
        List<String> endpoints = aggregate.getPrevConfigSnapshot().getNetworkEndpoints();
        if (endpoints == null || endpoints.isEmpty()) return true; // nothing to verify
        Long expected = aggregate.getPrevConfigSnapshot().getDeployUnitVersion();
        for (String ep : endpoints) {
            try {
                Map<String, Object> data = client.get(ep);
                Object v = data.get(versionKey);
                if (!(v instanceof String || v instanceof Number)) return false;
                long ver = v instanceof Number ? ((Number) v).longValue() : Long.parseLong((String) v);
                if (expected == null || ver != expected) {
                    return false;
                }
            } catch (Exception ex) {
                return false;
            }
        }
        return true;
    }
}
