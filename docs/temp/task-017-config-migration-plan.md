# T-017: 配置文件合并 - deploy-stages.yml → application.yml

> **任务 ID**: T-017  
> **优先级**: P1  
> **状态**: 待办  
> **创建时间**: 2025-11-24

---

## 1. 任务目标

将当前独立的 `deploy-stages.yml` 配置文件合并到 Spring Boot 标准的 `application.yml` 中，统一配置文件管理和加载逻辑。

---

## 2. 关键设计约束

### 2.1 Spring Boot 3.x AutoConfiguration 支持 ✅

**要求**：
- 使用 Spring Boot 3.x 的新 SPI 机制
- 不使用旧的 `spring.factories`，改用 `AutoConfiguration.imports`

**实现**：
```
# src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
xyz.firestige.deploy.autoconfigure.ExecutorStagesAutoConfiguration
```

**说明**：
- Spring Boot 2.7+ 开始支持新格式
- Spring Boot 3.0+ 推荐使用新格式，旧格式仍兼容但已废弃
- 新格式更清晰，每行一个配置类，无需 `EnableAutoConfiguration=` 前缀
- 项目已在使用此格式（参考 `ExecutorPersistenceAutoConfiguration`）

**验证**：
```bash
# 检查现有格式
cat src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

### 2.2 YAML Configuration Metadata（IDE 智能提示）✅

**要求**：
- 提供 `spring-configuration-metadata.json` 支持 IDE 自动补全
- 为所有配置属性提供描述和默认值
- 支持值提示（hints）

**实现步骤**：

#### 步骤 1：添加 Configuration Processor 依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-configuration-processor</artifactId>
    <optional>true</optional>
</dependency>
```

#### 步骤 2：在配置类中添加 JavaDoc

```java
@ConfigurationProperties(prefix = "executor.stages")
public class ExecutorStagesProperties {
    
    /**
     * 蓝绿网关阶段配置
     */
    private BlueGreenGatewayStageConfig blueGreenGateway = 
        BlueGreenGatewayStageConfig.defaultConfig();
    
    /**
     * Portal 阶段配置
     */
    private PortalStageConfig portal = 
        PortalStageConfig.defaultConfig();
    
    // getters/setters
}

public class BlueGreenGatewayStageConfig {
    /**
     * 是否启用蓝绿网关阶段
     */
    private boolean enabled = true;
    
    /**
     * 健康检查端点路径
     */
    private String healthCheckPath = "/health";
    
    /**
     * 健康检查间隔（秒）
     */
    private int healthCheckIntervalSeconds = 3;
    
    /**
     * 健康检查最大尝试次数
     */
    private int healthCheckMaxAttempts = 10;
    
    // getters/setters
}
```

#### 步骤 3：手动补充 Hints（可选）

```json
// src/main/resources/META-INF/additional-spring-configuration-metadata.json
{
  "hints": [
    {
      "name": "executor.stages.blue-green-gateway.steps[].type",
      "values": [
        {"value": "redis-write", "description": "Write configuration to Redis"},
        {"value": "health-check", "description": "Perform health check"},
        {"value": "pubsub-broadcast", "description": "Broadcast via Pub/Sub"},
        {"value": "http-request", "description": "Execute HTTP request"}
      ]
    }
  ]
}
```

**效果**：
- IDEA/VSCode 中输入 `executor.stages.` 时自动补全
- 显示属性描述和默认值
- 配置错误时实时提示

### 2.3 环境变量格式标准化 ✅

**要求**：
- 从非标准的 `{$ENV:defaultValue}` 迁移到标准的 `${ENV:defaultValue}` 格式
- 确保与 Spring 的属性占位符解析机制兼容

**迁移对照表**：

| 旧格式（deploy-stages.yml） | 新格式（application.yml） | 说明 |
|------------------------------|---------------------------|------|
| `{$GATEWAY_HOST:localhost}` | `${GATEWAY_HOST:localhost}` | 标准占位符 |
| `{$GATEWAY_PORT:8080}` | `${GATEWAY_PORT:8080}` | 数值型 |
| `{$ENABLED:true}` | `${ENABLED:true}` | 布尔型 |
| `{$TIMEOUT_MS:5000}` | `${GATEWAY_TIMEOUT_MS:5000}` | 建议加前缀避免冲突 |

**迁移示例**：

