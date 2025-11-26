# T-030: Nacos 服务发现命名空间问题修复方案

**任务ID**: T-030  
**优先级**: P1  
**创建日期**: 2025-11-27  
**状态**: 待评审

---

## 1. 问题分析

### 1.1 核心问题

当前 `NacosServiceDiscovery` 存在三个严重问题：

#### 问题 1：构造函数未指定 namespace
```java
public NacosServiceDiscovery(String serverAddr) throws NacosException {
    Properties properties = new Properties();
    properties.put("serverAddr", serverAddr);
    // ❌ 缺少 properties.put("namespace", xxx);
    this.namingService = NamingFactory.createNamingService(properties);
}
```
**后果**：NamingService 被固定绑定到 Nacos 的 `public` 默认命名空间，无法访问蓝环境/绿环境的独立命名空间。

#### 问题 2：getHealthyInstances 的 namespace 参数被误用
```java
// 当前代码（错误）
instances = namingService.selectInstances(serviceName, namespace, true);
```

查阅 Nacos Client API 文档：
```java
// Nacos API 签名
List<Instance> selectInstances(String serviceName, String groupName, boolean healthy);
List<Instance> selectInstances(String serviceName, String groupName, List<String> clusters, boolean healthy);
```

**真相**：
- 当前代码的第二个参数 `namespace` 实际上被当作 **groupName** 传入！
- Nacos 的 namespace 在**客户端初始化时绑定**，不能在查询时动态切换
- 正确的查询方式应该是：
  - 不同 namespace 需要**不同的 NamingService 实例**
  - 或者使用支持 namespace 参数的重载方法（但 Nacos 1.x/2.x API 不同）

#### 问题 3：资源泄漏风险
- 当前设计是单例 `NacosServiceDiscovery`，只有一个 `NamingService`
- 如果未来支持多 namespace，可能创建多个 `NamingService` 但缺少统一的生命周期管理
- `shutdown()` 方法只关闭单个实例，无法管理多个客户端

---

## 2. 关于命名冲突的澄清

### 2.1 Nacos 官方是否有重名类？

**结论：没有冲突，但需要说明**

1. **Nacos Client SDK（你项目当前使用的）**：
   - 依赖：`com.alibaba.nacos:nacos-client`
   - 核心接口：`com.alibaba.nacos.api.naming.NamingService`
   - ❌ **没有** `NacosServiceDiscovery` 类

2. **Spring Cloud Alibaba（如果你引入了）**：
   - 依赖：`spring-cloud-starter-alibaba-nacos-discovery`
   - 有一个：`com.alibaba.cloud.nacos.discovery.NacosServiceDiscovery`
   - ⚠️ 但它的包名是 `com.alibaba.cloud.nacos.discovery`，与你的 `xyz.firestige.deploy.infrastructure.discovery` **不冲突**

### 2.2 Spring Cloud 版本为什么不满足需求？

Spring Cloud Alibaba 的 `NacosServiceDiscovery` 存在限制：

| 特性 | Spring Cloud 版本 | 你的需求 | 结论 |
|------|------------------|---------|------|
| 支持 namespace | ✅ 支持一个 | ❌ 需要动态切换多个 | **不满足** |
| 绑定方式 | 启动时绑定（配置文件） | 运行时动态（根据租户配置） | **不满足** |
| 同时访问多个 namespace | ❌ 不支持 | ✅ 必须（蓝/绿环境） | **不满足** |
| 集成方式 | 绑定 Spring Cloud `DiscoveryClient` | 直接调用 NamingService API | **架构不同** |

**示例对比**：

```yaml
# Spring Cloud 方式（只能配置一个 namespace）
spring:
  cloud:
    nacos:
      discovery:
        server-addr: 192.168.1.100:8848
        namespace: blue-env  # ⚠️ 只能配置一个，整个应用绑定
```

```java
// 你的场景（需要运行时动态切换）
// 租户 A 使用蓝环境
List<String> instancesA = discovery.getHealthyInstances("gateway", "blue-env");

// 租户 B 使用绿环境
List<String> instancesB = discovery.getHealthyInstances("gateway", "green-env");
```

**结论**：必须自定义 `NacosServiceDiscovery` 类来满足多租户动态切换 namespace 的需求。

---

## 3. 业务场景需求

### 2.1 当前架构
```
Tenant Config (from Redis)
├── Tenant A → namespace="blue-env"  → Blue Gateway Service
├── Tenant B → namespace="green-env" → Green Gateway Service
└── Tenant C → namespace="blue-env"  → Blue Gateway Service
```

