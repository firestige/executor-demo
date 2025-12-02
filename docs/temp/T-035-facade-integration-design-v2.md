# T-035 Facade 集成设计：打通 Retry/Rollback 调用链路

> **设计日期**: 2025-12-02  
> **设计状态**: 设计完成  
> **相关任务**: T-035 无状态执行器重构  

---

## 📋 设计目标

**打通从 DeploymentTaskFacade 到 TaskExecutor 的 Retry/Rollback 调用链路**，适配 T-035 无状态执行器改造。

### 核心改造点

1. ✅ **每次调用创建新 Task**：不复用 taskId，每次 retry/rollback 都是新任务
2. ✅ **简化 Facade API**：只保留 create/retry/rollback，废弃查询和状态管理方法
3. ✅ **复用现有编排能力**：retry/rollback 通过 TaskOperationService 直接创建和执行
4. ✅ **事件驱动**：Caller 监听领域事件自行持久化状态
5. ✅ **最小改动原则**：复用现有类和方法签名（必须修改的除外）

---

## 🎯 核心问题分析

### T-035 改造前后对比

| 维度 | T-035 前 | T-035 后（目标） |
|------|---------|----------------|
| **Task 复用** | 同一个 taskId 反复重试 | 每次创建新 Task（新 taskId） |
| **状态持久化** | CheckpointService 自动保存 | Caller 监听事件自行持久化 |
| **恢复机制** | 查询 TaskId → 恢复状态 | Caller 提供完整参数（config + lastCompleteStageName） |
| **Facade API** | 多个查询/状态管理方法 | 只保留 create/retry/rollback |
| **Plan 角色** | 必须持久化 | 单任务操作不持久化 |

### 关键设计决策

#### 决策 1：每次创建新 Task

**背景**：
- Caller 不关心 taskId
- 不需要查询历史 Task
- 每次调用都是独立的执行请求

**方案**：
```java
// 旧方式（废弃）
retryTask(String taskId, ...) {
    TaskAggregate task = taskRepository.findById(taskId); // 查询旧 Task
    // 复用 taskId
}

// 新方式
retryTask(TenantDeployConfig config, String lastCompleteStageName) {
    TaskId newTaskId = TaskId.generate(); // 生成新 ID
    TaskAggregate newTask = TaskRecoveryService.recoverForRetry(
        newTaskId,  // 新 Task
        config,
        lastCompleteStageName
    );
}
```

#### 决策 2：废弃查询方法

**背景**：
- Caller 监听事件自行管理状态
- 不需要通过 Facade 查询状态

**废弃的方法**：
```java
// ❌ 全部废弃
- pauseTaskByTenant(tenantId)
- resumeTaskByTenant(tenantId)  
- pauseTaskByPlan(planId)
- resumeTaskByPlan(planId)
- cancelTaskByTenant(tenantId)
- queryTaskStatus(taskId)
- queryTaskStatusByTenant(tenantId)
```

#### 决策 3：planId 和 tenantId 从 Config 传入

**背景**：
- `planId` 和 `tenantId` 都包含在 `TenantDeployConfig` 中
- 只有 `taskId` 需要 Facade 层生成

**方案**：
```java
// ✅ 正确：从 config 获取
String tenantId = config.getTenantId();
String planId = config.getPlanId();  // 如果有的话

// ✅ 正确：只有 taskId 需要生成
TaskId newTaskId = TaskId.generate();

// ❌ 错误：不要自己生成 planId 或 tenantId
PlanId tempPlanId = PlanId.generate();  // 错误！
```

---

## 🏗️ 简化后的 Facade API

### DeploymentTaskFacade 最终 API

```java
public class DeploymentTaskFacade {
    
    /**
     * ✅ 保留：创建切换任务（批量）
     * 
     * @param configs 租户配置列表
     */
    public void createSwitchTask(List<TenantDeployConfig> configs);
    
    /**
     * ✅ 新增：重试任务
     * 
     * @param config 租户配置（包含目标版本等信息）
     * @param lastCompleteStageName 最后完成的 Stage 名称（由 Caller 提供）
     */
    public void retryTask(TenantDeployConfig config, String lastCompleteStageName);
    
    /**
     * ✅ 新增：回滚任务
     * 
     * @param config 旧版本配置（回滚目标）
     * @param lastCompleteStageName 最后完成的 Stage 名称（由 Caller 提供）
     * @param version 单调递增的操作版本号（用于版本校验）
     */
    public void rollbackTask(
        TenantDeployConfig config, 
        String lastCompleteStageName, 
        String version
    );
}
```

