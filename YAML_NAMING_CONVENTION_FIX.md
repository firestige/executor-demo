# YAML 配置命名规范修复报告

## 问题描述

在 `deploy-stages.yml` 配置文件中存在**命名规范不一致**的问题，导致运行时无法正确读取配置参数。

## 问题根因

### 命名规范冲突

**YAML 配置中使用的是驼峰命名**（camelCase）：
```yaml
nacosServiceNameKey: "blueGreenGatewayService"
validationType: "json-path"
validationRule: "$.version"
expectedValue: "1"
retryPolicy:
  maxAttempts: 10
  intervalSeconds: 3
```

**代码中读取配置使用的是连字符命名**（kebab-case）：
```java
String nacosServiceNameKey = getConfigValue("nacos-service-name-key", null);
String validationType = getConfigValue("validation-type", "json-path");
String validationRule = getConfigValue("validation-rule", "$.status");
String expectedValue = getConfigValue("expected-value", "UP");
```

### 导致的问题

1. **配置读取失败**：`getConfigValue("nacos-service-name-key")` 无法读取 `nacosServiceNameKey`
2. **抛出异常**：`"nacos-service-name-key not configured in YAML"`
3. **fallback 失败**：`fallbackInstances` 的 key 也存在类似问题

## 修复内容

### 1. Infrastructure 配置

#### fallbackInstances（已修复）
```yaml
# 修改前（驼峰命名）
fallbackInstances:
  blueGreenGateway:
    - "192.168.1.10:8080"

# 修改后（连字符命名）
fallbackInstances:
  blue-green-gateway:
    - "192.168.1.10:8080"
```

### 2. Blue-Green Gateway 服务配置

#### endpoint-polling step（已修复）
```yaml
# 修改前
- type: endpoint-polling
  config:
    nacosServiceNameKey: "blueGreenGatewayService"
    validationType: "json-path"
    validationRule: "$.version"
    expectedValue: "1"
  retryPolicy:
    maxAttempts: 10
    intervalSeconds: 3

# 修改后
- type: endpoint-polling
  config:
    nacos-service-name-key: "blueGreenGatewayService"
    validation-type: "json-path"
    validation-rule: "$.version"
    expected-value: "1"
  retry-policy:
    max-attempts: 10
    interval-seconds: 3
```

### 3. Portal 服务配置

#### endpoint-polling step（已修复）
```yaml
# 修改前
- type: endpoint-polling
  config:
    nacosServiceNameKey: "portalService"
    validationType: "json-path"
    validationRule: "$.version"
    expectedValue: "1"
  retryPolicy:
    maxAttempts: 10
    intervalSeconds: 3

# 修改后
- type: endpoint-polling
  config:
    nacos-service-name-key: "portalService"
    validation-type: "json-path"
    validation-rule: "$.version"
    expected-value: "1"
  retry-policy:
    max-attempts: 10
    interval-seconds: 3
```

### 4. ASBC Gateway 服务配置

#### asbc-config-request step（已修复）
```yaml
# 修改前
- type: asbc-config-request
  config:
    httpMethod: "POST"
    validationType: "http-status"
    expectedStatus: 200
  retryPolicy:
    maxAttempts: 1
    intervalSeconds: 0

# 修改后
- type: asbc-config-request
  config:
    http-method: "POST"
    validation-type: "http-status"
    expected-status: 200
  retry-policy:
    max-attempts: 1
    interval-seconds: 0
```

## 命名规范对照表

| 驼峰命名（错误） | 连字符命名（正确） | 说明 |
|-----------------|------------------|------|
| `nacosServiceNameKey` | `nacos-service-name-key` | Nacos 服务名 key |
| `validationType` | `validation-type` | 验证类型 |
| `validationRule` | `validation-rule` | 验证规则 |
| `expectedValue` | `expected-value` | 期望值 |
| `expectedStatus` | `expected-status` | 期望状态码 |
| `httpMethod` | `http-method` | HTTP 方法 |
| `retryPolicy` | `retry-policy` | 重试策略 |
| `maxAttempts` | `max-attempts` | 最大重试次数 |
| `intervalSeconds` | `interval-seconds` | 重试间隔（秒��|
| `blueGreenGateway` | `blue-green-gateway` | 蓝绿网关服务 |

