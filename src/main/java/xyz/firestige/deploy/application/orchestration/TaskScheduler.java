package xyz.firestige.deploy.application.orchestration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 任务调度器（替代 ExecutionUnitScheduler），支持并发阈值与 FIFO 等待队列。
 * 暂不接线旧流程。
 */
public class TaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(TaskScheduler.class);

    public interface TaskWorker {
        void runTask(String taskId) throws Exception;
    }

    private final ExecutorService pool;
    private final Set<String> running = new HashSet<>();
    private final Queue<String> waiting = new LinkedList<>();

    public TaskScheduler(int poolSize) {
        this.pool = Executors.newFixedThreadPool(poolSize);
    }

    public synchronized Future<?> schedule(String taskId, int maxConcurrency, TaskWorker worker) {
        if (running.size() < maxConcurrency) {
            running.add(taskId);
            log.info("调度执行任务: {} (直接执行)", taskId);
            return pool.submit(() -> execute(taskId, worker));
        } else {
            waiting.add(taskId);
            log.info("并发已满，入队等待: {}", taskId);
            return null;
        }
    }

    private void execute(String taskId, TaskWorker worker) {
        try {
            worker.runTask(taskId);
        } catch (Exception e) {
            log.error("任务执行异常: {}", taskId, e);
        } finally {
            onTaskFinished(taskId, worker);
        }
    }

    private synchronized void onTaskFinished(String taskId, TaskWorker worker) {
        running.remove(taskId);
        if (!waiting.isEmpty()) {
            String next = waiting.poll();
            running.add(next);
            log.info("拉起队列任务: {}", next);
            pool.submit(() -> execute(next, worker));
        }
    }
}

