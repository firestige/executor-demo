# Deploy Spring Boot Starter 化设计方案（修订版 v2）

> **创建日期**: 2025-11-26  
> **修订日期**: 2025-11-26  
> **修订原因**: 
> 1. 验证配置隔离设计（防腐层保护）
> 2. 明确新增/修改/移除范围
> 3. 澄清 HealthCheck 实际含义（Verify 端点配置）

---

## 🎯 核心设计验证：配置隔离

### 当前防腐层设计 ✅

```
消费者（Assembler）
    ↓
SharedStageResources（防腐层）
    ↓
DeploymentConfigLoader（配置加载）
    ↓
InfrastructureConfig（配置模型）
```

**关键发现**：
- ✅ **所有配置消费都通过 SharedStageResources**
- ✅ **没有直接注入 DeploymentConfigLoader 的消费者**
- ✅ **配置加载机制变更不影响消费者代码**

### 验证结果

检查点 | 现状 | 结论
--------|------|------
消费者直接依赖配置 | ❌ 没有 | ✅ 隔离良好
通过防腐层访问 | ✅ 都通过 SharedStageResources | ✅ 符合设计
配置加载可替换 | ✅ 只需修改 SharedStageResources 内部 | ✅ 可平滑迁移

**结论**: 当前设计已经有良好的配置隔离，**迁移 DeploymentConfigLoader → InfrastructureProperties 只需修改 SharedStageResources 内部实现，不影响消费者**。

---

## 📝 术语澄清

### HealthCheck 实际含义

**旧理解（错误）**: Spring Actuator 健康检查  
**实际含义**: RedisAck Verify 步骤的端点配置

#### T-019 集成中的实际用途

```java
// BlueGreenStageAssembler.java
private DataPreparer createRedisAckDataPreparer(...) {
    return (ctx) -> {
        // 1. 获取服务实例列表
        List<String> endpoints = resolveEndpoints(...);
        
        // 2. 提取 Verify 端点路径（这才是 healthCheckPath 的真实含义）
        String healthCheckPath = extractHealthCheckPath(config, resources);
        // 例如: /actuator/bg-sdk/{tenantId}
        
        // 3. 组装完整的 Verify URL
        List<String> verifyUrls = endpoints.stream()
            .map(ep -> "http://" + ep + healthCheckPath)  // 组装验证 URL
            .collect(Collectors.toList());
        
        // 4. 配置重试参数（来自 infrastructure.healthCheck）
        int maxAttempts = resources.getConfigLoader()
            .getInfrastructure()
            .getHealthCheck()  // ← 实际是 Verify 配置
            .getMaxAttempts();
        
        int intervalSec = resources.getConfigLoader()
            .getInfrastructure()
            .getHealthCheck()  // ← 实际是 Verify 配置
            .getIntervalSeconds();
        
        // 5. 传递给 RedisAckService 用于 Verify 步骤
        ctx.addVariable("verifyUrls", verifyUrls);
        ctx.addVariable("verifyJsonPath", "$.metadata.version");  // 提取 footprint
        ctx.addVariable("retryMaxAttempts", maxAttempts);
        ctx.addVariable("retryDelay", Duration.ofSeconds(intervalSec));
    };
}
```

#### 配置语义修正

**旧配置名称**（误导）:
```yaml
infrastructure:
  healthCheck:  # ❌ 名称误导，实际不是健康检查
    defaultPath: "/actuator/bg-sdk/{tenantId}"
    intervalSeconds: 3
    maxAttempts: 10
```

**应改为**（更准确）:
```yaml
infrastructure:
  verify:  # ✅ 更准确的名称
    default-path: "/actuator/bg-sdk/{tenantId}"  # Verify 端点路径
    interval-seconds: 3  # Verify 重试间隔
    max-attempts: 10  # Verify 最大重试次数
```

**命名建议**:
- `healthCheck` → `verify` 或 `ackVerify`
- 更准确反映其在 RedisAck 流程中的作用

---

## 📋 修改范围清单

### 新增文件（11个）

#### 配置属性类（6个）
```
deploy/src/main/java/xyz/firestige/deploy/config/properties/
├── ExecutorProperties.java                          [新增]
├── InfrastructureProperties.java                   [新增]
│   ├── RedisProperties (内部类)                    [新增]
│   ├── NacosProperties (内部类)                    [新增]
│   ├── VerifyProperties (内部类)                   [新增] ← 重命名自 HealthCheckProperties
│   └── AuthProperties (内部类)                     [新增]
└── (StagesProperties - 移动现有类)
```