### 2.2 实际需求
- **至少支持 2 个命名空间**：蓝环境 (`blue-env`) 和绿环境 (`green-env`)
- **运行时动态切换**：不同租户可能使用不同的 namespace
- **配置驱动**：namespace 应该从配置文件或 TenantConfig 获取，而不是硬编码
- **资源安全**：应用关闭时所有 NamingService 实例都应该被正确关闭

---

## 3. 技术方案设计

### 3.1 方案概述

**核心思路**：将 `NacosServiceDiscovery` 从"单客户端"改造为"带缓存驱逐的多客户端管理器"

```
NacosServiceDiscovery (Manager with LRU Cache)
├── Map<String, ClientEntry> clientsByNamespace (with TTL)
│   ├── "blue-env"  → ClientEntry { NamingService, lastAccessTime, refCount }
│   ├── "green-env" → ClientEntry { NamingService, lastAccessTime, refCount }
│   └── "public"    → ClientEntry { NamingService, lastAccessTime, refCount }
├── ScheduledExecutor: 定期检查并关闭空闲客户端（默认 5 分钟未使用则驱逐）
└── Lifecycle: shutdown() 关闭所有客户端 + 停止调度器
```

**关键特性**：
- **惰性加载**：namespace 首次访问时才创建 NamingService（不预初始化）
- **LRU + TTL 驱逐**：最后访问时间超过阈值（默认 5 分钟）且无引用时自动关闭
- **引用计数**：防止在使用中的客户端被驱逐（查询时 +1，完成后 -1）
- **线程安全**：并发访问和驱逐的同步控制
- **语义化接口**：通过方法重载区分默认分组和自定义分组

### 3.2 详细设计

#### 3.2.1 改造后的 NacosServiceDiscovery

