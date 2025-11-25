# Deploy 配置迁移对照表与实施细节

> **配套文档**: deploy-spring-boot-starter-design.md  
> **创建日期**: 2025-11-26

---

## 📋 配置文件对照表

### deploy-stages.yml → application.yml 映射

| 旧配置路径（deploy-stages.yml） | 新配置路径（application.yml） | 默认值 | 说明 |
|--------------------------------|-------------------------------|--------|------|
| `infrastructure.redis.hashKeyPrefix` | `executor.infrastructure.redis.hash-key-prefix` | `icc_ai_ops_srv:tenant_config:` | 命名从 camelCase 改为 kebab-case |
| `infrastructure.redis.pubsubTopic` | `executor.infrastructure.redis.pubsub-topic` | `icc_ai_ops_srv:tenant_config:topic` | 同上 |
| `infrastructure.nacos.services.*` | `executor.infrastructure.nacos.services.*` | 见下表 | 键名保持不变 |
| `infrastructure.fallbackInstances` | `executor.infrastructure.fallback-instances` | 空 Map | 键名改为 kebab-case |
| `infrastructure.auth` | `executor.infrastructure.auth` | 空 Map | 结构不变 |
| `infrastructure.healthCheck.defaultPath` | `executor.infrastructure.health-check.default-path` | `/actuator/bg-sdk/{tenantId}` | 命名改为 kebab-case |
| `infrastructure.healthCheck.intervalSeconds` | `executor.infrastructure.health-check.interval-seconds` | `3` | 同上 |
| `infrastructure.healthCheck.maxAttempts` | `executor.infrastructure.health-check.max-attempts` | `10` | 同上 |
| `defaultServiceNames` | `executor.default-service-names` | `[]` | 提升到 executor 根层级 |

### Nacos Services 默认映射

| Service Key | 默认 Nacos 服务名 |
|-------------|------------------|
| `blueGreenGatewayService` | `blue-green-gateway-service` |
| `portalService` | `portal-service` |
| `asbcService` | `asbc-gateway-service` |
| `obService` | `ob-service` |

### T-025 新增配置（已在 deploy-stages.yml）

| 配置项 | 新路径 | 默认值 | 说明 |
|--------|--------|--------|------|
| `nacos.enabled` | `executor.infrastructure.nacos.enabled` | `false` | T-025 新增 |
| `nacos.serverAddr` | `executor.infrastructure.nacos.server-addr` | `127.0.0.1:8848` | T-025 新增 |
| `nacos.healthCheckEnabled` | `executor.infrastructure.nacos.health-check-enabled` | `false` | T-025 新增 |

---

## 🔄 占位符语法转换

### 旧语法（自定义）

```yaml
# deploy-stages.yml
infrastructure:
  redis:
    hashKeyPrefix: "{$REDIS_HASH_PREFIX:icc_ai_ops_srv:tenant_config:}"
```

### 新语法（Spring Boot 标准）

```yaml
# application.yml
executor:
  infrastructure:
    redis:
      hash-key-prefix: ${REDIS_HASH_PREFIX:icc_ai_ops_srv:tenant_config:}
```

**转换规则**:
- `{$VAR:default}` → `${VAR:default}`
- 去掉前缀 `$`
- 保持冒号分隔符不变

---

## 📦 Java 类映射关系

### 旧类 → 新类映射

| 旧类（infrastructure.config.model） | 新类（config.properties） | 状态 |
|-------------------------------------|--------------------------|------|
| `DeploymentConfig` | `ExecutorProperties` | 替换 |
| `InfrastructureConfig` | `InfrastructureProperties` | 替换 |
| `InfrastructureConfig.RedisConfig` | `InfrastructureProperties.RedisProperties` | 替换 |
| `InfrastructureConfig.NacosConfig` | `InfrastructureProperties.NacosProperties` | 替换 |
| `InfrastructureConfig.ASBCConfig` | （废弃，合并到 fallback-instances） | 删除 |
| `InfrastructureConfig.HealthCheckConfig` | `InfrastructureProperties.HealthCheckProperties` | 替换 |
| `InfrastructureConfig.AuthConfig` | `InfrastructureProperties.AuthProperties` | 替换 |

