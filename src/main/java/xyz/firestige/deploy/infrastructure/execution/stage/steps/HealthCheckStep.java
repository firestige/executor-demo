package xyz.firestige.deploy.infrastructure.execution.stage.steps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.deploy.infrastructure.external.health.HealthCheckClient;
import xyz.firestige.entity.deploy.NetworkEndpoint;
import xyz.firestige.deploy.config.ExecutorProperties;
import xyz.firestige.deploy.infrastructure.execution.stage.StageStep;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 健康检查步骤：3秒间隔、最多10次，所有实例成功才通过。
 */
public class HealthCheckStep implements StageStep {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckStep.class);

    private final String stepName;
    private final List<NetworkEndpoint> endpoints;
    private final String expectedVersion;
    private final String versionKey;
    private final HealthCheckClient client;
    private final ExecutorProperties props;

    public HealthCheckStep(String stepName,
                           List<NetworkEndpoint> endpoints,
                           String expectedVersion,
                           String versionKey,
                           HealthCheckClient client,
                           ExecutorProperties props) {
        this.stepName = Objects.requireNonNull(stepName);
        this.endpoints = Objects.requireNonNull(endpoints);
        this.expectedVersion = Objects.requireNonNull(expectedVersion);
        this.versionKey = (versionKey != null && !versionKey.isEmpty()) ? versionKey : props.getHealthCheckVersionKey();
        this.client = Objects.requireNonNull(client);
        this.props = Objects.requireNonNull(props);
    }

    @Override
    public String getStepName() {
        return stepName;
    }

    @Override
    public void execute(TaskRuntimeContext ctx) throws Exception {
        int interval = props.getHealthCheckIntervalSeconds();
        int maxAttempts = props.getHealthCheckMaxAttempts();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            boolean allOk = true;
            for (NetworkEndpoint ep : endpoints) {
                String url = endpointToUrl(ep);
                Map<String, Object> resp = client.get(url);
                Object v = resp.get(versionKey);
                boolean ok = expectedVersion.equals(String.valueOf(v));
                log.info("HealthCheck attempt {} endpoint {} -> {} (expect {})", attempt, url, v, expectedVersion);
                if (!ok) {
                    allOk = false;
                }
            }
            if (allOk) {
                return; // 成功
            }
            if (attempt < maxAttempts) {
                Thread.sleep(interval * 1000L);
            }
        }
        throw new IllegalStateException("HealthCheck failed after max attempts");
    }

    private String endpointToUrl(NetworkEndpoint ep) {
        // 优先使用 value 作为完整 URL
        String value = ep.getValue();
        if (value != null && (value.startsWith("http://") || value.startsWith("https://"))) {
            return value;
        }
        // 其次使用 targetDomain/targetIp，默认 http 和 /health 路径
        String host = ep.getTargetDomain() != null && !ep.getTargetDomain().isEmpty()
                ? ep.getTargetDomain() : ep.getTargetIp();
        if (host == null || host.isEmpty()) {
            host = "localhost";
        }
        String path = props.getHealthCheckPath();
        if (path == null || path.isEmpty()) {
            path = "/health";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return "http://" + host + path;
    }
}
