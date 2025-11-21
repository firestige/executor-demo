# RF-19 统一 Stage/Step 架构设计 - 修正版

**最后更新**: 2025-11-21  
**状态**: 待确认

---

## 📋 架构调整说明

### 修正 1: 配置文件层级结构

**❌ 错误设计（层级冗余）**:
```yaml
services:
  asbc-gateway:  # ← 这一层是冗余的
    stages:
      - name: asbc-deploy-stage
        steps: [...]
```

**✅ 正确设计（两层结构）**:
```yaml
stages:
  - name: asbc-gateway  # ← Stage 直接作为顶层
    steps:
      - type: asbc-config-request
        config: {...}
```

**理由**:
- asbc-gateway 本身就是一个 Stage
- Stage 里不应该还有 stages 属性
- 简化为 stages → steps 两层结构

---

### 修正 2: Portal 不需要 MessageBroadcast

**❌ 错误理解**: Portal 可能需要 MessageBroadcastStep

**✅ 正确理解**: Portal 只需要一个 HttpRequestStep

**理由**:
- Portal 只需要发送 HTTP 通知
- 不需要 Redis Pub/Sub 广播
- MessageBroadcastStep 只用于蓝绿网关

---

## 🏗️ 正确的配置文件结构

### deploy-stages.yml（完整版）

```yaml
# ========================================
# Infrastructure Configuration
# ========================================
infrastructure:
  # Nacos 服务发现配置
  nacos:
    server-addr: "127.0.0.1:8848"
    namespace: "production"
    services:
      asbcService: "asbc-gateway-service"
      portalService: "portal-service"
      obService: "ob-service"
      blueGreenGatewayService: "blue-green-gateway-service"
  
  # 降级配置（Nacos 不可用时使用）
  fallbackInstances:
    asbc:
      - "192.168.1.100:8080"
      - "192.168.1.101:8080"
    portal:
      - "192.168.1.20:8080"
    ob-service:
      - "192.168.1.30:8080"
    blueGreenGateway:
      - "192.168.1.10:8080"
      - "192.168.1.11:8080"
  
  # Redis 配置
  redis:
    hashKeyPrefix: "deploy:config:"
    pubsubTopic: "deploy.config.notify"
  
  # 鉴权配置（扩展点）
  auth:
    asbc:
      enabled: false  # 关闭时：不填 Authorization header
      tokenProvider: "random"  # 打开时：random（随机 hex）/ oauth2（未实现）/ custom（未实现）
    portal:
      enabled: false
    ob-service:
      enabled: false
  
  # 说明：
  # - enabled=false: 不填 Authorization header
  # - enabled=true + tokenProvider=random: 生成随机 hex token
  # - enabled=true + tokenProvider=oauth2: 未实现（预留）
  # - enabled=true + tokenProvider=custom: 未实现（预留）

# ========================================
# Stages Configuration
# ========================================
# 说明：
# 1. Stage 执行顺序：严格按 YAML 列表顺序（从上到下）
# 2. Step 执行顺序：严格按 YAML 列表顺序（从上到下）
# 3. order 字段：可选，用于显式标记顺序（不影响实际顺序，仅供文档说明）
stages:
  # ----------------------------------------
  # ASBC Gateway Stage (order=1)
  # ----------------------------------------
  - name: asbc-gateway
    order: 1  # 可选字段，显式标记顺序
    description: "ASBC 网关配置下发"
    steps:
      - type: asbc-config-request
        order: 1  # 可选字段，显式标记顺序
        config:
          nacos-service-name: "asbcService"
          fallback-key: "asbc"
          endpoint-path: "/api/sbc/traffic-switch"
          http-method: "POST"
          validation-type: "response-body"
          auth-key: "asbc"
        retry-policy:
          max-attempts: 1
          interval-seconds: 0
  
  # ----------------------------------------
  # OB Service Stage (order=2)
  # ----------------------------------------
  - name: ob-service
    order: 2
    description: "OB 服务配置下发（轮询 + Redis 写入）"
    steps:
      # Step 1: 轮询 AgentService
      - type: polling
        order: 1
        config:
          poll-interval-ms: 5000
          poll-max-attempts: 20
          service-key: "ob-service"
        retry-policy:
          max-attempts: 1
          interval-seconds: 0
      
      # Step 2: Redis 写入
      - type: key-value-write
        order: 2
        config:
          hash-key-prefix-ref: "redis.hashKeyPrefix"
          hash-field: "ob-campaign"
          value-type: "json"
        retry-policy:
          max-attempts: 3
          interval-seconds: 1
  
  # ----------------------------------------
  # Portal Stage
  # ----------------------------------------
  - name: portal
    description: "Portal 通知"
    steps:
      - type: http-request
        config:
          nacos-service-name: "portalService"
          fallback-key: "portal"
          endpoint-path: "/api/notify"
          http-method: "POST"
          validation-type: "http-status"
          expected-status: 200
          auth-key: "portal"
        retry-policy:
          max-attempts: 3
          interval-seconds: 1
  
  # ----------------------------------------
  # Blue-Green Gateway Stage（参考）
  # ----------------------------------------
  - name: blue-green-gateway
    description: "蓝绿网关配置下发"
    steps:
      # Step 1: Redis 写入
      - type: key-value-write
        config:
          hash-key-prefix-ref: "redis.hashKeyPrefix"
          hash-field: "gateway.host"
          value-type: "plain"
      
      # Step 2: Redis Pub/Sub 广播
      - type: message-broadcast
        config:
          pubsub-topic-ref: "redis.pubsubTopic"
          message-type: "config-change"
      
      # Step 3: 健康检查
      - type: endpoint-polling
        config:
          poll-interval-seconds: 3
          poll-max-attempts: 10
          health-check-path: "/health"
          version-key: "version"
```

