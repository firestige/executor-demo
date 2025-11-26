package xyz.firestige.deploy.infrastructure.discovery;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Nacos 服务发现多命名空间管理器
 *
 * <p>核心功能：
 * <ul>
 *   <li>支持多命名空间动态切换（蓝/绿环境隔离）</li>
 *   <li>惰性加载：namespace 首次访问时才创建客户端</li>
 *   <li>LRU + TTL 驱逐：空闲客户端自动关闭释放资源</li>
 *   <li>引用计数：防止使用中的客户端被驱逐</li>
 *   <li>线程安全：并发访问和驱逐的同步控制</li>
 * </ul>
 *
 * @since T-025, T-030
 */
public class NacosServiceDiscovery {

    private static final Logger log = LoggerFactory.getLogger(NacosServiceDiscovery.class);
    private static final String DEFAULT_GROUP = "DEFAULT_GROUP";

    private final String serverAddr;
    private final String username;
    private final String password;
    private final String defaultNamespace;
    private final Map<String, ClientEntry> clientsByNamespace = new ConcurrentHashMap<>();
    private final ScheduledExecutorService evictionScheduler;
    private final long clientIdleTimeoutMillis;
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
     *
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
     *
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
        properties.put("namespace", namespace);  // ✅ 关键修复：在初始化时绑定 namespace

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
     * @param namespace   命名空间（null 表示使用配置的默认命名空间）
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
     * @param namespace   命名空间
     * @param groupName   Nacos 分组名（如 DEFAULT_GROUP, CUSTOM_GROUP）
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

        public int getRefCount() {
            return refCount;
        }

        public long getIdleTimeMillis() {
            return idleTimeMillis;
        }
    }
}

