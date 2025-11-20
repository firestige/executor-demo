# 模板变量替换实现方案

## 概述

实现了一个通用的模板变量替换机制，允许在 `deploy-stages.yml` 配置中使用占位符（如 `{tenantId}`），在运行时动态替换为实际值。

## 需求背景

在蓝绿网关和 Portal 部署流程中：
1. **健康检查路径**需要包含租户 ID：`/actuator/bg-sdk/{tenantId}`
2. **Redis 广播消息**需要发送 JSON 格式，包含租户 ID：`{"tenantId":"{tenantId}","appName":"icc-bg-gateway"}`

## 架构设计

### 核心原则
- **配置转换在 Registry/Factory 完成**：Step 只负责执行，不关心配置转换细节
- **统一处理**：所有模板替换逻辑集中在 `TemplateResolver` 中
- **可复用**：模板解析器可在其他场景复用（如动态 SQL、消息模板等）

### 职责划分

| 组件 | 职责 |
|------|------|
| `TemplateResolver` | 通用模板解析逻辑（递归处理 Map、List、String） |
| `VariableContextBuilder` | 从业务对象（`ServiceConfig`）提取变量上下文 |
| `StepRegistry` | 委托 `TemplateResolver` 解析步骤配置，创建 Step 实例 |
| `BlueGreenGatewayConfigFactory` | 在创建 `ServiceConfig` 时替换健康检查路径中的占位符 |
| `PortalConfigFactory` | 同上 |
| `Step`（如 `MessageBroadcastStep`） | 直接使用已解析的配置值 |

## 实现细节

### 1. 模板解析器 (`TemplateResolver`)

```java
@Component
public class TemplateResolver {
    // 正则模式：只匹配 {variableName} 格式（变量名仅包含字母、数字、下划线、连字符）
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_-]+)\\}");

    /**
     * 递归解析对象中的所有模板变量
     */
    public Object resolve(Object obj, Map<String, String> variables) {
        if (obj instanceof String) return resolveString((String) obj, variables);
        if (obj instanceof Map) return resolveMap((Map<?, ?>) obj, variables);
        if (obj instanceof List) return resolveList((List<?>) obj, variables);
        return obj;
    }
}
```

**特性**：
- 支持递归处理嵌套配置（Map 中的 Map）
- 严格的占位符模式，避免误匹配 JSON 字符串中的花括号
- 缺失变量时抛出清晰的异常

### 2. 变量上下文构建器 (`VariableContextBuilder`)

```java
@Component
public class VariableContextBuilder {
    public Map<String, String> buildContext(ServiceConfig serviceConfig) {
        Map<String, String> variables = new HashMap<>();
        variables.put("tenantId", serviceConfig.getTenantId().getValue());
        variables.put("serviceType", serviceConfig.getServiceType());
        return variables;
    }
}
```

**当前支持的变量**：
- `{tenantId}`: 租户 ID
- `{serviceType}`: 服务类型（如 `blue-green-gateway`）

### 3. StepRegistry 中的模板替换

```java
@Component
public class StepRegistry {
    @Autowired
    private TemplateResolver templateResolver;
    
    @Autowired
    private VariableContextBuilder variableContextBuilder;

    public StageStep createStep(StepDefinition stepDef, ServiceConfig serviceConfig) {
        // 1. 构建变量上下文
        Map<String, String> variables = variableContextBuilder.buildContext(serviceConfig);
        
        // 2. 解析配置中的模板变量
        Map<String, Object> resolvedConfig = (Map<String, Object>) templateResolver.resolve(
            stepDef.getConfig(),
            variables
        );
        
        // 3. 创建 Step 实例（传入已解析的配置）
        return createStepInstance(stepType, resolvedConfig, serviceConfig);
    }
}
```

### 4. ConfigFactory 中的模板替换

```java
@Component
public class BlueGreenGatewayConfigFactory {
    private final DeploymentConfigLoader configLoader;
    private final TemplateResolver templateResolver;

    private String extractHealthCheckPath(TenantConfig tenantConfig) {
        // 1. 获取路径模板（可能包含 {tenantId}）
        String pathTemplate = configLoader.getInfrastructure()
                .getHealthCheck()
                .getDefaultPath(); // "/actuator/bg-sdk/{tenantId}"
        
        // 2. 替换模板变量
        Map<String, String> variables = Map.of(
            "tenantId", tenantConfig.getTenantId().getValue()
        );
        
        return (String) templateResolver.resolve(pathTemplate, variables);
        // 返回: "/actuator/bg-sdk/tenant-001"
    }
}
```

## 配置示例

### deploy-stages.yml

```yaml
infrastructure:
  healthCheck:
    defaultPath: "/actuator/bg-sdk/{tenantId}"  # 模板

services:
  blue-green-gateway:
    stages:
      - name: deploy-stage
        steps:
          # Step 1: Redis Hash Write
          - type: key-value-write
            config:
              hashField: "icc-bg-gateway"

          # Step 2: Redis Pub/Sub Broadcast（使用模板）
          - type: message-broadcast
            config:
              message: '{"tenantId":"{tenantId}","appName":"icc-bg-gateway"}'
          
          # Step 3: Health Check Polling
          - type: endpoint-polling
            config:
              nacosServiceNameKey: "blueGreenGatewayService"
              validationType: "json-path"
              validationRule: "$.version"
              expectedValue: "1"

  portal:
    stages:
      - name: deploy-stage
        steps:
          - type: key-value-write
            config:
              hashField: "portal"
          
          - type: message-broadcast
            config:
              message: '{"tenantId":"{tenantId}","appName":"portal"}'
          
          - type: endpoint-polling
            config:
              nacosServiceNameKey: "portalService"
```

