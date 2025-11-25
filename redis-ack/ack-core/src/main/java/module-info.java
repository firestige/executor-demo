/**
 * Redis ACK Core 模块
 * <p>
 * 提供 API 的默认实现，包括执行器、Builder 实现、扩展点实现。
 */
module xyz.firestige.redis.ack.core {
    // 导出核心实现包
    exports xyz.firestige.redis.ack.core;
    exports xyz.firestige.redis.ack.endpoint;
    exports xyz.firestige.redis.ack.extractor;
    exports xyz.firestige.redis.ack.retry;

    // 依赖 API 模块
    requires transitive xyz.firestige.redis.ack.api;

    // 依赖外部库
    requires com.fasterxml.jackson.databind;
    requires spring.data.redis;
    requires spring.web;
    requires spring.context;
    requires org.slf4j;
    requires spring.beans;
}
