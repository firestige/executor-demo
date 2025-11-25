package xyz.firestige.deploy.infrastructure.persistence.projection.redis;

import org.springframework.data.redis.core.RedisTemplate;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.domain.task.TaskStatus;
import xyz.firestige.deploy.infrastructure.persistence.projection.TaskStateProjection;
import xyz.firestige.deploy.infrastructure.persistence.projection.TaskStateProjectionStore;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Task 状态投影 Redis 实现
 * Key: executor:task:{taskId}
 * TTL: 7天
 */
public class RedisTaskStateProjectionStore implements TaskStateProjectionStore {

    private static final String KEY_PREFIX = "executor:task:";
    private static final Duration TTL = Duration.ofDays(7);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private final RedisTemplate<String, String> redisTemplate;

    public RedisTaskStateProjectionStore(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(TaskStateProjection projection) {
        if (projection == null || projection.getTaskId() == null) return;
        String key = KEY_PREFIX + projection.getTaskId().getValue();
        redisTemplate.opsForHash().putAll(key, toHash(projection));
        redisTemplate.expire(key, TTL);
    }

    @Override
    public TaskStateProjection load(TaskId taskId) {
        if (taskId == null) return null;
        String key = KEY_PREFIX + taskId.getValue();
        Map<Object, Object> hash = redisTemplate.opsForHash().entries(key);
        if (hash.isEmpty()) return null;
        return fromHash(hash);
    }

    @Override
    public TaskStateProjection findByTenantId(TenantId tenantId) {
        // 需通过 TenantTaskIndexStore 先查 taskId，这里返回 null
        return null;
    }

    @Override
    public void remove(TaskId taskId) {
        if (taskId == null) return;
        redisTemplate.delete(KEY_PREFIX + taskId.getValue());
    }

    // ========== 序列化/反序列化 ==========

    private Map<String, String> toHash(TaskStateProjection p) {
        return Map.of(
                "taskId", p.getTaskId().getValue(),
                "tenantId", p.getTenantId().getValue(),
                "planId", p.getPlanId().getValue(),
                "status", p.getStatus().name(),
                "pauseRequested", String.valueOf(p.isPauseRequested()),
                "createdAt", p.getCreatedAt().format(FORMATTER),
                "updatedAt", p.getUpdatedAt().format(FORMATTER),
                "stageNames", String.join(",", p.getStageNames()),
                "lastCompletedStageIndex", String.valueOf(p.getLastCompletedStageIndex())
        );
    }

    private TaskStateProjection fromHash(Map<Object, Object> hash) {
        String stageNamesStr = (String) hash.get("stageNames");
        List<String> stageNames = (stageNamesStr == null || stageNamesStr.isEmpty()) ? List.of() : List.of(stageNamesStr.split(","));
        return TaskStateProjection.builder()
                .taskId(TaskId.of((String) hash.get("taskId"))) // 使用 of 替换 ofTrusted
                .tenantId(TenantId.of((String) hash.get("tenantId"))) // 使用 of
                .planId(PlanId.of((String) hash.get("planId"))) // 使用 of
                .status(TaskStatus.valueOf((String) hash.get("status")))
                .pauseRequested(Boolean.parseBoolean((String) hash.get("pauseRequested")))
                .createdAt(LocalDateTime.parse((String) hash.get("createdAt"), FORMATTER))
                .updatedAt(LocalDateTime.parse((String) hash.get("updatedAt"), FORMATTER))
                .stageNames(stageNames)
                .lastCompletedStageIndex(Integer.parseInt((String) hash.get("lastCompletedStageIndex")))
                .build();
    }
}
