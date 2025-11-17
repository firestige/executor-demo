# RF-11: 完善领域事件 - 实施指南

**执行日期**: 2025-11-18  
**状态**: 执行中 - 遇到技术问题，采用手动修改方案  

---

## 问题说明

在使用自动化工具批量修改 `TaskAggregate.java` 时遇到文件编辑技术问题。为确保修改质量和可控性，改为提供详细的手动修改指南。

---

## 修改方案

### 阶段 1: TaskAggregate.java 添加事件产生逻辑

#### 修改 1.1: 在构造函数后添加事件管理方法

**位置**: 在 `public TaskAggregate(...)` 构造函数后，`// 业务行为方法` 注释前

**添加代码**:
```java
    // ============================================
    // RF-11: 事件管理方法
    // ============================================

    /**
     * 获取聚合产生的领域事件（不可修改）
     */
    public List<TaskStatusEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    /**
     * 清空领域事件（发布后调用）
     */
    public void clearDomainEvents() {
        domainEvents.clear();
    }

    /**
     * 添加领域事件（私有方法）
     */
    private void addDomainEvent(TaskStatusEvent event) {
        this.domainEvents.add(event);
    }
```

---

#### 修改 1.2: 在 10 个业务方法中添加事件产生逻辑

##### 1. start() 方法

**查找**:
```java
public void start() {
    if (status != TaskStatus.PENDING) {
        throw new IllegalStateException(...);
    }
    this.status = TaskStatus.RUNNING;
    this.startedAt = LocalDateTime.now();
}
```

**替换为**:
```java
public void start() {
    if (status != TaskStatus.PENDING) {
        throw new IllegalStateException(...);
    }
    this.status = TaskStatus.RUNNING;
    this.startedAt = LocalDateTime.now();
    
    // ✅ 产生领域事件
    TaskStartedEvent event = new TaskStartedEvent(taskId, tenantId, 
        parsePlanIdToLong(planId), totalStages);
    addDomainEvent(event);
}
```

##### 2. applyPauseAtStageBoundary() 方法

**查找**:
```java
public void applyPauseAtStageBoundary() {
    if (pauseRequested && status == TaskStatus.RUNNING) {
        this.status = TaskStatus.PAUSED;
        this.pauseRequested = false;
    }
}
```

**替换为**:
```java
public void applyPauseAtStageBoundary() {
    if (pauseRequested && status == TaskStatus.RUNNING) {
        this.status = TaskStatus.PAUSED;
        this.pauseRequested = false;
        
        // ✅ 产生领域事件
        TaskPausedEvent event = new TaskPausedEvent(taskId, tenantId, 
            parsePlanIdToLong(planId));
        event.setMessage("任务在 Stage 边界暂停");
        addDomainEvent(event);
    }
}
```

##### 3. resume() 方法

**在方法末尾 } 前添加**:
```java
    // ✅ 产生领域事件
    TaskResumedEvent event = new TaskResumedEvent(taskId, tenantId, 
        parsePlanIdToLong(planId));
    addDomainEvent(event);
```

##### 4. cancel() 方法

**在 calculateDuration() 后添加**:
```java
    // ✅ 产生领域事件
    TaskCancelledEvent event = new TaskCancelledEvent(taskId, tenantId, 
        parsePlanIdToLong(planId));
    event.setCancelledBy(cancelledBy);
    addDomainEvent(event);
```

##### 5. complete() 方法（private）

**在 calculateDuration() 后添加**:
```java
    // ✅ 产生领域事件
    TaskCompletedEvent event = new TaskCompletedEvent(taskId, tenantId, 
        parsePlanIdToLong(planId));
    event.setMessage(String.format("任务成功完成，总耗时: %d ms", durationMillis));
    addDomainEvent(event);
```

##### 6. failStage() 方法

**在 calculateDuration() 后添加**:
```java
    // ✅ 产生领域事件
    TaskFailedEvent event = new TaskFailedEvent(taskId, tenantId, 
        parsePlanIdToLong(planId));
    if (result != null && result.getError() != null) {
        event.setMessage("Stage 失败: " + result.getError());
    }
    addDomainEvent(event);
```

##### 7. retry() 方法

**在方法末尾 } 前添加**:
```java
    // ✅ 产生领域事件
    TaskRetryStartedEvent event = new TaskRetryStartedEvent(taskId, tenantId, 
        parsePlanIdToLong(planId), retryCount);
    event.setFromCheckpoint(fromCheckpoint);
    event.setMessage(String.format("开始第 %d 次重试%s", retryCount, 
        fromCheckpoint ? "（从 checkpoint 恢复）" : "（从头开始）"));
    addDomainEvent(event);
```

##### 8. startRollback() 方法

**在 this.status = TaskStatus.ROLLING_BACK 后添加**:
```java
    // ✅ 产生领域事件
    TaskRollingBackEvent event = new TaskRollingBackEvent(taskId, tenantId, 
        parsePlanIdToLong(planId));
    event.setReason(reason);
    addDomainEvent(event);
```

##### 9. completeRollback() 方法

**在 calculateDuration() 后添加**:
```java
    // ✅ 产生领域事件
    TaskRolledBackEvent event = new TaskRolledBackEvent(taskId, tenantId, 
        parsePlanIdToLong(planId));
    if (prevConfigSnapshot != null) {
        event.setPrevVersion(prevConfigSnapshot.getVersion());
    }
    addDomainEvent(event);
```

##### 10. failRollback() 方法

**在 calculateDuration() 后添加**:
```java
    // ✅ 产生领域事件
    TaskRollbackFailedEvent event = new TaskRollbackFailedEvent(taskId, tenantId, 
        parsePlanIdToLong(planId));
    event.setReason(reason);
    addDomainEvent(event);
```

---

#### 修改 1.3: 在文件末尾添加辅助方法

**位置**: 在最后一个 getter 方法后，类的 } 前

**添加代码**:
```java
    // ============================================
    // 辅助方法
    // ============================================

    /**
     * 将 planId 转换为 Long（用于事件）
     * planId 格式：plan_timestamp_random
     */
    private Long parsePlanIdToLong(String planId) {
        if (planId == null || planId.isBlank()) {
            return null;
        }
        // 简化：如果 planId 是 plan_ 开头，提取时间戳部分
        if (planId.startsWith("plan_")) {
            String[] parts = planId.split("_");
            if (parts.length >= 2) {
                try {
                    return Long.parseLong(parts[1]);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }
```

---

## 验证步骤

完成修改后，执行以下验证：

```bash
# 1. 编译检查
mvn clean compile

# 2. 运行测试
mvn test

# 3. 检查文件
git diff src/main/java/xyz/firestige/executor/domain/task/TaskAggregate.java
```

---

## 下一步

由于遇到自动化修改的技术问题，我建议：

**选项 1**: 我可以为您生成完整的修改后的 TaskAggregate.java 文件内容，您直接替换整个文件

**选项 2**: 您手动按照上述指南进行修改

**选项 3**: 我继续尝试使用其他工具方法完成修改

请您指示偏好的方式，我将继续完成 RF-11 的实施。

