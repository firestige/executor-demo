# T-019: Redis ACK 服务（通用模块）

> **任务 ID**: T-019  
> **优先级**: P1  
> **状态**: 待办  
> **创建时间**: 2025-11-24

---

## 1. 任务目标

开发一个脱离 deploy 域的、可复用的 Redis 写入与服务上下文确认（ACK）服务，用于跟踪 Redis 写入并验证服务端是否已正确应用配置。

---

## 2. 业务场景

### 2.1 典型场景：配置推送与验证

**流程**：
1. 系统向 Redis 写入配置（如网关配置、服务配置）
2. 服务监听 Redis 变更，拉取新配置并应用
3. 系统需要验证：服务是否已成功应用新配置？

**问题**：
- 写入 Redis 成功 ≠ 服务已应用配置
- 服务可能延迟拉取、应用失败、或根本未感知变更
- 需要轮询服务端点确认配置已生效

### 2.2 核心需求

1. **跟踪写入**：记录向 Redis 写入的数据（包含 metadata）
2. **提取期望值**：从写入数据中提取期望的 metadata 值（通过 value_extract 函数）
3. **轮询验证**：访问服务端点，获取当前 metadata
4. **对比确认**：比较期望值与实际值，判断是否一致
5. **超时处理**：配置超时时间，超时则判定失败

---

## 3. 设计方案

### 3.1 核心接口

```java
/**
 * Redis ACK 服务
 */
public interface RedisAckService {
    
    /**
     * 跟踪 Redis 写入并等待确认
     * 
     * @param request ACK 请求配置
     * @return ACK 任务，可用于查询状态或取消
     */
    AckTask trackAndWait(AckRequest request);
    
    /**
     * 异步跟踪（非阻塞）
     */
    CompletableFuture<AckResult> trackAsync(AckRequest request);
    
    /**
     * 取消 ACK 任务
     */
    void cancel(String taskId);
    
    /**
     * 查询 ACK 任务状态
     */
    AckStatus getStatus(String taskId);
}
```

### 3.2 请求模型

```java
/**
 * ACK 请求配置
 */
public class AckRequest {
    // Redis 写入信息
    private RedisWriteInfo writeInfo;
    
    // 期望值提取函数
    private ValueExtractor valueExtractor;
    
    // 验证端点配置
    private EndpointConfig endpointConfig;
    
    // 验证策略
    private ValidationStrategy strategy;
    
    // 超时配置
    private Duration timeout;
    
    // 重试配置
    private RetryPolicy retryPolicy;
}

/**
 * Redis 写入信息
 */
public class RedisWriteInfo {
    private String key;
    private Object value;
    private Map<String, Object> metadata;  // 自定义数据结构的元数据
}

/**
 * 期望值提取器
 */
@FunctionalInterface
public interface ValueExtractor {
    /**
     * 从写入的数据中提取期望值
     * @param writeData Redis 写入的完整数据
     * @return 期望的 metadata 值
     */
    Map<String, Object> extract(Object writeData);
}

/**
 * 端点配置
 */
public class EndpointConfig {
    private String url;                    // 验证端点 URL
    private HttpMethod method;             // HTTP 方法（默认 GET）
    private Map<String, String> headers;   // 请求头
    private Duration requestTimeout;       // 单次请求超时
    private ResponseExtractor responseExtractor;  // 响应解析器
}

/**
 * 响应提取器
 */
@FunctionalInterface
public interface ResponseExtractor {
    /**
     * 从服务响应中提取实际 metadata
     * @param response HTTP 响应
     * @return 实际的 metadata
     */
    Map<String, Object> extract(HttpResponse response);
}

/**
 * 验证策略
 */
public class ValidationStrategy {
    private long pollIntervalSeconds = 3;     // 轮询间隔
    private int maxAttempts = 10;            // 最大尝试次数
    private MetadataMatcher matcher;         // 元数据匹配器
}

/**
 * 元数据匹配器
 */
@FunctionalInterface
public interface MetadataMatcher {
    /**
     * 判断实际值是否匹配期望值
     * @param expected 期望值
     * @param actual 实际值
     * @return true 如果匹配
     */
    boolean matches(Map<String, Object> expected, Map<String, Object> actual);
}
```

### 3.3 结果模型

```java
/**
 * ACK 结果
 */
public class AckResult {
    private String taskId;
    private AckStatus status;              // SUCCESS, TIMEOUT, MISMATCH, ERROR
    private Map<String, Object> expectedMetadata;
    private Map<String, Object> actualMetadata;
    private int attempts;
    private Duration totalDuration;
    private String errorMessage;
    private Instant completedAt;
}

/**
 * ACK 状态
 */
public enum AckStatus {
    PENDING,      // 等待中
    POLLING,      // 轮询中
    SUCCESS,      // 确认成功（metadata 匹配）
    TIMEOUT,      // 超时
    MISMATCH,     // 不匹配
    ERROR,        // 错误（网络、解析等）
    CANCELLED     // 已取消
}
```

---

## 4. 使用示例

### 4.1 简单场景：验证版本号

