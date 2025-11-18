# Stage Factory 使用指南

## 快速开始

### 1. 基础用法

```java
@Service
public class DeploymentService {
    
    @Autowired
    private DynamicStageFactory stageFactory;
    
    public void deployTenant(TenantConfig tenantConfig) {
        // 构建 Stage 列表
        List<TaskStage> stages = stageFactory.buildStages(tenantConfig);
        
        // 执行部署流程
        for (TaskStage stage : stages) {
            StageResult result = stage.execute(runtimeContext);
            
            if (!result.isSuccess()) {
                log.error("Stage execution failed: {}", result.getMessage());
                stage.rollback(runtimeContext);
                break;
            }
        }
    }
}
```

### 2. 蓝绿网关部署示例

```java
// 准备配置
TenantConfig config = new TenantConfig();
config.setTenantId("tenant-001");
config.setNacosNameSpace("production");

// 设置网络端点
List<NetworkEndpoint> endpoints = new ArrayList<>();
NetworkEndpoint ep1 = new NetworkEndpoint();
ep1.setKey("gateway.host");
ep1.setValue("192.168.1.100");
endpoints.add(ep1);
config.setNetworkEndpoints(endpoints);

// 执行部署（自动识别为 blue-green-gateway）
List<TaskStage> stages = stageFactory.buildStages(config);
// stages 包含 3 个步骤：
// 1. KeyValueWriteStep (Redis Hash 写入)
// 2. MessageBroadcastStep (Redis Pub/Sub 广播)
// 3. EndpointPollingStep (健康检查)
```

### 3. ASBC 网关部署示例

```java
TenantConfig config = new TenantConfig();
config.setTenantId("tenant-002");

// 设置媒体路由配置（触发 ASBC 模式）
MediaRoutingConfig mediaRouting = new MediaRoutingConfig();
mediaRouting.setTrunkGroupId("trunk-001");
mediaRouting.setRoutingRules("rules-001");
config.setMediaRoutingConfig(mediaRouting);

// 执行部署（自动识别为 asbc-gateway）
List<TaskStage> stages = stageFactory.buildStages(config);
// stages 包含 1 个步骤：
// 1. ASBCConfigRequestStep (HTTP POST 配置请求)
```

## YAML 配置

### 基础设施配置

```yaml
# src/main/resources/deploy-stages.yml
infrastructure:
  redis:
    hashKeyPrefix: "deploy:config:"
    pubsubTopic: "deploy.config.notify"
  
  nacos:
    services:
      blueGreenGatewayService: "blue-green-gateway-service"
      portalService: "portal-service"
  
  # 服务发现降级（Nacos 不可用时使用）
  fallbackInstances:
    blueGreenGateway:
      - "192.168.1.10:8080"
      - "192.168.1.11:8080"
    portal:
      - "192.168.1.20:8080"
  
  # ASBC 固定实例
  asbc:
    fixedInstances:
      - "192.168.1.100:8080"
    configEndpoint: "/api/v1/config"
  
  # 健康检查配置
  healthCheck:
    defaultPath: "/actuator/health"
    intervalSeconds: 3
    maxAttempts: 10
```

### 服务类型配置

```yaml
serviceTypes:
  blue-green-gateway:
    stages:
      - name: deploy-stage
        steps:
          - type: key-value-write
            config:
              hashField: "blue-green-gateway"
          
          - type: message-broadcast
            config:
              message: "blue-green-gateway"
          
          - type: endpoint-polling
            config:
              nacosServiceNameKey: "blueGreenGatewayService"
              validationType: "json-path"
              validationRule: "$.status"
              expectedValue: "UP"
            retryPolicy:
              maxAttempts: 10
              intervalSeconds: 3
```

## 扩展开发

### 添加新服务类型

**步骤 1**: 创建领域配置

```java
package xyz.firestige.deploy.domain.stage.config;

public class NewServiceConfig implements ServiceConfig {
    private final String tenantId;
    private final Map<String, String> customData;
    
    public NewServiceConfig(String tenantId, Map<String, String> customData) {
        this.tenantId = Objects.requireNonNull(tenantId);
        this.customData = Collections.unmodifiableMap(customData);
    }
    
    @Override
    public String getServiceType() {
        return "new-service";
    }
    
    @Override
    public String getTenantId() {
        return tenantId;
    }
    
    public Map<String, String> getCustomData() {
        return customData;
    }
}
```

**步骤 2**: 创建配置工厂

```java
package xyz.firestige.deploy.domain.stage.factory;

@Component
public class NewServiceConfigFactory implements ServiceConfigFactory {
    
    @Override
    public boolean supports(String serviceType) {
        return "new-service".equals(serviceType);
    }
    
    @Override
    public ServiceConfig create(TenantConfig tenantConfig) {
        // 转换逻辑
        Map<String, String> customData = buildCustomData(tenantConfig);
        return new NewServiceConfig(
            tenantConfig.getTenantId(),
            customData
        );
    }
    
    private Map<String, String> buildCustomData(TenantConfig config) {
        // 自定义转换逻辑
        return Map.of("key", "value");
    }
}
```

**步骤 3**: 添加 YAML 配置

```yaml
serviceTypes:
  new-service:
    stages:
      - name: deploy-stage
        steps:
          - type: custom-step
            config:
              customParam: "value"
```

**步骤 4**: 更新服务类型识别

```java
// DynamicStageFactory.java
private String determineServiceType(TenantConfig config) {
    if (config.getCustomFlag() != null) {
        return "new-service";
    }
    if (config.getMediaRoutingConfig() != null 
        && config.getMediaRoutingConfig().isEnabled()) {
        return "asbc-gateway";
    }
    return "blue-green-gateway";  // 默认
}
```

### 添加新 Step 类型

**步骤 1**: 实现 Step 类

