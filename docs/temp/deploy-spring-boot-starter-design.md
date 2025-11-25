# Deploy 模块 Spring Boot Starter 化设计方案

> **创建日期**: 2025-11-26  
> **背景**: T-017 完成了 ExecutorStagesProperties 但未完成配置迁移  
> **目标**: 实现类似 spring-boot-starter 的约定优于配置，支持灵活装配

---

## 📋 问题分析

### 当前状态

#### 已完成（T-017）✅
1. **ExecutorStagesProperties** - Stage 配置容器
   - `@ConfigurationProperties(prefix = "executor.stages")`
   - 支持 BlueGreen、Portal、ASBC 配置
   - 自动验证和默认值
   - 健康检查和配置报告

2. **ExecutorStagesAutoConfiguration** - 自动装配
   - Spring Boot 3.x SPI 格式
   - `@EnableConfigurationProperties`

#### 未完成（遗留问题）❌

1. **deploy-stages.yml 仍然存在**
   - 包含 infrastructure 配置（Redis、Nacos、健康检查等）
   - 使用自定义占位符 `{$VAR:default}`
   - 通过 DeploymentConfigLoader 手动加载
   - 与 Spring Boot 标准配置体系脱节

2. **application.yml 配置不完整**
   - 只有 executor.checkpoint 和 executor.persistence
   - 缺少 infrastructure 配置
   - 缺少 stages 配置

3. **缺少 Configuration Metadata**
   - 无 IDE 智能提示
   - 无属性文档
   - 无类型校验

4. **配置加载双轨制**
   - DeploymentConfigLoader 加载 deploy-stages.yml
   - @ConfigurationProperties 加载 application.yml
   - 两套机制并存，职责不清

### 核心问题

**问题 1**: 配置分散在两个文件中，管理混乱  
**问题 2**: 自定义占位符语法与 Spring Boot 不一致  
**问题 3**: 缺少约定优于配置的理念  
**问题 4**: 缺少 IDE 支持和类型安全

---

## 🎯 设计目标

### 1. 约定优于配置（Convention over Configuration）

**原则**：
- 80% 的场景零配置即可运行
- 提供合理的默认值
- 需要时才覆盖配置

**示例**：
```yaml
# 场景 1: 最小配置（开发环境）
# 不配置任何内容，使用所有默认值
# application.yml 可以为空或只有 spring.profiles.active

# 场景 2: 覆盖关键配置（测试环境）
executor:
  infrastructure:
    nacos:
      enabled: true
      server-addr: nacos-test:8848

# 场景 3: 完整配置（生产环境）
executor:
  infrastructure:
    nacos:
      enabled: true
      server-addr: nacos-prod:8848
      health-check-enabled: true
    redis:
      hash-key-prefix: "prod:tenant:config:"
  stages:
    blue-green-gateway:
      enabled: true
      timeout: 60s
```

### 2. 条件装配（Conditional Assembly）

**原则**：
- 根据配置自动启用/禁用组件
- 根据依赖自动装配服务
- 提供扩展点支持自定义

**示例**：
```java
// Nacos 只在启用时装配
@Bean
@ConditionalOnProperty(prefix = "executor.infrastructure.nacos", name = "enabled", havingValue = "true")
public NacosServiceDiscovery nacosServiceDiscovery(InfrastructureProperties props) { ... }

// RedisAckService 自动装配（如果 redis-ack-spring 在 classpath）
@Bean
@ConditionalOnMissingBean
public RedisAckService redisAckService(RedisTemplate template) { ... }
```

### 3. 类型安全与验证

**原则**：
- 使用 Java 类代替 Map/String
- 编译时类型检查
- 启动时验证配置

**示例**：
```java
@ConfigurationProperties(prefix = "executor.infrastructure")
@Validated
public class InfrastructureProperties {
    
    @NotNull
    private RedisProperties redis = new RedisProperties();
    
    @Valid
    private NacosProperties nacos = new NacosProperties();
    
    public static class RedisProperties {
        @NotBlank
        private String hashKeyPrefix = "icc_ai_ops_srv:tenant_config:";
        
        @NotBlank
        private String pubsubTopic = "icc_ai_ops_srv:tenant_config:topic";
    }
}
```

