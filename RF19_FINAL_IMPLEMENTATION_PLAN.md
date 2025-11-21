# RF-19 最终实施方案：函数注入 + 代码编排

**创建日期**: 2025-11-21  
**状态**: 最终确定方案

---

## 🎯 核心设计决策

### 1. 轮询使用函数注入

```java
/**
 * 轮询 Step（通用，支持函数注入）
 */
public class PollingStep implements StageStep {
    private final String stepName;
    
    // 函数式接口：轮询条件判断
    @FunctionalInterface
    public interface PollCondition {
        boolean check(StepContext context) throws Exception;
    }
    
    @Override
    public StepResult execute(StepContext context) {
        StepResult result = StepResult.start(stepName);
        
        try {
            // 1. 从 context 获取配置
            Integer intervalMs = context.getData("pollInterval", Integer.class);
            Integer maxAttempts = context.getData("pollMaxAttempts", Integer.class);
            PollCondition condition = context.getData("pollCondition", PollCondition.class);
            
            if (condition == null) {
                result.finishFailure("未提供轮询条件函数");
                return result;
            }
            
            // 2. 执行轮询
            int attempts = 0;
            while (attempts < maxAttempts) {
                boolean isReady = condition.check(context);  // ← 调用注入的函数
                
                if (isReady) {
                    context.putData("pollingResult", true);
                    result.finishSuccess();
                    result.setMessage(String.format("轮询成功，尝试次数: %d", attempts + 1));
                    return result;
                }
                
                attempts++;
                if (attempts < maxAttempts) {
                    Thread.sleep(intervalMs);
                }
            }
            
            // 3. 超时失败
            context.putData("pollingResult", false);
            result.finishFailure(String.format("轮询超时：已尝试 %d 次", maxAttempts));
            
        } catch (Exception e) {
            result.finishFailure("轮询异常: " + e.getMessage());
        }
        
        return result;
    }
}
```

**使用示例**：
```java
// OBService 的轮询逻辑
stepContext.putData("pollInterval", 5000);
stepContext.putData("pollMaxAttempts", 20);
stepContext.putData("pollCondition", (PollCondition) (ctx) -> {
    String tenantId = ctx.getTenantId();
    return agentService.judgeAgent(tenantId);  // ← 定制化逻辑
});
```

---

### 2. 使用代码编排（推荐）

**理由**：
- ✅ 切换动作不频繁变化
- ✅ 代码编排更直观、更灵活
- ✅ 有 IDE 支持，易于重构
- ✅ 类型安全，编译期检查
- ✅ 配合软件发布，无需动态配置

**实现方式**：通过 StageFactory 的代码组装

---

## 🏗️ 完整架构设计

### 核心组件

#### 1. DataPreparer（数据准备器）

```java
/**
 * 数据准备器接口
 */
@FunctionalInterface
public interface DataPreparer {
    void prepare(TaskRuntimeContext runtimeContext, StepContext stepContext);
}
```

#### 2. ResultValidator（结果验证器）

```java
/**
 * 结果验证器接口
 */
@FunctionalInterface
public interface ResultValidator {
    ValidationResult validate(StepContext stepContext);
}

@Data
public class ValidationResult {
    private boolean success;
    private String message;
    
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

#### 3. ConfigurableServiceStage（通用 Stage）

```java
/**
 * 可配置的服务 Stage
 * 支持多个 Step 的编排
 */
public class ConfigurableServiceStage implements TaskStage {
    private final String name;
    private final List<StepConfig> stepConfigs;
    
    @Data
    @Builder
    public static class StepConfig {
        private String stepName;
        private DataPreparer dataPreparer;
        private StageStep step;
        private ResultValidator resultValidator;
    }
    
