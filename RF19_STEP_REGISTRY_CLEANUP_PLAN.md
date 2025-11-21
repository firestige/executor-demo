# RF-19 清理工作 - StepRegistry 处理方案

**日期**: 2025-11-21  
**问题**: StepRegistry 仍保留 AbstractConfigurableStep，与 RF-19 设计理念冲突

---

## 🎯 RF-19 设计理念

### Step 应该是原子操作
```
✅ 正确：Step 从 TaskRuntimeContext 读取数据
✅ 正确：Step 只做技术动作，不包含业务逻辑
✅ 正确：编排在 Stage 层（DynamicStageFactory）

❌ 错误：Step 依赖 YAML 配置
❌ 错误：Step 继承 AbstractConfigurableStep
❌ 错误：Step 包含配置读取逻辑
```

---

## 🔍 当前状态分析

### StepRegistry 的作用
```java
// StepRegistry 基于 YAML 配置创建 Step
public StageStep createStep(StepDefinition stepDef, ServiceConfig serviceConfig) {
    // 从 YAML 读取配置
    // 创建继承 AbstractConfigurableStep 的 Step
    // 注入依赖
}
```

### 仍在使用 StepRegistry 的组件

1. **MessageBroadcastStep** - extends AbstractConfigurableStep
   - 用途：蓝绿网关 Redis Pub/Sub
   - 依赖：YAML 配置 + ServiceConfig

2. **EndpointPollingStep** - extends AbstractConfigurableStep
   - 用途：蓝绿网关健康检查
   - 依赖：YAML 配置 + ServiceConfig

3. **蓝绿网关相关代码**
   - BlueGreenGatewayConfigFactory
   - BlueGreenGatewayConfig
   - 仍使用旧的 YAML 配置模式

---

## 📊 新旧架构对比

### 旧架构（蓝绿网关使用）
```
YAML 配置 → StepRegistry → AbstractConfigurableStep
  ↓
services:
  blue-green-gateway:
    stages:
      - steps:
          - type: message-broadcast
            config: {...}
```

### 新架构（RF-19）
```
代码编排 → DynamicStageFactory → 通用 Step
  ↓
DynamicStageFactory.buildStages() {
    StepConfig.builder()
        .dataPreparer(...)
        .step(new HttpRequestStep(...))
        .resultValidator(...)
}
```

---

## 🔧 清理方案

### 方案 A: 全部迁移到 RF-19（推荐）

**步骤**:
1. ❌ 删除 StepRegistry
2. ❌ 删除 AbstractConfigurableStep
3. ❌ 删除旧的 MessageBroadcastStep 和 EndpointPollingStep
4. ✅ 为蓝绿网关创建新的 RF-19 实现
   - BlueGreenGatewayDataPreparer
   - BlueGreenGatewayResultValidator
   - DynamicStageFactory.createBlueGreenGatewayStage()
5. ✅ 使用 RF-19 的通用 Step
   - MessageBroadcastStep（新版，implements StageStep）
   - ConfigWriteStep（已有）
   - 健康检查 Step（新版）

**优点**:
- ✅ 架构统一
- ✅ 符合 RF-19 设计理念
- ✅ 代码清晰，易维护

**缺点**:
- ⚠️ 需要重构蓝绿网关
- ⚠️ 工作量较大（预计 4-6 小时）

---

### 方案 B: 保留 StepRegistry（不推荐）

**步骤**:
1. ⚠️ 保留 StepRegistry
2. ⚠️ 保留 AbstractConfigurableStep
3. ⚠️ 保留旧的 MessageBroadcastStep 和 EndpointPollingStep
4. ✅ 标注为 @Deprecated
5. ✅ 文档说明仅用于蓝绿网关向后兼容

**优点**:
- ✅ 无需重构蓝绿网关
- ✅ 工作量小

**缺点**:
- ❌ 新旧架构共存，混淆
- ❌ 违反 RF-19 设计理念
- ❌ 维护成本高

---

### 方案 C: 渐进式迁移（折中）

**Phase 1**: 当前（保持共存）
- ⚠️ 保留 StepRegistry（标注 @Deprecated）
- ⚠️ 保留旧的 MessageBroadcastStep 等
- ✅ RF-19 服务（ASBC、Portal）使用新架构

**Phase 2**: 下个迭代（迁移蓝绿网关）
- ✅ 为蓝绿网关创建 RF-19 实现
- ✅ 切换到新架构

