# RF-19 统一 Stage/Step 架构设计方案

**创建日期**: 2025-11-21  
**覆盖范围**: RF-19-02, RF-19-03, RF-19-04  
**状态**: 待确认

---

## 一、设计原则

### 1.1 核心理念

```
Stage (服务级别)
  ├─ 准备数据（从 TenantConfig 提取）
  ├─ 编排 Steps（按顺序执行）
  └─ 聚合结果（判断成功/失败）

Step (可复用的原子操作)
  ├─ 接收数据（StepContext）
  ├─ 执行动作（HTTP、Redis、轮询等）
  └─ 返回结果（StepResult）
```

**关键点**:
- ✅ **Stage 不关心 Step 的具体实现**，只关心数据准备和结果聚合
- ✅ **Step 不关心业务语义**，只关心执行技术动作
- ✅ **配置驱动**：通过 YAML 定义 Stage → Step 的映射关系
- ✅ **最大复用**：相同的 Step 可以被不同 Stage 使用

---

## 二、通用架构设计

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────┐
│  TenantConfig (外部输入)                                 │
│  - MediaRoutingConfig                                   │
│  - NetworkEndpoints                                     │
│  - ObConfig                                             │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│  DynamicStageFactory (工厂)                             │
│  - 读取 deploy-stages.yml                               │
│  - 根据服务类型创建 Stage                                │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│  ConfigurableServiceStage (通用 Stage 实现)             │
│  - 准备 StepContext（数据转换）                         │
│  - 编排 Steps（顺序执行）                                │
│  - 聚合 StepResult                                      │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│  Reusable Steps (可复用的 Step 实现)                     │
│  - HttpRequestStep (HTTP POST/GET)                      │
│  - PollingStep (轮询直到状态变化)                        │
│  - KeyValueWriteStep (Redis HSET)                       │
│  - MessageBroadcastStep (Redis Pub/Sub)                 │
│  - EndpointPollingStep (健康检查轮询)                    │
└─────────────────────────────────────────────────────────┘
```

---

## 三、核心组件设计

### 3.1 StepContext（Step 执行上下文）

```java
/**
 * Step 执行上下文（数据容器）
 * Stage 准备数据，Step 消费数据
 */
public class StepContext {
    // 必需字段
    private String tenantId;
    private TaskRuntimeContext runtimeContext;
    
    // 灵活的数据容器（Stage 准备，Step 消费）
    private Map<String, Object> data = new HashMap<>();
    
    // 便捷方法
    public <T> T getData(String key, Class<T> type) {
        return type.cast(data.get(key));
    }
    
    public String getDataAsString(String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }
    
    public void putData(String key, Object value) {
        data.put(key, value);
    }
}
```

**数据示例**:
```java
// ASBC Stage 准备的数据
context.putData("calledNumberMatch", Arrays.asList("96765", "96755"));
context.putData("targetTrunkGroupName", "ka-gw");
context.putData("endpoint", "https://192.168.1.100:8080/api/sbc/traffic-switch");
context.putData("accessToken", "random-hex-token");

// Portal Stage 准备的数据
context.putData("endpoint", "http://192.168.1.20:8080/api/notify");
context.putData("method", "POST");
context.putData("payload", jsonPayload);

// OBService Stage 准备的数据
context.putData("pollEndpoint", "http://agent-service/api/judge");
context.putData("pollInterval", 5000);
context.putData("obConfig", obConfigJson);
```

---

### 3.2 ConfigurableServiceStage（通用 Stage 实现）

```java
/**
 * 通用的可配置 Stage 实现
 * 通过配置驱动 Step 编排
 */
public class ConfigurableServiceStage implements TaskStage {
    private final String name;
    private final List<StageStep> steps;
    private final StepContextPreparer contextPreparer;  // 数据准备器
    
    public ConfigurableServiceStage(
        String name, 
        List<StageStep> steps,
        StepContextPreparer contextPreparer
    ) {
        this.name = name;
        this.steps = steps;
        this.contextPreparer = contextPreparer;
    }
    
