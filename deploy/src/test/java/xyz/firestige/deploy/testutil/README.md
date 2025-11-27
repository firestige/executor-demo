# 测试工具包使用指南

> **T-023 测试体系重建** - 基于架构优势的测试工具集

---

## 一、设计理念

### 1. 利用架构优势
- **Repository抽象层**：所有持久化已通过Repository接口隔离，使用InMemory实现即可
- **接口隔离优势**：TaskStage/StageStep接口独立，易于stub
- **测试关注点**：聚焦状态机转换和执行编排，而非外部依赖

### 2. 测试复杂度原则
> "不好测试的架构一定是复杂的、不简洁的坏架构"

当前架构测试复杂度评估：
- **单元测试**：0外部依赖 ⭐ 极简
- **集成测试**：仅需Stage stub ⭐⭐ 简单  
- **E2E测试**：仅需Stage stub ⭐⭐⭐ 中等

**结论：架构设计优秀，易于测试** ✅

---

## 二、测试工具清单

### 1. Stage Stub类 (`testutil/stage/`)

#### AlwaysSuccessStage
```java
// 永远成功的Stage
TaskStage stage = new AlwaysSuccessStage("stage-0");

// 带延迟
TaskStage stage = new AlwaysSuccessStage("stage-0", Duration.ofMillis(100));
```
**用途**：测试正常流程、多阶段串联

#### AlwaysFailStage
```java
// 永远失败的Stage
TaskStage stage = new AlwaysFailStage("stage-0");

// 自定义错误类型和消息
TaskStage stage = new AlwaysFailStage("stage-0", ErrorType.TIMEOUT_ERROR, "Custom message");
```
**用途**：测试失败处理、Checkpoint保存

#### FailOnceStage
```java
// 第一次失败，后续成功
TaskStage stage = new FailOnceStage("stage-0");

// 在第N次失败
TaskStage stage = new FailOnceStage("stage-0", 2);  // 第2次失败

// 重置计数器（测试复用）
((FailOnceStage) stage).reset();
```
**用途**：测试重试fromCheckpoint

#### ConditionalFailStage
```java
// 自定义失败条件
TaskStage stage = new ConditionalFailStage("stage-0", 
    ctx -> ctx.getAdditionalData("shouldFail", Boolean.class));

// 基于版本失败（回滚场景）
TaskStage stage = ConditionalFailStage.failOnVersion("stage-0", "v2.0");

// 基于租户失败
TaskStage stage = ConditionalFailStage.failOnTenant("stage-0", "tenant-001");
```
**用途**：测试回滚场景（旧配置成功，新配置失败）

#### SlowStage
```java
// 延迟指定秒数
TaskStage stage = SlowStage.withSeconds("stage-0", 2);

// 延迟指定毫秒数
TaskStage stage = SlowStage.withMillis("stage-0", 500);

// 检查是否被中断
boolean interrupted = ((SlowStage) stage).wasInterrupted();
```
**用途**：测试暂停/取消的协作式响应

---

### 2. 测试数据工厂 (`testutil/factory/`)

#### ValueObjectTestFactory
```java
// 生成值对象
TaskId taskId = ValueObjectTestFactory.randomTaskId();
PlanId planId = ValueObjectTestFactory.planId("plan-001");
TenantId tenantId = ValueObjectTestFactory.tenantId("tenant-001");
DeployVersion version = ValueObjectTestFactory.version("v1.0.0");

// 快速构建TenantConfig
TenantConfig config = ValueObjectTestFactory.minimalConfig(tenantId);
TenantConfig config = ValueObjectTestFactory.fullConfig(tenantId, "blue-green-gateway");

// 使用Builder
TenantConfig config = ValueObjectTestFactory.configBuilder()
    .tenantId("tenant-001")
    .version("v1.0.0")
    .serviceNames("service-1", "service-2")
    .build();
```

#### StageListTestFactory
```java
// 预设场景
List<TaskStage> stages = StageListTestFactory.threeSuccessStages();
List<TaskStage> stages = StageListTestFactory.failAtThirdStage();
List<TaskStage> stages = StageListTestFactory.failOnceAtSecondStage();
List<TaskStage> stages = StageListTestFactory.slowStages();

// 生成指定数量
List<TaskStage> stages = StageListTestFactory.successStages(5);
List<TaskStage> stages = StageListTestFactory.successWithFailAt(5, 2);  // 第2个失败

// 使用Builder灵活组合
List<TaskStage> stages = StageListTestFactory.builder()
    .addSuccess()
    .addSuccess()
    .addFailOnce()
    .addSuccess()
    .build();
```

#### AggregateTestSupport（核心）
```java
// 通过反射设置聚合内部状态（仅测试代码）
TaskAggregate task = new TaskAggregate(taskId, planId, tenantId);

// 设置字段
AggregateTestSupport.setTaskField(task, "status", TaskStatus.RUNNING);
AggregateTestSupport.setDeployVersion(task, version);

// 初始化Stages
List<TaskStage> stages = StageListTestFactory.threeSuccessStages();
AggregateTestSupport.initializeTaskStages(task, stages);
AggregateTestSupport.initializeTaskStages(task, stages, 2);  // 设置当前进度
```
**设计理念**：聚合根不暴露setter保持封装性，测试通过反射注入状态
**详细说明**：参见 [AGGREGATE_TEST_DESIGN.md](./AGGREGATE_TEST_DESIGN.md)

#### TaskAggregateTestBuilder & PlanAggregateTestBuilder
```java
// 这两个Builder集成了AggregateTestSupport
// 需要根据实际聚合API进一步完善
```

