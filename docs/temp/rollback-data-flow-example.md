# Rollback 数据流示例：DataPreparer 如何无感知地复用

**核心理念**：DataPreparer 和 Step 完全不知道自己在执行回滚，它们只是从 RuntimeContext 读取数据并执行。

---

## 场景：蓝绿网关配置切换失败后回滚

### 正向执行流程（失败）

```
配置数据：version=20, deployUnit=blue, tenant=abc

┌─────────────────────────────────────────────────────────┐
│ Stage: BlueGreenGatewayStage                           │
├─────────────────────────────────────────────────────────┤
│                                                         │
│ Step 1: ConfigWriteStep（写入 Redis）                  │
│   ├─ DataPreparer.prepare(ctx)                         │
│   │   ├─ 从 ctx 读取: deployUnitVersion = 20           │
│   │   ├─ 从 ctx 读取: tenantId = abc                   │
│   │   └─ 装配: key="tenant:abc", value="20"            │
│   └─ Step.execute(ctx)                                  │
│       └─ Redis HSET tenant:abc version 20  ✅          │
│                                                         │
│ Step 2: HttpRequestStep（通知网关）                    │
│   ├─ DataPreparer.prepare(ctx)                         │
│   │   ├─ 从 ctx 读取: deployUnitVersion = 20           │
│   │   └─ 装配: url="/gateway/config", body={v:20}      │
│   └─ Step.execute(ctx)                                  │
│       └─ HTTP POST /gateway/config {version:20}  ✅    │
│                                                         │
│ Step 3: PollingStep（健康检查）                        │
│   ├─ DataPreparer.prepare(ctx)                         │
│   │   ├─ 从 ctx 读取: deployUnitVersion = 20           │
│   │   ├─ 从 ctx 读取: healthCheckEndpoints = [...]     │
│   │   └─ 装配: expectVersion = 20                      │
│   └─ Step.execute(ctx)                                  │
│       ├─ Loop 1: GET /health → {version:19}  ❌        │
│       ├─ Loop 2: GET /health → {version:19}  ❌        │
│       └─ ... 超时失败 ❌                                 │
│                                                         │
└─────────────────────────────────────────────────────────┘

结果：TaskStatus = FAILED
```

---

### 回滚执行流程（成功）

```
配置数据：version=19, deployUnit=green, tenant=abc
         ↑ 从 prevConfigSnapshot 提取

┌─────────────────────────────────────────────────────────┐
│ Stage: BlueGreenGatewayStage  ← 完全相同的 Stage！     │
├─────────────────────────────────────────────────────────┤
│                                                         │
│ rollback() 方法：                                       │
│   ├─ 获取 prevConfigSnapshot (v19, green)              │
│   ├─ 构造 rollbackCtx（装填旧配置数据）                │
│   │   rollbackCtx.addVariable("deployUnitVersion", 19) │
│   │   rollbackCtx.addVariable("deployUnitId", 456)     │
│   │   rollbackCtx.addVariable("deployUnitName", "green")│
│   │   rollbackCtx.addVariable("healthCheckEndpoints", [..])│
│   └─ 执行相同的 Steps（传入 rollbackCtx）              │
│                                                         │
│ Step 1: ConfigWriteStep（写入 Redis）← 完全相同的 Step！│
│   ├─ DataPreparer.prepare(rollbackCtx)  ← 参数改了     │
│   │   ├─ 从 ctx 读取: deployUnitVersion = 19  ← 自动读到旧值│
│   │   ├─ 从 ctx 读取: tenantId = abc                   │
│   │   └─ 装配: key="tenant:abc", value="19"            │
│   └─ Step.execute(rollbackCtx)  ← 逻辑完全一样         │
│       └─ Redis HSET tenant:abc version 19  ✅          │
│                                                         │
│ Step 2: HttpRequestStep（通知网关）← 完全相同的 Step！  │
│   ├─ DataPreparer.prepare(rollbackCtx)                 │
│   │   ├─ 从 ctx 读取: deployUnitVersion = 19  ← 自动读到旧值│
│   │   └─ 装配: url="/gateway/config", body={v:19}      │
│   └─ Step.execute(rollbackCtx)                          │
│       └─ HTTP POST /gateway/config {version:19}  ✅    │
│                                                         │
│ Step 3: PollingStep（健康检查）← 完全相同的 Step！      │
│   ├─ DataPreparer.prepare(rollbackCtx)                 │
│   │   ├─ 从 ctx 读取: deployUnitVersion = 19  ← 自动读到旧值│
│   │   ├─ 从 ctx 读取: healthCheckEndpoints = [...]     │
│   │   └─ 装配: expectVersion = 19  ← 期望旧版本        │
│   └─ Step.execute(rollbackCtx)                          │
│       ├─ Loop 1: GET /health → {version:19}  ✅        │
│       └─ 版本匹配，检查通过！ ✅                         │
│                                                         │
└─────────────────────────────────────────────────────────┘

结果：TaskStatus = ROLLED_BACK
```

---

## 代码对比：DataPreparer 完全无感知

### 示例：ConfigWriteDataPreparer

