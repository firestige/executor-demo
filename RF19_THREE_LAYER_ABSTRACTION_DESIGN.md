# RF-19 Step 三层抽象设计方案（最终版）

**创建日期**: 2025-11-21  
**状态**: 最终设计方案

---

## 🎯 核心设计理念

### Step 的职责重新定义

```
Step = 纯技术函数（不做业务判断）
  1. 准备数据 (prepareData)
  2. 执行动作 (executeAction) 
  3. 返回原始结果 (rawResult)

Stage = 业务编排层
  1. 定义 DataPreparer（如何准备数据）
  2. 定义 ResultValidator（如何验证结果）
  3. 调用 Step 并判断结果
```

---

## 📐 三种基础 Step

### 1. HttpRequestStep（HTTP 请求）

```java
/**
 * HTTP 请求 Step（纯技术实现）
 * 不做业务判断，只发送请求并返回原始响应
 */
public class HttpRequestStep implements StageStep {
    private final RestTemplate restTemplate;
    
    @Override
    public StepResult execute(StepContext context) {
        StepResult result = StepResult.start("http-request");
        
        try {
            // 1. 准备数据（从 context 读取）
            HttpRequestData requestData = prepareData(context);
            
            // 2. 执行动作（发送 HTTP 请求）
            HttpResponseData responseData = executeAction(requestData);
            
            // 3. 返回原始结果（不做判断）
            context.putData("httpResponse", responseData);
            result.finishSuccess();
            result.setMessage(String.format("HTTP %s %s → %d", 
                requestData.getMethod(), 
                requestData.getUrl(), 
                responseData.getStatusCode()));
            
        } catch (Exception e) {
            result.finishFailure("HTTP 请求异常: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 准备数据（从 StepContext 提取）
     */
    private HttpRequestData prepareData(StepContext context) {
        return HttpRequestData.builder()
            .url(context.getDataAsString("url"))
            .method(context.getDataAsString("method"))
            .headers(context.getData("headers", Map.class))
            .body(context.getData("body", Object.class))
            .build();
    }
    
    /**
     * 执行动作（发送 HTTP 请求）
     */
    private HttpResponseData executeAction(HttpRequestData requestData) {
        // 纯技术实现：发送 HTTP 请求
        HttpHeaders headers = new HttpHeaders();
        if (requestData.getHeaders() != null) {
            requestData.getHeaders().forEach(headers::set);
        }
        
        HttpEntity<Object> entity = new HttpEntity<>(requestData.getBody(), headers);
        
        ResponseEntity<String> response;
        switch (requestData.getMethod().toUpperCase()) {
            case "GET":
                response = restTemplate.getForEntity(requestData.getUrl(), String.class);
                break;
            case "POST":
                response = restTemplate.postForEntity(requestData.getUrl(), entity, String.class);
                break;
            default:
                throw new IllegalArgumentException("不支持的 HTTP 方法: " + requestData.getMethod());
        }
        
        return HttpResponseData.builder()
            .statusCode(response.getStatusCodeValue())
            .headers(extractHeaders(response.getHeaders()))
            .body(response.getBody())
            .build();
    }
}

// ===== 数据对象 =====

@Data
@Builder
public class HttpRequestData {
    private String url;
    private String method;
    private Map<String, String> headers;
    private Object body;
}

@Data
@Builder
public class HttpResponseData {
    private int statusCode;
    private Map<String, String> headers;
    private String body;
    
    public boolean is2xx() {
        return statusCode >= 200 && statusCode < 300;
    }
    
    public <T> T parseBody(Class<T> clazz) {
        // Jackson 解析
        return new ObjectMapper().readValue(body, clazz);
    }
}
```

---

### 2. ConfigWriteStep（配置写入）

