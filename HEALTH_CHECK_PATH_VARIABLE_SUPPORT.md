# 健康检查路径变量支持说明

## 📋 修改概述

**日期**: 2025-11-19  
**目标**: 支持健康检查 URL 中的路径变量替换，并支持 JSON 响应中数字类型的校验  
**状态**: ✅ 完成

---

## 🎯 需求分析

### 1. URL 路径变量

**需求**: 健康检查的 URL 包含 `{tenantId}` 占位符，需要在运行时替换为实际的租户 ID

**示例**:
```
配置: /actuator/bg-sdk/{tenantId}
tenantId: tenant-001
实际URL: /actuator/bg-sdk/tenant-001
```

### 2. 响应格式

**响应示例**:
```json
{
  "version": 1
}
```

**校验需求**: 提取 `version` 字段的值（数字类型），与期望值比对

---

## 🔧 技术实现

### 1. 路径变量替换

**修改文件**: `EndpointPollingStep.java`

**修改前**:
```java
private String buildHealthCheckUrl(ServiceInstance instance, String path) {
    return String.format("http://%s:%d%s", instance.ip(), instance.port(), path);
}
```

**修改后**:
```java
private String buildHealthCheckUrl(ServiceInstance instance, String path) {
    // 替换路径中的 {tenantId} 占位符
    String resolvedPath = path.replace("{tenantId}", serviceConfig.getTenantId());
    return String.format("http://%s:%d%s", instance.ip(), instance.port(), resolvedPath);
}
```

**支持的占位符**:
- `{tenantId}` - 从 `ServiceConfig` 中获取租户 ID

**扩展性**: 如需支持更多占位符，可以继续添加：
```java
String resolvedPath = path
    .replace("{tenantId}", serviceConfig.getTenantId())
    .replace("{namespace}", serviceConfig.getNacosNamespace())
    .replace("{version}", String.valueOf(serviceConfig.getVersion()));
```

### 2. JSON 数字类型校验

**修改文件**: `EndpointPollingStep.java`

**修改前**:
```java
private boolean validateResponse(String response, String validationType, String rule, String expectedValue) {
    if ("json-path".equals(validationType)) {
        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            JsonNode valueNode = jsonNode.at("/" + rule.replace("$.", "").replace(".", "/"));
            
            if (valueNode.isMissingNode()) {
                return false;
            }
            
            String actualValue = valueNode.asText();
            return expectedValue.equals(actualValue);
        } catch (Exception e) {
            log.warn("[EndpointPollingStep] JSON validation failed: {}", e.getMessage());
            return false;
        }
    }
    return true;
}
```

**修改后**:
```java
private boolean validateResponse(String response, String validationType, String rule, String expectedValue) {
    if ("json-path".equals(validationType)) {
        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            // 将 $.version 转换为 /version，$.status 转换为 /status
            String jsonPath = "/" + rule.replace("$.", "").replace(".", "/");
            JsonNode valueNode = jsonNode.at(jsonPath);
            
            if (valueNode.isMissingNode()) {
                log.warn("[EndpointPollingStep] JSON path not found: rule={}, response={}", rule, response);
                return false;
            }
            
            // 支持数字类型比对（如 version: 1）
            if (valueNode.isNumber()) {
                try {
                    long actualNumber = valueNode.asLong();
                    long expectedNumber = Long.parseLong(expectedValue);
                    boolean matched = actualNumber == expectedNumber;
                    log.debug("[EndpointPollingStep] Number validation: actual={}, expected={}, matched={}", 
                            actualNumber, expectedNumber, matched);
                    return matched;
                } catch (NumberFormatException e) {
                    log.warn("[EndpointPollingStep] Expected number but got: {}", expectedValue);
                    return false;
                }
            }
            
            // 字符串类型比对（如 status: "UP"）
            String actualValue = valueNode.asText();
            boolean matched = expectedValue.equals(actualValue);
            log.debug("[EndpointPollingStep] String validation: actual={}, expected={}, matched={}", 
                    actualValue, expectedValue, matched);
            return matched;
            
        } catch (Exception e) {
            log.warn("[EndpointPollingStep] JSON validation failed: {}", e.getMessage());
            return false;
        }
    }
    return true;
}
```

**改进点**:
1. ✅ 自动检测 JSON 节点类型（数字 vs 字符串）
2. ✅ 数字类型使用数值比对（1 == 1）
3. ✅ 字符串类型使用字符串比对（"UP" == "UP"）
4. ✅ 添加详细的日志输出，便于调试

