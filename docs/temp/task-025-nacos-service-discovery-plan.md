# T-025: Nacos 服务发现集成方案

## 📋 任务概述

将 Nacos 服务发现功能正式接入到 deploy 模块，替换当前硬编码的 fallback 实例配置，支持动态服务发现。

---

## 🎯 目标

1. **动态服务发现**：从 Nacos 获取服务实例列表，而非使用硬编码配置
2. **降级机制**：Nacos 不可用时，自动降级到 fallbackInstances
3. **实例选择策略**：支持单实例（负载均衡）或多实例（并发调用）
4. **配置驱动**：通过配置文件控制服务发现行为

---

## 🔍 现状分析

### 当前架构

**配置结构（InfrastructureConfig）：**
```java
class InfrastructureConfig {
    private NacosConfig nacos;                         // Nacos 配置
    private Map<String, List<String>> fallbackInstances;  // 降级配置
}

class NacosConfig {
    private Map<String, String> services;  // serviceKey -> Nacos服务名
}
```

**Assembler 中的使用：**
```java
// BlueGreenStageAssembler.resolveEndpoints()
List<String> fallbackInstances = resources.getConfigLoader()
    .getInfrastructure()
    .getFallbackInstances()
    .get(fallbackKey);

// 直接返回 fallbackInstances，没有尝试从 Nacos 获取
```

**问题：**
- ❌ 硬编码 IP 列表，无法动态扩缩容
- ❌ 没有实际使用 NacosConfig
- ❌ 没有服务发现逻辑

---

## 📦 包结构与修改范围

### 新增文件

```
deploy/src/main/java/xyz/firestige/deploy/infrastructure/
├── discovery/                                    [新增包]
│   ├── ServiceDiscoveryHelper.java              [新增] 服务发现核心类
│   ├── NacosServiceDiscovery.java               [新增] Nacos 客户端封装
│   ├── SelectionStrategy.java                   [新增] 实例选择策略枚举
│   └── ServiceInstance.java                     [新增] 实例信息封装（可选）
```

### 修改文件

```
deploy/src/main/java/xyz/firestige/deploy/
├── infrastructure/
│   ├── config/model/
│   │   └── InfrastructureConfig.java           [修改] 扩展 NacosConfig
│   ├── execution/stage/factory/
│   │   ├── SharedStageResources.java           [修改] 注入 ServiceDiscoveryHelper
│   │   └── assembler/
│   │       ├── BlueGreenStageAssembler.java    [修改] resolveEndpoints() 方法
│   │       ├── ObServiceStageAssembler.java    [修改] resolveEndpoints() 方法
│   │       ├── PortalStageAssembler.java       [修改] 如果存在
│   │       └── AsbcStageAssembler.java         [修改] 如果存在
```

### 配置文件

```
deploy/src/main/resources/
└── deploy-stages.yml                            [修改] 添加 Nacos 配置
```

### 依赖文件

```
deploy/pom.xml                                   [修改] 移除 nacos-client 的 optional
```

---

## 🎯 影响边界分析

### 1. 核心影响模块

| 模块 | 影响范围 | 影响程度 | 风险等级 |
|------|---------|---------|---------|
| **discovery 包** | 新增 | 新功能 | 低 |
| **InfrastructureConfig** | NacosConfig 扩展 | 配置模型 | 低 |
| **SharedStageResources** | 新增字段 + 构造函数 | 资源管理 | 中 |
| **StageAssembler (4个)** | resolveEndpoints() 方法 | 实例解析逻辑 | 高 |
| **deploy-stages.yml** | 新增 nacos 配置 | 配置文件 | 低 |

### 2. 不受影响的模块

- ✅ **RedisAckStep** - 无需修改，仍接收 List<String> endpoints
- ✅ **RedisAckService** - 完全不受影响
- ✅ **TaskStage/StageStep** - 接口不变
- ✅ **TaskRuntimeContext** - 数据结构不变
- ✅ **所有 domain 层** - 业务逻辑不变

### 3. 向后兼容性

| 场景 | 兼容性 | 说明 |
|------|-------|------|
| **Nacos 未配置** | ✅ 兼容 | 自动降级到 fallbackInstances |
| **Nacos disabled** | ✅ 兼容 | 使用 fallbackInstances |
| **仅 fallbackInstances** | ✅ 兼容 | 当前行为保持不变 |
| **配置格式** | ✅ 兼容 | 仅扩展，不删除现有配置 |