#### 自动装配类（2个）
```
deploy/src/main/java/xyz/firestige/deploy/autoconfigure/
├── ExecutorAutoConfiguration.java                  [新增]
└── InfrastructureAutoConfiguration.java            [新增]
```

#### 配置文件（3个）
```
deploy/src/main/resources/
├── application-dev.yml                              [新增]
├── application-test.yml                             [新增]
└── application-prod.yml                             [新增]
```

### 修改文件（6个）

#### 核心修改
```
deploy/src/main/java/xyz/firestige/deploy/
├── infrastructure/
│   ├── execution/stage/factory/
│   │   └── SharedStageResources.java               [修改] ← 防腐层适配
│   └── config/
│       └── DeploymentConfigLoader.java             [修改] ← 标记 @Deprecated
├── config/
│   ├── ExecutorStagesProperties.java               [移动+重构] → properties/StagesProperties.java
│   └── ExecutorStagesAutoConfiguration.java        [移动+重构] → autoconfigure/StagesAutoConfiguration.java
└── autoconfigure/
    └── ExecutorStagesAutoConfiguration.java        [重构] ← 重命名为 StagesAutoConfiguration
```

#### 配置文件修改
```
deploy/src/main/resources/
├── application.yml                                  [修改] ← 添加 executor.infrastructure
├── deploy-stages.yml                                [重命名] → deploy-stages.yml.deprecated
└── META-INF/spring/
    └── org.springframework.boot.autoconfigure.AutoConfiguration.imports  [修改]
```

### 移除/废弃（5个）

#### 废弃但保留（过渡期）
```
deploy/src/main/java/xyz/firestige/deploy/infrastructure/config/
├── DeploymentConfigLoader.java                     [@Deprecated] 保留 1-2 版本
└── model/
    ├── DeploymentConfig.java                       [@Deprecated] 保留 1-2 版本
    └── InfrastructureConfig.java                   [@Deprecated] 保留 1-2 版本
```

#### 重命名归档
```
deploy/src/main/resources/
└── deploy-stages.yml                                [重命名] → .deprecated
```

#### 元数据新增
```
deploy/src/main/resources/META-INF/
└── spring-configuration-metadata.json               [新增]
```

---

## 🔄 关键修改详解

### 1. SharedStageResources 防腐层适配（核心）

**修改前**:
```java
@Component
public class SharedStageResources {
    private final DeploymentConfigLoader configLoader;
    
    @Autowired
    public SharedStageResources(DeploymentConfigLoader configLoader, ...) {
        this.configLoader = configLoader;
    }
    
    public DeploymentConfigLoader getConfigLoader() {
        return configLoader;
    }
}
```

**修改后**:
```java
@Component
public class SharedStageResources {
    
    private final DeploymentConfigLoader configLoader;  // 旧（@Deprecated，过渡期）
    private final InfrastructureProperties infrastructure;  // 新
    
    @Autowired
    public SharedStageResources(
            @Autowired(required = false) DeploymentConfigLoader configLoader,  // 可选
            InfrastructureProperties infrastructure,  // 新配置
            ...) {
        
        this.configLoader = configLoader;
        this.infrastructure = infrastructure;
    }
    
    // ========== 新方法（推荐） ==========
    
    /**
     * 获取基础设施配置（新，推荐使用）
     */
    public InfrastructureProperties getInfrastructure() {
        return infrastructure;
    }
    
    /**
     * 获取 Redis Key 前缀
     */
    public String getRedisKeyPrefix() {
        return infrastructure.getRedis().getHashKeyPrefix();
    }
    
    /**
     * 获取 Redis Pub/Sub Topic
     */
    public String getRedisPubsubTopic() {
        return infrastructure.getRedis().getPubsubTopic();
    }
    
    /**
     * 获取 Verify 端点路径模板
     */
    public String getVerifyDefaultPath() {
        return infrastructure.getVerify().getDefaultPath();
    }
    
    /**
     * 获取 Verify 重试间隔（秒）
     */
    public int getVerifyIntervalSeconds() {
        return infrastructure.getVerify().getIntervalSeconds();
    }
    
    /**
     * 获取 Verify 最大重试次数
     */
    public int getVerifyMaxAttempts() {
        return infrastructure.getVerify().getMaxAttempts();
    }
    
    // ========== 旧方法（@Deprecated） ==========
    
    /**
     * 获取配置加载器（旧）
     * @deprecated 使用 getInfrastructure() 或具体的 getter 方法
     */
    @Deprecated
    public DeploymentConfigLoader getConfigLoader() {
        return configLoader;
    }
}
```

