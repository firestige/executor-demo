# TaskExecutor 事件驱动重构完整方案

## 🎯 重构目标

基于前面所有讨论，实现以下目标：

1. **TaskExecutor 只负责编排执行**，不创建事件
2. **所有领域事件由 TaskAggregate 产生**
3. **监控事件由 HeartbeatScheduler 独立发布**
4. **废弃 TaskEventSink 和 SpringTaskEventSink**
5. **引入 StateTransitionService 接口**（依赖倒置）

---

## 📦 新增类和包结构

### 1. 领域事件包

**路径**: `domain/task/event/`

```java
// domain/task/event/TaskEvent.java
package xyz.firestige.deploy.domain.task.event;

import xyz.firestige.deploy.domain.shared.event.DomainEvent;
import java.time.LocalDateTime;

public abstract class TaskEvent implements DomainEvent {
    protected final String taskId;
    protected final LocalDateTime occurredOn;
    
    protected TaskEvent(String taskId, LocalDateTime occurredOn) {
        this.taskId = taskId;
        this.occurredOn = occurredOn;
    }
    
    public String getTaskId() { return taskId; }
    
    @Override
    public LocalDateTime occurredOn() { return occurredOn; }
}
```

```java
// domain/task/event/TaskStageCompletedEvent.java
package xyz.firestige.deploy.domain.task.event;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Stage 完成领域事件（包含进度信息）
 */
public class TaskStageCompletedEvent extends TaskEvent {
    private final String stageName;
    private final int completedStages;
    private final int totalStages;
    private final Duration duration;
    
    public TaskStageCompletedEvent(
            String taskId, 
            String stageName,
            int completedStages,
            int totalStages,
            Duration duration,
            LocalDateTime occurredOn) {
        super(taskId, occurredOn);
        this.stageName = stageName;
        this.completedStages = completedStages;
        this.totalStages = totalStages;
        this.duration = duration;
    }
    
    public String getStageName() { return stageName; }
    public int getCompletedStages() { return completedStages; }
    public int getTotalStages() { return totalStages; }
    public Duration getDuration() { return duration; }
    
    public double getPercentage() {
        return totalStages == 0 ? 0 : (completedStages * 100.0 / totalStages);
    }
}
```

其他事件类似创建：
- `TaskStartedEvent`
- `TaskPausedEvent`
- `TaskResumedEvent`
- `TaskCompletedEvent`
- `TaskFailedEvent`
- `TaskCancelledEvent`

---

### 2. 监控事件包

**路径**: `infrastructure/event/monitoring/`

```java
// infrastructure/event/monitoring/MonitoringEvent.java
package xyz.firestige.deploy.infrastructure.event.monitoring;

import java.time.LocalDateTime;

public abstract class MonitoringEvent {
    protected final LocalDateTime timestamp;
    
    protected MonitoringEvent(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public LocalDateTime getTimestamp() { return timestamp; }
}
```

```java
// infrastructure/event/monitoring/TaskProgressMonitoringEvent.java
package xyz.firestige.deploy.infrastructure.event.monitoring;

import xyz.firestige.deploy.domain.task.TaskStatus;
import java.time.LocalDateTime;

/**
 * 任务进度监控事件（技术事件，非领域事件）
 */
public class TaskProgressMonitoringEvent extends MonitoringEvent {
    private final String taskId;
    private final int completedStages;
    private final int totalStages;
    private final double percentage;
    private final TaskStatus currentStatus;
    
    public TaskProgressMonitoringEvent(
            String taskId,
            int completedStages,
            int totalStages,
            double percentage,
            TaskStatus currentStatus,
            LocalDateTime timestamp) {
        super(timestamp);
        this.taskId = taskId;
        this.completedStages = completedStages;
        this.totalStages = totalStages;
        this.percentage = percentage;
        this.currentStatus = currentStatus;
    }
    
    // Getters...
    public String getTaskId() { return taskId; }
    public int getCompletedStages() { return completedStages; }
    public int getTotalStages() { return totalStages; }
    public double getPercentage() { return percentage; }
    public TaskStatus getCurrentStatus() { return currentStatus; }
}
```

---

### 3. 状态转换服务接口（依赖倒置）

**路径**: `domain/task/StateTransitionService.java`

