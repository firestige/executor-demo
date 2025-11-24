package xyz.firestige.infrastructure.redis.ack.api;

import xyz.firestige.infrastructure.redis.ack.exception.AckEndpointException;

/**
 * ACK 端点接口
 * <p>
 * 用于查询业务端点，获取当前配置状态
 *
 * @author AI
 * @since 1.0
 */
public interface AckEndpoint {

    /**
     * 查询端点，获取响应
     *
     * @param context ACK 上下文
     * @return 端点响应字符串（需要进一步提取 footprint）
     * @throws AckEndpointException 查询失败
     */
    String query(AckContext context) throws AckEndpointException;
}

