package xyz.firestige.deploy.infrastructure.persistence.projection.memory;

import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.infrastructure.persistence.projection.TaskStateProjection;
import xyz.firestige.deploy.infrastructure.persistence.projection.TaskStateProjectionStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory TaskStateProjectionStore (fallback when Redis/JDBC not configured)
 */
public class InMemoryTaskStateProjectionStore implements TaskStateProjectionStore {
    private final Map<String, TaskStateProjection> store = new ConcurrentHashMap<>();
    private final Map<String, String> tenantIndex = new ConcurrentHashMap<>();

    @Override
    public void save(TaskStateProjection projection) {
        if (projection == null || projection.getTaskId() == null) return;
        store.put(projection.getTaskId().getValue(), projection);
        if (projection.getTenantId() != null) {
            tenantIndex.put(projection.getTenantId().getValue(), projection.getTaskId().getValue());
        }
    }

    @Override
    public TaskStateProjection load(TaskId taskId) {
        if (taskId == null) return null;
        return store.get(taskId.getValue());
    }

    @Override
    public TaskStateProjection findByTenantId(TenantId tenantId) {
        if (tenantId == null) return null;
        String taskId = tenantIndex.get(tenantId.getValue());
        return taskId != null ? store.get(taskId) : null;
    }

    @Override
    public void remove(TaskId taskId) {
        if (taskId == null) return;
        TaskStateProjection p = store.remove(taskId.getValue());
        if (p != null && p.getTenantId() != null) {
            tenantIndex.remove(p.getTenantId().getValue());
        }
    }
}

