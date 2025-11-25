/**
 * Redis ACK Core 模块
 * <p>
 * 提供 API 的默认实现，包括执行器、Builder 实现、扩展点实现。
 * 不直接依赖 Spring Web 和 Spring Data Redis，通过接口抽象 HTTP 和 Redis 客户端。
 */
module xyz.firestige.redis.ack.core {
    // 导出核心实现包
    exports xyz.firestige.redis.ack.core;
    exports xyz.firestige.redis.ack.core.exception; // 内部异常包（非公共 API）
    exports xyz.firestige.redis.ack.endpoint;
    exports xyz.firestige.redis.ack.extractor;
    exports xyz.firestige.redis.ack.retry;

    // 依赖 API 模块
    requires transitive xyz.firestige.redis.ack.api;

    // 依赖外部库
    requires com.fasterxml.jackson.databind;
    requires org.slf4j;
}
