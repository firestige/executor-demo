# RF-19 旧架构代码清理方案

**分析日期**: 2025-11-22  
**分析人**: GitHub Copilot

---

## 🔍 问题分析

根据 RF-19 重构文档，所有 Stage 和 Step 的编排已从 YAML 配置迁移到代码编排（DynamicStageFactory），但仍存在大量旧架构的遗留代码。

---

## 📊 RF-19 架构演变

### 旧架构（已废弃）
```yaml
# deploy-stages.yml
services:
  asbc-gateway:
    stages:
      - name: deploy-stage
        steps:
          - type: asbc-config-request
            config: {...}
```

**对应代码**:
- DeploymentConfig.services / serviceTypes
- ServiceTypeConfig
- StageDefinition
- StepDefinition
- DeploymentConfigLoader.getServiceType()
- DeploymentConfigLoader.getServiceConfig()
- DeploymentConfigLoader.supportsServiceType()
- DeploymentConfigLoader.getAllServiceNames()

### 新架构（RF-19）
```yaml
# deploy-stages.yml
infrastructure:
  redis: {...}
  nacos: {...}
  fallbackInstances: {...}
  auth: {...}
  healthCheck: {...}

defaultServiceNames:
  - asbc-gateway
  - portal
  - blue-green-gateway
```

**对应代码**:
- DynamicStageFactory（代码编排）
- 只读取 infrastructure 和 defaultServiceNames

---

## 🗑️ 待清理的遗留代码

### 1. 配置模型类（完全未使用）

#### ServiceTypeConfig.java ❌
**路径**: `src/main/java/xyz/firestige/deploy/infrastructure/config/model/ServiceTypeConfig.java`

**原因**: 
- RF-19 不再从 YAML 读取 stages/steps 配置
- 所有 Stage 编排在 DynamicStageFactory 中完成
- deploy-stages.yml 中已无 services 配置

**引用情况**: 
- ✅ 无实际业务代码调用
- ⚠️ DeploymentConfig 中有字段定义
- ⚠️ DeploymentConfigLoader 中有方法调用

#### StageDefinition.java ❌
**路径**: `src/main/java/xyz/firestige/deploy/infrastructure/config/model/StageDefinition.java`

**原因**: 
- ServiceTypeConfig 的子结构
- RF-19 不再使用

**引用情况**: 
- ✅ 仅被 ServiceTypeConfig 引用

#### StepDefinition.java ❌
**路径**: `src/main/java/xyz/firestige/deploy/infrastructure/config/model/StepDefinition.java`

**原因**: 
- StageDefinition 的子结构
- RF-19 不再使用

**引用情况**: 
- ✅ 仅被 StageDefinition 引用

---

### 2. DeploymentConfig 中的遗留字段 ❌

**文件**: `src/main/java/xyz/firestige/deploy/infrastructure/config/model/DeploymentConfig.java`

**待删除字段**:
```java
private Map<String, ServiceTypeConfig> services;
private Map<String, ServiceTypeConfig> serviceTypes;  // 兼容旧配置
```

**待删除方法**:
```java
public Map<String, ServiceTypeConfig> getServices()
public void setServices(Map<String, ServiceTypeConfig> services)
public Map<String, ServiceTypeConfig> getServiceTypes()
public void setServiceTypes(Map<String, ServiceTypeConfig> serviceTypes)
```

**保留字段**:
```java
private InfrastructureConfig infrastructure;  // ✅ 保留
private List<String> defaultServiceNames;     // ✅ 保留
```

---

### 3. DeploymentConfigLoader 中的遗留方法 ❌

**文件**: `src/main/java/xyz/firestige/deploy/infrastructure/config/DeploymentConfigLoader.java`

**待删除方法**:
```java
// 行 77-82
public ServiceTypeConfig getServiceType(String serviceName)

// 行 87-89
public ServiceTypeConfig getServiceConfig(String serviceName)

// 行 94-98
public boolean supportsServiceType(String serviceName)

// 行 123-127
public List<String> getAllServiceNames()
```

**待修改方法**:
```java
// 行 136-149: validateConfig()
// 需要移除对 services 的验证
private void validateConfig() {
    if (config == null) {
        throw new IllegalStateException("Configuration is null");
    }
    
    if (config.getInfrastructure() == null) {
        throw new IllegalStateException("Infrastructure configuration is missing");
    }
    
    // ❌ 删除这部分
    // if (config.getServices() == null || config.getServices().isEmpty()) {
    //     throw new IllegalStateException("No services configured");
    // }
    
    // ❌ 删除这部分
    // log.info("Configuration validated: {} services configured",
    //         config.getServices().size());
    
    // ✅ 改为
    log.info("Configuration validated successfully");
}
```

---

## ✅ 清理步骤

### Step 1: 删除配置模型类

