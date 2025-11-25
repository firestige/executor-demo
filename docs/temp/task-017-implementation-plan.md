# T-017 配置加载解耦方案实施计划

> **任务 ID**: T-017  
> **实施方案版本**: v1.0  
> **创建时间**: 2025-11-24  
> **预计工期**: 3-5 天

---

## 1. 实施概览

### 1.1 目标

基于完全解耦的配置加载机制，实现：
- ✅ 业务变更时只需修改 Properties 数据结构
- ✅ 配置类自动发现和注册
- ✅ 加载逻辑零修改

### 1.2 实施阶段

| 阶段 | 任务 | 工期 | 交付物 |
|------|------|------|--------|
| Phase 1 | 核心接口和工具类 | 0.5天 | StageConfigurable、ValidationResult |
| Phase 2 | 配置容器重构 | 1天 | ExecutorStagesProperties |
| Phase 3 | 现有配置类迁移 | 1天 | 3个配置类实现接口 |
| Phase 4 | 健康检查和报告重构 | 0.5天 | 解耦的 HealthIndicator 和 Reporter |
| Phase 5 | 测试和验证 | 1-2天 | 完整测试套件 |

---

## 2. Phase 1: 核心接口和工具类

### 2.1 任务清单

- [ ] 创建 `StageConfigurable` 接口
- [ ] 创建 `ValidationResult` 类
- [ ] 创建配置工具类

### 2.2 文件结构

```
src/main/java/xyz/firestige/deploy/
├── config/
│   ├── stage/
│   │   ├── StageConfigurable.java          # 新建
│   │   ├── ValidationResult.java           # 新建
│   │   └── StageConfigUtils.java           # 新建（工具类）
```

### 2.3 实施步骤

#### 步骤 1.1: 创建 StageConfigurable 接口

```java
package xyz.firestige.deploy.infrastructure.execution.stage.config.stage;

/**
 * 可配置阶段标记接口
 *
 * <p>所有阶段配置类实现此接口，以支持自动发现和统一管理。
 *
 * <p>设计理念：
 * <ul>
 *   <li>约定优于配置：通过接口约定行为</li>
 *   <li>零侵入扩展：新增配置类只需实现接口</li>
 *   <li>自动发现：无需手动注册配置类</li>
 * </ul>
 *
 * @since T-017
 */
public interface StageConfigurable {

    /**
     * 是否启用此阶段
     *
     * @return true 如果阶段已启用
     */
    boolean isEnabled();

    /**
     * 阶段名称（用于日志和报告）
     *
     * <p>默认实现：从类名推断（移除 "StageConfig" 或 "Config" 后缀）
     *
     * @return 阶段显示名称
     */
    default String getStageName() {
        String className = this.getClass().getSimpleName();
        return className
                .replace("StageConfig", "")
                .replace("Config", "");
    }

    /**
     * 验证配置有效性
     *
     * <p>设计原则：
     * <ul>
     *   <li>永不抛异常：返回验证结果，不阻塞启动</li>
     *   <li>自动修复：发现问题时尝试使用默认值</li>
     *   <li>记录警告：将问题记录在 ValidationResult 中</li>
     * </ul>
     *
     * <p>默认实现：返回成功（无验证）
     *
     * @return 验证结果
     */
    default ValidationResult validate() {
        return ValidationResult.success();
    }
}
```

**验证点**：
- [ ] 接口编译通过
- [ ] JavaDoc 完整清晰
- [ ] 包路径正确

---

#### 步骤 1.2: 创建 ValidationResult 类

```java
package xyz.firestige.deploy.infrastructure.execution.stage.config.stage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 配置验证结果
 *
 * <p>不可变对象，包含验证状态、警告和错误信息。
 *
 * @since T-017
 */
public class ValidationResult {

    private final boolean valid;
    private final List<String> warnings;
    private final List<String> errors;

    private ValidationResult(boolean valid, List<String> warnings, List<String> errors) {
        this.valid = valid;
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }

    /**
     * 创建成功结果
     */
    public static ValidationResult success() {
        return new ValidationResult(true, List.of(), List.of());
    }

    /**
     * 创建带警告的成功结果
     */
    public static ValidationResult warning(String message) {
        return new ValidationResult(true, List.of(message), List.of());
    }

    /**
     * 创建失败结果
     */
    public static ValidationResult error(String message) {
        return new ValidationResult(false, List.of(), List.of(message));
    }

    /**
     * 创建 Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    // Getters

    public boolean isValid() {
        return valid;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public List<String> getErrors() {
        return errors;
    }

    /**
     * ValidationResult Builder
     */
    public static class Builder {
        private boolean valid = true;
        private final List<String> warnings = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();

        public Builder warning(String message) {
            this.warnings.add(message);
            return this;
        }

        public Builder error(String message) {
            this.errors.add(message);
            this.valid = false;
            return this;
        }

        public Builder warnings(List<String> messages) {
            this.warnings.addAll(messages);
            return this;
        }

        public Builder errors(List<String> messages) {
            this.errors.addAll(messages);
            if (!messages.isEmpty()) {
                this.valid = false;
            }
            return this;
        }

        public ValidationResult build() {
            return new ValidationResult(valid, warnings, errors);
        }
    }

    @Override
    public String toString() {
        return "ValidationResult{" +
                "valid=" + valid +
                ", warnings=" + warnings.size() +
                ", errors=" + errors.size() +
                '}';
    }
}
```