```java
// domain/task/StateTransitionService.java
package xyz.firestige.deploy.domain.task;

/**
 * 状态转换服务接口（Domain 层接口，Infrastructure 层实现）
 * 
 * 职责：
 * - 提供状态转换策略路由
 * - 检查状态转换前置条件
 * - 委托给聚合执行业务方法
 */
public interface StateTransitionService {
    
    /**
     * 执行状态转换
     * 
     * @param aggregate Task 聚合
     * @param targetStatus 目标状态
     * @param runtimeContext 运行时上下文
     * @param additionalData 额外数据
     * @return 是否成功转换
     */
    boolean transition(
            TaskAggregate aggregate, 
            TaskStatus targetStatus,
            TaskRuntimeContext runtimeContext,
            Object additionalData);
    
    /**
     * 检查是否可以转换（查询 API）
     */
    boolean canTransition(
            TaskAggregate aggregate,
            TaskStatus targetStatus,
            TaskRuntimeContext runtimeContext);
}
```

---

### 4. Spring 领域事件发布器

**路径**: `infrastructure/event/SpringDomainEventPublisher.java`

```java
// infrastructure/event/SpringDomainEventPublisher.java
package xyz.firestige.deploy.infrastructure.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import xyz.firestige.deploy.domain.shared.event.DomainEvent;
import xyz.firestige.deploy.domain.shared.event.DomainEventPublisher;

import java.util.Collection;

@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {
    
    private final ApplicationEventPublisher eventPublisher;
    
    public SpringDomainEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }
    
    @Override
    public void publish(DomainEvent event) {
        eventPublisher.publishEvent(event);
    }
    
    @Override
    public void publishAll(Collection<DomainEvent> events) {
        events.forEach(this::publish);
    }
}
```

---

### 5. HeartbeatScheduler 重构

**路径**: `infrastructure/execution/HeartbeatScheduler.java`

```java
// infrastructure/execution/HeartbeatScheduler.java
package xyz.firestige.deploy.infrastructure.execution;

import org.springframework.context.ApplicationEventPublisher;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.StageProgress;
import xyz.firestige.deploy.infrastructure.event.monitoring.TaskProgressMonitoringEvent;

import java.time.LocalDateTime;
import java.util.concurrent.*;

/**
 * 任务进度心跳调度器
 * 
 * 职责：
 * - 定期读取 TaskAggregate 的进度状态
 * - 发布 TaskProgressMonitoringEvent 监控事件
 * - 不修改聚合状态（只读）
 */
public class HeartbeatScheduler {
    
    private final ApplicationEventPublisher eventPublisher;
    private final TaskAggregate task;
    private final int intervalSeconds;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledFuture;
    private volatile boolean running = false;
    
    public HeartbeatScheduler(
            ApplicationEventPublisher eventPublisher,
            TaskAggregate task,
            int intervalSeconds) {
        this.eventPublisher = eventPublisher;
        this.task = task;
        this.intervalSeconds = intervalSeconds;
    }
    
    /**
     * 启动心跳
     */
    public void start() {
        if (running) {
            return;
        }
        
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "heartbeat-" + task.getTaskId())
        );
        
        String taskId = task.getTaskId();
        
        scheduledFuture = scheduler.scheduleAtFixedRate(() -> {
            try {
                // ✅ 只读取聚合状态
                StageProgress progress = task.getProgress();
                
                // ✅ 发布监控事件
                TaskProgressMonitoringEvent event = new TaskProgressMonitoringEvent(
                    taskId,
                    progress.getCompletedStages(),
                    progress.getTotalStages(),
                    progress.getPercentage(),
                    task.getStatus(),
                    LocalDateTime.now()
                );
                
                eventPublisher.publishEvent(event);
                
            } catch (Exception e) {
                // 心跳失败不影响主流程
            }
        }, 0, intervalSeconds, TimeUnit.SECONDS);
        
        running = true;
    }
    
    /**
     * 停止心跳
     */
    public void stop() {
        if (!running) {
            return;
        }
        
        if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
            scheduledFuture.cancel(false);
        }
        
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        running = false;
    }
    
    public boolean isRunning() {
        return running;
    }
}
```

---

## 🔄 TaskExecutor 完整重构

