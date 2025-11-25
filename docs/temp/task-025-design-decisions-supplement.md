# T-025: 设计决策补充文档

本文档补充 T-025 方案中需要特别说明的设计决策。

---

## 1. Namespace 从 TenantConfig 动态获取

### 接口变更

**ServiceDiscoveryHelper:**
```java
// 修改前
public List<String> getInstances(String serviceKey)

// 修改后
public List<String> getInstances(String serviceKey, String namespace)
public List<String> selectInstances(String serviceKey, String namespace, SelectionStrategy strategy, boolean enableHealthCheck)
```

**NacosServiceDiscovery:**
```java
// 修改前
public List<String> getHealthyInstances(String serviceName)

// 修改后
public List<String> getHealthyInstances(String serviceName, String namespace)
```

### Assembler 调用示例

```java
private DataPreparer createRedisAckDataPreparer(TenantConfig config, SharedStageResources resources) {
    return (ctx) -> {
        // 从 TenantConfig 获取 namespace
        String namespace = config.getNamespace(); // 假设 TenantConfig 有此字段
        
        // 5. Verify 配置
        List<String> endpoints = resolveEndpoints(
            "blueGreenGatewayService", 
            namespace,  // 动态传入
            SelectionStrategy.ALL,
            true,  // 启用健康检查
            resources
        );
        
        // ...
    };
}
```

### InfrastructureConfig 变更

**移除硬编码 namespace:**
```java
public static class NacosConfig {
    private boolean enabled = false;
    private String serverAddr;
    // private String namespace;  // ❌ 删除此字段
    private Map<String, String> services;
    private boolean healthCheckEnabled = false;  // [新增]
    
    // Getters/Setters...
}
```

---

## 2. 健康检查设计

### 配置层

```yaml
infrastructure:
  nacos:
    enabled: true
    serverAddr: "127.0.0.1:8848"
    healthCheckEnabled: true  # 全局开关
    services:
      # ...
```

### 使用层

```java
// BlueGreenStageAssembler / ObServiceStageAssembler
List<String> endpoints = resolveEndpoints(
    "blueGreenGatewayService",
    namespace,
    SelectionStrategy.ALL,
    true  // 启用健康检查
);

// PortalStageAssembler / AsbcStageAssembler
List<String> endpoints = resolveEndpoints(
    "portalService",
    namespace,
    SelectionStrategy.RANDOM,
    false  // 跳过健康检查
);
```

### 实现细节

```java
public class ServiceDiscoveryHelper {
    
    private List<String> filterHealthyInstances(String serviceKey, String namespace, List<String> instances) {
        if (!isHealthCheckEnabled()) {
            return instances;  // 全局未启用，直接返回
        }
        
        List<String> healthyInstances = new ArrayList<>();
        
        for (String instance : instances) {
            try {
                // 使用 Spring Actuator health endpoint
                String url = "http://" + instance + "/actuator/health";
                String response = restTemplate.getForObject(url, String.class);
                
                if (response != null && response.contains("UP")) {
                    healthyInstances.add(instance);
                } else {
                    markInstanceFailed(serviceKey, namespace, instance);
                }
            } catch (Exception e) {
                log.debug("健康检查失败: {}", instance, e);
                markInstanceFailed(serviceKey, namespace, instance);
            }
        }
        
        return healthyInstances;
    }
}
```

---

## 3. 缓存与 Failback 机制

### 缓存数据结构

```java
static class CacheEntry {
    private final List<String> instances;           // 原始实例列表
    private final long timestamp;                   // 缓存时间戳
    private final long ttl = 30_000;               // TTL: 30秒
    private final Set<String> failedInstances;     // 失败实例集合
    
    // 获取有效实例（排除失败实例）
    List<String> getValidInstances() {
        return instances.stream()
            .filter(inst -> !failedInstances.contains(inst))
            .collect(Collectors.toList());
    }
}
```

### 缓存策略

#### 获取实例流程

```
1. 检查缓存
   ├─ 缓存未过期 且 有可用实例 → 返回缓存
   ├─ 缓存未过期 但 全部失败   → 强制刷新（跳到步骤2）
   └─ 缓存已过期             → 刷新（跳到步骤2）

2. 查询 Nacos
   ├─ 成功 → 更新缓存，返回实例
   └─ 失败 → 降级到 fallback

3. 降级 fallback
   ├─ 有配置 → 返回 fallback 实例
   └─ 无配置 → 抛出异常
```

#### Failback 触发