```yaml
# 旧格式（deploy-stages.yml）
blue-green-gateway:
  host: {$GATEWAY_HOST:localhost}
  port: {$GATEWAY_PORT:8080}
  timeout: {$GATEWAY_TIMEOUT:5000}
  enabled: {$GATEWAY_ENABLED:true}

# 新格式（application.yml）
executor:
  stages:
    blue-green-gateway:
      host: ${EXECUTOR_GATEWAY_HOST:localhost}
      port: ${EXECUTOR_GATEWAY_PORT:8080}
      timeout: ${EXECUTOR_GATEWAY_TIMEOUT:5000}
      enabled: ${EXECUTOR_GATEWAY_ENABLED:true}
```

**迁移工具脚本**（可选）：

```java
/**
 * 配置文件格式迁移工具
 */
public class ConfigMigrationUtil {
    
    private static final Pattern OLD_PATTERN = 
        Pattern.compile("\\{\\$([^:}]+):([^}]+)\\}");
    
    /**
     * 转换旧格式占位符为标准格式
     * 
     * @param value 原始值，如 "{$ENV:default}"
     * @return 标准格式，如 "${ENV:default}"
     */
    public static String convertPlaceholder(String value) {
        if (value == null) return null;
        
        Matcher matcher = OLD_PATTERN.matcher(value);
        return matcher.replaceAll("\\${$1:$2}");
    }
    
    /**
     * 批量转换配置文件
     */
    public static void migrateConfigFile(Path inputFile, Path outputFile) {
        try {
            List<String> lines = Files.readAllLines(inputFile);
            List<String> convertedLines = lines.stream()
                .map(ConfigMigrationUtil::convertPlaceholder)
                .collect(Collectors.toList());
            Files.write(outputFile, convertedLines);
        } catch (IOException e) {
            throw new RuntimeException("配置迁移失败", e);
        }
    }
}
```

**验证工具**：

```java
@Component
@ConditionalOnProperty("executor.config.validate-placeholders")
public class PlaceholderValidator implements ApplicationListener<ApplicationReadyEvent> {
    
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // 检查是否还有旧格式占位符
        checkForOldFormatPlaceholders(event.getApplicationContext());
    }
    
    private void checkForOldFormatPlaceholders(ApplicationContext context) {
        // 实现逻辑...
    }
}
```

### 2.4 配置加载容错与降级 ✅

**要求**：
- 配置缺失时提供合理默认值
- 配置加载异常不允许导致应用启动失败
- 提供配置验证但不阻塞启动

**实现策略**：

#### 策略 1：字段级默认值（推荐）

```java
@ConfigurationProperties(prefix = "executor.stages")
public class ExecutorStagesProperties {
    
    /**
     * 蓝绿网关配置（默认启用）
     */
    private BlueGreenGatewayStageConfig blueGreenGateway = 
        BlueGreenGatewayStageConfig.defaultConfig();
    
    /**
     * Portal 配置（默认启用）
     */
    private PortalStageConfig portal = 
        PortalStageConfig.defaultConfig();
    
    /**
     * ASBC 网关配置（默认禁用）
     */
    private ASBCGatewayStageConfig asbcGateway = 
        ASBCGatewayStageConfig.defaultConfig();
    
    // getters/setters
}

public class BlueGreenGatewayStageConfig {
    private boolean enabled = true;
    private String healthCheckPath = "/health";
    private String healthCheckVersionKey = "version";
    private int healthCheckIntervalSeconds = 3;
    private int healthCheckMaxAttempts = 10;
    private List<StepConfig> steps = new ArrayList<>();
    
    /**
     * 创建默认配置
     */
    public static BlueGreenGatewayStageConfig defaultConfig() {
        BlueGreenGatewayStageConfig config = new BlueGreenGatewayStageConfig();
        config.setEnabled(true);
        config.setHealthCheckPath("/health");
        config.setHealthCheckVersionKey("version");
        config.setHealthCheckIntervalSeconds(3);
        config.setHealthCheckMaxAttempts(10);
        config.setSteps(defaultSteps());
        return config;
    }
    
    private static List<StepConfig> defaultSteps() {
        List<StepConfig> steps = new ArrayList<>();
        steps.add(StepConfig.redisWrite());
        steps.add(StepConfig.healthCheck());
        return steps;
    }
}
```

#### 策略 2：构造后验证（非阻塞）