```java
// 1. 写入 Redis
Map<String, Object> gatewayConfig = Map.of(
    "version", "v1.2.3",
    "routes", routes,
    "metadata", Map.of("deployId", "deploy-001")
);
redisClient.set("gateway:config:tenant-001", gatewayConfig);

// 2. 配置 ACK 验证
AckRequest request = AckRequest.builder()
    .writeInfo(RedisWriteInfo.builder()
        .key("gateway:config:tenant-001")
        .value(gatewayConfig)
        .metadata(Map.of("version", "v1.2.3", "deployId", "deploy-001"))
        .build())
    
    // 提取期望值
    .valueExtractor(data -> {
        Map<String, Object> config = (Map<String, Object>) data;
        return Map.of(
            "version", config.get("version"),
            "deployId", ((Map) config.get("metadata")).get("deployId")
        );
    })
    
    // 端点配置
    .endpointConfig(EndpointConfig.builder()
        .url("http://gateway.tenant-001.svc/health")
        .method(HttpMethod.GET)
        .requestTimeout(Duration.ofSeconds(5))
        .responseExtractor(response -> {
            JsonNode json = objectMapper.readTree(response.body());
            return Map.of(
                "version", json.get("version").asText(),
                "deployId", json.get("metadata").get("deployId").asText()
            );
        })
        .build())
    
    // 验证策略
    .strategy(ValidationStrategy.builder()
        .pollIntervalSeconds(3)
        .maxAttempts(10)
        .matcher(DefaultMetadataMatcher.EXACT_MATCH)
        .build())
    
    .timeout(Duration.ofSeconds(60))
    .build();

// 3. 执行验证
AckResult result = ackService.trackAndWait(request);

if (result.getStatus() == AckStatus.SUCCESS) {
    log.info("配置已成功应用: {}", result.getActualMetadata());
} else {
    log.error("配置应用失败: {}, 期望: {}, 实际: {}", 
        result.getStatus(), 
        result.getExpectedMetadata(), 
        result.getActualMetadata());
}
```

### 4.2 异步验证

```java
CompletableFuture<AckResult> future = ackService.trackAsync(request);

future.thenAccept(result -> {
    if (result.getStatus() == AckStatus.SUCCESS) {
        log.info("配置已确认");
    }
}).exceptionally(ex -> {
    log.error("验证异常", ex);
    return null;
});
```

### 4.3 批量验证

```java
List<AckRequest> requests = tenants.stream()
    .map(tenant -> buildAckRequest(tenant))
    .collect(Collectors.toList());

List<CompletableFuture<AckResult>> futures = requests.stream()
    .map(ackService::trackAsync)
    .collect(Collectors.toList());

CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
    .thenRun(() -> {
        log.info("所有租户配置已验证完成");
    });
```

### 4.4 自定义匹配器

```java
// 只验证关键字段
MetadataMatcher customMatcher = (expected, actual) -> {
    return Objects.equals(expected.get("version"), actual.get("version")) &&
           Objects.equals(expected.get("deployId"), actual.get("deployId"));
};

ValidationStrategy strategy = ValidationStrategy.builder()
    .matcher(customMatcher)
    .build();
```

---

## 5. 配置支持

### 5.1 YAML 配置

```yaml
redis:
  ack:
    enabled: true
    default-timeout-seconds: 60
    default-poll-interval-seconds: 3
    default-max-attempts: 10
    
    executor:
      core-pool-size: 5
      max-pool-size: 20
      queue-capacity: 100
    
    http-client:
      connect-timeout-seconds: 5
      read-timeout-seconds: 10
      max-connections: 50
    
    metrics:
      enabled: true
    
    # 预定义 ACK 规则
    rules:
      - name: gateway-config
        key-pattern: "gateway:config:*"
        value-extractor: gateway-version-extractor
        endpoint-template: "http://gateway.{tenantId}.svc/health"
        response-extractor: standard-health-response
        poll-interval-seconds: 3
        max-attempts: 10
        timeout-seconds: 60
```

### 5.2 配置类

```java
@ConfigurationProperties(prefix = "redis.ack")
public class RedisAckProperties {
    private boolean enabled = true;
    private long defaultTimeoutSeconds = 60;
    private long defaultPollIntervalSeconds = 3;
    private int defaultMaxAttempts = 10;
    private ExecutorConfig executor = new ExecutorConfig();
    private HttpClientConfig httpClient = new HttpClientConfig();
    private MetricsConfig metrics = new MetricsConfig();
    private List<AckRule> rules = new ArrayList<>();
    
    // getters/setters
}
```

---

## 6. 实现类设计

