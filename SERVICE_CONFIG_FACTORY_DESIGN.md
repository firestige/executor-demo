# 服务配置工厂设计文档

## 1. 设计目标

通过工厂模式实现 **防腐层（Anti-Corruption Layer, ACL）**，隔离应用层的 `TenantConfig` 与领域层的服务配置模型，防止外部数据结构污染领域模型。

### 核心职责
1. **数据转换**：将应用层 DTO (`TenantConfig`) 转换为领域层配置 (`ServiceConfig`)
2. **类型隔离**：不同服务类型使用独立的配置模型，保证类型安全
3. **验证封装**：在工厂层完成数据完整性验证，领域对象保证不变性
4. **扩展性**：新增服务类型只需实现工厂接口，无需修改现有代码

---

## 2. 架构设计

### 2.1 核心接口

```
ServiceConfigFactory (接口)
├── supports(serviceType: String): boolean
└── create(tenantConfig: TenantConfig): ServiceConfig
```

### 2.2 实现类

```
ServiceConfigFactoryComposite (组合器)
├── BlueGreenGatewayConfigFactory
├── PortalConfigFactory
└── ASBCGatewayConfigFactory
```

### 2.3 配置模型

```
ServiceConfig (标记接口)
├── BlueGreenGatewayConfig
├── PortalConfig
└── ASBCGatewayConfig
```

---

## 3. 数据转换映射

### 3.1 蓝绿网关 (BlueGreenGatewayConfig)

| 领域模型字段          | TenantConfig 来源                     | 转换规则                                    |
|---------------------|--------------------------------------|-------------------------------------------|
| tenantId            | TenantConfig.tenantId                | 直接映射                                   |
| configVersion       | TenantConfig.deployUnit.version      | 直接映射                                   |
| nacosNamespace      | TenantConfig.nacosNameSpace          | 直接映射                                   |
| nacosServiceName    | 配置文件（硬编码）                     | 固定值："blue-green-gateway-service"       |
| healthCheckPath     | TenantConfig.healthCheckEndpoints[0] | 取第一个，默认值："/actuator/health"        |
| routingData         | TenantConfig.networkEndpoints        | 提取 key-value 对为 Map<String, String>   |

**转换逻辑**：
```java
Map<String, String> routingData = new HashMap<>();
for (NetworkEndpoint endpoint : networkEndpoints) {
    if (endpoint.getKey() != null && endpoint.getValue() != null) {
        routingData.put(endpoint.getKey(), endpoint.getValue());
    }
}
```

### 3.2 Portal (PortalConfig)

与蓝绿网关转换逻辑完全相同，仅服务标识不同：
- `serviceType = "portal"`
- `nacosServiceName = "portal-service"`

### 3.3 ASBC 网关 (ASBCGatewayConfig)

| 领域模型字段          | TenantConfig 来源                     | 转换规则                                    |
|---------------------|--------------------------------------|-------------------------------------------|
| tenantId            | TenantConfig.tenantId                | 直接映射                                   |
| configVersion       | TenantConfig.deployUnit.version      | 直接映射                                   |
| fixedInstances      | 配置文件（硬编码）                     | 固定列表：["192.168.1.100:8080", ...]      |
| configEndpoint      | 配置文件（硬编码）                     | 固定值："/api/v1/config"                   |
| mediaRouting        | TenantConfig.mediaRoutingConfig      | 转换为领域值对象 MediaRouting              |

**转换逻辑**：
```java
MediaRoutingConfig source = tenantConfig.getMediaRoutingConfig();
if (source == null || !source.isEnabled()) {
    throw new IllegalArgumentException("mediaRoutingConfig must be enabled for ASBC");
}

ASBCGatewayConfig.MediaRouting target = new ASBCGatewayConfig.MediaRouting(
    source.trunkGroup(),
    source.calledNumberRules()
);
```

---

## 4. 使用示例

### 4.1 注入组合工厂

```java
@Service
public class DeploymentService {
    
    private final ServiceConfigFactoryComposite configFactory;
    
    public DeploymentService(ServiceConfigFactoryComposite configFactory) {
        this.configFactory = configFactory;
    }
}
```

### 4.2 创建服务配置

```java
// 场景 1：部署蓝绿网关
TenantConfig tenantConfig = getTenantConfig();
ServiceConfig config = configFactory.createConfig("blue-green-gateway", tenantConfig);

// 场景 2：部署 Portal
ServiceConfig portalConfig = configFactory.createConfig("portal", tenantConfig);

// 场景 3：部署 ASBC 网关
ServiceConfig asbcConfig = configFactory.createConfig("asbc-gateway", tenantConfig);
```

### 4.3 类型安全的转换

```java
if (config instanceof BlueGreenGatewayConfig bgConfig) {
    // 访问蓝绿网关特有的方法
    String redisKey = bgConfig.getRedisHashKey();
    String pubSubMessage = bgConfig.getRedisPubSubMessage();
    Map<String, String> routing = bgConfig.getRoutingData();
}

if (config instanceof ASBCGatewayConfig asbcConfig) {
    // 访问 ASBC 网关特有的方法
    List<String> instances = asbcConfig.getFixedInstances();
    MediaRouting routing = asbcConfig.getMediaRouting();
}
```

---

## 5. 防腐层设计原则

### 5.1 隔离外部模型