```java
@ConfigurationProperties(prefix = "executor.stages")
public class ExecutorStagesProperties implements InitializingBean {
    
    private static final Logger log = LoggerFactory.getLogger(ExecutorStagesProperties.class);
    
    @Override
    public void afterPropertiesSet() {
        // 配置验证（记录警告但不抛异常）
        validateAndFixConfiguration();
    }
    
    /**
     * 验证并修复配置
     * 原则：记录问题，自动修复，永不抛异常
     */
    private void validateAndFixConfiguration() {
        try {
            // 验证蓝绿网关配置
            if (blueGreenGateway == null) {
                log.warn("蓝绿网关配置缺失，使用默认配置");
                blueGreenGateway = BlueGreenGatewayStageConfig.defaultConfig();
            } else {
                validateBlueGreenGatewayConfig();
            }
            
            // 验证 Portal 配置
            if (portal == null) {
                log.warn("Portal 配置缺失，使用默认配置");
                portal = PortalStageConfig.defaultConfig();
            } else {
                validatePortalConfig();
            }
            
            // 验证 ASBC 网关配置
            if (asbcGateway == null) {
                log.warn("ASBC 网关配置缺失，使用默认配置（禁用状态）");
                asbcGateway = ASBCGatewayStageConfig.defaultConfig();
            }
            
            log.info("Executor stages 配置验证完成");
            log.debug("配置详情: blueGreenGateway={}, portal={}, asbcGateway={}", 
                blueGreenGateway.isEnabled(), 
                portal.isEnabled(), 
                asbcGateway.isEnabled());
                
        } catch (Exception e) {
            log.error("配置验证过程发生异常，将使用默认配置: {}", e.getMessage(), e);
            // 确保所有配置都有默认值
            ensureDefaultConfigurations();
        }
    }
    
    private void validateBlueGreenGatewayConfig() {
        if (blueGreenGateway.isEnabled()) {
            if (blueGreenGateway.getHealthCheckPath() == null || 
                blueGreenGateway.getHealthCheckPath().isBlank()) {
                log.warn("蓝绿网关健康检查路径为空，使用默认值: /health");
                blueGreenGateway.setHealthCheckPath("/health");
            }
            
            if (blueGreenGateway.getHealthCheckIntervalSeconds() <= 0) {
                log.warn("蓝绿网关健康检查间隔无效: {}, 使用默认值: 3", 
                    blueGreenGateway.getHealthCheckIntervalSeconds());
                blueGreenGateway.setHealthCheckIntervalSeconds(3);
            }
            
            if (blueGreenGateway.getHealthCheckMaxAttempts() <= 0) {
                log.warn("蓝绿网关健康检查最大尝试次数无效: {}, 使用默认值: 10", 
                    blueGreenGateway.getHealthCheckMaxAttempts());
                blueGreenGateway.setHealthCheckMaxAttempts(10);
            }
            
            if (blueGreenGateway.getSteps() == null || 
                blueGreenGateway.getSteps().isEmpty()) {
                log.warn("蓝绿网关未配置步骤，使用默认步骤");
                blueGreenGateway.setSteps(BlueGreenGatewayStageConfig.defaultSteps());
            }
        }
    }
    
    private void validatePortalConfig() {
        if (portal.isEnabled() && 
            (portal.getSteps() == null || portal.getSteps().isEmpty())) {
            log.warn("Portal 已启用但未配置步骤，使用默认步骤");
            portal.setSteps(PortalStageConfig.defaultSteps());
        }
    }
    
    private void ensureDefaultConfigurations() {
        if (blueGreenGateway == null) {
            blueGreenGateway = BlueGreenGatewayStageConfig.defaultConfig();
        }
        if (portal == null) {
            portal = PortalStageConfig.defaultConfig();
        }
        if (asbcGateway == null) {
            asbcGateway = ASBCGatewayStageConfig.defaultConfig();
        }
    }
}
```

#### 策略 3：AutoConfiguration 容错装配