```java
/**
 * 配置写入 Step（Redis HSET）
 * 不做业务判断，只写入配置并返回结果
 */
public class ConfigWriteStep implements StageStep {
    private final RedisTemplate<String, String> redisTemplate;
    
    @Override
    public StepResult execute(StepContext context) {
        StepResult result = StepResult.start("config-write");
        
        try {
            // 1. 准备数据
            ConfigWriteData writeData = prepareData(context);
            
            // 2. 执行动作（Redis HSET）
            ConfigWriteResult writeResult = executeAction(writeData);
            
            // 3. 返回原始结果
            context.putData("writeResult", writeResult);
            result.finishSuccess();
            result.setMessage(String.format("写入配置: key=%s, field=%s", 
                writeData.getKey(), writeData.getField()));
            
        } catch (Exception e) {
            result.finishFailure("配置写入异常: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 准备数据
     */
    private ConfigWriteData prepareData(StepContext context) {
        return ConfigWriteData.builder()
            .key(context.getDataAsString("key"))
            .field(context.getDataAsString("field"))
            .value(context.getDataAsString("value"))
            .build();
    }
    
    /**
     * 执行动作（Redis HSET）
     */
    private ConfigWriteResult executeAction(ConfigWriteData writeData) {
        Boolean success = redisTemplate.opsForHash().putIfAbsent(
            writeData.getKey(), 
            writeData.getField(), 
            writeData.getValue()
        );
        
        return ConfigWriteResult.builder()
            .success(success != null && success)
            .key(writeData.getKey())
            .field(writeData.getField())
            .build();
    }
}

// ===== 数据对象 =====

@Data
@Builder
public class ConfigWriteData {
    private String key;
    private String field;
    private String value;
}

@Data
@Builder
public class ConfigWriteResult {
    private boolean success;
    private String key;
    private String field;
}
```

---

### 3. MessageBroadcastStep（消息广播）

```java
/**
 * 消息广播 Step（Redis Pub/Sub）
 * 不做业务判断，只发送消息并返回结果
 */
public class MessageBroadcastStep implements StageStep {
    private final RedisTemplate<String, String> redisTemplate;
    
    @Override
    public StepResult execute(StepContext context) {
        StepResult result = StepResult.start("message-broadcast");
        
        try {
            // 1. 准备数据
            MessageBroadcastData broadcastData = prepareData(context);
            
            // 2. 执行动作（Redis PUBLISH）
            MessageBroadcastResult broadcastResult = executeAction(broadcastData);
            
            // 3. 返回原始结果
            context.putData("broadcastResult", broadcastResult);
            result.finishSuccess();
            result.setMessage(String.format("广播消息: topic=%s, 接收者=%d", 
                broadcastData.getTopic(), broadcastResult.getReceiverCount()));
            
        } catch (Exception e) {
            result.finishFailure("消息广播异常: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 准备数据
     */
    private MessageBroadcastData prepareData(StepContext context) {
        return MessageBroadcastData.builder()
            .topic(context.getDataAsString("topic"))
            .message(context.getDataAsString("message"))
            .build();
    }
    
    /**
     * 执行动作（Redis PUBLISH）
     */
    private MessageBroadcastResult executeAction(MessageBroadcastData broadcastData) {
        Long receiverCount = redisTemplate.convertAndSend(
            broadcastData.getTopic(), 
            broadcastData.getMessage()
        );
        
        return MessageBroadcastResult.builder()
            .topic(broadcastData.getTopic())
            .receiverCount(receiverCount != null ? receiverCount.intValue() : 0)
            .build();
    }
}

// ===== 数据对象 =====

@Data
@Builder
public class MessageBroadcastData {
    private String topic;
    private String message;
}

@Data
@Builder
public class MessageBroadcastResult {
    private String topic;
    private int receiverCount;
}
```

---

## 🎨 业务层接口定义

### DataPreparer（数据准备器）

```java
/**
 * 数据准备器接口
 * 每个服务实现自己的数据准备逻辑
 */
public interface DataPreparer {
    /**
     * 准备 Step 执行所需的数据
     * 
     * @param runtimeContext Task 运行时上下文
     * @param stepContext Step 上下文（输出）
     */
    void prepare(TaskRuntimeContext runtimeContext, StepContext stepContext);
}
```