### API 参数说明

#### 1. TenantDeployConfig

```java
public class TenantDeployConfig {
    private Long deployUnitId;
    private Long deployUnitVersion;      // 目标版本
    private String tenantId;             // ✅ 从外部传入
    private String planId;               // ✅ 从外部传入（如果有）
    private List<NetworkEndpoint> networkEndpoints;
    private List<String> serviceNames;   // 需要切换的服务
    // ... 其他字段
}
```

#### 2. lastCompleteStageName

- **类型**：`String`
- **来源**：Caller 监听 `TaskStageCompletedEvent` 事件获取
- **用途**：计算恢复起点（retry 从 lastComplete+1 开始，rollback 执行 0~lastComplete+1）

#### 3. version（仅 rollback）

- **类型**：`String`
- **语义**：单调递增的操作版本号
- **用途**：避免版本标记回拨（确保操作顺序正确）

---

## 🏛️ 架构设计

### 整体调用链路

```
┌─────────────────────┐
│  External Caller    │
│  (监听事件持久化)    │
└──────────┬──────────┘
           │ 调用 Facade API
           ↓
┌─────────────────────────────────────────────┐
│         DeploymentTaskFacade                │
│         (胶水层 - 只做转换和委派)             │
│  ┌────────────────────────────────────┐    │
│  │ retryTask(config, lastComplete)    │    │
│  │ rollbackTask(config, lastComplete, │    │
│  │              version)               │    │
│  └─────────────┬──────────────────────┘    │
│                │ 1. DTO 转换                │
│                │ 2. 委派调用                │
└────────────────┼───────────────────────────┘
                 ↓
┌─────────────────────────────────────────────┐
│       TaskOperationService                  │
│       (应用层 - 负责编排)                    │
│  ┌────────────────────────────────────┐    │
│  │ retryTaskByTenant(config, taskId,  │    │
│  │                   lastComplete)     │    │
│  │ rollbackTaskByTenant(tenantId,     │    │
│  │                      version)       │    │
│  └─────────────┬──────────────────────┘    │
│                │ 1. 调用 TaskDomainService  │
│                │ 2. 创建 TaskExecutor       │
│                │ 3. 异步执行                │
└────────────────┼───────────────────────────┘
                 ↓
         TaskDomainService
         (准备恢复上下文)
                 ↓
         TaskRecoveryService
         (重建 TaskAggregate)
                 ↓
         TaskExecutor
         (无状态执行)
                 ↓
        发布领域事件
        ├─ TaskStartedEvent
        ├─ TaskStageCompletedEvent
        ├─ TaskCompletedEvent
        └─ TaskFailedEvent
```

### 核心组件职责

#### 1. DeploymentTaskFacade（门面层 - 胶水层）

- **职责**：外部 API 入口（纯胶水层，不做编排）
- **功能**：
  - DTO 转换（`TenantDeployConfig` → `TenantConfig`）
  - 参数标准化
  - 委派给应用层服务（`TaskOperationService`）
- **保持不变**：`createSwitchTask()` 方法
- **新增**：`retryTask()` 和 `rollbackTask()` 方法

#### 2. TaskOperationService（应用层服务）

- **职责**：单任务的操作编排（retry/rollback 的核心逻辑）
- **功能**：
  - `retryTask(TenantConfig, String lastCompletedStageName)` 方法
  - `rollbackTask(TenantConfig, String lastCompletedStageName, String version)` 方法
  - 异步执行，发布领域事件
- **状态**：⚠️ 需要适配新的调用方式（修改方法名和签名）

#### 3. TaskRecoveryService（恢复层）

- **职责**：重建 TaskAggregate
- **功能**：
  - 使用 `StageFactory.buildStages()` 重建 stages
  - 使用 `StageFactory.calculateStartIndex()` 计算起始索引
  - **Retry**：从 lastCompletedIndex+1 继续执行
  - **Rollback**：使用旧版本配置重新执行 stages（不是逆向）
