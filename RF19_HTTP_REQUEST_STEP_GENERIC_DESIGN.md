# RF-19 HttpRequestStep 通用化设计补充

**创建日期**: 2025-11-21  
**状态**: 设计方案

---

## 🎯 设计目标

**让 HttpRequestStep 成为真正通用的、数据无关的可复用函数**

### 核心思想

```
Step（通用）: 只负责发送 HTTP 请求
  ├─ 接收 HttpRequest（请求对象）
  ├─ 发送请求
  └─ 返回 HttpResponse（响应对象）

Stage（特定）: 负责构建请求和解析响应
  ├─ RequestFactory: 构建 HttpRequest
  ├─ ResponseParser: 解析 HttpResponse → 业务结果
  └─ 调用 HttpRequestStep
```

---

## 📐 设计方案

### 1. HttpRequest（请求对象）

```java
/**
 * HTTP 请求对象（数据容器）
 * Step 不关心业务语义，只负责发送
 */
public class HttpRequest {
    private String url;  // 完整 URL
    private String method;  // GET / POST / PUT / DELETE
    private Map<String, String> headers;  // HTTP Headers
    private Object body;  // 请求 Body（可以是 String, Map, POJO）
    private Integer connectTimeoutMs;  // 连接超时
    private Integer readTimeoutMs;  // 读取超时
    
    // Builder 模式
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        // ...builder methods...
    }
}
```

---

### 2. HttpResponse（响应对象）

```java
/**
 * HTTP 响应对象（数据容器）
 * Step 不关心业务语义，只负责返回
 */
public class HttpResponse {
    private int statusCode;  // HTTP 状态码
    private Map<String, String> headers;  // 响应 Headers
    private String body;  // 响应 Body（原始字符串）
    private Long durationMs;  // 请求耗时
    private Exception exception;  // 异常（如果有）
    
    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300 && exception == null;
    }
    
    public boolean is2xx() {
        return statusCode >= 200 && statusCode < 300;
    }
    
    public <T> T parseBody(Class<T> clazz) {
        // 使用 Jackson 解析 JSON
        return objectMapper.readValue(body, clazz);
    }
}
```

---

### 3. HttpRequestStep（通用 Step）

```java
/**
 * 通用 HTTP 请求 Step
 * 完全数据无关，只负责发送请求
 */
public class HttpRequestStep implements StageStep {
    private final String stepName;
    private final RestTemplate restTemplate;
    
    public HttpRequestStep(String stepName, RestTemplate restTemplate) {
        this.stepName = stepName;
        this.restTemplate = restTemplate;
    }
    
    @Override
    public String getStepName() {
        return stepName;
    }
    
    @Override
    public StepResult execute(StepContext context) {
        StepResult result = StepResult.start(stepName);
        
        try {
            // 1. 从 StepContext 获取 HttpRequest
            HttpRequest httpRequest = context.getData("httpRequest", HttpRequest.class);
            if (httpRequest == null) {
                result.finishFailure("StepContext 中未找到 httpRequest");
                return result;
            }
            
            // 2. 发送 HTTP 请求
            long startTime = System.currentTimeMillis();
            HttpResponse httpResponse = sendRequest(httpRequest);
            httpResponse.setDurationMs(System.currentTimeMillis() - startTime);
            
            // 3. 将响应放回 StepContext（供 Stage 解析）
            context.putData("httpResponse", httpResponse);
            
            // 4. Step 级别的成功判断（只看是否有异常）
            if (httpResponse.getException() != null) {
                result.finishFailure("HTTP 请求异常: " + httpResponse.getException().getMessage());
            } else {
                result.finishSuccess();
                result.setMessage(String.format("HTTP %s %s → %d (耗时 %dms)", 
                    httpRequest.getMethod(), 
                    httpRequest.getUrl(), 
                    httpResponse.getStatusCode(),
                    httpResponse.getDurationMs()));
            }
            
        } catch (Exception e) {
            result.finishFailure("Step 执行异常: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 发送 HTTP 请求（纯技术实现）
     */
    private HttpResponse sendRequest(HttpRequest request) {
        HttpResponse response = new HttpResponse();
        
        try {
            // 构建 Spring HttpHeaders
            HttpHeaders headers = new HttpHeaders();
            if (request.getHeaders() != null) {
                request.getHeaders().forEach(headers::set);
            }
            
            // 构建 HttpEntity
            HttpEntity<Object> entity = new HttpEntity<>(request.getBody(), headers);
            
            // 发送请求
            ResponseEntity<String> responseEntity;
            String method = request.getMethod().toUpperCase();
            
            switch (method) {
                case "GET":
                    responseEntity = restTemplate.getForEntity(request.getUrl(), String.class);
                    break;
                case "POST":
                    responseEntity = restTemplate.postForEntity(request.getUrl(), entity, String.class);
                    break;
                case "PUT":
                    responseEntity = restTemplate.exchange(
                        request.getUrl(), 
                        HttpMethod.PUT, 
                        entity, 
                        String.class
                    );
                    break;
                case "DELETE":
                    responseEntity = restTemplate.exchange(
                        request.getUrl(), 
                        HttpMethod.DELETE, 
                        entity, 
                        String.class
                    );
                    break;
                default:
                    throw new IllegalArgumentException("不支持的 HTTP 方法: " + method);
            }
            
            // 填充响应
            response.setStatusCode(responseEntity.getStatusCodeValue());
            response.setBody(responseEntity.getBody());
            if (responseEntity.getHeaders() != null) {
                Map<String, String> responseHeaders = new HashMap<>();
                responseEntity.getHeaders().forEach((key, values) -> {
                    if (values != null && !values.isEmpty()) {
                        responseHeaders.put(key, values.get(0));
                    }
                });
                response.setHeaders(responseHeaders);
            }
            
        } catch (Exception e) {
            response.setException(e);
        }
        
        return response;
    }
}
```

