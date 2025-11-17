# RF-01: Facade 业务逻辑剥离 — 重构技术方案

**创建日期**: 2025-11-17  
**状态**: 待审批  
**优先级**: 高

---

## 1. 问题分析

### 1.1 当前架构问题

**问题点 1**: `DeploymentTaskFacadeImpl.createSwitchTask` 承担了过多职责
- ✅ DTO 转换（configs → PlanAggregate）
- ✅ 业务校验（ValidationChain.validateAll）
- ✅ 业务编排（Plan 创建、Task 初始化、状态机注册、Executor 创建）
- ✅ 状态管理（状态机迁移、事件发布）
- ✅ 注册表维护（planRegistry、taskRegistry、contextRegistry、stageRegistry）
- ✅ 执行调度（planOrchestrator.submitPlan）

**问题点 2**: Facade 直接操作多个内部注册表
```java
private final Map<String, PlanAggregate> planRegistry = new HashMap<>();
private final Map<String, TaskAggregate> taskRegistry = new HashMap<>();
private final Map<String, TaskRuntimeContext> contextRegistry = new HashMap<>();
private final Map<String, List<TaskStage>> stageRegistry = new HashMap<>();
private final Map<String, TaskExecutor> executorRegistry = new HashMap<>();
private final Map<String, PlanStateMachine> planSmRegistry = new HashMap<>();
```
这些注册表应该由应用服务层管理。

**问题点 3**: Facade 方法返回业务结果对象
- 当前返回 `TaskCreationResult`、`TaskOperationResult`、`TaskStatusInfo`
- 但 Facade 作为防腐层，应该通过异常机制处理错误，而非返回结果对象

**问题点 4**: Result DTO 设计不符合 DDD 原则（新增）
- `TaskCreationResult` 实际创建的是 Plan（包含多个 Task），但命名为 "Task"，语义不清
- `TaskOperationResult` 既用于 Plan 操作也用于 Task 操作，职责混淆
- 缺少聚合关系表达：Plan 包含 Task 的关系在返回值中未体现
- 违背通用语言原则：命名与领域概念不匹配
- **详细分析参见**: `RF01_RESULT_DTO_ANALYSIS.md`

### 1.2 架构目标

遵循分层架构原则：
```
Controller/API Layer (未来)
        ↓
  Facade Layer (防腐层)
        ↓
Application Service Layer (业务编排)
        ↓
Domain Layer (领域模型 + 状态机)
        ↓
Infrastructure Layer (Repository/外部服务)
```

---

## 2. 重构方案设计

### 2.1 核心设计原则

根据用户诉求：

1. **应用服务层**维持当前返回值设计（重构后改为符合 DDD 的新 Result DTO）
   - `PlanApplicationService` 方法返回 `PlanCreationResult` / `PlanOperationResult`
   - `TaskApplicationService` 方法返回 `TaskOperationResult` / `TaskStatusInfo`
   - 明确区分 Plan 聚合和 Task 聚合的操作结果

2. **新 Facade 层**统一返回 `void`
   - 成功时：无返回值
   - 失败时：抛出异常
   - 参数校验失败：将 `PlanCreationResult` 中的错误信息包装到 `IllegalArgumentException` 中抛出

3. **职责清晰分离**
   - **Facade**: **外部 DTO → 内部 DTO 转换** + 参数校验 + 调用应用服务 + 异常转换（保护应用层接口稳定）
   - **Application Service**: 业务编排 + 状态管理 + 返回结果对象（使用内部 DTO，遵循 DDD 原则）
   - **Domain**: 领域逻辑 + 状态机

4. **Result DTO 遵循 DDD 设计**
   - **聚合边界清晰**：`PlanCreationResult` 包含 `PlanInfo`（值对象），`PlanInfo` 包含 `List<TaskInfo>`
   - **通用语言一致**：命名与领域概念匹配（Plan vs Task）
   - **类型安全**：Plan 操作返回 `PlanOperationResult`，Task 操作返回 `TaskOperationResult`
   - **值对象不可变**：`PlanInfo` 和 `TaskInfo` 为不可变对象

---

## 3. 详细设计

### 3.0 Result DTO 设计（DDD 视角）

#### 3.0.1 设计原则

遵循 DDD 核心原则：
1. **明确聚合边界**：Plan 是聚合根，Task 是聚合内实体
2. **通用语言一致**：命名与领域概念匹配
3. **单一职责**：每个 Result 类只负责一种聚合操作
4. **组合关系表达**：使用嵌套对象表达 Plan 包含 Task 的聚合关系
5. **值对象不可变**：`PlanInfo` 和 `TaskInfo` 为不可变值对象

#### 3.0.2 PlanCreationResult（Plan 创建结果）

```java
package xyz.firestige.executor.application.dto;

/**
 * Plan 创建结果
 * 表达 Plan 聚合的创建结果，包含 Plan 和其包含的 Task 信息
 */
public class PlanCreationResult {
    
    private boolean success;
    private PlanInfo planInfo;              // Plan 聚合信息（值对象）
    private ValidationSummary validationSummary;
    private FailureInfo failureInfo;
    private String message;
    
    // 静态工厂方法
    public static PlanCreationResult success(PlanInfo planInfo) {
        PlanCreationResult result = new PlanCreationResult();
        result.success = true;
        result.planInfo = planInfo;
        result.message = "Plan 创建成功";
        return result;
    }
    
    public static PlanCreationResult validationFailure(ValidationSummary summary) {
        PlanCreationResult result = new PlanCreationResult();
        result.success = false;
        result.validationSummary = summary;
        result.message = "配置校验失败";
        return result;
    }
    
    public static PlanCreationResult failure(FailureInfo failureInfo, String message) {
        PlanCreationResult result = new PlanCreationResult();
        result.success = false;
        result.failureInfo = failureInfo;
        result.message = message;
        return result;
    }
    
    // Getters...
}
```

#### 3.0.3 PlanInfo（Plan 聚合信息 - 值对象）