### 4. IDE 智能提示（Configuration Metadata）

**原则**：
- 提供完整的 spring-configuration-metadata.json
- 支持属性自动补全
- 提供描述和默认值
- 支持值提示（hints）

---

## 🏗️ 架构设计

### 整体结构

```
deploy/src/main/
├── java/xyz/firestige/deploy/
│   ├── config/
│   │   ├── properties/                     [新增] 配置属性包
│   │   │   ├── ExecutorProperties.java    [新增] 根配置
│   │   │   ├── InfrastructureProperties.java   [新增] 基础设施配置
│   │   │   ├── StagesProperties.java      [重构] Stage 配置
│   │   │   └── ...
│   │   ├── stage/                          [保留] Stage 配置接口
│   │   └── ...
│   ├── autoconfigure/                      [扩展] 自动装配
│   │   ├── ExecutorAutoConfiguration.java [新增] 根自动配置
│   │   ├── InfrastructureAutoConfiguration.java  [新增]
│   │   ├── StagesAutoConfiguration.java   [重构]
│   │   └── ...
│   └── infrastructure/
│       ├── config/
│       │   ├── DeploymentConfigLoader.java [废弃] 删除或标记 @Deprecated
│       │   └── model/                      [迁移] 转为 Properties 类
│       └── ...
└── resources/
    ├── application.yml                     [扩展] 主配置文件
    ├── application-dev.yml                 [新增] 开发环境配置
    ├── application-test.yml                [新增] 测试环境配置
    ├── application-prod.yml                [新增] 生产环境配置
    ├── deploy-stages.yml                   [废弃] 删除或重命名为 .deprecated
    └── META-INF/
        ├── spring/
        │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
        └── spring-configuration-metadata.json  [新增] 配置元数据
```

### 配置层次结构

```
executor.*                                  # 根命名空间
├── infrastructure.*                        # 基础设施配置
│   ├── redis.*                            # Redis 配置
│   │   ├── hash-key-prefix               # Redis Hash Key 前缀
│   │   └── pubsub-topic                  # Pub/Sub Topic
│   ├── nacos.*                           # Nacos 服务发现
│   │   ├── enabled                       # 是否启用
│   │   ├── server-addr                   # 服务器地址
│   │   ├── health-check-enabled          # 健康检查
│   │   └── services.*                    # 服务映射
│   ├── fallback-instances.*              # 降级实例配置
│   ├── auth.*                            # 认证配置
│   └── health-check.*                    # 健康检查配置
├── stages.*                               # Stage 配置
│   ├── blue-green-gateway.*              # 蓝绿网关
│   ├── portal.*                          # Portal
│   └── asbc-gateway.*                    # ASBC
├── checkpoint.*                           # Checkpoint 配置
└── persistence.*                          # 持久化配置
```

---

## 📐 详细设计

### 1. 配置属性类设计

#### 1.1 根配置类

```java
package xyz.firestige.deploy.config.properties;

@ConfigurationProperties(prefix = "executor")
@Validated
public class ExecutorProperties {
    
    /**
     * 基础设施配置
     */
    @Valid
    @NotNull
    private InfrastructureProperties infrastructure = new InfrastructureProperties();
    
    /**
     * Stage 配置
     */
    @Valid
    @NotNull
    private StagesProperties stages = new StagesProperties();
    
    /**
     * Checkpoint 配置
     */
    @Valid
    @NotNull
    private CheckpointProperties checkpoint = new CheckpointProperties();
    
    /**
     * 持久化配置
     */
    @Valid
    @NotNull
    private PersistenceProperties persistence = new PersistenceProperties();
    
    // Getters/Setters
}
```

#### 1.2 基础设施配置类