```java
/**
 * 默认实现
 */
public class DefaultRedisAckService implements RedisAckService {
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final Map<String, AckTask> tasks;
    private final AckMetrics metrics;
    
    @Override
    public AckTask trackAndWait(AckRequest request) {
        AckTask task = createTask(request);
        tasks.put(task.getTaskId(), task);
        
        try {
            // 同步等待
            AckResult result = task.waitForCompletion(request.getTimeout());
            return task;
        } finally {
            tasks.remove(task.getTaskId());
        }
    }
    
    @Override
    public CompletableFuture<AckResult> trackAsync(AckRequest request) {
        AckTask task = createTask(request);
        tasks.put(task.getTaskId(), task);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.waitForCompletion(request.getTimeout());
            } finally {
                tasks.remove(task.getTaskId());
            }
        }, scheduler);
    }
    
    private AckTask createTask(AckRequest request) {
        return new AckTask(
            UUID.randomUUID().toString(),
            request,
            httpClient,
            scheduler,
            metrics
        );
    }
}

/**
 * ACK 任务
 */
public class AckTask {
    private final String taskId;
    private final AckRequest request;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<AckStatus> status;
    private final AtomicInteger attempts;
    
    public AckResult waitForCompletion(Duration timeout) {
        Instant startTime = Instant.now();
        Instant deadline = startTime.plus(timeout);
        
        Map<String, Object> expectedMetadata = 
            request.getValueExtractor().extract(request.getWriteInfo().getValue());
        
        status.set(AckStatus.POLLING);
        
        while (Instant.now().isBefore(deadline)) {
            attempts.incrementAndGet();
            
            try {
                // 调用服务端点
                HttpResponse response = callEndpoint();
                
                // 提取实际 metadata
                Map<String, Object> actualMetadata = 
                    request.getEndpointConfig()
                        .getResponseExtractor()
                        .extract(response);
                
                // 匹配验证
                boolean matches = request.getStrategy()
                    .getMatcher()
                    .matches(expectedMetadata, actualMetadata);
                
                if (matches) {
                    status.set(AckStatus.SUCCESS);
                    return buildResult(AckStatus.SUCCESS, 
                        expectedMetadata, actualMetadata, null);
                }
                
                // 未匹配，继续轮询
                Thread.sleep(request.getStrategy()
                    .getPollIntervalSeconds() * 1000);
                
            } catch (Exception e) {
                log.warn("ACK 验证失败: {}", e.getMessage());
            }
            
            // 检查最大尝试次数
            if (attempts.get() >= request.getStrategy().getMaxAttempts()) {
                status.set(AckStatus.TIMEOUT);
                return buildResult(AckStatus.TIMEOUT, 
                    expectedMetadata, null, "超过最大尝试次数");
            }
        }
        
        status.set(AckStatus.TIMEOUT);
        return buildResult(AckStatus.TIMEOUT, 
            expectedMetadata, null, "超时");
    }
    
    private HttpResponse callEndpoint() {
        EndpointConfig config = request.getEndpointConfig();
        // HTTP 调用实现...
    }
}
```

---

## 7. 监控与指标

### 7.1 关键指标

```java
public interface AckMetrics {
    // 活跃 ACK 任务数
    Gauge activeAckTasks();
    
    // ACK 成功次数
    Counter ackSuccessCount();
    
    // ACK 失败次数（按类型分组）
    Counter ackFailureCount(AckStatus status);
    
    // ACK 完成时长
    Timer ackDuration();
    
    // 平均轮询次数
    Histogram pollAttemptsDistribution();
}
```

---

## 8. 包结构

```
xyz.firestige.common.redis.ack/
├── RedisAckService.java                # 核心接口
├── DefaultRedisAckService.java         # 默认实现
├── model/
│   ├── AckRequest.java                 # 请求模型
│   ├── AckResult.java                  # 结果模型
│   ├── AckTask.java                    # 任务模型
│   └── AckStatus.java                  # 状态枚举
├── config/
│   ├── EndpointConfig.java             # 端点配置
│   ├── ValidationStrategy.java         # 验证策略
│   └── RetryPolicy.java                # 重试策略
├── extractor/
│   ├── ValueExtractor.java             # 值提取器接口
│   ├── ResponseExtractor.java          # 响应提取器接口
│   └── JsonPathExtractor.java          # JsonPath 实现
├── matcher/
│   ├── MetadataMatcher.java            # 匹配器接口
│   ├── ExactMatcher.java               # 精确匹配
│   └── PartialMatcher.java             # 部分匹配
├── metrics/
│   ├── AckMetrics.java                 # 指标接口
│   └── MicrometerAckMetrics.java       # Micrometer 实现
└── autoconfigure/
    ├── RedisAckAutoConfiguration.java
    └── RedisAckProperties.java
```

---

## 9. Definition of Done

- [ ] 核心接口和实现类完成
- [ ] 支持同步和异步验证
- [ ] 支持自定义 ValueExtractor 和 ResponseExtractor
- [ ] 支持自定义 MetadataMatcher
- [ ] 支持轮询策略配置（间隔、最大次数、超时）
- [ ] HTTP 客户端集成（支持超时、重试）
- [ ] 配置支持（YAML + @ConfigurationProperties）
- [ ] 监控指标集成（Micrometer）
- [ ] 单元测试覆盖率 > 80%
- [ ] 集成测试（Mock HTTP Server）
- [ ] 文档完善（使用指南、API 文档）
- [ ] 与现有系统集成（HealthCheckStep）

---

## 10. 参考资料

- RestTemplate / WebClient
- Apache HttpClient
- JsonPath
- 现有 HealthCheckStep 实现

