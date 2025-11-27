# 测试工具快速开始

## 🚀 5分钟上手

### 1. 创建测试数据

```java
// 值对象
TenantId tenantId = ValueObjectTestFactory.tenantId("tenant-001");
TaskId taskId = ValueObjectTestFactory.randomTaskId();
PlanId planId = ValueObjectTestFactory.randomPlanId();

// TenantConfig
TenantConfig config = ValueObjectTestFactory.minimalConfig(tenantId);
```

### 2. 构建聚合根

```java
// 快捷方法
TaskAggregate task = TaskAggregateTestBuilder.pending();
TaskAggregate task = TaskAggregateTestBuilder.running();
TaskAggregate task = TaskAggregateTestBuilder.failed();

// 定制方法
TaskAggregate task = new TaskAggregateTestBuilder()
    .tenantId(tenantId)
    .totalStages(5)
    .buildRunning(2);  // 已完成2个Stage
```

### 3. 创建Stage列表

```java
// 3个成功Stage
List<TaskStage> stages = StageListTestFactory.threeSuccessStages();

// 自定义Stage列表
List<TaskStage> stages = new StageListBuilder()
    .add(new AlwaysSuccessStage("stage-1"))
    .add(new SlowStage("stage-2", Duration.ofSeconds(2)))
    .add(new FailOnceStage("stage-3"))
    .build();
```

### 4. 使用反射设置状态

```java
// 创建Task
TaskAggregate task = new TaskAggregate(taskId, planId, tenantId);

// 设置内部状态（不暴露setter）
AggregateTestSupport.setDeployVersion(task, version);
AggregateTestSupport.initializeTaskStages(task, stages, 2);  // 已完成2个Stage
```

## 📝 常见测试场景

### 场景1: 正常执行流程

```java
@Test
void shouldCompleteTaskSuccessfully() {
    // 1. 准备Stage
    List<TaskStage> stages = StageListTestFactory.threeSuccessStages();
    
    // 2. 创建Task并初始化
    TaskAggregate task = new TaskAggregate(taskId, planId, tenantId);
    AggregateTestSupport.initializeTaskStages(task, stages);
    
    // 3. 执行业务方法
    task.markAsPending();
    task.start();
    
    // 4. 验证
    assertEquals(TaskStatus.RUNNING, task.getStatus());
}
```

### 场景2: 中途失败 + Checkpoint

```java
@Test
void shouldSaveCheckpointWhenFailed() {
    // 1. Stage列表：2成功 + 1失败
    List<TaskStage> stages = StageListTestFactory.failAtThirdStage();
    
    // 2. 创建Task并模拟执行到stage-2
    TaskAggregate task = new TaskAggregate(taskId, planId, tenantId);
    AggregateTestSupport.initializeTaskStages(task, stages, 2);
    AggregateTestSupport.setTaskField(task, "status", TaskStatus.RUNNING);
    
    // 3. 模拟失败
    FailureInfo failure = FailureInfo.of(ErrorType.SYSTEM_ERROR, "Stage failed");
    task.fail(failure);
    
    // 4. 验证
    assertEquals(TaskStatus.FAILED, task.getStatus());
}
```

### 场景3: 从Checkpoint重试

```java
@Test
void shouldRetryFromCheckpoint() {
    // 1. 准备带Checkpoint的Task
    TaskAggregate task = new TaskAggregateTestBuilder()
        .buildWithCheckpoint(2);  // 已完成0,1,2三个Stage
    
    // 2. 模拟失败
    task.start();
    task.fail(FailureInfo.of(ErrorType.BUSINESS_ERROR, "Failed"));
    
    // 3. 重试
    task.retry();
    
    // 4. 验证
    assertEquals(TaskStatus.RUNNING, task.getStatus());
    assertEquals(1, task.getRetryCount());
}
```

### 场景4: 回滚

```java
@Test
void shouldRollbackAfterFailure() {
    // 1. 准备失败的Task（有快照）
    TaskAggregate task = new TaskAggregate(taskId, planId, tenantId);
    
    // TODO: 设置prevConfigSnapshot（需要实际API）
    
    // 2. 触发回滚
    task.startRollback("Manual rollback");
    
    // 3. 验证
    assertEquals(TaskStatus.ROLLING_BACK, task.getStatus());
}
```

## 🔗 文档索引

- **完整指南**: [testutil/README.md](./testutil/README.md)
- **设计文档**: [testutil/AGGREGATE_TEST_DESIGN.md](./testutil/AGGREGATE_TEST_DESIGN.md)
- **E2E测试**: [e2e/README.md](./e2e/README.md)
- **完成总结**: [T-023-COMPLETION-SUMMARY.md](./T-023-COMPLETION-SUMMARY.md)

## ⚡ 最佳实践

1. **优先使用快捷方法**: `TaskAggregateTestBuilder.pending()`
2. **按需定制**: 只有特殊需求才用Builder模式
3. **测试独立性**: 每个测试独立运行，不依赖执行顺序
4. **清晰命名**: 使用`@DisplayName`提供中文描述
5. **Given-When-Then**: 结构化测试代码

## 🐛 常见问题

**Q: 如何设置Task的私有字段？**
A: 使用`AggregateTestSupport.setTaskField(task, fieldName, value)`

**Q: Builder和快捷方法如何选择？**
A: 简单场景用快捷方法，复杂定制用Builder

**Q: 如何验证异步执行？**
A: E2E测试中使用`TimeUnit.sleep()`或轮询状态

**Q: 测试数据如何隔离？**
A: 使用唯一ID（tenant-xxx-001, tenant-xxx-002...）

## 📞 帮助

遇到问题？查看：
1. [testutil/README.md](./testutil/README.md) - 完整文档
2. [testutil/AGGREGATE_TEST_DESIGN.md](./testutil/AGGREGATE_TEST_DESIGN.md) - 设计理念
3. [e2e/T-023-E2E-TODO.md](./e2e/T-023-E2E-TODO.md) - 已知问题

---
**Created**: 2025-11-28  
**For**: T-023 测试体系重建