```java
@Configuration
@EnableConfigurationProperties(ExecutorStagesProperties.class)
public class ExecutorStagesAutoConfiguration {
    
    private static final Logger log = LoggerFactory.getLogger(ExecutorStagesAutoConfiguration.class);
    
    /**
     * Stage 工厂（容错创建）
     */
    @Bean
    @ConditionalOnMissingBean
    public StageFactory stageFactory(ExecutorStagesProperties properties) {
        try {
            log.info("创建 StageFactory，配置: {}", properties);
            return new ConfigurableStageFactory(properties);
        } catch (Exception e) {
            log.error("创建 StageFactory 失败，使用降级实现: {}", e.getMessage(), e);
            // 返回降级实现（最小功能集）
            return createFallbackStageFactory();
        }
    }
    
    /**
     * 创建降级 StageFactory
     */
    private StageFactory createFallbackStageFactory() {
        log.warn("使用 FallbackStageFactory，功能受限");
        return new FallbackStageFactory();
    }
    
    /**
     * 配置健康检查
     */
    @Bean
    @ConditionalOnProperty(value = "management.health.executor-stages.enabled", havingValue = "true", matchIfMissing = true)
    public ExecutorStagesHealthIndicator executorStagesHealthIndicator(
            ExecutorStagesProperties properties) {
        return new ExecutorStagesHealthIndicator(properties);
    }
}
```

#### 策略 4：配置健康检查

```java
@Component
public class ExecutorStagesHealthIndicator implements HealthIndicator {
    
    private final ExecutorStagesProperties properties;
    
    public ExecutorStagesHealthIndicator(ExecutorStagesProperties properties) {
        this.properties = properties;
    }
    
    @Override
    public Health health() {
        try {
            Map<String, Object> details = new HashMap<>();
            
            // 检查各阶段配置状态
            details.put("blueGreenGateway", checkStageConfig(
                properties.getBlueGreenGateway(), "蓝绿网关"));
            details.put("portal", checkStageConfig(
                properties.getPortal(), "Portal"));
            details.put("asbcGateway", checkStageConfig(
                properties.getAsbcGateway(), "ASBC网关"));
            
            // 统计启用的阶段数
            long enabledCount = Stream.of(
                properties.getBlueGreenGateway(),
                properties.getPortal(),
                properties.getAsbcGateway()
            ).filter(config -> config != null && config.isEnabled()).count();
            
            details.put("enabledStages", enabledCount);
            details.put("totalStages", 3);
            
            // 判断健康状态
            boolean hasWarnings = details.values().stream()
                .anyMatch(v -> v instanceof Map && 
                    "WARNING".equals(((Map<?, ?>) v).get("status")));
            
            if (hasWarnings) {
                return Health.status("WARNING")
                    .withDetail("message", "部分配置存在问题，但应用可正常运行")
                    .withDetails(details)
                    .build();
            }
            
            return Health.up().withDetails(details).build();
                
        } catch (Exception e) {
            return Health.down()
                .withException(e)
                .withDetail("message", "配置验证异常，但应用仍可运行")
                .build();
        }
    }
    
    private Map<String, Object> checkStageConfig(Object config, String stageName) {
        Map<String, Object> result = new HashMap<>();
        
        if (config == null) {
            result.put("status", "WARNING");
            result.put("message", stageName + " 配置缺失，已使用默认配置");
            return result;
        }
        
        try {
            boolean enabled = (boolean) config.getClass()
                .getMethod("isEnabled")
                .invoke(config);
            
            result.put("status", "OK");
            result.put("enabled", enabled);
            
            if (!enabled) {
                result.put("message", stageName + " 已禁用");
            }
            
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("message", "配置检查失败: " + e.getMessage());
        }
        
        return result;
    }
}
```

#### 策略 5：启动时配置报告

```java
@Component
public class ExecutorStagesConfigurationReporter implements ApplicationListener<ApplicationReadyEvent> {
    
    private static final Logger log = LoggerFactory.getLogger(ExecutorStagesConfigurationReporter.class);
    
    private final ExecutorStagesProperties properties;
    
    public ExecutorStagesConfigurationReporter(ExecutorStagesProperties properties) {
        this.properties = properties;
    }
    
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        printConfigurationReport();
    }
    
    private void printConfigurationReport() {
        log.info("========================================");
        log.info("Executor Stages 配置报告");
        log.info("========================================");
        
        reportStageConfig("蓝绿网关", properties.getBlueGreenGateway());
        reportStageConfig("Portal", properties.getPortal());
        reportStageConfig("ASBC网关", properties.getAsbcGateway());
        
        log.info("========================================");
    }
    
    private void reportStageConfig(String stageName, Object config) {
        if (config == null) {
            log.warn("{}: 配置缺失（已使用默认配置）", stageName);
            return;
        }
        
        try {
            boolean enabled = (boolean) config.getClass()
                .getMethod("isEnabled")
                .invoke(config);
            
            if (enabled) {
                log.info("{}: ✓ 已启用", stageName);
                // 可以添加更多详细信息
            } else {
                log.info("{}: ✗ 已禁用", stageName);
            }
        } catch (Exception e) {
            log.error("{}: 配置读取失败: {}", stageName, e.getMessage());
        }
    }
}
```