```java
public class NacosServiceDiscovery {
    
    private static final Logger log = LoggerFactory.getLogger(NacosServiceDiscovery.class);
    private static final String DEFAULT_GROUP = "DEFAULT_GROUP";
    
    private final String serverAddr;
    private final String username;          // 可选
    private final String password;          // 可选（推荐通过环境变量传入）
    private final String defaultNamespace;  // 默认命名空间
    private final Map<String, ClientEntry> clientsByNamespace = new ConcurrentHashMap<>();
    private final ScheduledExecutorService evictionScheduler;
    private final long clientIdleTimeoutMillis;  // 客户端空闲超时时间（默认 5 分钟）
    private volatile boolean available = true;
    
    /**
     * 客户端条目（包含 NamingService + 元数据）
     */
    private static class ClientEntry {
        private final NamingService client;
        private volatile long lastAccessTime;
        private final AtomicInteger refCount = new AtomicInteger(0);
        
        ClientEntry(NamingService client) {
            this.client = client;
            this.lastAccessTime = System.currentTimeMillis();
        }
        
        void updateAccessTime() {
            this.lastAccessTime = System.currentTimeMillis();
        }
        
        boolean isIdle(long timeoutMillis) {
            return refCount.get() == 0 && 
                   (System.currentTimeMillis() - lastAccessTime) > timeoutMillis;
        }
        
        void incrementRef() {
            refCount.incrementAndGet();
        }
        
        void decrementRef() {
            refCount.decrementAndGet();
        }
    }
    
    /**
     * 私有构造函数（强制使用 Builder）
     */
    private NacosServiceDiscovery(Builder builder) {
        this.serverAddr = builder.serverAddr;
        this.username = builder.username;
        this.password = builder.password;
        this.defaultNamespace = builder.defaultNamespace;
        this.clientIdleTimeoutMillis = builder.clientIdleTimeoutMillis;
        
        this.evictionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nacos-client-evictor");
            t.setDaemon(true);
            return t;
        });
        
        // 启动驱逐调度器
        this.evictionScheduler.scheduleWithFixedDelay(
            this::evictIdleClients, 
            builder.evictionIntervalMillis, 
            builder.evictionIntervalMillis, 
            TimeUnit.MILLISECONDS
        );
        
        log.info("Nacos 服务发现管理器初始化: serverAddr={}, defaultNamespace={}, username={}, idleTimeout={}ms, evictionInterval={}ms", 
            serverAddr, this.defaultNamespace, username != null ? "***" : "null", 
            clientIdleTimeoutMillis, builder.evictionIntervalMillis);
    }
    
    /**
     * 创建 Builder 实例
     * @param serverAddr Nacos 服务器地址（必填）
     */
    public static Builder builder(String serverAddr) {
        return new Builder(serverAddr);
    }
    
    /**
     * Builder 模式构造器
     */
    public static class Builder {
        // 必填参数
        private final String serverAddr;
        
        // 可选参数（带默认值）
        private String username;
        private String password;
        private String defaultNamespace = "public";
        private long clientIdleTimeoutMillis = TimeUnit.MINUTES.toMillis(5);   // 默认 5 分钟
        private long evictionIntervalMillis = TimeUnit.MINUTES.toMillis(1);    // 默认 1 分钟
        
        private Builder(String serverAddr) {
            if (serverAddr == null || serverAddr.isEmpty()) {
                throw new IllegalArgumentException("serverAddr cannot be null or empty");
            }
            this.serverAddr = serverAddr;
        }
        
        /**
         * 设置用户名（可选）
         */
        public Builder username(String username) {
            this.username = username;
            return this;
        }
        
        /**
         * 设置密码（可选，推荐通过环境变量传入）
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }
        
        /**
         * 设置默认命名空间（可选，默认 "public"）
         */
        public Builder defaultNamespace(String defaultNamespace) {
            this.defaultNamespace = defaultNamespace;
            return this;
        }
        
        /**
         * 设置客户端空闲超时时间（毫秒）
         */
        public Builder clientIdleTimeoutMillis(long clientIdleTimeoutMillis) {
            if (clientIdleTimeoutMillis <= 0) {
                throw new IllegalArgumentException("clientIdleTimeoutMillis must be positive");
            }
            this.clientIdleTimeoutMillis = clientIdleTimeoutMillis;
            return this;
        }
        
        /**
         * 设置客户端空闲超时时间（分钟）
         */
        public Builder clientIdleTimeoutMinutes(long minutes) {
            return clientIdleTimeoutMillis(TimeUnit.MINUTES.toMillis(minutes));
        }
        
        /**
         * 设置驱逐检查间隔（毫秒）
         */
        public Builder evictionIntervalMillis(long evictionIntervalMillis) {
            if (evictionIntervalMillis <= 0) {
                throw new IllegalArgumentException("evictionIntervalMillis must be positive");
            }
            this.evictionIntervalMillis = evictionIntervalMillis;
            return this;
        }
        
        /**
         * 设置驱逐检查间隔（分钟）
         */
        public Builder evictionIntervalMinutes(long minutes) {
            return evictionIntervalMillis(TimeUnit.MINUTES.toMillis(minutes));
        }
        
        /**
         * 构建 NacosServiceDiscovery 实例
         */
        public NacosServiceDiscovery build() {
            return new NacosServiceDiscovery(this);
        }
    }
    
    /**
     * 延迟初始化指定命名空间的客户端（线程安全，带引用计数）
     * @param namespace 命名空间（null 或 "" 表示默认命名空间）
     * @return ClientEntry 实例
     * @throws NacosException 初始化失败
     */
    private ClientEntry getOrCreateClient(String namespace) throws NacosException {
        String ns = normalizeNamespace(namespace);
        
        // Double-Check Locking
        ClientEntry entry = clientsByNamespace.get(ns);
        if (entry != null) {
            entry.updateAccessTime();
            entry.incrementRef();
            return entry;
        }
        
        synchronized (this) {
            entry = clientsByNamespace.get(ns);
            if (entry != null) {
                entry.updateAccessTime();
                entry.incrementRef();
                return entry;
            }
            
            return initNamespaceClient(ns);
        }
    }
    
    /**
     * 初始化命名空间客户端（必须在同步块内调用）
     */
    private ClientEntry initNamespaceClient(String namespace) throws NacosException {
        Properties properties = new Properties();
        properties.put("serverAddr", serverAddr);
        properties.put("namespace", namespace);  // ✅ 关键修复
        
        // 可选：认证配置
        if (username != null && !username.isEmpty()) {
            properties.put("username", username);
        }
        if (password != null && !password.isEmpty()) {
            properties.put("password", password);
        }
        
        try {
            NamingService client = NamingFactory.createNamingService(properties);
            ClientEntry entry = new ClientEntry(client);
            entry.incrementRef();  // 初始引用计数 +1
            clientsByNamespace.put(namespace, entry);
            log.info("Nacos 客户端初始化成功: namespace={}, serverAddr={}", namespace, serverAddr);
            return entry;
        } catch (NacosException e) {
            log.error("Nacos 客户端初始化失败: namespace={}, serverAddr={}", namespace, serverAddr, e);
            available = false;
            throw e;
        }
    }
    
    /**
     * 归一化命名空间（null/"" → 默认命名空间）
     */
    private String normalizeNamespace(String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            return defaultNamespace;
        }
        return namespace;
    }
    
    /**
     * 释放客户端引用（查询完成后调用）
     */
    private void releaseClient(ClientEntry entry) {
        if (entry != null) {
            entry.decrementRef();
        }
    }
    
    /**
     * 驱逐空闲客户端（定时任务）
     */
    private void evictIdleClients() {
        try {
            List<String> toEvict = new ArrayList<>();
            
            // 找出所有空闲客户端
            for (Map.Entry<String, ClientEntry> entry : clientsByNamespace.entrySet()) {
                if (entry.getValue().isIdle(clientIdleTimeoutMillis)) {
                    toEvict.add(entry.getKey());
                }
            }
            
            // 关闭并移除
            for (String namespace : toEvict) {
                ClientEntry entry = clientsByNamespace.remove(namespace);
                if (entry != null) {
                    try {
                        entry.client.shutDown();
                        log.info("驱逐空闲 Nacos 客户端: namespace={}, idleTime={}ms", 
                            namespace, System.currentTimeMillis() - entry.lastAccessTime);
                    } catch (NacosException e) {
                        log.warn("关闭空闲客户端异常: namespace={}", namespace, e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("驱逐空闲客户端任务异常", e);
        }
    }
    
    /**
     * 获取健康实例列表（使用默认分组 DEFAULT_GROUP）
     * <p>
     * 语义化接口：不传 groupName 参数时，默认使用 DEFAULT_GROUP
     * 
     * @param serviceName Nacos 服务名
     * @param namespace 命名空间（null 表示使用配置的默认命名空间）
     * @return 实例列表（host:port 格式），失败返回空列表
     */
    public List<String> getHealthyInstances(String serviceName, String namespace) {
        return getHealthyInstances(serviceName, namespace, DEFAULT_GROUP);
    }
    
    /**
     * 获取健康实例列表（支持自定义分组）
     * <p>
     * 语义化接口：显式传入 groupName，支持自定义分组
     * 
     * @param serviceName Nacos 服务名
     * @param namespace 命名空间
     * @param groupName Nacos 分组名（如 DEFAULT_GROUP, CUSTOM_GROUP）
     * @return 实例列表（host:port 格式），失败返回空列表
     */
    public List<String> getHealthyInstances(String serviceName, String namespace, String groupName) {
        if (!available) {
            log.warn("Nacos 不可用，跳过查询: service={}", serviceName);
            return Collections.emptyList();
        }
        
        ClientEntry entry = null;
        try {
            // ✅ 修复：使用对应 namespace 的客户端
            entry = getOrCreateClient(namespace);
            
            // ✅ 修复：selectInstances 使用 groupName 参数（不是 namespace）
            List<Instance> instances = entry.client.selectInstances(serviceName, groupName, true);
            
            if (instances == null || instances.isEmpty()) {
                log.warn("Nacos 未找到健康实例: service={}, namespace={}, group={}", 
                    serviceName, namespace, groupName);
                return Collections.emptyList();
            }
            
            List<String> endpoints = instances.stream()
                .map(inst -> inst.getIp() + ":" + inst.getPort())
                .collect(Collectors.toList());
            
            log.debug("Nacos 查询成功: service={}, namespace={}, group={}, instances={}", 
                serviceName, namespace, groupName, endpoints);
            return endpoints;
            
        } catch (NacosException e) {
            log.error("Nacos 查询失败: service={}, namespace={}, group={}, error={}", 
                serviceName, namespace, groupName, e.getMessage());
            return Collections.emptyList();
        } finally {
            // 释放引用计数
            releaseClient(entry);
        }
    }
    
    /**
     * 检查 Nacos 是否可用
     */
    public boolean isAvailable() {
        return available;
    }
    
    /**
     * 关闭所有 Nacos 客户端和驱逐调度器（✅ 修复资源泄漏）
     */
    public void shutdown() {
        log.info("开始关闭 Nacos 服务发现管理器，当前管理 {} 个命名空间", clientsByNamespace.size());
        
        // 1. 停止驱逐调度器
        if (evictionScheduler != null && !evictionScheduler.isShutdown()) {
            evictionScheduler.shutdown();
            try {
                if (!evictionScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    evictionScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                evictionScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("驱逐调度器已关闭");
        }
        
        // 2. 关闭所有 Nacos 客户端
        for (Map.Entry<String, ClientEntry> entry : clientsByNamespace.entrySet()) {
            String namespace = entry.getKey();
            ClientEntry clientEntry = entry.getValue();
            try {
                clientEntry.client.shutDown();
                log.info("Nacos 客户端已关闭: namespace={}", namespace);
            } catch (NacosException e) {
                log.warn("Nacos 客户端关闭异常: namespace={}", namespace, e);
            }
        }
        
        clientsByNamespace.clear();
        available = false;
        log.info("所有 Nacos 客户端已关闭");
    }
    
    /**
     * 获取当前管理的命名空间列表（用于监控/调试）
     */
    public Set<String> getManagedNamespaces() {
        return new HashSet<>(clientsByNamespace.keySet());
    }
    
    /**
     * 获取客户端统计信息（用于监控）
     */
    public Map<String, ClientStats> getClientStats() {
        Map<String, ClientStats> stats = new HashMap<>();
        for (Map.Entry<String, ClientEntry> entry : clientsByNamespace.entrySet()) {
            ClientEntry clientEntry = entry.getValue();
            stats.put(entry.getKey(), new ClientStats(
                clientEntry.refCount.get(),
                System.currentTimeMillis() - clientEntry.lastAccessTime
            ));
        }
        return stats;
    }
    
    /**
     * 客户端统计信息
     */
    public static class ClientStats {
        private final int refCount;
        private final long idleTimeMillis;
        
        public ClientStats(int refCount, long idleTimeMillis) {
            this.refCount = refCount;
            this.idleTimeMillis = idleTimeMillis;
        }
        
        public int getRefCount() { return refCount; }
        public long getIdleTimeMillis() { return idleTimeMillis; }
    }
}
```