---

### 4. RequestFactory（Stage 提供）

```java
/**
 * HTTP 请求工厂接口
 * 每个 Stage 实现自己的请求构建逻辑
 */
public interface HttpRequestFactory {
    /**
     * 构建 HTTP 请求
     * 
     * @param context Step 执行上下文
     * @return HttpRequest
     */
    HttpRequest buildRequest(StepContext context);
}
```

**ASBC 实现**:
```java
public class ASBCRequestFactory implements HttpRequestFactory {
    
    @Override
    public HttpRequest buildRequest(StepContext context) {
        // 1. 从 context 获取数据
        String endpoint = context.getDataAsString("endpoint");
        List<String> calledNumberMatch = context.getData("calledNumberMatch", List.class);
        String targetTrunkGroupName = context.getDataAsString("targetTrunkGroupName");
        String accessToken = context.getDataAsString("accessToken");
        
        // 2. 构建请求 Body
        Map<String, Object> body = new HashMap<>();
        body.put("calledNumberMatch", calledNumberMatch);
        body.put("targetTrunkGroupName", targetTrunkGroupName);
        
        // 3. 构建 Headers
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        if (accessToken != null) {  // ← 只有 enabled=true 时才有 token
            headers.put("Authorization", "Bearer " + accessToken);
        }
        
        // 4. 构建 HttpRequest
        return HttpRequest.builder()
            .url(endpoint)
            .method("POST")
            .headers(headers)
            .body(body)
            .connectTimeoutMs(5000)
            .readTimeoutMs(30000)
            .build();
    }
}
```

**Portal 实现**:
```java
public class PortalRequestFactory implements HttpRequestFactory {
    
    @Override
    public HttpRequest buildRequest(StepContext context) {
        String endpoint = context.getDataAsString("endpoint");
        Object payload = context.getData("payload", Object.class);
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        
        return HttpRequest.builder()
            .url(endpoint)
            .method("POST")
            .headers(headers)
            .body(payload)
            .connectTimeoutMs(3000)
            .readTimeoutMs(10000)
            .build();
    }
}
```

---

### 5. ResponseParser（Stage 提供）

```java
/**
 * HTTP 响应解析器接口
 * 每个 Stage 实现自己的响应解析逻辑
 */
public interface HttpResponseParser {
    /**
     * 解析 HTTP 响应
     * 
     * @param response HTTP 响应
     * @return 解析结果（成功/失败 + 消息）
     */
    ParseResult parse(HttpResponse response);
}

/**
 * 解析结果
 */
public class ParseResult {
    private boolean success;
    private String message;
    private Object data;  // 可选的业务数据
    
    public static ParseResult success(String message) {
        ParseResult result = new ParseResult();
        result.setSuccess(true);
        result.setMessage(message);
        return result;
    }
    
    public static ParseResult failure(String message) {
        ParseResult result = new ParseResult();
        result.setSuccess(false);
        result.setMessage(message);
        return result;
    }
}
```