```java
/**
 * 配置写入数据准备器
 * ✅ 完全不知道是正向执行还是回滚
 * ✅ 只负责从 RuntimeContext 读取数据并装配
 */
public class ConfigWriteDataPreparer implements DataPreparer {
    
    @Override
    public void prepare(TaskRuntimeContext ctx) {
        // ✅ 无论正向还是回滚，逻辑完全一样
        // 区别只在于 ctx 中装填的数据不同
        
        TenantId tenantId = ctx.getTenantId();
        Long deployUnitVersion = (Long) ctx.getAdditionalData("deployUnitVersion");
        Long deployUnitId = (Long) ctx.getAdditionalData("deployUnitId");
        String deployUnitName = (String) ctx.getAdditionalData("deployUnitName");
        
        // 构造 Redis Key
        String redisKey = String.format("tenant:%s", tenantId.getValue());
        
        // 装配到 ctx，供 ConfigWriteStep 使用
        ctx.addVariable("key", redisKey);
        ctx.addVariable("field", "version");
        ctx.addVariable("value", String.valueOf(deployUnitVersion));
        
        log.debug("准备配置写入数据: tenant={}, version={}", 
            tenantId, deployUnitVersion);
        // ↑ 日志打印的版本号：正向=20，回滚=19，自动适配！
    }
}
```

**关键点**：
- ✅ `deployUnitVersion` 从 `ctx.getAdditionalData()` 读取
- ✅ 正向执行时，ctx 装填的是 `version=20`（新配置）
- ✅ 回滚执行时，ctx 装填的是 `version=19`（旧配置）
- ✅ DataPreparer 代码完全不需要改动！

---

### 示例：HealthCheckDataPreparer

```java
/**
 * 健康检查数据准备器
 * ✅ 完全不知道是正向执行还是回滚
 */
public class HealthCheckDataPreparer implements DataPreparer {
    
    @Override
    public void prepare(TaskRuntimeContext ctx) {
        Long deployUnitVersion = (Long) ctx.getAdditionalData("deployUnitVersion");
        List<String> endpoints = (List<String>) ctx.getAdditionalData("healthCheckEndpoints");
        
        // 装配健康检查配置
        ctx.addVariable("expectVersion", deployUnitVersion);  // ← 关键
        ctx.addVariable("pollingEndpoints", endpoints);
        ctx.addVariable("maxAttempts", 10);  // 可配置
        ctx.addVariable("intervalSeconds", 3);
        
        log.debug("准备健康检查: expectVersion={}, endpoints={}", 
            deployUnitVersion, endpoints.size());
        // ↑ 正向：expectVersion=20，回滚：expectVersion=19，自动适配！
    }
}
```

**关键点**：
- ✅ `expectVersion` = `deployUnitVersion`
- ✅ 正向执行时：`expectVersion=20`，检查新版本是否生效
- ✅ 回滚执行时：`expectVersion=19`，检查旧版本是否恢复
- ✅ PollingStep 只负责轮询，不关心版本新旧

---

## Stage 层面：唯一的改动

### ConfigurableServiceStage.rollback()

```java
@Override
public void rollback(TaskRuntimeContext ctx) {
    log.info("Stage '{}' 开始回滚", name);
    
    // ========== 唯一需要写的代码 ==========
    
    // 1. 获取旧配置数据
    TenantDeployConfigSnapshot prevSnap = ctx.getTask().getPrevConfigSnapshot();
    if (prevSnap == null) {
        log.warn("无上一次配置，跳过回滚");
        return;
    }
    
    // 2. 构造新的 RuntimeContext，装填旧配置
    TaskRuntimeContext rollbackCtx = new TaskRuntimeContext(
        ctx.getPlanId(), ctx.getTaskId(), ctx.getTenantId()
    );
    
    // ✅ 核心：装填旧配置数据（替换新配置）
    rollbackCtx.addVariable("deployUnitVersion", prevSnap.getDeployUnitVersion());  // 19
    rollbackCtx.addVariable("deployUnitId", prevSnap.getDeployUnitId());            // 456
    rollbackCtx.addVariable("deployUnitName", prevSnap.getDeployUnitName());        // "green"
    rollbackCtx.addVariable("healthCheckEndpoints", prevSnap.getNetworkEndpoints());
    rollbackCtx.addVariable("planVersion", ctx.getAdditionalData("planVersion"));  // 保留
    
    // ========== 以下代码与 execute() 完全一样 ==========
    
    // 3. 执行相同的 Steps
    for (StepConfig stepConfig : stepConfigs) {
        if (stepConfig.getDataPreparer() != null) {
            stepConfig.getDataPreparer().prepare(rollbackCtx);  // ← 传入 rollbackCtx
        }
        stepConfig.getStep().execute(rollbackCtx);  // ← 传入 rollbackCtx
    }
    
    log.info("Stage '{}' 回滚成功", name);
}
```

**对比 execute() 方法**：

