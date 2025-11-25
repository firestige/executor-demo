# Nacos 服务发现集成设计

> **创建日期**: 2025-11-25  
> **任务**: T-025  
> **状态**: Implemented

---

## 概述

本文档描述 deploy 模块中 Nacos 服务发现的集成设计，用于动态获取服务实例，替换硬编码的 IP 地址配置。

---

## 设计目标

1. **动态服务发现**：从 Nacos 动态获取服务实例列表
2. **降级机制**：Nacos 不可用时自动降级到 fallbackInstances
3. **灵活策略**：支持多种实例选择策略（ALL/RANDOM/ROUND_ROBIN）
4. **租户隔离**：支持通过 namespace 实现多租户隔离
5. **性能优化**：缓存机制和 Failback 自动恢复

---

## 架构设计

### 组件结构

```
infrastructure/
├── discovery/
│   ├── ServiceDiscoveryHelper.java      // 核心服务发现逻辑
│   ├── NacosServiceDiscovery.java       // Nacos 客户端封装
│   └── SelectionStrategy.java           // 实例选择策略
└── config/
    └── ServiceDiscoveryConfiguration.java  // Spring 配置
```

### 核心类设计

#### ServiceDiscoveryHelper

**职责**：
- 从 Nacos 获取服务实例（支持动态 namespace）
- 降级到 fallbackInstances
- 实例选择策略（ALL/RANDOM/ROUND_ROBIN）
- 缓存与 Failback 机制
- 可选健康检查

**关键方法**：
```java
List<String> getInstances(String serviceKey, String namespace)
List<String> selectInstances(String serviceKey, String namespace, 
                             SelectionStrategy strategy, boolean enableHealthCheck)
void markInstanceFailed(String serviceKey, String namespace, String failedInstance)
void clearCache(String serviceKey, String namespace)
```

#### NacosServiceDiscovery

**职责**：
- 封装 Nacos NamingService
- 处理连接、异常、健康检查

**关键方法**：
```java
List<String> getHealthyInstances(String serviceName, String namespace)
boolean isAvailable()
void shutdown()
```

#### SelectionStrategy

**枚举值**：
- `ALL`: 返回全部实例（用于并发验证）
- `RANDOM`: 随机选择一个实例（简单负载均衡）
- `ROUND_ROBIN`: 轮询选择一个实例（有状态负载均衡）

---

## 使用场景

### 场景 1: BlueGreen/ObService - 多实例并发验证

```java
// BlueGreenStageAssembler
String namespace = config.getNacosNameSpace();
List<String> endpoints = resources.getServiceDiscoveryHelper()
    .selectInstances("blueGreenGatewayService", namespace, 
                     SelectionStrategy.ALL, true);  // 启用健康检查
```

**特点**：
- 策略：ALL（返回所有实例）
- 健康检查：启用
- 用途：RedisAckStep 并发验证所有实例是否加载配置

### 场景 2: Portal/ASBC - 单实例调用

```java
// PortalStageAssembler
String namespace = config.getNacosNameSpace();
List<String> instances = resources.getServiceDiscoveryHelper()
    .selectInstances("portalService", namespace, 
                     SelectionStrategy.RANDOM, false);  // 不启用健康检查

String instance = instances.get(0);  // RANDOM 返回单实例
String endpoint = "http://" + instance + "/api/notify";
```

**特点**：
- 策略：RANDOM（随机选择一个）
- 健康检查：禁用
- 用途：单次 HTTP 调用，简单负载均衡

---

## 缓存与 Failback 机制

### 缓存策略

```java
class CacheEntry {
    List<String> instances;           // 原始实例列表
    long timestamp;                   // 缓存时间戳
    long ttl = 30_000;               // 30秒 TTL
    Set<String> failedInstances;     // 失败实例集合
}
```

**缓存触发**：
- 首次查询：从 Nacos 获取 → 缓存
- TTL 未过期：返回缓存（排除失败实例）
- TTL 过期：刷新 Nacos → 更新缓存
- 全部失败：强制刷新

### Failback 机制

**标记失败**：
```java
// RedisAckStep 执行失败后
serviceDiscoveryHelper.markInstanceFailed(serviceKey, namespace, failedInstance);
```

**自动过滤**：
```java
// 下次查询自动过滤失败实例
List<String> validInstances = cacheEntry.getValidInstances();
// 如果全部失败，触发强制刷新
```

---

## 降级策略

### 优先级顺序

1. **优先**：Nacos 服务发现（`nacos.enabled=true`）
2. **降级**：`fallbackInstances` 配置
3. **失败**：抛出 `ServiceDiscoveryException`

### 降级触发条件

- Nacos 服务器不可达
- Nacos 服务名未注册
- Nacos 返回空实例列表
- Nacos 客户端初始化失败