---

## 3. 当前问题

### 3.1 现状
- 存在独立的 `deploy-stages.yml` 配置文件
- 使用自定义加载逻辑读取配置
- 使用非标准占位符格式 `{$ENV:default}`
- 与 Spring Boot 标准配置体系分离

### 3.2 问题
- 配置文件分散，不易管理
- 自定义加载逻辑增加维护成本
- 不符合 Spring Boot 最佳实践
- 难以利用 Spring 的配置特性（Profile、外部化配置等）
- IDE 无智能提示
- 配置错误可能导致启动失败

---

## 4. 期望结果

### 4.1 配置结构

```yaml
# application.yml
executor:
  stages:
    # 蓝绿网关配置
    blue-green-gateway:
      enabled: ${EXECUTOR_BGW_ENABLED:true}
      health-check-path: ${EXECUTOR_BGW_HEALTH_PATH:/health}
      health-check-version-key: ${EXECUTOR_BGW_VERSION_KEY:version}
      health-check-interval-seconds: ${EXECUTOR_BGW_INTERVAL:3}
      health-check-max-attempts: ${EXECUTOR_BGW_MAX_ATTEMPTS:10}
      steps:
        - type: redis-write
          key-pattern: "gateway:config:{tenantId}"
        - type: health-check
    
    # Portal 配置
    portal:
      enabled: ${EXECUTOR_PORTAL_ENABLED:true}
      steps:
        - type: redis-write
        - type: pubsub-broadcast
          channel: "portal:reload"
    
    # ASBC 网关配置
    asbc-gateway:
      enabled: ${EXECUTOR_ASBC_ENABLED:false}
```

### 4.2 加载逻辑
- 移除自定义配置加载代码
- 使用 Spring Boot 的 `@ConfigurationProperties` 绑定
- 支持多环境配置（dev/test/prod）
- 支持外部化配置（命令行参数、环境变量等）
- 配置缺失时使用默认值，不阻塞启动

---

## 5. 实施计划

### 5.1 配置迁移

**步骤 1**：分析现有配置结构
```bash
# 检查 deploy-stages.yml 位置和内容
find . -name "deploy-stages.yml"
cat src/main/resources/deploy-stages.yml
```

**步骤 2**：创建配置类

```java
// ExecutorStagesProperties.java
@ConfigurationProperties(prefix = "executor.stages")
public class ExecutorStagesProperties implements InitializingBean {
    // ...完整实现见上文
}

// BlueGreenGatewayStageConfig.java
public class BlueGreenGatewayStageConfig {
    // ...完整实现见上文
}

// PortalStageConfig.java
// ASBCGatewayStageConfig.java
// StepConfig.java
```

**步骤 3**：转换占位符格式
```bash
# 使用脚本或手动转换
# {$VAR:default} -> ${VAR:default}
```

**步骤 4**：迁移到 application.yml
```yaml
# 将转换后的配置合并到 application.yml
```

**步骤 5**：添加 Configuration Metadata
```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-configuration-processor</artifactId>
    <optional>true</optional>
</dependency>
```

### 5.2 代码重构

**步骤 1**：识别自定义加载逻辑
```bash
# 搜索配置加载相关代码
grep -r "deploy-stages.yml" --include="*.java"
grep -r "YamlPropertySourceLoader" --include="*.java"
```

**步骤 2**：创建 AutoConfiguration
```java
@Configuration
@EnableConfigurationProperties(ExecutorStagesProperties.class)
public class ExecutorStagesAutoConfiguration {
    // ...完整实现见上文
}
```

**步骤 3**：注册 AutoConfiguration
```
# META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
xyz.firestige.deploy.autoconfigure.ExecutorStagesAutoConfiguration
```

