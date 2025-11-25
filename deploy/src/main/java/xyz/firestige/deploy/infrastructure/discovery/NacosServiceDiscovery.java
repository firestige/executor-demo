package xyz.firestige.deploy.infrastructure.discovery;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Nacos 服务发现封装
 *
 * <p>职责：
 * <ul>
 *   <li>初始化 Nacos NamingService</li>
 *   <li>获取健康服务实例（支持动态 namespace）</li>
 *   <li>异常处理和日志</li>
 * </ul>
 *
 * @since T-025
 */
public class NacosServiceDiscovery {

    private static final Logger log = LoggerFactory.getLogger(NacosServiceDiscovery.class);

    private final NamingService namingService;
    private volatile boolean available = true;

    /**
     * 构造函数
     *
     * @param serverAddr Nacos 服务器地址（如 "127.0.0.1:8848"）
     * @throws NacosException Nacos 初始化失败
     */
    public NacosServiceDiscovery(String serverAddr) throws NacosException {
        Properties properties = new Properties();
        properties.put("serverAddr", serverAddr);

        try {
            this.namingService = NamingFactory.createNamingService(properties);
            log.info("Nacos 客户端初始化成功: serverAddr={}", serverAddr);
        } catch (NacosException e) {
            log.error("Nacos 客户端初始化失败: serverAddr={}", serverAddr, e);
            this.available = false;
            throw e;
        }
    }

    /**
     * 从 Nacos 获取健康实例列表
     *
     * @param serviceName Nacos 服务名
     * @param namespace 命名空间（可选，为 null 则使用默认命名空间）
     * @return 实例列表（host:port 格式），失败返回空列表
     */
    public List<String> getHealthyInstances(String serviceName, String namespace) {
        if (!available) {
            log.warn("Nacos 不可用，跳过查询: service={}", serviceName);
            return Collections.emptyList();
        }

        try {
            List<Instance> instances;

            if (namespace != null && !namespace.isEmpty()) {
                // 指定 namespace 查询
                instances = namingService.selectInstances(serviceName, namespace, true);
            } else {
                // 默认 namespace 查询
                instances = namingService.selectInstances(serviceName, true);
            }

            if (instances == null || instances.isEmpty()) {
                log.warn("Nacos 未找到健康实例: service={}, namespace={}", serviceName, namespace);
                return Collections.emptyList();
            }

            List<String> endpoints = instances.stream()
                .map(inst -> inst.getIp() + ":" + inst.getPort())
                .collect(Collectors.toList());

            log.debug("Nacos 查询成功: service={}, namespace={}, instances={}",
                serviceName, namespace, endpoints);
            return endpoints;

        } catch (NacosException e) {
            log.error("Nacos 查询失败: service={}, namespace={}, error={}",
                serviceName, namespace, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 检查 Nacos 是否可用
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * 关闭 Nacos 客户端
     */
    public void shutdown() {
        try {
            if (namingService != null) {
                namingService.shutDown();
                log.info("Nacos 客户端已关闭");
            }
        } catch (NacosException e) {
            log.warn("Nacos 客户端关闭异常", e);
        }
    }
}