### ResultValidator（结果验证器）

```java
/**
 * 结果验证器接口
 * 每个服务实现自己的结果验证逻辑
 */
public interface ResultValidator {
    /**
     * 验证 Step 执行结果
     * 
     * @param stepContext Step 上下文（包含执行结果）
     * @return 验证结果
     */
    ValidationResult validate(StepContext stepContext);
}

/**
 * 验证结果
 */
@Data
public class ValidationResult {
    private boolean success;
    private String message;
    private Object data;  // 可选的业务数据
    
    public static ValidationResult success(String message) {
        ValidationResult result = new ValidationResult();
        result.setSuccess(true);
        result.setMessage(message);
        return result;
    }
    
    public static ValidationResult failure(String message) {
        ValidationResult result = new ValidationResult();
        result.setSuccess(false);
        result.setMessage(message);
        return result;
    }
}
```

---

## 🔄 ConfigurableServiceStage（通用 Stage）

```java
/**
 * 可配置的服务 Stage
 * 编排 DataPreparer + Step + ResultValidator
 */
public class ConfigurableServiceStage implements TaskStage {
    private final String name;
    private final List<StageStepConfig> stepConfigs;  // Step 配置列表
    
    @Data
    public static class StageStepConfig {
        private DataPreparer dataPreparer;
        private StageStep step;
        private ResultValidator resultValidator;
    }
    
    @Override
    public StageResult execute(TaskRuntimeContext runtimeContext) {
        StageResult result = StageResult.start(name);
        StepContext stepContext = new StepContext();
        stepContext.setTenantId(runtimeContext.getTenantId().getValue());
        stepContext.setRuntimeContext(runtimeContext);
        
        // 顺序执行每个 Step
        for (StageStepConfig stepConfig : stepConfigs) {
            try {
                // 1. 准备数据
                stepConfig.getDataPreparer().prepare(runtimeContext, stepContext);
                
                // 2. 执行 Step
                StepResult stepResult = stepConfig.getStep().execute(stepContext);
                result.addStepResult(stepResult);
                
                if (!stepResult.isSuccess()) {
                    result.failure(FailureInfo.of(ErrorType.SYSTEM_ERROR, stepResult.getMessage()));
                    return result;
                }
                
                // 3. 验证结果
                ValidationResult validationResult = stepConfig.getResultValidator().validate(stepContext);
                if (!validationResult.isSuccess()) {
                    result.failure(FailureInfo.of(ErrorType.BUSINESS_ERROR, validationResult.getMessage()));
                    return result;
                }
                
            } catch (Exception e) {
                result.failure(FailureInfo.of(ErrorType.SYSTEM_ERROR, "Step 执行异常: " + e.getMessage()));
                return result;
            }
        }
        
        result.success();
        return result;
    }
}
```

---

## 📊 三个服务的实现示例

### 1️⃣ ASBC Gateway