### 使用方迁移示例

#### 示例 1: SharedStageResources

**旧代码**:
```java
@Component
public class SharedStageResources {
    private final DeploymentConfigLoader configLoader;
    
    @Autowired
    public SharedStageResources(DeploymentConfigLoader configLoader, ...) {
        this.configLoader = configLoader;
    }
    
    public String getRedisKeyPrefix() {
        return configLoader.getInfrastructure().getRedis().getHashKeyPrefix();
    }
}
```

**新代码**:
```java
@Component
public class SharedStageResources {
    private final InfrastructureProperties infrastructure;
    
    @Autowired
    public SharedStageResources(InfrastructureProperties infrastructure, ...) {
        this.infrastructure = infrastructure;
    }
    
    public String getRedisKeyPrefix() {
        return infrastructure.getRedis().getHashKeyPrefix();
    }
}
```

#### 示例 2: StageAssembler

**旧代码**:
```java
private DataPreparer createPreparer(TenantConfig config, SharedStageResources resources) {
    return (ctx) -> {
        String prefix = resources.getConfigLoader()
            .getInfrastructure()
            .getRedis()
            .getHashKeyPrefix();
        
        String topic = resources.getConfigLoader()
            .getInfrastructure()
            .getRedis()
            .getPubsubTopic();
        
        // ...
    };
}
```

**新代码（过渡期 - 通过 SharedStageResources 适配）**:
```java
private DataPreparer createPreparer(TenantConfig config, SharedStageResources resources) {
    return (ctx) -> {
        // SharedStageResources 内部适配 Properties
        String prefix = resources.getRedisKeyPrefix();
        String topic = resources.getRedisPubsubTopic();
        
        // ...
    };
}
```

**新代码（最终 - 直接注入 Properties）**:
```java
@Component
public class BlueGreenStageAssembler implements StageAssembler {
    
    private final InfrastructureProperties infrastructure;
    
    @Autowired
    public BlueGreenStageAssembler(InfrastructureProperties infrastructure) {
        this.infrastructure = infrastructure;
    }
    
    private DataPreparer createPreparer(TenantConfig config) {
        return (ctx) -> {
            String prefix = infrastructure.getRedis().getHashKeyPrefix();
            String topic = infrastructure.getRedis().getPubsubTopic();
            
            // ...
        };
    }
}
```

---

## 🛠️ 适配器实现

### SharedStageResources 适配器（过渡期）

```java
@Component
public class SharedStageResources {
    
    private final DeploymentConfigLoader configLoader;  // 旧（@Deprecated）
    private final InfrastructureProperties infrastructure;  // 新
    
    @Autowired
    public SharedStageResources(
            @Autowired(required = false) DeploymentConfigLoader configLoader,  // 可选
            InfrastructureProperties infrastructure,
            ...) {
        
        this.configLoader = configLoader;
        this.infrastructure = infrastructure;
    }
    
    /**
     * 获取 Redis Key 前缀
     * @deprecated 直接使用 InfrastructureProperties
     */
    @Deprecated
    public String getRedisKeyPrefix() {
        // 优先使用新配置
        if (infrastructure != null) {
            return infrastructure.getRedis().getHashKeyPrefix();
        }
        // 降级到旧配置
        if (configLoader != null) {
            return configLoader.getInfrastructure().getRedis().getHashKeyPrefix();
        }
        throw new IllegalStateException("No configuration available");
    }
    
    /**
     * 获取基础设施配置（新）
     */
    public InfrastructureProperties getInfrastructure() {
        return infrastructure;
    }
    
    /**
     * 获取配置加载器（旧）
     * @deprecated 使用 getInfrastructure()
     */
    @Deprecated
    public DeploymentConfigLoader getConfigLoader() {
        return configLoader;
    }
}
```

