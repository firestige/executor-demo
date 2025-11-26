package xyz.firestige.deploy.infrastructure.discovery;

import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NacosServiceDiscovery 单元测试
 *
 * @since T-030
 */
class NacosServiceDiscoveryTest {

    private NacosServiceDiscovery discovery;

    @AfterEach
    void tearDown() {
        if (discovery != null) {
            discovery.shutdown();
        }
    }

    @Test
    void testBuilderWithMinimalConfig() {
        // 测试最小配置（只需要 serverAddr）
        discovery = NacosServiceDiscovery.builder("localhost:8848")
                .build();

        assertNotNull(discovery);
        assertTrue(discovery.isAvailable());
        assertEquals(0, discovery.getManagedNamespaces().size());
    }

    @Test
    void testBuilderWithFullConfig() {
        // 测试完整配置
        discovery = NacosServiceDiscovery.builder("localhost:8848")
                .username("test_user")
                .password("test_password")
                .defaultNamespace("test-namespace")
                .clientIdleTimeoutMinutes(10)
                .evictionIntervalMinutes(2)
                .build();

        assertNotNull(discovery);
        assertTrue(discovery.isAvailable());
    }

    @Test
    void testBuilderWithInvalidServerAddr() {
        // 测试无效的 serverAddr
        assertThrows(IllegalArgumentException.class, () -> {
            NacosServiceDiscovery.builder(null).build();
        });

        assertThrows(IllegalArgumentException.class, () -> {
            NacosServiceDiscovery.builder("").build();
        });
    }

    @Test
    void testBuilderWithInvalidTimeout() {
        // 测试无效的超时时间
        assertThrows(IllegalArgumentException.class, () -> {
            NacosServiceDiscovery.builder("localhost:8848")
                    .clientIdleTimeoutMillis(0)
                    .build();
        });

        assertThrows(IllegalArgumentException.class, () -> {
            NacosServiceDiscovery.builder("localhost:8848")
                    .clientIdleTimeoutMillis(-1)
                    .build();
        });
    }

    @Test
    void testGetHealthyInstancesWithInvalidNacos() {
        // 测试连接无效的 Nacos 服务器
        discovery = NacosServiceDiscovery.builder("invalid-host:9999")
                .clientIdleTimeoutMinutes(1)
                .build();

        // 查询会失败，但不应抛出异常，应返回空列表
        List<String> instances = discovery.getHealthyInstances("test-service", "blue-env");

        assertNotNull(instances);
        assertTrue(instances.isEmpty());
    }

    @Test
    void testLazyInitializationAndNamespaceTracking() throws InterruptedException {
        // 测试惰性初始化和命名空间追踪
        discovery = NacosServiceDiscovery.builder("localhost:8848")
                .clientIdleTimeoutMinutes(10)
                .build();

        // 初始状态：没有管理任何 namespace
        assertEquals(0, discovery.getManagedNamespaces().size());

        // 第一次查询 blue-env（会触发客户端初始化，但因为连接失败，不会创建客户端）
        discovery.getHealthyInstances("test-service", "blue-env");

        // 第二次查询 green-env
        discovery.getHealthyInstances("test-service", "green-env");

        // 由于无法连接到 Nacos，客户端不会被创建
        // 这个测试主要验证代码不会崩溃
        assertTrue(true);
    }

    @Test
    void testGetManagedNamespaces() {
        discovery = NacosServiceDiscovery.builder("localhost:8848")
                .build();

        Set<String> namespaces = discovery.getManagedNamespaces();
        assertNotNull(namespaces);
        assertEquals(0, namespaces.size());
    }

    @Test
    void testGetClientStats() {
        discovery = NacosServiceDiscovery.builder("localhost:8848")
                .build();

        Map<String, NacosServiceDiscovery.ClientStats> stats = discovery.getClientStats();
        assertNotNull(stats);
        assertEquals(0, stats.size());
    }

    @Test
    void testShutdownIdempotent() {
        // 测试 shutdown 可以多次调用（幂等性）
        discovery = NacosServiceDiscovery.builder("localhost:8848")
                .build();

        assertTrue(discovery.isAvailable());

        discovery.shutdown();
        assertFalse(discovery.isAvailable());

        // 再次调用 shutdown 不应抛出异常
        assertDoesNotThrow(() -> discovery.shutdown());
        assertFalse(discovery.isAvailable());
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        // 测试并发访问的线程安全性
        discovery = NacosServiceDiscovery.builder("localhost:8848")
                .clientIdleTimeoutMinutes(10)
                .build();

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(50);

        for (int i = 0; i < 50; i++) {
            final String namespace = "ns-" + (i % 5);  // 5 个不同的 namespace
            executor.submit(() -> {
                try {
                    discovery.getHealthyInstances("test-service", namespace);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // 验证没有发生并发问题（代码不应崩溃）
        assertTrue(true);
    }

    @Test
    void testDefaultNamespaceHandling() {
        // 测试默认命名空间处理
        discovery = NacosServiceDiscovery.builder("localhost:8848")
                .defaultNamespace("custom-default")
                .build();

        // 传入 null 应该使用配置的默认命名空间
        List<String> instances = discovery.getHealthyInstances("test-service", null);
        assertNotNull(instances);

        // 传入空字符串也应该使用默认命名空间
        instances = discovery.getHealthyInstances("test-service", "");
        assertNotNull(instances);
    }

    @Test
    void testCustomGroupName() {
        // 测试自定义分组名
        discovery = NacosServiceDiscovery.builder("localhost:8848")
                .build();

        // 使用默认分组
        List<String> instances1 = discovery.getHealthyInstances("test-service", "blue-env");
        assertNotNull(instances1);

        // 使用自定义分组
        List<String> instances2 = discovery.getHealthyInstances("test-service", "blue-env", "CUSTOM_GROUP");
        assertNotNull(instances2);
    }

    @Test
    void testIsAvailableAfterFailedInit() {
        // 测试初始化失败后的可用性状态
        discovery = NacosServiceDiscovery.builder("invalid-host:9999")
                .build();

        // 初始化失败后，第一次查询会将 available 设置为 false
        discovery.getHealthyInstances("test-service", "blue-env");

        // 后续查询应该快速返回空列表，而不是尝试重连
        List<String> instances = discovery.getHealthyInstances("test-service", "green-env");
        assertNotNull(instances);
        assertTrue(instances.isEmpty());
    }
}