**验证点**：
- [ ] 类编译通过
- [ ] Builder 模式正确实现
- [ ] 不可变性保证

---

#### 步骤 1.3: 创建工具类

```java
package xyz.firestige.deploy.infrastructure.execution.stage.config.stage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 阶段配置工具类
 *
 * @since T-017
 */
public class StageConfigUtils {

    private static final Pattern CAMEL_CASE_PATTERN =
            Pattern.compile("([a-z])([A-Z])");

    /**
     * 驼峰命名转烤串命名
     *
     * @param camelCase 驼峰命名字符串，如 "blueGreenGateway"
     * @return 烤串命名字符串，如 "blue-green-gateway"
     */
    public static String toKebabCase(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return camelCase;
        }

        Matcher matcher = CAMEL_CASE_PATTERN.matcher(camelCase);
        return matcher.replaceAll("$1-$2").toLowerCase();
    }

    /**
     * 烤串命名转驼峰命名
     *
     * @param kebabCase 烤串命名字符串，如 "blue-green-gateway"
     * @return 驼峰命名字符串，如 "blueGreenGateway"
     */
    public static String toCamelCase(String kebabCase) {
        if (kebabCase == null || kebabCase.isEmpty()) {
            return kebabCase;
        }

        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = false;

        for (char c : kebabCase.toCharArray()) {
            if (c == '-') {
                capitalizeNext = true;
            } else {
                if (capitalizeNext) {
                    result.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    result.append(c);
                }
            }
        }

        return result.toString();
    }

    private StageConfigUtils() {
        throw new UnsupportedOperationException("Utility class");
    }
}
```

**验证点**：
- [ ] 工具方法单元测试通过
- [ ] 边界情况处理正确

---

### 2.4 Phase 1 验收标准

- [ ] 3 个新文件创建完成
- [ ] 所有代码编译通过
- [ ] 单元测试覆盖率 > 90%
- [ ] JavaDoc 完整

---

## 3. Phase 2: 配置容器重构

### 3.1 任务清单

- [ ] 备份现有 `ExecutorStagesProperties`
- [ ] 重构为支持自动发现的版本
- [ ] 添加自动注册机制
- [ ] 添加统一验证机制

### 3.2 实施步骤

#### 步骤 2.1: 备份现有实现

```bash
# 创建备份
cp src/main/java/xyz/firestige/deploy/config/ExecutorStagesProperties.java \
   src/main/java/xyz/firestige/deploy/config/ExecutorStagesProperties.java.bak

# 或使用 Git 分支
git checkout -b feature/t-017-config-decoupling
```

---

#### 步骤 2.2: 重构 ExecutorStagesProperties