```java
// infrastructure/execution/TaskExecutor.java
package xyz.firestige.deploy.infrastructure.execution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import xyz.firestige.deploy.application.checkpoint.CheckpointService;
import xyz.firestige.deploy.domain.shared.event.DomainEventPublisher;
import xyz.firestige.deploy.domain.shared.exception.ErrorType;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.domain.task.*;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;
import xyz.firestige.deploy.infrastructure.metrics.MetricsRegistry;
import xyz.firestige.deploy.infrastructure.metrics.NoopMetricsRegistry;
import xyz.firestige.deploy.infrastructure.scheduling.TenantConflictManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * TaskExecutor (RF-18: 事件驱动重构版)
 *
 * 职责：
 * 1. 编排 Stage 执行流程
 * 2. 在关键点调用 TaskAggregate 的业务方法
 * 3. 保存聚合并发布领域事件
 * 4. 管理心跳调度器（监控事件）
 * 5. 处理检查点和异常
 *
 * 不再负责：
 * - ❌ 创建事件（由聚合负责）
 * - ❌ 直接调用 TaskEventSink
 * - ❌ 直接修改聚合状态
 */
public class TaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutor.class);

    // 核心依赖
    private final String planId;
    private final TaskAggregate task;
    private final List<TaskStage> stages;
    private final TaskRuntimeContext context;

    // 基础设施依赖
    private final TaskRepository taskRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final ApplicationEventPublisher technicalEventPublisher;
    private final CheckpointService checkpointService;
    private final TenantConflictManager conflictManager;
    private final MetricsRegistry metrics;

    // 心跳调度器
    private HeartbeatScheduler heartbeatScheduler;
    private final int progressIntervalSeconds;

    public TaskExecutor(
            String planId,
            TaskAggregate task,
            List<TaskStage> stages,
            TaskRuntimeContext context,
            TaskRepository taskRepository,
            DomainEventPublisher domainEventPublisher,
            ApplicationEventPublisher technicalEventPublisher,
            CheckpointService checkpointService,
            TenantConflictManager conflictManager,
            int progressIntervalSeconds,
            MetricsRegistry metrics) {

        this.planId = planId;
        this.task = task;
        this.stages = stages != null ? stages : new ArrayList<>();
        this.context = context;
        this.taskRepository = taskRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.technicalEventPublisher = technicalEventPublisher;
        this.checkpointService = checkpointService;
        this.conflictManager = conflictManager;
        this.progressIntervalSeconds = progressIntervalSeconds <= 0 ? 10 : progressIntervalSeconds;
        this.metrics = metrics != null ? metrics : new NoopMetricsRegistry();
    }

    /**
     * 执行任务
     */
    public TaskResult execute() {
        String taskId = task.getTaskId();
        context.injectMdc(null);
        metrics.incrementCounter("task_active");
        LocalDateTime startTime = LocalDateTime.now();

        List<StageResult> completedStages = new ArrayList<>();

        try {
            // 1. 启动任务（调用聚合方法，产生领域事件）
            if (task.getStatus() == TaskStatus.PAUSED) {
                task.resume();
            } else if (task.getStatus() == TaskStatus.PENDING) {
                task.start();
            }
            saveAndPublishDomainEvents();

            // 2. 从检查点恢复
            var checkpoint = checkpointService.loadCheckpoint(task);
            int startIndex = (checkpoint != null) ? checkpoint.getLastCompletedStageIndex() + 1 : 0;

            // 3. 启动心跳（发布监控事件）
            startHeartbeat();

            // 4. 执行 Stages
            for (int i = startIndex; i < stages.size(); i++) {
                TaskStage stage = stages.get(i);
                String stageName = stage.getName();

                // 4.1 检查是否可以跳过
                if (stage.canSkip(context)) {
                    log.info("[TaskExecutor] 跳过 Stage: {}", stageName);
                    StageResult skippedResult = StageResult.skipped(stageName, "条件不满足");
                    completedStages.add(skippedResult);
                    checkpointService.saveCheckpoint(task, getStageNames(completedStages), i);
                    continue;
                }

                // 4.2 执行 Stage
                context.injectMdc(stageName);
                log.info("[TaskExecutor] 开始执行 Stage: {}", stageName);

                StageResult stageResult = stage.execute(context);

                if (stageResult.isSuccess()) {
                    // ✅ Stage 成功：调用聚合方法
                    Duration duration = Duration.ofMillis(stageResult.getDurationMillis());
                    task.completeStage(stageName, duration);
                    saveAndPublishDomainEvents();

                    completedStages.add(stageResult);
                    checkpointService.saveCheckpoint(task, getStageNames(completedStages), i);

                    log.info("[TaskExecutor] Stage 执行成功: {}, 耗时: {}ms",
                            stageName, stageResult.getDurationMillis());

                } else {
                    // ❌ Stage 失败：调用聚合方法
                    FailureInfo failure = FailureInfo.of(
                            ErrorType.STAGE_FAILED,
                            stageResult.getMessage()
                    );
                    task.fail(failure);
                    saveAndPublishDomainEvents();

                    completedStages.add(stageResult);

                    log.error("[TaskExecutor] Stage 执行失败: {}, 原因: {}",
                            stageName, stageResult.getMessage());

                    // 释放租户锁并返回
                    stopHeartbeat();
                    releaseTenantLock();
                    metrics.incrementCounter("task_failed");

                    Duration duration = Duration.between(startTime, LocalDateTime.now());
                    return TaskResult.fail(
                            planId, taskId, task.getStatus(),
                            stageResult.getMessage(), duration, completedStages
                    );
                }

                // 4.3 检查暂停请求
                if (context.isPauseRequested()) {
                    task.pause();
                    saveAndPublishDomainEvents();

                    stopHeartbeat();
                    metrics.incrementCounter("task_paused");

                    log.info("[TaskExecutor] 任务已暂停: {}", taskId);

                    Duration duration = Duration.between(startTime, LocalDateTime.now());
                    return TaskResult.ok(
                            planId, taskId, task.getStatus(), duration, completedStages
                    );
                }

                // 4.4 检查取消请求
                if (context.isCancelRequested()) {
                    task.cancel("用户取消");
                    saveAndPublishDomainEvents();

                    stopHeartbeat();
                    releaseTenantLock();
                    metrics.incrementCounter("task_cancelled");

                    log.info("[TaskExecutor] 任务已取消: {}", taskId);

                    Duration duration = Duration.between(startTime, LocalDateTime.now());
                    return TaskResult.ok(
                            planId, taskId, task.getStatus(), duration, completedStages
                    );
                }
            }

            // 5. 所有 Stage 完成
            task.complete();
            saveAndPublishDomainEvents();

            checkpointService.clearCheckpoint(task);
            stopHeartbeat();
            releaseTenantLock();
            metrics.incrementCounter("task_completed");

            log.info("[TaskExecutor] 任务执行完成: {}", taskId);

            Duration duration = Duration.between(startTime, LocalDateTime.now());
            return TaskResult.ok(
                    planId, taskId, task.getStatus(), duration, completedStages
            );

        } catch (Exception e) {
            log.error("[TaskExecutor] 任务执行异常: {}", taskId, e);

            // 异常处理：标记任务失败
            FailureInfo failure = FailureInfo.of(ErrorType.SYSTEM_ERROR, e.getMessage());
            task.fail(failure);
            saveAndPublishDomainEvents();

            stopHeartbeat();
            releaseTenantLock();
            metrics.incrementCounter("task_failed");

            Duration duration = Duration.between(startTime, LocalDateTime.now());
            return TaskResult.fail(
                    planId, taskId, task.getStatus(),
                    e.getMessage(), duration, completedStages
            );

        } finally {
            context.clearMdc();
        }
    }

    /**
     * 回滚任务
     */
    public TaskResult rollback() {
        String taskId = task.getTaskId();
        log.info("[TaskExecutor] 开始回滚任务: {}", taskId);

        List<StageResult> rollbackStages = new ArrayList<>();
        LocalDateTime startTime = LocalDateTime.now();

        try {
            // 1. 开始回滚（调用聚合方法）
            task.rollback();
            saveAndPublishDomainEvents();

            // 2. 逆序执行 Stage 的 rollback
            List<TaskStage> reversedStages = new ArrayList<>(stages);
            java.util.Collections.reverse(reversedStages);

            boolean anyFailed = false;

            for (TaskStage stage : reversedStages) {
                String stageName = stage.getName();
                log.info("[TaskExecutor] 回滚 Stage: {}", stageName);

                try {
                    stage.rollback(context);

                    StageResult result = new StageResult();
                    result.setStageName(stageName);
                    result.setSuccess(true);
                    rollbackStages.add(result);

                    log.info("[TaskExecutor] Stage 回滚成功: {}", stageName);

                } catch (Exception e) {
                    log.error("[TaskExecutor] Stage 回滚失败: {}", stageName, e);

                    StageResult result = new StageResult();
                    result.setStageName(stageName);
                    result.setSuccess(false);
                    result.setMessage(e.getMessage());
                    rollbackStages.add(result);

                    anyFailed = true;
                }
            }

            // 3. 完成回滚或回滚失败
            if (anyFailed) {
                FailureInfo failure = FailureInfo.of(
                        ErrorType.ROLLBACK_FAILED,
                        "部分 Stage 回滚失败"
                );
                task.rollbackFail(failure);
            } else {
                task.rollbackComplete();
            }
            saveAndPublishDomainEvents();

            releaseTenantLock();
            metrics.incrementCounter("rollback_count");

            log.info("[TaskExecutor] 任务回滚完成: {}, 状态: {}", taskId, task.getStatus());

            Duration duration = Duration.between(startTime, LocalDateTime.now());
            return TaskResult.ok(
                    planId, taskId, task.getStatus(), duration, rollbackStages
            );

        } catch (Exception e) {
            log.error("[TaskExecutor] 回滚执行异常: {}", taskId, e);

            FailureInfo failure = FailureInfo.of(ErrorType.SYSTEM_ERROR, e.getMessage());
            task.rollbackFail(failure);
            saveAndPublishDomainEvents();

            releaseTenantLock();

            Duration duration = Duration.between(startTime, LocalDateTime.now());
            return TaskResult.fail(
                    planId, taskId, task.getStatus(),
                    e.getMessage(), duration, rollbackStages
            );
        }
    }

    /**
     * 重试任务
     */
    public TaskResult retry(boolean fromCheckpoint) {
        String taskId = task.getTaskId();
        log.info("[TaskExecutor] 重试任务: {}, fromCheckpoint: {}", taskId, fromCheckpoint);

        // 调用聚合的 retry 方法
        task.retry();
        saveAndPublishDomainEvents();

        if (!fromCheckpoint) {
            checkpointService.clearCheckpoint(task);
        }

        // 重新执行
        return execute();
    }

    // ========== 辅助方法 ==========

    /**
     * 保存聚合并发布领域事件
     */
    private void saveAndPublishDomainEvents() {
        taskRepository.save(task);
        domainEventPublisher.publishAll(task.getDomainEvents());
        task.clearDomainEvents();
    }

    /**
     * 启动心跳
     */
    private void startHeartbeat() {
        if (heartbeatScheduler == null) {
            heartbeatScheduler = new HeartbeatScheduler(
                    technicalEventPublisher,
                    task,
                    progressIntervalSeconds
            );
        }

        if (!heartbeatScheduler.isRunning()) {
            heartbeatScheduler.start();
            log.debug("[TaskExecutor] 心跳已启动: {}", task.getTaskId());
        }
    }

    /**
     * 停止心跳
     */
    private void stopHeartbeat() {
        if (heartbeatScheduler != null && heartbeatScheduler.isRunning()) {
            heartbeatScheduler.stop();
            log.debug("[TaskExecutor] 心跳已停止: {}", task.getTaskId());
        }
    }

    /**
     * 释放租户锁
     */
    private void releaseTenantLock() {
        if (conflictManager != null) {
            conflictManager.releaseTask(task.getTenantId());
            log.debug("[TaskExecutor] 租户锁已释放: {}", task.getTenantId());
        }
    }

    /**
     * 获取 Stage 名称列表
     */
    private List<String> getStageNames(List<StageResult> results) {
        return results.stream()
                .map(StageResult::getStageName)
                .toList();
    }

    /**
     * 获取当前 Stage 名称
     */
    public String getCurrentStageName() {
        int idx = task.getCurrentStageIndex() - 1;
        if (idx >= 0 && idx < stages.size()) {
            return stages.get(idx).getName();
        }
        return null;
    }

    /**
     * 获取已完成 Stage 数量
     */
    public int getCompletedStageCount() {
        return task.getCurrentStageIndex();
    }
}
```

