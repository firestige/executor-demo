/**
 * Redis ACK API 模块
 * <p>
 * 提供 Write → Publish → Verify 流式配置确认链路的接口定义。
 */
module xyz.firestige.redis.ack.api {
    // 导出核心 API
    exports xyz.firestige.redis.ack.api;

    // 导出异常包
    exports xyz.firestige.redis.ack.exception;

    // 需要 JDK 模块
    requires java.base;
}