- **状态**：✅ 已实现，TaskDomainService 内部调用
  - 调用 `TaskExecutor.execute()`
- **状态**：✅ 已实现，无需修改

#### 4. TaskExecutor（执行层）
- **职责**：无状态执行引擎
- **功能**：
  - 只关心：初始状态 → 领域操作 → 结束状态
  - 不维护状态，不持久化
  - 发布领域事件
- **状态**：✅ T-035 已改造完成

---

## 🔄 Retry 流程设计

### 完整调用流程

```java
// ===== 1. Caller 调用 Facade =====
DeploymentTaskFacade.retryTask(
    tenantDeployConfig,      // 包含目标版本等信息
    "PreCheckStage"          // 最后完成的 Stage
);

// ===== 2. Facade 处理（胶水层 - 只做转换和委派）=====
public void retryTask(TenantDeployConfig config, String lastCompleteStageName) {
    // 2.1 DTO 转换（tenantId 和 planId 从 config 中获取）
    TenantConfig tenantConfig = tenantConfigConverter.convert(config);
    
    // 2.2 委派给应用层服务
    taskOperationService.retryTask(
        tenantConfig,
        lastCompleteStageName
    );
}

// ===== 3. TaskOperationService 编排（应用层）=====
public TaskOperationResult retryTask(
    TenantConfig config,                  // 用于重试的配置（和上次执行一样）
    String lastCompletedStageName         // 最近完成的 stage 名称（null 表示从头重试）
) {
    // 3.1 调用领域服务准备重试上下文
    // 注意：lastCompletedStageName 为 null 时，从头到尾全部重试
    TaskWorkerCreationContext context = taskDomainService.prepareRetry(
        config,
        lastCompletedStageName
    );
    
    // 3.2 设置重试标志位
    context.getRuntimeContext().requestRetry(lastCompletedStageName);
    
    // 3.3 创建 TaskExecutor
    TaskExecutor executor = taskWorkerFactory.create(context);
    
    // 3.4 异步执行
    CompletableFuture.runAsync(() -> {
        executor.execute();
    });
    
    return TaskOperationResult.success(...);
}

// ===== 4. TaskDomainService → TaskRecoveryService =====
// (内部实现，恢复 TaskAggregate)

// ===== 5. TaskExecutor 执行 + 发布事件 =====
public void execute() {
    task.start();  // → TaskStartedEvent
    
    for (Stage stage : task.getRemainingStages()) {
        stage.execute(context);  // → TaskStageCompletedEvent
    }
    
    task.complete();  // → TaskCompletedEvent
}
```

### Retry 关键点

| 项目 | 说明 |
|------|------|
| **新 Task**| 每次生成新 `TaskId`，不复用 |
| **执行范围** | 从 lastCompletedIndex+1 继续执行剩余 stages |
| **参数来源** | Caller 提供 `lastCompleteStageName`（监听事件获取） |
| **事件通知** | 异步，Caller 监听 `TaskStageCompletedEvent` 等事件 |

---

## ⏮️ Rollback 流程设计

### 完整调用流程

```java
// ===== 1. Caller 调用 Facade =====
DeploymentTaskFacade.rollbackTask(
    oldVersionConfig,        // 旧版本配置（回滚目标）
    "DataMigrationStage",    // 最后完成的 Stage
    "v100"                   // 操作版本号
);

// ===== 2. Facade 处理（胶水层 - 只做转换和委派）=====
public void rollbackTask(
    TenantDeployConfig config,
    String lastCompleteStageName,
    String version
) {
    // 2.1 DTO 转换（tenantId 和 planId 从 config 中获取）
    TenantConfig tenantConfig = tenantConfigConverter.convert(config);
    
    // 2.2 委派给应用层服务（不需要生成 taskId，由应用层管理）
    taskOperationService.rollbackTaskByTenant(
        TenantId.of(tenantConfig.getTenantId()),
        version
    );
}

// ===== 3. TaskOperationService 编排（应用层）=====
public TaskOperationResult rollbackTask(
    TenantConfig oldConfig,               // 旧版本配置
    String lastCompletedStageName,        // 最近完成的 stage 名称（null 表示全部回滚）
    String version                        // 操作版本号
) {
    // 3.1 调用领域服务准备回滚上下文
    // 注意：回滚不是逆向操作，而是用旧版本配置重新执行 stages
    TaskWorkerCreationContext context = taskDomainService.prepareRollback(
        oldConfig,
        lastCompletedStageName,
        version
    );
    
    // 3.2 设置回滚标志位
    context.getRuntimeContext().requestRollback(version);
    
    // 3.3 创建 TaskExecutor
    TaskExecutor executor = taskWorkerFactory.create(context);
    
    // 3.4 异步执行
    CompletableFuture.runAsync(() -> {
        executor.execute();
    });
    
    return TaskOperationResult.success(...);
}

// ===== 4. TaskDomainService → TaskRecoveryService =====
// (内部实现，恢复 TaskAggregate)

// ===== 5. TaskExecutor 执行 + 发布事件 =====
public void execute() {
    task.start();  // → TaskStartedEvent
    
    for (Stage stage : task.getRemainingStages()) {
        stage.execute(context);  // → TaskStageCompletedEvent
    }
    
    task.complete();  // → TaskCompletedEvent
}
```