```java
package xyz.firestige.executor.application.dto;

/**
 * Plan 聚合信息
 * 值对象，表达 Plan 聚合的基本信息和包含的 Task 列表
 * 不可变对象，体现 Plan 包含 Task 的聚合关系
 */
public class PlanInfo {
    
    private final String planId;
    private final int maxConcurrency;
    private final PlanStatus status;
    private final List<TaskInfo> tasks;     // Plan 包含的 Task 列表（聚合关系）
    private final LocalDateTime createdAt;
    
    public PlanInfo(String planId, int maxConcurrency, PlanStatus status, 
                    List<TaskInfo> tasks, LocalDateTime createdAt) {
        this.planId = planId;
        this.maxConcurrency = maxConcurrency;
        this.status = status;
        this.tasks = Collections.unmodifiableList(new ArrayList<>(tasks));  // 防御性拷贝 + 不可变
        this.createdAt = createdAt;
    }
    
    // 静态工厂方法：从领域模型构造
    public static PlanInfo from(PlanAggregate plan) {
        List<TaskInfo> taskInfos = plan.getTasks().stream()
            .map(TaskInfo::from)
            .collect(Collectors.toList());
        
        return new PlanInfo(
            plan.getPlanId(),
            plan.getMaxConcurrency(),
            plan.getStatus(),
            taskInfos,
            plan.getCreatedAt()
        );
    }
    
    // Getters only (值对象不可变，无 Setters)
    public String getPlanId() { return planId; }
    public int getMaxConcurrency() { return maxConcurrency; }
    public PlanStatus getStatus() { return status; }
    public List<TaskInfo> getTasks() { return tasks; }  // 已经是不可变的
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

#### 3.0.4 TaskInfo（Task 信息 - 值对象）

```java
package xyz.firestige.executor.application.dto;

/**
 * Task 信息
 * 值对象，表达 Task 实体的基本信息
 * 不可变对象
 */
public class TaskInfo {
    
    private final String taskId;
    private final String tenantId;
    private final String configVersion;
    private final TaskStatus status;
    
    public TaskInfo(String taskId, String tenantId, String configVersion, TaskStatus status) {
        this.taskId = taskId;
        this.tenantId = tenantId;
        this.configVersion = configVersion;
        this.status = status;
    }
    
    // 静态工厂方法：从领域模型构造
    public static TaskInfo from(TaskAggregate task) {
        return new TaskInfo(
            task.getTaskId(),
            task.getTenantId(),
            task.getConfigVersion(),
            task.getStatus()
        );
    }
    
    // Getters only (值对象不可变，无 Setters)
    public String getTaskId() { return taskId; }
    public String getTenantId() { return tenantId; }
    public String getConfigVersion() { return configVersion; }
    public TaskStatus getStatus() { return status; }
}
```

#### 3.0.5 PlanOperationResult（Plan 操作结果）

```java
package xyz.firestige.executor.application.dto;

/**
 * Plan 操作结果
 * 用于 Plan 级别的操作（暂停、恢复、回滚、重试）
 * 明确区分 Plan 聚合的操作结果
 */
public class PlanOperationResult {
    
    private boolean success;
    private String planId;
    private PlanStatus status;
    private FailureInfo failureInfo;
    private String message;
    
    public static PlanOperationResult success(String planId, PlanStatus status, String message) {
        PlanOperationResult result = new PlanOperationResult();
        result.success = true;
        result.planId = planId;
        result.status = status;
        result.message = message;
        return result;
    }
    
    public static PlanOperationResult failure(String planId, FailureInfo failureInfo, String message) {
        PlanOperationResult result = new PlanOperationResult();
        result.success = false;
        result.planId = planId;
        result.failureInfo = failureInfo;
        result.message = message;
        return result;
    }
    
    // Getters and Setters...
}
```

#### 3.0.6 TaskOperationResult（Task 操作结果）

```java
package xyz.firestige.executor.application.dto;

/**
 * Task 操作结果
 * 用于单个 Task 级别的操作（暂停、恢复、回滚、重试、取消）
 * 明确区分 Task 聚合的操作结果
 */
public class TaskOperationResult {
    
    private boolean success;
    private String taskId;
    private TaskStatus status;
    private FailureInfo failureInfo;
    private String message;
    
    public static TaskOperationResult success(String taskId, TaskStatus status, String message) {
        TaskOperationResult result = new TaskOperationResult();
        result.success = true;
        result.taskId = taskId;
        result.status = status;
        result.message = message;
        return result;
    }
    
    public static TaskOperationResult failure(String taskId, FailureInfo failureInfo, String message) {
        TaskOperationResult result = new TaskOperationResult();
        result.success = false;
        result.taskId = taskId;
        result.failureInfo = failureInfo;
        result.message = message;
        return result;
    }
    
    // Getters and Setters...
}
```

**设计价值**：
1. ⭐⭐⭐⭐⭐ **领域模型清晰**：Plan 和 Task 的聚合关系在返回值中明确表达
2. ⭐⭐⭐⭐ **类型安全**：编译期检查，避免 Plan ID 和 Task ID 混用
3. ⭐⭐⭐⭐ **可扩展性**：Plan 和 Task 可独立演进
4. ⭐⭐⭐⭐⭐ **DDD 最佳实践**：符合聚合根、值对象、工厂方法等模式

**详细分析**: 参见 `RF01_RESULT_DTO_ANALYSIS.md`

---

### 3.1 新增应用服务层

#### 3.1.1 PlanApplicationService

```java
package xyz.firestige.executor.application;

/**
 * Plan 应用服务
 * 负责 Plan 级别的业务编排和状态管理
 * 
 * 注意：
 * 1. 使用内部 DTO（TenantConfig），与外部 DTO（TenantDeployConfig）解耦
 * 2. 返回值使用 DDD 设计的 Result DTO（PlanCreationResult、PlanOperationResult）
 */
public class PlanApplicationService {
    
    private final ValidationChain validationChain;
    private final TaskStateManager stateManager;
    private final PlanFactory planFactory;
    private final PlanOrchestrator planOrchestrator;
    private final StageFactory stageFactory;
    private final TaskWorkerFactory workerFactory;
    private final ExecutorProperties executorProperties;
    private final HealthCheckClient healthCheckClient;
    private final CheckpointService checkpointService;
    private final SpringTaskEventSink eventSink;
    private final ConflictRegistry conflictRegistry;
    
    // 注册表管理（从 Facade 迁移）
    private final Map<String, PlanAggregate> planRegistry = new HashMap<>();
    private final Map<String, TaskAggregate> taskRegistry = new HashMap<>();
    private final Map<String, TaskRuntimeContext> contextRegistry = new HashMap<>();
    private final Map<String, List<TaskStage>> stageRegistry = new HashMap<>();
    private final Map<String, TaskExecutor> executorRegistry = new HashMap<>();
    private final Map<String, PlanStateMachine> planSmRegistry = new HashMap<>();
    