```java
package xyz.firestige.deploy.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import xyz.firestige.deploy.infrastructure.execution.stage.config.stage.StageConfigurable;
import xyz.firestige.deploy.infrastructure.execution.stage.config.stage.StageConfigUtils;
import xyz.firestige.deploy.infrastructure.execution.stage.config.stage.ValidationResult;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 执行阶段配置容器
 *
 * <p>完全解耦的设计：
 * <ul>
 *   <li>通过 Map 统一管理所有阶段配置</li>
 *   <li>自动发现实现 StageConfigurable 的字段</li>
 *   <li>统一验证，永不抛异常</li>
 * </ul>
 *
 * <p>扩展方式：
 * <pre>
 * // 1. 创建配置类实现 StageConfigurable
 * public class NewServiceConfig implements StageConfigurable { ... }
 *
 * // 2. 在此类添加字段（仅此一处修改）
 * {@literal @}NestedConfigurationProperty
 * private NewServiceConfig newService;
 *
 * // 3. 无需修改其他任何代码，自动生效！
 * </pre>
 *
 * @since T-017
 */
@ConfigurationProperties(prefix = "executor.stages")
public class ExecutorStagesProperties implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(ExecutorStagesProperties.class);

    /**
     * 所有阶段配置的统一容器
     * Key: 阶段名称（blue-green-gateway, portal, asbc-gateway, ...）
     * Value: 具体配置对象
     */
    private final Map<String, StageConfigurable> stages = new LinkedHashMap<>();

    // ========== 具体配置字段（用于 Spring 绑定）==========

    /**
     * 蓝绿网关阶段配置
     */
    @NestedConfigurationProperty
    private BlueGreenGatewayStageConfig blueGreenGateway;

    /**
     * Portal 阶段配置
     */
    @NestedConfigurationProperty
    private PortalStageConfig portal;

    /**
     * ASBC 网关阶段配置
     */
    @NestedConfigurationProperty
    private ASBCGatewayStageConfig asbcGateway;

    // 未来新增配置只需在此添加字段，无需修改其他逻辑
    // @NestedConfigurationProperty
    // private NewServiceStageConfig newService;

    @Override
    public void afterPropertiesSet() {
        log.info("开始初始化 Executor Stages 配置...");

        // 自动发现所有配置字段并注册到统一容器
        registerStageConfigurations();

        // 统一验证所有配置
        validateAllConfigurations();

        log.info("Executor Stages 配置初始化完成，共 {} 个阶段，{} 个已启用",
                stages.size(),
                stages.values().stream().filter(StageConfigurable::isEnabled).count());
    }

    /**
     * 自动发现并注册所有阶段配置
     *
     * <p>通过反射找到所有实现 StageConfigurable 的字段，
     * 自动注册到统一容器，无需手动维护配置列表。
     */
    private void registerStageConfigurations() {
        try {
            Field[] fields = this.getClass().getDeclaredFields();
            int registeredCount = 0;

            for (Field field : fields) {
                // 跳过非配置字段
                if (shouldSkipField(field)) {
                    continue;
                }

                field.setAccessible(true);
                Object fieldValue = field.get(this);

                // 检查字段类型是否实现 StageConfigurable
                if (!StageConfigurable.class.isAssignableFrom(field.getType())) {
                    continue;
                }

                // 如果字段为 null，尝试创建默认配置
                if (fieldValue == null) {
                    fieldValue = createDefaultConfig(field.getType());
                    if (fieldValue != null) {
                        field.set(this, fieldValue);
                        log.debug("字段 {} 为 null，已创建默认配置", field.getName());
                    } else {
                        log.warn("无法为字段 {} 创建默认配置", field.getName());
                        continue;
                    }
                }

                // 注册到统一容器
                StageConfigurable config = (StageConfigurable) fieldValue;
                String stageName = StageConfigUtils.toKebabCase(field.getName());
                stages.put(stageName, config);
                registeredCount++;

                log.debug("注册阶段配置: {} -> {} (enabled={})",
                        stageName,
                        config.getClass().getSimpleName(),
                        config.isEnabled());
            }

            log.info("已注册 {} 个阶段配置", registeredCount);

        } catch (Exception e) {
            log.error("注册阶段配置失败: {}", e.getMessage(), e);
            // 不抛异常，确保应用可以启动
        }
    }

    /**
     * 判断是否应该跳过字段
     */
    private boolean shouldSkipField(Field field) {
        String fieldName = field.getName();
        // 跳过统一容器本身和日志对象
        return fieldName.equals("stages") ||
                fieldName.equals("log") ||
                fieldName.equals("$jacocoData") ||  // Jacoco 插桩字段
                field.isSynthetic();  // 合成字段
    }

    /**
     * 统一验证所有配置
     *
     * <p>关键原则：永不抛异常，只记录警告和错误
     */
    private void validateAllConfigurations() {
        int validCount = 0;
        int warningCount = 0;
        int errorCount = 0;

        for (Map.Entry<String, StageConfigurable> entry : stages.entrySet()) {
            String stageName = entry.getKey();
            StageConfigurable config = entry.getValue();

            try {
                ValidationResult result = config.validate();

                if (!result.isValid()) {
                    errorCount++;
                    log.error("阶段配置验证失败: {}, 错误: {}",
                            stageName,
                            String.join("; ", result.getErrors()));
                    // 不抛异常，允许应用继续启动
                } else {
                    validCount++;
                }

                if (!result.getWarnings().isEmpty()) {
                    warningCount++;
                    log.warn("阶段配置警告: {}, 警告: {}",
                            stageName,
                            String.join("; ", result.getWarnings()));
                }

            } catch (Exception e) {
                errorCount++;
                log.error("验证阶段配置异常: {}, 错误: {}", stageName, e.getMessage(), e);
                // 不抛异常，允许应用继续启动
            }
        }

        log.info("配置验证完成: 成功 {}, 警告 {}, 错误 {}", validCount, warningCount, errorCount);
    }

    /**
     * 创建默认配置
     *
     * <p>尝试两种方式：
     * <ol>
     *   <li>调用静态 defaultConfig() 方法（推荐）</li>
     *   <li>调用无参构造函数</li>
     * </ol>
     */
    @SuppressWarnings("unchecked")
    private StageConfigurable createDefaultConfig(Class<?> configClass) {
        try {
            // 方式 1：尝试调用静态 defaultConfig() 方法
            try {
                Method defaultConfigMethod = configClass.getMethod("defaultConfig");
                return (StageConfigurable) defaultConfigMethod.invoke(null);
            } catch (NoSuchMethodException e) {
                // 方式 2：尝试无参构造函数
                return (StageConfigurable) configClass.getDeclaredConstructor().newInstance();
            }
        } catch (Exception e) {
            log.error("创建默认配置失败: {}", configClass.getSimpleName(), e);
            return null;
        }
    }

    // ========== 公共访问方法 ==========

    /**
     * 获取所有阶段配置（不可变视图）
     */
    public Map<String, StageConfigurable> getAllStages() {
        return Collections.unmodifiableMap(stages);
    }

    /**
     * 获取指定阶段配置
     *
     * @param stageName 阶段名称（kebab-case）
     * @param configClass 配置类型
     * @return 配置对象，不存在则返回 null
     */
    public <T extends StageConfigurable> T getStage(String stageName, Class<T> configClass) {
        StageConfigurable config = stages.get(stageName);
        return config != null ? configClass.cast(config) : null;
    }

    /**
     * 获取所有已启用的阶段
     */
    public Map<String, StageConfigurable> getEnabledStages() {
        return stages.entrySet().stream()
                .filter(entry -> entry.getValue().isEnabled())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    /**
     * 检查指定阶段是否存在且已启用
     */
    public boolean isStageEnabled(String stageName) {
        StageConfigurable config = stages.get(stageName);
        return config != null && config.isEnabled();
    }

    // ========== 兼容性方法（保留原有 getter/setter）==========

    public BlueGreenGatewayStageConfig getBlueGreenGateway() {
        return blueGreenGateway;
    }

    public void setBlueGreenGateway(BlueGreenGatewayStageConfig blueGreenGateway) {
        this.blueGreenGateway = blueGreenGateway;
    }

    public PortalStageConfig getPortal() {
        return portal;
    }

    public void setPortal(PortalStageConfig portal) {
        this.portal = portal;
    }

    public ASBCGatewayStageConfig getAsbcGateway() {
        return asbcGateway;
    }

    public void setAsbcGateway(ASBCGatewayStageConfig asbcGateway) {
        this.asbcGateway = asbcGateway;
    }
}
```

