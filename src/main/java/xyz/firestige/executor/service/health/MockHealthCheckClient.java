package xyz.firestige.executor.service.health;

import java.util.HashMap;
import java.util.Map;

/**
 * 默认 Mock 实现：返回固定版本或可注入的版本号。
 */
public class MockHealthCheckClient implements HealthCheckClient {

    private final String versionKey;
    private final String fixedVersion;

    public MockHealthCheckClient() {
        this("version", "ok");
    }

    public MockHealthCheckClient(String versionKey, String fixedVersion) {
        this.versionKey = versionKey;
        this.fixedVersion = fixedVersion;
    }

    @Override
    public Map<String, Object> get(String endpoint) {
        Map<String, Object> map = new HashMap<>();
        map.put(versionKey, fixedVersion);
        return map;
    }
}