### 4. 测试影响范围

| 测试类型 | 需要修改 | 原因 |
|---------|---------|------|
| **单元测试** | 新增 | ServiceDiscoveryHelper/NacosServiceDiscovery 测试 |
| **Assembler 测试** | 修改 | Mock ServiceDiscoveryHelper |
| **集成测试** | 新增 | Nacos + Fallback 场景测试 |
| **E2E 测试** | 无需修改 | 端到端行为不变 |

---

## 💡 设计方案

### 1. 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│ StageAssembler (BlueGreen/ObService/Portal/Asbc)           │
│   ├─ createRedisAckDataPreparer()                          │
│   └─ resolveEndpoints(serviceKey, resources)               │
│       ↓                                                     │
│   ┌─────────────────────────────────────────────────┐     │
│   │ ServiceDiscoveryHelper                           │     │
│   │   ├─ getInstances(serviceKey)                   │     │
│   │   │   ├─ 1️⃣ Try Nacos (if enabled)              │     │
│   │   │   ├─ 2️⃣ Fallback to config                 │     │
│   │   │   └─ 3️⃣ Throw if both fail                 │     │
│   │   └─ selectInstance(instances, strategy)        │     │
│   │       ├─ ALL: 返回全部实例                       │     │
│   │       ├─ RANDOM: 随机选一个                      │     │
│   │       └─ ROUND_ROBIN: 轮询选一个                 │     │
│   └─────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────┘
```

### 2. 核心组件

#### 2.1 ServiceDiscoveryHelper

**位置**：`xyz.firestige.deploy.infrastructure.discovery.ServiceDiscoveryHelper`

**职责：**
- 封装服务发现逻辑
- 支持 Nacos + Fallback 降级
- 提供实例选择策略

**完整类设计：**
```java
package xyz.firestige.deploy.infrastructure.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.deploy.infrastructure.config.model.InfrastructureConfig;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 服务发现辅助类
 * 
 * <p>职责：
 * <ul>
 *   <li>从 Nacos 获取服务实例（如果启用）</li>
 *   <li>降级到 fallbackInstances</li>
 *   <li>实例选择策略（ALL/RANDOM/ROUND_ROBIN）</li>
 * </ul>
 *
 * @since T-025
 */
public class ServiceDiscoveryHelper {
    
    private static final Logger log = LoggerFactory.getLogger(ServiceDiscoveryHelper.class);
    
    private final InfrastructureConfig config;
    private final NacosServiceDiscovery nacosDiscovery;  // nullable
    private final Map<String, AtomicInteger> roundRobinCounters = new HashMap<>();
    
    /**
     * 构造函数
     * 
     * @param config 基础设施配置
     * @param nacosDiscovery Nacos 服务发现（可选）
     */
    public ServiceDiscoveryHelper(InfrastructureConfig config, 
                                   NacosServiceDiscovery nacosDiscovery) {
        this.config = config;
        this.nacosDiscovery = nacosDiscovery;
    }
    
    /**
     * 获取服务实例列表（无策略选择）
     * 
     * @param serviceKey 服务标识（如 "blueGreenGatewayService"）
     * @return 实例列表（host:port 格式）
     * @throws ServiceDiscoveryException 无法获取实例时抛出
     */
    public List<String> getInstances(String serviceKey) {
        // 1. 尝试从 Nacos 获取
        if (isNacosEnabled()) {
            try {
                String nacosServiceName = getNacosServiceName(serviceKey);
                List<String> instances = nacosDiscovery.getHealthyInstances(nacosServiceName);
                
                if (instances != null && !instances.isEmpty()) {
                    log.info("从 Nacos 获取实例: service={}, count={}", serviceKey, instances.size());
                    return instances;
                }
                
                log.warn("Nacos 返回空实例列表: service={}", serviceKey);
            } catch (Exception e) {
                log.warn("Nacos 获取实例失败: service={}, error={}", serviceKey, e.getMessage());
            }
        }
        
        // 2. 降级到 fallback 配置
        List<String> fallbackInstances = getFallbackInstances(serviceKey);
        if (fallbackInstances != null && !fallbackInstances.isEmpty()) {
            log.info("使用 fallback 实例: service={}, count={}", serviceKey, fallbackInstances.size());
            return new ArrayList<>(fallbackInstances);
        }
        
        // 3. 完全失败
        throw new ServiceDiscoveryException(
            "无法获取服务实例: serviceKey=" + serviceKey + 
            ", Nacos=" + (isNacosEnabled() ? "失败" : "未启用") + 
            ", Fallback=无配置"
        );
    }
    