---

## 4. 配置变更

### 4.1 InfrastructureProperties 扩展

在 `NacosProperties` 中增加认证和命名空间配置：

```java
public static class NacosProperties {
    private boolean enabled = false;
    private String serverAddr = "127.0.0.1:8848";
    
    /** ✅ 新增：默认命名空间（用于 null 参数时的回退） */
    private String defaultNamespace = "public";
    
    /** ✅ 新增：Nacos 用户名（可选，用于鉴权） */
    private String username;
    
    /** ✅ 新增：客户端空闲超时时间（分钟），超过此时间未使用则驱逐，默认 5 分钟 */
    private long clientIdleTimeoutMinutes = 5;
    
    /** ✅ 新增：驱逐检查间隔（分钟），默认 1 分钟 */
    private long evictionIntervalMinutes = 1;
    
    // 服务名映射（已存在）
    private Map<String, String> services = new HashMap<>() {{
        put("blueGreenGatewayService", "blue-green-gateway-service");
        put("portalService", "portal-service");
        put("asbcService", "asbc-gateway-service");
        put("obService", "ob-service");
    }};
    
    // getters/setters...
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getServerAddr() { return serverAddr; }
    public void setServerAddr(String serverAddr) { this.serverAddr = serverAddr; }
    public String getDefaultNamespace() { return defaultNamespace; }
    public void setDefaultNamespace(String defaultNamespace) { this.defaultNamespace = defaultNamespace; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public long getClientIdleTimeoutMinutes() { return clientIdleTimeoutMinutes; }
    public void setClientIdleTimeoutMinutes(long clientIdleTimeoutMinutes) { 
        this.clientIdleTimeoutMinutes = clientIdleTimeoutMinutes; 
    }
    public long getEvictionIntervalMinutes() { return evictionIntervalMinutes; }
    public void setEvictionIntervalMinutes(long evictionIntervalMinutes) { 
        this.evictionIntervalMinutes = evictionIntervalMinutes; 
    }
    public Map<String, String> getServices() { return services; }
    public void setServices(Map<String, String> services) { this.services = services; }
}
```