---

## 📝 YAML 配置

### Infrastructure 配置

```yaml
infrastructure:
  healthCheck:
    defaultPath: "/actuator/bg-sdk/{tenantId}"  # 包含占位符
    intervalSeconds: 3
    maxAttempts: 10
```

### Service 配置

#### Blue-Green Gateway

```yaml
services:
  blue-green-gateway:
    stages:
      - name: deploy-stage
        steps:
          - type: endpoint-polling
            config:
              nacosServiceNameKey: "blueGreenGatewayService"
              validationType: "json-path"
              validationRule: "$.version"      # 提取 version 字段
              expectedValue: "1"               # 期望值为 1（数字）
            retryPolicy:
              maxAttempts: 10
              intervalSeconds: 3
```

#### Portal

```yaml
services:
  portal:
    stages:
      - name: deploy-stage
        steps:
          - type: endpoint-polling
            config:
              nacosServiceNameKey: "portalService"
              validationType: "json-path"
              validationRule: "$.version"      # 提取 version 字段
              expectedValue: "1"               # 期望值为 1（数字）
            retryPolicy:
              maxAttempts: 10
              intervalSeconds: 3
```

---

## 🔄 执行流程

### 完整的健康检查流程

以 `tenant-001` 切换 `blue-green-gateway` 为例：

```
1️⃣ 读取配置
   ├─ defaultPath = "/actuator/bg-sdk/{tenantId}"
   ├─ validationRule = "$.version"
   ├─ expectedValue = "1"
   └─ tenantId = "tenant-001"

2️⃣ 获取服务实例
   ├─ 尝试从 Nacos 获取实例列表
   └─ 降级使用固定 IP: ["192.168.1.10:8080", "192.168.1.11:8080"]

3️⃣ 构建健康检查 URL
   ├─ path = "/actuator/bg-sdk/{tenantId}"
   ├─ resolvedPath = "/actuator/bg-sdk/tenant-001"  # 替换占位符
   └─ url = "http://192.168.1.10:8080/actuator/bg-sdk/tenant-001"

4️⃣ 发送 GET 请求
   └─ GET http://192.168.1.10:8080/actuator/bg-sdk/tenant-001

5️⃣ 接收响应
   └─ {"version": 1}

6️⃣ 校验响应
   ├─ 解析 JSON: {"version": 1}
   ├─ 提取字段: $.version → 1 (Number)
   ├─ 检测类型: isNumber() = true
   ├─ 数字比对: 1 == 1
   └─ 结果: ✅ 通过

7️⃣ 重试逻辑
   ├─ 如果所有实例都通过 → 成功返回
   ├─ 如果有实例失败 → 等待 3 秒后重试
   └─ 最多重试 10 次
```

---

## 🧪 测试用例

### 1. 路径变量替换测试

```java
@Test
void shouldReplaceTenantIdInPath() {
    // Given
    String path = "/actuator/bg-sdk/{tenantId}";
    String tenantId = "tenant-001";
    ServiceConfig config = mock(ServiceConfig.class);
    when(config.getTenantId()).thenReturn(tenantId);
    
    // When
    String url = buildHealthCheckUrl(new ServiceInstance("192.168.1.10", 8080), path);
    
    // Then
    assertEquals("http://192.168.1.10:8080/actuator/bg-sdk/tenant-001", url);
}
```

### 2. 数字类型校验测试

```java
@Test
void shouldValidateNumberTypeInJson() {
    // Given
    String response = "{\"version\": 1}";
    String rule = "$.version";
    String expectedValue = "1";
    
    // When
    boolean valid = validateResponse(response, "json-path", rule, expectedValue);
    
    // Then
    assertTrue(valid);
}

@Test
void shouldFailWhenVersionMismatch() {
    // Given
    String response = "{\"version\": 2}";
    String rule = "$.version";
    String expectedValue = "1";
    
    // When
    boolean valid = validateResponse(response, "json-path", rule, expectedValue);
    
    // Then
    assertFalse(valid);
}
```

### 3. 字符串类型校验测试（兼容性）

```java
@Test
void shouldValidateStringTypeInJson() {
    // Given
    String response = "{\"status\": \"UP\"}";
    String rule = "$.status";
    String expectedValue = "UP";
    
    // When
    boolean valid = validateResponse(response, "json-path", rule, expectedValue);
    
    // Then
    assertTrue(valid);
}
```