---

## 🔧 TaskWorkerFactory 调整

```java
// infrastructure/execution/TaskWorkerFactory.java
public class DefaultTaskWorkerFactory implements TaskWorkerFactory {
    
    private final TaskRepository taskRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final ApplicationEventPublisher technicalEventPublisher;
    private final CheckpointService checkpointService;
    private final TenantConflictManager conflictManager;
    private final int progressIntervalSeconds;
    private final MetricsRegistry metrics;
    
    @Override
    public TaskExecutor createExecutor(TaskWorkerCreationContext ctx) {
        return new TaskExecutor(
            ctx.getPlanId(),
            ctx.getTask(),
            ctx.getStages(),
            ctx.getRuntimeContext(),
            taskRepository,
            domainEventPublisher,
            technicalEventPublisher,
            checkpointService,
            conflictManager,
            progressIntervalSeconds,
            metrics
        );
    }
}
```

---

## 📋 配置类调整

```java
@Configuration
public class ExecutorConfiguration {
    
    @Bean
    public DomainEventPublisher domainEventPublisher(
            ApplicationEventPublisher springEventPublisher) {
        return new SpringDomainEventPublisher(springEventPublisher);
    }
    
    @Bean
    public TaskWorkerFactory taskWorkerFactory(
            TaskRepository taskRepository,
            DomainEventPublisher domainEventPublisher,
            ApplicationEventPublisher technicalEventPublisher,
            CheckpointService checkpointService,
            TenantConflictManager conflictManager,
            ExecutorProperties executorProperties,
            MetricsRegistry metrics) {
        return new DefaultTaskWorkerFactory(
            taskRepository,
            domainEventPublisher,
            technicalEventPublisher,
            checkpointService,
            conflictManager,
            executorProperties.getTaskProgressIntervalSeconds(),
            metrics
        );
    }
    
    // ❌ 废弃：不再需要 SpringTaskEventSink
}
```