    public ConfigurableServiceStage(String name, List<StepConfig> stepConfigs) {
        this.name = name;
        this.stepConfigs = stepConfigs;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public StageResult execute(TaskRuntimeContext runtimeContext) {
        StageResult result = StageResult.start(name);
        StepContext stepContext = new StepContext();
        stepContext.setTenantId(runtimeContext.getTenantId().getValue());
        stepContext.setRuntimeContext(runtimeContext);
        
        // 顺序执行每个 Step
        for (StepConfig stepConfig : stepConfigs) {
            try {
                // 1. 准备数据
                if (stepConfig.getDataPreparer() != null) {
                    stepConfig.getDataPreparer().prepare(runtimeContext, stepContext);
                }
                
                // 2. 执行 Step
                StepResult stepResult = stepConfig.getStep().execute(stepContext);
                result.addStepResult(stepResult);
                
                if (!stepResult.isSuccess()) {
                    result.failure(FailureInfo.of(ErrorType.SYSTEM_ERROR, stepResult.getMessage()));
                    return result;
                }
                
                // 3. 验证结果
                if (stepConfig.getResultValidator() != null) {
                    ValidationResult validationResult = stepConfig.getResultValidator().validate(stepContext);
                    if (!validationResult.isSuccess()) {
                        result.failure(FailureInfo.of(ErrorType.BUSINESS_ERROR, validationResult.getMessage()));
                        return result;
                    }
                }
                
            } catch (Exception e) {
                result.failure(FailureInfo.of(ErrorType.SYSTEM_ERROR, 
                    String.format("Step '%s' 执行异常: %s", stepConfig.getStepName(), e.getMessage())));
                return result;
            }
        }
        
        result.success();
        return result;
    }
    
    @Override
    public void rollback(TaskRuntimeContext ctx) {
        // 可选的回滚逻辑
    }
    
    @Override
    public List<StageStep> getSteps() {
        return stepConfigs.stream()
            .map(StepConfig::getStep)
            .collect(Collectors.toList());
    }
    
    @Override
    public boolean canSkip(TaskRuntimeContext ctx) {
        return false;
    }
}
```

---

## 📦 DynamicStageFactory（代码编排）

```java
/**
 * 动态 Stage 工厂（代码编排）
 * 根据 TenantConfig 动态创建 Stage 列表
 */
@Component
public class DynamicStageFactory {
    
    private final RestTemplate restTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final AgentService agentService;
    private final NacosClient nacosClient;
    private final StageConfigProperties stageConfig;
    
    /**
     * 构建 Stage 列表
     * 
     * @param tenantConfig 租户配置
     * @return Stage 列表（严格按顺序）
     */
    public List<TaskStage> buildStages(TenantConfig tenantConfig) {
        List<TaskStage> stages = new ArrayList<>();
        
        // ===== Stage 1: ASBC Gateway =====
        if (tenantConfig.getMediaRoutingConfig() != null) {
            stages.add(createASBCStage(tenantConfig));
        }
        
        // ===== Stage 2: OB Service =====
        if (tenantConfig.getObConfig() != null) {
            stages.add(createOBServiceStage(tenantConfig));
        }
        
        // ===== Stage 3: Portal =====
        if (tenantConfig.getNetworkEndpoints() != null) {
            stages.add(createPortalStage(tenantConfig));
        }
        
        // ===== Stage 4: Blue-Green Gateway =====
        // stages.add(createBlueGreenGatewayStage(tenantConfig));
        
        return stages;
    }
    
    // ========================================
    // ASBC Gateway Stage
    // ========================================
    
    private TaskStage createASBCStage(TenantConfig tenantConfig) {
        StepConfig stepConfig = StepConfig.builder()
            .stepName("asbc-http-request")
            .dataPreparer(createASBCDataPreparer(tenantConfig))
            .step(new HttpRequestStep(restTemplate))
            .resultValidator(createASBCResultValidator())
            .build();
        
        return new ConfigurableServiceStage("asbc-gateway", Arrays.asList(stepConfig));
    }
    
