# KeyValueWriteStep 写入 Metadata 实施方案

## 需求

在 `KeyValueWriteStep` 执行时，除了写入业务数据，还需要额外写入一个 `metadata` field，包含 `planVersion` 信息。

## 最终效果

执行后 Redis 中的数据结构：
```
Key: icc_ai_ops_srv:tenant_config:tenant-001
Fields:
  - icc-bg-gateway: {"tenantId":"tenant-001","sourceUnitName":"blue","targetUnitName":"green",...}
  - metadata: {"version": 123}
```

## 设计方案

### 核心思路
1. **不破坏领域模型**：`planVersion` 不属于 Task 领域，通过运行时上下文传递
2. **利用 TaskRuntimeContext 的 additionalData**：专门用于存储运行时额外数据
3. **在创建 Task 时保存**：在 `DeploymentPlanCreator` 中从 `TenantConfig` 提取并保存
4. **在 Step 执行时读取**：`KeyValueWriteStep` 从上下文获取并写入 Redis

### 数据流

```
TenantConfig (planVersion)
    ↓
DeploymentPlanCreator.createAndLinkTask()
    ↓
TaskRuntimeContext.addVariable("planVersion", config.getPlanVersion())
    ↓
TaskRuntimeRepository.saveContext()
    ↓
TaskExecutionOrchestrator.createExecutionContext()
    ↓
TaskWorker.execute()
    ↓
KeyValueWriteStep.execute(TaskRuntimeContext ctx)
    ↓
ctx.getAdditionalData("planVersion", Long.class)
    ↓
Redis HSET key "metadata" '{"version":123}'
```

## 实施细节

### 1. 修改 DeploymentPlanCreator

在创建 Task 后立即创建 `TaskRuntimeContext` 并保存 `planVersion`：

```java
private TaskAggregate createAndLinkTask(TenantConfig config) {
    PlanId planId = config.getPlanId();
    TaskAggregate task = taskDomainService.createTask(planId, config);

    // 创建并保存 TaskRuntimeContext（包含 planVersion）
    TaskRuntimeContext runtimeContext = new TaskRuntimeContext(
        planId,
        task.getTaskId(),
        task.getTenantId()
    );
    // 将 planVersion 存储到运行时上下文中
    runtimeContext.addVariable("planVersion", config.getPlanVersion());
    taskRuntimeRepository.saveContext(task.getTaskId(), runtimeContext);
    
    // ... 后续逻辑
}
```

**注意**：这里提前创建了 `TaskRuntimeContext`，而原来是在 `TaskExecutionOrchestrator` 中延迟创建。这样做的好处是可以在创建时就保存 `planVersion`。

### 2. 修改 KeyValueWriteStep

在 `execute()` 方法中增加第二次 HSET 写入：

```java
@Override
public void execute(TaskRuntimeContext ctx) throws Exception {
    // 1-4. 现有逻辑：获取配置、构建 key、获取业务数据 JSON
    String hashField = getConfigValue("hashField", null);
    TenantId tenantId = serviceConfig.getTenantId();
    String hashKey = keyPrefix + tenantId.getValue();
    String jsonValue = getRedisValueJson();
    
    // 5. 第一次写入：业务数据
    redisTemplate.opsForHash().put(hashKey, hashField, jsonValue);
    log.info("[KeyValueWriteStep] Redis Hash written: key={}, field={}, valueLength={}",
            hashKey, hashField, jsonValue.length());
    
    // 6. 第二次写入：metadata（包含 planVersion）
    Long planVersion = ctx.getAdditionalData("planVersion", Long.class);
    if (planVersion != null) {
        String metadataJson = objectMapper.writeValueAsString(
            Map.of("version", planVersion)
        );
        redisTemplate.opsForHash().put(hashKey, "metadata", metadataJson);
        log.info("[KeyValueWriteStep] Redis Hash metadata written: key={}, field=metadata, version={}",
                hashKey, planVersion);
    } else {
        log.warn("[KeyValueWriteStep] planVersion not found in context, skipping metadata write");
    }
}
```

### 3. 修改依赖注入

在 `DeploymentPlanCreator` 的构造函数中添加 `TaskRuntimeRepository` 依赖：

