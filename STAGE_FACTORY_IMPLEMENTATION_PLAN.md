# StageFactory 动态编排实现方案

> **版本**: v1.0  
> **日期**: 2025-11-19  
> **状态**: ✅ 已完成

---

## ✅ 实施完成总结

**完成日期**: 2025-11-19  
**实施结果**: 
- [x] Phase 1: 基础设施准备（配置模型） ✅
- [x] Phase 2: Step 实现 ✅
- [x] Phase 3: Factory 实现 ✅
- [x] Phase 4: 集成测试 ✅ (5/5 通过)
- [x] Phase 5: 文档和优化 ✅

**关键指标**:
- 新增文件: **23 个**
- 集成测试: **✅ 5/5 通过**
- 编译状态: **✅ BUILD SUCCESS**
- 测试覆盖: 端到端集成测试完整

**📖 详细实现报告**: 见 [STAGE_FACTORY_IMPLEMENTATION_COMPLETE.md](./STAGE_FACTORY_IMPLEMENTATION_COMPLETE.md)

---

## 📋 目录

1. [背景与目标](#1-背景与目标)
2. [总体架构](#2-总体架构)
3. [设计原则](#3-设计原则)
4. [核心组件设计](#4-核心组件设计)
5. [多步实现方案](#5-多步实现方案)
6. [数据流转示例](#6-数据流转示例)
7. [测试策略](#7-测试策略)
8. [风险评估](#8-风险评估)
9. [附录](#9-附录)

---

## 1. 背景与目标

### 1.1 现状分析

**当前实现**：
- `DefaultStageFactory` 硬编码固定的 Stage 列表
- 所有服务类型使用相同的步骤组合
- 无法灵活支持不同服务的差异化部署流程

```java
// 当前硬编码实现
public List<TaskStage> buildStages(TenantConfig cfg) {
    TaskStage stage = new CompositeServiceStage(
        "switch-service",
        List.of(
            new ConfigUpdateStep("config-update", deployUnitVersion),
            new BroadcastStep("broadcast-change")
        )
    );
    return List.of(stage);
}
```

**存在问题**：
1. ❌ 扩展性差：新增服务类型需要修改工厂代码
2. ❌ 耦合度高：业务逻辑和基础设施混合
3. ❌ 可维护性差：配置变更需要重新编译部署

### 1.2 目标需求

**三种服务类型的差异化流程**：

| 服务类型           | 部署流程                                      | 特殊性                          |
|-------------------|---------------------------------------------|--------------------------------|
| blue-green-gateway| Redis 写入 → Pub/Sub 广播 → 健康检查          | 使用 Nacos 服务发现              |
| portal            | Redis 写入 → Pub/Sub 广播 → 健康检查          | 与蓝绿网关流程相同                |
| asbc-gateway      | HTTP POST 配置请求                           | 固定实例、无重试、自定义数据结构   |

**核心目标**：
1. ✅ 配置驱动：通过 YAML 配置服务类型和步骤组合
2. ✅ 可复用性：抽象通用步骤，多个服务共享
3. ✅ 可扩展性：新增服务类型无需修改代码
4. ✅ 类型安全：利用工厂模式的防腐层隔离配置
5. ✅ 易测试性：步骤独立可测，配置驱动可验证

---

## 2. 总体架构

### 2.1 架构分层

```
┌─────────────────────────────────────────────────────────────┐
│                   Application Layer                         │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  TaskDomainService                                   │   │
│  │  - buildTaskStages(TenantConfig) → List<TaskStage>  │   │
│  └──────────────────┬───────────────────────────────────┘   │
└─────────────────────┼───────────────────────────────────────┘
                      │ 调用
┌─────────────────────▼───────────────────────────────────────┐
│              Infrastructure Layer (新设计)                   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  DynamicStageFactory (配置驱动的工厂)                 │   │
│  │  ├─ ServiceConfigFactoryComposite (防腐层)           │   │
│  │  ├─ DeploymentConfigLoader (YAML 配置加载器)         │   │
│  │  └─ StepRegistry (步骤注册表)                        │   │
│  └──────────────────┬───────────────────────────────────┘   │
│                     │                                        │
│  ┌──────────────────▼───────────────────────────────────┐   │
│  │  Abstract Steps (可复用步骤基类)                      │   │
│  │  ├─ KeyValueWriteStep       (Redis Hash 写入)        │   │
│  │  ├─ MessageBroadcastStep    (Redis Pub/Sub)          │   │
│  │  ├─ EndpointPollingStep     (健康检查 + 轮询)         │   │
│  │  └─ ASBCConfigRequestStep   (ASBC HTTP POST)         │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Configuration (deploy-stages.yml)                    │   │
│  │  - 基础设施配置 (Redis/Nacos/固定实例)                 │   │
│  │  - 服务类型定义 (3 种服务的 Stage/Step 组合)           │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                      │ 使用
┌─────────────────────▼───────────────────────────────────────┐
│                   Domain Layer (防腐层)                      │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  ServiceConfig 系列 (领域配置模型)                     │   │
│  │  ├─ BlueGreenGatewayConfig                           │   │
│  │  ├─ PortalConfig                                     │   │
│  │  └─ ASBCGatewayConfig                                │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 核心交互流程

```
┌─────────┐      ┌──────────────────┐      ┌─────────────────┐      ┌──────────────┐
│ Domain  │      │ DynamicStage     │      │ ServiceConfig   │      │ YAML Config  │
│ Service │      │ Factory          │      │ FactoryComposite│      │ Loader       │
└────┬────┘      └────────┬─────────┘      └────────┬────────┘      └──────┬───────┘
     │                    │                          │                       │
     │ buildStages()      │                          │                       │
     ├───────────────────>│                          │                       │
     │                    │                          │                       │
     │                    │ createConfig()           │                       │
     │                    ├─────────────────────────>│                       │
     │                    │                          │                       │
     │                    │  <ServiceConfig>         │                       │
     │                    │<─────────────────────────┤                       │
     │                    │                          │                       │
     │                    │ getServiceTypeConfig()   │                       │
     │                    ├─────────────────────────────────────────────────>│
     │                    │                          │                       │
     │                    │  <StageDefinitions>      │                       │
     │                    │<─────────────────────────────────────────────────┤
     │                    │                          │                       │
     │                    │ createStep()             │                       │
     │                    ├───────────┐              │                       │
     │                    │           │ (反射或注册表)│                       │
     │                    │<──────────┘              │                       │
     │                    │                          │                       │
     │  <List<TaskStage>> │                          │                       │
     │<───────────────────┤                          │                       │
     │                    │                          │                       │
```

---

## 3. 设计原则

### 3.1 分层原则

| 层次           | 职责                                   | 数据模型                    |
|---------------|---------------------------------------|---------------------------|
| **Domain**    | 防腐层：TenantConfig → ServiceConfig   | ServiceConfig 系列（不可变）|
| **Infra**     | 动态编排：ServiceConfig + YAML → Stage | StageDefinition + Steps    |
| **Config**    | 基础设施配置 + 业务流程配置              | deploy-stages.yml          |

### 3.2 配置分离原则

**YAML 配置负责**（固定基础设施）：
- Redis 连接信息（host, port, topic）
- Nacos 服务名称（固定的服务标识）
- ASBC 固定实例列表（IP + Port）
- 健康检查路径（固定的接口路径）
- 重试策略（固定的执行参数）

**TenantConfig 负责**（运行时业务数据）：
- 租户 ID（业务标识）
- 配置版本号（业务版本）
- 路由数据（key-value pairs）
- 媒体路由配置（ASBC 业务数据）
- Nacos 命名空间（租户级别隔离）

### 3.3 可复用性原则

**抽象步骤设计**：
- `KeyValueWriteStep`：通用的 Redis Hash 写入（蓝绿网关 + Portal 共享）
- `MessageBroadcastStep`：通用的 Redis Pub/Sub（蓝绿网关 + Portal 共享）
- `EndpointPollingStep`：通用的健康检查（蓝绿网关 + Portal 共享）
- `ASBCConfigRequestStep`：ASBC 专用的 HTTP POST（独立实现）

**配置驱动组合**：
```yaml
service-types:
  blue-green-gateway:
    stages:
      - name: deploy-stage
        steps:
          - type: key-value-write      # 复用
          - type: message-broadcast    # 复用
          - type: endpoint-polling     # 复用
  
  portal:
    stages:
      - name: deploy-stage
        steps:
          - type: key-value-write      # 复用（与蓝绿网关相同）
          - type: message-broadcast    # 复用
          - type: endpoint-polling     # 复用
  
  asbc-gateway:
    stages:
      - name: deploy-stage
        steps:
          - type: asbc-config-request  # 独立实现
```

---

## 4. 核心组件设计

### 4.1 配置模型（YAML）

#### 4.1.1 配置文件结构

```yaml
# deploy-stages.yml
deployment:
  # 基础设施配置
  infrastructure:
    redis:
      hash-key-prefix: "deploy:config:"      # Redis Hash key 前缀
      pubsub-topic: "deploy.config.notify"   # Pub/Sub topic
    
    nacos:
      blue-green-gateway-service: "blue-green-gateway-service"
      portal-service: "portal-service"
    
    # 降级配置：当服务发现不可用时，使用固定 IP 列表
    fallback-instances:
      blue-green-gateway:
        - "192.168.1.10:8080"
        - "192.168.1.11:8080"
      portal:
        - "192.168.1.20:8080"
        - "192.168.1.21:8080"
    
    asbc:
      fixed-instances:
        - "192.168.1.100:8080"
        - "192.168.1.101:8080"
      config-endpoint: "/api/v1/config"
    
    health-check:
      default-path: "/actuator/health"
      interval-seconds: 3
      max-attempts: 10
  
  # 服务类型定义
  service-types:
    blue-green-gateway:
      stages:
        - name: deploy-stage
          steps:
            - type: key-value-write
              config:
                hash-field: "blue-green-gateway"
            
            - type: message-broadcast
              config:
                message: "blue-green-gateway"
            
            - type: endpoint-polling
              config:
                nacos-service-name-key: "blue-green-gateway-service"
                validation-type: "json-path"
                validation-rule: "$.status == 'UP'"
              retry-policy:
                max-attempts: 10
                interval-seconds: 3
    
    portal:
      stages:
        - name: deploy-stage
          steps:
            - type: key-value-write
              config:
                hash-field: "portal"
            
            - type: message-broadcast
              config:
                message: "portal"
            
            - type: endpoint-polling
              config:
                nacos-service-name-key: "portal-service"
                validation-type: "json-path"
                validation-rule: "$.status == 'UP'"
              retry-policy:
                max-attempts: 10
                interval-seconds: 3
    
    asbc-gateway:
      stages:
        - name: deploy-stage
          steps:
            - type: asbc-config-request
              config:
                endpoint-key: "config-endpoint"
                http-method: "POST"
                validation-type: "http-status"
                validation-rule: "200"
              retry-policy:
                max-attempts: 1  # ASBC 不支持重试
                interval-seconds: 0
```

#### 4.1.2 配置加载器

```java
/**
 * YAML 配置加载器
 */
@Component
public class DeploymentConfigLoader {
    
    private DeploymentConfig config;
    
    @PostConstruct
    public void loadConfig() {
        // 从 classpath 加载 deploy-stages.yml
        this.config = loadFromYaml("deploy-stages.yml");
    }
    
    public InfrastructureConfig getInfrastructure() {
        return config.infrastructure;
    }
    
    public ServiceTypeConfig getServiceType(String serviceType) {
        return config.serviceTypes.get(serviceType);
    }
    
    // YAML 映射的配置类
    public static class DeploymentConfig {
        private InfrastructureConfig infrastructure;
        private Map<String, ServiceTypeConfig> serviceTypes;
    }
    
    public static class InfrastructureConfig {
        private RedisConfig redis;
        private NacosConfig nacos;
        private ASBCConfig asbc;
        private HealthCheckConfig healthCheck;
    }
    
    public static class ServiceTypeConfig {
        private List<StageDefinition> stages;
    }
    
    public static class StageDefinition {
        private String name;
        private List<StepDefinition> steps;
    }
    
    public static class StepDefinition {
        private String type;                   // 步骤类型
        private Map<String, Object> config;    // 步骤配置
        private RetryPolicy retryPolicy;       // 重试策略
    }
}
```

### 4.2 抽象步骤基类

#### 4.2.1 基类设计

```java
/**
 * 可配置步骤的抽象基类
 * 
 * 职责：
 * 1. 封装通用的配置注入逻辑
 * 2. 提供 ServiceConfig + StepConfig 的双重注入
 * 3. 定义模板方法供子类实现
 */
public abstract class AbstractConfigurableStep implements StageStep {
    
    protected final String stepName;
    protected final Map<String, Object> stepConfig;     // 来自 YAML
    protected final ServiceConfig serviceConfig;        // 来自防腐层
    
    public AbstractConfigurableStep(
            String stepName,
            Map<String, Object> stepConfig,
            ServiceConfig serviceConfig) {
        
        this.stepName = Objects.requireNonNull(stepName);
        this.stepConfig = stepConfig != null ? stepConfig : Map.of();
        this.serviceConfig = Objects.requireNonNull(serviceConfig);
    }
    
    @Override
    public String getStepName() {
        return stepName;
    }
    
    /**
     * 从 stepConfig 中获取配置值
     */
    protected String getConfigValue(String key, String defaultValue) {
        Object value = stepConfig.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }
    
    protected int getConfigInt(String key, int defaultValue) {
        Object value = stepConfig.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
}
```

#### 4.2.2 具体步骤实现

**步骤 1：Redis Hash 写入**

```java
/**
 * Redis Hash 写入步骤（可复用）
 * 
 * 配置来源：
 * - YAML: hash-field（固定字段名）
 * - ServiceConfig: tenantId, configVersion, routingData（运行时数据）
 */
public class KeyValueWriteStep extends AbstractConfigurableStep {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    public KeyValueWriteStep(
            String stepName,
            Map<String, Object> stepConfig,
            ServiceConfig serviceConfig,
            RedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper) {
        
        super(stepName, stepConfig, serviceConfig);
        this.redisTemplate = Objects.requireNonNull(redisTemplate);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }
    
    @Override
    public void execute(TaskRuntimeContext ctx) throws Exception {
        // 1. 从 YAML 配置获取 Hash field
        String hashField = getConfigValue("hash-field", null);
        if (hashField == null) {
            throw new IllegalArgumentException("hash-field not configured");
        }
        
        // 2. 从 ServiceConfig 获取运行时数据
        String tenantId = serviceConfig.getTenantId();
        String hashKey = "deploy:config:" + tenantId;  // 可从 infrastructure 配置获取前缀
        
        // 3. 构建写入数据（类型安全）
        Map<String, Object> data;
        if (serviceConfig instanceof BlueGreenGatewayConfig bgConfig) {
            data = Map.of(
                "version", bgConfig.getConfigVersion(),
                "routing", bgConfig.getRoutingData()
            );
        } else if (serviceConfig instanceof PortalConfig portalConfig) {
            data = Map.of(
                "version", portalConfig.getConfigVersion(),
                "routing", portalConfig.getRoutingData()
            );
        } else {
            throw new UnsupportedOperationException("Unsupported config type");
        }
        
        // 4. 序列化为 JSON 并写入 Redis
        String jsonValue = objectMapper.writeValueAsString(data);
        redisTemplate.opsForHash().put(hashKey, hashField, jsonValue);
        
        log.info("Redis Hash written: key={}, field={}, data={}", hashKey, hashField, jsonValue);
    }
}
```

**步骤 2：Redis Pub/Sub 广播**

```java
/**
 * Redis Pub/Sub 广播步骤（可复用）
 * 
 * 配置来源：
 * - YAML: message（固定的消息内容，即 serviceName）
 * - Infrastructure: pubsub-topic（固定的 topic）
 */
public class MessageBroadcastStep extends AbstractConfigurableStep {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final DeploymentConfigLoader configLoader;
    
    public MessageBroadcastStep(
            String stepName,
            Map<String, Object> stepConfig,
            ServiceConfig serviceConfig,
            RedisTemplate<String, Object> redisTemplate,
            DeploymentConfigLoader configLoader) {
        
        super(stepName, stepConfig, serviceConfig);
        this.redisTemplate = Objects.requireNonNull(redisTemplate);
        this.configLoader = Objects.requireNonNull(configLoader);
    }
    
    @Override
    public void execute(TaskRuntimeContext ctx) throws Exception {
        // 1. 从 YAML 配置获取 message
        String message = getConfigValue("message", null);
        if (message == null) {
            throw new IllegalArgumentException("message not configured");
        }
        
        // 2. 从 Infrastructure 配置获取 topic
        String topic = configLoader.getInfrastructure()
                .getRedis()
                .getPubsubTopic();
        
        // 3. 发布消息
        redisTemplate.convertAndSend(topic, message);
        
        log.info("Redis Pub/Sub message sent: topic={}, message={}", topic, message);
    }
}
```

**步骤 3：健康检查轮询**

```java
/**
 * 端点健康检查轮询步骤（可复用）
 * 
 * 配置来源：
 * - YAML: nacos-service-name-key, validation-type, validation-rule, retry-policy
 * - Infrastructure: health-check (path, interval, max-attempts)
 * - ServiceConfig: tenantId, nacosNamespace, healthCheckPath
 */
public class EndpointPollingStep extends AbstractConfigurableStep {
    
    private final NamingService namingService;      // Nacos 服务发现
    private final RestTemplate restTemplate;        // HTTP 客户端
    private final DeploymentConfigLoader configLoader;
    
    public EndpointPollingStep(
            String stepName,
            Map<String, Object> stepConfig,
            ServiceConfig serviceConfig,
            NamingService namingService,
            RestTemplate restTemplate,
            DeploymentConfigLoader configLoader) {
        
        super(stepName, stepConfig, serviceConfig);
        this.namingService = Objects.requireNonNull(namingService);
        this.restTemplate = Objects.requireNonNull(restTemplate);
        this.configLoader = Objects.requireNonNull(configLoader);
    }
    
    @Override
    public void execute(TaskRuntimeContext ctx) throws Exception {
        // 1. 从 YAML 配置获取 Nacos 服务名
        String nacosServiceNameKey = getConfigValue("nacos-service-name-key", null);
        if (nacosServiceNameKey == null) {
            throw new IllegalArgumentException("nacos-service-name-key not configured");
        }
        
        String nacosServiceName = configLoader.getInfrastructure()
                .getNacos()
                .getServiceName(nacosServiceNameKey);
        
        // 2. 从 ServiceConfig 获取命名空间
        String namespace = null;
        String healthCheckPath = "/actuator/health";
        
        if (serviceConfig instanceof BlueGreenGatewayConfig bgConfig) {
            namespace = bgConfig.getNacosNamespace();
            healthCheckPath = bgConfig.getHealthCheckPath();
        } else if (serviceConfig instanceof PortalConfig portalConfig) {
            namespace = portalConfig.getNacosNamespace();
            healthCheckPath = portalConfig.getHealthCheckPath();
        }
        
        // 3. 从 Nacos 查询实例列表（支持降级到固定 IP）
        List<Instance> instances = null;
        try {
            instances = namingService.selectInstances(nacosServiceName, namespace, true);
        } catch (Exception e) {
            log.warn("Nacos service discovery failed, falling back to fixed instances", e);
        }
        
        // 降级到固定 IP 列表
        if (instances == null || instances.isEmpty()) {
            String serviceType = serviceConfig.getServiceType();
            List<String> fallbackIps = configLoader.getInfrastructure()
                    .getFallbackInstances()
                    .get(serviceType);
            
            if (fallbackIps == null || fallbackIps.isEmpty()) {
                throw new IllegalStateException("No available instances and no fallback configured for: " + nacosServiceName);
            }
            
            log.info("Using fallback instances for {}: {}", serviceType, fallbackIps);
            instances = fallbackIps.stream()
                    .map(this::parseInstanceFromAddress)
                    .collect(Collectors.toList());
        }
        
        // 4. 获取重试策略
        int maxAttempts = getConfigInt("retry-policy.max-attempts", 10);
        int intervalSeconds = getConfigInt("retry-policy.interval-seconds", 3);
        String validationType = getConfigValue("validation-type", "json-path");
        String validationRule = getConfigValue("validation-rule", "$.status == 'UP'");
        
        // 5. 轮询所有实例
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            boolean allHealthy = true;
            
            for (Instance instance : instances) {
                String url = buildHealthCheckUrl(instance, healthCheckPath);
                try {
                    String response = restTemplate.getForObject(url, String.class);
                    boolean valid = validateResponse(response, validationType, validationRule);
                    
                    log.info("Health check attempt {}: instance={}, valid={}", 
                            attempt, instance.getIp(), valid);
                    
                    if (!valid) {
                        allHealthy = false;
                    }
                } catch (Exception e) {
                    log.warn("Health check failed: instance={}, error={}", 
                            instance.getIp(), e.getMessage());
                    allHealthy = false;
                }
            }
            
            if (allHealthy) {
                log.info("All instances healthy after {} attempts", attempt);
                return;  // 成功
            }
            
            if (attempt < maxAttempts) {
                Thread.sleep(intervalSeconds * 1000L);
            }
        }
        
        throw new IllegalStateException("Health check failed after " + maxAttempts + " attempts");
    }
    
    private String buildHealthCheckUrl(Instance instance, String path) {
        return String.format("http://%s:%d%s", instance.getIp(), instance.getPort(), path);
    }
    
    private boolean validateResponse(String response, String validationType, String rule) {
        // 实现 JSON Path 或 HTTP Status 验证
        if ("json-path".equals(validationType)) {
            // 使用 JSONPath 库验证
            return JsonPath.read(response, rule);
        }
        return true;
    }
    
    private Instance parseInstanceFromAddress(String address) {
        // 解析 "192.168.1.10:8080" 格式
        String[] parts = address.split(":");
        Instance instance = new Instance();
        instance.setIp(parts[0]);
        instance.setPort(parts.length > 1 ? Integer.parseInt(parts[1]) : 8080);
        return instance;
    }
}
```

**步骤 4：ASBC 配置请求**

```java
/**
 * ASBC 配置请求步骤（独立实现）
 * 
 * 配置来源：
 * - YAML: endpoint-key, http-method, validation-type, validation-rule
 * - Infrastructure: asbc.fixed-instances, asbc.config-endpoint
 * - ServiceConfig: ASBCGatewayConfig (tenantId, configVersion, mediaRouting)
 */
public class ASBCConfigRequestStep extends AbstractConfigurableStep {
    
    private final RestTemplate restTemplate;
    private final DeploymentConfigLoader configLoader;
    private final ObjectMapper objectMapper;
    
    public ASBCConfigRequestStep(
            String stepName,
            Map<String, Object> stepConfig,
            ServiceConfig serviceConfig,
            RestTemplate restTemplate,
            DeploymentConfigLoader configLoader,
            ObjectMapper objectMapper) {
        
        super(stepName, stepConfig, serviceConfig);
        this.restTemplate = Objects.requireNonNull(restTemplate);
        this.configLoader = Objects.requireNonNull(configLoader);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        
        // 类型检查
        if (!(serviceConfig instanceof ASBCGatewayConfig)) {
            throw new IllegalArgumentException("ASBCConfigRequestStep requires ASBCGatewayConfig");
        }
    }
    
    @Override
    public void execute(TaskRuntimeContext ctx) throws Exception {
        ASBCGatewayConfig asbcConfig = (ASBCGatewayConfig) serviceConfig;
        
        // 1. 从 Infrastructure 配置获取固定实例列表和端点
        List<String> instances = configLoader.getInfrastructure()
                .getAsbc()
                .getFixedInstances();
        
        String endpointPath = configLoader.getInfrastructure()
                .getAsbc()
                .getConfigEndpoint();
        
        // 2. 构建请求数据（ASBC 自定义数据结构）
        Map<String, Object> requestBody = Map.of(
            "tenantId", asbcConfig.getTenantId(),
            "version", asbcConfig.getConfigVersion(),
            "mediaRouting", Map.of(
                "trunkGroup", asbcConfig.getMediaRouting().trunkGroup(),
                "calledNumberRules", asbcConfig.getMediaRouting().calledNumberRules()
            )
        );
        
        // 3. 向所有固定实例发送 POST 请求（不重试）
        for (String instance : instances) {
            String url = "http://" + instance + endpointPath;
            
            try {
                var response = restTemplate.postForEntity(url, requestBody, String.class);
                
                // 验证响应
                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("ASBC config request success: instance={}, status={}", 
                            instance, response.getStatusCode());
                } else {
                    throw new IllegalStateException("ASBC config request failed: " + 
                            response.getStatusCode());
                }
            } catch (Exception e) {
                log.error("ASBC config request failed: instance={}, error={}", 
                        instance, e.getMessage());
                throw e;  // ASBC 不支持重试，直接失败
            }
        }
    }
}
```

### 4.3 动态 Stage 工厂

```java
/**
 * 动态 Stage 工厂（配置驱动）
 * 
 * 职责：
 * 1. 读取 YAML 配置
 * 2. 使用防腐层转换 TenantConfig → ServiceConfig
 * 3. 基于配置动态创建 Stage 和 Step
 */
@Component
public class DynamicStageFactory implements StageFactory {
    
    private final ServiceConfigFactoryComposite configFactory;
    private final DeploymentConfigLoader configLoader;
    private final StepRegistry stepRegistry;
    
    public DynamicStageFactory(
            ServiceConfigFactoryComposite configFactory,
            DeploymentConfigLoader configLoader,
            StepRegistry stepRegistry) {
        
        this.configFactory = configFactory;
        this.configLoader = configLoader;
        this.stepRegistry = stepRegistry;
    }
    
    @Override
    public List<TaskStage> buildStages(TenantConfig tenantConfig) {
        // 1. 确定服务类型（从 TenantConfig 推断或显式指定）
        String serviceType = determineServiceType(tenantConfig);
        
        // 2. 通过防腐层转换为领域配置
        ServiceConfig serviceConfig = configFactory.createConfig(serviceType, tenantConfig);
        
        // 3. 从 YAML 读取服务类型定义
        ServiceTypeConfig serviceTypeConfig = configLoader.getServiceType(serviceType);
        if (serviceTypeConfig == null) {
            throw new UnsupportedOperationException("Service type not configured: " + serviceType);
        }
        
        // 4. 动态构建 Stage 列表
        List<TaskStage> stages = new ArrayList<>();
        for (StageDefinition stageDef : serviceTypeConfig.getStages()) {
            TaskStage stage = buildStage(stageDef, serviceConfig);
            stages.add(stage);
        }
        
        return stages;
    }
    
    private TaskStage buildStage(StageDefinition stageDef, ServiceConfig serviceConfig) {
        List<StageStep> steps = new ArrayList<>();
        
        for (StepDefinition stepDef : stageDef.getSteps()) {
            StageStep step = stepRegistry.createStep(stepDef, serviceConfig);
            steps.add(step);
        }
        
        return new CompositeServiceStage(stageDef.getName(), steps);
    }
    
    private String determineServiceType(TenantConfig tenantConfig) {
        // 根据 TenantConfig 的特征推断服务类型
        if (tenantConfig.getMediaRoutingConfig() != null && 
            tenantConfig.getMediaRoutingConfig().isEnabled()) {
            return "asbc-gateway";
        }
        
        // 可根据其他字段进一步推断
        // 或者在 TenantConfig 中显式添加 serviceType 字段
        return "blue-green-gateway";  // 默认
    }
}
```

### 4.4 步骤注册表

```java
/**
 * 步骤注册表（工厂 + 依赖注入）
 * 
 * 职责：
 * 1. 注册所有可用的步骤类型
 * 2. 基于配置创建步骤实例
 * 3. 注入必要的依赖（Redis, Nacos, RestTemplate 等）
 */
@Component
public class StepRegistry {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final NamingService namingService;
    private final RestTemplate restTemplate;
    private final DeploymentConfigLoader configLoader;
    private final ObjectMapper objectMapper;
    
    public StepRegistry(
            RedisTemplate<String, Object> redisTemplate,
            NamingService namingService,
            RestTemplate restTemplate,
            DeploymentConfigLoader configLoader,
            ObjectMapper objectMapper) {
        
        this.redisTemplate = redisTemplate;
        this.namingService = namingService;
        this.restTemplate = restTemplate;
        this.configLoader = configLoader;
        this.objectMapper = objectMapper;
    }
    
    /**
     * 基于步骤定义创建步骤实例
     */
    public StageStep createStep(StepDefinition stepDef, ServiceConfig serviceConfig) {
        String stepType = stepDef.getType();
        String stepName = stepType + "-" + System.currentTimeMillis();  // 唯一名称
        Map<String, Object> stepConfig = stepDef.getConfig();
        
        return switch (stepType) {
            case "key-value-write" -> new KeyValueWriteStep(
                stepName, stepConfig, serviceConfig, redisTemplate, objectMapper);
            
            case "message-broadcast" -> new MessageBroadcastStep(
                stepName, stepConfig, serviceConfig, redisTemplate, configLoader);
            
            case "endpoint-polling" -> new EndpointPollingStep(
                stepName, stepConfig, serviceConfig, namingService, restTemplate, configLoader);
            
            case "asbc-config-request" -> new ASBCConfigRequestStep(
                stepName, stepConfig, serviceConfig, restTemplate, configLoader, objectMapper);
            
            default -> throw new UnsupportedOperationException("Unknown step type: " + stepType);
        };
    }
}
```

---

## 5. 多步实现方案

### 阶段 1：基础设施准备（1-2 天）

#### 任务 1.1：创建配置模型类
- [ ] `DeploymentConfigLoader` - YAML 加载器
- [ ] `DeploymentConfig` 及嵌套配置类
- [ ] 单元测试：验证 YAML 解析正确性

#### 任务 1.2：创建 YAML 配置文件
- [ ] `deploy-stages.yml` - 完整配置
- [ ] 包含 3 种服务类型定义
- [ ] 包含基础设施配置

#### 任务 1.3：创建抽象步骤基类
- [ ] `AbstractConfigurableStep` - 基类
- [ ] 提供配置注入和模板方法
- [ ] 单元测试：验证配置注入逻辑

**验收标准**：
- ✅ YAML 文件能正确加载并解析为配置对象
- ✅ 抽象基类提供配置注入功能
- ✅ 单元测试覆盖率 > 80%

---

### 阶段 2：步骤实现（3-4 天）

#### 任务 2.1：实现 KeyValueWriteStep
- [ ] Redis Hash 写入逻辑
- [ ] 集成 BlueGreenGatewayConfig / PortalConfig
- [ ] 单元测试 + 集成测试（使用嵌入式 Redis）

#### 任务 2.2：实现 MessageBroadcastStep
- [ ] Redis Pub/Sub 发布逻辑
- [ ] 集成 Infrastructure 配置
- [ ] 单元测试 + 集成测试

#### 任务 2.3：实现 EndpointPollingStep
- [ ] Nacos 服务发现集成
- [ ] HTTP 健康检查 + 重试逻辑
- [ ] JSON Path 验证
- [ ] 单元测试 + 集成测试（Mock Nacos）

#### 任务 2.4：实现 ASBCConfigRequestStep
- [ ] 固定实例 HTTP POST
- [ ] ASBC 自定义数据结构
- [ ] 无重试逻辑
- [ ] 单元测试 + 集成测试

**验收标准**：
- ✅ 4 个步骤全部通过单元测试
- ✅ 集成测试验证与外部系统（Redis, Nacos）的交互
- ✅ 测试覆盖率 > 80%

---

### 阶段 3：工厂实现（2-3 天）

#### 任务 3.1：实现 StepRegistry
- [ ] 注册所有步骤类型
- [ ] 依赖注入（Redis, Nacos, RestTemplate）
- [ ] 单元测试：验证步骤创建逻辑

#### 任务 3.2：实现 DynamicStageFactory
- [ ] 集成 ServiceConfigFactoryComposite
- [ ] 集成 DeploymentConfigLoader
- [ ] 动态创建 Stage 和 Step
- [ ] 单元测试：验证不同服务类型的 Stage 创建

#### 任务 3.3：替换 DefaultStageFactory
- [ ] 在 Spring 配置中替换为 DynamicStageFactory
- [ ] 移除旧的硬编码逻辑
- [ ] 集成测试：验证完整流程

**验收标准**：
- ✅ DynamicStageFactory 能基于配置创建正确的 Stage
- ✅ 3 种服务类型的 Stage 组合符合预期
- ✅ 集成测试通过

---

### 阶段 4：集成测试（2-3 天）

#### 任务 4.1：蓝绿网关端到端测试
- [ ] 创建 TenantConfig（蓝绿网关）
- [ ] 调用 DynamicStageFactory.buildStages()
- [ ] 验证生成的 Stage 和 Step 顺序
- [ ] 执行 Stage 并验证 Redis 数据

#### 任务 4.2：Portal 端到端测试
- [ ] 创建 TenantConfig（Portal）
- [ ] 验证与蓝绿网关相同的流程
- [ ] 验证步骤复用逻辑

#### 任务 4.3：ASBC 网关端到端测试
- [ ] 创建 TenantConfig（ASBC）
- [ ] 验证 ASBC 独立流程
- [ ] 验证固定实例 + 无重试逻辑

#### 任务 4.4：异常场景测试
- [ ] 配置缺失
- [ ] 不支持的服务类型
- [ ] 网络故障
- [ ] 健康检查失败

**验收标准**：
- ✅ 3 种服务类型的端到端测试全部通过
- ✅ 异常场景能正确抛出异常
- ✅ 测试覆盖率 > 85%

---

### 阶段 5：文档和优化（1-2 天）

#### 任务 5.1：完善文档
- [ ] 更新 README.md
- [ ] 添加配置说明文档
- [ ] 添加步骤开发指南

#### 任务 5.2：性能优化
- [ ] 步骤执行性能分析
- [ ] 连接池优化（Redis, HTTP）
- [ ] 日志优化

#### 任务 5.3：代码审查
- [ ] 代码规范检查
- [ ] 安全漏洞扫描
- [ ] 单元测试覆盖率检查

**验收标准**：
- ✅ 文档完整且准确
- ✅ 性能满足要求（单步骤 < 100ms，健康检查除外）
- ✅ 代码审查通过

---

## 6. 数据流转示例

### 6.1 蓝绿网关部署流程

```
1. 应用层调用
   TaskDomainService.buildTaskStages(tenantConfig)
   
2. 工厂转换
   DynamicStageFactory.buildStages(tenantConfig)
   ├─> ServiceConfigFactoryComposite.createConfig("blue-green-gateway", tenantConfig)
   │   └─> BlueGreenGatewayConfigFactory.create(tenantConfig)
   │       └─> return BlueGreenGatewayConfig {
   │             tenantId: "tenant-001",
   │             configVersion: 1,
   │             nacosNamespace: "test-ns",
   │             routingData: {"key1": "value1", "key2": "value2"}
   │           }
   │
   ├─> DeploymentConfigLoader.getServiceType("blue-green-gateway")
   │   └─> return ServiceTypeConfig {
   │         stages: [
   │           { name: "deploy-stage",
   │             steps: [
   │               { type: "key-value-write", config: {"hash-field": "blue-green-gateway"} },
   │               { type: "message-broadcast", config: {"message": "blue-green-gateway"} },
   │               { type: "endpoint-polling", config: {...} }
   │             ]
   │           }
   │         ]
   │       }
   │
   └─> StepRegistry.createStep(stepDef, serviceConfig)
       ├─> new KeyValueWriteStep(
       │     stepName: "key-value-write-123456",
       │     stepConfig: {"hash-field": "blue-green-gateway"},
       │     serviceConfig: BlueGreenGatewayConfig,
       │     redisTemplate,
       │     objectMapper
       │   )
       │
       ├─> new MessageBroadcastStep(...)
       │
       └─> new EndpointPollingStep(...)

3. 返回结果
   List<TaskStage> {
     CompositeServiceStage("deploy-stage", [
       KeyValueWriteStep,
       MessageBroadcastStep,
       EndpointPollingStep
     ])
   }
```

### 6.2 步骤执行流程

```
1. KeyValueWriteStep.execute(ctx)
   ├─> getConfigValue("hash-field")  → "blue-green-gateway" (来自 YAML)
   ├─> serviceConfig.getTenantId()   → "tenant-001" (来自 TenantConfig)
   ├─> serviceConfig.getConfigVersion() → 1 (来自 TenantConfig)
   ├─> serviceConfig.getRoutingData() → {"key1": "value1", "key2": "value2"}
   ├─> 序列化: {"version": 1, "routing": {...}}
   └─> redisTemplate.opsForHash().put(
         "deploy:config:tenant-001",
         "blue-green-gateway",
         "{\"version\":1,\"routing\":{\"key1\":\"value1\"}}"
       )

2. MessageBroadcastStep.execute(ctx)
   ├─> getConfigValue("message") → "blue-green-gateway" (来自 YAML)
   ├─> configLoader.getInfrastructure().getRedis().getPubsubTopic()
   │   → "deploy.config.notify" (来自 Infrastructure 配置)
   └─> redisTemplate.convertAndSend("deploy.config.notify", "blue-green-gateway")

3. EndpointPollingStep.execute(ctx)
   ├─> getConfigValue("nacos-service-name-key") → "blue-green-gateway-service"
   ├─> configLoader.getInfrastructure().getNacos().getServiceName(...)
   │   → "blue-green-gateway-service" (来自 Infrastructure 配置)
   ├─> serviceConfig.getNacosNamespace() → "test-ns" (来自 TenantConfig)
   ├─> namingService.selectInstances("blue-green-gateway-service", "test-ns", true)
   │   → [Instance{ip="192.168.1.10", port=8080}, Instance{ip="192.168.1.11", port=8080}]
   ├─> 轮询所有实例（最多 10 次，间隔 3 秒）
   │   ├─> GET http://192.168.1.10:8080/actuator/health
   │   │   → {"status": "UP"} ✅
   │   └─> GET http://192.168.1.11:8080/actuator/health
   │       → {"status": "UP"} ✅
   └─> 所有实例健康，返回成功
```

---

## 7. 测试策略

### 7.1 单元测试

**测试范围**：
- ✅ 配置加载器（DeploymentConfigLoader）
- ✅ 抽象步骤基类（AbstractConfigurableStep）
- ✅ 4 个具体步骤（Mock 外部依赖）
- ✅ 步骤注册表（StepRegistry）
- ✅ 动态工厂（DynamicStageFactory）

**测试工具**：
- JUnit 5
- Mockito（Mock Redis, Nacos, RestTemplate）
- AssertJ

**示例测试**：

```java
@ExtendWith(MockitoExtension.class)
class KeyValueWriteStepTest {
    
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    
    @Mock
    private HashOperations<String, Object, Object> hashOps;
    
    @Mock
    private ObjectMapper objectMapper;
    
    @Test
    void shouldWriteToRedisHash() throws Exception {
        // Given
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"version\":1}");
        
        BlueGreenGatewayConfig config = new BlueGreenGatewayConfig(
            "tenant-001", 1L, "test-ns", "service-name", "/health", Map.of("k1", "v1")
        );
        
        Map<String, Object> stepConfig = Map.of("hash-field", "blue-green-gateway");
        
        KeyValueWriteStep step = new KeyValueWriteStep(
            "test-step", stepConfig, config, redisTemplate, objectMapper
        );
        
        // When
        step.execute(mock(TaskRuntimeContext.class));
        
        // Then
        verify(hashOps).put(
            eq("deploy:config:tenant-001"),
            eq("blue-green-gateway"),
            anyString()
        );
    }
}
```

### 7.2 集成测试

**测试范围**：
- ✅ Redis 集成（使用 Testcontainers 或嵌入式 Redis）
- ✅ Nacos 集成（Mock Nacos Server）
- ✅ 完整 Stage 执行流程

**测试工具**：
- Spring Boot Test
- Testcontainers（Redis, Mock HTTP Server）

**示例测试**：

```java
@SpringBootTest
@Testcontainers
class DynamicStageFactoryIntegrationTest {
    
    @Container
    static GenericContainer redis = new GenericContainer("redis:7.0")
        .withExposedPorts(6379);
    
    @Autowired
    private DynamicStageFactory stageFactory;
    
    @Test
    void shouldCreateStagesForBlueGreenGateway() {
        // Given
        TenantConfig tenantConfig = createBlueGreenGatewayConfig();
        
        // When
        List<TaskStage> stages = stageFactory.buildStages(tenantConfig);
        
        // Then
        assertThat(stages).hasSize(1);
        assertThat(stages.get(0).getStageName()).isEqualTo("deploy-stage");
        assertThat(stages.get(0).getSteps()).hasSize(3);
        assertThat(stages.get(0).getSteps().get(0)).isInstanceOf(KeyValueWriteStep.class);
        assertThat(stages.get(0).getSteps().get(1)).isInstanceOf(MessageBroadcastStep.class);
        assertThat(stages.get(0).getSteps().get(2)).isInstanceOf(EndpointPollingStep.class);
    }
}
```

### 7.3 端到端测试

**测试范围**：
- ✅ 蓝绿网关完整部署流程
- ✅ Portal 完整部署流程
- ✅ ASBC 网关完整部署流程
- ✅ 异常场景（配置缺失、网络故障）

**测试工具**：
- Spring Boot Test
- WireMock（Mock HTTP 端点）
- Awaitility（异步验证）

---

## 8. 风险评估

### 8.1 技术风险

| 风险项              | 影响 | 概率 | 缓解措施                                    |
|--------------------|-----|-----|-------------------------------------------|
| Nacos 依赖         | 高   | 中   | ✅ 支持降级到固定 IP 列表，配置在 YAML 中      |
| Redis 连接池耗尽    | 中   | 低   | 配置合理的连接池参数，监控连接数               |
| YAML 配置错误      | 高   | 中   | 启动时校验配置，提供配置模板和文档             |
| 步骤执行超时       | 中   | 中   | 配置合理的超时时间，提供手动干预机制           |

### 8.2 性能风险

| 风险项              | 影响 | 概率 | 缓解措施                                    |
|--------------------|-----|-----|-------------------------------------------|
| 健康检查耗时长     | 中   | 高   | 优化轮询间隔，支持并发健康检查                |
| Redis 写入延迟     | 低   | 低   | 使用 Pipeline 批量操作                     |
| Nacos 查询延迟     | 中   | 低   | 启用本地缓存，定期刷新                      |

### 8.3 兼容性风险

| 风险项              | 影响 | 概率 | 缓解措施                                    |
|--------------------|-----|-----|-------------------------------------------|
| 现有 Stage 接口变更 | 高   | 低   | 保持接口兼容，提供适配器                     |
| YAML 格式升级      | 中   | 中   | 版本化配置，提供迁移工具                     |

---

## 9. 附录

### 9.1 完整文件清单

#### 生产代码（新增）

```
src/main/java/xyz/firestige/deploy/infrastructure/
├── config/
│   ├── DeploymentConfigLoader.java              # YAML 加载器
│   ├── model/
│   │   ├── DeploymentConfig.java                # 配置根对象
│   │   ├── InfrastructureConfig.java            # 基础设施配置
│   │   ├── ServiceTypeConfig.java               # 服务类型配置
│   │   ├── StageDefinition.java                 # Stage 定义
│   │   └── StepDefinition.java                  # Step 定义
│   └── validation/
│       └── ConfigValidator.java                 # 配置校验器
│
├── execution/
│   ├── stage/
│   │   ├── DynamicStageFactory.java             # 动态工厂（替换 DefaultStageFactory）
│   │   ├── StepRegistry.java                    # 步骤注册表
│   │   ├── AbstractConfigurableStep.java        # 抽象步骤基类
│   │   └── steps/
│   │       ├── KeyValueWriteStep.java           # Redis Hash 写入
│   │       ├── MessageBroadcastStep.java        # Redis Pub/Sub
│   │       ├── EndpointPollingStep.java         # 健康检查轮询
│   │       └── ASBCConfigRequestStep.java       # ASBC HTTP POST
│   └── util/
│       └── JsonPathValidator.java               # JSON Path 验证工具

src/main/resources/
└── deploy-stages.yml                            # YAML 配置文件
```

#### 测试代码（新增）

```
src/test/java/xyz/firestige/deploy/infrastructure/
├── config/
│   └── DeploymentConfigLoaderTest.java
│
└── execution/
    └── stage/
        ├── DynamicStageFactoryTest.java
        ├── DynamicStageFactoryIntegrationTest.java
        ├── StepRegistryTest.java
        └── steps/
            ├── KeyValueWriteStepTest.java
            ├── MessageBroadcastStepTest.java
            ├── EndpointPollingStepTest.java
            └── ASBCConfigRequestStepTest.java
```

### 9.2 依赖清单

#### Maven 依赖（需添加）

```xml
<!-- JSON Path 验证 -->
<dependency>
    <groupId>com.jayway.jsonpath</groupId>
    <artifactId>json-path</artifactId>
    <version>2.8.0</version>
</dependency>

<!-- YAML 解析（Spring Boot 已包含） -->
<dependency>
    <groupId>org.yaml</groupId>
    <artifactId>snakeyaml</artifactId>
</dependency>

<!-- Nacos 客户端 -->
<dependency>
    <groupId>com.alibaba.nacos</groupId>
    <artifactId>nacos-client</artifactId>
    <version>2.2.3</version>
</dependency>

<!-- 测试容器 -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

### 9.3 配置示例（完整版）

参见 [核心组件设计 - 4.1.1 配置文件结构](#411-配置文件结构)

### 9.4 关键接口变更

#### StageFactory 接口（保持兼容）

```java
// 接口保持不变，实现类从 DefaultStageFactory 切换到 DynamicStageFactory
public interface StageFactory {
    List<TaskStage> buildStages(TenantConfig cfg);
}
```

#### Spring 配置变更

```java
@Configuration
public class StageFactoryConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public StageFactory stageFactory(
            ServiceConfigFactoryComposite configFactory,
            DeploymentConfigLoader configLoader,
            StepRegistry stepRegistry) {
        
        // 替换为动态工厂
        return new DynamicStageFactory(configFactory, configLoader, stepRegistry);
    }
}
```

---

## 总结

本实现方案通过以下设计实现了 StageFactory 的动态编排：

1. **防腐层隔离**：ServiceConfigFactory 将 TenantConfig 转换为领域配置
2. **配置驱动**：YAML 配置定义服务类型和步骤组合
3. **步骤复用**：抽象步骤基类 + 4 个具体实现支持多服务共享
4. **类型安全**：利用 Java 类型系统保证配置和运行时数据的正确性
5. **易扩展**：新增服务类型只需修改 YAML 配置，无需改代码

**实施周期**：10-15 个工作日  
**风险等级**：中  
**优先级**：高

---

**评审检查点**：
- [ ] 架构设计是否合理
- [ ] 配置分离原则是否清晰
- [ ] 步骤复用性是否充分
- [ ] 测试策略是否完整
- [ ] 实施计划是否可行
- [ ] 风险评估是否全面