---

## 🔄 DynamicStageFactory 工作流程

```
1. 读取 deploy-stages.yml
   ↓
2. 根据 TenantConfig 匹配 Stage
   例如：TenantConfig.mediaRoutingConfig 存在 → asbc-gateway Stage
   ↓
3. 创建 StepContextPreparer
   例如：ASBCStepContextPreparer(tenantConfig, nacosClient, stageConfig)
   ↓
4. 创建 Steps
   遍历 stage.steps，根据 type 创建对应的 Step 实例
   ↓
5. 创建 ConfigurableServiceStage
   new ConfigurableServiceStage(name, steps, contextPreparer)
   ↓
6. 返回 Stage 列表
```

---

## 📊 三个服务的完整配置

### 1️⃣ ASBC Gateway

```yaml
stages:
  - name: asbc-gateway
    steps:
      - type: asbc-config-request
        config:
          nacos-service-name: "asbcService"  # → infrastructure.nacos.services.asbcService
          fallback-key: "asbc"  # → infrastructure.fallbackInstances.asbc
          endpoint-path: "/api/sbc/traffic-switch"
          http-method: "POST"
          validation-type: "response-body"
          auth-key: "asbc"  # → infrastructure.auth.asbc
```

**数据流**:
```
TenantConfig.mediaRoutingConfig
  ↓
ASBCStepContextPreparer
  ├─ calledNumberRules.split(",") → List<String>
  ├─ resolveEndpoint("asbcService") → "https://192.168.1.100:8080"
  ├─ generateToken("asbc") → "random-hex" (auth.asbc.enabled=false)
  └─ StepContext
      ├─ calledNumberMatch: ["96765", "96755"]
      ├─ targetTrunkGroupName: "ka-gw"
      ├─ endpoint: "https://192.168.1.100:8080/api/sbc/traffic-switch"
      └─ accessToken: "a1b2c3d4..."
  ↓
ASBCConfigRequestStep
  ├─ POST {calledNumberMatch, targetTrunkGroupName}
  ├─ Header: Authorization: Bearer {token}
  ├─ 解析响应: {code, msg, data: {successList, failList}}
  └─ 判断: failList 不为空 → 失败（列出详情）
```

---

### 2️⃣ OB Service

```yaml
stages:
  - name: ob-service
    steps:
      - type: polling
        config:
          poll-interval-ms: 5000
          poll-max-attempts: 20
      
      - type: key-value-write
        config:
          hash-key-prefix-ref: "redis.hashKeyPrefix"
          hash-field: "ob-campaign"
          value-type: "json"
```