    @Override
    public StageResult execute(TaskRuntimeContext runtimeContext) {
        // 1. 准备 StepContext（Stage 职责）
        StepContext stepContext = contextPreparer.prepare(runtimeContext);
        
        // 2. 顺序执行 Steps
        StageResult result = StageResult.start(name);
        for (StageStep step : steps) {
            StepResult stepResult = step.execute(stepContext);
            result.addStepResult(stepResult);
            
            if (!stepResult.isSuccess()) {
                result.failure(stepResult.getFailureInfo());
                return result;
            }
        }
        
        result.success();
        return result;
    }
}
```

---

### 3.3 StepContextPreparer（数据准备器接口）

```java
/**
 * Step 上下文准备器
 * 每个 Stage 实现自己的数据准备逻辑
 */
public interface StepContextPreparer {
    /**
     * 准备 Step 执行所需的上下文数据
     * 
     * @param runtimeContext Task 运行时上下文
     * @return StepContext
     */
    StepContext prepare(TaskRuntimeContext runtimeContext);
}
```

**实现示例**:
```java
// ASBC Stage 的数据准备器
public class ASBCStepContextPreparer implements StepContextPreparer {
    private final TenantConfig tenantConfig;
    private final NacosClient nacosClient;
    private final StageConfigProperties stageConfig;
    
    @Override
    public StepContext prepare(TaskRuntimeContext runtimeContext) {
        StepContext context = new StepContext();
        context.setTenantId(runtimeContext.getTenantId().getValue());
        context.setRuntimeContext(runtimeContext);
        
        // 1. 解析 calledNumberRules
        MediaRoutingConfig mediaRouting = tenantConfig.getMediaRoutingConfig();
        String[] numbers = mediaRouting.getCalledNumberRules().split(",");
        context.putData("calledNumberMatch", Arrays.asList(numbers));
        context.putData("targetTrunkGroupName", mediaRouting.getTrunkGroupId());
        
        // 2. 获取 endpoint（Nacos → Fallback）
        String endpoint = resolveEndpoint("asbc-gateway");
        context.putData("endpoint", endpoint + "/api/sbc/traffic-switch");
        
        // 3. 生成 accessToken（扩展点）
        String token = generateAccessToken();
        context.putData("accessToken", token);
        
        return context;
    }
    
    private String resolveEndpoint(String serviceName) {
        // 优先从 Nacos 获取
        List<String> instances = nacosClient.getInstances(serviceName);
        if (instances != null && !instances.isEmpty()) {
            return "https://" + instances.get(0);
        }
        
        // 降级到配置文件
        List<String> fallbackInstances = stageConfig.getFallbackInstances(serviceName);
        if (fallbackInstances != null && !fallbackInstances.isEmpty()) {
            return "https://" + fallbackInstances.get(0);
        }
        
        throw new IllegalStateException("无法解析服务实例: " + serviceName);
    }
    
    private String generateAccessToken() {
        // 检查是否启用鉴权
        if (!stageConfig.isAuthEnabled("asbc-gateway")) {
            return RandomStringUtils.randomAlphanumeric(32);  // 随机 hex
        }
        
        // TODO: 实现真实的 token 获取逻辑
        return "real-token";
    }
}
```

---

### 3.4 可复用的 Step 实现

#### 3.4.1 HttpRequestStep（通用 HTTP 请求）

```java
/**
 * 通用 HTTP 请求 Step
 * 支持 POST/GET，支持响应验证
 */
public class HttpRequestStep implements StageStep {
    private final String stepName;
    private final RestTemplate restTemplate;
    private final HttpRequestConfig config;
    
    @Override
    public StepResult execute(StepContext context) {
        StepResult result = StepResult.start(stepName);
        
        try {
            // 1. 构建请求
            String endpoint = context.getDataAsString("endpoint");
            String method = config.getMethod();  // POST/GET
            Object payload = context.getData("payload", Object.class);
            Map<String, String> headers = buildHeaders(context);
            
            // 2. 发送请求
            ResponseEntity<String> response;
            if ("POST".equals(method)) {
                HttpEntity<Object> entity = new HttpEntity<>(payload, new HttpHeaders(headers));
                response = restTemplate.postForEntity(endpoint, entity, String.class);
            } else {
                // GET 请求
                response = restTemplate.getForEntity(endpoint, String.class);
            }
            
            // 3. 验证响应
            boolean isSuccess = validateResponse(response, config);
            
            if (isSuccess) {
                result.finishSuccess();
            } else {
                String message = String.format("HTTP 请求失败: status=%d, body=%s", 
                    response.getStatusCodeValue(), response.getBody());
                result.finishFailure(message);
            }
            
        } catch (Exception e) {
            result.finishFailure("HTTP 请求异常: " + e.getMessage());
        }
        
        return result;
    }
    
