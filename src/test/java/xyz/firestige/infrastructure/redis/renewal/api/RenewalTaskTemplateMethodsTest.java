package xyz.firestige.infrastructure.redis.renewal.api;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RenewalTaskTemplateMethodsTest {

    @Test
    void fixedRenewal_createsTaskWithFixedStrategies() {
        Duration ttl = Duration.ofMinutes(5);
        Duration interval = Duration.ofMinutes(2);

        RenewalTask task = RenewalTask.fixedRenewal(
            List.of("key1", "key2"),
            ttl,
            interval
        );

        assertNotNull(task);
        assertNotNull(task.getKeySelector());
        assertNotNull(task.getTtlStrategy());
        assertNotNull(task.getIntervalStrategy());
        assertNotNull(task.getStopCondition());
    }

    @Test
    void untilTime_createsTaskWithTimeBasedStop() {
        Instant endTime = Instant.now().plusSeconds(3600);
        Duration baseTtl = Duration.ofMinutes(10);

        RenewalTask task = RenewalTask.untilTime(
            List.of("key1"),
            baseTtl,
            endTime
        );

        assertNotNull(task);
        RenewalContext ctx = new RenewalContext("test");

        // 验证停止条件：当前时间应该可以继续
        assertFalse(task.getStopCondition().shouldStop(ctx));
    }

    @Test
    void maxRenewals_createsTaskWithCountLimit() {
        Duration ttl = Duration.ofMinutes(3);
        long maxCount = 5;

        RenewalTask task = RenewalTask.maxRenewals(
            List.of("key1", "key2", "key3"),
            ttl,
            maxCount
        );

        assertNotNull(task);
        RenewalContext ctx = new RenewalContext("test");

        // 验证策略：未达到最大次数应该继续
        assertTrue(task.getTtlStrategy().shouldContinue(ctx));

        // 模拟达到最大次数
        for (int i = 0; i < maxCount; i++) {
            ctx.incrementRenewalCount();
        }
        assertFalse(task.getTtlStrategy().shouldContinue(ctx));
    }
}

