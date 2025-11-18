# RF-18: 方案C完整重构指南

## 🎯 重构目标

基于方案C架构，实现完整的事件驱动TaskExecutor重构：
- TaskExecutor 依赖 TaskDomainService + StateTransitionService
- 所有状态转换先通过 StateTransitionService.canTransition() 前置检查
- TaskDomainService 封装 save + publishAll + clear 逻辑
- TaskExecutor 只负责编排执行流程

---

## 📋 已完成的工作

### ✅ 1. TaskDomainService 依赖调整
- 已注入 `StateTransitionService` 替代 `TaskStateManager`
- 文件: `domain/task/TaskDomainService.java`
- 行数: 40-51

### ✅ 2. 新增生命周期方法框架
- 已添加方法声明框架
- 文件: `domain/task/TaskDomainService.java`  
- 行数: 102-250

---

## 🔧 待完成的重构任务

### Task 1: 调整 TaskAggregate 方法签名

**目标**: 统一聚合方法签名，支持 Duration 参数

**当前问题**:
```java
// 当前
public void completeStage(StageResult result)

// 期望  
public void completeStage(String stageName, Duration duration)
```

**修改文件**: `domain/task/TaskAggregate.java`

**具体改动**:

```java
// 1. 添加新的 completeStage 方法
public void completeStage(String stageName, Duration duration) {
    validateCanCompleteStage();
    
    // 推进进度
    this.stageProgress = stageProgress.advance();
    
    // 产生领域事件（包含进度信息）
    TaskStageCompletedEvent event = new TaskStageCompletedEvent(
        taskId.getValue(),
        stageName,
        stageProgress.getCurrentStageIndex(),
        stageProgress.getTotalStages(),
        duration,
        LocalDateTime.now()
    );
    addDomainEvent(event);
}

// 2. 添加 fail 方法（接受 FailureInfo）
public void fail(FailureInfo failure) {
    if (status.isTerminal()) {
        return;
    }
    
    this.status = TaskStatus.FAILED;
    this.timeRange = timeRange.end();
    calculateDuration();
    
    TaskFailedEvent event = new TaskFailedEvent(taskId.getValue(), TaskStatus.FAILED);
    event.setMessage(failure.getErrorMessage());
    addDomainEvent(event);
}

// 3. 添加 pause 方法
public void pause() {
    if (status != TaskStatus.RUNNING) {
        throw new IllegalStateException("只有 RUNNING 状态才能暂停");
    }
    
    this.status = TaskStatus.PAUSED;
    this.pauseRequested = false;  // 清除标志
    
    TaskPausedEvent event = new TaskPausedEvent();
    event.setTaskId(taskId.getValue());
    event.setStatus(TaskStatus.PAUSED);
    addDomainEvent(event);
}

// 4. 添加 complete 方法（公开）
public void complete() {
    if (status != TaskStatus.RUNNING) {
        throw new IllegalStateException("只有 RUNNING 状态才能完成");
    }
    
    if (!stageProgress.isCompleted()) {
        throw new IllegalStateException("还有未完成的 Stage");
    }
    
    this.status = TaskStatus.COMPLETED;
    this.timeRange = timeRange.end();
    calculateDuration();
    
    TaskCompletedEvent event = new TaskCompletedEvent();
    event.setTaskId(taskId.getValue());
    event.setStatus(TaskStatus.COMPLETED);
    addDomainEvent(event);
}

// 5. 添加 rollback 方法（无参数）
public void rollback() {
    if (status.isTerminal()) {
        throw new IllegalStateException("终态任务无法回滚");
    }
    
    this.status = TaskStatus.ROLLING_BACK;
    
    TaskRollingBackEvent event = new TaskRollingBackEvent();
    event.setTaskId(taskId.getValue());
    event.setStatus(TaskStatus.ROLLING_BACK);
    addDomainEvent(event);
}

// 6. 添加 retry 方法（无参数简化版）
public void retry() {
    if (status != TaskStatus.FAILED && status != TaskStatus.ROLLED_BACK) {
        throw new IllegalStateException("只有 FAILED 或 ROLLED_BACK 状态才能重试");
    }
    
    // 重置进度和重试计数
    if (retryPolicy != null) {
        this.retryPolicy = retryPolicy.incrementRetryCount();
    }
    
    if (stageProgress != null) {
        this.stageProgress = stageProgress.reset();
    }
    
    this.status = TaskStatus.RUNNING;
    
    TaskRetryStartedEvent event = new TaskRetryStartedEvent();
    event.setTaskId(taskId.getValue());
    event.setStatus(TaskStatus.RUNNING);
    addDomainEvent(event);
}
```