**验证点**：
- [ ] 编译通过
- [ ] 自动发现逻辑正确
- [ ] 日志输出清晰
- [ ] 兼容性 getter/setter 保留

---

### 3.3 Phase 2 验收标准

- [ ] ExecutorStagesProperties 重构完成
- [ ] 自动发现机制工作正常
- [ ] 统一验证机制工作正常
- [ ] 单元测试通过

---

## 4. Phase 3: 现有配置类迁移

### 4.1 任务清单

- [ ] BlueGreenGatewayStageConfig 实现 StageConfigurable
- [ ] PortalStageConfig 实现 StageConfigurable
- [ ] ASBCGatewayStageConfig 实现 StageConfigurable

### 4.2 实施步骤

#### 步骤 3.1: 迁移 BlueGreenGatewayStageConfig

```java
package xyz.firestige.deploy.config;

import xyz.firestige.deploy.infrastructure.execution.stage.config.stage.StageConfigurable;
import xyz.firestige.deploy.infrastructure.execution.stage.config.stage.ValidationResult;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
 * 蓝绿网关阶段配置
 *
 * @since T-017 - 实现 StageConfigurable 接口
 */
public class BlueGreenGatewayStageConfig implements StageConfigurable {

    @NotNull(message = "enabled 不能为 null")
    private Boolean enabled = true;

    @NotBlank(message = "healthCheckPath 不能为空")
    private String healthCheckPath = "/health";

    private String healthCheckVersionKey = "version";

    @Min(value = 1, message = "healthCheckIntervalSeconds 必须 >= 1")
    private Integer healthCheckIntervalSeconds = 3;

    @Min(value = 1, message = "healthCheckMaxAttempts 必须 >= 1")
    private Integer healthCheckMaxAttempts = 10;

    @Valid
    private List<StepConfig> steps = new ArrayList<>();

    // ========== StageConfigurable 实现 ==========

    @Override
    public boolean isEnabled() {
        return enabled != null && enabled;
    }

    @Override
    public String getStageName() {
        return "蓝绿网关";
    }

    @Override
    public ValidationResult validate() {
        ValidationResult.Builder result = ValidationResult.builder();

        // 只在启用时验证详细配置
        if (!isEnabled()) {
            return result.build();
        }

        // 验证健康检查路径
        if (healthCheckPath == null || healthCheckPath.isBlank()) {
            result.warning("健康检查路径为空，将使用默认值: /health");
            this.healthCheckPath = "/health";
        }

        // 验证健康检查间隔
        if (healthCheckIntervalSeconds == null || healthCheckIntervalSeconds <= 0) {
            result.warning(String.format(
                    "健康检查间隔无效: %s，将使用默认值: 3",
                    healthCheckIntervalSeconds));
            this.healthCheckIntervalSeconds = 3;
        }

        // 验证最大尝试次数
        if (healthCheckMaxAttempts == null || healthCheckMaxAttempts <= 0) {
            result.warning(String.format(
                    "健康检查最大尝试次数无效: %s，将使用默认值: 10",
                    healthCheckMaxAttempts));
            this.healthCheckMaxAttempts = 10;
        }

        // 验证步骤配置
        if (steps == null || steps.isEmpty()) {
            result.warning("未配置步骤，将使用默认步骤");
            this.steps = defaultSteps();
        }

        return result.build();
    }

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

    /**
     * 默认步骤
     */
    private static List<StepConfig> defaultSteps() {
        List<StepConfig> steps = new ArrayList<>();
        // 假设 StepConfig 有静态工厂方法
        // steps.add(StepConfig.redisWrite());
        // steps.add(StepConfig.healthCheck());
        return steps;
    }

    // ========== Getters and Setters ==========

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getHealthCheckPath() {
        return healthCheckPath;
    }

    public void setHealthCheckPath(String healthCheckPath) {
        this.healthCheckPath = healthCheckPath;
    }

    public String getHealthCheckVersionKey() {
        return healthCheckVersionKey;
    }

    public void setHealthCheckVersionKey(String healthCheckVersionKey) {
        this.healthCheckVersionKey = healthCheckVersionKey;
    }

    public Integer getHealthCheckIntervalSeconds() {
        return healthCheckIntervalSeconds;
    }

    public void setHealthCheckIntervalSeconds(Integer healthCheckIntervalSeconds) {
        this.healthCheckIntervalSeconds = healthCheckIntervalSeconds;
    }

    public Integer getHealthCheckMaxAttempts() {
        return healthCheckMaxAttempts;
    }

    public void setHealthCheckMaxAttempts(Integer healthCheckMaxAttempts) {
        this.healthCheckMaxAttempts = healthCheckMaxAttempts;
    }

    public List<StepConfig> getSteps() {
        return steps;
    }

    public void setSteps(List<StepConfig> steps) {
        this.steps = steps;
    }
}
```