## 执行流程

```
1. 应用层创建 TenantConfig
   ↓
2. BlueGreenGatewayConfigFactory.create()
   - 从模板 "/actuator/bg-sdk/{tenantId}" 
   - 替换为 "/actuator/bg-sdk/tenant-001"
   - 创建 BlueGreenGatewayConfig（包含已替换的路径）
   ↓
3. DynamicStageFactory 获取 deploy-stage 配置
   ↓
4. StepRegistry.createStep()
   - 读取配置：message: '{"tenantId":"{tenantId}","appName":"icc-bg-gateway"}'
   - 替换为：message: '{"tenantId":"tenant-001","appName":"icc-bg-gateway"}'
   - 创建 MessageBroadcastStep（传入已替换的配置）
   ↓
5. MessageBroadcastStep.execute()
   - 直接读取 message 配置
   - 发送到 Redis：{"tenantId":"tenant-001","appName":"icc-bg-gateway"}
```

## 关键改动

### 新增文件
1. `TemplateResolver.java` - 模板解析器
2. `VariableContextBuilder.java` - 变量上下文构建器
3. `TemplateResolverTest.java` - 单元测试
4. `VariableContextBuilderTest.java` - 单元测试

### 修改文件
1. `StepRegistry.java` - 注入模板解析器，在创建 Step 前解析配置
2. `BlueGreenGatewayConfigFactory.java` - 注入依赖，替换健康检查路径模板
3. `PortalConfigFactory.java` - 同上
4. `EndpointPollingStep.java` - 移除内部的 `{tenantId}` 替换逻辑
5. `deploy-stages.yml` - 更新 message 配置为 JSON 模板格式

### 更新测试
1. `ServiceConfigFactoryCompositeTest.java` - 添加 mock 依赖
2. `DynamicStageFactoryIntegrationTest.java` - 添加模板解析器依赖

## 测试结果

### 单元测试
```bash
mvn test -Dtest=TemplateResolverTest,VariableContextBuilderTest
```

**测试覆盖**：
- ✅ 单占位符替换
- ✅ 多占位符替换
- ✅ JSON 模板替换
- ✅ 嵌套 Map 递归替换
- ✅ List 中的元素替换
- ✅ 缺失变量异常处理

### 集成测试
```bash
mvn test -Dtest=DynamicStageFactoryIntegrationTest
```

**测试通过**：6/6
- ✅ 蓝绿网关配置生成
- ✅ Portal 配置生成
- ✅ ASBC 网关配置生成
- ✅ 多服务组合配置

## 扩展性

### 添加新变量
在 `VariableContextBuilder` 中扩展：

```java
public Map<String, String> buildContext(ServiceConfig serviceConfig) {
    Map<String, String> variables = new HashMap<>();
    variables.put("tenantId", serviceConfig.getTenantId().getValue());
    variables.put("serviceType", serviceConfig.getServiceType());
    variables.put("timestamp", String.valueOf(System.currentTimeMillis()));  // 新变量
    variables.put("environment", System.getProperty("env", "prod"));         // 新变量
    return variables;
}
```

### 添加新的占位符位置
只需在 YAML 配置中使用 `{variableName}` 格式，无需修改代码：

```yaml
- type: custom-step
  config:
    url: "http://{serviceName}.{environment}.example.com/{tenantId}"
    timeout: 3000
```

## 优势

1. **配置灵活**：在 YAML 中直接定义模板，无需修改代码
2. **职责清晰**：Step 只执行，不处理模板逻辑
3. **统一处理**：所有模板替换在 `StepRegistry` 统一完成
4. **可扩展**：支持添加更多变量，支持多种占位符位置
5. **类型安全**：运行时校验变量是否存在，避免空值
6. **可复用**：`TemplateResolver` 可在其他场景复用

## 注意事项

1. **占位符命名规范**：只支持字母、数字、下划线、连字符（`[a-zA-Z0-9_-]+`）
2. **变量必须存在**：引用的变量必须在 `VariableContextBuilder` 中定义，否则抛出异常
3. **嵌套花括号**：JSON 字符串中的 `{}` 不会被误识别为占位符
4. **执行顺序**：
   - `ConfigFactory` 中替换健康检查路径（创建 `ServiceConfig` 时）
   - `StepRegistry` 中替换步骤配置（创建 `Step` 实例时）

## 后续优化建议

1. **缓存优化**：对于相同的模板和变量组合，可以缓存解析结果
2. **默认值支持**：支持 `{variableName:defaultValue}` 语法
3. **表达式支持**：支持简单表达式，如 `{tenantId.toUpperCase()}`
4. **验证工具**：提供配置验证工具，在启动时检查所有模板是否有效

## 总结

通过引入 `TemplateResolver` 和 `VariableContextBuilder`，实现了一个通用的、可扩展的模板变量替换机制。该机制遵循单一职责原则，保持了代码的清晰性和可维护性，同时为未来的扩展提供了灵活性。