**关键设计**:
1. ✅ **双重注入**：同时保留新旧配置，过渡期并存
2. ✅ **防腐层增强**：提供便捷方法（getRedisKeyPrefix 等），消费者无需知道配置来源
3. ✅ **向后兼容**：旧方法标记 @Deprecated 但仍可用
4. ✅ **消费者无感**：Assembler 代码无需修改（或仅需微调）

### 2. Assembler 使用示例（可选优化）

#### 选项 A: 保持现有代码（无需修改）

```java
// BlueGreenStageAssembler.java - 完全不修改
private DataPreparer createRedisAckDataPreparer(TenantConfig config, SharedStageResources resources) {
    return (ctx) -> {
        // 旧代码继续工作（通过 @Deprecated 方法）
        String prefix = resources.getConfigLoader()
            .getInfrastructure()
            .getRedis()
            .getHashKeyPrefix();
        
        int maxAttempts = resources.getConfigLoader()
            .getInfrastructure()
            .getHealthCheck()  // ← 旧名称
            .getMaxAttempts();
        
        // ...
    };
}
```

#### 选项 B: 使用防腐层便捷方法（推荐）

```java
// BlueGreenStageAssembler.java - 简化调用
private DataPreparer createRedisAckDataPreparer(TenantConfig config, SharedStageResources resources) {
    return (ctx) -> {
        // 使用防腐层便捷方法
        String prefix = resources.getRedisKeyPrefix();  // ✅ 更简洁
        int maxAttempts = resources.getVerifyMaxAttempts();  // ✅ 语义更清晰
        
        // ...
    };
}
```

#### 选项 C: 直接使用新配置（最终状态）

```java
// BlueGreenStageAssembler.java - 直接注入 InfrastructureProperties
@Component
public class BlueGreenStageAssembler implements StageAssembler {
    
    private final InfrastructureProperties infrastructure;
    
    @Autowired
    public BlueGreenStageAssembler(InfrastructureProperties infrastructure) {
        this.infrastructure = infrastructure;
    }
    
    private DataPreparer createRedisAckDataPreparer(TenantConfig config) {
        return (ctx) -> {
            String prefix = infrastructure.getRedis().getHashKeyPrefix();
            int maxAttempts = infrastructure.getVerify().getMaxAttempts();
            
            // ...
        };
    }
}
```

**推荐策略**:
- **Phase 1**: 选项 A（零修改，验证新配置加载）
- **Phase 2**: 选项 B（利用防腐层简化调用）
- **Phase 3**: 选项 C（移除旧配置，直接注入）

### 3. InfrastructureProperties 设计（重点：Verify 命名）

```java
@ConfigurationProperties(prefix = "executor.infrastructure")
@Validated
public class InfrastructureProperties {
    
    @Valid
    @NotNull
    private RedisProperties redis = new RedisProperties();
    
    @Valid
    @NotNull
    private NacosProperties nacos = new NacosProperties();
    
    /**
     * Verify 配置（RedisAck Verify 步骤）
     * 注意：不是 Spring Actuator 健康检查，而是 ACK 验证端点配置
     */
    @Valid
    @NotNull
    private VerifyProperties verify = new VerifyProperties();  // ← 重命名
    
    private Map<String, List<String>> fallbackInstances = new HashMap<>();
    private Map<String, AuthProperties> auth = new HashMap<>();
    
    // 内部类
    
    public static class RedisProperties {
        @NotBlank
        private String hashKeyPrefix = "icc_ai_ops_srv:tenant_config:";
        
        @NotBlank
        private String pubsubTopic = "icc_ai_ops_srv:tenant_config:topic";
        
        // Getters/Setters
    }
    
    public static class NacosProperties {
        private boolean enabled = false;
        private String serverAddr = "127.0.0.1:8848";
        private boolean healthCheckEnabled = false;  // ← 这个才是 Nacos 健康检查
        private Map<String, String> services = new HashMap<>();
        
        // Getters/Setters
    }
    
    /**
     * Verify 端点配置（RedisAck Verify 步骤使用）
     */
    public static class VerifyProperties {
        /**
         * Verify 端点路径模板
         * 用于 RedisAck Verify 步骤构建验证 URL
         * 支持占位符: {tenantId}
         * 
         * 例如: /actuator/bg-sdk/{tenantId}
         * 最终 URL: http://instance:port/actuator/bg-sdk/tenant001
         */
        @NotBlank
        private String defaultPath = "/actuator/bg-sdk/{tenantId}";
        
        /**
         * Verify 重试间隔（秒）
         * RedisAck Verify 步骤的轮询间隔
         */
        @Min(1)
        private int intervalSeconds = 3;
        
        /**
         * Verify 最大重试次数
         * RedisAck Verify 步骤的最大尝试次数
         */
        @Min(1)
        private int maxAttempts = 10;
        
        // Getters/Setters
    }
    
    public static class AuthProperties {
        private boolean enabled = false;
        private String tokenProvider = "random";
        
        // Getters/Setters
    }
    
    // Getters/Setters
}
```