    /**
     * 根据策略选择实例
     * 
     * @param serviceKey 服务标识
     * @param strategy 选择策略
     * @return 选中的实例列表
     */
    public List<String> selectInstances(String serviceKey, SelectionStrategy strategy) {
        List<String> allInstances = getInstances(serviceKey);
        
        switch (strategy) {
            case ALL:
                return allInstances;
                
            case RANDOM:
                if (allInstances.size() == 1) {
                    return allInstances;
                }
                int randomIndex = new Random().nextInt(allInstances.size());
                return Collections.singletonList(allInstances.get(randomIndex));
                
            case ROUND_ROBIN:
                AtomicInteger counter = roundRobinCounters.computeIfAbsent(
                    serviceKey, k -> new AtomicInteger(0)
                );
                int index = counter.getAndIncrement() % allInstances.size();
                return Collections.singletonList(allInstances.get(index));
                
            default:
                throw new IllegalArgumentException("Unsupported strategy: " + strategy);
        }
    }
    
    // ---- 私有辅助方法 ----
    
    private boolean isNacosEnabled() {
        return config.getNacos() != null && 
               config.getNacos().isEnabled() && 
               nacosDiscovery != null;
    }
    
    private String getNacosServiceName(String serviceKey) {
        if (config.getNacos() == null || config.getNacos().getServices() == null) {
            throw new ServiceDiscoveryException("Nacos services 配置为空");
        }
        
        String serviceName = config.getNacos().getServiceName(serviceKey);
        if (serviceName == null) {
            throw new ServiceDiscoveryException("未找到 Nacos 服务映射: " + serviceKey);
        }
        
        return serviceName;
    }
    
    private List<String> getFallbackInstances(String serviceKey) {
        if (config.getFallbackInstances() == null) {
            return null;
        }
        
        // 尝试多种 fallbackKey 匹配
        // 1. 直接匹配 serviceKey
        List<String> instances = config.getFallbackInstances().get(serviceKey);
        if (instances != null) {
            return instances;
        }
        
        // 2. 转换为 kebab-case 匹配（blueGreenGatewayService -> blue-green-gateway）
        String kebabKey = toKebabCase(serviceKey);
        return config.getFallbackInstances().get(kebabKey);
    }
    
    private String toKebabCase(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1-$2")
                        .replaceAll("Service$", "")
                        .toLowerCase();
    }
    
    /**
     * 服务发现异常
     */
    public static class ServiceDiscoveryException extends RuntimeException {
        public ServiceDiscoveryException(String message) {
            super(message);
        }
        
        public ServiceDiscoveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
```

#### 2.2 实例选择策略

**位置**：`xyz.firestige.deploy.infrastructure.discovery.SelectionStrategy`

```java
package xyz.firestige.deploy.infrastructure.discovery;

/**
 * 实例选择策略
 *
 * @since T-025
 */
public enum SelectionStrategy {
    /**
     * 全部实例（用于并发健康检查、并发通知）
     */
    ALL,
    
    /**
     * 随机选择一个实例（简单负载均衡）
     */
    RANDOM,
    
    /**
     * 轮询选择一个实例（有状态负载均衡）
     */
    ROUND_ROBIN
}
```

#### 2.3 Nacos 客户端封装

**位置**：`xyz.firestige.deploy.infrastructure.discovery.NacosServiceDiscovery`

**完整类设计：**
```java
package xyz.firestige.deploy.infrastructure.discovery;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Nacos 服务发现封装
 * 
 * <p>职责：
 * <ul>
 *   <li>初始化 Nacos NamingService</li>
 *   <li>获取健康服务实例</li>
 *   <li>异常处理和日志</li>
 * </ul>
 *
 * @since T-025
 */
public class NacosServiceDiscovery {
    
    private static final Logger log = LoggerFactory.getLogger(NacosServiceDiscovery.class);
    
    private final NamingService namingService;
    private final String namespace;
    private volatile boolean available = true;
    