```java
package xyz.firestige.deploy.config.properties;

public class InfrastructureProperties {
    
    /**
     * Redis 配置
     */
    @Valid
    @NotNull
    private RedisProperties redis = new RedisProperties();
    
    /**
     * Nacos 服务发现配置
     */
    @Valid
    @NotNull
    private NacosProperties nacos = new NacosProperties();
    
    /**
     * 降级实例配置（Nacos 不可用时使用）
     */
    private Map<String, List<String>> fallbackInstances = new HashMap<>();
    
    /**
     * 认证配置
     */
    private Map<String, AuthProperties> auth = new HashMap<>();
    
    /**
     * 健康检查配置
     */
    @Valid
    @NotNull
    private HealthCheckProperties healthCheck = new HealthCheckProperties();
    
    // 内部类
    
    public static class RedisProperties {
        /**
         * Redis Hash Key 前缀
         * 默认值: icc_ai_ops_srv:tenant_config:
         */
        @NotBlank
        private String hashKeyPrefix = "icc_ai_ops_srv:tenant_config:";
        
        /**
         * Redis Pub/Sub Topic
         * 默认值: icc_ai_ops_srv:tenant_config:topic
         */
        @NotBlank
        private String pubsubTopic = "icc_ai_ops_srv:tenant_config:topic";
        
        // Getters/Setters
    }
    
    public static class NacosProperties {
        /**
         * 是否启用 Nacos 服务发现
         * 默认值: false
         */
        private boolean enabled = false;
        
        /**
         * Nacos 服务器地址
         * 格式: host:port
         * 默认值: 127.0.0.1:8848
         */
        private String serverAddr = "127.0.0.1:8848";
        
        /**
         * 是否启用健康检查
         * 默认值: false
         */
        private boolean healthCheckEnabled = false;
        
        /**
         * 服务映射：serviceKey -> Nacos 服务名
         */
        private Map<String, String> services = new HashMap<>() {{
            put("blueGreenGatewayService", "blue-green-gateway-service");
            put("portalService", "portal-service");
            put("asbcService", "asbc-gateway-service");
            put("obService", "ob-service");
        }};
        
        // Getters/Setters
    }
    
    public static class AuthProperties {
        private boolean enabled = false;
        private String tokenProvider = "random";
        
        // Getters/Setters
    }
    
    public static class HealthCheckProperties {
        /**
         * 默认健康检查路径模板
         * 支持占位符: {tenantId}
         */
        @NotBlank
        private String defaultPath = "/actuator/bg-sdk/{tenantId}";
        
        /**
         * 检查间隔（秒）
         */
        @Min(1)
        private int intervalSeconds = 3;
        
        /**
         * 最大重试次数
         */
        @Min(1)
        private int maxAttempts = 10;
        
        // Getters/Setters
    }
    
    // Getters/Setters
}
```

#### 1.3 Stage 配置类（重构现有）

```java
package xyz.firestige.deploy.config.properties;

@ConfigurationProperties(prefix = "executor.stages")
@Validated
public class StagesProperties {
    
    /**
     * 蓝绿网关配置
     */
    @Valid
    private BlueGreenGatewayStageConfig blueGreenGateway = BlueGreenGatewayStageConfig.defaultConfig();
    
    /**
     * Portal 配置
     */
    @Valid
    private PortalStageConfig portal = PortalStageConfig.defaultConfig();
    
    /**
     * ASBC 网关配置
     */
    @Valid
    private ASBCGatewayStageConfig asbcGateway = ASBCGatewayStageConfig.defaultConfig();
    
    // Getters/Setters
    // 保留现有的 registerStageConfigurations() 等逻辑
}
```

### 2. 自动装配设计

#### 2.1 根自动配置

```java
package xyz.firestige.deploy.autoconfigure;

@AutoConfiguration
@EnableConfigurationProperties({
    ExecutorProperties.class,
    InfrastructureProperties.class,
    StagesProperties.class
})
@Import({
    InfrastructureAutoConfiguration.class,
    StagesAutoConfiguration.class
})
public class ExecutorAutoConfiguration {
    
    private static final Logger log = LoggerFactory.getLogger(ExecutorAutoConfiguration.class);
    
    @PostConstruct
    public void logConfiguration() {
        log.info("Executor Auto-Configuration initialized");
    }
}
```

#### 2.2 基础设施自动配置