### 4. 配置文件迁移

#### deploy-stages.yml（旧）
```yaml
infrastructure:
  redis:
    hashKeyPrefix: "{$REDIS_HASH_PREFIX:icc_ai_ops_srv:tenant_config:}"
    pubsubTopic: "{$REDIS_PUBSUB_TOPIC:icc_ai_ops_srv:tenant_config:topic}"
  
  nacos:
    enabled: "{$NACOS_ENABLED:false}"
    serverAddr: "{$NACOS_SERVER_ADDR:127.0.0.1:8848}"
    # ...
  
  healthCheck:  # ← 旧名称（误导）
    defaultPath: "{$HEALTH_CHECK_PATH:/actuator/bg-sdk/{tenantId}}"
    intervalSeconds: 3
    maxAttempts: 10
```

#### application.yml（新）
```yaml
executor:
  infrastructure:
    redis:
      hash-key-prefix: ${REDIS_HASH_PREFIX:icc_ai_ops_srv:tenant_config:}
      pubsub-topic: ${REDIS_PUBSUB_TOPIC:icc_ai_ops_srv:tenant_config:topic}
    
    nacos:
      enabled: ${NACOS_ENABLED:false}
      server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
      health-check-enabled: ${NACOS_HEALTH_CHECK_ENABLED:false}  # ← Nacos 健康检查
      # ...
    
    verify:  # ← 新名称（更准确）
      default-path: ${VERIFY_DEFAULT_PATH:/actuator/bg-sdk/{tenantId}}
      interval-seconds: ${VERIFY_INTERVAL_SECONDS:3}
      max-attempts: ${VERIFY_MAX_ATTEMPTS:10}
```

**变更点**:
1. 占位符：`{$VAR}` → `${VAR}` (Spring 标准)
2. 命名风格：`camelCase` → `kebab-case` (Spring 推荐)
3. 语义修正：`healthCheck` → `verify` (更准确)

---

## 🎯 配置隔离验证结论

### 修改影响范围

| 层级 | 影响程度 | 说明 |
|------|---------|------|
| **消费者（Assembler）** | ✅ 零影响（选项 A）<br>⭐ 微调（选项 B）<br>🔄 重构（选项 C） | 通过防腐层隔离 |
| **防腐层（SharedStageResources）** | ⚠️ 修改 | 添加双重注入 + 便捷方法 |
| **配置加载（DeploymentConfigLoader）** | 🔄 废弃 | 标记 @Deprecated |
| **配置文件** | 🔄 迁移 | deploy-stages.yml → application.yml |

### 迁移路径

```
Phase 1: 基础设施（防腐层不破坏现有）
├── 创建 InfrastructureProperties
├── 修改 SharedStageResources（双重注入）
└── 验证：旧代码继续工作

Phase 2: 逐步优化（使用便捷方法）
├── Assembler 使用 resources.getRedisKeyPrefix()
├── 简化调用链
└── 验证：功能不变，代码更简洁

Phase 3: 废弃旧配置（最终状态）
├── 直接注入 InfrastructureProperties
├── 移除 DeploymentConfigLoader
└── 验证：完全使用新配置体系
```

---