### 日志示例

```
INFO  - 从 Nacos 获取实例: service=blueGreenGatewayService, namespace=dev, count=2
WARN  - Nacos 不可用，使用 fallback 实例: service=portalService, reason=Connection timeout
ERROR - 服务发现失败: service=asbcService, 无 Nacos 实例且无 fallback 配置
```

---

## 配置说明

### deploy-stages.yml

```yaml
infrastructure:
  nacos:
    enabled: false                          # 是否启用 Nacos
    serverAddr: "127.0.0.1:8848"           # Nacos 服务器地址
    healthCheckEnabled: false               # 是否启用健康检查
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

### 环境变量

```bash
export NACOS_ENABLED=true
export NACOS_SERVER_ADDR="10.0.0.100:8848"
export NACOS_HEALTH_CHECK_ENABLED=true
export NACOS_BG_SERVICE_NAME="blue-green-gateway-service"
```

---

## 健康检查

### 配置项

```yaml
nacos:
  healthCheckEnabled: true  # 全局开关
```

### 使用场景

```java
// 启用健康检查
List<String> instances = helper.selectInstances(
    "service", namespace, SelectionStrategy.ALL, true);

// 跳过健康检查
List<String> instances = helper.selectInstances(
    "service", namespace, SelectionStrategy.RANDOM, false);
```

### 实现机制

- 使用 Spring Actuator `/actuator/health` endpoint
- 解析 JSON 响应，检查 `status: "UP"`
- 失败实例自动标记到 Failback

---

## 性能考虑

### 优化点

1. **连接复用**：NamingService 单例
2. **缓存机制**：30秒 TTL 减少 Nacos 调用
3. **并发查询**：多服务并发获取实例
4. **健康实例过滤**：Nacos 自动返回健康实例

### 性能指标

- **首次查询**：Nacos 查询 + 缓存（约 50-100ms）
- **缓存命中**：内存查询（<1ms）
- **Failback 标记**：O(1) 操作
- **强制刷新**：Nacos 查询（约 50-100ms）

---

## 扩展点

### 1. 支持其他注册中心

实现 `ServiceDiscovery` 接口：
```java
public interface ServiceDiscovery {
    List<String> getHealthyInstances(String serviceName, String namespace);
    boolean isAvailable();
}

// 实现
public class ConsulServiceDiscovery implements ServiceDiscovery { ... }
public class EurekaServiceDiscovery implements ServiceDiscovery { ... }
```

### 2. 自定义选择策略

扩展 `SelectionStrategy` 枚举：
```java
public enum SelectionStrategy {
    ALL,
    RANDOM,
    ROUND_ROBIN,
    WEIGHTED,          // 加权轮询
    LEAST_CONNECTIONS  // 最少连接
}
```

### 3. 自定义健康检查

```java
public interface HealthChecker {
    boolean check(String instance);
}

// 使用
helper.selectInstances(serviceKey, namespace, strategy, 
                      enableHealthCheck, customHealthChecker);
```

---

## 依赖关系

```
StageAssembler
    ↓
SharedStageResources
    ↓
ServiceDiscoveryHelper
    ↓
NacosServiceDiscovery
    ↓
Nacos NamingService (com.alibaba.nacos:nacos-client)
```

---

## 已知限制

1. **Namespace 来源**：必须从 `TenantConfig.nacosNameSpace` 获取，不支持全局配置
2. **缓存 TTL**：固定 30秒，暂不支持配置
3. **健康检查端点**：固定 `/actuator/health`，不支持自定义
4. **重试机制**：依赖 Nacos 客户端内置重试

---

## 测试策略

### 单元测试

- ✅ `ServiceDiscoveryHelper.getInstances()` - Nacos 正常
- ✅ `ServiceDiscoveryHelper.getInstances()` - Nacos 失败降级
- ✅ `ServiceDiscoveryHelper.selectInstances()` - ALL/RANDOM 策略
- ✅ `ServiceDiscoveryHelper.markInstanceFailed()` - Failback
- ✅ `NacosServiceDiscovery.getHealthyInstances()` - 多 namespace

### 集成测试

- ✅ BlueGreenStageAssembler - Nacos + 健康检查
- ✅ PortalStageAssembler - RANDOM 策略
- ✅ Failback 触发 - 实例失败后缓存更新

---

## 相关文档

- 方案文档：`docs/temp/task-025-nacos-service-discovery-plan.md`
- 设计决策：`docs/temp/task-025-design-decisions-supplement.md`
- 配置文件：`deploy/src/main/resources/deploy-stages.yml`

---

## 变更历史

| 日期 | 版本 | 变更内容 |
|------|------|---------|
| 2025-11-25 | 1.0 | 初始版本，实现 Phase 1-3 |