    /**
     * ASBC 数据准备器
     */
    private DataPreparer createASBCDataPreparer(TenantConfig tenantConfig) {
        return (runtimeContext, stepContext) -> {
            MediaRoutingConfig mediaRouting = tenantConfig.getMediaRoutingConfig();
            
            // 1. 解析 calledNumberRules
            String[] numbers = mediaRouting.getCalledNumberRules().split(",");
            
            // 2. 获取 endpoint (Nacos → Fallback)
            String endpoint = resolveEndpoint("asbcService", "asbc");
            
            // 3. 生成 token (if enabled)
            String accessToken = generateAccessToken("asbc");
            
            // 4. 构建请求数据
            Map<String, Object> body = new HashMap<>();
            body.put("calledNumberMatch", Arrays.asList(numbers));
            body.put("targetTrunkGroupName", mediaRouting.getTrunkGroupId());
            
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            if (accessToken != null) {
                headers.put("Authorization", "Bearer " + accessToken);
            }
            
            // 5. 放入 StepContext
            stepContext.putData("url", endpoint + "/api/sbc/traffic-switch");
            stepContext.putData("method", "POST");
            stepContext.putData("headers", headers);
            stepContext.putData("body", body);
        };
    }
    
    /**
     * ASBC 结果验证器
     */
    private ResultValidator createASBCResultValidator() {
        return (stepContext) -> {
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
                    return ValidationResult.failure(buildASBCFailureMessage(data));
                }
                
                // 5. 全部成功
                return ValidationResult.success(
                    String.format("成功配置 %d 个规则", data.getSuccessList().size())
                );
                
            } catch (Exception e) {
                return ValidationResult.failure("响应解析失败: " + e.getMessage());
            }
        };
    }
    
    // ========================================
    // OB Service Stage (多 Step 组合)
    // ========================================
    
    private TaskStage createOBServiceStage(TenantConfig tenantConfig) {
        // Step 1: Polling
        StepConfig pollingConfig = StepConfig.builder()
            .stepName("ob-polling")
            .dataPreparer(createOBPollingDataPreparer())
            .step(new PollingStep("ob-polling"))
            .resultValidator(createOBPollingResultValidator())
            .build();
        
        // Step 2: Config Write
        StepConfig writeConfig = StepConfig.builder()
            .stepName("ob-config-write")
            .dataPreparer(createOBConfigWriteDataPreparer(tenantConfig))
            .step(new ConfigWriteStep(redisTemplate))
            .resultValidator(createOBConfigWriteResultValidator())
            .build();
        
        return new ConfigurableServiceStage("ob-service", Arrays.asList(pollingConfig, writeConfig));
    }
    
    /**
     * OB Polling 数据准备器（注入函数）
     */
    private DataPreparer createOBPollingDataPreparer() {
        return (runtimeContext, stepContext) -> {
            stepContext.putData("pollInterval", 5000);
            stepContext.putData("pollMaxAttempts", 20);
            
            // ← 关键：注入轮询条件函数
            stepContext.putData("pollCondition", (PollingStep.PollCondition) (ctx) -> {
                String tenantId = ctx.getTenantId();
                return agentService.judgeAgent(tenantId);
            });
        };
    }
    
    /**
     * OB Polling 结果验证器
     */
    private ResultValidator createOBPollingResultValidator() {
        return (stepContext) -> {
            Boolean isReady = stepContext.getData("pollingResult", Boolean.class);
            if (isReady != null && isReady) {
                return ValidationResult.success("轮询成功，Agent 已就绪");
            } else {
                return ValidationResult.failure("轮询失败，Agent 未就绪");
            }
        };
    }
    
    /**
     * OB ConfigWrite 数据准备器
     */
    private DataPreparer createOBConfigWriteDataPreparer(TenantConfig tenantConfig) {
        return (runtimeContext, stepContext) -> {
            String tenantId = runtimeContext.getTenantId().getValue();
            ObConfig obConfig = tenantConfig.getObConfig();
            
            stepContext.putData("key", stageConfig.getRedisHashKeyPrefix() + tenantId);
            stepContext.putData("field", "ob-campaign");
            stepContext.putData("value", JSON.toJSONString(obConfig));
        };
    }
    
    /**
     * OB ConfigWrite 结果验证器
     */
    private ResultValidator createOBConfigWriteResultValidator() {
        return (stepContext) -> {
            ConfigWriteResult writeResult = stepContext.getData("writeResult", ConfigWriteResult.class);
            if (writeResult != null && writeResult.isSuccess()) {
                return ValidationResult.success("配置写入成功");
            } else {
                return ValidationResult.failure("配置写入失败");
            }
        };
    }
    
    // ========================================
    // Portal Stage
    // ========================================
    
    private TaskStage createPortalStage(TenantConfig tenantConfig) {
        StepConfig stepConfig = StepConfig.builder()
            .stepName("portal-http-request")
            .dataPreparer(createPortalDataPreparer(tenantConfig))
            .step(new HttpRequestStep(restTemplate))
            .resultValidator(createPortalResultValidator())
            .build();
        
        return new ConfigurableServiceStage("portal", Arrays.asList(stepConfig));
    }
    
    /**
     * Portal 数据准备器
     */
    private DataPreparer createPortalDataPreparer(TenantConfig tenantConfig) {
        return (runtimeContext, stepContext) -> {
            String endpoint = resolveEndpoint("portalService", "portal");
            
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
        };
    }
    
    /**
     * Portal 结果验证器
     */
    private ResultValidator createPortalResultValidator() {
        return (stepContext) -> {
            HttpResponseData response = stepContext.getData("httpResponse", HttpResponseData.class);
            
            if (response.is2xx()) {
                return ValidationResult.success(
                    String.format("Portal 接收成功 (HTTP %d)", response.getStatusCode())
                );
            } else {
                return ValidationResult.failure(
                    String.format("Portal 接收失败 (HTTP %d)", response.getStatusCode())
                );
            }
        };
    }
    
    // ========================================
    // 辅助方法
    // ========================================
    
    /**
     * 解析 endpoint (Nacos → Fallback)
     */
    private String resolveEndpoint(String nacosServiceName, String fallbackKey) {
        // 优先从 Nacos 获取
        try {
            List<String> instances = nacosClient.getInstances(
                stageConfig.getNacosServiceName(nacosServiceName)
            );
            if (instances != null && !instances.isEmpty()) {
                return "https://" + instances.get(0);
            }
        } catch (Exception e) {
            // Nacos 失败，降级到配置文件
        }
        
        // 降级到配置文件
        List<String> fallbackInstances = stageConfig.getFallbackInstances(fallbackKey);
        if (fallbackInstances != null && !fallbackInstances.isEmpty()) {
            return "https://" + fallbackInstances.get(0);
        }
        
        throw new IllegalStateException("无法解析服务实例: " + nacosServiceName);
    }
    
    /**
     * 生成 access token
     */
    private String generateAccessToken(String serviceKey) {
        AuthConfig authConfig = stageConfig.getAuth(serviceKey);
        
        if (!authConfig.isEnabled()) {
            return null;  // ← 不填 Authorization header
        }
        
        if ("random".equals(authConfig.getTokenProvider())) {
            return RandomStringUtils.randomAlphanumeric(32);
        }
        
        // oauth2 和 custom 未实现
        return null;
    }
    
    /**
     * 构建 ASBC 失败信息
     */
    private String buildASBCFailureMessage(ASBCResponseData data) {
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
```

---

## 🎯 核心特性

### 1. 函数注入（PollingStep）

```java
// 注入轮询条件函数
stepContext.putData("pollCondition", (PollingStep.PollCondition) (ctx) -> {
    return agentService.judgeAgent(ctx.getTenantId());
});

// PollingStep 只负责调用
boolean isReady = condition.check(context);
```

**优势**：
- ✅ Step 不包含业务逻辑
- ✅ 定制化逻辑通过函数注入
- ✅ 类型安全（函数式接口）

### 2. 代码编排（DynamicStageFactory）

```java
public List<TaskStage> buildStages(TenantConfig config) {
    List<TaskStage> stages = new ArrayList<>();
    
    // 按顺序添加 Stage
    if (config.getMediaRoutingConfig() != null) {
        stages.add(createASBCStage(config));  // Stage 1
    }
    
    if (config.getObConfig() != null) {
        stages.add(createOBServiceStage(config));  // Stage 2
    }
    
    if (config.getNetworkEndpoints() != null) {
        stages.add(createPortalStage(config));  // Stage 3
    }
    
    return stages;  // 严格按代码顺序
}
```

**优势**：
- ✅ 顺序清晰（代码即文档）
- ✅ IDE 支持（重构、跳转）
- ✅ 类型安全（编译期检查）
- ✅ 调试方便（断点、日志）

### 3. 只有 3-4 种 Step

```
HttpRequestStep - HTTP 请求
ConfigWriteStep - Redis HSET
MessageBroadcastStep - Redis Pub/Sub
PollingStep - 轮询（支持函数注入）
```

---

## 📊 对比：YAML vs 代码编排

| 维度 | YAML 配置 | 代码编排 |
|------|----------|---------|
| **灵活性** | 低 | ✅ 高 |
| **类型安全** | ❌ 无 | ✅ 有 |
| **IDE 支持** | ❌ 差 | ✅ 好 |
| **调试难度** | 高 | ✅ 低 |
| **热更新** | ✅ 支持 | ❌ 需发布 |
| **复杂逻辑** | ❌ 难表达 | ✅ 易表达 |
| **适用场景** | 简单配置 | ✅ 复杂编排 |

**结论**：对于切换动作编排，**代码编排更合适**

---

## ✅ 最终方案总结

### 核心设计

```
1. 只有 3-4 种通用 Step
2. 轮询使用函数注入
3. 使用代码编排（DynamicStageFactory）
4. DataPreparer + Step + ResultValidator 三层分离
```

### 关键优势

| 优势 | 说明 |
|------|------|
| **Step 极简** | 只有 3-4 个 Step，100% 通用 |
| **函数注入** | 定制化逻辑通过函数注入，Step 保持纯粹 |
| **代码编排** | 直观、灵活、类型安全 |
| **易于调试** | 代码即文档，断点调试 |
| **易于扩展** | 新增服务只需添加 createXXXStage 方法 |

---

## 🚀 实施优先级

### P0 - 基础框架
- [ ] DataPreparer 接口
- [ ] ResultValidator 接口
- [ ] ValidationResult 类
- [ ] ConfigurableServiceStage
- [ ] StepContext 增强

### P0 - 通用 Step
- [ ] HttpRequestStep + 数据模型
- [ ] ConfigWriteStep + 数据模型
- [ ] PollingStep（支持函数注入）
- [ ] MessageBroadcastStep + 数据模型

### P0 - DynamicStageFactory
- [ ] createASBCStage()
- [ ] createPortalStage()
- [ ] createOBServiceStage()
- [ ] 辅助方法（resolveEndpoint, generateAccessToken）

### P0 - ASBC 实现
- [ ] ASBCResponse 模型类
- [ ] ASBC 数据准备器
- [ ] ASBC 结果验证器

### P1 - Portal 实现
- [ ] Portal 数据准备器
- [ ] Portal 结果验证器

### P1 - OBService 实现
- [ ] OB Polling 数据准备器（函数注入）
- [ ] OB ConfigWrite 数据准备器
- [ ] OB 结果验证器

---

**此方案结合了函数注入和代码编排的优势，是最实用的实施方案！** ✅