    /**
     * 创建切换任务 (Plan + Tasks)
     * @param configs 内部 DTO：租户配置列表
     * @return PlanCreationResult 包含成功/失败信息和 Plan 聚合信息
     */
    public PlanCreationResult createSwitchTask(List<TenantConfig> configs) {
        // 从 Facade 迁移过来的完整业务逻辑
        // 注意：这里接收的是 TenantConfig（内部 DTO），而非 TenantDeployConfig（外部 DTO）
        
        // 业务逻辑处理...
        
        // 成功时返回 PlanInfo（值对象，包含 Plan 和 Task 信息）
        PlanInfo planInfo = PlanInfo.from(plan);
        return PlanCreationResult.success(planInfo);
    }
    
    /**
     * 暂停整个 Plan
     * @return PlanOperationResult 明确返回 Plan 操作结果
     */
    public PlanOperationResult pausePlan(Long planId) {
        // 业务逻辑
        return PlanOperationResult.success(String.valueOf(planId), PlanStatus.PAUSED, "计划暂停成功");
    }
    
    /**
     * 恢复整个 Plan
     */
    public PlanOperationResult resumePlan(Long planId) {
        // 业务逻辑
        return PlanOperationResult.success(String.valueOf(planId), PlanStatus.RUNNING, "计划恢复成功");
    }
    
    /**
     * 回滚整个 Plan
     */
    public PlanOperationResult rollbackPlan(Long planId) {
        // 业务逻辑
        return PlanOperationResult.success(String.valueOf(planId), PlanStatus.ROLLED_BACK, "计划回滚完成");
    }
    
    /**
     * 重试整个 Plan
     */
    public PlanOperationResult retryPlan(Long planId, boolean fromCheckpoint) {
        // 业务逻辑
        return PlanOperationResult.success(String.valueOf(planId), PlanStatus.RUNNING, "计划重试启动");
    }
    
    // 辅助方法
    private String generatePlanId() { /* ... */ }
}
```

**关键变化**：
- 应用服务层方法参数从 `List<TenantDeployConfig>` 改为 `List<TenantConfig>`
- 返回值从 `TaskCreationResult` 改为 `PlanCreationResult`（语义清晰）
- 返回值从 `TaskOperationResult` 改为 `PlanOperationResult`（类型安全）
- `TenantConfig` 是内部 DTO，由 Facade 层从外部 DTO 转换而来
- 这样应用层接口与外部 DTO 解耦，外部 DTO 变化不影响应用层

#### 3.1.2 TaskApplicationService

```java
package xyz.firestige.executor.application;

/**
 * Task 应用服务
 * 负责单个 Task 级别的操作
 */
public class TaskApplicationService {
    
    // 注入必要依赖（可复用 PlanApplicationService 的注册表）
    private final TaskStateManager stateManager;
    private final TaskRepository taskRepository; // 可选：访问注册表的抽象
    
    /**
     * 根据租户 ID 暂停任务
     */
    public TaskOperationResult pauseTaskByTenant(String tenantId) {
        // 从注册表查找 Task，设置暂停标志
    }
    
    /**
     * 根据租户 ID 恢复任务
     */
    public TaskOperationResult resumeTaskByTenant(String tenantId) {
        // 业务逻辑
    }
    
    /**
     * 根据租户 ID 回滚任务
     */
    public TaskOperationResult rollbackTaskByTenant(String tenantId) {
        // 业务逻辑
    }
    
    /**
     * 根据租户 ID 重试任务
     */
    public TaskOperationResult retryTaskByTenant(String tenantId, boolean fromCheckpoint) {
        // 业务逻辑
    }
    
    /**
     * 查询任务状态
     */
    public TaskStatusInfo queryTaskStatus(String executionUnitId) {
        // 业务逻辑
    }
    
    /**
     * 根据租户 ID 查询任务状态
     */
    public TaskStatusInfo queryTaskStatusByTenant(String tenantId) {
        // 业务逻辑
    }
    
    /**
     * 取消任务
     */
    public TaskOperationResult cancelTask(String executionUnitId) {
        // 业务逻辑
    }
    
    /**
     * 根据租户 ID 取消任务
     */
    public TaskOperationResult cancelTaskByTenant(String tenantId) {
        // 业务逻辑
    }
}
```

### 3.2 重构后的 Facade 层

**设计决策：不定义接口，直接使用具体类**

**原因**：
1. 当前只有一个实现，符合 YAGNI 原则
2. 不同外部系统需要不同的防腐层（REST、MQ、gRPC），接口签名不同，无法共享接口
3. 防腐层本身就是边界，不需要额外的抽象层
4. 测试可以直接实例化具体类，无需 mock 接口

**未来扩展**：
- 如需对接 REST API：创建 `TaskRestController`（不同的接口签名）
- 如需对接 MQ：创建 `TaskMqListener`（不同的接口签名）
- 各防腐层独立演进，互不影响

#### 3.2.1 DeploymentTaskFacade 实现（无接口）

```java
package xyz.firestige.executor.facade;

import org.springframework.stereotype.Component;
import xyz.firestige.executor.application.PlanApplicationService;
import xyz.firestige.executor.application.TaskApplicationService;
import xyz.firestige.executor.facade.exception.*;

/**
 * 部署任务 Facade（重构版）
 * 职责：DTO 转换 + 参数校验 + 调用应用服务 + 异常转换
 * 
 * 设计说明：
 * - 不定义接口，直接使用具体类（符合 YAGNI 原则）
 * - 返回 void（查询操作除外），通过异常机制处理错误
 * - 负责外部 DTO → 内部 DTO 转换，保护应用层接口稳定
 */
@Component
public class DeploymentTaskFacade {

    private static final Logger logger = LoggerFactory.getLogger(DeploymentTaskFacade.class);

    private final PlanApplicationService planApplicationService;
    private final TaskApplicationService taskApplicationService;

    public DeploymentTaskFacade(
            PlanApplicationService planApplicationService,
            TaskApplicationService taskApplicationService) {
        this.planApplicationService = planApplicationService;
        this.taskApplicationService = taskApplicationService;
    }

