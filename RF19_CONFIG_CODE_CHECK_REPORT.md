# RF-19 配置和代码检查报告

**检查日期**: 2025-11-21  
**检查项目**: 
1. deploy-stages.yml 是否符合 RF-19 新设计
2. ASBC 是否有不需要的 AbstractConfigurableStep

---

## 🔴 问题 1: deploy-stages.yml 配置过时

### ❌ 当前配置（旧设计）

```yaml
services:
  # 问题：使用了三层结构 services → service → stages
  asbc-gateway:
    stages:
      - name: deploy-stage
        steps:
          - type: asbc-config-request  # ← 专用 Step 类型
            config:
              http-method: "POST"
              validation-type: "http-status"
              expected-status: 200
            retry-policy:
              max-attempts: 1
              interval-seconds: 0
```

### ✅ RF-19 新设计应该是

根据 RF-19 设计文档，配置应该简化为：

```yaml
# infrastructure 配置（保持不变）
infrastructure:
  redis: {...}
  nacos: {...}
  fallbackInstances: {...}
  asbc:
    fixedInstances:
      - "192.168.1.100:8080"
      - "192.168.1.101:8080"
  
  # Auth 配置（新增）
  auth:
    asbc:
      enabled: false  # 关闭时不填 Authorization header
      tokenProvider: "random"  # 打开时支持 random

# stages 配置（两层结构，不再是 services）
# 注意：RF-19 使用代码编排，不依赖 YAML 配置 stages
# 但如果要保留 YAML，应该简化为：
stages:
  - name: asbc-gateway
    # RF-19 中 Stage 由 DynamicStageFactory 代码创建
    # 不需要在 YAML 中配置 steps
```

### 🎯 核心问题

1. **配置层级错误**：
   - 当前: `services → service → stages → steps`
   - RF-19: 代码编排，不依赖 YAML 配置 stages

2. **仍在使用旧的 Step 类型**：
   - 当前: `type: asbc-config-request`
   - RF-19: 使用通用的 HttpRequestStep，由 DynamicStageFactory 代码编排

3. **缺少 RF-19 的配置**：
   - 缺少 `auth` 配置节
   - ASBC 的 endpoint path 硬编码在代码中

---

## 🔴 问题 2: ASBC 仍有旧的 AbstractConfigurableStep

### ❌ 发现的旧代码

```java
// ASBCConfigRequestStep.java
public class ASBCConfigRequestStep extends AbstractConfigurableStep {
    
    public ASBCConfigRequestStep(
            String stepName,
            Map<String, Object> stepConfig,
            ServiceConfig serviceConfig,
            RestTemplate restTemplate,
            DeploymentConfigLoader configLoader,
            ObjectMapper objectMapper) {
        
        super(stepName, stepConfig, serviceConfig);
        // ...
    }
    
    @Override
    public void execute(TaskRuntimeContext ctx) throws Exception {
        // ASBC 专用逻辑...
    }
}
```

### ✅ RF-19 新设计

RF-19 设计中，**不应该有 ASBCConfigRequestStep 这个专用类**！

应该是：
1. **通用 HttpRequestStep** - 完全数据无关
2. **ASBCDataPreparer** - 准备 ASBC 请求数据
3. **ASBCResultValidator** - 验证 ASBC 响应结果
4. **DynamicStageFactory.createASBCStage()** - 代码编排

### 🔍 其他旧代码

```bash
# 仍然使用 AbstractConfigurableStep 的类：
1. ASBCConfigRequestStep ← ❌ 应该删除（RF-19 已有新实现）
2. MessageBroadcastStep ← ✅ 合理（蓝绿网关专用）
3. KeyValueWriteStep ← ⚠️ 可能冲突（RF-19 有新实现）
4. EndpointPollingStep ← ✅ 合理（健康检查）
```

---

## 📊 冲突分析

### 新旧代码共存情况

| 组件 | 旧实现（AbstractConfigurableStep） | 新实现（RF-19） | 状态 |
|------|-----------------------------------|----------------|------|
| **HttpRequestStep** | ❌ 无 | ✅ 有（通用） | 🆕 新增 |
| **ConfigWriteStep** | ✅ KeyValueWriteStep | ✅ ConfigWriteStep | ⚠️ **冲突** |
| **PollingStep** | ❌ 无 | ✅ 有（函数注入） | 🆕 新增 |
| **ASBCConfigRequestStep** | ✅ 有（专用） | ❌ 无（改用通用） | ⚠️ **冗余** |

### ConfigWriteStep 冲突详情

#### 旧实现: KeyValueWriteStep
```java
// KeyValueWriteStep.java (extends AbstractConfigurableStep)
public class KeyValueWriteStep extends AbstractConfigurableStep {
    // 依赖 YAML 配置
    // 依赖 ServiceConfig
}
```

#### 新实现: ConfigWriteStep
```java
// ConfigWriteStep.java (implements StageStep)
public class ConfigWriteStep implements StageStep {
    // 从 TaskRuntimeContext 读取数据
    // 不依赖 YAML 配置
    // 不依赖 ServiceConfig
}
```

**结论**: 两个实现**功能相同但接口不同**，会造成混淆！

---

## 🔧 修正建议

### 1. 清理旧代码

