package xyz.firestige.redis.ack.api;

/**
 * Redis ACK 服务主入口
 * <p>
 * 提供 Write → Pub/Sub → Verify 的完整流程
 *
 * @author AI
 * @since 1.0
 */
public interface RedisAckService {

    /**
     * 开始构建 Redis 写入 + Pub/Sub + Verify 流程
     *
     * @return Write 阶段构建器
     */
    WriteStageBuilder write();
}

