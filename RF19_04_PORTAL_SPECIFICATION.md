# RF-19-04: Portal Stage 实施规格

**创建日期**: 2025-11-21  
**状态**: 待实施

---

## 📋 Portal 接口规格

### Endpoint
```
POST /icc-agent-portal/inner/v1/notify/bgSwitch
```

### 请求格式
```json
{
  "tenantId": "tenant-001",           // 使用租户 ID
  "targetDeployUnit": "deploy-unit-A", // 使用 TenantConfig.deployUnitName
  "timestamp": "1732181234567"        // 当前时间戳（毫秒）
}
```

### 响应格式
```json
{
  "code": "0",      // "0" 表示成功，其他表示失败
  "msg": "信息"     // 详细消息
}
```

### 成功判断
- `code == "0"` → 成功
- `code != "0"` → 失败

---

## 🎯 实施计划

### 1. PortalResponse 模型类
```java
@Data
public class PortalResponse {
    private String code;  // "0" 表示成功
    private String msg;
}
```

### 2. PortalDataPreparer
```java
DataPreparer portalPreparer = (ctx) -> {
    TenantConfig config = ...;  // 获取配置
    
    // 获取 endpoint
    String baseUrl = resolveEndpoint("portalService", "portal");
    String endpoint = baseUrl + "/icc-agent-portal/inner/v1/notify/bgSwitch";
    
    // 构建请求 body
    Map<String, Object> body = new HashMap<>();
    body.put("tenantId", config.getTenantId());
    body.put("targetDeployUnit", config.getDeployUnitName());
    body.put("timestamp", String.valueOf(System.currentTimeMillis()));
    
    // 构建 headers
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/json");
    
    // 放入 TaskRuntimeContext
    ctx.addVariable("url", endpoint);
    ctx.addVariable("method", "POST");
    ctx.addVariable("headers", headers);
    ctx.addVariable("body", body);
};
```

### 3. PortalResultValidator
```java
ResultValidator portalValidator = (ctx) -> {
    HttpResponseData response = ctx.getAdditionalData("httpResponse", HttpResponseData.class);
    
    // 1. 检查 HTTP 状态码
    if (!response.is2xx()) {
        return ValidationResult.failure(
            String.format("Portal HTTP 错误: %d", response.getStatusCode())
        );
    }
    
    // 2. 解析响应 JSON
    try {
        PortalResponse portalResponse = response.parseBody(PortalResponse.class);
        
        // 3. 检查业务 code
        if ("0".equals(portalResponse.getCode())) {
            return ValidationResult.success(
                String.format("Portal 通知成功: %s", portalResponse.getMsg())
            );
        } else {
            return ValidationResult.failure(
                String.format("Portal 通知失败: code=%s, msg=%s", 
                    portalResponse.getCode(), portalResponse.getMsg())
            );
        }
        
    } catch (Exception e) {
        return ValidationResult.failure("Portal 响应解析失败: " + e.getMessage());
    }
};
```

### 4. DynamicStageFactory 中的创建方法
```java
private TaskStage createPortalStage(TenantConfig config) {
    StepConfig stepConfig = StepConfig.builder()
        .stepName("portal-notify")
        .dataPreparer(createPortalDataPreparer(config))
        .step(new HttpRequestStep(restTemplate))
        .resultValidator(createPortalResultValidator())
        .build();
    
    return new ConfigurableServiceStage("portal", Arrays.asList(stepConfig));
}
```

---

## ✅ 验证 Portal 设计的可扩展性

### 优势展示

1. **完全复用 HttpRequestStep** ✅
   - 无需创建 PortalNotificationStep
   - HttpRequestStep 完全通用

2. **业务逻辑集中** ✅
   - 数据准备在 PortalDataPreparer
   - 结果验证在 PortalResultValidator
   - Step 只做 HTTP 请求

3. **易于扩展** ✅
   - 新增 Portal：实现 2 个方法（Preparer + Validator）
   - 修改 endpoint：只需修改 Preparer
   - 修改验证逻辑：只需修改 Validator

4. **可维护性** ✅
   - 代码清晰，职责分离
   - 易于测试
   - 易于调试

---

## 📊 Portal vs ASBC 对比

| 维度 | Portal | ASBC |
|------|--------|------|
| **Step 类型** | HttpRequestStep | HttpRequestStep |
| **是否复用** | ✅ 完全复用 | ✅ 完全复用 |
| **Preparer** | 简单（3 个字段）| 复杂（拆分 + token）|
| **Validator** | 简单（只看 code）| 复杂（failList 判断）|
| **代码量** | ~50 行 | ~150 行 |

**结论**: 不同复杂度的服务，都能很好地适配这套架构！✅

---

## 🎯 实施清单

- [ ] 创建 PortalResponse 模型类
- [ ] 创建 PortalDataPreparer
- [ ] 创建 PortalResultValidator
- [ ] 在 DynamicStageFactory 中添加 createPortalStage()
- [ ] 编译验证
- [ ] 单元测试

**预计时间**: 1 小时

---

**此规格确认了三层抽象架构的可扩展性和可维护性！** ✅