**ASBC 实现**:
```java
public class ASBCResponseParser implements HttpResponseParser {
    
    @Override
    public ParseResult parse(HttpResponse response) {
        // 1. 检查 HTTP 状态码
        if (!response.is2xx()) {
            return ParseResult.failure(
                String.format("HTTP 状态码错误: %d", response.getStatusCode())
            );
        }
        
        // 2. 解析 JSON 响应
        try {
            ASBCResponse asbcResponse = response.parseBody(ASBCResponse.class);
            
            // 3. 检查业务 code
            if (asbcResponse.getCode() != 0) {
                return ParseResult.failure(
                    String.format("ASBC 返回错误: code=%d, msg=%s", 
                        asbcResponse.getCode(), asbcResponse.getMsg())
                );
            }
            
            // 4. 检查 failList
            ASBCResponseData data = asbcResponse.getData();
            if (data.getFailList() != null && !data.getFailList().isEmpty()) {
                // 构建详细的失败信息
                String failureMessage = buildFailureMessage(data);
                return ParseResult.failure(failureMessage);
            }
            
            // 5. 全部成功
            return ParseResult.success(
                String.format("成功配置 %d 个规则", data.getSuccessList().size())
            );
            
        } catch (Exception e) {
            return ParseResult.failure("响应解析失败: " + e.getMessage());
        }
    }
    
    private String buildFailureMessage(ASBCResponseData data) {
        StringBuilder sb = new StringBuilder("ASBC 配置部分失败:\n");
        
        // 列出成功和失败详情
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
```

**Portal 实现**:
```java
public class PortalResponseParser implements HttpResponseParser {
    
    @Override
    public ParseResult parse(HttpResponse response) {
        // Portal 只需要验证 HTTP 状态码
        if (response.is2xx()) {
            return ParseResult.success(
                String.format("Portal 接收成功 (HTTP %d)", response.getStatusCode())
            );
        } else {
            return ParseResult.failure(
                String.format("Portal 接收失败 (HTTP %d): %s", 
                    response.getStatusCode(), 
                    response.getBody())
            );
        }
    }
}
```

---

### 6. ConfigurableServiceStage（使用 Factory 和 Parser）

```java
/**
 * 可配置的服务 Stage
 * 使用 RequestFactory 和 ResponseParser 处理业务逻辑
 */
public class ConfigurableServiceStage implements TaskStage {
    private final String name;
    private final List<StageStep> steps;
    private final StepContextPreparer contextPreparer;
    private final HttpRequestFactory requestFactory;  // ← 请求工厂（可选）
    private final HttpResponseParser responseParser;  // ← 响应解析器（可选）
    
    @Override
    public StageResult execute(TaskRuntimeContext runtimeContext) {
        // 1. 准备 StepContext
        StepContext stepContext = contextPreparer.prepare(runtimeContext);
        
        // 2. 如果有 requestFactory，构建 HttpRequest
        if (requestFactory != null) {
            HttpRequest httpRequest = requestFactory.buildRequest(stepContext);
            stepContext.putData("httpRequest", httpRequest);
        }
        
        // 3. 执行 Steps
        StageResult result = StageResult.start(name);
        for (StageStep step : steps) {
            StepResult stepResult = step.execute(stepContext);
            result.addStepResult(stepResult);
            
            if (!stepResult.isSuccess()) {
                result.failure(FailureInfo.of(ErrorType.SYSTEM_ERROR, stepResult.getMessage()));
                return result;
            }
        }
        
        // 4. 如果有 responseParser，解析响应
        if (responseParser != null) {
            HttpResponse httpResponse = stepContext.getData("httpResponse", HttpResponse.class);
            if (httpResponse != null) {
                ParseResult parseResult = responseParser.parse(httpResponse);
                if (!parseResult.isSuccess()) {
                    result.failure(FailureInfo.of(ErrorType.BUSINESS_ERROR, parseResult.getMessage()));
                    return result;
                }
                result.setMessage(parseResult.getMessage());
            }
        }
        
        result.success();
        return result;
    }
}
```

---

## 🔄 完整的执行流程

### ASBC Stage 执行流程