**步骤 4**：更新使用配置的类
```java
// 旧方式（自定义加载）
DeployStagesConfig config = customLoader.load();

// 新方式（Spring 注入）
@Autowired
private ExecutorStagesProperties stagesProperties;
```

### 5.3 测试验证

**单元测试**：
```java
@SpringBootTest
@TestPropertySource(properties = {
    "executor.stages.blue-green-gateway.enabled=true",
    "executor.stages.blue-green-gateway.health-check-path=/actuator/health"
})
class ExecutorStagesPropertiesTest {
    
    @Autowired
    private ExecutorStagesProperties properties;
    
    @Test
    void shouldLoadConfiguration() {
        assertThat(properties.getBlueGreenGateway()).isNotNull();
        assertThat(properties.getBlueGreenGateway().isEnabled()).isTrue();
        assertThat(properties.getBlueGreenGateway().getHealthCheckPath())
            .isEqualTo("/actuator/health");
    }
    
    @Test
    void shouldUseDefaultsWhenNotConfigured() {
        // 测试默认值
    }
}
```

**配置缺失测试**：
```java
@SpringBootTest(properties = {
    "executor.stages.blue-green-gateway.enabled="  // 空值
})
class ExecutorStagesDefaultsTest {
    
    @Autowired
    private ExecutorStagesProperties properties;
    
    @Test
    void shouldNotFailOnMissingConfig() {
        // 应该使用默认配置，不抛异常
        assertThat(properties.getBlueGreenGateway()).isNotNull();
    }
}
```

**多环境测试**：
```java
@SpringBootTest
@ActiveProfiles("prod")
class ExecutorStagesProductionTest {
    // 测试生产环境配置
}
```

---

## 6. Definition of Done

- [ ] deploy-stages.yml 内容已迁移到 application.yml
- [ ] 占位符格式已标准化（`${VAR:default}`）
- [ ] 创建 ExecutorStagesProperties 及相关配置类
- [ ] 所有配置类实现默认值和容错逻辑
- [ ] 移除自定义配置加载逻辑
- [ ] 创建 ExecutorStagesAutoConfiguration
- [ ] 注册到 AutoConfiguration.imports（Spring Boot 3.x 格式）
- [ ] 添加 spring-boot-configuration-processor 依赖
- [ ] 所有使用配置的地方已更新为 Spring 注入
- [ ] 单元测试覆盖率 > 80%
- [ ] 配置缺失/异常测试通过（不阻塞启动）
- [ ] 多环境配置测试通过（dev/test/prod）
- [ ] IDE 智能提示验证通过
- [ ] 添加配置健康检查
- [ ] 添加启动配置报告
- [ ] 文档已更新（README.md、architecture-overview.md）
- [ ] 代码审查通过

---

## 7. 风险与缓解

### 7.1 风险

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|---------|
| 配置结构变更影响现有功能 | 高 | 中 | 保留 deploy-stages.yml 作为备份，渐进式迁移 |
| 占位符格式转换错误 | 中 | 低 | 编写转换工具，充分测试 |
| 配置绑定失败导致启动失败 | 高 | 低 | 实现完善的默认值和容错逻辑 |
| IDE 智能提示不生效 | 低 | 低 | 验证 metadata 生成，检查 IDE 设置 |

### 7.2 缓解措施

1. **分阶段迁移**：
   - Phase 1：创建新配置类，与旧配置并存
   - Phase 2：逐步切换使用新配置
   - Phase 3：移除旧配置加载逻辑
   - Phase 4：清理 deploy-stages.yml

2. **兼容性保证**：
   - 保留旧配置文件作为备份
   - 提供配置迁移工具
   - 充分的测试覆盖

3. **容错保证**：
   - 所有配置字段提供默认值
   - 配置验证不抛异常
   - 提供降级机制

---

## 8. 参考资料

- [Spring Boot 3.x AutoConfiguration](https://docs.spring.io/spring-boot/docs/3.0.0/reference/html/features.html#features.developing-auto-configuration)
- [Spring Boot Configuration Properties](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
- [Spring Boot Configuration Metadata](https://docs.spring.io/spring-boot/docs/current/reference/html/configuration-metadata.html)
- [Spring Boot Profiles](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.profiles)
- 项目现有配置结构：`ExecutorProperties`, `ExecutorPersistenceProperties`
- 项目现有 AutoConfiguration：`ExecutorPersistenceAutoConfiguration`