```java
package xyz.firestige.deploy.autoconfigure;

@Configuration
@EnableConfigurationProperties(InfrastructureProperties.class)
public class InfrastructureAutoConfiguration {
    
    /**
     * Nacos 服务发现（条件装配）
     */
    @Bean
    @ConditionalOnProperty(prefix = "executor.infrastructure.nacos", name = "enabled", havingValue = "true")
    public NacosServiceDiscovery nacosServiceDiscovery(InfrastructureProperties props) {
        NacosProperties nacos = props.getNacos();
        try {
            return new NacosServiceDiscovery(nacos.getServerAddr());
        } catch (Exception e) {
            log.error("Failed to initialize Nacos", e);
            throw new IllegalStateException("Nacos initialization failed", e);
        }
    }
    
    /**
     * 服务发现辅助类（始终装配）
     */
    @Bean
    @ConditionalOnMissingBean
    public ServiceDiscoveryHelper serviceDiscoveryHelper(
            InfrastructureProperties props,
            RestTemplate restTemplate,
            @Autowired(required = false) NacosServiceDiscovery nacosDiscovery) {
        
        return new ServiceDiscoveryHelper(
            convertToInfrastructureConfig(props), // 兼容适配器
            nacosDiscovery,
            restTemplate
        );
    }
    
    /**
     * 兼容适配器：InfrastructureProperties -> InfrastructureConfig
     * 过渡期使用，后续重构 ServiceDiscoveryHelper 直接使用 Properties
     */
    private InfrastructureConfig convertToInfrastructureConfig(InfrastructureProperties props) {
        // 转换逻辑
        InfrastructureConfig config = new InfrastructureConfig();
        // ... 字段映射
        return config;
    }
}
```

### 3. 配置元数据设计

#### 3.1 spring-configuration-metadata.json

```json
{
  "groups": [
    {
      "name": "executor",
      "type": "xyz.firestige.deploy.config.properties.ExecutorProperties",
      "description": "Executor 执行引擎配置"
    },
    {
      "name": "executor.infrastructure",
      "type": "xyz.firestige.deploy.config.properties.InfrastructureProperties",
      "description": "基础设施配置（Redis、Nacos、认证等）"
    },
    {
      "name": "executor.infrastructure.nacos",
      "type": "xyz.firestige.deploy.config.properties.InfrastructureProperties$NacosProperties",
      "description": "Nacos 服务发现配置"
    }
  ],
  "properties": [
    {
      "name": "executor.infrastructure.redis.hash-key-prefix",
      "type": "java.lang.String",
      "description": "Redis Hash Key 前缀，用于租户配置存储",
      "defaultValue": "icc_ai_ops_srv:tenant_config:"
    },
    {
      "name": "executor.infrastructure.nacos.enabled",
      "type": "java.lang.Boolean",
      "description": "是否启用 Nacos 服务发现。false 时使用 fallbackInstances",
      "defaultValue": false
    },
    {
      "name": "executor.infrastructure.nacos.server-addr",
      "type": "java.lang.String",
      "description": "Nacos 服务器地址，格式: host:port",
      "defaultValue": "127.0.0.1:8848"
    }
  ],
  "hints": [
    {
      "name": "executor.infrastructure.nacos.enabled",
      "values": [
        {"value": false, "description": "使用 fallbackInstances 固定配置"},
        {"value": true, "description": "从 Nacos 动态获取服务实例"}
      ]
    },
    {
      "name": "executor.infrastructure.auth.*.token-provider",
      "values": [
        {"value": "random", "description": "生成随机 Hex Token"},
        {"value": "oauth2", "description": "使用 OAuth2 Token（未实现）"},
        {"value": "custom", "description": "自定义 Token 提供器"}
      ],
      "providers": [
        {"name": "any"}
      ]
    }
  ]
}
```

#### 3.2 生成方式

**选项 A：手动编写**（推���用于复杂场景）
- 完全控制
- 可以添加丰富的描述和 hints
- 适合当前阶段

**选项 B：使用 spring-boot-configuration-processor**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-configuration-processor</artifactId>
    <optional>true</optional>
</dependency>
```
- 自动生成基础元数据
- 通过 JavaDoc 添加描述
- 手动补充 hints

### 4. 配置文件设计

#### 4.1 application.yml（主配置，最小化）

```yaml
# 主配置文件 - 只包含必需配置
spring:
  application:
    name: executor-deploy
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

