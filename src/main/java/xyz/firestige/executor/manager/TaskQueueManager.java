package xyz.firestige.executor.manager;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import xyz.firestige.executor.domain.Task;

/**
 * 任务队列管理器
 * 实现租户级FIFO队列，确保同一租户的任务顺序执行，不同租户可并发
 */
@Component
public class TaskQueueManager {
    
    private static final Logger log = LoggerFactory.getLogger(TaskQueueManager.class);
    
    // 租户 -> 任务队列
    private final Map<String, Queue<String>> tenantQueues = new ConcurrentHashMap<>();
    
    // 租户 -> 当前执行中的任务ID
    private final Map<String, String> tenantRunningTasks = new ConcurrentHashMap<>();
    
    // 任务ID -> 租户ID（用于反向查询）
    private final Map<String, String> taskTenantMapping = new ConcurrentHashMap<>();
    
    /**
     * 将任务加入租户队列
     * 
     * @param task 任务对象
     */
    public void enqueue(Task task) {
        String taskId = task.getTaskId();
        String tenantId = task.getTenantId();
        
        if (tenantId == null || tenantId.trim().isEmpty()) {
            log.warn("Task has no tenantId, skipping enqueue: taskId={}", taskId);
            return;
        }
        
        // 记录任务-租户映射
        taskTenantMapping.put(taskId, tenantId);
        
        // 获取或创建租户队列
        Queue<String> queue = tenantQueues.computeIfAbsent(tenantId, k -> new ConcurrentLinkedQueue<>());
        
        // 加入队列
        queue.offer(taskId);
        
        log.info("Task enqueued: taskId={}, tenantId={}, queueSize={}", 
            taskId, tenantId, queue.size());
    }
    
    /**
     * 从租户队列中移除任务
     * 
     * @param taskId 任务ID
     */
    public void dequeue(String taskId) {
        String tenantId = taskTenantMapping.remove(taskId);
        if (tenantId == null) {
            log.warn("Task not found in queue: taskId={}", taskId);
            return;
        }
        
        Queue<String> queue = tenantQueues.get(tenantId);
        if (queue != null) {
            queue.remove(taskId);
            log.info("Task dequeued: taskId={}, tenantId={}, remainingQueueSize={}", 
                taskId, tenantId, queue.size());
            
            // 如果队列为空，清理租户队列
            if (queue.isEmpty()) {
                tenantQueues.remove(tenantId);
            }
        }
    }
    
    /**
     * 标记任务开始执行
     * 
     * @param taskId 任务ID
     */
    public void markRunning(String taskId) {
        String tenantId = taskTenantMapping.get(taskId);
        if (tenantId != null) {
            tenantRunningTasks.put(tenantId, taskId);
            log.info("Task marked as running: taskId={}, tenantId={}", taskId, tenantId);
        }
    }
    
    /**
     * 标记任务完成执行
     * 
     * @param taskId 任务ID
     */
    public void markCompleted(String taskId) {
        String tenantId = taskTenantMapping.get(taskId);
        if (tenantId != null) {
            tenantRunningTasks.remove(tenantId, taskId);
            log.info("Task marked as completed: taskId={}, tenantId={}", taskId, tenantId);
        }
    }
    
    /**
     * 检查租户是否有任务正在执行
     * 
     * @param tenantId 租户ID
     * @return true 如果有任务正在执行
     */
    public boolean isRunning(String tenantId) {
        return tenantRunningTasks.containsKey(tenantId);
    }
    
    /**
     * 获取租户当前执行的任务ID
     * 
     * @param tenantId 租户ID
     * @return 任务ID，如果没有则返回null
     */
    public String getRunningTask(String tenantId) {
        return tenantRunningTasks.get(tenantId);
    }
    
    /**
     * 获取租户队列中的下一个任务（不移除）
     * 
     * @param tenantId 租户ID
     * @return 任务ID，如果队列为空则返回null
     */
    public String peekNext(String tenantId) {
        Queue<String> queue = tenantQueues.get(tenantId);
        return queue != null ? queue.peek() : null;
    }
    
    /**
     * 获取租户队列的大小
     * 
     * @param tenantId 租户ID
     * @return 队列大小
     */
    public int getQueueSize(String tenantId) {
        Queue<String> queue = tenantQueues.get(tenantId);
        return queue != null ? queue.size() : 0;
    }
    
    /**
     * 获取任务所属的租户ID
     * 
     * @param taskId 任务ID
     * @return 租户ID，如果不存在则返回null
     */
    public String getTenantId(String taskId) {
        return taskTenantMapping.get(taskId);
    }
    
    /**
     * 检查任务是否在队列中
     * 
     * @param taskId 任务ID
     * @return true 如果任务在队列中
     */
    public boolean contains(String taskId) {
        return taskTenantMapping.containsKey(taskId);
    }
    
    /**
     * 清理租户的所有任务
     * 
     * @param tenantId 租户ID
     */
    public void clearTenant(String tenantId) {
        Queue<String> queue = tenantQueues.remove(tenantId);
        if (queue != null) {
            // 清理任务-租户映射
            queue.forEach(taskTenantMapping::remove);
            log.info("Cleared tenant queue: tenantId={}, clearedTasks={}", tenantId, queue.size());
        }
        
        // 清理执行中任务记录
        tenantRunningTasks.remove(tenantId);
    }
}