**验证点**：
- [ ] 实现 StageConfigurable 接口
- [ ] validate() 方法逻辑正确
- [ ] defaultConfig() 方法可用
- [ ] 编译通过

---

#### 步骤 3.2: 迁移其他配置类

按照相同模式迁移 `PortalStageConfig` 和 `ASBCGatewayStageConfig`：

1. 实现 `StageConfigurable` 接口
2. 实现 `isEnabled()` 方法
3. 重写 `getStageName()` 方法（可选）
4. 实现 `validate()` 方法
5. 添加 `defaultConfig()` 静态方法

---

### 4.3 Phase 3 验收标准

- [ ] 3 个配置类全部实现 StageConfigurable
- [ ] 所有配置类编译通过
- [ ] validate() 方法测试通过
- [ ] defaultConfig() 方法测试通过

---

## 5. Phase 4: 健康检查和报告重构

### 5.1 任务清单

- [ ] 重构 ExecutorStagesHealthIndicator
- [ ] 重构 ExecutorStagesConfigurationReporter
- [ ] 移除硬编码逻辑

### 5.2 实施步骤

#### 步骤 4.1: 重构健康检查

创建文件：`src/main/java/xyz/firestige/deploy/health/ExecutorStagesHealthIndicator.java`

```java
package xyz.firestige.deploy.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import xyz.firestige.deploy.config.ExecutorStagesProperties;
import xyz.firestige.deploy.infrastructure.execution.stage.config.stage.StageConfigurable;
import xyz.firestige.deploy.infrastructure.execution.stage.config.stage.ValidationResult;

import java.util.HashMap;
import java.util.Map;

/**
 * Executor Stages 健康检查
 *
 * <p>完全解耦的实现：
 * <ul>
 *   <li>自动发现所有阶段配置</li>
 *   <li>无需硬编码配置类列表</li>
 *   <li>新增配置类自动包含在健康检查中</li>
 * </ul>
 *
 * @since T-017
 */
@Component
public class ExecutorStagesHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(ExecutorStagesHealthIndicator.class);

    private final ExecutorStagesProperties properties;

    public ExecutorStagesHealthIndicator(ExecutorStagesProperties properties) {
        this.properties = properties;
    }

    @Override
    public Health health() {
        try {
            Map<String, Object> details = new HashMap<>();

            // 遍历所有阶段配置（自动发现，无需硬编码）
            Map<String, StageConfigurable> allStages = properties.getAllStages();

            if (allStages.isEmpty()) {
                return Health.down()
                        .withDetail("message", "未发现任何阶段配置")
                        .build();
            }

            // 检查每个阶段配置
            allStages.forEach((stageName, config) -> {
                details.put(stageName, checkStageConfig(config));
            });

            // 统计信息
            long enabledCount = allStages.values().stream()
                    .filter(StageConfigurable::isEnabled)
                    .count();

            details.put("summary", Map.of(
                    "totalStages", allStages.size(),
                    "enabledStages", enabledCount,
                    "disabledStages", allStages.size() - enabledCount
            ));

            // 判断健康状态
            boolean hasErrors = details.values().stream()
                    .filter(v -> v instanceof Map)
                    .anyMatch(v -> "ERROR".equals(((Map<?, ?>) v).get("status")));

            boolean hasWarnings = details.values().stream()
                    .filter(v -> v instanceof Map)
                    .anyMatch(v -> "WARNING".equals(((Map<?, ?>) v).get("status")));

            if (hasErrors) {
                return Health.down()
                        .withDetail("message", "部分配置存在错误")
                        .withDetails(details)
                        .build();
            }

            if (hasWarnings) {
                return Health.status("WARNING")
                        .withDetail("message", "部分配置存在警告，但应用可正常运行")
                        .withDetails(details)
                        .build();
            }

            return Health.up()
                    .withDetail("message", "所有配置正常")
                    .withDetails(details)
                    .build();

        } catch (Exception e) {
            log.error("健康检查异常", e);
            return Health.down()
                    .withException(e)
                    .withDetail("message", "健康检查异常，但应用仍可运行")
                    .build();
        }
    }

    /**
     * 检查单个阶段配置
     */
    private Map<String, Object> checkStageConfig(StageConfigurable config) {
        Map<String, Object> result = new HashMap<>();

        if (config == null) {
            result.put("status", "WARNING");
            result.put("message", "配置缺失，已使用默认配置");
            return result;
        }

        try {
            result.put("status", "OK");
            result.put("enabled", config.isEnabled());
            result.put("displayName", config.getStageName());

            if (!config.isEnabled()) {
                result.put("message", "已禁用");
                return result;
            }

            // 执行配置验证
            ValidationResult validation = config.validate();

            if (!validation.getWarnings().isEmpty()) {
                result.put("status", "WARNING");
                result.put("warnings", validation.getWarnings());
            }

            if (!validation.isValid()) {
                result.put("status", "ERROR");
                result.put("errors", validation.getErrors());
            }

        } catch (Exception e) {
            log.error("检查配置失败: {}", config.getClass().getSimpleName(), e);
            result.put("status", "ERROR");
            result.put("message", "配置检查失败: " + e.getMessage());
        }

        return result;
    }
}
```