    /**
     * 构造函数
     * 
     * @param serverAddr Nacos 服务器地址（如 "127.0.0.1:8848"）
     * @param namespace 命名空间（可选）
     * @throws NacosException Nacos 初始化失败
     */
    public NacosServiceDiscovery(String serverAddr, String namespace) throws NacosException {
        this.namespace = namespace;
        
        Properties properties = new Properties();
        properties.put("serverAddr", serverAddr);
        if (namespace != null && !namespace.isEmpty()) {
            properties.put("namespace", namespace);
        }
        
        try {
            this.namingService = NamingFactory.createNamingService(properties);
            log.info("Nacos 客户端初始化成功: serverAddr={}, namespace={}", serverAddr, namespace);
        } catch (NacosException e) {
            log.error("Nacos 客户端初始化失败: serverAddr={}", serverAddr, e);
            this.available = false;
            throw e;
        }
    }
    
    /**
     * 从 Nacos 获取健康实例列表
     * 
     * @param serviceName Nacos 服务名
     * @return 实例列表（host:port 格式），失败返回空列表
     */
    public List<String> getHealthyInstances(String serviceName) {
        if (!available) {
            log.warn("Nacos 不可用，跳过查询: service={}", serviceName);
            return Collections.emptyList();
        }
        
        try {
            List<Instance> instances = namingService.selectInstances(serviceName, true);
            
            if (instances == null || instances.isEmpty()) {
                log.warn("Nacos 未找到健康实例: service={}, namespace={}", serviceName, namespace);
                return Collections.emptyList();
            }
            
            List<String> endpoints = instances.stream()
                .map(inst -> inst.getIp() + ":" + inst.getPort())
                .collect(Collectors.toList());
            
            log.debug("Nacos 查询成功: service={}, instances={}", serviceName, endpoints);
            return endpoints;
            
        } catch (NacosException e) {
            log.error("Nacos 查询失败: service={}, error={}", serviceName, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 检查 Nacos 是否可用
     */
    public boolean isAvailable() {
        return available;
    }
    
    /**
     * 关闭 Nacos 客户端
     */
    public void shutdown() {
        try {
            if (namingService != null) {
                namingService.shutDown();
                log.info("Nacos 客户端已关闭");
            }
        } catch (NacosException e) {
            log.warn("Nacos 客户端关闭异常", e);
        }
    }
}
```

---

## 📐 配置扩展

### 3.1 InfrastructureConfig 扩展

**文件**：`InfrastructureConfig.java`

**修改内容：**
```java
// 在 NacosConfig 内部类中添加字段

public static class NacosConfig {
    private boolean enabled = false;  // [新增] 是否启用 Nacos
    private String serverAddr;        // [新增] Nacos 服务器地址
    private String namespace;         // [新增] 命名空间
    private Map<String, String> services;  // [已存在] serviceKey -> Nacos服务名
    
    // Getters/Setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public String getServerAddr() { return serverAddr; }
    public void setServerAddr(String serverAddr) { this.serverAddr = serverAddr; }
    
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    
    // ...existing getters/setters...
}
```

**影响分析：**
- ✅ 向后兼容：新增字段有默认值
- ✅ 现有 services Map 不变
- ✅ 配置加载逻辑不变（Jackson 自动映射）

### 3.2 SharedStageResources 修改

**文件**：`SharedStageResources.java`

**修改内容：**
```java
@Component
public class SharedStageResources {

    // ...existing fields...
    private final RedisAckService redisAckService;
    private final ServiceDiscoveryHelper serviceDiscoveryHelper;  // [新增]

    @Autowired
    public SharedStageResources(
            RestTemplate restTemplate,
            StringRedisTemplate redisTemplate,
            DeploymentConfigLoader configLoader,
            ObjectMapper objectMapper,
            @Autowired(required = false) AgentService agentService,
            RedisAckService redisAckService,
            @Autowired(required = false) ServiceDiscoveryHelper serviceDiscoveryHelper) {  // [新增]

        // ...existing validation...
        
        this.serviceDiscoveryHelper = serviceDiscoveryHelper != null 
            ? serviceDiscoveryHelper 
            : createDefaultServiceDiscoveryHelper(configLoader);  // [新增降级]
    }
    
    // [新增] 默认实现（无 Nacos）
    private ServiceDiscoveryHelper createDefaultServiceDiscoveryHelper(DeploymentConfigLoader configLoader) {
        return new ServiceDiscoveryHelper(
            configLoader.getInfrastructure(), 
            null  // 无 Nacos
        );
    }
    
    // [新增] Getter
    public ServiceDiscoveryHelper getServiceDiscoveryHelper() {
        return serviceDiscoveryHelper;
    }
    
    // ...existing methods...
}
```

**影响分析：**
- ⚠️ 构造函数参数增加：需要修改 Spring 配置
- ✅ 降级机制：ServiceDiscoveryHelper 可选，默认创建无 Nacos 版本
- ✅ 向后兼容：不影响现有功能

### 3.3 Spring Configuration

**新增文件**：`ServiceDiscoveryConfiguration.java`

**位置**：`xyz.firestige.deploy.config.ServiceDiscoveryConfiguration`

```java
package xyz.firestige.deploy.infrastructure.config;

import com.alibaba.nacos.api.exception.NacosException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.firestige.deploy.infrastructure.config.model.InfrastructureConfig;
import xyz.firestige.deploy.infrastructure.discovery.NacosServiceDiscovery;
import xyz.firestige.deploy.infrastructure.discovery.ServiceDiscoveryHelper;

/**
 * 服务发现配置
 *
 * @since T-025
 */
@Configuration
public class ServiceDiscoveryConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ServiceDiscoveryConfiguration.class);

    /**
     * Nacos 服务发现 Bean（仅在启用时创建）
     */
    @Bean
    @ConditionalOnProperty(prefix = "infrastructure.nacos", name = "enabled", havingValue = "true")
    public NacosServiceDiscovery nacosServiceDiscovery(DeploymentConfigLoader configLoader) {
        InfrastructureConfig.NacosConfig nacosConfig = configLoader.getInfrastructure().getNacos();
        
        try {
            NacosServiceDiscovery discovery = new NacosServiceDiscovery(
                nacosConfig.getServerAddr(),
                nacosConfig.getNamespace()
            );
            log.info("Nacos 服务发现已启用: serverAddr={}", nacosConfig.getServerAddr());
            return discovery;
            
        } catch (NacosException e) {
            log.error("Nacos 初始化失败，将使用 fallback 配置", e);
            throw new IllegalStateException("Failed to initialize Nacos", e);
        }
    }

    /**
     * 服务发现辅助类 Bean
     */
    @Bean
    public ServiceDiscoveryHelper serviceDiscoveryHelper(
            DeploymentConfigLoader configLoader,
            @Autowired(required = false) NacosServiceDiscovery nacosDiscovery) {
        
        return new ServiceDiscoveryHelper(
            configLoader.getInfrastructure(),
            nacosDiscovery  // 可能为 null
        );
    }
}
```

### 3.2 YAML 配置示例

```yaml
infrastructure:
  nacos:
    enabled: true
    serverAddr: "127.0.0.1:8848"
    namespace: "dev"
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
```

---

## 🔄 使用流程

### 4.1 Assembler 改造示例

**修改前：**
```java
private List<String> resolveEndpoints(String nacosServiceKey, String fallbackKey, SharedStageResources resources) {
    List<String> fallbackInstances = resources.getConfigLoader()
        .getInfrastructure()
        .getFallbackInstances()
        .get(fallbackKey);
    
    if (fallbackInstances == null || fallbackInstances.isEmpty()) {
        throw new IllegalStateException("No fallback instances configured");
    }
    
    return fallbackInstances;
}
```

**修改后：**
```java
private List<String> resolveEndpoints(String serviceKey, SelectionStrategy strategy, SharedStageResources resources) {
    ServiceDiscoveryHelper helper = resources.getServiceDiscoveryHelper();
    
    try {
        // 优先从 Nacos 获取，自动降级到 fallback
        List<String> instances = helper.selectInstances(serviceKey, strategy);
        log.info("获取服务实例成功: service={}, count={}, strategy={}", 
            serviceKey, instances.size(), strategy);
        return instances;
        
    } catch (Exception e) {
        log.error("服务发现失败: service={}", serviceKey, e);
        throw new IllegalStateException("Failed to resolve service instances: " + serviceKey, e);
    }
}
```

### 4.2 RedisAck DataPreparer 改造

**BlueGreenStageAssembler：**
```java
// 6. Verify 配置
List<String> endpoints = resolveEndpoints(
    "blueGreenGatewayService",  // Nacos serviceKey
    SelectionStrategy.ALL,       // 多实例并发验证
    resources
);

List<String> verifyUrls = endpoints.stream()
    .map(ep -> "http://" + ep + healthCheckPath)
    .collect(Collectors.toList());
```

**ObServiceStageAssembler：**
```java
// 5. Verify 配置
List<String> endpoints = resolveEndpoints(
    "obService",                 // Nacos serviceKey
    SelectionStrategy.ALL,       // 多实例并发验证
    resources
);
```

---

## 🛠️ 实现步骤

### Phase 1: 基础设施（核心组件）

1. **创建 ServiceDiscoveryHelper**
   - `getInstances(serviceKey)` - 获取实例列表
   - `selectInstances(serviceKey, strategy)` - 选择实例
   - Nacos + Fallback 降级逻辑

2. **创建 NacosServiceDiscovery**
   - 封装 Nacos NamingService
   - 处理连接、异常、健康检查

3. **创建 SelectionStrategy 枚举**
   - ALL, RANDOM, ROUND_ROBIN

4. **扩展 InfrastructureConfig**
   - NacosConfig 添加 enabled、serverAddr、namespace

5. **修改 SharedStageResources**
   - 注入 ServiceDiscoveryHelper

### Phase 2: Assembler 改造

6. **修改 BlueGreenStageAssembler**
   - `resolveEndpoints()` 使用 ServiceDiscoveryHelper
   - 策略选择：SelectionStrategy.ALL

7. **修改 ObServiceStageAssembler**
   - 同样改造 `resolveEndpoints()`

8. **修改 PortalStageAssembler**
   - 如果存在的话

9. **修改 AsbcStageAssembler**
   - 如果存在的话

### Phase 3: 配置与测试

10. **更新配置文件**
    - deploy-stages.yml 添加 Nacos 配置
    - 保留 fallbackInstances 作为降级

11. **集成测试**
    - Nacos 正常工作场景
    - Nacos 不可用降级场景
    - 实例选择策略测试

12. **文档更新**
    - 配置说明
    - 服务发现使用指南

---

## 🎭 降级策略

### 优先级顺序

1. **优先**：Nacos 服务发现（nacos.enabled=true）
2. **降级**：fallbackInstances 配置
3. **失败**：抛出异常

### 降级触发条件

- Nacos 服务器不可达
- Nacos 服务名未注册
- Nacos 返回空实例列表
- Nacos 客户端初始化失败

### 日志策略

```java
// 使用 Nacos
log.info("从 Nacos 获取实例: service={}, instances={}", serviceName, instances);

// 降级到 Fallback
log.warn("Nacos 不可用，使用 fallback 实例: service={}, reason={}", serviceKey, reason);

// 完全失败
log.error("服务发现失败: service={}, 无 Nacos 实例且无 fallback 配置", serviceKey);
```

---

## 🔒 风险与缓解

### 风险

1. **Nacos 单点故障** → fallbackInstances 降级
2. **网络延迟** → 添加超时配置（connectTimeout, readTimeout）
3. **实例变化频繁** → 缓存机制（可选，Phase 4）
4. **配置错误** → 启动时验证配置完整性

### 缓解措施

- ✅ 强制要求 fallbackInstances 配置
- ✅ Nacos 客户端异常捕获
- ✅ 健康检查机制
- ✅ 详细的日志记录

---

## 📊 性能考虑

### 优化点

1. **连接池复用**：NamingService 单例
2. **结果缓存**（可选）：缓存 Nacos 查询结果 30s
3. **并发查询**：多服务并发获取实例列表
4. **健康实例过滤**：Nacos 自动返回健康实例

---

## 🧪 测试场景

### 单元测试

- ✅ ServiceDiscoveryHelper.getInstances() - Nacos 正常
- ✅ ServiceDiscoveryHelper.getInstances() - Nacos 失败降级
- ✅ ServiceDiscoveryHelper.selectInstances() - ALL 策略
- ✅ ServiceDiscoveryHelper.selectInstances() - RANDOM 策略
- ✅ NacosServiceDiscovery.getHealthyInstances()

### 集成测试

- ✅ BlueGreenStageAssembler - Nacos 获取实例
- ✅ ObServiceStageAssembler - Fallback 降级
- ✅ RedisAckStep - 多实例并发验证

---

## 📝 设计决策（已确认）

### 1. Nacos 配置位置 ✅
**当前方案**：InfrastructureConfig (deploy-stages.yml)
**未来规划**：整体迁移到 application.yml（参考 T-017 设计）

**理由**：
- 保持当前配置统一性
- 为后续 T-017 配置迁移做准备
- 配置结构保持一致性

### 2. 实例选择策略 ✅
**明确需求：**
- **BlueGreen/ObService**：`ALL` - 多实例全部并发验证
- **Portal/Asbc**：`RANDOM` - 随机选择一个单实例调用

**实现**：Assembler 层面灵活选择策略

### 3. Nacos Namespace ✅
**数据源**：从 `TenantConfig` 动态获取，而非配置文件

**设计变更：**
```java
// ServiceDiscoveryHelper 需要支持动态 namespace
public List<String> getInstances(String serviceKey, String namespace) {
    // namespace 从 TenantConfig 传入
}
```

**影响**：
- NacosServiceDiscovery 需要支持动态 namespace 查询
- 不再在 InfrastructureConfig 中硬编码 namespace

### 4. 健康检查能力 ✅
**需求**：支持健康检查，但可跳过

**设计**：
```java
// ServiceDiscoveryHelper 添加健康检查选项
public List<String> selectInstances(
    String serviceKey, 
    SelectionStrategy strategy,
    boolean enableHealthCheck  // 是否启用健康检查
) {
    List<String> instances = getInstances(serviceKey);
    
    if (enableHealthCheck) {
        instances = filterHealthyInstances(instances);
    }
    
    return applyStrategy(instances, strategy);
}
```

**配置控制**：
```yaml
infrastructure:
  nacos:
    healthCheckEnabled: true  # 全局开关
```

### 5. Nacos 缓存策略 ✅
**需求**：
- 缓存 Nacos 查询结果（减少调用频率）
- Failback 机制：URL 失败时更新缓存

**设计方案：**

#### 缓存结构
```java
class ServiceInstanceCache {
    private Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    
    static class CacheEntry {
        List<String> instances;
        long timestamp;
        long ttl = 30_000;  // 30秒
        Set<String> failedInstances;  // 失败实例列表
    }
}
```

#### Failback 机制
```java
// 1. Assembler 调用时获取实例
List<String> instances = helper.selectInstances("service", ALL, true);

// 2. RedisAckStep 执行失败后回调
helper.markInstanceFailed("service", "192.168.1.10:8080");

// 3. 下次调用时自动过滤失败实例
// 如果缓存中所有实例都失败，强制刷新缓存
```

#### 缓存刷新策略
- **时间触发**：TTL 过期（30秒）
- **失败触发**：所有缓存实例都标记失败
- **手动触发**：提供 API 强制刷新

---

## 📝 待讨论问题（已解决）

~~所有问题已在上方"设计决策"中明确~~

---

## 🎯 验收标准

### 功能验收

- ✅ Nacos 正常时，从 Nacos 获取实例
- ✅ Nacos 不可用时，自动降级到 fallbackInstances
- ✅ 支持 ALL 实例选择策略
- ✅ BlueGreenStageAssembler 集成完成
- ✅ ObServiceStageAssembler 集成完成

### 质量验收

- ✅ 单元测试覆盖率 > 80%
- ✅ 集成测试通过
- ✅ 日志完整（INFO/WARN/ERROR）
- ✅ 无硬编码配置残留

### 文档验收

- ✅ 配置文件示例
- ✅ 服务发现使用指南
- ✅ 降级策略说明

---

## 📅 时间估算

| Phase | 任务 | 预计时间 |
|-------|------|---------|
| Phase 1 | 基础设施（5个任务） | 4h |
| Phase 2 | Assembler 改造（4个） | 3h |
| Phase 3 | 配置与测试 | 2h |
| **总计** | | **9h** |

---

## 🔗 相关文档

- Nacos 服务发现文档：https://nacos.io/zh-cn/docs/open-api.html
- InfrastructureConfig：`deploy/src/main/java/.../config/model/InfrastructureConfig.java`
- BlueGreenStageAssembler：`deploy/src/main/java/.../assembler/BlueGreenStageAssembler.java`

---

**方案提出日期**：2025-11-25  
**状态**：待讨论