```bash
rm src/main/java/xyz/firestige/deploy/infrastructure/config/model/ServiceTypeConfig.java
rm src/main/java/xyz/firestige/deploy/infrastructure/config/model/StageDefinition.java
rm src/main/java/xyz/firestige/deploy/infrastructure/config/model/StepDefinition.java
```

### Step 2: 清理 DeploymentConfig

**删除**:
- `services` 字段
- `serviceTypes` 字段
- `getServices()` 方法
- `setServices()` 方法
- `getServiceTypes()` 方法
- `setServiceTypes()` 方法

**保留**:
- `infrastructure` 字段及其 getter/setter
- `defaultServiceNames` 字段及其 getter/setter

### Step 3: 清理 DeploymentConfigLoader

**删除**:
- `getServiceType()` 方法
- `getServiceConfig()` 方法
- `supportsServiceType()` 方法
- `getAllServiceNames()` 方法

**修改**:
- `validateConfig()` 方法 - ���除 services 验证

**保留**:
- `getInfrastructure()` 方法 ✅
- `getDefaultServiceNames()` 方法 ✅
- `loadConfig()` 方法 ✅
- `loadFromYaml()` 方法 ✅

### Step 4: 验证编译

```bash
mvn clean compile -DskipTests
```

### Step 5: 验证没有代码引用

```bash
# 搜索是否有其他代码引用
grep -r "ServiceTypeConfig" src/main/java/
grep -r "StageDefinition" src/main/java/
grep -r "StepDefinition" src/main/java/
grep -r "getServiceType" src/main/java/
grep -r "getServiceConfig" src/main/java/
grep -r "supportsServiceType" src/main/java/
grep -r "getAllServiceNames" src/main/java/
```

---

## 📋 清理前后对比

### DeploymentConfig.java

**清理前**:
```java
public class DeploymentConfig {
    private InfrastructureConfig infrastructure;
    private Map<String, ServiceTypeConfig> services;          // ❌ 删除
    private Map<String, ServiceTypeConfig> serviceTypes;      // ❌ 删除
    private List<String> defaultServiceNames;
}
```

**清理后**:
```java
public class DeploymentConfig {
    private InfrastructureConfig infrastructure;              // ✅ 保留
    private List<String> defaultServiceNames;                 // ✅ 保留
}
```

### DeploymentConfigLoader.java

**清理前**:
- 9 个公共方法
- 验证 services 配置

**清理后**:
- 3 个公共方法（getInfrastructure, getDefaultServiceNames, loadConfig）
- 只验证 infrastructure 配置

---

## 🎯 清理收益

### 代码简化
- **删除文件**: 3 个（ServiceTypeConfig, StageDefinition, StepDefinition）
- **删除方法**: 8 个
- **删除字段**: 2 个
- **代码行数**: 减少 ~200 行

### 架构清晰
- ✅ YAML 配置职责明确：只提供运行时无关配置
- ✅ DeploymentConfig 不再包含 Stage/Step 编排信息
- ✅ DeploymentConfigLoader 职责单一：加载 infrastructure 和 defaultServiceNames

### 维护成本降低
- ✅ 无冗余代码
- ✅ 无歧义（不会混淆新旧架构）
- ✅ 易于理解

---

## ⚠️ 风险评估

### 影响范围
- ✅ **主代码**: 无影响（DynamicStageFactory 不使用这些类）
- ⚠️ **测试代码**: 可能有测试引用（需要检查并删除）
- ✅ **运行时**: 无影响（YAML 中已无 services 配置）

### 回滚方案
- Git 保留历史记录，可随时回滚
- 如果发现有遗漏的引用，可以先标记 @Deprecated

---

## 📝 清理检查清单

- [ ] 删除 ServiceTypeConfig.java
- [ ] 删除 StageDefinition.java
- [ ] 删除 StepDefinition.java
- [ ] 清理 DeploymentConfig 中的 services 相关字段和方法
- [ ] 清理 DeploymentConfigLoader 中的 services 相关方法
- [ ] 修改 validateConfig() 方法
- [ ] 编译验证无错误
- [ ] 搜索验证无遗漏引用
- [ ] 检查测试代码是否需要修改
- [ ] 运行测试验证
- [ ] Git 提交

---

## 🎉 预期结果

清理后，deploy-stages.yml 的配置模型将完全对应 RF-19 架构：

```
deploy-stages.yml
├─ infrastructure      → InfrastructureConfig
│  ├─ redis           → RedisConfig
│  ├─ nacos           → NacosConfig
│  ├─ fallbackInstances → Map<String, List<String>>
│  ├─ auth            → Map<String, AuthConfig>
│  └─ healthCheck     → HealthCheckConfig
└─ defaultServiceNames → List<String>
```

**无冗余，无歧义，完全对应 RF-19 代码编排架构！** ✅