**数据流**:
```
TenantConfig.obConfig
  ↓
OBStepContextPreparer
  └─ StepContext
      ├─ tenantId: "tenant-001"
      ├─ pollInterval: 5000
      ├─ pollMaxAttempts: 20
      ├─ obConfig: ObConfig 对象
      └─ hashKeyPrefix: "deploy:config:"
  ↓
Step 1: PollingStep
  ├─ 循环调用 AgentService.judgeAgent(tenantId)
  ├─ false → sleep(5000) → 重试
  ├─ true → 成功，进入 Step 2
  └─ 超过 20 次 → 失败
  ↓
Step 2: KeyValueWriteStep
  ├─ key = "deploy:config:tenant-001"
  ├─ field = "ob-campaign"
  ├─ value = JSON.stringify(obConfig)
  └─ HSET key field value
```

---

### 3️⃣ Portal

```yaml
stages:
  - name: portal
    steps:
      - type: http-request
        config:
          nacos-service-name: "portalService"
          fallback-key: "portal"
          endpoint-path: "/api/notify"
          http-method: "POST"
          validation-type: "http-status"
          expected-status: 200
```

**数据流**:
```
TenantConfig (全部内容)
  ↓
PortalStepContextPreparer
  └─ StepContext
      ├─ endpoint: "http://192.168.1.20:8080/api/notify"
      ├─ method: "POST"
      └─ payload: {tenantId, deployUnitId, version, ...}
  ↓
HttpRequestStep
  ├─ POST payload to endpoint
  ├─ 检查响应码
  └─ 2xx → 成功，其他 → 失败
```

---

## ✅ Step 复用情况

| Step 类型 | 使用 Stage | 说明 |
|----------|-----------|------|
| **ASBCConfigRequestStep** | asbc-gateway | ASBC 专用（解析 successList/failList）|
| **HttpRequestStep** | portal | 通用 HTTP（可扩展到其他 HTTP 通知场景）|
| **PollingStep** | ob-service | 轮询专用（可扩展到其他轮询场景）|
| **KeyValueWriteStep** | ob-service, blue-green-gateway | ✅ **复用**（Redis HSET）|
| **MessageBroadcastStep** | blue-green-gateway | 蓝绿网关专用（Redis Pub/Sub）|
| **EndpointPollingStep** | blue-green-gateway | 健康检查专用 |

**说明**:
- ✅ Portal **不使用** MessageBroadcastStep
- ✅ 只有蓝绿网关需要 Pub/Sub 广播
- ✅ KeyValueWriteStep 是唯一被多个 Stage 复用的 Step

---

## 🎯 关键设计点总结

### 1. 配置文件结构

```
infrastructure: {...}  # 基础设施配置
stages:  # Stage 列表（顶层）
  - name: stage-name-1
    steps: [...]
  - name: stage-name-2
    steps: [...]
```

**优势**:
- ✅ 层级清晰（两层：stages → steps）
- ✅ 无冗余层级
- ✅ Stage 名称直接可用

### 2. Portal 的 Steps

```yaml
- name: portal
  steps:
    - type: http-request  # ← 只需要一个 Step
```

**不需要**:
- ❌ message-broadcast（不需要 Redis Pub/Sub）
- ❌ key-value-write（不需要写 Redis）
- ❌ endpoint-polling（不需要健康检查）

### 3. 配置引用机制

```yaml
config:
  hash-key-prefix-ref: "redis.hashKeyPrefix"  # 引用 infrastructure 配置
  pubsub-topic-ref: "redis.pubsubTopic"
  nacos-service-name: "asbcService"  # 在 nacos.services 中查找
  fallback-key: "asbc"  # 在 fallbackInstances 中查找
  auth-key: "asbc"  # 在 auth 中查找
```

---

## 📋 待确认问题

1. ✅ **配置层级**: 是否确认使用 `stages` 作为顶层，不需要 `services` 层？
2. ✅ **Portal Steps**: 是否确认 Portal 只需要 `http-request` 一个 Step？
3. ✅ **配置引用**: 是否同意使用 `-ref` 后缀引用 infrastructure 配置？
4. ✅ **ASBC 实现**: 是否确认 ASBCConfigRequestStep 的实现方式？
5. ✅ **OBService 实现**: 是否确认两步 Step 的组合方式？

---

**请确认修正后的设计方案！** 🚀