```java
@Override
public StageResult execute(TaskRuntimeContext ctx) {
    log.info("Stage '{}' 开始执行", name);
    
    // ✅ 直接使用传入的 ctx（装填的是新配置）
    
    // 执行 Steps（与 rollback() 完全一样）
    for (StepConfig stepConfig : stepConfigs) {
        if (stepConfig.getDataPreparer() != null) {
            stepConfig.getDataPreparer().prepare(ctx);  // ← 传入 ctx
        }
        stepConfig.getStep().execute(ctx);  // ← 传入 ctx
    }
    
    log.info("Stage '{}' 执行成功", name);
    return StageResult.success();
}
```

**唯一区别**：
- `execute()` 使用传入的 `ctx`（装填新配置）
- `rollback()` 构造新的 `rollbackCtx`（装填旧配置）
- 之后的流程**完全一样**！

---

## 数据流对比表

| 阶段 | 正向执行 | 回滚执行 | DataPreparer 是否需要改动？ |
|------|----------|----------|----------------------------|
| **RuntimeContext 构造** | 应用层装填新配置 | Stage 装填旧配置 | ❌ 不需要 |
| **DataPreparer.prepare()** | 读取 ctx 中的新配置 | 读取 ctx 中的旧配置 | ❌ 不需要 |
| **Step.execute()** | 执行操作（新配置） | 执行操作（旧配置） | ❌ 不需要 |
| **结果** | version=20 生效 | version=19 恢复 | ❌ 不需要 |

**总结**：
- ✅ **零 DataPreparer 改动**
- ✅ **零 Step 改动**
- ✅ **零 Stage 重新编排**
- ✅ **只需要在 Stage.rollback() 中构造新的 RuntimeContext**

---

## 工作量估算

### 需要修改的地方（仅 2 处）

1. **TaskDomainService.createTask()**（2 小时）
   - 添加 10 行代码：设置 `prevConfigSnapshot`

2. **ConfigurableServiceStage.rollback()**（6 小时）
   - 添加 30 行代码：构造 `rollbackCtx` + 执行 Steps
   - 含测试：2 小时

### 不需要修改的地方（0 工作量）

- ✅ 所有 DataPreparer（6 个类，0 改动）
- ✅ 所有 Step（6 个类，0 改动）
- ✅ StageFactory（0 改动）
- ✅ TaskExecutor.rollback()（已有框架，只需优化错误信息）

---

## 扩展性验证

### 场景：新增一个 Step（如 NacosConfigUpdateStep）

**正向流程**：
```java
// 1. 实现 Step
public class NacosConfigUpdateStep implements StageStep {
    @Override
    public void execute(TaskRuntimeContext ctx) {
        Long version = (Long) ctx.getAdditionalData("deployUnitVersion");
        // 调用 Nacos API 更新配置
        nacosClient.updateConfig(version);
    }
}

// 2. 实现 DataPreparer
public class NacosConfigDataPreparer implements DataPreparer {
    @Override
    public void prepare(TaskRuntimeContext ctx) {
        Long version = (Long) ctx.getAdditionalData("deployUnitVersion");
        String namespace = (String) ctx.getAdditionalData("nacosNamespace");
        // 装配数据
        ctx.addVariable("nacosVersion", version);
        ctx.addVariable("nacosNamespace", namespace);
    }
}

// 3. 在 StageFactory 中添加到 Stage
stage.addStep(
    StepConfig.builder()
        .dataPreparer(new NacosConfigDataPreparer())
        .step(new NacosConfigUpdateStep())
        .build()
);
```

**回滚流程**：
- ✅ **零额外代码**
- ✅ `rollbackCtx` 中的 `deployUnitVersion` 自动是旧版本
- ✅ NacosConfigUpdateStep 自动更新为旧版本
- ✅ 完全自动复用！

---

## 总结：为什么这个设计优雅？

### 1. 符合开闭原则（OCP）
- 新增 Step：无需修改回滚代码
- 修改 Step：回滚逻辑自动适配

### 2. 符合单一职责原则（SRP）
- DataPreparer：只负责数据装配
- Step：只负责执行操作
- Stage：只负责编排流程
- 回滚：只负责数据源替换

### 3. 符合 DRY 原则
- 零重复代码
- 正向和回滚共享 100% 的逻辑代码

### 4. 低维护成本
- 新增功能：自动支持回滚
- 修改功能：回滚同步更新
- 调试问题：只需看一套代码

### 5. 易于测试
- 单元测试：测试正向执行即可（回滚自动覆盖）
- 集成测试：只需验证 `rollbackCtx` 装填正确

---

## 与其他方案对比

### 方案 A：配置回滚（当前方案）

```
工作量：8 小时
代码改动：2 个文件，40 行代码
维护成本：极低（自动复用）
扩展性：极好（新 Step 自动支持）
```

### 方案 B：操作回滚（补偿事务）

```
工作量：40 小时
代码改动：12 个文件，每个 Step 实现 rollback()
维护成本：高（每个 Step 两套逻辑）
扩展性：差（新 Step 需要实现两个方法）
```

### 方案 C：专用回滚 Steps

```
工作量：30 小时
代码改动：重复实现 6 个 RollbackStep
维护成本：中（需要同步维护两套 Step）
扩展性：中（新功能需要实现两次）
```

---

**结论**：配置回滚方案是最优选择，工作量最少、维护成本最低、扩展性最好。

