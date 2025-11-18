package xyz.firestige.deploy.service.health;

import java.util.Map;

/**
 * 健康检查客户端抽象；真实实现可 HTTP 调用。
 */
public interface HealthCheckClient {
    /**
     * @param endpoint 端点URL
     * @return 返回数据（例如版本号）
     */
    Map<String, Object> get(String endpoint) throws Exception;
}