    /**
     * 创建切换任务
     * @param configs 租户部署配置列表（外部 DTO）
     * @throws IllegalArgumentException 参数校验失败时抛出，包含详细错误信息
     * @throws TaskCreationException 任务创建失败时抛出
     */
    public void createSwitchTask(List<TenantDeployConfig> configs) {
        logger.info("[Facade] 创建切换任务，配置数量: {}", configs != null ? configs.size() : 0);
        
        // 1. 参数校验（快速失败）
        if (configs == null || configs.isEmpty()) {
            throw new IllegalArgumentException("配置列表不能为空");
        }
        
        // 2. DTO 转换：外部 DTO → 内部 DTO（保护应用层接口稳定）
        List<TenantConfig> internalConfigs = convertToInternalConfigs(configs);
        
        // 3. 调用应用服务（使用内部 DTO）
        PlanCreationResult result = planApplicationService.createSwitchTask(internalConfigs);
        
        // 4. 处理结果 - 根据用户诉求，失败时抛出异常
        if (!result.isSuccess()) {
            // 4.1 校验失败 - 包装详细错误信息到 IllegalArgumentException
            if (result.getValidationSummary() != null && result.getValidationSummary().hasErrors()) {
                String errorDetail = formatValidationErrors(result.getValidationSummary());
                throw new IllegalArgumentException("配置校验失败: " + errorDetail);
            }
            
            // 4.2 其他失败 - 抛出业务异常
            FailureInfo failureInfo = result.getFailureInfo();
            throw new TaskCreationException(
                failureInfo != null ? failureInfo.getErrorMessage() : "任务创建失败",
                failureInfo
            );
        }
        
        // 成功：记录日志（可选记录 Plan 信息）
        PlanInfo planInfo = result.getPlanInfo();
        logger.info("[Facade] Plan 创建成功，planId: {}, tasks: {}", 
                    planInfo.getPlanId(), planInfo.getTasks().size());
    }

    /**
     * 根据租户 ID 暂停任务
     * @param tenantId 租户 ID
     * @throws TaskNotFoundException 任务不存在时抛出
     * @throws TaskOperationException 操作失败时抛出
     */
    public void pauseTaskByTenant(String tenantId) {
        logger.info("[Facade] 暂停租户任务: {}", tenantId);
        
        TaskOperationResult result = taskApplicationService.pauseTaskByTenant(tenantId);
        handleTaskOperationResult(result, "暂停任务");
    }

    /**
     * 根据计划 ID 暂停任务
     * @param planId 计划 ID
     * @throws PlanNotFoundException 计划不存在时抛出
     * @throws TaskOperationException 操作失败时抛出
     */
    public void pauseTaskByPlan(Long planId) {
        logger.info("[Facade] 暂停计划: {}", planId);
        
        PlanOperationResult result = planApplicationService.pausePlan(planId);
        handlePlanOperationResult(result, "暂停计划");
    }

    public void resumeTaskByTenant(String tenantId) {
        logger.info("[Facade] 恢复租户任务: {}", tenantId);
        
        TaskOperationResult result = taskApplicationService.resumeTaskByTenant(tenantId);
        handleTaskOperationResult(result, "恢复任务");
    }

    public void resumeTaskByPlan(Long planId) {
        logger.info("[Facade] 恢复计划: {}", planId);
        
        PlanOperationResult result = planApplicationService.resumePlan(planId);
        handlePlanOperationResult(result, "恢复计划");
    }

    public void rollbackTaskByTenant(String tenantId) {
        logger.info("[Facade] 回滚租户任务: {}", tenantId);
        
        TaskOperationResult result = taskApplicationService.rollbackTaskByTenant(tenantId);
        handleTaskOperationResult(result, "回滚任务");
    }

    public void rollbackTaskByPlan(Long planId) {
        logger.info("[Facade] 回滚计划: {}", planId);
        
        PlanOperationResult result = planApplicationService.rollbackPlan(planId);
        handlePlanOperationResult(result, "回滚计划");
    }

    public void retryTaskByTenant(String tenantId, boolean fromCheckpoint) {
        logger.info("[Facade] 重试租户任务: {}, fromCheckpoint: {}", tenantId, fromCheckpoint);
        
        TaskOperationResult result = taskApplicationService.retryTaskByTenant(tenantId, fromCheckpoint);
        handleTaskOperationResult(result, "重试任务");
    }

    public void retryTaskByPlan(Long planId, boolean fromCheckpoint) {
        logger.info("[Facade] 重试计划: {}, fromCheckpoint: {}", planId, fromCheckpoint);
        
        PlanOperationResult result = planApplicationService.retryPlan(planId, fromCheckpoint);
        handlePlanOperationResult(result, "重试计划");
    }

    /**
     * 查询任务状态
     * @param executionUnitId 执行单 ID
     * @return 任务状态信息（查询操作保留返回值）
     * @throws TaskNotFoundException 任务不存在时抛出
     */
    public TaskStatusInfo queryTaskStatus(String executionUnitId) {
        logger.debug("[Facade] 查询任务状态: {}", executionUnitId);
        
        TaskStatusInfo result = taskApplicationService.queryTaskStatus(executionUnitId);
        
        // 查询失败时抛出异常
        if (result.getStatus() == null) {
            throw new TaskNotFoundException("任务不存在: " + executionUnitId);
        }
        
        return result;
    }

    public TaskStatusInfo queryTaskStatusByTenant(String tenantId) {
        logger.debug("[Facade] 查询租户任务状态: {}", tenantId);
        
        TaskStatusInfo result = taskApplicationService.queryTaskStatusByTenant(tenantId);
        
        if (result.getStatus() == null) {
            throw new TaskNotFoundException("租户任务不存在: " + tenantId);
        }
        
        return result;
    }

    public void cancelTask(String executionUnitId) {
        logger.info("[Facade] 取消任务: {}", executionUnitId);
        
        TaskOperationResult result = taskApplicationService.cancelTask(executionUnitId);
        handleOperationResult(result, "取消任务");
    }

    public void cancelTaskByTenant(String tenantId) {
        logger.info("[Facade] 取消租户任务: {}", tenantId);
        
        TaskOperationResult result = taskApplicationService.cancelTaskByTenant(tenantId);
        handleOperationResult(result, "取消任务");
    }

    // ========== 辅助方法 ==========