---

## 🧪 测试策略

### 1. 配置绑定测试

```java
@SpringBootTest(properties = {
    "executor.infrastructure.redis.hash-key-prefix=test:prefix:",
    "executor.infrastructure.nacos.enabled=true",
    "executor.infrastructure.nacos.server-addr=test-nacos:8848"
})
class InfrastructurePropertiesTest {
    
    @Autowired
    private InfrastructureProperties properties;
    
    @Test
    void shouldBindRedisProperties() {
        assertThat(properties.getRedis().getHashKeyPrefix())
            .isEqualTo("test:prefix:");
    }
    
    @Test
    void shouldBindNacosProperties() {
        assertThat(properties.getNacos().isEnabled()).isTrue();
        assertThat(properties.getNacos().getServerAddr())
            .isEqualTo("test-nacos:8848");
    }
}
```

### 2. 默认值测试

```java
@SpringBootTest(properties = {
    // 不配置任何 executor.infrastructure.*
})
class DefaultValuesTest {
    
    @Autowired
    private InfrastructureProperties properties;
    
    @Test
    void shouldUseDefaultValues() {
        // Redis 默认值
        assertThat(properties.getRedis().getHashKeyPrefix())
            .isEqualTo("icc_ai_ops_srv:tenant_config:");
        
        // Nacos 默认值
        assertThat(properties.getNacos().isEnabled()).isFalse();
        assertThat(properties.getNacos().getServerAddr())
            .isEqualTo("127.0.0.1:8848");
        
        // HealthCheck 默认值
        assertThat(properties.getHealthCheck().getIntervalSeconds())
            .isEqualTo(3);
        assertThat(properties.getHealthCheck().getMaxAttempts())
            .isEqualTo(10);
    }
}
```

### 3. 条件装配测试

```java
@SpringBootTest(properties = {
    "executor.infrastructure.nacos.enabled=false"
})
class ConditionalBeanTest {
    
    @Autowired(required = false)
    private NacosServiceDiscovery nacosDiscovery;
    
    @Autowired
    private ServiceDiscoveryHelper serviceDiscoveryHelper;
    
    @Test
    void nacosDisabled_shouldNotCreateNacosBean() {
        assertThat(nacosDiscovery).isNull();
    }
    
    @Test
    void nacosDisabled_shouldStillCreateHelper() {
        assertThat(serviceDiscoveryHelper).isNotNull();
    }
}
```

### 4. Profile 配置测试

```java
@SpringBootTest
@ActiveProfiles("prod")
class ProfileConfigTest {
    
    @Autowired
    private InfrastructureProperties properties;
    
    @Test
    void prod_shouldLoadProdConfig() {
        assertThat(properties.getNacos().isEnabled()).isTrue();
        assertThat(properties.getRedis().getHashKeyPrefix())
            .startsWith("prod:");
    }
}
```

---

## 📝 迁移清单（详细步骤）

### Step 1: 创建新的 Properties 类（2h）

- [ ] 创建 `config/properties` 包
- [ ] 创建 `ExecutorProperties.java`
- [ ] 创建 `InfrastructureProperties.java`
  - [ ] 内部类 `RedisProperties`
  - [ ] 内部类 `NacosProperties`
  - [ ] 内部类 `AuthProperties`
  - [ ] 内部类 `HealthCheckProperties`
- [ ] 添加 `@ConfigurationProperties` 注解
- [ ] 添加 `@Validated` 和验证注解
- [ ] 添加 JavaDoc 注释

### Step 2: 创建自动装配类（2h）

- [ ] 创建 `autoconfigure` 包（如不存在）
- [ ] 创建 `ExecutorAutoConfiguration.java`
- [ ] 创建 `InfrastructureAutoConfiguration.java`
  - [ ] NacosServiceDiscovery Bean（条件装配）
  - [ ] ServiceDiscoveryHelper Bean
- [ ] 更新 `AutoConfiguration.imports`

### Step 3: 实现适配器（2h）