---

## 🎯 关键变化总结

### 1. TaskExecutor 的职责变化

#### Before（旧版）:
```java
// ❌ 直接调用 eventSink 创建事件
eventSink.publishTaskStarted(planId, taskId, stages.size(), 0);
eventSink.publishTaskCompleted(planId, taskId, duration, completedStages, 0);

// ❌ 直接修改状态
stateManager.updateState(taskId, TaskStatus.RUNNING);
```

#### After（新版）:
```java
// ✅ 调用聚合方法（聚合产生事件）
task.start();
task.complete();
saveAndPublishDomainEvents();

// ✅ 心跳独立发布监控事件
heartbeatScheduler.start();
```

---

### 2. 事件流向变化

#### Before:
```
TaskExecutor → TaskEventSink → TaskStateManager → 创建事件 → Spring发布
```

#### After:
```
领域事件：
TaskExecutor → TaskAggregate.method() → 产生事件 → saveAndPublish()

监控事件：
HeartbeatScheduler → 读取聚合状态 → 发布监控事件
```

---

### 3. 依赖注入变化

#### Before:
```java
TaskExecutor(
    TaskEventSink eventSink,        // ❌ 废弃
    TaskStateManager stateManager   // ❌ 直接操作状态
)
```

#### After:
```java
TaskExecutor(
    TaskRepository taskRepository,              // ✅ 保存聚合
    DomainEventPublisher domainEventPublisher,  // ✅ 发布领域事件
    ApplicationEventPublisher technicalEventPublisher  // ✅ 发布监控事件
)
```