```java
// ===== 数据准备器 =====
public class ASBCDataPreparer implements DataPreparer {
    private final TenantConfig tenantConfig;
    private final NacosClient nacosClient;
    private final StageConfigProperties stageConfig;
    
    @Override
    public void prepare(TaskRuntimeContext runtimeContext, StepContext stepContext) {
        // 解析 calledNumberRules
        MediaRoutingConfig mediaRouting = tenantConfig.getMediaRoutingConfig();
        String[] numbers = mediaRouting.getCalledNumberRules().split(",");
        
        // 获取 endpoint
        String endpoint = resolveEndpoint("asbcService");
        
        // 生成 token（如果需要）
        String accessToken = generateAccessToken();
        
        // 构建请求数据
        Map<String, Object> body = new HashMap<>();
        body.put("calledNumberMatch", Arrays.asList(numbers));
        body.put("targetTrunkGroupName", mediaRouting.getTrunkGroupId());
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        if (accessToken != null) {
            headers.put("Authorization", "Bearer " + accessToken);
        }
        
        // 放入 StepContext
        stepContext.putData("url", endpoint + "/api/sbc/traffic-switch");
        stepContext.putData("method", "POST");
        stepContext.putData("headers", headers);
        stepContext.putData("body", body);
    }
    
    private String generateAccessToken() {
        AuthConfig authConfig = stageConfig.getAuth("asbc");
        if (!authConfig.isEnabled()) {
            return null;  // ← 不填 Authorization header
        }
        
        if ("random".equals(authConfig.getTokenProvider())) {
            return RandomStringUtils.randomAlphanumeric(32);
        }
        
        return null;
    }
}

// ===== 结果验证器 =====
public class ASBCResultValidator implements ResultValidator {
    
    @Override
    public ValidationResult validate(StepContext stepContext) {
        HttpResponseData response = stepContext.getData("httpResponse", HttpResponseData.class);
        
        // 1. 检查 HTTP 状态码
        if (!response.is2xx()) {
            return ValidationResult.failure(
                String.format("HTTP 状态码错误: %d", response.getStatusCode())
            );
        }
        
        // 2. 解析 JSON
        try {
            ASBCResponse asbcResponse = response.parseBody(ASBCResponse.class);
            
            // 3. 检查业务 code
            if (asbcResponse.getCode() != 0) {
                return ValidationResult.failure(
                    String.format("ASBC 返回错误: code=%d, msg=%s", 
                        asbcResponse.getCode(), asbcResponse.getMsg())
                );
            }
            
            // 4. 检查 failList
            ASBCResponseData data = asbcResponse.getData();
            if (data.getFailList() != null && !data.getFailList().isEmpty()) {
                return ValidationResult.failure(buildFailureMessage(data));
            }
            
            // 5. 全部成功
            return ValidationResult.success(
                String.format("成功配置 %d 个规则", data.getSuccessList().size())
            );
            
        } catch (Exception e) {
            return ValidationResult.failure("响应解析失败: " + e.getMessage());
        }
    }
    
    private String buildFailureMessage(ASBCResponseData data) {
        StringBuilder sb = new StringBuilder("ASBC 配置部分失败:\n");
        
        if (data.getSuccessList() != null && !data.getSuccessList().isEmpty()) {
            sb.append("成功 (").append(data.getSuccessList().size()).append(" 项):\n");
            for (ASBCResultItem item : data.getSuccessList()) {
                sb.append("  ✓ ").append(item.getCalledNumberMatch())
                  .append(" → ").append(item.getTargetTrunkGroupName()).append("\n");
            }
        }
        
        if (data.getFailList() != null && !data.getFailList().isEmpty()) {
            sb.append("失败 (").append(data.getFailList().size()).append(" 项):\n");
            for (ASBCResultItem item : data.getFailList()) {
                sb.append("  ✗ ").append(item.getCalledNumberMatch())
                  .append(" → ").append(item.getTargetTrunkGroupName())
                  .append(" [").append(item.getMsg()).append("]\n");
            }
        }
        
        return sb.toString();
    }
}

// ===== Stage 组装 =====
public TaskStage createASBCStage() {
    StageStepConfig stepConfig = new StageStepConfig();
    stepConfig.setDataPreparer(new ASBCDataPreparer(tenantConfig, nacosClient, stageConfig));
    stepConfig.setStep(new HttpRequestStep(restTemplate));
    stepConfig.setResultValidator(new ASBCResultValidator());
    
    return new ConfigurableServiceStage("asbc-gateway", Arrays.asList(stepConfig));
}
```

---

### 2️⃣ Portal