- [ ] 在 `SharedStageResources` 添加双重注入
- [ ] 实现适配方法（优先新配置）
- [ ] 标记旧方法为 `@Deprecated`

### Step 4: 编写单元测试（3h）

- [ ] `InfrastructurePropertiesTest`
- [ ] `InfrastructureAutoConfigurationTest`
- [ ] 默认值测试
- [ ] 条件装配测试
- [ ] Profile 配置测试

### Step 5: 迁移配置文件（1h）

- [ ] 更新 `application.yml`
- [ ] 创建 `application-dev.yml`
- [ ] 创建 `application-test.yml`
- [ ] 创建 `application-prod.yml`
- [ ] 重命名 `deploy-stages.yml` → `deploy-stages.yml.deprecated`

### Step 6: 逐个迁移使用方（4h）

- [ ] 识别所有 `DeploymentConfigLoader` 注入点
- [ ] 迁移 `SharedStageResources`（适配器模式）
- [ ] 迁移 `ServiceDiscoveryConfiguration`
- [ ] 迁移其他使用方

### Step 7: 创建 Configuration Metadata（3h）

- [ ] 创建 `spring-configuration-metadata.json`
- [ ] 添加所有 properties 定义
- [ ] 添加 groups 定义
- [ ] 添加 hints 定义
- [ ] 测试 IDE 自动补全

### Step 8: 文档更新（2h）

- [ ] 更新设计文档
- [ ] 编写迁移指南
- [ ] 更新 README
- [ ] 添加配置示例

### Step 9: 清理旧代码（1h）

- [ ] 标记 `DeploymentConfigLoader` 为 `@Deprecated`
- [ ] 标记 `InfrastructureConfig` 为 `@Deprecated`
- [ ] 添加迁移提示注释
- [ ] 计划删除时间

---

## ⚠️ 注意事项

### 1. 兼容性问题

**问题**: 旧配置 (deploy-stages.yml) 和新配置 (application.yml) 同时存在时的优先级

**解决方案**:
- 新配置优先级高于旧配置
- 适配器中优先使用新配置
- 启动日志明确指出使用的配置来源

### 2. 占位符语法差异

**问题**: `{$VAR:default}` vs `${VAR:default}`

**解决方案**:
- 一次性转换所有占位符
- 提供转换脚本（可选）
- 在迁移文档中明确说明

### 3. 命名规范变化

**问题**: camelCase → kebab-case

**解决方案**:
- Spring Boot 自动支持两种格式
- 但推荐统一使用 kebab-case
- 文档中提供对照表

### 4. 测试环境影响

**问题**: 单元测试可能依赖 deploy-stages.yml

**解决方案**:
- 逐步迁移测试配置
- 使用 `@TestPropertySource` 或 `@SpringBootTest(properties=...)`
- 保留 deploy-stages.yml 直到所有测试迁移完成

---

## 🎯 验收检查清单

### 功能验收

- [ ] 使用默认配置可以启动应用
- [ ] dev/test/prod Profile 配置正确加载
- [ ] Nacos enabled=true 时正常连接
- [ ] Nacos enabled=false 时降级到 fallback
- [ ] 所有原有功能正常工作

### 代码质量

- [ ] 新代码有完整的 JavaDoc
- [ ] 所有 Properties 类有验证注解
- [ ] 适配器有 @Deprecated 注释
- [ ] 没有警告（除了 @Deprecated）

### 测试覆盖

- [ ] 单元测试覆盖率 > 80%
- [ ] 所有配置绑定测试通过
- [ ] 条件装配测试通过
- [ ] Profile 配置测试通过

### 文档完整性

- [ ] 设计文档完整
- [ ] 迁移指南清晰
- [ ] 配置示例完整
- [ ] 对照表准确

### IDE 支持

- [ ] IDEA 自动补全配置项
- [ ] 鼠标悬停显示文档
- [ ] 类型检查生效

---

**文档版本**: 1.0  
**最后更新**: 2025-11-26