---

### Task 2: 更新 TaskStageCompletedEvent

**目标**: 支持进度信息

**修改文件**: `domain/task/event/TaskStageCompletedEvent.java`

**具体改动**:

```java
package xyz.firestige.deploy.domain.task.event;

import java.time.Duration;
import java.time.LocalDateTime;
import xyz.firestige.deploy.domain.task.TaskStatus;

/**
 * Stage 完成领域事件（RF-18: 包含进度信息）
 */
public class TaskStageCompletedEvent extends TaskStatusEvent {

    private String stageName;
    private int completedStages;
    private int totalStages;
    private Duration duration;

    public TaskStageCompletedEvent() {
        super();
        setStatus(TaskStatus.RUNNING);
    }

    public TaskStageCompletedEvent(
            String taskId, 
            String stageName,
            int completedStages,
            int totalStages,
            Duration duration,
            LocalDateTime occurredOn) {
        super(taskId, TaskStatus.RUNNING);
        this.stageName = stageName;
        this.completedStages = completedStages;
        this.totalStages = totalStages;
        this.duration = duration;
        setMessage("Stage 执行完成: " + stageName);
    }

    // Getters
    public String getStageName() { return stageName; }
    public int getCompletedStages() { return completedStages; }
    public int getTotalStages() { return totalStages; }
    public Duration getDuration() { return duration; }
    
    public double getPercentage() {
        return totalStages == 0 ? 0 : (completedStages * 100.0 / totalStages);
    }

    // Setters
    public void setStageName(String stageName) { this.stageName = stageName; }
    public void setCompletedStages(int completedStages) { this.completedStages = completedStages; }
    public void setTotalStages(int totalStages) { this.totalStages = totalStages; }
    public void setDuration(Duration duration) { this.duration = duration; }
}
```

---

### Task 3: 创建监控事件包

**目标**: 创建独立的监控事件类

**新增文件**: `infrastructure/event/monitoring/TaskProgressMonitoringEvent.java`

```java
package xyz.firestige.deploy.infrastructure.event.monitoring;

import xyz.firestige.deploy.domain.task.TaskStatus;
import java.time.LocalDateTime;

/**
 * 任务进度监控事件（技术事件，非领域事件）
 * 
 * 特点：
 * - 高频发布（每 10 秒）
 * - 不改变领域状态
 * - 仅用于监控面板、告警系统
 */
public class TaskProgressMonitoringEvent {
    
    private final String taskId;
    private final int completedStages;
    private final int totalStages;
    private final double percentage;
    private final TaskStatus currentStatus;
    private final LocalDateTime timestamp;
    
    public TaskProgressMonitoringEvent(
            String taskId,
            int completedStages,
            int totalStages,
            double percentage,
            TaskStatus currentStatus,
            LocalDateTime timestamp) {
        this.taskId = taskId;
        this.completedStages = completedStages;
        this.totalStages = totalStages;
        this.percentage = percentage;
        this.currentStatus = currentStatus;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
    }
    
    // Getters
    public String getTaskId() { return taskId; }
    public int getCompletedStages() { return completedStages; }
    public int getTotalStages() { return totalStages; }
    public double getPercentage() { return percentage; }
    public TaskStatus getCurrentStatus() { return currentStatus; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
```

---

### Task 4: 创建 SpringDomainEventPublisher

**新增文件**: `infrastructure/event/SpringDomainEventPublisher.java`

```java
package xyz.firestige.deploy.infrastructure.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import xyz.firestige.deploy.domain.shared.event.DomainEventPublisher;

import java.util.List;

@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {
    
    private final ApplicationEventPublisher eventPublisher;
    
    public SpringDomainEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }
    
    @Override
    public void publish(Object event) {
        eventPublisher.publishEvent(event);
    }
    
    @Override
    public void publishAll(List<?> events) {
        if (events != null) {
            events.forEach(this::publish);
        }
    }
}
```

