# T-035: 统一领域事件的负载模型 - 三大视图 + 宽表设计

**任务ID**: T-035  
**优先级**: P2  
**开始日期**: 2025-11-30  
**完成日期**: 2025-11-30  
**状态**: ✅ **已完成**  
**负责人**: Copilot  
**依赖**: T-034（已完成）

---

## 背景

### 当前问题

在 T-034 完成后，我们创建了 TaskProgressView 用于组合进度信息。但当前事件系统存在以下问题：

1. **事件负载分散**：不同事件携带不同字段，没有统一的视图模型
2. **字段重复**：TaskInfo、进度信息、错误信息在不同事件中重复定义
3. **扩展困难**：添加新字段需要修改多个事件类
4. **JSON 冗余**：序列化时所有字段都输出，即使为 null

### 设计目标

创建**三大视图 + 宽表设计**，统一事件负载模型：

1. **TaskInfoView**：Task 基本信息（增强版 TaskInfo）
2. **TaskProgressView**：执行状态和进度（T-034 已创建）
3. **TaskErrorView**：错误信息和失败详情

---

## 设计方案

### 核心理念

**三大视图是数据容器类（宽表），事件根据需要自己组装**

```
┌─────────────────────────────────────────┐
│        领域事件（Event）                 │
│  - 接受 TaskAggregate 作为参数          │
│  - 根据自己的兴趣选择创建哪些视图        │
└─────────────────────────────────────────┘
                  ↓ 按需调用
    ┌─────────────┬─────────────┬─────────────┐
    │ TaskInfoView│TaskProgressView│TaskErrorView│
    │   .from()   │   .from()   │   .from()   │
    │（静态方法）  │（静态方法）  │（静态方法）  │
    └─────────────┴─────────────┴─────────────┘
                  ↓ 从聚合根提取数据
          ┌─────────────────┐
          │  TaskAggregate  │
          │  (提供 getter)  │
          └─────────────────┘
```

**关键点**：
- ✅ 视图类是**工具类/容器类**，提供静态工厂方法
- ✅ TaskAggregate **只提供 getter**，不预先创建视图（除了 getProgressView）
- ✅ **事件自己决定**需要哪些视图，在构造器中调用静态方法创建
- ✅ 宽表设计：视图包含所有可能字段，序列化时忽略 null（稀疏表）

### 三大视图设计

#### 1. TaskInfoView（Task 基本信息宽表）

**职责**：承载 Task 的基本标识和元数据信息

```java
public class TaskInfoView {
    // 核心标识
    private final String taskId;
    private final String tenantId;
    private final String planId;
    
    // 部署信息
    private final String deployUnitName;
    private final Long deployUnitVersion;
    private final TaskStatus status;
    
    // 时间信息（宽表字段）
    private final LocalDateTime createdAt;
    private final LocalDateTime startedAt;
    private final LocalDateTime endedAt;
    private final Long durationMillis;
    
    // 配置信息（宽表字段）
    private final String deployUnitId;
    private final Long previousVersion;  // 上一个版本
    private final Boolean rollbackIntent;  // 是否回滚
    
    // 重试信息（宽表字段）
    private final Integer retryCount;
    private final Integer maxRetry;
    
    // 工厂方法
    public static TaskInfoView from(TaskAggregate task);
}
```

**设计要点**：
- ✅ 包含所有基本信息字段（宽表）
- ✅ 支持 JSON 序列化时忽略 null 字段（稀疏表）
- ✅ 向后兼容：保留 TaskInfo.from(task) 方法

#### 2. TaskProgressView（执行进度视图）

**已在 T-034 创建**，字段包括：
- currentStageIndex, currentStageName
- completedStages, totalStages
- totalStagesInRange, startIndex, endIndex
- progressPercentage

#### 3. TaskErrorView（错误信息视图）

**职责**：承载任务失败和错误的详细信息