    private Map<String, String> buildHeaders(StepContext context) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        
        // 可选的鉴权 header
        String token = context.getDataAsString("accessToken");
        if (token != null) {
            headers.put("Authorization", "Bearer " + token);
        }
        
        return headers;
    }
    
    private boolean validateResponse(ResponseEntity<String> response, HttpRequestConfig config) {
        // 验证 HTTP 状态码
        if (config.getValidationType() == ValidationType.HTTP_STATUS) {
            return response.getStatusCodeValue() == config.getExpectedStatus();
        }
        
        // 验证响应 Body（JSON）
        if (config.getValidationType() == ValidationType.JSON_BODY) {
            return validateJsonBody(response.getBody(), config);
        }
        
        return false;
    }
}
```

#### 3.4.2 ASBCConfigRequestStep（ASBC 专用）

```java
/**
 * ASBC 配置请求 Step
 * 特殊处理：需要解析 successList 和 failList
 */
public class ASBCConfigRequestStep implements StageStep {
    private final RestTemplate restTemplate;
    
    @Override
    public StepResult execute(StepContext context) {
        StepResult result = StepResult.start("asbc-config-request");
        
        try {
            // 1. 构建请求
            String endpoint = context.getDataAsString("endpoint");
            List<String> calledNumberMatch = context.getData("calledNumberMatch", List.class);
            String targetTrunkGroupName = context.getDataAsString("targetTrunkGroupName");
            String accessToken = context.getDataAsString("accessToken");
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("calledNumberMatch", calledNumberMatch);
            requestBody.put("targetTrunkGroupName", targetTrunkGroupName);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + accessToken);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            // 2. 发送请求
            ResponseEntity<ASBCResponse> response = restTemplate.postForEntity(
                endpoint, 
                entity, 
                ASBCResponse.class
            );
            
            // 3. 解析响应
            ASBCResponse asbcResponse = response.getBody();
            if (asbcResponse == null || asbcResponse.getCode() != 0) {
                String message = asbcResponse != null ? asbcResponse.getMsg() : "响应为空";
                result.finishFailure("ASBC 配置下发失败: " + message);
                return result;
            }
            
            // 4. 检查 data.failList
            ASBCResponseData data = asbcResponse.getData();
            if (data.getFailList() != null && !data.getFailList().isEmpty()) {
                // 有失败项，整体失败
                String failureMessage = buildFailureMessage(data);
                result.finishFailure(failureMessage);
                return result;
            }
            
            // 5. 全部成功
            result.finishSuccess();
            result.setMessage(String.format("成功配置 %d 个规则", data.getSuccessList().size()));
            
        } catch (Exception e) {
            result.finishFailure("ASBC 请求异常: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 构建失败信息（包含成功和失败的详细列表）
     */
    private String buildFailureMessage(ASBCResponseData data) {
        StringBuilder sb = new StringBuilder("ASBC 配置部分失败:\n");
        
        // 成功列表
        if (data.getSuccessList() != null && !data.getSuccessList().isEmpty()) {
            sb.append("成功 (").append(data.getSuccessList().size()).append(" 项):\n");
            for (ASBCResultItem item : data.getSuccessList()) {
                sb.append("  - ").append(item.getCalledNumberMatch())
                  .append(" → ").append(item.getTargetTrunkGroupName())
                  .append("\n");
            }
        }
        
        // 失败列表
        if (data.getFailList() != null && !data.getFailList().isEmpty()) {
            sb.append("失败 (").append(data.getFailList().size()).append(" 项):\n");
            for (ASBCResultItem item : data.getFailList()) {
                sb.append("  - ").append(item.getCalledNumberMatch())
                  .append(" → ").append(item.getTargetTrunkGroupName())
                  .append(" [错误: ").append(item.getMsg()).append("]\n");
            }
        }
        
        return sb.toString();
    }
}

// ===== 响应模型 =====

@Data
public class ASBCResponse {
    private Integer code;
    private String msg;
    private ASBCResponseData data;
}

@Data
public class ASBCResponseData {
    private List<ASBCResultItem> successList;
    private List<ASBCResultItem> failList;
}

@Data
public class ASBCResultItem {
    private Integer code;
    private String msg;
    private String calledNumberMatch;
    private String targetTrunkGroupName;
}
```

#### 3.4.3 PollingStep（轮询 Step）

```java
/**
 * 轮询 Step
 * 用于 OBService：定时轮询 AgentService.judgeAgent()，直到返回 true
 */
public class PollingStep implements StageStep {
    private final AgentService agentService;
    
    @Override
    public StepResult execute(StepContext context) {
        StepResult result = StepResult.start("polling-step");
        
        try {
            String tenantId = context.getTenantId();
            Integer intervalMs = context.getData("pollInterval", Integer.class);
            Integer maxAttempts = context.getData("pollMaxAttempts", Integer.class);
            
            int attempts = 0;
            while (attempts < maxAttempts) {
                // 调用判断接口
                boolean isReady = agentService.judgeAgent(tenantId);
                
                if (isReady) {
                    result.finishSuccess();
                    result.setMessage(String.format("轮询成功，尝试次数: %d", attempts + 1));
                    return result;
                }
                
                // 未就绪，等待后重试
                attempts++;
                if (attempts < maxAttempts) {
                    Thread.sleep(intervalMs);
                }
            }
            
            // 超过最大尝试次数
            result.finishFailure(String.format("轮询超时：已尝试 %d 次", maxAttempts));
            
        } catch (Exception e) {
            result.finishFailure("轮询异常: " + e.getMessage());
        }
        
        return result;
    }
}
```

---

## 四、配置文件增强

### 4.1 deploy-stages.yml 增强

```yaml
# Infrastructure Configuration (基础设施配置)
infrastructure:
  # Nacos Configuration
  nacos:
    services:
      asbcService: "asbc-gateway-service"
      portalService: "portal-service"
      obService: "ob-service"
  
  # Fallback Instances (Nacos 不可用时降级)
  fallbackInstances:
    asbc:
      - "192.168.1.100:8080"
      - "192.168.1.101:8080"
    portal:
      - "192.168.1.20:8080"
    ob-service:
      - "192.168.1.30:8080"
  
  # Redis Configuration
  redis:
    hashKeyPrefix: "deploy:config:"
    pubsubTopic: "deploy.config.notify"
  
  # Authentication Configuration (鉴权配置)
  auth:
    asbc:
      enabled: false  # 默认关闭鉴权
    portal:
      enabled: false
    ob-service:
      enabled: false

# Stages Configuration (Stage 编排配置)
stages:
  # ASBC Gateway Stage
  - name: asbc-gateway
    steps:
      - type: asbc-config-request
        config:
          nacos-service-name: "asbcService"  # 在 infrastructure.nacos.services 中查找
          fallback-key: "asbc"  # 在 infrastructure.fallbackInstances 中查找
          endpoint-path: "/api/sbc/traffic-switch"
          http-method: "POST"
          validation-type: "response-body"  # 验证响应 body
          auth-key: "asbc"  # 在 infrastructure.auth 中查找
        retry-policy:
          max-attempts: 1
          interval-seconds: 0
  
  # OB Service Stage
  - name: ob-service
    steps:
      # Step 1: 轮询 AgentService 直到返回 true
      - type: polling
        config:
          poll-interval-ms: 5000  # 轮询间隔（毫秒）
          poll-max-attempts: 20  # 最大尝试次数
          service-key: "ob-service"  # 用于日志和监控
      
      # Step 2: Redis 写入 ObConfig
      - type: key-value-write
        config:
          hash-key-prefix-key: "redis.hashKeyPrefix"  # 引用 infrastructure.redis.hashKeyPrefix
          hash-field: "ob-campaign"
          value-type: "json"  # ObConfig 序列化为 JSON
        retry-policy:
          max-attempts: 3
          interval-seconds: 1
  
  # Portal Stage
  - name: portal
    steps:
      - type: http-request
        config:
          nacos-service-name: "portalService"
          fallback-key: "portal"
          endpoint-path: "/api/notify"
          http-method: "POST"
          validation-type: "http-status"  # 只验证 HTTP 状态码
          expected-status: 200
          auth-key: "portal"
        retry-policy:
          max-attempts: 3
          interval-seconds: 1
```

---

## 五、实施计划

### 5.1 RF-19-02: ASBC 实施

**优先级**: P0

**任务清单**:
1. ✅ 创建 ASBCResponse 模型类
2. ✅ 创建 ASBCStepContextPreparer
3. ✅ 实现 ASBCConfigRequestStep
4. ✅ 更新 deploy-stages.yml（ASBC 配置）
5. ✅ 集成测试

**预计时间**: 4-5 小时

---

### 5.2 RF-19-03: OBService 实施

**优先级**: P1

**任务清单**:
1. ✅ 定义 AgentService 接口
2. ✅ 创建 ObConfig 模型
3. ✅ 创建 OBStepContextPreparer
4. ✅ 实现 PollingStep
5. ✅ 复用 KeyValueWriteStep（已存在）
6. ✅ 更新 deploy-stages.yml（OB 配置）
7. ✅ 集成测试

**预计时间**: 6-8 小时

---

### 5.3 RF-19-04: Portal 实施

**优先级**: P1

**任务清单**:
1. ✅ 创建 PortalStepContextPreparer
2. ✅ 实现 HttpRequestStep（通用）
3. ✅ 更新 deploy-stages.yml（Portal 配置）
4. ✅ 集成测试

**预计时间**: 3-4 小时

---

## 六、关键设计决策

### 6.1 ASBC 关键点

| 问题 | 决策 |
|------|------|
| calledNumberRules 格式 | 字符串，逗号分隔，Step 负责拆分 |
| MediaRoutingConfig 是否调整 | 不调整，仅在 Step 中转换 |
| 解析失败判断 | code != 0 即失败 |
| 部分成功处理 | 无部分成功，failList 不为空即失败 |
| 失败信息格式 | 字符串，包含成功和失败的详细列表 |
| access_token 获取 | 扩展点设计，默认随机 hex |
| endpoint 解析 | Nacos → Fallback（配置文件）|

### 6.2 OBService 关键点

| 问题 | 决策 |
|------|------|
| 轮询间隔 | 可配置，默认 5 秒 |
| 最大尝试次数 | 可配置，默认 20 次 |
| ObConfig 来源 | 从 TenantConfig 提取 |
| Redis key 格式 | `{prefix}:{tenantId}:ob-campaign` |
| Redis value 格式 | ObConfig JSON 字符串 |

### 6.3 Portal 关键点

| 问题 | 决策 |
|------|------|
| 请求方法 | POST（可配置支持 GET）|
| 成功判断 | HTTP 2xx 状态码 |
| 响应 Body 解析 | 可选，默认只看状态码 |
| endpoint 解析 | Nacos → Fallback（配置文件）|

---

## 七、架构优势

### 7.1 可复用性

✅ **Step 复用**：
- HttpRequestStep 可用于 Portal 和其他 HTTP 场景
- KeyValueWriteStep 可用于所有 Redis 写入场景
- PollingStep 可用于所有轮询场景

✅ **配置驱动**：
- 新增服务只需修改 YAML，无需修改代码
- Step 编排灵活，可任意组合

### 7.2 可扩展性

✅ **扩展点清晰**：
- StepContextPreparer：数据准备逻辑
- StageStep：原子操作实现
- 配置文件：服务和 Step 的映射

✅ **鉴权扩展点**：
- authEnabled 开关
- 可插拔的 token 获取策略

### 7.3 可维护性

✅ **职责清晰**：
- Stage：数据准备 + 编排
- Step：技术实现
- Config：映射关系

✅ **易于测试**：
- Step 可独立单元测试
- StepContextPreparer 可独立测试
- Stage 可集成测试

---

## 八、待确认问题

### 请用户确认

1. **架构方案**: 是否同意通用的 ConfigurableServiceStage + 可复用 Step 的设计？
2. **ASBC 实现**: 是否同意 ASBCConfigRequestStep 的实现方式？
3. **OBService 实现**: 是否同意 PollingStep + KeyValueWriteStep 的组合？
4. **Portal 实现**: 是否同意复用 HttpRequestStep？
5. **配置文件**: 是否同意 deploy-stages.yml 的增强方案？

---

**总结**: 通过统一的 Stage/Step 架构，我们可以最大限度地复用代码，通过配置驱动实现灵活的服务编排，同时保持清晰的职责分离。