---

### Task 5: 重构 HeartbeatScheduler

**目标**: 只读取聚合状态，发布监控事件

**修改文件**: `infrastructure/execution/HeartbeatScheduler.java`

**关键改动**:
```java
public class HeartbeatScheduler {
    
    private final ApplicationEventPublisher eventPublisher;
    private final TaskAggregate task;
    private final int intervalSeconds;
    
    public void start() {
        scheduledFuture = scheduler.scheduleAtFixedRate(() -> {
            try {
                // ✅ 只读取聚合状态
                StageProgress progress = task.getStageProgress();
                
                // ✅ 发布监控事件
                TaskProgressMonitoringEvent event = new TaskProgressMonitoringEvent(
                    task.getTaskId(),
                    progress.getCurrentStageIndex(),
                    progress.getTotalStages(),
                    progress.getProgressPercentage(),
                    task.getStatus(),
                    LocalDateTime.now()
                );
                
                eventPublisher.publishEvent(event);
                
            } catch (Exception e) {
                // 心跳失败不影响主流程
            }
        }, 0, intervalSeconds, TimeUnit.SECONDS);
    }
}
```

---

### Task 6: 重构 TaskExecutor（核心）

**目标**: 依赖 TaskDomainService + StateTransitionService

**修改文件**: `infrastructure/execution/TaskExecutor.java`

**关键改动**:

```java
public class TaskExecutor {
    
    // ✅ 核心依赖
    private final TaskAggregate task;
    private final TaskRuntimeContext context;
    private final TaskDomainService taskDomainService;
    private final StateTransitionService stateTransitionService;
    
    // 基础设施依赖
    private final ApplicationEventPublisher technicalEventPublisher;
    private final CheckpointService checkpointService;
    private final TenantConflictManager conflictManager;
    private final MetricsRegistry metrics;
    
    // 心跳调度器
    private HeartbeatScheduler heartbeatScheduler;
    
    public TaskExecutionResult execute() {
        String taskId = task.getTaskId();
        LocalDateTime startTime = LocalDateTime.now();
        List<StageResult> completedStages = new ArrayList<>();
        
        try {
            // 1. ✅ 前置检查：是否可以启动/恢复
            TaskStatus currentStatus = task.getStatus();
            TaskStatus targetStatus = TaskStatus.RUNNING;
            
            if (!stateTransitionService.canTransition(task, targetStatus, context)) {
                log.error("状态转换不允许: {} -> {}", currentStatus, targetStatus);
                return TaskExecutionResult.fail(...);
            }
            
            // 2. ✅ 通过检查后才执行高成本操作
            if (currentStatus == TaskStatus.PAUSED) {
                taskDomainService.resumeTask(task, context);
            } else {
                taskDomainService.startTask(task, context);
            }
            
            // 3. 启动心跳
            startHeartbeat();
            
            // 4. 执行 Stages
            for (int i = startIndex; i < stages.size(); i++) {
                TaskStage stage = stages.get(i);
                String stageName = stage.getName();
                
                // 执行 Stage
                StageResult stageResult = stage.execute(context);
                
                if (stageResult.isSuccess()) {
                    // ✅ Stage 成功
                    Duration duration = Duration.ofMillis(stageResult.getDurationMillis());
                    taskDomainService.completeStage(task, stageName, duration, context);
                    
                    completedStages.add(stageResult);
                } else {
                    // ✅ Stage 失败：前置检查
                    if (stateTransitionService.canTransition(task, TaskStatus.FAILED, context)) {
                        FailureInfo failure = FailureInfo.of(
                            ErrorType.STAGE_FAILED, 
                            stageResult.getMessage()
                        );
                        taskDomainService.failTask(task, failure, context);
                    }
                    
                    stopHeartbeat();
                    releaseTenantLock();
                    return TaskExecutionResult.fail(...);
                }
                
                // 检查暂停
                if (context.isPauseRequested()) {
                    if (stateTransitionService.canTransition(task, TaskStatus.PAUSED, context)) {
                        taskDomainService.pauseTask(task, context);
                        stopHeartbeat();
                        return TaskExecutionResult.ok(...);
                    }
                }
                
                // 检查取消
                if (context.isCancelRequested()) {
                    if (stateTransitionService.canTransition(task, TaskStatus.CANCELLED, context)) {
                        taskDomainService.cancelTask(task, "用户取消", context);
                        stopHeartbeat();
                        releaseTenantLock();
                        return TaskExecutionResult.ok(...);
                    }
                }
            }
            
            // 5. 完成任务
            if (stateTransitionService.canTransition(task, TaskStatus.COMPLETED, context)) {
                taskDomainService.completeTask(task, context);
            }
            
            stopHeartbeat();
            releaseTenantLock();
            return TaskExecutionResult.ok(...);
            
        } catch (Exception e) {
            // 异常处理也前置检查
            if (stateTransitionService.canTransition(task, TaskStatus.FAILED, context)) {
                FailureInfo failure = FailureInfo.of(ErrorType.SYSTEM_ERROR, e.getMessage());
                taskDomainService.failTask(task, failure, context);
            }
            
            stopHeartbeat();
            releaseTenantLock();
            return TaskExecutionResult.fail(...);
        }
    }
}
```