```java
public class TaskErrorView {
    // 错误基本信息
    private final String errorType;      // ERROR, TIMEOUT, VALIDATION_FAILED, etc.
    private final String errorMessage;
    private final String errorCode;
    
    // 失败详情
    private final String failedStageName;
    private final String failedStepName;
    private final Integer failedStageIndex;
    
    // 异常信息
    private final String exceptionClass;
    private final String stackTrace;  // 可选，调试用
    
    // 重试信息
    private final Boolean retriable;
    private final String retryStrategy;
    
    // 时间信息
    private final LocalDateTime failedAt;
    
    // 工厂方法
    public static TaskErrorView from(FailureInfo failureInfo);
    public static TaskErrorView from(StageResult failedResult);
    public static TaskErrorView fromException(Exception ex);
}
```

**设计要点**：
- ✅ 统一错误信息结构
- ✅ 支持多种创建方式（FailureInfo、StageResult、Exception）
- ✅ 可选字段（如 stackTrace）可为 null

---

## 事件负载组合模式

### 事件类型与视图组合

| 事件类型 | TaskInfoView | TaskProgressView | TaskErrorView |
|---------|-------------|-----------------|--------------|
| TaskStarted | ✅ | ✅ | - |
| TaskProgress | ✅ | ✅ | - |
| TaskCompleted | ✅ | ✅ | - |
| TaskFailed | ✅ | ✅ | ✅ |
| TaskRetryStarted | ✅ | ✅ | ✅ (上次错误) |
| TaskRolledBack | ✅ | ✅ | - |
| StageStarted | ✅ | ✅ | - |
| StageFailed | ✅ | ✅ | ✅ |

### 示例：事件如何使用视图（T-036 阶段）

```java
// ✅ 正确：事件在构造器中，根据自己的兴趣创建视图
public class TaskFailedEvent extends TaskStatusEvent {
    private final TaskInfoView taskInfo;      // 这个事件需要基本信息
    private final TaskProgressView progress;   // 这个事件需要进度信息
    private final TaskErrorView error;         // 这个事件需要错误信息
    
    // 接受 TaskAggregate 作为参数
    public TaskFailedEvent(TaskAggregate task, FailureInfo failureInfo) {
        // 事件自己调用静态方法创建视图
        this.taskInfo = TaskInfoView.from(task);           // 静态方法
        this.progress = task.getProgressView();            // 聚合根方法（T-034已有）
        this.error = TaskErrorView.from(failureInfo);      // 静态方法
        
        setMessage(String.format("任务失败: %s", error.getErrorMessage()));
    }
}

// ✅ 正确：事件只创建自己需要的视图
public class TaskStartedEvent extends TaskStatusEvent {
    private final TaskInfoView taskInfo;
    private final TaskProgressView progress;
    // 注意：这个事件不关心错误，所以不创建 TaskErrorView
    
    public TaskStartedEvent(TaskAggregate task) {
        this.taskInfo = TaskInfoView.from(task);    // 只创建需要的
        this.progress = task.getProgressView();
    }
}

// ✅ 正确：事件可以只要部分信息
public class TaskCompletedEvent extends TaskStatusEvent {
    private final TaskInfoView taskInfo;
    // 只需要基本信息，不需要进度和错误
    
    public TaskCompletedEvent(TaskAggregate task, Duration duration) {
        this.taskInfo = TaskInfoView.from(task);
        // 不创建其他视图
    }
}
```

**设计要点**：
- ❌ **不是**：TaskAggregate.getInfoView() - 聚合根不预先创建
- ✅ **而是**：TaskInfoView.from(task) - 事件自己调用静态方法
- ✅ 事件根据自己的关注点，选择性创建视图
- ✅ 每个事件的字段组合可以不同

### JSON 输出示例（稀疏表）