**Phase 3**: 清理
- ❌ 删除 StepRegistry
- ❌ 删除 AbstractConfigurableStep
- ❌ 删除旧 Step 实现

---

## 💡 推荐方案

### 建议采用**方案 A**（全部迁移）

**理由**:
1. ✅ **架构统一**: 所有服务都用 RF-19 设计
2. ✅ **符合设计理念**: Step 是原子操作，编排在 Stage
3. ✅ **长期收益**: 易维护，易扩展
4. ✅ **学习成本低**: 只需要学习一套架构

### 蓝绿网关迁移到 RF-19 实施清单

#### 1. 创建新的 MessageBroadcastStep（RF-19 版本）
```java
// 新版：implements StageStep（不是 extends AbstractConfigurableStep）
public class MessageBroadcastStep implements StageStep {
    
    @Override
    public void execute(TaskRuntimeContext ctx) throws Exception {
        // 从 TaskRuntimeContext 读取数据
        String topic = (String) ctx.getAdditionalData("topic");
        String message = (String) ctx.getAdditionalData("message");
        
        // 执行 Redis Pub/Sub
        redisTemplate.convertAndSend(topic, message);
    }
}
```

#### 2. 创建蓝绿网关 DataPreparer
```java
private DataPreparer createBlueGreenGatewayDataPreparer(TenantConfig config) {
    return (ctx) -> {
        // 准备 ConfigWrite 数据
        ctx.addVariable("key", "deploy:config:" + config.getTenantId());
        ctx.addVariable("field", "icc-bg-gateway");
        ctx.addVariable("value", JSON.toJSONString(bgConfig));
        
        // 准备 MessageBroadcast 数据
        ctx.addVariable("topic", "icc_ai_ops_srv:tenant_config:topic");
        ctx.addVariable("message", buildMessage(config));
        
        // 准备健康检查数据
        ctx.addVariable("healthCheckEndpoints", endpoints);
    };
}
```

#### 3. 在 DynamicStageFactory 添加
```java
private TaskStage createBlueGreenGatewayStage(TenantConfig config) {
    List<StepConfig> stepConfigs = new ArrayList<>();
    
    // Step 1: Config Write
    stepConfigs.add(StepConfig.builder()
        .stepName("bg-config-write")
        .dataPreparer(createBGConfigWriteDataPreparer(config))
        .step(new ConfigWriteStep(redisTemplate))
        .resultValidator(createBGConfigWriteValidator())
        .build());
    
    // Step 2: Message Broadcast
    stepConfigs.add(StepConfig.builder()
        .stepName("bg-message-broadcast")
        .dataPreparer(createBGMessageBroadcastDataPreparer(config))
        .step(new MessageBroadcastStep(redisTemplate))
        .resultValidator(createBGMessageBroadcastValidator())
        .build());
    
    // Step 3: Health Check (可以用新的 PollingStep + 函数注入)
    stepConfigs.add(StepConfig.builder()
        .stepName("bg-health-check")
        .dataPreparer(createBGHealthCheckDataPreparer(config))
        .step(new PollingStep("bg-health-check"))
        .resultValidator(createBGHealthCheckValidator())
        .build());
    
    return new ConfigurableServiceStage("blue-green-gateway", stepConfigs);
}
```

**预计工作量**: 4-6 小时

---

## 📋 立即行动（如果采用方案 A）

### 第一步：标注旧代码为过时
```java
@Deprecated(since = "RF-19", forRemoval = true)
@Component
public class StepRegistry {
    // 仅用于向后兼容，将在蓝绿网关迁移后删除
}
```

### 第二步：实施蓝绿网关迁移
- [ ] 创建 MessageBroadcastStep（RF-19 版本）
- [ ] 创建蓝绿网关 DataPreparer
- [ ] 创建蓝绿网关 ResultValidator
- [ ] 在 DynamicStageFactory 添加 createBlueGreenGatewayStage()
- [ ] 更新 YAML 配置（删除 blue-green-gateway 的 services 配置）

### 第三步：清理旧代码
- [ ] 删除 StepRegistry
- [ ] 删除 AbstractConfigurableStep
- [ ] 删除旧的 MessageBroadcastStep 和 EndpointPollingStep

---

## ❓ 等待决策

**您希望采用哪个方案？**

- **方案 A**: 全部迁移到 RF-19（推荐，需要 4-6 小时）
- **方案 B**: 保留 StepRegistry（不推荐）
- **方案 C**: 渐进式迁移（折中）

或者，您有其他想法？