    /**
     * DTO 转换：外部 DTO → 内部 DTO
     * 保护应用层接口稳定性，外部 DTO 变化不影响应用层
     */
    private List<TenantConfig> convertToInternalConfigs(List<TenantDeployConfig> externalConfigs) {
        if (externalConfigs == null) {
            return Collections.emptyList();
        }
        
        return externalConfigs.stream()
            .map(this::convertToInternalConfig)
            .collect(Collectors.toList());
    }
    
    /**
     * 单个配置转换
     */
    private TenantConfig convertToInternalConfig(TenantDeployConfig external) {
        TenantConfig internal = new TenantConfig();
        internal.setPlanId(external.getPlanId());
        internal.setTenantId(external.getTenantId());
        internal.setConfigVersion(external.getConfigVersion());
        internal.setHealthCheckEndpoints(external.getHealthCheckEndpoints());
        internal.setPreviousConfigVersion(external.getPreviousConfigVersion());
        // ... 复制其他必要字段
        return internal;
    }

    /**
     * 统一处理 Plan 操作结果，失败时抛出异常
     */
    private void handlePlanOperationResult(PlanOperationResult result, String operation) {
        if (!result.isSuccess()) {
            String message = result.getMessage() != null ? result.getMessage() : operation + "失败";
            FailureInfo failureInfo = result.getFailureInfo();
            
            // 根据错误类型抛出不同异常
            if (message.contains("不存在") || message.contains("未找到")) {
                throw new PlanNotFoundException(message);
            } else {
                throw new TaskOperationException(message, failureInfo);
            }
        }
        
        logger.info("[Facade] {} 成功: {}", operation, result.getMessage());
    }

    /**
     * 统一处理 Task 操作结果，失败时抛出异常
     */
    private void handleTaskOperationResult(TaskOperationResult result, String operation) {
        if (!result.isSuccess()) {
            String message = result.getMessage() != null ? result.getMessage() : operation + "失败";
            FailureInfo failureInfo = result.getFailureInfo();
            
            // 根据错误类型抛出不同异常
            if (message.contains("不存在") || message.contains("未找到")) {
                throw new TaskNotFoundException(message);
            } else {
                throw new TaskOperationException(message, failureInfo);
            }
        }
        
        logger.info("[Facade] {} 成功: {}", operation, result.getMessage());
    }

    /**
     * 格式化校验错误信息
     */
    private String formatValidationErrors(ValidationSummary summary) {
        if (summary == null || !summary.hasErrors()) {
            return "未知校验错误";
        }
        
        List<String> allErrors = summary.getAllErrors();
        if (allErrors.size() <= 3) {
            return String.join("; ", allErrors);
        } else {
            // 只展示前 3 个错误 + 总数
            String preview = String.join("; ", allErrors.subList(0, 3));
            return String.format("%s... (共 %d 个错误)", preview, allErrors.size());
        }
    }
}
```

### 3.3 新增异常类型

在 `xyz.firestige.executor.facade.exception` 包下新增：

```java
// TaskCreationException.java
public class TaskCreationException extends RuntimeException {
    private final FailureInfo failureInfo;
    
    public TaskCreationException(String message, FailureInfo failureInfo) {
        super(message);
        this.failureInfo = failureInfo;
    }
    
    public FailureInfo getFailureInfo() {
        return failureInfo;
    }
}

// TaskOperationException.java
public class TaskOperationException extends RuntimeException {
    private final FailureInfo failureInfo;
    
    public TaskOperationException(String message, FailureInfo failureInfo) {
        super(message);
        this.failureInfo = failureInfo;
    }
    
    public FailureInfo getFailureInfo() {
        return failureInfo;
    }
}

// TaskNotFoundException.java
public class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException(String message) {
        super(message);
    }
}