### 4.2 application.yml 配置示例

```yaml
executor:
  infrastructure:
    nacos:
      enabled: true
      server-addr: "192.168.1.100:8848"
      
      # ✅ 默认命名空间（当查询时传入 null 时使用）
      default-namespace: "public"
      
      # ✅ Nacos 鉴权用户名（可选）
      username: "nacos_user"
      # ⚠️ 密码不要直接写在配置文件中！使用以下任一方式：
      # 方式 1: 环境变量（推荐）
      # password: ${NACOS_PASSWORD}
      # 方式 2: Spring Cloud Config Server 加密
      # password: '{cipher}AQA...'
      # 方式 3: 外部密钥管理（如 Vault、K8s Secret）
      
      # ✅ 客户端缓存策略
      client-idle-timeout-minutes: 5    # 5 分钟未使用则驱逐
      eviction-interval-minutes: 1      # 每分钟检查一次
      
      # 服务名映射
      services:
        blueGreenGatewayService: "blue-green-gateway-service"
        portalService: "portal-service"
        asbcService: "asbc-gateway-service"
        obService: "ob-service"
```

### 4.3 密码安全传入方式（环境变量）

**推荐方式：通过环境变量传入密码**（现阶段采用此方案）

```yaml
# application.yml
executor:
  infrastructure:
    nacos:
      username: "nacos_user"
      password: ${NACOS_PASSWORD:}  # 从环境变量读取，未设置时为空字符串
```