## 📊 修改范围总结表

### 按修改类型统计

| 类型 | 数量 | 文件列表 |
|------|------|---------|
| **新增** | 11 | ExecutorProperties<br>InfrastructureProperties<br>ExecutorAutoConfiguration<br>InfrastructureAutoConfiguration<br>application-{dev,test,prod}.yml (3个)<br>spring-configuration-metadata.json (4个内部类) |
| **修改** | 6 | SharedStageResources (防腐层适配)<br>application.yml (添加配置)<br>AutoConfiguration.imports (更新 SPI)<br>StagesProperties (移动+重构)<br>StagesAutoConfiguration (移动+重构)<br>deploy-stages.yml (重命名) |
| **废弃** | 3 | DeploymentConfigLoader (@Deprecated)<br>DeploymentConfig (@Deprecated)<br>InfrastructureConfig (@Deprecated) |
| **移除** | 0 | (过渡期不删除任何文件) |

### 按影响范围统计

| 模块 | 新增 | 修改 | 废弃 | 影响度 |
|------|------|------|------|--------|
| config/properties | 6 | 0 | 0 | ✅ 新增 |
| autoconfigure | 2 | 2 | 0 | ⚠️ 中等 |
| infrastructure/config | 0 | 1 | 3 | 🔄 重构 |
| infrastructure/execution | 0 | 1 | 0 | ⚠️ 中等（防腐层） |
| resources | 3 | 2 | 0 | ⚠️ 中等 |
| **总计** | **11** | **6** | **3** | **20 个文件** |

---

## ❓ 关键决策点

### 1. 配置命名修正

**问题**: `healthCheck` 名称误导，实际是 RedisAck Verify 配置  
**建议**: 重命名为 `verify` 或 `ackVerify`

**投票**:
- [ ] A. `verify` (简洁，推荐)
- [ ] B. `ackVerify` (明确，但冗长)
- [ ] C. 保持 `healthCheck` (避免破坏性变更)

### 2. 迁移策略

**建议**: 选项 A（Phase 1 零修改）

**原因**:
1. ✅ 验证配置隔离设计有效
2. ✅ 最小化风险
3. ✅ 逐步优化，不急于一次重构

### 3. 旧配置保留期

**建议**: 保留 1-2 个版本

**计划**:
- v1.1: 新旧并存，标记 @Deprecated
- v1.2: 继续保留，添加移除警告
- v2.0: 移除旧配置

### 4. Configuration Metadata 优先级

**建议**: Phase 5 实施（非阻塞）

**原因**:
- IDE 支持是锦上添花
- 不影响功能实现
- 可独立作为低优先级任务

---

## ✅ 验收标准（修订）

### 配置隔离验证
- [ ] 使用选项 A（零修改）启动应用
- [ ] 所有 Assembler 正常工作
- [ ] 配置正确加载（新旧配置等价）
- [ ] 防腐层隔离生效（修改配置源不影响消费者）

### 功能验收
- [ ] 零配置可启动
- [ ] Profile 配置切换正确
- [ ] Nacos enabled=true/false 条件装配
- [ ] Verify 端点配置正确应用

### 质量验收
- [ ] 单元测试通过（配置绑定）
- [ ] 集成测试通过（端到端流程）
- [ ] 日志明确指出配置来源（新/旧）

---

## 📅 时间估算（修订）

| Phase | 任务 | 预计时间 |
|-------|------|---------|
| Phase 1 | 创建 Properties + 防腐层适配 | 4h |
| Phase 2 | 配置文件迁移 + 测试 | 2h |
| Phase 3 | Assembler 优化（选项 B） | 3h |
| Phase 4 | 废弃旧代码 + 文档 | 2h |
| Phase 5 | Configuration Metadata | 3h |
| **总计** | | **14h (约 2 天)** |

**减少原因**: 
- 验证了配置隔离设计良好
- 选项 A 零修改，风险低
- 防腐层已存在，只需扩展

---

**方案修订完成，等待评审** ✅

**核心改进**:
1. ✅ 验证配置隔离设计（防腐层保护消费者）
2. ✅ 明确新增/修改/移除范围（20个文件）
3. ✅ 澄清 healthCheck 实际含义（Verify 端点配置）
4. ✅ 提供三种迁移路径（选项 A/B/C）
5. ✅ 降低风险和工作量（14h vs 22h）

