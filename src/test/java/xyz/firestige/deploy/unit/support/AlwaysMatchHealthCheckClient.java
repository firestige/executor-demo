package xyz.firestige.deploy.unit.support;

import xyz.firestige.deploy.service.health.HealthCheckClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Test health check client that always returns expected version for any endpoint.
 * Version is extracted from endpoint query (?expect=xxx) or falls back to defaultVersion.
 */
public class AlwaysMatchHealthCheckClient implements HealthCheckClient {
    private final String versionKey;
    private final String defaultVersion;

    public AlwaysMatchHealthCheckClient() {
        this("version", "100");
    }

    public AlwaysMatchHealthCheckClient(String versionKey, String defaultVersion) {
        this.versionKey = versionKey;
        this.defaultVersion = defaultVersion;
    }

    @Override
    public Map<String, Object> get(String endpoint) {
        String expected = defaultVersion;
        int idx = endpoint.indexOf("expect=");
        if (idx >= 0) {
            expected = endpoint.substring(idx + 7).split("&")[0];
        }
        Map<String, Object> map = new HashMap<>();
        map.put(versionKey, expected);
        return map;
    }
}