### Rollback 关键点

| 项目 | 说明 |
|------|------|
| **回滚原理** | 用旧版本配置重新执行 stages，刷回已变更的内容（不是逆向操作） |
| **配置来源** | 使用**旧版本**配置（回滚目标） |
| **版本参数** | `version` 用于单调递增版本校验（避免版本回拨） |
| **执行方式** | 正向执行 stages，使用旧版本配置 |

---

## 📝 实现清单

### 需要修改的文件

| 文件 | 路径 | 改动类型 | 说明 |
|------|------|---------|------|
| `DeploymentTaskFacade.java` | `deploy/.../facade/` | ✏️ 修改 | 新增 `retryTask()` 和 `rollbackTask()` 方法（只做转换和委派） |
| `TaskOperationService.java` | `deploy/.../application/task/` | ✅ 已存在 | 已有 `retryTaskByTenant()` 和 `rollbackTaskByTenant()` 方法 |
| `TaskDomainService.java` | `deploy/.../domain/task/` | ✅ 已存在 | 已有 `prepareRetryByTenant()` 和 `prepareRollbackByTenant()` 方法 |
| `TaskRecoveryService.java` | `deploy/.../service/` | ✅ 已存在 | 内部被 TaskDomainService 调用 |
| `TenantConfigConverter.java` | `deploy/.../converter/` | ✅ 已存在 | 已支持 DTO 转换 |

**总结**：只需修改 1 个文件（`DeploymentTaskFacade.java`），新增 2 个方法。其他组件已实现。

---

## 💻 具体实现代码

### DeploymentTaskFacade.java

```java
package xyz.firestige.facade.deploy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import xyz.firestige.core.domain.task.TaskAggregate;
import xyz.firestige.core.domain.task.TaskId;
import xyz.firestige.core.domain.task.TaskRuntimeContext;
import xyz.firestige.core.service.TaskOperationService;
import xyz.firestige.core.service.TaskRecoveryService;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.dto.tenant.TenantConfig;
import xyz.firestige.converter.TenantConfigConverter;

import java.util.List;

/**
 * 部署任务门面（胶水层）
 * <p>
 * 职责：
 * 1. 外部 API 入口
 * 2. DTO 转换（TenantDeployConfig → TenantConfig）
 * 3. 参数标准化
 * 4. 委派给应用层服务（TaskOperationService）
 * <p>
 * 设计原则：
 * - 不做业务编排，只做转换和委派
 * - 不直接调用领域服务或基础设施层
 */
@Component
@RequiredArgsConstructor
public class DeploymentTaskFacade {
    
    private final TaskOperationService taskOperationService;
    private final TenantConfigConverter tenantConfigConverter;
    
    /**
     * 创建切换任务（批量）
     * <p>
     * ✅ 保持不变
     *
     * @param configs 租户配置列表
     */
    public void createSwitchTask(List<TenantDeployConfig> configs) {
        // 现有实现保持不变
        // ...
    }
    
    /**
     * 重试任务
     * <p>
     * 设计要点：
     * 1. 纯胶水层：只做 DTO 转换和委派
     * 2. 不生成 taskId（由应用层管理）
     * 3. 委派给 TaskOperationService 处理业务逻辑
     *
     * @param config 租户配置（包含目标版本等信息）
     * @param lastCompleteStageName 最后完成的 Stage 名称（由 Caller 提供）
     */
    public void retryTask(TenantDeployConfig config, String lastCompleteStageName) {
        // 1. DTO 转换
        TenantConfig tenantConfig = tenantConfigConverter.convert(config);
        
        // 2. 委派给应用层服务
        taskOperationService.retryTask(
            tenantConfig,
            lastCompleteStageName  // null 表示从头重试
        );
    }
    
    /**
     * 回滚任务
     * <p>
     * 设计要点：
     * 1. 纯胶水层：只做 DTO 转换和委派
     * 2. 委派给 TaskOperationService 处理业务逻辑
     * 3. version 用于单调递增版本号校验
     *
     * @param config 旧版本配置（回滚目标）
     * @param lastCompleteStageName 最后完成的 Stage 名称（由 Caller 提供，可能不使用）
     * @param version 单调递增的操作版本号（用于版本校验）
     */
    public void rollbackTask(
        TenantDeployConfig config,
        String lastCompleteStageName,
        String version
    ) {
        // 1. DTO 转换
        TenantConfig tenantConfig = tenantConfigConverter.convert(config);
        
        // 2. 委派给应用层服务
        taskOperationService.rollbackTask(
            tenantConfig,
            lastCompleteStageName,
            version
        );  version
        );
    }
}
```