#### 应该删除的文件：
```bash
# ❌ 删除旧的 ASBC 专用 Step
src/main/java/xyz/firestige/deploy/infrastructure/execution/stage/steps/ASBCConfigRequestStep.java

# ⚠️ 重命名或删除旧的 KeyValueWriteStep（与 RF-19 的 ConfigWriteStep 冲突）
src/main/java/xyz/firestige/deploy/infrastructure/execution/stage/steps/KeyValueWriteStep.java
```

#### 应该保留的文件：
```bash
# ✅ 蓝绿网关专用（暂时保留）
src/main/java/xyz/firestige/deploy/infrastructure/execution/stage/steps/MessageBroadcastStep.java
src/main/java/xyz/firestige/deploy/infrastructure/execution/stage/steps/EndpointPollingStep.java

# ✅ RF-19 新实现
src/main/java/xyz/firestige/deploy/infrastructure/execution/stage/steps/HttpRequestStep.java
src/main/java/xyz/firestige/deploy/infrastructure/execution/stage/steps/ConfigWriteStep.java
src/main/java/xyz/firestige/deploy/infrastructure/execution/stage/steps/PollingStep.java
```

### 2. 更新 deploy-stages.yml

#### 选项 A: 简化配置（推荐）

```yaml
# ========== Infrastructure Configuration ==========
infrastructure:
  redis:
    hashKeyPrefix: "icc_ai_ops_srv:tenant_config:"
    pubsubTopic: "icc_ai_ops_srv:tenant_config:topic"
  
  nacos:
    services:
      blueGreenGatewayService: "blue-green-gateway-service"
      portalService: "portal-service"
      asbcService: "asbc-gateway-service"
  
  fallbackInstances:
    blue-green-gateway:
      - "192.168.1.10:8080"
      - "192.168.1.11:8080"
    portal:
      - "192.168.1.20:8080"
    asbc:
      - "192.168.1.100:8080"
      - "192.168.1.101:8080"
  
  # Auth 配置（RF-19 新增）
  auth:
    asbc:
      enabled: false
      tokenProvider: "random"
    portal:
      enabled: false

# ========== Service Names (Default Order) ==========
defaultServiceNames:
  - asbc-gateway
  - portal
  - blue-green-gateway

# 注意：RF-19 使用代码编排（DynamicStageFactory）
# 不再需要 services 和 stages 配置
# 旧的 services 配置可以删除或保留用于向后兼容
```

#### 选项 B: 保留旧配置（向后兼容）

如果需要保持向后兼容，可以同时保留新旧配置：
- 旧系统使用 `services` 配置
- 新系统（RF-19）使用 `infrastructure` + 代码编排

### 3. 迁移路径

#### Phase 1: 共存期
- ✅ 保留旧代码和旧配置
- ✅ 新增 RF-19 代码和配置
- ✅ 通过开关控制使用新旧系统

#### Phase 2: 切换期
- ⚠️ 默认使用 RF-19 新系统
- ⚠️ 旧系统作为降级方案

#### Phase 3: 清理期
- ❌ 删除旧代码（ASBCConfigRequestStep 等）
- ❌ 删除旧配置（services.asbc-gateway.stages）
- ❌ 删除 AbstractConfigurableStep（如果不再使用）

---

## 📋 检查结果总结

### 问题 1: deploy-stages.yml ❌
- **状态**: 配置过时，不符合 RF-19 设计
- **问题**: 
  1. 仍使用三层结构 `services → service → stages`
  2. 仍引用旧的 `asbc-config-request` Step 类型
  3. 缺少 RF-19 的 `auth` 配置
- **影响**: DynamicStageFactory 不依赖此配置，新旧系统配置分离

### 问题 2: ASBCConfigRequestStep ❌
- **状态**: 存在旧的专用 Step 实现
- **问题**:
  1. ASBCConfigRequestStep 继承 AbstractConfigurableStep（旧设计）
  2. RF-19 已有新实现（HttpRequestStep + DataPreparer + Validator）
  3. 两套实现共存，造成混淆
- **影响**: 代码冗余，维护成本高

### 额外发现: KeyValueWriteStep ⚠️
- **状态**: 新旧实现命名冲突
- **问题**:
  1. 旧实现: KeyValueWriteStep (extends AbstractConfigurableStep)
  2. 新实现: ConfigWriteStep (implements StageStep)
  3. 功能相同但接口不同
- **影响**: 可能造成使用混淆

---

## 🎯 建议措施

### 立即行动
1. ✅ **文档化现状** - 本报告
2. ⚠️ **与用户确认迁移策略** - 立即切换？还是保持共存？

### 短期（如果立即切换）
1. ❌ 删除 ASBCConfigRequestStep
2. ⚠️ 重命名或删除旧 KeyValueWriteStep
3. ✅ 更新 deploy-stages.yml（简化或标注过时）
4. ✅ 更新文档说明新旧系统差异

### 长期
1. ❌ 评估 AbstractConfigurableStep 是否还需要
2. ❌ 统一 Step 实现方式（全部改用 RF-19 模式）
3. ✅ 完善 DynamicStageFactory（补充 OBService）

---

**检查完成！发现多处新旧设计冲突，需要用户确认迁移策略。**