**验证点**：
- [ ] 编译通过
- [ ] 自动发现所有配置
- [ ] 健康状态判断正确
- [ ] 集成测试通过

---

#### 步骤 4.2: 重构配置报告

创建文件：`src/main/java/xyz/firestige/deploy/config/ExecutorStagesConfigurationReporter.java`

```java
package xyz.firestige.deploy.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import xyz.firestige.deploy.infrastructure.execution.stage.config.stage.StageConfigurable;
import xyz.firestige.deploy.infrastructure.execution.stage.config.stage.ValidationResult;

import java.util.Map;

/**
 * Executor Stages 配置报告
 *
 * <p>完全解耦的实现：
 * <ul>
 *   <li>自动发现所有阶段配置</li>
 *   <li>无需硬编码配置类列表</li>
 *   <li>新增配置类自动包含在报告中</li>
 * </ul>
 *
 * @since T-017
 */
@Component
public class ExecutorStagesConfigurationReporter
        implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(
            ExecutorStagesConfigurationReporter.class);

    private final ExecutorStagesProperties properties;

    public ExecutorStagesConfigurationReporter(ExecutorStagesProperties properties) {
        this.properties = properties;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        printConfigurationReport();
    }

    /**
     * 打印配置报告
     */
    private void printConfigurationReport() {
        log.info("╔════════════════════════════════════════╗");
        log.info("║  Executor Stages 配置报告              ║");
        log.info("╚════════════════════════════════════════╝");

        // 自动遍历所有阶段配置
        Map<String, StageConfigurable> allStages = properties.getAllStages();

        if (allStages.isEmpty()) {
            log.warn("⚠ 未发现任何阶段配置");
            return;
        }

        allStages.forEach(this::reportStageConfig);

        // 统计信息
        long enabledCount = allStages.values().stream()
                .filter(StageConfigurable::isEnabled)
                .count();

        log.info("────────────────────────────────────────");
        log.info("总计: {} 个阶段, {} 个已启用, {} 个已禁用",
                allStages.size(),
                enabledCount,
                allStages.size() - enabledCount);
        log.info("════════════════════════════════════════");
    }

    /**
     * 报告单个阶段配置
     */
    private void reportStageConfig(String stageName, StageConfigurable config) {
        if (config == null) {
            log.warn("⚠ {}: 配置缺失（已使用默认配置）", stageName);
            return;
        }

        try {
            String status = config.isEnabled() ? "✓ 已启用" : "✗ 已禁用";
            String displayName = config.getStageName();
            String className = config.getClass().getSimpleName();

            log.info("  {} ({})", displayName, stageName);
            log.info("    状态: {}", status);
            log.info("    类型: {}", className);

            // 如果有验证警告，也打印出来
            if (config.isEnabled()) {
                ValidationResult validation = config.validate();

                if (!validation.getWarnings().isEmpty()) {
                    log.info("    警告:");
                    validation.getWarnings().forEach(warning ->
                            log.warn("      - {}", warning));
                }

                if (!validation.isValid()) {
                    log.info("    错误:");
                    validation.getErrors().forEach(error ->
                            log.error("      - {}", error));
                }
            }

        } catch (Exception e) {
            log.error("⚠ {}: 配置读取失败: {}", stageName, e.getMessage());
        }
    }
}
```