// PlanNotFoundException.java
public class PlanNotFoundException extends RuntimeException {
    public PlanNotFoundException(String message) {
        super(message);
    }
}
```

---

## 4. 实施步骤

**重要原则**：
- ✅ 项目处于开发阶段，不保留旧代码，直接替换
- ✅ 使用 Git 管理风险，如遇问题可回退
- ✅ 每个 Phase 完成后提交，便于回退

### 4.1 Phase 1: 创建 Result DTO（DDD 设计）

1. ✅ 创建 `xyz.firestige.executor.application.dto` 包
2. ✅ 定义新的 Result DTO 类（5 个）：
   - `PlanCreationResult`：Plan 创建结果
   - `PlanInfo`：Plan 聚合信息（值对象，不可变）
   - `TaskInfo`：Task 信息（值对象，不可变）
   - `PlanOperationResult`：Plan 操作结果
   - `TaskOperationResult`：Task 操作结果（重新定义，明确用于 Task 聚合）
3. ✅ 实现静态工厂方法（`from`、`success`、`failure`）
4. ✅ Git commit + tag: "feat: add DDD-based Result DTOs"

### 4.2 Phase 2: 创建内部 DTO（不破坏现有代码）

1. ✅ 在 `xyz.firestige.executor.application.dto` 包中定义 `TenantConfig`（内部 DTO）
   - 参考 `TenantDeployConfig` 字段设计
   - 只包含应用层需要的字段（去除外部系统特有字段）
2. ✅ Git commit + tag: "feat: add internal DTO TenantConfig"

### 4.3 Phase 3: 创建应用服务层（不破坏现有代码）

1. ✅ 创建 `xyz.firestige.executor.application` 包
2. ✅ 实现 `PlanApplicationService`
   - 从 `DeploymentTaskFacadeImpl` 迁移 `createSwitchTask` 业务逻辑
   - 修改参数类型为 `List<TenantConfig>`（内部 DTO）
   - 修改返回值类型为 `PlanCreationResult` / `PlanOperationResult`
   - 迁移所有注册表字段
   - 迁移 Plan 级别操作方法（pause/resume/rollback/retry）
3. ✅ 实现 `TaskApplicationService`
   - 迁移 Task 级别操作方法（按租户查找、状态查询、操作执行）
   - 返回值类型为 `TaskOperationResult`（新版本）
   - 与 `PlanApplicationService` 共享注册表（通过构造器注入或共享服务）
4. ✅ 编写应用服务层单元测试
5. ✅ Git commit + tag: "feat: add application service layer with DDD Result DTOs"

### 4.4 Phase 4: 创建新 Facade + 删除旧代码

1. ✅ 创建 `xyz.firestige.executor.facade.exception` 包
2. ✅ 实现所有异常类（TaskCreationException、TaskOperationException、TaskNotFoundException、PlanNotFoundException）
3. ✅ 重命名旧 Facade：`DeploymentTaskFacadeImpl` → `DeploymentTaskFacadeImpl_OLD`（临时保留供参考）
4. ✅ 创建新 `DeploymentTaskFacade`（无接口，直接实现类）
   - 实现 DTO 转换逻辑（外部 DTO → 内部 DTO）
   - 实现异常转换逻辑（Result DTO → 异常）
   - 调用应用服务层
   - 分别处理 `PlanOperationResult` 和 `TaskOperationResult`
5. ✅ 编写 Facade 层单元测试（重点测试 DTO 转换和异常转换）
6. ✅ Git commit + tag: "feat: refactor facade layer with new design"

### 4.5 Phase 5: 迁移测试 + 清理旧代码

1. ✅ 更新所有集成测试
   - 修改为断言异常（而非断言返回值）
   - 使用新的 `DeploymentTaskFacade`
2. ✅ 更新所有引用旧 Facade 的地方
   - Spring 配置（如有）
   - 测试代码
3. ✅ **删除旧代码**
   - 删除 `DeploymentTaskFacadeImpl_OLD`
   - 删除旧的 Result DTO（`facade/TaskCreationResult`、`facade/TaskOperationResult`、`facade/TaskStatusInfo`）
4. ✅ Git commit + tag: "refactor: remove old facade and result DTOs"

### 4.6 Phase 6: 验证与文档更新

1. ✅ 运行完整测试套件（`mvn clean test`）
2. ✅ 检查测试覆盖率
3. ✅ 更新文档
   - ARCHITECTURE_PROMPT.md（反映新架构）
   - TODO.md（标记 RF-01 完成）
   - develop.log（记录变更）
4. ✅ Git commit: "docs: update architecture documentation for RF-01"
5. ✅ Code Review
6. ✅ 性能回归测试（可选）

---

## 5. 关键决策记录

### 5.1 防腐层为什么不定义接口？

**决策**: `DeploymentTaskFacade` 不定义接口，直接使用具体类

**原因**：
1. **YAGNI 原则**（You Aren't Gonna Need It）
   - 目前只有一个实现，没有多态替换需求
   - 过早抽象会增加不必要的复杂度

2. **不同防腐层服务于不同外部系统**
   ```java
   // 场景 1：直接方法调用（当前）
   class DeploymentTaskFacade {
       void createSwitchTask(List<TenantDeployConfig> configs);
   }
   
   // 场景 2：REST API（未来）
   @RestController
   class TaskRestController {
       @PostMapping("/tasks")
       ResponseEntity<Void> createTask(@RequestBody CreateTaskRequest request);
   }
   
   // 场景 3：消息队列（未来）
   class TaskMqListener {
       @RabbitListener(queues = "task.create")
       void handleCreateTask(CreateTaskMessage message);
   }
   ```
   这三者接口签名完全不同，无法共享接口

3. **防腐层本身就是边界**
   - 防腐层的职责是隔离外部系统变化对内部的影响
   - 不同外部系统需要不同的防腐层实现
   - 它们之间不需要多态替换，各自独立演进

4. **测试依然简单**
   ```java
   // 直接实例化具体类进行测试
   @Test
   void should_create_task_successfully() {
       DeploymentTaskFacade facade = new DeploymentTaskFacade(
           planApplicationService, 
           taskApplicationService
       );
       assertDoesNotThrow(() -> facade.createSwitchTask(configs));
   }
   ```

**未来扩展**：
- 需要对接新系统时，直接创建新的防腐层类（如 `TaskRestController`、`TaskMqListener`）
- 各防腐层独立设计接口，不强制统一
- 灵活性更高，职责更清晰

### 5.2 为什么 Facade 返回 void？

**原因**：
1. Facade 作为防腐层，主要职责是隔离外部接口与内部实现
2. 现代 API 设计推荐通过异常机制处理错误，而非返回错误码/状态对象
3. 统一异常处理便于上层（如未来的 REST Controller）进行全局异常拦截

**例外**：
- `queryTaskStatus` 方法保留返回值，因为查询操作本质上需要返回数据
- 如果未来需要返回创建成功的 ID，可以考虑返回 `String planId`

### 5.2 为什么 Facade 返回 void？

**原因**：
1. Facade 作为防腐层，主要职责是隔离外部接口与内部实现
2. 现代 API 设计推荐通过异常机制处理错误，而非返回错误码/状态对象
3. 统一异常处理便于上层（如未来的 REST Controller）进行全局异常拦截

**例外**：
- `queryTaskStatus` 方法保留返回值，因为查询操作本质上需要返回数据
- 如果未来需要返回创建成功的 ID，可以考虑返回 `String planId`

### 5.3 为什么应用服务层仍返回 Result 对象？

**原因**：
1. 应用服务层负责业务编排，需要明确表达成功/失败的业务语义
2. Result 对象可以携带丰富的上下文信息（ValidationSummary、FailureInfo）
3. 便于 Facade 层根据不同失败类型进行精细化的异常转换
4. 保持与现有测试代码的兼容性，减少改动范围

### 5.4 注册表应该放在哪里？

**决策**: 移到 `PlanApplicationService` 中

**原因**：
1. 注册表本质上是应用状态管理，属于应用服务层职责
2. 领域层（TaskAggregate）不应该知道注册表的存在
3. 未来可以演进为更完善的 Repository 模式（内存 → Redis → DB）

**可选方案**：
- 创建独立的 `TaskRepository` 接口，注入到应用服务中
- 实现 `InMemoryTaskRepository`，后续可扩展为 `RedisTaskRepository`

### 5.5 Facade 层为什么要做 DTO 转换？

**决策**: Facade 负责 `TenantDeployConfig`（外部 DTO）→ `TenantConfig`（内部 DTO）转换

**原因**：
1. **保护应用层接口稳定性**：外部 DTO 变化（字段增删、重命名）不影响应用层
2. **防腐层职责**：Facade 作为防腐层，隔离外部系统变化对内部系统的影响
3. **清晰的边界**：外部 DTO 用于外部集成，内部 DTO 用于内部业务逻辑
4. **易于演进**：未来可以支持多种外部 DTO（REST API、MQ 消息）映射到同一内部 DTO

**设计细节**：
```java
// 外部 DTO（来自外部系统，可能频繁变化）
TenantDeployConfig (xyz.firestige.dto.deploy 包)

