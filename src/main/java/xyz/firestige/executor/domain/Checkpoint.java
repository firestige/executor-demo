package xyz.firestige.executor.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 检查点
 * 记录任务执行的关键节点，用于暂停恢复和异常重启
 */
public class Checkpoint {
    /**
     * 任务ID
     */
    private String taskId;
    
    /**
     * 当前执行到的服务ID
     */
    private String currentServiceId;
    
    /**
     * 当前服务的索引位置
     */
    private int currentServiceIndex;
    
    /**
     * 当前服务已完成配置下发的租户列表
     */
    private List<String> completedTenants;
    
    /**
     * 上下文数据 - 存储执行过程中的临时数据
     */
    private Map<String, Object> context;
    
    /**
     * 检查点创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 检查点描述
     */
    private String description;
    
    public Checkpoint() {
        this.completedTenants = new ArrayList<>();
        this.context = new HashMap<>();
        this.createTime = LocalDateTime.now();
    }
    
    public Checkpoint(String taskId, String currentServiceId, int currentServiceIndex) {
        this();
        this.taskId = taskId;
        this.currentServiceId = currentServiceId;
        this.currentServiceIndex = currentServiceIndex;
    }
    
    // Getters and Setters
    
    public String getTaskId() {
        return taskId;
    }
    
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }
    
    public String getCurrentServiceId() {
        return currentServiceId;
    }
    
    public void setCurrentServiceId(String currentServiceId) {
        this.currentServiceId = currentServiceId;
    }
    
    public int getCurrentServiceIndex() {
        return currentServiceIndex;
    }
    
    public void setCurrentServiceIndex(int currentServiceIndex) {
        this.currentServiceIndex = currentServiceIndex;
    }
    
    public List<String> getCompletedTenants() {
        return completedTenants;
    }
    
    public void setCompletedTenants(List<String> completedTenants) {
        this.completedTenants = completedTenants;
    }
    
    public void addCompletedTenant(String tenantId) {
        if (!this.completedTenants.contains(tenantId)) {
            this.completedTenants.add(tenantId);
        }
    }
    
    public Map<String, Object> getContext() {
        return context;
    }
    
    public void setContext(Map<String, Object> context) {
        this.context = context;
    }
    
    public void putContext(String key, Object value) {
        this.context.put(key, value);
    }
    
    public Object getContext(String key) {
        return this.context.get(key);
    }
    
    public LocalDateTime getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    @Override
    public String toString() {
        return "Checkpoint{" +
                "taskId='" + taskId + '\'' +
                ", currentServiceId='" + currentServiceId + '\'' +
                ", currentServiceIndex=" + currentServiceIndex +
                ", completedTenants=" + completedTenants +
                ", createTime=" + createTime +
                ", description='" + description + '\'' +
                '}';
    }
}