**验证点**：
- [ ] 编译通过
- [ ] 启动时打印报告
- [ ] 报告格式清晰
- [ ] 新增配置自动包含

---

### 5.3 Phase 4 验收标准

- [ ] 健康检查完全解耦
- [ ] 配置报告完全解耦
- [ ] 启动时报告正确
- [ ] Actuator 健康检查端点可用

---

## 6. Phase 5: 测试和验证

### 6.1 单元测试

创建测试类：

```java
package xyz.firestige.deploy.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import xyz.firestige.deploy.infrastructure.execution.stage.config.stage.StageConfigurable;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ExecutorStagesProperties 测试
 */
@SpringBootTest
@TestPropertySource(properties = {
        "executor.stages.blue-green-gateway.enabled=true",
        "executor.stages.portal.enabled=false",
        "executor.stages.asbc-gateway.enabled=true"
})
class ExecutorStagesPropertiesTest {

    @Autowired
    private ExecutorStagesProperties properties;

    @Test
    void shouldAutoDiscoverAllStages() {
        Map<String, StageConfigurable> allStages = properties.getAllStages();

        assertThat(allStages).isNotEmpty();
        assertThat(allStages).containsKeys(
                "blue-green-gateway",
                "portal",
                "asbc-gateway"
        );
    }

    @Test
    void shouldFilterEnabledStages() {
        Map<String, StageConfigurable> enabledStages = properties.getEnabledStages();

        assertThat(enabledStages).hasSize(2);
        assertThat(enabledStages).containsKeys(
                "blue-green-gateway",
                "asbc-gateway"
        );
        assertThat(enabledStages).doesNotContainKey("portal");
    }

    @Test
    void shouldLoadConfiguration() {
        assertThat(properties.getBlueGreenGateway()).isNotNull();
        assertThat(properties.getBlueGreenGateway().isEnabled()).isTrue();

        assertThat(properties.getPortal()).isNotNull();
        assertThat(properties.getPortal().isEnabled()).isFalse();
    }

    @Test
    void shouldValidateConfigurations() {
        // 验证逻辑在 afterPropertiesSet() 中执行
        // 此测试确保应用可以启动且配置已验证
        assertThat(properties.getAllStages()).allSatisfy((name, config) -> {
            assertThat(config).isNotNull();
        });
    }
}
```