// 内部 DTO（稳定的内部契约，由应用服务层定义）
TenantConfig (xyz.firestige.executor.application.dto 包)

// Facade 负责转换
List<TenantConfig> internal = convertToInternalConfigs(externalConfigs);
```

**未来扩展**：
- 如果引入 REST API，可能有新的 `CreateTaskRequest` DTO
- Facade 可以将 `CreateTaskRequest` 也转换为 `TenantConfig`
- 应用服务层接口保持不变，只接收 `TenantConfig`

### 5.6 内部 DTO（TenantConfig）应该定义在哪里？

**决策**: 定义在 `xyz.firestige.executor.application.dto` 包

**原因**：
1. 内部 DTO 是应用层的契约，属于应用层的一部分
2. 与外部 DTO 清晰分离（外部 DTO 在 `xyz.firestige.dto.deploy` 包）
3. 便于后续演进为更规范的 DDD 分层结构

**包结构**：
```
xyz.firestige.executor
├── application/
│   ├── PlanApplicationService.java
│   ├── TaskApplicationService.java
│   └── dto/
│       ├── TenantConfig.java        // 内部 DTO
│       └── TaskQueryCriteria.java   // 未来可能的查询条件 DTO
├── facade/
│   ├── DeploymentTaskFacade.java
│   └── DeploymentTaskFacadeImpl.java // 负责 DTO 转换
└── domain/
    ├── plan/
    └── task/
```

---

## 6. 风险评估与缓解

**前提**：项目处于开发阶段，无需向后兼容，使用 Git 管理风险

### 6.1 测试迁移风险

**风险**: 大量测试需要从断言返回值改为断言异常

**缓解措施**：
1. 应用服务层测试保持不变（仍断言 Result 对象）
2. 仅 Facade 层和集成测试需要改为断言异常
3. 提供测试工具类简化异常断言：
   ```java
   public class TestAssertions {
       public static void assertThrowsWithMessage(
           Class<? extends Exception> expectedType,
           String expectedMessageContains,
           Executable executable) {
           Exception ex = assertThrows(expectedType, executable);
           assertTrue(ex.getMessage().contains(expectedMessageContains));
       }
   }
   ```
4. 每个 Phase 完成后运行测试，逐步验证

### 6.2 业务逻辑迁移风险

**风险**: 从 Facade 迁移业务逻辑到应用服务层时可能遗漏细节

**缓解措施**：
1. 使用 IDE 的"提取方法"重构功能，减少手工错误
2. 先复制粘贴完整逻辑，再逐步调整参数类型
3. 对比新旧实现的测试覆盖率
4. Code Review 重点检查业务逻辑完整性
5. Git commit 粒度要细，便于定位问题和回退

### 6.3 DTO 转换风险

**风险**: Facade 层 DTO 转换逻辑可能遗漏字段

**缓解措施**：
1. 参考 `TenantDeployConfig` 的所有字段逐一映射
2. 编写专门的 DTO 转换测试：
   ```java
   @Test
   void should_convert_all_fields_from_external_to_internal_dto() {
       TenantDeployConfig external = createFullConfig();
       TenantConfig internal = facade.convertToInternalConfig(external);
       
       // 逐字段断言
       assertEquals(external.getTenantId(), internal.getTenantId());
       assertEquals(external.getConfigVersion(), internal.getConfigVersion());
       // ... 所有字段
   }
   ```
3. 如果有遗漏，集成测试会快速发现

### 6.4 注册表状态风险

**风险**: 注册表迁移到应用服务层后，状态管理可能出现问题

**缓解措施**：
1. Phase 2 完成后，先不删除旧代码，对比运行测试
2. 检查注册表的并发安全性（当前使用 HashMap，未来可能需要 ConcurrentHashMap）
3. 确认注册表的生命周期管理（Spring 单例作用域）

### 6.5 回退策略

**风险**: 重构失败需要回退

**缓解措施**：
1. 每个 Phase 完成后 Git commit，并打上 tag
   ```bash
   git tag rf01-phase1-internal-dto
   git tag rf01-phase2-application-service
   git tag rf01-phase3-new-facade
   git tag rf01-phase4-cleanup
   ```
2. 如果某个 Phase 出现问题，直接回退：
   ```bash
   git reset --hard rf01-phase2-application-service
   ```
3. 如果整个重构失败，回退到起点：
   ```bash
   git reset --hard <重构前的commit>
   ```

### 6.6 性能风险

**风险**: 引入应用服务层和 DTO 转换可能影响性能

**评估**: 影响极小
- DTO 转换只是简单的字段拷贝（内存操作）
- 应用服务层调用是 JVM 内部方法调用（可内联优化）
- 没有引入网络调用、序列化、I/O 等高开销操作

**验证**：
- Phase 5 可选运行性能回归测试
- 对比重构前后的任务创建耗时

---

## 7. 示例对比

### 7.1 Before（当前架构）

```java
// Controller 调用 Facade
TaskCreationResult result = facade.createSwitchTask(configs);
if (result.isSuccess()) {
    return ResponseEntity.ok(result.getPlanId());
} else {
    return ResponseEntity.badRequest().body(result.getMessage());
}
```

### 7.2 After（重构后）

```java
// Controller 调用 Facade + 全局异常处理
try {
    facade.createSwitchTask(configs);
    return ResponseEntity.ok("任务创建成功");
} catch (IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(e.getMessage());
} catch (TaskCreationException e) {
    return ResponseEntity.status(500).body(e.getMessage());
}

// 或使用全局异常处理器（推荐）
@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handle(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
    
    @ExceptionHandler(TaskCreationException.class)
    public ResponseEntity<String> handle(TaskCreationException e) {
        return ResponseEntity.status(500).body(e.getMessage());
    }
}
```

---

## 8. 测试策略

### 8.1 应用服务层测试

```java
@Test
void should_create_plan_with_tasks_successfully() {
    // Given - 使用内部 DTO
    List<TenantConfig> configs = createValidInternalConfigs();
    
    // When
    PlanCreationResult result = planApplicationService.createSwitchTask(configs);
    
    // Then
    assertTrue(result.isSuccess());
    
    // 验证 Plan 聚合信息
    PlanInfo planInfo = result.getPlanInfo();
    assertNotNull(planInfo.getPlanId());
    assertEquals(3, planInfo.getTasks().size());
    
    // 验证 Task 信息
    TaskInfo firstTask = planInfo.getTasks().get(0);
    assertEquals("tenant-1", firstTask.getTenantId());
    assertEquals(TaskStatus.PENDING, firstTask.getStatus());
}

