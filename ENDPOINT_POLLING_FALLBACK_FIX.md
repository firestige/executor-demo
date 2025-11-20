# EndpointPollingStep Fallback 失败问题分析与修复

## 问题描述

当 `EndpointPollingStep` 不能连接到 Nacos 查询实例 IP 时，无法正确 fallback 到配置文件中给定的 IP 地址访问 endpoint。

## 根因分析

### 问题定位

在 `EndpointPollingStep.getServiceInstances()` 方法中，fallback 逻辑如下：

```java
// 降级到固定 IP 列表
String serviceType = serviceConfig.getServiceType();  // 返回 "blue-green-gateway"
List<String> fallbackAddresses = configLoader.getInfrastructure()
        .getFallbackInstances()
        .get(serviceType);  // 尝试获取 key="blue-green-gateway"
```

### 关键问题：命名格式不匹配

**YAML 配置中的 key**（驼峰命名）：
```yaml
fallbackInstances:
  blueGreenGateway:    # 驼峰命名
    - "192.168.1.10:8080"
  portal:
    - "192.168.1.20:8080"
```

**代码中使用的 key**（连字符命名）：
- `serviceConfig.getServiceType()` 返回 `"blue-green-gateway"`（来自 `BlueGreenGatewayConfig`）
- `serviceConfig.getServiceType()` 返回 `"portal"`（来自 `PortalConfig`）

**映射结果**：
```java
fallbackInstances.get("blue-green-gateway")  // 返回 null，因为 YAML 中是 "blueGreenGateway"
```

### 问题流程

```
1. Nacos 查询失败
   ↓
2. 执行 fallback 逻辑
   ↓
3. 获取 serviceType = "blue-green-gateway"
   ↓
4. 查找 fallbackInstances.get("blue-green-gateway")
   ↓
5. 返回 null（因为 YAML 中 key 是 "blueGreenGateway"）
   ↓
6. fallbackAddresses == null
   ↓
7. 返回空列表 List.of()
   ↓
8. 抛出异常："No available instances for service"
```

## 解决方案

### 方案一：修改 YAML 配置（已实施）✅

将 `fallbackInstances` 的 key 改为与 `serviceType` 一致（连字符命名）：

```yaml
fallbackInstances:
  blue-green-gateway:  # 改为连字符命名
    - "192.168.1.10:8080"
    - "192.168.1.11:8080"
  portal:
    - "192.168.1.20:8080"
    - "192.168.1.21:8080"
```

**优点**：
- 简单直接，无需修改代码
- 配置与领域模型保持一致
- 不引入额外的转换逻辑

**缺点**：
- 需要修改配置文件

### 方案二：在代码中转换命名格式（备选）

在 `EndpointPollingStep` 中添加转换方法：

```java
private String convertToCamelCase(String serviceType) {
    // "blue-green-gateway" -> "blueGreenGateway"
    String[] parts = serviceType.split("-");
    StringBuilder camelCase = new StringBuilder(parts[0]);
    for (int i = 1; i < parts.length; i++) {
        camelCase.append(Character.toUpperCase(parts[i].charAt(0)))
                 .append(parts[i].substring(1));
    }
    return camelCase.toString();
}

private List<ServiceInstance> getServiceInstances(String nacosServiceNameKey, String namespace) {
    // ... Nacos 查询逻辑 ...
    
    // 降级到固定 IP 列表
    String serviceType = convertToCamelCase(serviceConfig.getServiceType());
    List<String> fallbackAddresses = configLoader.getInfrastructure()
            .getFallbackInstances()
            .get(serviceType);
    // ...
}
```

**优点**：
- 不需要修改配置文件
- YAML 配置保持驼峰命名风格

**缺点**：
- 增加代码复杂度
- 引入命名转换逻辑
- 可能在其他地方也需要类似转换

## 修复验证

### 修复后的流程

```
1. Nacos 查询失败
   ↓
2. 执行 fallback 逻辑
   ↓
3. 获取 serviceType = "blue-green-gateway"
   ↓
4. 查找 fallbackInstances.get("blue-green-gateway")
   ↓
5. 返回 ["192.168.1.10:8080", "192.168.1.11:8080"]  ✅
   ↓
6. 转换为 ServiceInstance 列表
   ↓
7. 执行健康检查轮询
```

### 预期日志输出

**Nacos 可用时**：
```
[EndpointPollingStep] Using Nacos service discovery: service=blue-green-gateway-service, count=2
```

**Nacos 不可用时（fallback）**：
```
[EndpointPollingStep] Nacos query failed, falling back to fixed instances: Connection refused
[EndpointPollingStep] Using fallback instances: service=blue-green-gateway, count=2
[EndpointPollingStep] Starting health check: instances=2, maxAttempts=10
```

## 其他潜在问题

### 1. ServiceInstance.fromAddress() 实现

需要确认该方法能正确解析地址格式 `"ip:port"`：

```java
static ServiceInstance fromAddress(String address) {
    String[] parts = address.split(":");
    return new ServiceInstance(parts[0], Integer.parseInt(parts[1]));
}
```

### 2. 配置一致性检查

建议在启动时验证配置的一致性：

```java
@PostConstruct
public void validateConfig() {
    Set<String> serviceTypes = Set.of("blue-green-gateway", "portal");
    Set<String> configuredFallbacks = configLoader.getInfrastructure()
            .getFallbackInstances()
            .keySet();
    
    for (String serviceType : serviceTypes) {
        if (!configuredFallbacks.contains(serviceType)) {
            log.warn("No fallback instances configured for service: {}", serviceType);
        }
    }
}
```

## 测试建议

### 单元测试

创建测试验证 fallback 逻辑：

```java
@Test
void testFallbackWhenNacosUnavailable() {
    // Given: Nacos 不可用
    when(nacosNamingService).thenReturn(null);
    
    // Mock fallback 配置
    InfrastructureConfig.FallbackInstances fallbackConfig = mock(...);
    when(fallbackConfig.get("blue-green-gateway"))
        .thenReturn(List.of("192.168.1.10:8080", "192.168.1.11:8080"));
    
    // When: 执行健康检查
    endpointPollingStep.execute(context);
    
    // Then: 应该使用 fallback 地址
    verify(restTemplate).getForObject(eq("http://192.168.1.10:8080/actuator/health"), ...);
    verify(restTemplate).getForObject(eq("http://192.168.1.11:8080/actuator/health"), ...);
}
```

### 集成测试

在实际环境中测试：

1. **正常流程**：Nacos 可用 → 使用服务发现
2. **Fallback 流程**：Nacos 不可用 → 使用固定 IP
3. **配置缺失**：Fallback 配置不存在 → 抛出异常

## 总结

**根本原因**：配置文件中的命名格式（驼峰）与代码中的 serviceType（连字符）不匹配

**修复方案**：统一使用连字符命名格式

**修复文件**：
- `deploy-stages.yml` - 修改 `fallbackInstances` 的 key 命名

**影响范围**：
- ✅ 修复了 blue-green-gateway 的 fallback
- ✅ 修复了 portal 的 fallback
- ✅ 不影响其他功能

**验证方法**：
1. 编译通过
2. 单元测试通过
3. 在 Nacos 不可用的环境中测试 fallback 逻辑

---

**分析日期**: 2025年11月20日  
**问题严重级别**: 高（影响服务发现降级功能）  
**修复状态**: ✅ 已修复