```json
{
  "eventId": "evt-123",
  "eventType": "TASK_FAILED",
  "timestamp": "2025-11-30T10:00:00",
  "taskInfo": {
    "taskId": "task-001",
    "tenantId": "tenant-123",
    "planId": "plan-456",
    "deployUnitName": "user-service",
    "deployUnitVersion": 100,
    "status": "FAILED",
    "createdAt": "2025-11-30T09:55:00",
    "startedAt": "2025-11-30T09:56:00",
    "retryCount": 1
    // endedAt, durationMillis 为 null，不输出
  },
  "progress": {
    "currentStageIndex": 1,
    "currentStageName": "stage-2",
    "completedStages": 1,
    "totalStages": 3,
    "progressPercentage": 33.3
  },
  "error": {
    "errorType": "EXECUTION_ERROR",
    "errorMessage": "连接超时",
    "failedStageName": "stage-2",
    "failedStageIndex": 1,
    "failedAt": "2025-11-30T09:57:30",
    "retriable": true
    // stackTrace 为 null，不输出
  }
}
```

---

## 向后兼容策略

### 保留现有 TaskInfo

```java
// 旧代码继续工作
TaskInfo info = TaskInfo.from(task);

// 新代码使用宽表
TaskInfoView infoView = TaskInfoView.from(task);

// TaskInfo 作为 TaskInfoView 的简化版本
// TaskInfoView 可以转换为 TaskInfo
public TaskInfo toTaskInfo() {
    return new TaskInfo(taskId, tenantId, planId, 
                       deployUnitName, deployUnitVersion, status);
}
```

---

## 实施计划

### 阶段 1：创建三大视图类（✅ 已完成）

1. ✅ TaskProgressView - 已创建（104行）
2. ✅ TaskInfoView - 已创建（320行）
3. ✅ TaskErrorView - 已创建（350行）

### 阶段 2：添加 JSON 注解（稀疏表支持）（✅ 已完成）

所有视图类已添加 `@JsonInclude(JsonInclude.Include.NON_NULL)` 注解，实现稀疏表序列化

### 阶段 3：验证和测试（✅ 已完成）

1. ✅ 编译验证 - BUILD SUCCESS（199 files编译通过）
2. ⏳ JSON 序列化测试 - 待 T-036 使用时验证
3. ✅ 向后兼容性测试（TaskInfo.from(task) 继续可用）

---

## 实施完成

**状态**: ✅ **已完成**  
**完成日期**: 2025-11-30  
**编译结果**: BUILD SUCCESS (199 files)

### 成果总结

#### 创建的视图类
1. **TaskInfoView** (320行)
   - 提供静态方法 `TaskInfoView.from(task)`
   - 包含核心标识、部署信息、时间信息、回滚信息、重试信息
   - 支持 JSON 稀疏表序列化
   - 可转换为 TaskInfo（向后兼容）

2. **TaskProgressView** (104行)
   - 提供静态方法 `TaskProgressView.from(progress, range)`
   - 包含当前进度、Stage 总数、执行范围、进度百分比
   - 支持 JSON 稀疏表序列化

3. **TaskErrorView** (350行)
   - 提供静态方法 `TaskErrorView.from(failureInfo)`, `from(stageResult)`, `fromException(ex)`
   - 包含错误基本信息、失败详情、异常信息、重试信息
   - 支持 JSON 稀疏表序列化

#### 设计特点
✅ 三大视图作为**数据容器类**（宽表）  
✅ 提供**静态工厂方法**，事件自己调用  
✅ TaskAggregate **只提供 getter**，不预先创建视图（getProgressView除外）  
✅ 支持 **JSON 稀疏表**（@JsonInclude(NON_NULL)）  
✅ **向后兼容**（TaskInfo 继续可用）

---

## 后续任务

T-035 完成后，进入 T-036：
- 修改所有事件构造器，使用三大视图
- 事件接受 TaskAggregate 作为入参
- 统一事件创建逻辑

---

## 成功标准

✅ TaskInfoView、TaskProgressView、TaskErrorView 三个视图类创建完成  
✅ 支持 JSON 稀疏表（@JsonInclude(NON_NULL)）  
✅ 提供静态工厂方法（from() 方法）  
✅ 编译通过，无破坏性变更  
✅ 保持向后兼容（TaskInfo 继续可用）  
✅ TaskAggregate 只提供 getter，不预先创建视图（getProgressView除外）