```java
// ===== 数据准备器 =====
public class PortalDataPreparer implements DataPreparer {
    private final TenantConfig tenantConfig;
    private final NacosClient nacosClient;
    
    @Override
    public void prepare(TaskRuntimeContext runtimeContext, StepContext stepContext) {
        String endpoint = resolveEndpoint("portalService");
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantConfig.getTenantId());
        payload.put("deployUnitId", tenantConfig.getDeployUnitId());
        payload.put("version", tenantConfig.getDeployUnitVersion());
        // ... 其他字段
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        
        stepContext.putData("url", endpoint + "/api/notify");
        stepContext.putData("method", "POST");
        stepContext.putData("headers", headers);
        stepContext.putData("body", payload);
    }
}

// ===== 结果验证器 =====
public class PortalResultValidator implements ResultValidator {
    
    @Override
    public ValidationResult validate(StepContext stepContext) {
        HttpResponseData response = stepContext.getData("httpResponse", HttpResponseData.class);
        
        // Portal 只验证 HTTP 状态码
        if (response.is2xx()) {
            return ValidationResult.success(
                String.format("Portal 接收成功 (HTTP %d)", response.getStatusCode())
            );
        } else {
            return ValidationResult.failure(
                String.format("Portal 接收失败 (HTTP %d)", response.getStatusCode())
            );
        }
    }
}

// ===== Stage 组装 =====
public TaskStage createPortalStage() {
    StageStepConfig stepConfig = new StageStepConfig();
    stepConfig.setDataPreparer(new PortalDataPreparer(tenantConfig, nacosClient));
    stepConfig.setStep(new HttpRequestStep(restTemplate));
    stepConfig.setResultValidator(new PortalResultValidator());
    
    return new ConfigurableServiceStage("portal", Arrays.asList(stepConfig));
}
```

---

### 3️⃣ OBService（多 Step 组合）

```java
// ===== Step 1: Polling 数据准备器 =====
public class OBPollingDataPreparer implements DataPreparer {
    @Override
    public void prepare(TaskRuntimeContext runtimeContext, StepContext stepContext) {
        stepContext.putData("tenantId", runtimeContext.getTenantId().getValue());
        stepContext.putData("pollInterval", 5000);
        stepContext.putData("pollMaxAttempts", 20);
    }
}

// ===== Step 1: Polling 结果验证器 =====
public class OBPollingResultValidator implements ResultValidator {
    @Override
    public ValidationResult validate(StepContext stepContext) {
        Boolean isReady = stepContext.getData("pollingResult", Boolean.class);
        if (isReady != null && isReady) {
            return ValidationResult.success("轮询成功，Agent 已就绪");
        } else {
            return ValidationResult.failure("轮询失败，Agent 未就绪");
        }
    }
}

// ===== Step 2: ConfigWrite 数据准备器 =====
public class OBConfigWriteDataPreparer implements DataPreparer {
    private final TenantConfig tenantConfig;
    
    @Override
    public void prepare(TaskRuntimeContext runtimeContext, StepContext stepContext) {
        String tenantId = runtimeContext.getTenantId().getValue();
        ObConfig obConfig = tenantConfig.getObConfig();
        
        stepContext.putData("key", "deploy:config:" + tenantId);
        stepContext.putData("field", "ob-campaign");
        stepContext.putData("value", JSON.toJSONString(obConfig));
    }
}

// ===== Step 2: ConfigWrite 结果验证器 =====
public class OBConfigWriteResultValidator implements ResultValidator {
    @Override
    public ValidationResult validate(StepContext stepContext) {
        ConfigWriteResult writeResult = stepContext.getData("writeResult", ConfigWriteResult.class);
        if (writeResult != null && writeResult.isSuccess()) {
            return ValidationResult.success("配置写入成功");
        } else {
            return ValidationResult.failure("配置写入失败");
        }
    }
}

// ===== Stage 组装 =====
public TaskStage createOBServiceStage() {
    // Step 1: Polling
    StageStepConfig pollingConfig = new StageStepConfig();
    pollingConfig.setDataPreparer(new OBPollingDataPreparer());
    pollingConfig.setStep(new PollingStep(agentService));
    pollingConfig.setResultValidator(new OBPollingResultValidator());
    
    // Step 2: ConfigWrite
    StageStepConfig writeConfig = new StageStepConfig();
    writeConfig.setDataPreparer(new OBConfigWriteDataPreparer(tenantConfig));
    writeConfig.setStep(new ConfigWriteStep(redisTemplate));
    writeConfig.setResultValidator(new OBConfigWriteResultValidator());
    
    return new ConfigurableServiceStage("ob-service", Arrays.asList(pollingConfig, writeConfig));
}
```