**应用层 DTO (TenantConfig)**：
- 来源：外部 API、数据库持久化
- 特点：可变、宽松验证、业务无关字段多
- 生命周期：跨层传输

**领域层配置 (ServiceConfig)**：
- 来源：工厂转换
- 特点：不可变、强验证、业务相关字段
- 生命周期：领域服务内部

### 5.2 单向依赖

```
应用层 (TenantConfig)
    ↓ (工厂转换)
领域层 (ServiceConfig)
```

领域层不依赖应用层的数据结构，保证领域模型的纯粹性。

### 5.3 验证边界

**工厂层验证**：
- 数据完整性（非空、格式）
- 业务规则（ASBC 必须有 MediaRouting）

**领域对象验证**：
- 不变性约束（final 字段）
- 逻辑完整性（构造器验证）

---

## 6. 扩展指南

### 6.1 新增服务类型

1. **创建领域配置类**：
```java
public class NewServiceConfig implements ServiceConfig {
    private final String tenantId;
    // ... 其他字段
    
    @Override
    public String getServiceType() {
        return "new-service";
    }
}
```

2. **创建工厂实现**：
```java
@Component
public class NewServiceConfigFactory implements ServiceConfigFactory {
    
    @Override
    public boolean supports(String serviceType) {
        return "new-service".equals(serviceType);
    }
    
    @Override
    public ServiceConfig create(TenantConfig tenantConfig) {
        // 转换逻辑
        return new NewServiceConfig(...);
    }
}
```

3. **自动注册**：
Spring 自动扫描 `@Component`，无需手动注册到组合器。

### 6.2 修改转换逻辑

只需修改对应的工厂类，不影响其他服务类型：
```java
@Component
public class BlueGreenGatewayConfigFactory implements ServiceConfigFactory {
    
    @Override
    public ServiceConfig create(TenantConfig tenantConfig) {
        // 新增字段提取
        String newField = tenantConfig.getNewField();
        
        return new BlueGreenGatewayConfig(
            // ... 原有字段
            newField  // 新增字段
        );
    }
}
```

---

## 7. 设计优势

### 7.1 防腐层隔离
- ✅ 领域模型不受外部数据结构变化影响
- ✅ 应用层 DTO 变更只需修改工厂
- ✅ 领域对象保持纯粹和稳定

### 7.2 类型安全
- ✅ 编译期检查服务配置类型
- ✅ 避免运行时类型转换错误
- ✅ IDE 自动补全和重构支持

### 7.3 扩展性
- ✅ 新增服务类型无需修改现有代码（开闭原则）
- ✅ 工厂实现相互独立（单一职责）
- ✅ 支持不同服务的差异化配置

### 7.4 可测试性
- ✅ 工厂可独立单元测试
- ✅ 领域对象验证可独立测试
- ✅ 组合器可 Mock 工厂实现

---

## 8. 待优化项（TODO）

### 8.1 配置外部化
当前硬编码的配置应移到配置文件：
- `NACOS_SERVICE_NAME`（蓝绿网关、Portal）
- `FIXED_INSTANCES`（ASBC 网关）
- `CONFIG_ENDPOINT`（ASBC 网关）
- `DEFAULT_HEALTH_CHECK_PATH`

建议使用 `@ConfigurationProperties` 注入：
```java
@ConfigurationProperties(prefix = "deploy.blue-green-gateway")
public class BlueGreenGatewayProperties {
    private String nacosServiceName;
    private String defaultHealthCheckPath;
}
```

### 8.2 错误处理增强
添加自定义异常类：
```java
public class ServiceConfigConversionException extends RuntimeException {
    private final String serviceType;
    private final String fieldName;
    // ...
}
```

### 8.3 日志记录
在工厂中添加转换日志：
```java
@Slf4j
@Component
public class BlueGreenGatewayConfigFactory implements ServiceConfigFactory {
    
    @Override
    public ServiceConfig create(TenantConfig tenantConfig) {
        log.debug("Converting TenantConfig to BlueGreenGatewayConfig: tenantId={}", 
                  tenantConfig.getTenantId());
        // 转换逻辑
        log.info("Created BlueGreenGatewayConfig: {}", config);
        return config;
    }
}
```

---

## 9. 文件清单

### 领域配置模型
- `ServiceConfig.java` - 标记接口
- `BlueGreenGatewayConfig.java` - 蓝绿网关配置
- `PortalConfig.java` - Portal 配置
- `ASBCGatewayConfig.java` - ASBC 网关配置

### 工厂实现
- `ServiceConfigFactory.java` - 工厂接口
- `BlueGreenGatewayConfigFactory.java` - 蓝绿网关工厂
- `PortalConfigFactory.java` - Portal 工厂
- `ASBCGatewayConfigFactory.java` - ASBC 网关工厂
- `ServiceConfigFactoryComposite.java` - 组合器

---

## 10. 总结

通过工厂模式实现的防腐层，成功地：
1. **隔离了应用层和领域层**的数据结构
2. **封装了复杂的转换逻辑**，简化调用方使用
3. **提供了类型安全的配置模型**，避免运行时错误
4. **支持灵活扩展**，新增服务类型无需修改现有代码

这个设计为后续的 StageFactory 动态编排提供了坚实的基础，确保领域模型的纯粹性和稳定性。
