# 多服务 Stage 构建重构完成报告

## 📋 重构概述

**日期**: 2025-11-19  
**目标**: 修复 `DynamicStageFactory` 只能构建单个服务 Stage 的问题，支持一个 Task 包含多个服务的 Stage  
**状态**: ✅ 完成

---

## 🎯 问题分析

### 原始问题

**现象**:
- `DynamicStageFactory#buildStages` 通过 `determineServiceType()` 只推断出**一个**服务类型
- 实际业务需求：一个租户可能需要切换**多个服务**（如 blue-green-gateway, portal, asbc-gateway）
- 导致只有一个服务（asbc-gateway）的 Stage 被创建，其他服务被忽略

**根本原因**:
```java
// ❌ 旧设计
String serviceType = determineServiceType(tenantConfig);  // 只返回一个
ServiceConfig serviceConfig = configFactory.createConfig(serviceType, tenantConfig);
ServiceTypeConfig config = configLoader.getServiceType(serviceType);
// 只为一个服务构建 Stage
```

---

## 🏗️ 解决方案

### 核心设计思路

1. **ServiceName vs ServiceType 概念澄清**:
   - `ServiceName`: 具体服务名称（如 `blue-green-gateway`, `portal`）
   - `DeploySolution`: 部署方案/技术模式（如 `redis-pubsub`, `http-post`）
   - 一个 ServiceName 对应一个 DeploySolution 和一组 Step

2. **服务列表显式化**:
   - 在 `TenantConfig` 中添加 `List<String> serviceNames` 字段
   - 支持外部 API 显式传入，或使用配置文件默认值

3. **简化的解析策略**（两级）:
   - **优先**: 外部 DTO 显式提供 `serviceNames` → 直接使用
   - **兜底**: 外部 DTO 未提供 → 使用配置文件 `defaultServiceNames`

---

## 📝 详细修改内容

### 1. YAML 配置文件

**文件**: `src/main/resources/deploy-stages.yml`

```yaml
# 新增：默认服务列表
defaultServiceNames:
  - blue-green-gateway
  - portal
  - asbc-gateway

# 重命名：serviceTypes → services（语义更清晰）
services:
  blue-green-gateway:
    # ... 配置
  portal:
    # ... 配置
  asbc-gateway:
    # ... 配置
```

### 2. 配置模型

**文件**: `DeploymentConfig.java`

```java
public class DeploymentConfig {
    private Map<String, ServiceTypeConfig> services;      // 新增
    private Map<String, ServiceTypeConfig> serviceTypes;  // 兼容旧配置
    private List<String> defaultServiceNames;             // 新增
}
```

**文件**: `DeploymentConfigLoader.java`

```java
// 新增方法
public ServiceTypeConfig getServiceConfig(String serviceName);
public List<String> getDefaultServiceNames();
public List<String> getAllServiceNames();
```

### 3. DTO 模型

**外部 DTO**: `TenantDeployConfig.java`

```java
/**
 * 可选字段：如果外部明确知道需要哪些服务，可以指定
 */
private List<String> serviceNames;
```

**内部 DTO**: `TenantConfig.java`

```java
/**
 * 必填字段：由 Converter 负责填充
 */
@NotNull
private List<String> serviceNames;
```

### 4. 转换器（防腐层）

**文件**: `TenantConfigConverter.java`

```java
@Component  // 改为 Spring Bean
public class TenantConfigConverter {
    
    private final DeploymentConfigLoader configLoader;
    
    private List<String> resolveServiceNames(TenantDeployConfig external) {
        // 策略 1：显式指定
        if (external.getServiceNames() != null && !external.getServiceNames().isEmpty()) {
            return new ArrayList<>(external.getServiceNames());
        }
        
        // 策略 2：配置文件默认值
        return new ArrayList<>(configLoader.getDefaultServiceNames());
    }
}
```

### 5. 核心工厂重构

**文件**: `DynamicStageFactory.java`

**关键变更**:

```java
@Override
public List<TaskStage> buildStages(TenantConfig tenantConfig) {
    // 1. 获取服务名称列表（有序）
    List<String> serviceNames = tenantConfig.getServiceNames();
    
    // 2. 遍历服务列表，为每个服务构建 Stage
    List<TaskStage> allStages = new ArrayList<>();
    
    for (String serviceName : serviceNames) {
        // 2.1 从 YAML 读取服务配置模板
        ServiceTypeConfig serviceTypeConfig = configLoader.getServiceConfig(serviceName);
        
        // 2.2 通过防腐层转换为领域服务配置
        ServiceConfig serviceConfig = configFactory.createConfig(serviceName, tenantConfig);
        
        // 2.3 构建该服务的所有 Stage
        List<TaskStage> serviceStages = buildStagesForService(
            serviceName, 
            serviceTypeConfig, 
            serviceConfig
        );
        allStages.addAll(serviceStages);
    }
    
    return allStages;
}
```