---

## 🔍 关键实现细节

### 1. ID 来源说明

```java
// ✅ taskId 由 Facade 层生成（仅 retry 需要）
TaskId newTaskId = TaskId.generate();  // retry 时生成新 Task

// ✅ tenantId 和 planId 从 config 中获取
String tenantId = config.getTenantId();      // 从外部传入
String planId = config.getPlanId();          // 从外部传入（如果有）
```

**设计原则**：
- **retry**: Facade 生成新 taskId，然后委派
- **rollback**: 不需要新 taskId，使用现有租户查找
- **tenantId/planId**: 始终从 `TenantDeployConfig` 获取

### 2. Facade 职责边界

```java
// ✅ Facade 只做转换和委派
public void retryTask(...) {
    TenantConfig config = converter.convert(externalConfig);  // 转换
    taskOperationService.retryTaskByTenant(...);              // 委派
}

// ❌ Facade 不应该做的事
// - 不直接调用 TaskRecoveryService
// - 不创建 TaskAggregate
// - 不创建 TaskRuntimeContext
// - 不调用 TaskExecutor
```

### 3. TaskOperationService 方法签名

```java
// Retry 方法
public TaskOperationResult retryTask(
    TenantConfig config,                  // 用于重试的配置（和上次执行一样）
    String lastCompletedStageName         // 最近完成的 stage 名称（null 表示从头重试）
);

// Rollback 方法
public TaskOperationResult rollbackTask(
    TenantConfig oldConfig,               // 旧版本配置
    String lastCompletedStageName,        // 最近完成的 stage 名称（null 表示全部回滚）
    String version                        // 操作版本号
);
```

**关键点**：
- retry：config + lastCompletedStageName（null 时从头重试）
- rollback：oldConfig + lastCompletedStageName + version（null 时全部回滚）
- lastCompletedStageName 为 null 或不存在时，RecoveryService 按从头到尾全部执行的逻辑构建
- 回滚不是逆向操作，而是用旧版本配置重新执行 stages
- 两个方法都是异步执行，立即返回

### 4. 异步执行和事件通知

```java
// TaskOperationService 内部
CompletableFuture.runAsync(() -> {
    executor.execute();  // 异步执行
});

// Facade 方法立即返回
return;  // 不等待执行完成
```

**特点**：
- Facade 方法立即返回（不等待执行完成）
- Caller 通过监听领域事件获取执行结果：
  - `TaskStartedEvent`：任务开始
  - `TaskStageCompletedEvent`：Stage 完成
  - `TaskCompletedEvent`：任务完成
  - `TaskFailedEvent`：任务失败

---

## 🧪 测试验证

### Retry 测试场景