```java
public DeploymentPlanCreator(
        PlanDomainService planDomainService,
        TaskDomainService taskDomainService,
        StageFactory stageFactory,
        BusinessValidator businessValidator,
        ExecutorProperties executorProperties,
        TaskRuntimeRepository taskRuntimeRepository) {
    this.taskRuntimeRepository = taskRuntimeRepository;
    // ...
}
```

在 `ExecutorConfiguration` 中更新 Bean 定义：

```java
@Bean
public DeploymentPlanCreator deploymentPlanCreator(
        PlanDomainService planDomainService,
        TaskDomainService taskDomainService,
        StageFactory stageFactory,
        BusinessValidator businessValidator,
        ExecutorProperties executorProperties,
        TaskRuntimeRepository taskRuntimeRepository) {
    return new DeploymentPlanCreator(
            planDomainService,
            taskDomainService,
            stageFactory,
            businessValidator,
            executorProperties,
            taskRuntimeRepository
    );
}
```

## 修改的文件列表

1. **DeploymentPlanCreator.java**
   - 添加 `TaskRuntimeRepository` 依赖
   - 在 `createAndLinkTask()` 中创建并保存 `TaskRuntimeContext`
   - 添加 `planVersion` 到运行时上下文

2. **KeyValueWriteStep.java**
   - 在 `execute()` 中增加第二次 HSET 写入 metadata

3. **ExecutorConfiguration.java**
   - 在 `deploymentPlanCreator` Bean 定义中添加 `TaskRuntimeRepository` 参数

## 设计优势

1. **不破坏 DDD 边界**：
   - `planVersion` 不属于 Task 领域模型
   - 通过运行时上下文传递，保持领域模型纯粹

2. **利用现有机制**：
   - `TaskRuntimeContext.additionalData` 就是为此设计
   - 无需修改领域模型结构

3. **通用能力**：
   - 所有使用 `KeyValueWriteStep` 的服务都会自动写入 metadata
   - 无需在 YAML 配置中声明

4. **灵活扩展**：
   - 未来可以添加更多运行时数据到 context
   - metadata 的内容可以扩展（如添加 timestamp、operator 等）

5. **职责清晰**：
   - `DeploymentPlanCreator`：创建时保存运行时数据
   - `KeyValueWriteStep`：执行时读取并写入 Redis
   - `TaskRuntimeContext`：运行时数据容器

## 注意事项

1. **TaskRuntimeContext 的创建时机**：
   - 原来：在 `TaskExecutionOrchestrator` 中延迟创建
   - 现在：在 `DeploymentPlanCreator` 中提前创建
   - 影响：`TaskExecutionOrchestrator` 会优先使用已存在的 context

2. **planVersion 为 null 的处理**：
   - 如果 `planVersion` 为 null，会记录 WARN 日志
   - 不会抛出异常，metadata field 不会写入

3. **幂等性**：
   - HSET 操作是幂等的，重复执行不会有问题

4. **性能考虑**：
   - 两次 HSET 操作，可以考虑使用 Pipeline 批量写入（未来优化）

## 测试建议

1. **单元测试**：
   - 验证 `planVersion` 正确保存到 `TaskRuntimeContext`
   - 验证 `KeyValueWriteStep` 正确读取并写入 metadata

2. **集成测试**：
   - 验证完整流程：TenantConfig → Context → Redis
   - 验证 Redis 中两个 field 都正确写入

3. **边界测试**：
   - `planVersion` 为 null 的情况
   - 运行时上下文中没有 `planVersion` 的情况

## 后续扩展

1. **metadata 内容扩展**：
   ```java
   Map<String, Object> metadata = new HashMap<>();
   metadata.put("version", planVersion);
   metadata.put("timestamp", System.currentTimeMillis());
   metadata.put("operator", "system");
   ```

2. **使用 Pipeline 优化性能**：
   ```java
   redisTemplate.executePipelined(new SessionCallback<Object>() {
       @Override
       public Object execute(RedisOperations operations) {
           operations.opsForHash().put(hashKey, hashField, jsonValue);
           operations.opsForHash().put(hashKey, "metadata", metadataJson);
           return null;
       }
   });
   ```

3. **metadata 版本管理**：
   - 如果 metadata 结构需要演进，可以添加 `metadataVersion` 字段

## 总结

本方案通过利用 `TaskRuntimeContext` 的 `additionalData` 机制，优雅地解决了跨层传递 `planVersion` 的问题，既不破坏 DDD 的领域边界，又实现了通用的 metadata 写入能力。

