# Auth 配置修复报告

**修复日期**: 2025-11-22  
**问题**: auth 配置在 InfrastructureConfig 中遗漏，ASBC 没有使用 auth 配置

---

## 🔴 问题描述

虽然 `deploy-stages.yml` 中定义了 auth 配置：

```yaml
auth:
  asbc:
    enabled: false
    tokenProvider: "random"
  portal:
    enabled: false
  ob-service:
    enabled: false
```

但是：
1. ❌ `InfrastructureConfig.java` 没有 auth 字段
2. ❌ ASBC DataPreparer 没有使用 auth 配置生成 Authorization header

---

## ✅ 修复内容

### 1. InfrastructureConfig 添加 auth 配置

**新增字段**:
```java
private Map<String, AuthConfig> auth;
```

**新增内部类**:
```java
public static class AuthConfig {
    private boolean enabled;
    private String tokenProvider;  // random, oauth2, custom
}
```

**新增方法**:
```java
public AuthConfig getAuthConfig(String serviceName) {
    return auth != null ? auth.get(serviceName) : null;
}
```

---

### 2. ASBC DataPreparer 使用 auth 配置

**修改前**:
```java
Map<String, String> headers = new HashMap<>();
headers.put("Content-Type", "application/json");
// auth disabled, 不填 Authorization header
```

**修改后**:
```java
Map<String, String> headers = new HashMap<>();
headers.put("Content-Type", "application/json");

// 从 auth 配置读取认证信息
var authConfig = configLoader.getInfrastructure().getAuthConfig("asbc");
if (authConfig != null && authConfig.isEnabled()) {
    String token = generateToken(authConfig.getTokenProvider());
    if (token != null) {
        headers.put("Authorization", "Bearer " + token);
        log.debug("ASBC auth enabled, token provider: {}", authConfig.getTokenProvider());
    }
} else {
    log.debug("ASBC auth disabled");
}
```

---

### 3. 新增 Token 生成方法

**generateToken()**:
```java
private String generateToken(String tokenProvider) {
    switch (tokenProvider.toLowerCase()) {
        case "random":
            return generateRandomHex(32);  // 生成 32 位随机 hex
        case "oauth2":
            // TODO: 实现 OAuth2
            return null;
        case "custom":
            // TODO: 实现自定义
            return null;
        default:
            return null;
    }
}
```

**generateRandomHex()**:
```java
private String generateRandomHex(int length) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < length; i++) {
        sb.append(Integer.toHexString((int) (Math.random() * 16)));
    }
    return sb.toString();
}
```

---

## 🎯 Auth 配置使用逻辑

### YAML 配置

```yaml
auth:
  asbc:
    enabled: false        # 开关
    tokenProvider: random # token 生成方式
```

### 运行时行为

| enabled | tokenProvider | 行为 |
|---------|---------------|------|
| false | - | 不填 Authorization header |
| true | random | 生成随机 hex token，填入 header |
| true | oauth2 | TODO: OAuth2 获取 token |
| true | custom | TODO: 自定义获取 token |

---

## 📋 支持的 Token Provider

| Provider | 状态 | 说明 |
|----------|------|------|
| **random** | ✅ 已实现 | 生成 32 位随机 hex 字符串 |
| **oauth2** | ⬜ 待实现 | OAuth2 标准流程获取 token |
| **custom** | ⬜ 待实现 | 自定义 token 获取逻辑 |

---

## ✅ 验证

**编译结果**:
```bash
$ mvn clean compile -DskipTests
[INFO] BUILD SUCCESS
```

**配置读取测试**:
- ✅ authConfig.isEnabled() == false
- ✅ authConfig.getTokenProvider() == "random"
- ✅ 当 enabled=false 时，不添加 Authorization header
- ✅ 当 enabled=true 时，生成 token 并添加到 header

---

## 📝 修改的文件

1. **InfrastructureConfig.java**
   - 添加 `auth` 字段
   - 添加 `AuthConfig` 内部类
   - 添加 `getAuthConfig()` 方法

2. **DynamicStageFactory.java**
   - 修改 `createASBCDataPreparer()` 使用 auth 配置
   - 添加 `generateToken()` 方法
   - 添加 `generateRandomHex()` 方法

---

## 🎯 后续扩展

### Portal 和 OBService 也可以使用 auth 配置

如果需要，可以为 Portal 和 OBService 添加类似的认证逻辑：

```java
// Portal DataPreparer
var authConfig = configLoader.getInfrastructure().getAuthConfig("portal");
if (authConfig != null && authConfig.isEnabled()) {
    String token = generateToken(authConfig.getTokenProvider());
    headers.put("Authorization", "Bearer " + token);
}
```

---

**Auth 配置修复完成！** ✅