```java
@Test
void testRetry() {
    // 1. 准备数据
    TenantDeployConfig config = TenantDeployConfig.builder()
        .deployUnitId(100L)
        .deployUnitVersion(200L)  // 目标版本
        .tenantId("tenant-001")   // ✅ 从外部传入
        .planId("plan-001")       // ✅ 从外部传入（可选）
        .serviceNames(List.of("service-a", "service-b"))
        .build();
    
    String lastCompleteStageName = "PreCheckStage";  // 假设执行到这里
    
    // 2. 调用 Facade
    deploymentTaskFacade.retryTask(config, lastCompleteStageName);
    
    // 3. 验证（通过监听事件）
    // - TaskStartedEvent 发布
    // - 从 "DataMigrationStage" 开始执行（PreCheckStage 的下一个）
    // - TaskStageCompletedEvent 依次发布
    // - TaskCompletedEvent 发布
}
```

### Rollback 测试场景

```java
@Test
void testRollback() {
    // 1. 准备数据
    TenantDeployConfig oldConfig = TenantDeployConfig.builder()
        .deployUnitId(100L)
        .deployUnitVersion(100L)  // 旧版本（回滚目标）
        .tenantId("tenant-001")   // ✅ 从外部传入
        .planId("plan-001")       // ✅ 从外部传入（可选）
        .serviceNames(List.of("service-a", "service-b"))
        .build();
    
    String lastCompleteStageName = "DataMigrationStage";
    String version = "v100";  // 操作版本号
    
    // 2. 调用 Facade
    deploymentTaskFacade.rollbackTask(oldConfig, lastCompleteStageName, version);
    
    // 3. 验证（通过监听事件）
    // - TaskStartedEvent 发布
    // - 逆向执行 [PreCheckStage, DataMigrationStage, TrafficSwitchStage]
    // - TaskStageCompletedEvent 依次发布
    // - TaskCompletedEvent 发布
}
```

---

## 📊 对比总结

### Retry vs Rollback

| 维度 | Retry | Rollback |
|------|-------|----------|
| **目标** | 继续执行未完成的 Stage | 用旧版本配置刷回已变更内容 |
| **配置** | 使用当前配置（目标版本） | 使用旧版本配置 |
| **执行范围** | 从 lastCompletedIndex+1 继续 | 重新执行所有 stages |
| **执行方式** | 正向执行（继续） | 正向执行（使用旧配置） |
| **版本参数** | 无需 version | 需要 version（版本校验） |
| **使用场景** | 失败后重试 | 升级失败回滚 |

### 废弃的方法对比

| 旧方法 | 新方法 | 变化 |
|-------|--------|------|
| ❌ `pauseTaskByTenant()` | — | 废弃（Caller 自行管理状态） |
| ❌ `resumeTaskByTenant()` | — | 废弃（Caller 自行管理状态） |
| ❌ `cancelTaskByTenant()` | — | 废弃（Caller 自行管理状态） |
| ❌ `queryTaskStatus()` | — | 废弃（事件驱动，无需查询） |
| ✅ `createSwitchTask()` | ✅ `createSwitchTask()` | 保持不变 |
| — | ✅ `retryTask()` | 新增 |
| — | ✅ `rollbackTask()` | 新增 |

---

## ✅ 设计完成检查清单

- [x] ✅ 明确每次创建新 Task（不复用 taskId）
- [x] ✅ 简化 Facade API（只保留 create/retry/rollback）
- [x] ✅ 复用 TaskRecoveryService（无需修改）
- [x] ✅ 复用 TaskOperationService（无需修改）
- [x] ✅ 定义 retry 方法签名：`retryTask(TenantDeployConfig, String lastCompleteStageName)`
- [x] ✅ 定义 rollback 方法签名：`rollbackTask(TenantDeployConfig, String lastCompleteStageName, String version)`
- [x] ✅ 明确异步执行（事件驱动）
- [x] ✅ 明确不需要 Plan 持久化
- [x] ✅ 提供完整代码实现
- [x] ✅ 提供测试验证方案

---

## 📋 实施步骤

1. **修改 DeploymentTaskFacade.java**
   - 新增 `retryTask()` 方法
   - 新增 `rollbackTask()` 方法

2. **编写单元测试**
   - `DeploymentTaskFacadeTest.testRetry()`
   - `DeploymentTaskFacadeTest.testRollback()`

3. **集成测试**
   - 验证事件发布
   - 验证执行范围
   - 验证版本号校验（rollback）

4. **文档更新**
   - 更新 API 文档
   - 更新调用示例

---

**设计文档完成** ✅


