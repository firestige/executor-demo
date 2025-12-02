# T-035 无状态执行器重构

## 📋 任务概述

**任务 ID**: T-035  
**任务名称**: 无状态执行器重构：移除 Checkpoint/Projection，实现 TaskRecoveryService  
**优先级**: P1  
**负责人**: Copilot  
**开始日期**: 2025-12-02  
**预计完成**: 2025-12-04  

## 🎯 目标

将项目简化为"无状态执行器"架构：
- 移除所有内部持久化机制（Checkpoint、Projection）
- 依赖 caller 进行状态持久化和管理
- 实现基于 caller 提供状态的重启恢复机制

## 📊 工作清单

### 阶段 1：清理 Checkpoint 机制（0.5天）

- [ ] **步骤 1：删除 TaskCheckpoint 及相关代码**
  - 删除文件：
    - `TaskCheckpoint.java`
    - `CheckpointService.java`
    - `CheckpointRepository.java`
    - `InMemoryCheckpointRepository.java`
    - `RedisCheckpointRepository.java`
  - 修改 `TaskAggregate.java`：
    - 删除 `checkpoint` 字段
    - 删除 `recordCheckpoint()` 方法
    - 删除 `getCheckpoint()` 方法

- [ ] **步骤 2：清理 ExecutionRange 对 Checkpoint 的依赖**
  - 修改 `ExecutionRange.java`：
    - 删除 `forRollback(TaskCheckpoint)` 方法
    - 删除 `forRetry(TaskCheckpoint)` 方法
    - 保留基于 `int lastCompletedIndex` 的基础工厂方法

- [ ] **步骤 3：清理 StageProgress 对 Checkpoint 的依赖**
  - 修改 `StageProgress.java`：
    - 删除 `of(TaskCheckpoint)` 工厂方法
    - 确保 `of(int currentStageIndex, List<TaskStage> stages)` 可用

- [ ] **步骤 4：移除 TaskExecutor 中的 checkpoint 保存逻辑**
  - 修改 `TaskExecutor.java`：
    - 删除 `handleStageSuccess()` 中的 `checkpointService.saveCheckpoint()` 调用
  - 修改 `TaskExecutorDependencies.java`：
    - 移除 `CheckpointService` 依赖

### 阶段 2：清理 Projection 和 Redis（0.3天）

- [ ] **步骤 5：删除 Projection 相关代码**
  - 删除文件：
    - `TaskStateProjection.java`
    - `PlanStateProjection.java`
    - `ProjectionStore.java` 接口
    - `InMemoryProjectionStore.java`
    - `RedisProjectionStore.java`
    - `ProjectionUpdater.java` 相关类
    - `TaskQueryService.java`
    - `PlanQueryService.java`

- [ ] **步骤 6：删除 Redis 持久化依赖**
  - 删除文件：
    - `RedisTaskRepository.java`
    - `RedisPlanRepository.java`
    - Redis 相关配置类
  - 修改 `pom.xml`：
    - 移除 `spring-data-redis` 依赖（如果完全不需要）

### 阶段 3：实现无状态恢复（1天）

- [ ] **步骤 7：实现 TaskRecoveryService（Application Layer）**
  - 位置：`xyz.firestige.deploy.application.task.TaskRecoveryService`
  - 实现 `recoverFromRestart()` 方法：
    - 接受 `RestartRecoveryRequest`（包含 config、lastCompletedStageName、taskId、mode）
    - 编排逻辑：
      1. 调用 `StageFactory.calculateStartIndex()` 计算索引
      2. 调用 `TaskDomainService.createTask()` 创建 Task（CREATED 状态）
      3. 设置 `StageProgress` 和 `ExecutionRange`
      4. 调用 `TaskExecutor.execute()` 执行
  - 创建 `RestartRecoveryRequest.java` DTO

- [ ] **步骤 8：实现 StageFactory.calculateStartIndex() 方法**
  - 修改 `StageFactory.java`：
    - 添加根据 `lastCompletedStageName` 计算 `startIndex` 的方法
    - 返回 `lastCompletedIndex + 1` 作为下次执行的起点
    - 验证 StageList 生成的幂等性（相同输入 → 相同顺序）

- [ ] **步骤 9：更新 TaskAggregate 构造和初始化逻辑**
  - 修改 `TaskAggregate.java`：
    - 确保正确初始化 `StageProgress` 和 `ExecutionRange`
    - 支持从外部设置 startIndex（恢复场景）
    - 添加 `setStageProgress()` 和 `setExecutionRange()` 方法（如果不存在）
    - 移除对 `checkpoint` 字段的所有依赖

