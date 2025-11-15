package xyz.firestige.executor.unit.execution;

import org.junit.jupiter.api.Test;
import xyz.firestige.executor.event.TaskEventSink;
import xyz.firestige.executor.execution.HeartbeatScheduler;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class HeartbeatSchedulerTest {

    @Test
    void testHeartbeatPublishesProgressDetail() throws Exception {
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger publishCount = new AtomicInteger(0);
        AtomicReference<Integer> lastCompleted = new AtomicReference<>(0);

        TaskEventSink sink = new TaskEventSink() {
            @Override
            public void publishTaskProgressDetail(String planId, String taskId, int c, int t, long seq) {
                publishCount.incrementAndGet();
                lastCompleted.set(c);
            }
        };

        HeartbeatScheduler hs = new HeartbeatScheduler("plan1", "task1", 5, completed::get, sink, 1);
        hs.start();
        // 模拟完成进度
        completed.set(2);
        Thread.sleep(1200); // 至少一次心跳
        completed.set(3);
        Thread.sleep(1200); // 第二次心跳
        hs.stop();

        assertTrue(publishCount.get() >= 2, "Should publish at least two heartbeats");
        assertTrue(lastCompleted.get() == 3, "Last completed should be updated to 3");
    }
}

