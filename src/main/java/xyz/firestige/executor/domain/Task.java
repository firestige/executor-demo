package xyz.firestige.executor.domain;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import xyz.firestige.executor.api.dto.ExecutionOrder;

/**
 * 任务实体
 * 代表一个蓝绿环境切换任务
 */
public class Task {
    /**
     * 任务唯一标识
     */
    private String taskId;
    
    /**
     * 任务状态 - 使用 AtomicReference 保证线程安全
     */
    private AtomicReference<TaskStatus> status;
    
    /**
     * 执行单对象（原始数据）
     */
    private ExecutionOrder executionOrder;
    
    /**
     * 任务上下文
     */
    private TaskContext context;
    
    /**
     * 任务创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 任务开始执行时间
     */
    private LocalDateTime startTime;
    
    /**
     * 任务结束时间
     */
    private LocalDateTime endTime;
    
    /**
     * 创建者标识
     */
    private String createdBy;
    
    /**
     * 原任务ID（用于回滚场景，记录被回滚的任务ID）
     */
    private String originalTaskId;
    
    /**
     * 关联的租户ID（用于队列管理）
     * 如果任务涉及多个租户，可使用逗号分隔或采用主租户ID
     */
    private String tenantId;
    
    public Task() {
        this.status = new AtomicReference<>(TaskStatus.READY);
        this.createTime = LocalDateTime.now();
    }
    
    public Task(String taskId) {
        this();
        this.taskId = taskId;
        this.context = new TaskContext(taskId);
    }
    
    public Task(String taskId, Object executionOrder) {
        this(taskId);
        this.executionOrder = (ExecutionOrder) executionOrder;
    }
    
    /**
     * 获取当前状态
     */
    public TaskStatus getStatus() {
        return status.get();
    }
    
    /**
     * 设置状态（线程安全）
     */
    public void setStatus(TaskStatus newStatus) {
        this.status.set(newStatus);
    }
    
    /**
     * CAS更新状态
     */
    public boolean compareAndSetStatus(TaskStatus expect, TaskStatus update) {
        return this.status.compareAndSet(expect, update);
    }
    
    /**
     * 判断是否为终态
     */
    public boolean isTerminalState() {
        TaskStatus currentStatus = getStatus();
        return currentStatus == TaskStatus.COMPLETED 
            || currentStatus == TaskStatus.FAILED
            || currentStatus == TaskStatus.STOPPED
            || currentStatus == TaskStatus.ROLLBACK_COMPLETE;
    }
    
    /**
     * 判断任务是否可以暂停
     */
    public boolean canPause() {
        TaskStatus currentStatus = getStatus();
        return currentStatus == TaskStatus.RUNNING;
    }
    
    /**
     * 判断任务是否可以恢复
     */
    public boolean canResume() {
        TaskStatus currentStatus = getStatus();
        return currentStatus == TaskStatus.PAUSED;
    }
    
    /**
     * 判断任务是否可以停止
     */
    public boolean canStop() {
        TaskStatus currentStatus = getStatus();
        return currentStatus == TaskStatus.RUNNING 
            || currentStatus == TaskStatus.PAUSED;
    }
    
    // Getters and Setters
    
    public String getTaskId() {
        return taskId;
    }
    
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }
    
    public Object getExecutionOrder() {
        return executionOrder;
    }
    
    public void setExecutionOrder(Object executionOrder) {
        this.executionOrder = (ExecutionOrder) executionOrder;
    }
    
    public TaskContext getContext() {
        return context;
    }
    
    public void setContext(TaskContext context) {
        this.context = context;
    }
    
    public LocalDateTime getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
    
    public LocalDateTime getStartTime() {
        return startTime;
    }
    
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }
    
    public LocalDateTime getEndTime() {
        return endTime;
    }
    
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
    
    public String getCreatedBy() {
        return createdBy;
    }
    
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
    
    public String getOriginalTaskId() {
        return originalTaskId;
    }
    
    public void setOriginalTaskId(String originalTaskId) {
        this.originalTaskId = originalTaskId;
    }
    
    public String getTenantId() {
        return tenantId;
    }
    
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
    
    @Override
    public String toString() {
        return "Task{" +
                "taskId='" + taskId + '\'' +
                ", status=" + status.get() +
                ", createTime=" + createTime +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                '}';
    }
}