---

## ✅ 重构收益

1. **职责清晰**：TaskExecutor 只编排流程，不创建事件
2. **事件单一来源**：所有领域事件由聚合产生
3. **易于测试**：可以 Mock TaskRepository 和 EventPublisher
4. **符合 DDD**：领域模型驱动，基础设施只做技术支持
5. **监控独立**：心跳事件与业务逻辑解耦
6. **可扩展**：新增事件只需修改聚合，不影响 TaskExecutor

---

## 🚀 迁移步骤

1. ✅ 创建领域事件类（`domain/task/event/`）
2. ✅ 创建监控事件类（`infrastructure/event/monitoring/`）
3. ✅ 创建 `SpringDomainEventPublisher`
4. ✅ 重构 `HeartbeatScheduler`（读取聚合状态）
5. ✅ 重构 `TaskExecutor`（调用聚合方法）
6. ✅ 调整 `TaskWorkerFactory`（注入新依赖）
7. ✅ 调整 `ExecutorConfiguration`（移除 TaskEventSink）
8. ✅ 废弃 `TaskEventSink` 和 `SpringTaskEventSink`

---

## 📝 注意事项

1. **渐进式迁移**：可以先保留 TaskEventSink，逐步迁移到新架构
2. **兼容性**：旧的事件监听器可能需要调整订阅的事件类型
3. **性能**：心跳事件高频发布，注意监听器性能
4. **测试**：重点测试聚合方法的事件产生逻辑

---

**完整方案已准备就绪！需要我开始实施重构吗？**