```bash
# Linux/Mac 启动时设置
export NACOS_PASSWORD='your_secure_password'
java -jar app.jar

# Windows 启动时设置
set NACOS_PASSWORD=your_secure_password
java -jar app.jar

# Docker 运行时传入
docker run -e NACOS_PASSWORD='your_secure_password' your-image

# Kubernetes 部署（推荐使用 Secret）
# k8s-deployment.yaml
apiVersion: apps/v1
kind: Deployment
spec:
  template:
    spec:
      containers:
      - name: app
        env:
        - name: NACOS_PASSWORD
          valueFrom:
            secretKeyRef:
              name: nacos-credentials
              key: password
```

**注意事项**：
- ❌ **不要直接在 application.yml 中明文写入密码**
- ✅ **如果没有设置环境变量，password 为空字符串，Nacos 客户端会尝试无密码连接**（适用于开发环境无认证的 Nacos）
- ✅ **生产环境务必通过环境变量传入密码**

---

## 5. Spring Boot 集成变更

### 5.1 InfrastructureAutoConfiguration 修改

```java
@Bean(destroyMethod = "shutdown")  // ✅ 确保 Spring 容器关闭时调用 shutdown()
@ConditionalOnProperty(prefix = "executor.infrastructure.nacos", name = "enabled", havingValue = "true")
public NacosServiceDiscovery nacosServiceDiscovery(InfrastructureProperties props, Environment env) {
    NacosProperties nacos = props.getNacos();
    
    // ✅ 使用 Builder 模式创建实例（清晰易读）
    NacosServiceDiscovery.Builder builder = NacosServiceDiscovery.builder(nacos.getServerAddr())
        .defaultNamespace(nacos.getDefaultNamespace())
        .clientIdleTimeoutMinutes(nacos.getClientIdleTimeoutMinutes())
        .evictionIntervalMinutes(nacos.getEvictionIntervalMinutes());
    
    // 可选：设置用户名
    if (nacos.getUsername() != null && !nacos.getUsername().isEmpty()) {
        builder.username(nacos.getUsername());
    }
    
    // 可选：密码从环境变量读取
    String password = env.getProperty("executor.infrastructure.nacos.password");
    if (password != null && !password.isEmpty()) {
        builder.password(password);
    }
    
    log.info("[Infrastructure] Initializing NacosServiceDiscovery: serverAddr={}, defaultNamespace={}, username={}, " +
            "idleTimeout={}min, evictionInterval={}min", 
            nacos.getServerAddr(), nacos.getDefaultNamespace(), 
            nacos.getUsername() != null ? "***" : "null",
            nacos.getClientIdleTimeoutMinutes(), nacos.getEvictionIntervalMinutes());
    
    return builder.build();
}
```

**优点**：
- ✅ 链式调用，代码清晰易读
- ✅ 必填参数（serverAddr）通过 `builder(serverAddr)` 强制传入
- ✅ 可选参数按需设置，不设置则使用默认值
- ✅ 密码通过 `Environment.getProperty()` 获取，支持占位符 `${NACOS_PASSWORD:}` 的解析
- ✅ 如果环境变量未设置，不调用 `builder.password()`，客户端会尝试无密码连接（适用于开发环境）

---

## 6. 向后兼容性

### 6.1 兼容策略

| 场景 | 旧行为 | 新行为 | 兼容性 |
|------|--------|--------|--------|
| 构造函数 `new NacosServiceDiscovery(serverAddr)` | 单客户端，绑定默认 namespace | 延迟初始化模式，按需创建客户端 | ✅ 完全兼容 |
| `getHealthyInstances(serviceName, null)` | 查询默认 namespace（错误地传给 groupName） | 查询 "public" namespace，正确处理 | ✅ 兼容（修复了 bug） |
| `getHealthyInstances(serviceName, "blue-env")` | 错误地将 "blue-env" 当作 groupName | 正确查询 "blue-env" namespace | ⚠️ **行为变更**（但原行为是错的） |
| Spring Bean 生命周期 | 只关闭单个客户端 | 关闭所有客户端 | ✅ 兼容（增强） |