```java
package xyz.firestige.deploy.infrastructure.execution.stage.steps;

public class CustomStep extends AbstractConfigurableStep {
    
    private final CustomDependency dependency;
    
    public CustomStep(
            StepDefinition stepConfig,
            ServiceConfig serviceConfig,
            CustomDependency dependency) {
        super(stepConfig, serviceConfig);
        this.dependency = Objects.requireNonNull(dependency);
    }
    
    @Override
    protected StepResult doExecute(TaskRuntimeContext ctx) {
        try {
            // 自定义逻辑
            String result = dependency.performAction(serviceConfig);
            log.info("Custom step executed: {}", result);
            return StepResult.success();
        } catch (Exception e) {
            log.error("Custom step failed", e);
            return StepResult.failed(e.getMessage());
        }
    }
    
    @Override
    public void rollback(TaskRuntimeContext ctx) {
        // 回滚逻辑
        log.info("Rolling back custom step");
        dependency.rollbackAction(serviceConfig);
    }
    
    @Override
    public String getStepName() {
        return "custom-step-" + serviceConfig.getTenantId();
    }
}
```

**步骤 2**: 注册到 StepRegistry

```java
// StepRegistry.java
public StageStep createStep(
        String stepType,
        StepDefinition stepDef,
        ServiceConfig serviceConfig) {
    
    return switch (stepType) {
        case "key-value-write" -> new KeyValueWriteStep(...);
        case "message-broadcast" -> new MessageBroadcastStep(...);
        case "endpoint-polling" -> new EndpointPollingStep(...);
        case "asbc-config-request" -> new ASBCConfigRequestStep(...);
        case "custom-step" -> new CustomStep(stepDef, serviceConfig, customDependency);
        default -> throw new IllegalArgumentException(
            "Unknown step type: " + stepType);
    };
}
```

**步骤 3**: 配置依赖注入

```java
@Configuration
public class CustomStepConfiguration {
    
    @Bean
    public CustomDependency customDependency() {
        return new CustomDependency();
    }
}
```

## 配置管理

### 环境分离

```yaml
# application-dev.yml
deploy:
  config:
    location: classpath:deploy-stages-dev.yml

# application-prod.yml
deploy:
  config:
    location: classpath:deploy-stages-prod.yml
```

### 敏感信息处理

```yaml
infrastructure:
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}  # 环境变量注入
```

## 监控与日志

### 日志级别配置

```yaml
logging:
  level:
    xyz.firestige.deploy.infrastructure.execution.stage: DEBUG
    xyz.firestige.deploy.domain.stage.factory: INFO
```

### 关键日志输出

```
[INFO] DynamicStageFactory - Building stages for service type: blue-green-gateway (tenant=tenant-001)
[INFO] DynamicStageFactory - Created stage: name=deploy-stage, steps=3
[INFO] DynamicStageFactory - Total stages built: 1
[WARN] StepRegistry - Nacos NamingService not configured, will use fallback instances
[INFO] EndpointPollingStep - Health check passed for instance: 192.168.1.10:8080
```

## 故障排查

### 常见问题

**问题 1**: `IllegalArgumentException: nacosNameSpace cannot be null`

**原因**: TenantConfig 缺少必需字段

**解决**:
```java
config.setNacosNameSpace("production");  // 必需
```

---

**问题 2**: `UnsupportedOperationException: Service type not configured: xxx`

**原因**: YAML 中未定义该服务类型

**解决**:
```yaml
serviceTypes:
  xxx:  # 添加服务类型定义
    stages: [...]
```

---

**问题 3**: Nacos 服务发现失败但程序继续运行

**原因**: 自动降级到固定 IP

**日志**:
```
[WARN] Nacos NamingService not configured, will use fallback instances
[INFO] Using fallback instances: [192.168.1.10:8080, 192.168.1.11:8080]
```

**解决**: 检查 Nacos 连接或更新 fallbackInstances 配置

---

**问题 4**: Redis 操作超时

**原因**: Redis 连接池配置不当

**解决**:
```yaml
spring:
  redis:
    timeout: 5000ms
    lettuce:
      pool:
        max-active: 20
        max-idle: 10
```

## 性能优化

### 并发健康检查

```java
// EndpointPollingStep 已实现并发检查
List<CompletableFuture<Boolean>> futures = instances.stream()
    .map(instance -> CompletableFuture.supplyAsync(
        () -> checkHealth(instance), executor))
    .toList();

CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
    .join();
```

### 配置缓存

```java
// DeploymentConfigLoader 使用 @PostConstruct 一次性加载
private DeploymentConfig config;  // 缓存配置对象

@PostConstruct
public void loadConfig() {
    this.config = loadFromYaml();  // 启动时加载一次
}
```

## 最佳实践

### ✅ 推荐做法

1. **配置外部化**: 使用环境变量或配置中心
2. **日志完善**: 关键操作添加 INFO/ERROR 日志
3. **异常处理**: 捕获具体异常类型
4. **测试完整**: 单元测试 + 集成测试
5. **文档更新**: 代码变更同步更新文档

### ❌ 避免做法

1. **硬编码**: IP、端口、密码等
2. **忽略异常**: 捕获后不处理
3. **过度依赖**: 核心逻辑依赖外部服务
4. **配置分散**: 配置文件散布各处
5. **缺少日志**: 关键操作无日志记录

## 参考资料

- [完整实现报告](./STAGE_FACTORY_IMPLEMENTATION_COMPLETE.md)
- [设计方案](./STAGE_FACTORY_IMPLEMENTATION_PLAN.md)
- [Spring Boot 文档](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Jackson YAML](https://github.com/FasterXML/jackson-dataformats-text/tree/master/yaml)

---

**文档版本**: v1.0  
**最后更新**: 2025-11-19