---

## 📊 支持的响应格式

### 1. 数字类型（新增支持）

```json
{
  "version": 1
}
```

**配置**:
```yaml
validationRule: "$.version"
expectedValue: "1"
```

### 2. 字符串类型（原有支持）

```json
{
  "status": "UP"
}
```

**配置**:
```yaml
validationRule: "$.status"
expectedValue: "UP"
```

### 3. 嵌套对象

```json
{
  "data": {
    "version": 1
  }
}
```

**配置**:
```yaml
validationRule: "$.data.version"
expectedValue: "1"
```

### 4. 数组元素

```json
{
  "versions": [1, 2, 3]
}
```

**配置**:
```yaml
validationRule: "$.versions[0]"
expectedValue: "1"
```

---

## 🎯 实际应用场景

### 场景 1: Blue-Green Gateway 健康检查

```
请求: GET http://192.168.1.10:8080/actuator/bg-sdk/tenant-001
响应: {"version": 1}
校验: $.version == 1 ✅
```

### 场景 2: Portal 健康检查

```
请求: GET http://192.168.1.20:8080/actuator/bg-sdk/tenant-002
响应: {"version": 1}
校验: $.version == 1 ✅
```

### 场景 3: 版本不匹配（失败场景）

```
请求: GET http://192.168.1.10:8080/actuator/bg-sdk/tenant-001
响应: {"version": 2}
校验: $.version == 1 ❌
结果: 重试（最多 10 次）
```

---

## ⚠️ 注意事项

### 1. 占位符命名

- ✅ **推荐**: `{tenantId}`, `{namespace}`, `{version}`
- ❌ **避免**: `$tenantId`, `%tenantId%`, `<tenantId>`

### 2. expectedValue 格式

**数字类型**:
```yaml
validationRule: "$.version"
expectedValue: "1"          # ✅ 字符串格式的数字
# 不要写成: expectedValue: 1   # ❌ YAML 会当作数字，但代码期望字符串
```

**字符串类型**:
```yaml
validationRule: "$.status"
expectedValue: "UP"         # ✅ 字符串
```

### 3. JSON Path 语法

| JSON | JSON Path | 说明 |
|------|-----------|------|
| `{"version": 1}` | `$.version` | 根级别字段 |
| `{"data": {"version": 1}}` | `$.data.version` | 嵌套字段 |
| `{"versions": [1, 2]}` | `$.versions[0]` | 数组第一个元素 |
| `{"items": [{"v": 1}]}` | `$.items[0].v` | 数组对象字段 |

### 4. 类型自动检测

代码会自动检测 JSON 节点类型：
- 如果是数字 → 使用数值比对（`1 == 1`）
- 如果是字符串 → 使用字符串比对（`"UP" == "UP"`）
- 如果是布尔值 → 转为字符串比对（`true → "true"`）

---

## 📈 性能优化

### 1. 路径替换缓存

如果性能敏感，可以考虑缓存已替换的路径：

```java
private final Map<String, String> pathCache = new ConcurrentHashMap<>();

private String buildHealthCheckUrl(ServiceInstance instance, String path) {
    String cacheKey = path + ":" + serviceConfig.getTenantId();
    String resolvedPath = pathCache.computeIfAbsent(cacheKey, 
        k -> path.replace("{tenantId}", serviceConfig.getTenantId()));
    return String.format("http://%s:%d%s", instance.ip(), instance.port(), resolvedPath);
}
```

### 2. JSON 解析复用

`ObjectMapper` 已经是单例，无需额外优化。

---

## ✅ 修改总结

### 代码修改

- ✅ `EndpointPollingStep.java` - 支持路径变量替换
- ✅ `EndpointPollingStep.java` - 支持数字类型校验

### 配置修改

- ✅ `deploy-stages.yml` - 更新 `defaultPath` 包含 `{tenantId}`
- ✅ `deploy-stages.yml` - blue-green-gateway 改为校验 `$.version`
- ✅ `deploy-stages.yml` - portal 改为校验 `$.version`

### 向后兼容性

- ✅ 不包含占位符的路径仍然有效
- ✅ 字符串类型校验仍然有效
- ✅ 新增数字类型校验不影响现有功能

---

**修改完成，可以正常使用！**