### 阶段 4：测试和验证（0.5天）

- [ ] **步骤 10：更新测试用例**
  - 修复依赖 Checkpoint 的测试：
    - `testRollbackCheckpointBehavior`
    - `testCheckpointSavedForNonLastStage`
    - `testCheckpointNotSavedForLastStage`
  - 添加新测试：
    - `testTaskRecoveryService_normalRecovery`
    - `testTaskRecoveryService_rollbackRecovery`
    - `testStageFactory_idempotence`（验证幂等性）

- [ ] **步骤 11：清理配置文件和文档**
  - 删除配置类：
    - `CheckpointProperties.java`
    - `CheckpointAutoConfiguration.java`
    - `ExecutorCheckpointProperties.java`
  - 更新配置文件：
    - `application.yml`：移除 checkpoint 相关配置
  - 更新架构文档：
    - `correct_tree_view.puml`
    - `development-view.puml`
    - `logical-view.puml`
  - 更新设计文档：
    - `README.md`
    - `docs/architecture-overview.md`
    - 移除 Checkpoint 和 Projection 相关描述

- [ ] **步骤 12：验证编译和运行**
  - 编译验证：`mvn clean compile`
  - 测试验证：`mvn test`
  - 代码检查：使用 grep 搜索遗漏的引用
    - `Checkpoint`
    - `CheckpointService`
    - `TaskStateProjection`
    - `PlanStateProjection`
    - `ProjectionStore`

## 🎓 设计原则

### DDD 分层架构

```
Facade Layer
    ↓
Application Layer  ← TaskRecoveryService 在这里
    ↓
Domain Layer
    ↓
Infrastructure Layer
```

### TaskRecoveryService 职责定位

- **层次**: Application Layer
- **职责**: 编排多个组件完成"重启恢复"用例
- **依赖**: 
  - `TaskDomainService`（领域服务）
  - `StageFactory`（领域服务）
  - `TaskWorkerFactory`（基础设施工厂）
- **对比**: 与 `TaskOperationService` 是同层次的服务

### 核心简化原则

| 组件 | 职责 | 是否保留 | 说明 |
|------|------|---------|------|
| **ExecutionRange** | 定义执行范围 [start, end) | ✅ 保留 | 静态的，决定本次执行哪些 Stage |
| **StageProgress** | 追踪当前进度 currentIndex | ✅ 保留 | 动态的，运行时状态 |
| **TaskCheckpoint** | 持久化检查点 | ❌ 移除 | 与 StageProgress 重复，caller 已负责持久化 |
| **TaskStateProjection** | 查询投影 | ❌ 移除 | caller 负责状态管理 |
| **CheckpointService** | 检查点服务 | ❌ 移除 | 无状态架构不需要 |

## 📝 参考文档

- [分析文档](task-checkpoint-restart-recovery-analysis.md)：详细问题分析和方案对比
- [回滚设计](rollback-task-level-design.md)：现有回滚机制设计

## 📅 时间线

| 日期 | 阶段 | 预期产出 |
|------|------|---------|
| 2025-12-02 | 阶段 1-2 | Checkpoint 和 Projection 代码清理完成 |
| 2025-12-03 | 阶段 3 | TaskRecoveryService 实现完成 |
| 2025-12-04 | 阶段 4 | 测试和文档更新完成，验证通过 |

## ✅ 完成标准

- [ ] 所有 Checkpoint 和 Projection 相关代码已删除
- [ ] TaskRecoveryService 实现完成并通过测试
- [ ] StageFactory.calculateStartIndex() 实现完成并验证幂等性
- [ ] 所有测试用例通过（包括新增的恢复测试）
- [ ] 代码编译通过，无遗漏的引用
- [ ] 架构文档和设计文档已更新
- [ ] 无 Checkpoint/Projection 的残留配置

## 🔍 验证清单

```bash
# 1. 编译检查
mvn clean compile

# 2. 测试检查
mvn test

# 3. 代码引用检查
grep -r "TaskCheckpoint" --include="*.java" deploy/src/
grep -r "CheckpointService" --include="*.java" deploy/src/
grep -r "TaskStateProjection" --include="*.java" deploy/src/
grep -r "PlanStateProjection" --include="*.java" deploy/src/
grep -r "ProjectionStore" --include="*.java" deploy/src/

# 4. 配置检查
grep -r "checkpoint" deploy/src/main/resources/
```