### 6.2 集成测试

```java
package xyz.firestige.deploy.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 健康检查集成测试
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExecutorStagesHealthIntegrationTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void shouldProvideHealthEndpoint() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/actuator/health/executorStages",
            String.class
        );
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("totalStages");
        assertThat(response.getBody()).contains("enabledStages");
    }
}
```

### 6.3 扩展性测试

创建测试配置类验证扩展性：

```java
package xyz.firestige.deploy.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.firestige.deploy.infrastructure.execution.stage.config.stage.StageConfigurable;
import xyz.firestige.deploy.infrastructure.execution.stage.config.stage.ValidationResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 扩展性测试：验证新增配置类的便捷性
 */
@SpringBootTest
class ExecutorStagesExtensibilityTest {

    /**
     * 模拟新增配置类
     */
    public static class NewServiceStageConfig implements StageConfigurable {
        private Boolean enabled = false;

        @Override
        public boolean isEnabled() {
            return enabled != null && enabled;
        }

        @Override
        public String getStageName() {
            return "新服务";
        }

        public static NewServiceStageConfig defaultConfig() {
            return new NewServiceStageConfig();
        }
    }

    @Test
    void shouldSupportNewConfigurationClass() {
        // 验证新配置类符合接口约定
        NewServiceStageConfig config = new NewServiceStageConfig();

        assertThat(config.isEnabled()).isFalse();
        assertThat(config.getStageName()).isEqualTo("新服务");
        assertThat(config.validate()).isNotNull();
        assertThat(config.validate().isValid()).isTrue();
    }

    @Test
    void shouldCreateDefaultConfig() {
        NewServiceStageConfig config = NewServiceStageConfig.defaultConfig();

        assertThat(config).isNotNull();
        assertThat(config.isEnabled()).isFalse();
    }
}
```

### 6.4 验收标准

- [ ] 所有单元测试通过（覆盖率 > 80%）
- [ ] 集成测试通过
- [ ] 健康检查端点可访问
- [ ] 启动日志显示配置报告
- [ ] 扩展性测试通过

---

## 7. 回滚计划

### 7.1 回滚触发条件

- 应用启动失败
- 关键功能异常
- 性能显著下降
- 测试失败率 > 20%

### 7.2 回滚步骤

```bash
# 1. 回滚代码
git revert <commit-hash>

# 或切换回旧分支
git checkout main
git branch -D feature/t-017-config-decoupling

# 2. 恢复备份文件
cp src/main/java/xyz/firestige/deploy/config/ExecutorStagesProperties.java.bak \
   src/main/java/xyz/firestige/deploy/config/ExecutorStagesProperties.java

# 3. 重新编译和测试
mvn clean compile test

# 4. 重启应用
```

---

## 8. 验收清单

### 8.1 功能验收

- [ ] 应用正常启动
- [ ] 所有配置类自动发现
- [ ] 配置验证正常工作
- [ ] 健康检查正常工作
- [ ] 配置报告正常显示
- [ ] 新增配置类只需 2 步

### 8.2 质量验收

- [ ] 单元测试覆盖率 > 80%
- [ ] 集成测试通过
- [ ] 无编译警告
- [ ] 无 SonarQube 严重问题
- [ ] 代码审查通过

### 8.3 文档验收

- [ ] JavaDoc 完整
- [ ] README 更新
- [ ] 架构文档更新
- [ ] 变更日志更新

---

## 9. 时间表

| 日期 | 阶段 | 里程碑 |
|------|------|--------|
| Day 1 上午 | Phase 1 | 核心接口和工具类完成 |
| Day 1 下午 | Phase 2 | 配置容器重构完成 |
| Day 2 上午 | Phase 3 | 现有配置类迁移完成 |
| Day 2 下午 | Phase 4 | 健康检查和报告重构完成 |
| Day 3-4 | Phase 5 | 测试和修复 |
| Day 5 | 验收 | 代码审查和文档更新 |

---

## 10. 附录

### 10.1 Git 提交消息模板

```
feat(config): T-017 Phase 1 - 添加核心接口

- 添加 StageConfigurable 接口
- 添加 ValidationResult 类
- 添加 StageConfigUtils 工具类

Related: T-017
```

### 10.2 相关文档

- [T-017 配置合并设计方案](./task-017-config-migration-plan.md)
- [T-017 耦合度评估与改进](./task-017-coupling-analysis-and-improvement.md)

---

**实施方案结束**

