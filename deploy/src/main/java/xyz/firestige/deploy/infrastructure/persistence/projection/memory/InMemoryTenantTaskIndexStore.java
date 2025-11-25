package xyz.firestige.deploy.infrastructure.persistence.projection.memory;

import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.infrastructure.persistence.projection.TenantTaskIndexStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory TenantTaskIndexStore (fallback)
 */
public class InMemoryTenantTaskIndexStore implements TenantTaskIndexStore {
    private final Map<String, String> index = new ConcurrentHashMap<>();

    @Override
    public void put(TenantId tenantId, TaskId taskId) {
        if (tenantId == null || taskId == null) return;
        index.put(tenantId.getValue(), taskId.getValue());
    }

    @Override
    public TaskId get(TenantId tenantId) {
        if (tenantId == null) return null;
        String v = index.get(tenantId.getValue());
        return v != null ? TaskId.of(v) : null;
    }

    @Override
    public void remove(TenantId tenantId) {
        if (tenantId == null) return;
        index.remove(tenantId.getValue());
    }
}