### 6.2 迁移指南

**对于现有部署**：
1. **低风险**：如果当前没有使用 Nacos（`nacos.enabled=false`），无影响
2. **中风险**：如果使用 Nacos 但只用默认 namespace，行为基本不变（只是修复了参数传递错误）
3. **需要测试**：如果之前尝试传入非默认 namespace，需要验证新逻辑是否符合预期

**建议**：
- Phase 1: 发布新版本，默认不开启多 namespace 支持（保持延迟初始化）
- Phase 2: 在测试环境验证多 namespace 功能
- Phase 3: 生产环境配置 `namespaces` 列表并启用

---

## 7. 测试计划

### 7.1 单元测试

```java
@Test
void testSingleNamespaceCompatibility() {
    // 测试兼容旧接口
    NacosServiceDiscovery discovery = new NacosServiceDiscovery("localhost:8848");
    List<String> instances = discovery.getHealthyInstances("test-service", null);
    // 验证查询默认 namespace
}

@Test
void testMultipleNamespaces() throws NacosException {
    NacosServiceDiscovery discovery = new NacosServiceDiscovery(
        "localhost:8848", 
        Arrays.asList("blue-env", "green-env")
    );
    
    List<String> blueInstances = discovery.getHealthyInstances("test-service", "blue-env");
    List<String> greenInstances = discovery.getHealthyInstances("test-service", "green-env");
    
    // 验证不同 namespace 返回不同实例
    assertNotEquals(blueInstances, greenInstances);
}

@Test
void testLazyInitialization() {
    NacosServiceDiscovery discovery = new NacosServiceDiscovery("localhost:8848");
    
    // 第一次调用时初始化 blue-env
    discovery.getHealthyInstances("test-service", "blue-env");
    assertTrue(discovery.getManagedNamespaces().contains("blue-env"));
    
    // 第二次调用时初始化 green-env
    discovery.getHealthyInstances("test-service", "green-env");
    assertTrue(discovery.getManagedNamespaces().contains("green-env"));
    
    assertEquals(2, discovery.getManagedNamespaces().size());
}

@Test
void testShutdownAllClients() throws NacosException {
    NacosServiceDiscovery discovery = new NacosServiceDiscovery(
        "localhost:8848", 
        Arrays.asList("ns1", "ns2", "ns3")
    );
    
    assertEquals(3, discovery.getManagedNamespaces().size());
    
    discovery.shutdown();
    
    assertEquals(0, discovery.getManagedNamespaces().size());
    assertFalse(discovery.isAvailable());
}

@Test
void testConcurrentNamespaceInit() throws InterruptedException {
    NacosServiceDiscovery discovery = new NacosServiceDiscovery("localhost:8848");
    
    // 并发访问不同 namespace
    ExecutorService executor = Executors.newFixedThreadPool(10);
    CountDownLatch latch = new CountDownLatch(100);
    
    for (int i = 0; i < 100; i++) {
        final String namespace = "ns-" + (i % 5);  // 5 个不同的 namespace
        executor.submit(() -> {
            try {
                discovery.getHealthyInstances("test-service", namespace);
            } finally {
                latch.countDown();
            }
        });
    }
    
    latch.await(10, TimeUnit.SECONDS);
    
    // 验证只创建了 5 个客户端（没有重复）
    assertEquals(5, discovery.getManagedNamespaces().size());
    
    discovery.shutdown();
}
```

### 7.2 集成测试

使用 Testcontainers 启动真实的 Nacos Server：

```java
@SpringBootTest
@Testcontainers
class NacosServiceDiscoveryIntegrationTest {
    
    @Container
    static NacosContainer nacos = new NacosContainer("nacos/nacos-server:2.2.3")
        .withExposedPorts(8848);
    
    @Test
    void testRealNacosQuery() throws NacosException {
        String serverAddr = nacos.getHost() + ":" + nacos.getMappedPort(8848);
        
        // 1. 注册服务到不同 namespace
        registerService(serverAddr, "blue-env", "test-service", "192.168.1.1:8080");
        registerService(serverAddr, "green-env", "test-service", "192.168.1.2:8080");
        
        // 2. 测试查询
        NacosServiceDiscovery discovery = new NacosServiceDiscovery(
            serverAddr, 
            Arrays.asList("blue-env", "green-env")
        );
        
        List<String> blueInstances = discovery.getHealthyInstances("test-service", "blue-env");
        List<String> greenInstances = discovery.getHealthyInstances("test-service", "green-env");
        
        assertEquals(1, blueInstances.size());
        assertEquals(1, greenInstances.size());
        assertTrue(blueInstances.get(0).contains("192.168.1.1"));
        assertTrue(greenInstances.get(0).contains("192.168.1.2"));
        
        discovery.shutdown();
    }
}
```