**场景1: RedisAckStep 失败**
```java
// RedisAckStep.java
try {
    AckResult result = redisAckService.write()
        .hashKey(key, field)
        .value(value)
        // ...
        .executeAndWait();
    
} catch (AckTimeoutException e) {
    // 标记失败实例
    if (e.getFailedUrl() != null) {
        String instance = extractInstance(e.getFailedUrl());
        resources.getServiceDiscoveryHelper()
            .markInstanceFailed(serviceKey, namespace, instance);
    }
    // ...
}
```

**场景2: 健康检查失败**
```java
// ServiceDiscoveryHelper.filterHealthyInstances()
for (String instance : instances) {
    if (!checkHealth(instance)) {
        markInstanceFailed(serviceKey, namespace, instance);
    }
}
```

#### 缓存清除

```java
// 手动清除缓存
serviceDiscoveryHelper.clearCache("blueGreenGatewayService", namespace);

// 系统自动清除
// 1. TTL 过期（30秒）
// 2. 全部实例失败（触发强制刷新）
```

---

## 4. 修改范围总结

### 新增类

| 类名 | 位置 | 职责 |
|------|------|------|
| ServiceDiscoveryHelper | `infrastructure.discovery` | 服务发现核心逻辑 |
| NacosServiceDiscovery | `infrastructure.discovery` | Nacos 客户端封装 |
| SelectionStrategy | `infrastructure.discovery` | 实例选择策略枚举 |
| ServiceDiscoveryConfiguration | `infrastructure.config` | Spring 配置类 |

### 修改类

| 类名 | 修改内容 | 风险 |
|------|---------|------|
| InfrastructureConfig.NacosConfig | 添加 `enabled`, `serverAddr`, `healthCheckEnabled` 字段；移除 `namespace` | 低 |
| SharedStageResources | 添加 `ServiceDiscoveryHelper` 字段和 getter | 中 |
| BlueGreenStageAssembler | 修改 `resolveEndpoints()` 方法签名和实现 | 高 |
| ObServiceStageAssembler | 修改 `resolveEndpoints()` 方法签名和实现 | 高 |
| PortalStageAssembler | 修改 `resolveEndpoints()` 方法签名和实现（如存在）| 高 |
| AsbcStageAssembler | 修改 `resolveEndpoints()` 方法签名和实现（如存在）| 高 |

### Assembler resolveEndpoints() 方法签名变更

**修改前:**
```java
private List<String> resolveEndpoints(
    String nacosServiceKey,   // 未使用
    String fallbackKey,       // 用于查询 fallbackInstances
    SharedStageResources resources
)
```

**修改后:**
```java
private List<String> resolveEndpoints(
    String serviceKey,              // 服务标识（Nacos key + fallback key）
    String namespace,               // 从 TenantConfig 获取
    SelectionStrategy strategy,     // ALL / RANDOM / ROUND_ROBIN
    boolean enableHealthCheck,      // 是否启用健康检查
    SharedStageResources resources
)
```

---

## 5. 配置示例

### deploy-stages.yml

```yaml
infrastructure:
  nacos:
    enabled: true
    serverAddr: "127.0.0.1:8848"
    healthCheckEnabled: true
    services:
      blueGreenGatewayService: "icc-bg-gateway"
      obService: "ob-campaign"
      portalService: "icc-portal"
      asbcService: "asbc-config"
  
  fallbackInstances:
    blue-green-gateway:
      - "192.168.1.10:8080"
      - "192.168.1.11:8080"
    ob-service:
      - "192.168.1.20:9090"
    portal:
      - "192.168.1.30:7070"
    asbc-config:
      - "192.168.1.40:6060"
```

---

## 6. 测试场景补充

### 单元测试

- ✅ ServiceDiscoveryHelper - 动态 namespace
- ✅ ServiceDiscoveryHelper - 缓存过期刷新
- ✅ ServiceDiscoveryHelper - Failback 标记
- ✅ ServiceDiscoveryHelper - 全部失败强制刷新
- ✅ ServiceDiscoveryHelper - 健康检查过滤
- ✅ NacosServiceDiscovery - 多 namespace 查询

### 集成测试

- ✅ BlueGreenStageAssembler - Nacos + 健康检查 + ALL 策略
- ✅ ObServiceStageAssembler - Nacos + 健康检查 + ALL 策略
- ✅ PortalStageAssembler - Nacos + RANDOM 策略（无健康检查）
- ✅ Failback 触发场景 - 实例失败后缓存更新
- ✅ 缓存 TTL 过期场景

---

**更新日期**: 2025-11-25  
**状态**: 设计已确认，待实施