```
1. DynamicStageFactory 创建 Stage
   ├─ contextPreparer = new ASBCStepContextPreparer(...)
   ├─ requestFactory = new ASBCRequestFactory()
   ├─ responseParser = new ASBCResponseParser()
   ├─ steps = [new HttpRequestStep("asbc-http-request")]
   └─ new ConfigurableServiceStage(name, steps, contextPreparer, requestFactory, responseParser)

2. ConfigurableServiceStage.execute()
   ├─ stepContext = contextPreparer.prepare(runtimeContext)
   │   └─ 准备数据：endpoint, calledNumberMatch, targetTrunkGroupName, accessToken
   │
   ├─ httpRequest = requestFactory.buildRequest(stepContext)
   │   └─ 构建 HttpRequest（URL, Headers, Body）
   │
   ├─ stepContext.putData("httpRequest", httpRequest)
   │
   ├─ httpRequestStep.execute(stepContext)
   │   ├─ 发送 HTTP 请求
   │   └─ stepContext.putData("httpResponse", httpResponse)
   │
   ├─ httpResponse = stepContext.getData("httpResponse")
   │
   ├─ parseResult = responseParser.parse(httpResponse)
   │   └─ 解析 JSON，检查 code 和 failList
   │
   └─ 返回 StageResult（成功/失败 + 详细消息）
```

---

## 📊 对比：通用化前后

### ❌ 通用化前（Step 包含业务逻辑）

```java
// ASBCConfigRequestStep - 包含 ASBC 特定逻辑
public class ASBCConfigRequestStep implements StageStep {
    public StepResult execute(StepContext context) {
        // 构建 ASBC 请求
        // 发送请求
        // 解析 ASBC 响应
        // 判断 failList
    }
}

// PortalNotificationStep - 包含 Portal 特定逻辑
public class PortalNotificationStep implements StageStep {
    public StepResult execute(StepContext context) {
        // 构建 Portal 请求
        // 发送请求
        // 判断状态码
    }
}
```

**问题**:
- ❌ 每个服务都要实现一个 Step
- ❌ HTTP 请求逻辑重复
- ❌ Step 和业务耦合

---

### ✅ 通用化后（Step 完全数据无关）

```java
// HttpRequestStep - 完全通用
public class HttpRequestStep implements StageStep {
    public StepResult execute(StepContext context) {
        HttpRequest request = context.getData("httpRequest");
        HttpResponse response = sendRequest(request);
        context.putData("httpResponse", response);
    }
}

// ASBC 业务逻辑在 Factory 和 Parser
ASBCRequestFactory.buildRequest(context) → HttpRequest
ASBCResponseParser.parse(response) → ParseResult

// Portal 业务逻辑在 Factory 和 Parser
PortalRequestFactory.buildRequest(context) → HttpRequest
PortalResponseParser.parse(response) → ParseResult
```

**优势**:
- ✅ HttpRequestStep 完全复用
- ✅ Step 和业务解耦
- ✅ 业务逻辑集中在 Stage 层（Factory + Parser）

---

## ✅ 优势总结

### 1. Step 真正通用
- HttpRequestStep 只做技术动作
- 不包含任何业务逻辑
- 可以被任何需要 HTTP 的 Stage 复用

### 2. 业务逻辑集中
- RequestFactory：Stage 负责构建请求
- ResponseParser：Stage 负责解析响应
- 业务逻辑集中在 Stage 层，易于维护

### 3. 易于扩展
- 新增服务：实现 RequestFactory 和 ResponseParser
- 无需修改 HttpRequestStep
- 符合开闭原则

### 4. 职责清晰
```
Step: 我只负责发送 HTTP 请求，不关心业务
Stage: 我负责准备数据和解析结果
```

---

## 📋 实施清单

### ASBC Gateway
- [ ] ASBCRequestFactory
- [ ] ASBCResponseParser
- [ ] ASBCStepContextPreparer
- [ ] ASBCResponse 模型类

### Portal
- [ ] PortalRequestFactory
- [ ] PortalResponseParser
- [ ] PortalStepContextPreparer

### 通用组件
- [ ] HttpRequest
- [ ] HttpResponse
- [ ] HttpRequestStep
- [ ] HttpRequestFactory 接口
- [ ] HttpResponseParser 接口
- [ ] ParseResult

---

**此设计方案让 HttpRequestStep 成为真正的通用组件！** ✅