---

## 8. 风险与缓解

### 8.1 风险矩阵

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| 现有部署行为变更 | 高 | 低 | 保持向后兼容，延迟初始化为默认模式 |
| 多客户端资源消耗 | 中 | 中 | 限制预初始化的 namespace 数量；添加监控 |
| 并发初始化竞态条件 | 高 | 低 | Double-Check Locking + synchronized |
| Nacos 连接失败导致启动失败 | 高 | 中 | 捕获异常，降级到 fallbackInstances |
| 内存泄漏（客户端未关闭） | 高 | 低 | Spring Bean `destroyMethod`；单元测试验证 |

### 8.2 回滚计划

如果生产环境出现问题：
1. **立即回滚**：恢复旧版本代码
2. **临时缓解**：设置 `nacos.enabled=false`，使用 fallbackInstances
3. **问题定位**：收集日志，分析具体 namespace 查询失败原因

---

## 9. 实施计划

### Phase 1: 核心修复（3天）
- [ ] 修改 `NacosServiceDiscovery` 类（支持多客户端管理）
- [ ] 修改 `InfrastructureProperties`（增加 namespaces 配置）
- [ ] 修改 `InfrastructureAutoConfiguration`（支持预初始化）
- [ ] 编写单元测试（覆盖率 > 80%）

### Phase 2: 集成测试（2天）
- [ ] 编写 Nacos 集成测试（使用 Testcontainers）
- [ ] 在测试环境部署并验证蓝绿环境隔离
- [ ] 压力测试（并发查询、客户端泄漏检查）

### Phase 3: 文档与发布（1天）
- [ ] 更新 README.md（配置说明）
- [ ] 更新 CHANGELOG.md（Breaking Changes 说明）
- [ ] 编写迁移指南（docs/migration-guide.md）
- [ ] 发布新版本（建议版本号：1.1.0，包含 Breaking Changes）

### Phase 4: 生产验证（1周）
- [ ] 灰度发布到测试租户
- [ ] 监控 Nacos 客户端连接数、查询延迟
- [ ] 全量发布

---

## 10. 后续优化（可选）

### 10.1 监控增强
- 暴露 Prometheus Metrics：`nacos_clients_total`, `nacos_query_duration_seconds`
- 添加 Actuator Endpoint：`/actuator/nacos/namespaces`（查看当前管理的命名空间）

### 10.2 动态配置
- 支持运行时通过 API 添加/删除 namespace 客户端
- 支持 Nacos Config 动态刷新 namespace 列表

### 10.3 高可用
- 支持多个 Nacos Server 地址（集群模式）
- 客户端故障自动重连

---

## 11. 待讨论的问题

### Q1: 是否需要支持自定义 groupName？
**当前方案**：默认使用 `DEFAULT_GROUP`  
**备选方案**：在配置中增加 `nacos.defaultGroup` 或 per-service 的 group 配置

### Q2: 预初始化 vs 延迟初始化，哪个作为默认？
**方案 A**：默认延迟初始化（兼容性最好，但首次查询会慢）  
**方案 B**：默认预初始化（性能更好，但要求配置 namespaces）

**建议**：采用 **方案 A**，允许通过配置切换到方案 B

### Q3: 是否需要支持 Nacos 1.x 和 2.x 的 API 差异？
**当前假设**：使用 Nacos 2.x Client API  
**如果需要兼容 1.x**：需要抽象 `NacosClientAdapter` 接口

### Q4: 客户端初始化失败是否应该阻塞应用启动？
**当前行为**：抛出异常，阻塞启动  
**备选方案**：捕获异常，标记 `available=false`，降级到 fallbackInstances

**建议**：提供配置项 `nacos.failFast=true/false`（默认 true）

---

## 12. 评审清单

在开始实施前，请确认：
- [ ] 方案是否满足"至少支持蓝绿两个环境"的需求？
- [ ] API 参数修复（namespace vs groupName）是否正确？
- [ ] 资源泄漏问题是否已解决？
- [ ] 向后兼容性策略是否可接受？
- [ ] 测试覆盖率是否足够？
- [ ] 是否需要调整实施计划的优先级？

---

**下一步**：请评审此方案，确认后我将开始实施代码变更。