---

## 📝 YAML 配置文件

```yaml
infrastructure:
  nacos: {...}
  fallbackInstances: {...}
  redis: {...}
  auth:
    asbc:
      enabled: false  # 关闭 → 不填 header
      tokenProvider: "random"

stages:
  # ASBC: 1 个 HttpRequestStep
  - name: asbc-gateway
    order: 1
    steps:
      - type: http-request
        order: 1
        data-preparer: "ASBCDataPreparer"
        result-validator: "ASBCResultValidator"
  
  # OBService: 2 个 Step (Polling + ConfigWrite)
  - name: ob-service
    order: 2
    steps:
      - type: polling
        order: 1
        data-preparer: "OBPollingDataPreparer"
        result-validator: "OBPollingResultValidator"
      
      - type: config-write
        order: 2
        data-preparer: "OBConfigWriteDataPreparer"
        result-validator: "OBConfigWriteResultValidator"
  
  # Portal: 1 个 HttpRequestStep
  - name: portal
    order: 3
    steps:
      - type: http-request
        order: 1
        data-preparer: "PortalDataPreparer"
        result-validator: "PortalResultValidator"
```

---

## 🎯 架构优势总结

### 1. Step 极简纯粹
```
HttpRequestStep: 发送 HTTP 请求，返回响应
ConfigWriteStep: 写入 Redis，返回结果
MessageBroadcastStep: 发送消息，返回接收者数量
PollingStep: 轮询接口，返回就绪状态
```

只有 3-4 种 Step，完全通用，数据无关。

### 2. 业务逻辑分离
```
DataPreparer: 准备数据（业务逻辑）
Step: 执行动作（技术实现）
ResultValidator: 验证结果（业务逻辑）
```

业务逻辑在 Stage 层，技术实现在 Step 层。

### 3. 易于扩展
新增服务只需要：
1. 实现 DataPreparer
2. 实现 ResultValidator
3. 在 YAML 中配置组装

无需修改 Step 代码。

### 4. 配置驱动
通过 YAML 配置：
- 使用哪些 Step
- 使用哪些 DataPreparer 和 ResultValidator
- Step 的执行顺序

### 5. 职责清晰
```
Step: 我只负责执行技术动作，不判断业务结果
DataPreparer: 我准备数据
ResultValidator: 我判断业务结果
Stage: 我编排流程
```

---

## ✅ 对比：原方案 vs 新方案

| 维度 | 原方案 | 新方案（您的建议）|
|------|-------|-----------------|
| Step 数量 | 5-6 个（ASBCStep, PortalStep, ...）| 3-4 个（Http, Config, Message, Polling）|
| Step 职责 | 包含业务判断 | 纯技术动作 |
| 业务逻辑 | 在 Factory + Parser | 在 DataPreparer + ResultValidator |
| 复用性 | 中等 | 极高 |
| 扩展性 | 需要实现 Factory + Parser | 需要实现 Preparer + Validator |
| 清晰度 | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |

---

## 🚀 实施优先级

### P0 - 基础 Step 实现
- [ ] HttpRequestStep
- [ ] ConfigWriteStep  
- [ ] MessageBroadcastStep
- [ ] PollingStep
- [ ] DataPreparer 接口
- [ ] ResultValidator 接口
- [ ] ConfigurableServiceStage

### P0 - ASBC Gateway
- [ ] ASBCDataPreparer
- [ ] ASBCResultValidator
- [ ] ASBCResponse 模型

### P1 - Portal
- [ ] PortalDataPreparer
- [ ] PortalResultValidator

### P1 - OBService
- [ ] OBPollingDataPreparer
- [ ] OBPollingResultValidator
- [ ] OBConfigWriteDataPreparer
- [ ] OBConfigWriteResultValidator

---

**这个方案非常优雅！Step 真正做到了数据无关和可复用！** ✅