**Stage 命名规则**:
```
旧: "deploy-stage"
新: "service-{serviceName}-deploy-stage"
例: "service-blue-green-gateway-deploy-stage"
```

---

## 🧪 测试验证

### 新增测试

**文件**: `DynamicStageFactoryIntegrationTest.java`

```java
@Test
void shouldCreateStagesForMultipleServices() {
    // Given
    TenantConfig tenantConfig = new TenantConfig();
    tenantConfig.setServiceNames(
        List.of("blue-green-gateway", "portal", "asbc-gateway")
    );
    
    // When
    List<TaskStage> stages = stageFactory.buildStages(tenantConfig);
    
    // Then
    assertEquals(3, stages.size());  // 三个服务 = 三个 Stage
    
    // 验证每个 Stage
    assertTrue(stages.get(0).getName().contains("blue-green-gateway"));
    assertTrue(stages.get(1).getName().contains("portal"));
    assertTrue(stages.get(2).getName().contains("asbc-gateway"));
}
```

### 测试结果

```
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
✅ 所有测试通过
```

---

## 📊 数据流示例

### 场景 1：外部显式指定服务

```
API 请求:
  serviceNames: ["blue-green-gateway", "portal"]
  
↓ Converter

TenantConfig:
  serviceNames: ["blue-green-gateway", "portal"]
  
↓ DynamicStageFactory

遍历:
  1. blue-green-gateway → Stage 1 (3 steps)
  2. portal → Stage 2 (3 steps)
  
返回: [Stage1, Stage2]
```

### 场景 2：使用默认值

```
API 请求:
  serviceNames: null  // 未指定
  
↓ Converter (读取配置默认值)

TenantConfig:
  serviceNames: ["blue-green-gateway", "portal", "asbc-gateway"]
  
↓ DynamicStageFactory

遍历:
  1. blue-green-gateway → Stage 1
  2. portal → Stage 2
  3. asbc-gateway → Stage 3
  
返回: [Stage1, Stage2, Stage3]
```

---

## ✅ 方案优势

1. **概念清晰**: ServiceName（服务） vs DeploySolution（方案）明确分离
2. **灵活性强**: 支持显式指定或自动兜底
3. **扩展性好**: 新增服务只需在 YAML 添加配置
4. **向后兼容**: 
   - 外部 DTO `serviceNames` 可选
   - 配置文件同时支持 `services` 和 `serviceTypes`
5. **有序执行**: 严格按 List 顺序构建 Stage
6. **易于测试**: 显式配置使测试用例更清晰

---

## 📂 涉及的文件列表

### 配置文件
- ✅ `deploy-stages.yml`

### 模型类
- ✅ `DeploymentConfig.java`
- ✅ `TenantDeployConfig.java`
- ✅ `TenantConfig.java`

### 核心逻辑
- ✅ `DeploymentConfigLoader.java`
- ✅ `TenantConfigConverter.java`
- ✅ `DynamicStageFactory.java`

### 配置类
- ✅ `ExecutorConfiguration.java`

### 测试类
- ✅ `DynamicStageFactoryIntegrationTest.java`

---

## 🔧 后续优化建议

1. **DeploySolution 模板化**:
   - 如果多个服务共享相同的 steps，可以提取模板
   - 减少 YAML 配置重复

2. **动态参数注入**:
   - 使用占位符（如 `${serviceName}`）
   - 进一步简化配置

3. **配置校验增强**:
   - 启动时校验 `defaultServiceNames` 是否都有对应的 service 配置
   - 防止配置错误

4. **监控埋点**:
   - 记录每个服务 Stage 的执行耗时
   - 便于性能分析

---

## 🎉 总结

本次重构成功解决了单服务限制问题，实现了：

- ✅ **支持多服务**: 一个 Task 可以包含多个服务的 Stage
- ✅ **有序执行**: 按配置顺序依次创建 Stage
- ✅ **配置驱动**: 通过 YAML 灵活定义服务列表
- ✅ **向后兼容**: 不破坏现有功能
- ✅ **测试覆盖**: 完整的测试用例验证

**核心改进**: 将隐式的单服务推断改为显式的服务列表配置，使设计更符合 DDD 显式建模原则。