# Executor 配置 - 使用默认值，无需显式配置
# executor:
#   infrastructure: ...  # 全部使用默认值
#   stages: ...          # 全部使用默认值
```

#### 4.2 application-dev.yml（开发环境）

```yaml
# 开发环境配置
executor:
  infrastructure:
    nacos:
      enabled: false  # 开发环境不使用 Nacos
    fallback-instances:
      blue-green-gateway:
        - localhost:8081
        - localhost:8082
      portal:
        - localhost:8083
      asbc-gateway:
        - localhost:8084
      ob-service:
        - localhost:8085

logging:
  level:
    xyz.firestige.deploy: DEBUG
```

#### 4.3 application-prod.yml（生产环境）

```yaml
# 生产环境配置
executor:
  infrastructure:
    redis:
      hash-key-prefix: "prod:tenant:config:"
      pubsub-topic: "prod:tenant:config:topic"
    nacos:
      enabled: true
      server-addr: ${NACOS_SERVER:nacos-prod:8848}
      health-check-enabled: true
      services:
        blueGreenGatewayService: icc-bg-gateway-prod
        portalService: icc-portal-prod
        asbcService: asbc-config-prod
        obService: ob-campaign-prod
    health-check:
      interval-seconds: 5
      max-attempts: 20
  stages:
    blue-green-gateway:
      enabled: true
    portal:
      enabled: true
    asbc-gateway:
      enabled: true

logging:
  level:
    xyz.firestige.deploy: INFO
```

---

## 🔄 迁移策略

### Phase 1: 创建新的 Properties 类（不破坏现有）

**目标**: 建立新的配置体系，与旧体系并存

**步骤**:
1. 创建 `ExecutorProperties`、`InfrastructureProperties`
2. 创建 `InfrastructureAutoConfiguration`
3. 添加 `@EnableConfigurationProperties`
4. 测试新配置加载

**验证**:
- 新配置可以从 application.yml 加载
- 旧配置（DeploymentConfigLoader）仍然工作

### Phase 2: 迁移使用方（逐步替换）

**目标**: 将使用 DeploymentConfigLoader 的地方改为使用 InfrastructureProperties

**步骤**:
1. 识别所有 DeploymentConfigLoader 的注入点
2. 逐个替换为 InfrastructureProperties
3. 提供适配器兼容旧接口（过渡期）

**示例**:
```java
// 旧代码
@Autowired
private DeploymentConfigLoader configLoader;

String prefix = configLoader.getInfrastructure().getRedis().getHashKeyPrefix();

// 新代码
@Autowired
private InfrastructureProperties infrastructure;