---

### Task 7: 更新 TaskWorkerFactory

**目标**: 注入新的依赖

**修改文件**: `infrastructure/execution/DefaultTaskWorkerFactory.java`

```java
public class DefaultTaskWorkerFactory implements TaskWorkerFactory {
    
    private final TaskDomainService taskDomainService;
    private final StateTransitionService stateTransitionService;
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
            taskDomainService,
            stateTransitionService,
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

### Task 8: 更新配置类

**目标**: 配置所有新的 Bean

**修改文件**: `config/ExecutorConfiguration.java`

```java
@Configuration
public class ExecutorConfiguration {
    
    @Bean
    public DomainEventPublisher domainEventPublisher(
            ApplicationEventPublisher springEventPublisher) {
        return new SpringDomainEventPublisher(springEventPublisher);
    }
    
    @Bean
    public StateTransitionService stateTransitionService(ApplicationEventPublisher eventPublisher) {
        return new TaskStateManager(eventPublisher);  // TaskStateManager 实现接口
    }
    
    @Bean
    public TaskDomainService taskDomainService(
            TaskRepository taskRepository,
            TaskRuntimeRepository taskRuntimeRepository,
            StateTransitionService stateTransitionService,
            DomainEventPublisher domainEventPublisher) {
        return new TaskDomainService(
            taskRepository,
            taskRuntimeRepository,
            stateTransitionService,
            domainEventPublisher
        );
    }
    
    @Bean
    public TaskWorkerFactory taskWorkerFactory(
            TaskDomainService taskDomainService,
            StateTransitionService stateTransitionService,
            ApplicationEventPublisher technicalEventPublisher,
            CheckpointService checkpointService,
            TenantConflictManager conflictManager,
            ExecutorProperties executorProperties,
            MetricsRegistry metrics) {
        return new DefaultTaskWorkerFactory(
            taskDomainService,
            stateTransitionService,
            technicalEventPublisher,
            checkpointService,
            conflictManager,
            executorProperties.getTaskProgressIntervalSeconds(),
            metrics
        );
    }
}
```

---

## 🎯 重构价值总结

### 方案C的核心优势

1. **低成本前置检查** - 避免不必要的DB操作和事件发布
2. **零回滚风险** - 检查不通过直接返回，无副作用
3. **提供查询API** - UI可以查询可用操作
4. **完美复用** - 复用DomainService的封装能力
5. **职责清晰** - 三层分离（Executor + DomainService + StateTransition）
6. **易于测试** - 策略可独立测试
7. **符合OCP** - 新增状态转换无需修改现有代码

### 执行顺序建议

1. Task 1: 调整 TaskAggregate 方法签名（基础）
2. Task 2: 更新 TaskStageCompletedEvent（基础）
3. Task 3: 创建监控事件包（独立）
4. Task 4: 创建 SpringDomainEventPublisher（独立）
5. Task 5: 重构 HeartbeatScheduler（独立）
6. Task 6: 重构 TaskExecutor（核心，依赖前面所有）
7. Task 7: 更新 TaskWorkerFactory（配置）
8. Task 8: 更新配置类（配置）

---

**重构完成后，将拥有一个完整的事件驱动架构，符合DDD最佳实践！** 🎉