@Test
void should_return_validation_failure_when_configs_invalid() {
    // Given - 使用内部 DTO
    List<TenantConfig> configs = createInvalidInternalConfigs();
    
    // When
    PlanCreationResult result = planApplicationService.createSwitchTask(configs);
    
    // Then
    assertFalse(result.isSuccess());
    assertTrue(result.getValidationSummary().hasErrors());
}

@Test
void should_pause_plan_successfully() {
    // Given
    Long planId = 123L;
    
    // When
    PlanOperationResult result = planApplicationService.pausePlan(planId);
    
    // Then
    assertTrue(result.isSuccess());
    assertEquals("123", result.getPlanId());
    assertEquals(PlanStatus.PAUSED, result.getStatus());
}

@Test
void should_pause_task_successfully() {
    // Given
    String tenantId = "tenant-1";
    
    // When
    TaskOperationResult result = taskApplicationService.pauseTaskByTenant(tenantId);
    
    // Then
    assertTrue(result.isSuccess());
    assertNotNull(result.getTaskId());
    assertEquals(TaskStatus.PAUSED, result.getStatus());
}
```

### 8.2 Facade 层测试

```java
@Test
void should_create_switch_task_successfully() {
    // Given - 使用外部 DTO
    List<TenantDeployConfig> configs = createValidExternalConfigs();
    
    // When & Then (无异常即成功)
    assertDoesNotThrow(() -> facade.createSwitchTask(configs));
    
    // 验证 DTO 转换逻辑
    verify(planApplicationService).createSwitchTask(argThat(internalConfigs -> 
        internalConfigs.size() == configs.size() &&
        internalConfigs.get(0).getTenantId().equals(configs.get(0).getTenantId())
    ));
}

@Test
void should_throw_IllegalArgumentException_when_configs_invalid() {
    // Given - 使用外部 DTO
    List<TenantDeployConfig> configs = createInvalidExternalConfigs();
    
    // When & Then
    IllegalArgumentException ex = assertThrows(
        IllegalArgumentException.class,
        () -> facade.createSwitchTask(configs)
    );
    
    assertTrue(ex.getMessage().contains("配置校验失败"));
}

@Test
void should_throw_TaskNotFoundException_when_tenant_not_exists() {
    // When & Then
    TaskNotFoundException ex = assertThrows(
        TaskNotFoundException.class,
        () -> facade.pauseTaskByTenant("non-existent-tenant")
    );
    
    assertTrue(ex.getMessage().contains("租户任务不存在"));
}

@Test
void should_convert_dto_correctly() {
    // Given - 测试 DTO 转换逻辑
    TenantDeployConfig external = createExternalConfig();
    
    // When
    facade.createSwitchTask(List.of(external));
    
    // Then - 验证内部 DTO 的字段正确转换
    verify(planApplicationService).createSwitchTask(argThat(internalConfigs -> {
        TenantConfig internal = internalConfigs.get(0);
        return internal.getTenantId().equals(external.getTenantId()) &&
               internal.getConfigVersion().equals(external.getConfigVersion()) &&
               internal.getHealthCheckEndpoints().equals(external.getHealthCheckEndpoints());
    }));
}
```

---

## 9. 后续优化方向

1. **引入 Repository 接口**
   - 将注册表抽象为 `PlanRepository`、`TaskRepository`
   - 支持多种实现（InMemory、Redis、MySQL）

2. **引入事件总线**
   - 应用服务发布领域事件
   - 解耦状态管理和事件发布

3. **引入命令模式**
   - 将操作封装为 Command 对象（CreateSwitchTaskCommand、PauseTaskCommand）
   - 支持命令审计、撤销/重做

4. **引入 CQRS**
   - 查询操作（queryTaskStatus）使用独立的查询服务
   - 命令操作（create/pause/resume）使用应用服务

---

## 10. 总结

本重构方案：
- ✅ 满足用户诉求
  - 应用服务层返回 Result DTO（重构为 DDD 设计）
  - Facade 返回 void + 异常
  - Facade 负责 DTO 转换（外部 → 内部）
  
- ✅ 符合分层架构原则
  - Facade：防腐层，DTO 转换 + 异常转换
  - Application Service：业务编排 + 状态管理
  - Domain：领域逻辑 + 状态机
  
- ✅ 符合 DDD 最佳实践
  - **明确聚合边界**：`PlanCreationResult` 包含 `PlanInfo`，体现 Plan 包含 Task 的聚合关系
  - **通用语言一致**：`PlanOperationResult` vs `TaskOperationResult`，语义清晰
  - **类型安全**：编译期检查，避免 Plan ID 和 Task ID 混用
  - **值对象不可变**：`PlanInfo` 和 `TaskInfo` 为不可变值对象
  
- ✅ 便于测试
  - 应用服务层测试断言 Result DTO，验证业务逻辑
  - Facade 层测试断言异常，验证 DTO 转换和异常转换
  
- ✅ 易于扩展
  - Plan 和 Task 可独立演进，互不影响
  - 未来可引入 Repository、事件总线、CQRS 等
  
- ✅ 项目处于开发阶段，无需向后兼容
  - 直接替换旧代码，通过 Git tag 管理风险
  - 每个 Phase 完成后测试验证

**核心价值**：
1. ⭐⭐⭐⭐⭐ **领域模型清晰度**：Plan 和 Task 聚合关系明确
2. ⭐⭐⭐⭐ **类型安全**：编译期检查，减少运行时错误
3. ⭐⭐⭐⭐ **可扩展性**：Plan 和 Task 独立演进
4. ⭐⭐⭐⭐⭐ **DDD 最佳实践**：符合聚合根、值对象、工厂方法等模式
5. ⭐⭐⭐ **测试可读性**：测试意图清晰，自解释

**实施路线**：6 个 Phase（Phase 1: Result DTO → Phase 2: 内部 DTO → Phase 3: 应用服务层 → Phase 4: Facade → Phase 5: 清理 → Phase 6: 验证）

**建议审批后立即开始实施，每个 Phase 完成后进行测试验证并打 Git tag。**

**详细分析**：
- Result DTO 重构分析：`RF01_RESULT_DTO_ANALYSIS.md`
- 关键决策总结：`RF01_DESIGN_DECISIONS.md`