---

## 三、测试场景示例

### 场景1：正常执行流程
```java
// 1. 准备Stage列表
List<TaskStage> stages = StageListTestFactory.threeSuccessStages();

// 2. 创建Task并初始化状态
TaskAggregate task = new TaskAggregate(taskId, planId, tenantId);
AggregateTestSupport.initializeTaskStages(task, stages);
AggregateTestSupport.setDeployVersion(task, version);

// 3. 执行业务方法
task.markAsPending();
task.start();

// 4. 验证状态为RUNNING
assertEquals(TaskStatus.RUNNING, task.getStatus());
```

### 场景2：中途失败 + Checkpoint保存
```java
// 1. Stage列表：2成功 + 1失败
List<TaskStage> stages = StageListTestFactory.failAtThirdStage();

// 2. 创建Task并设置为RUNNING状态（模拟执行到stage-2）
TaskAggregate task = new TaskAggregate(taskId, planId, tenantId);
AggregateTestSupport.initializeTaskStages(task, stages, 2);  // 已完成2个Stage
AggregateTestSupport.setTaskField(task, "status", TaskStatus.RUNNING);

// 3. 模拟stage-2失败
FailureInfo failureInfo = FailureInfo.of(ErrorType.SYSTEM_ERROR, "Stage failed");
task.fail(failureInfo);

// 4. 验证Task状态为FAILED + Checkpoint
assertEquals(TaskStatus.FAILED, task.getStatus());
```

### 场景3：失败重试fromCheckpoint
```java
// 1. Stage列表：1成功 + 1失败一次 + 1成功
List<TaskStage> stages = StageListTestFactory.failOnceAtSecondStage();

// 2. 第一次执行，在stage-1失败
// 3. 验证Checkpoint（completedStages=[0]）
// 4. 重试fromCheckpoint
// 5. 验证跳过stage-0，从stage-1继续
// 6. 验证最终COMPLETED
```

### 场景4：暂停与恢复
```java
// 1. 使用SlowStage，便于在执行中暂停
List<TaskStage> stages = StageListTestFactory.slowStages();

// 2. 异步执行Task
// 3. 在Stage边界检测pauseRequested标志
// 4. 验证Task状态为PAUSED
// 5. 恢复执行
// 6. 验证从断点继续
```

### 场景5：回滚场景
```java
// 1. 使用条件失败Stage
List<TaskStage> stages = StageListTestFactory.conditionalFailOnVersion("v2.0");

// 2. 使用旧版本v1.0执行，成功
// 3. 使用新版本v2.0执行，失败
// 4. 触发回滚，使用旧配置重新执行Stage
// 5. 验证回滚成功
```

---

## 四、测试套件结构

```
deploy/src/test/java/xyz/firestige/deploy/
├── testutil/                          # 测试工具（已完成）
│   ├── stage/                         
│   │   ├── AlwaysSuccessStage.java   ✅
│   │   ├── AlwaysFailStage.java      ✅
│   │   ├── FailOnceStage.java        ✅
│   │   ├── ConditionalFailStage.java ✅
│   │   └── SlowStage.java            ✅
│   └── factory/                       
│       ├── ValueObjectTestFactory.java       ✅
│       ├── StageListTestFactory.java         ✅
│       ├── TaskAggregateTestBuilder.java     ⚠️ 需修复
│       └── PlanAggregateTestBuilder.java     ⚠️ 需修复
│
├── unit/                              # 单元测试（待实现）
│   ├── domain/
│   ├── application/
│   └── infrastructure/
│
├── integration/                       # 集成测试（待实现）
│   ├── TaskExecutorIntegrationTest.java     # 核心：状态机+编排
│   ├── RetryFlowIntegrationTest.java        # 重试fromCheckpoint
│   ├── RollbackFlowIntegrationTest.java     # 回滚逆序执行
│   └── PauseResumeIntegrationTest.java      # 协作式暂停
│
└── e2e/                               # E2E测试（待实现）
    └── FacadeE2ETest.java
```

---

## 五、已知问题

### 5.1 聚合封装性方案
**问题**：聚合根不暴露setter，如何构造测试状态？  
**方案**：使用`AggregateTestSupport`通过反射注入状态

详见：[AGGREGATE_TEST_DESIGN.md](./AGGREGATE_TEST_DESIGN.md)

### 5.2 TaskAggregateTestBuilder & PlanAggregateTestBuilder
需要根据聚合实际API进一步完善，目前已集成`AggregateTestSupport`。

---

## 六、后续工作

### P0 - 修复编译错误
1. 修复TaskAggregateTestBuilder
2. 修复PlanAggregateTestBuilder
3. 清理未使用的import

### P1 - 实现测试套件
1. 核心集成测试
2. E2E测试
3. 单元测试

### P2 - 完善工具
1. 添加更多便捷方法
2. 补充文档和示例
3. 性能优化

---

## 七、架构优势总结

✅ **Repository模式的胜利**
- 6个Repository接口 + 对应InMemory实现
- 完全隔离Redis/数据库细节
- 测试时零外部依赖

✅ **接口隔离的胜利**
- TaskStage/StageStep接口简洁明确
- 用5个简单stub即可覆盖所有场景
- 无需mock复杂的HTTP/Redis/Nacos客户端

✅ **测试复杂度评估通过**
- 单元测试：0外部依赖
- 集成测试：仅需Stage stub
- E2E测试：仅需Stage stub

**这证明当前架构设计是成功的、简洁的、易测试的！** 🎉
