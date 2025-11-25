package xyz.firestige.deploy.infrastructure.persistence.projection.redis;

import org.springframework.data.redis.core.RedisTemplate;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.plan.PlanStatus;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.infrastructure.persistence.projection.PlanStateProjection;
import xyz.firestige.deploy.infrastructure.persistence.projection.PlanStateProjectionStore;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Plan 状态投影 Redis 实现
 *
 * @since T-016 投影型持久化
 */
public class RedisPlanStateProjectionStore implements PlanStateProjectionStore {

    private static final String KEY_PREFIX = "executor:plan:";
    private static final Duration TTL = Duration.ofDays(7);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final RedisTemplate<String, String> redisTemplate;

    public RedisPlanStateProjectionStore(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(PlanStateProjection projection) {
        if (projection == null || projection.getPlanId() == null) {
            return;
        }

        String key = KEY_PREFIX + projection.getPlanId().getValue();
        Map<String, String> hash = toHash(projection);

        redisTemplate.opsForHash().putAll(key, hash);
        redisTemplate.expire(key, TTL);
    }

    @Override
    public PlanStateProjection load(PlanId planId) {
        if (planId == null) {
            return null;
        }

        String key = KEY_PREFIX + planId.getValue();
        Map<Object, Object> hash = redisTemplate.opsForHash().entries(key);

        if (hash.isEmpty()) {
            return null;
        }

        return fromHash(hash);
    }

    @Override
    public void remove(PlanId planId) {
        if (planId == null) {
            return;
        }

        String key = KEY_PREFIX + planId.getValue();
        redisTemplate.delete(key);
    }

    private Map<String, String> toHash(PlanStateProjection p) {
        String taskIdsStr = p.getTaskIds().stream()
            .map(id -> String.valueOf(id.getValue()))
            .collect(Collectors.joining(","));

        return Map.of(
            "planId", String.valueOf(p.getPlanId().getValue()),
            "status", p.getStatus().name(),
            "taskIds", taskIdsStr,
            "maxConcurrency", String.valueOf(p.getMaxConcurrency()),
            "createdAt", p.getCreatedAt().format(FORMATTER),
            "updatedAt", p.getUpdatedAt().format(FORMATTER)
        );
    }

    private PlanStateProjection fromHash(Map<Object, Object> hash) {
        String taskIdsStr = (String) hash.get("taskIds");
        List<TaskId> taskIds = taskIdsStr != null && !taskIdsStr.isEmpty()
            ? List.of(taskIdsStr.split(",")).stream()
                .map(TaskId::of) // 使用静态工厂 of 替换 new
                .collect(Collectors.toList())
            : List.of();

        return PlanStateProjection.builder()
            .planId(PlanId.of((String) hash.get("planId"))) // 使用静态工厂 of
            .status(PlanStatus.valueOf((String) hash.get("status")))
            .taskIds(taskIds)
            .maxConcurrency(Integer.parseInt((String) hash.get("maxConcurrency")))
            .createdAt(LocalDateTime.parse((String) hash.get("createdAt"), FORMATTER))
            .updatedAt(LocalDateTime.parse((String) hash.get("updatedAt"), FORMATTER))
            .build();
    }
}