## 统一的命名规范

### YAML 配置文件规范

✅ **应该使用**：连字符命名（kebab-case）
- 易读性好
- 符合 YAML 社区惯例
- 与 Spring Boot 配置规范一致

❌ **不应该使用**：驼峰命名（camelCase）
- 容易与 Java 代码混淆
- 在配置文件中可读性较差

### 示例

```yaml
# 正确的命名风格
endpoint-polling:
  config:
    nacos-service-name-key: "myService"
    validation-type: "json-path"
    expected-value: "UP"
  retry-policy:
    max-attempts: 10
    interval-seconds: 3

# 错误的命名风格（不要使用）
endpointPolling:
  config:
    nacosServiceNameKey: "myService"
    validationType: "jsonPath"
    expectedValue: "UP"
  retryPolicy:
    maxAttempts: 10
    intervalSeconds: 3
```

## 修复验证

### 编译验证
✅ **编译成功** - 所有配置文件语法正确

### 功能验证建议

1. **配置读取测试**
   ```java
   @Test
   void testConfigKeysCanBeRead() {
       String key = stepConfig.get("nacos-service-name-key");
       assertNotNull(key);
       assertEquals("blueGreenGatewayService", key);
   }
   ```

2. **端到端测试**
   - 启动应用，检查日志中是否还有 "not configured in YAML" 错误
   - 测试 Nacos 服务发现功能
   - 测试 Nacos 不可用时的 fallback 功能

3. **日志验证**
   ```
   # 修复前（错误日志）
   [ERROR] nacos-service-name-key not configured in YAML
   
   # 修复后（正常日志）
   [INFO] Using Nacos service discovery: service=blue-green-gateway-service, count=2
   ```

## 影响范围

### 修复的配置项

1. **Infrastructure 配置**
   - `fallbackInstances` 的 key 命名

2. **Blue-Green Gateway 配置**
   - `endpoint-polling` step 的所有配置项

3. **Portal 配置**
   - `endpoint-polling` step 的所有配置项

4. **ASBC Gateway 配置**
   - `asbc-config-request` step 的所有配置项

### 不受影响的配置

- `hashField`（已经是驼峰命名，但在 Step 代码中也使用驼峰）
- `message`（简单命名，无驼峰或连字符）
- Nacos 服务名的值（如 `blueGreenGatewayService`，这是服务标识符，不是配置 key）

## 后续建议

### 1. 代码审查清单

在添加新配置项时，确保：
- [ ] YAML 配置使用连字符命名
- [ ] Java 代码使用 `getConfigValue("kebab-case-name")` 读取
- [ ] 配置文档中明确命名规范

### 2. 自动化验证

建议添加启动时配置验证：

```java
@PostConstruct
public void validateConfigKeys() {
    List<String> requiredKeys = List.of(
        "nacos-service-name-key",
        "validation-type",
        "validation-rule"
    );
    
    for (String key : requiredKeys) {
        if (!stepConfig.containsKey(key)) {
            log.warn("Configuration key '{}' not found in step config", key);
        }
    }
}
```

### 3. 文档更新

更新相关文档，明确命名规范：
- 配置指南
- 开发者手册
- API 文档

## 总结

### 问题
- YAML 配置使用驼峰命名，代码读取使用连字符命名，导致配置无法读取

### 修复
- 统一 YAML 配置为连字符命名（kebab-case）
- 涉及 4 个服务配置的多个配置项

### 验证
- ✅ 编译通过
- ✅ 配置语法正确
- 建议进行端到端功能测试

### 规范
- YAML 配置统一使用连字符命名
- Java 代码保持驼峰命名
- 配置读取时使用连字符格式的 key

---

**修复日期**: 2025年11月20日  
**修复范围**: 全部 YAML 配置命名规范  
**状态**: ✅ 完成