String prefix = infrastructure.getRedis().getHashKeyPrefix();
```

### Phase 3: 迁移配置文件（deploy-stages.yml → application.yml）

**目标**: 将配置内容迁移到 application.yml

**步骤**:
1. 将 deploy-stages.yml 内容转换为 Spring Boot 标准格式
2. 更新 application.yml
3. 重命名 deploy-stages.yml 为 deploy-stages.yml.deprecated
4. 添加启动日志提示配置已迁移

### Phase 4: 废弃旧代码

**目标**: 清理技术债务

**步骤**:
1. 标记 DeploymentConfigLoader 为 `@Deprecated`
2. 标记 InfrastructureConfig 为 `@Deprecated`
3. 添加注释说明迁移路径
4. 计划后续版本删除

### Phase 5: 添加 Configuration Metadata

**目标**: 提供 IDE 支持

**步骤**:
1. 编写 spring-configuration-metadata.json
2. 添加丰富的描述和 hints
3. 测试 IDE 自动补全

---

## 📋 实施检查清单

### 代码层面

- [ ] 创建 `config.properties` 包
- [ ] 创建 `ExecutorProperties`
- [ ] 创建 `InfrastructureProperties`
- [ ] 重构 `StagesProperties`（移到新包）
- [ ] 创建 `ExecutorAutoConfiguration`
- [ ] 创建 `InfrastructureAutoConfiguration`
- [ ] 重构 `StagesAutoConfiguration`
- [ ] 更新 `AutoConfiguration.imports`
- [ ] 提供适配器（Properties ↔ Config）
- [ ] 标记 `DeploymentConfigLoader` 为 `@Deprecated`

### 配置层面

- [ ] 更新 `application.yml`（添加 executor.infrastructure）
- [ ] 创建 `application-dev.yml`
- [ ] 创建 `application-test.yml`
- [ ] 创建 `application-prod.yml`
- [ ] 重命名 `deploy-stages.yml` 为 `.deprecated`
- [ ] 创建 `spring-configuration-metadata.json`

### 测试层面

- [ ] 新 Properties 类的单元测试
- [ ] 自动装配测试
- [ ] 配置绑定测试
- [ ] 条件装配测试
- [ ] 默认值测试
- [ ] 验证规则测试

### 文档层面

- [ ] 更新设计文档
- [ ] 编写配置迁移指南
- [ ] 更新 README（配置章节）
- [ ] 添加配置示例

---

## 🎯 验收标准

### 功能验收

1. **零配置启动** ✅
   - application.yml 可以为空
   - 使用所有默认值
   - 应用正常启动

2. **环境特化配置** ✅
   - dev/test/prod 配置分离
   - `spring.profiles.active` 切换
   - 配置正确加载

3. **条件装配** ✅
   - Nacos disabled → NacosServiceDiscovery 不创建
   - Nacos enabled → 正常创建并连接

4. **配置验证** ✅
   - 非法值在启动时报错
   - 错误信息清晰

### 质量验收

1. **IDE 支持** ✅
   - IDEA/VSCode 自动补全
   - 鼠标悬停显示文档
   - 类型检查

2. **类型安全** ✅
   - 编译时类型检查
   - 无 Map<String, Object>

3. **向后兼容** ✅
   - 旧代码仍可运行（标记 @Deprecated）
   - 提供适配器
   - 迁移路径清晰

### 文档验收

1. **配置示例** ✅
   - 最小配置示例
   - 常用配置示例
   - 完整配置示例

2. **迁移指南** ✅
   - deploy-stages.yml → application.yml 对照表
   - 分步迁移说明

---

## ❓ 待讨论问题

### 1. 迁移时机

**选项 A**: 立即开始，分阶段迁移（推荐）
- 不影响现有功能
- 逐步替换
- 技术债逐步清理

**选项 B**: 延后到主要功能稳定后
- 风险低
- 但技术债累积

**建议**: 选项 A

### 2. 旧配置保留策略

**选项 A**: 保留 1-2 个版本，提供 @Deprecated 警告
**选项 B**: 立即删除，强制迁移
**选项 C**: 永久保留，双轨并行

**建议**: 选项 A

### 3. Configuration Metadata 生成方式

**选项 A**: 手动编写（当前推荐）
**选项 B**: 使用 spring-boot-configuration-processor
**选项 C**: 混合（基础自动生成 + 手动补充）

**建议**: 选项 A（初期），后续演进到选项 C

### 4. 适配器层保留时间

**过渡期提供适配器**：Properties ↔ Config
- 方便渐进��迁移
- 但增加复杂度

**问题**: 何时移除适配器？
**建议**: 所有使用方迁移完成后的下一个大版本

---

## 📅 时间估算

| Phase | 任务 | 预计时间 |
|-------|------|---------|
| Phase 1 | 创建 Properties 类 | 4h |
| Phase 2 | 迁移使用方 | 6h |
| Phase 3 | 迁移配置文件 | 2h |
| Phase 4 | 废弃旧代码 | 1h |
| Phase 5 | Configuration Metadata | 3h |
| 测试 | 单元测试 + 集成测试 | 4h |
| 文档 | 设计文档 + 迁移指南 | 2h |
| **总计** | | **22h (约 3 天)** |

---

## 📚 参考资料

- Spring Boot Configuration Metadata: https://docs.spring.io/spring-boot/docs/current/reference/html/configuration-metadata.html
- Spring Boot Auto-Configuration: https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.developing-auto-configuration
- Spring Boot Properties: https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config

---

**方案提出日期**: 2025-11-26  
**状态**: 待评审

