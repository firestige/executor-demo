/**
 * Redis ACK Spring Boot 集成模块
 * <p>
 * 提供 Spring Boot 自动配置、属性绑定、健康检查、Micrometer 指标集成、HttpClient 和 RedisClient 实现。
 */
module xyz.firestige.redis.ack.spring {
    // 导出自动配置包
    exports xyz.firestige.redis.ack.spring.autoconfigure;
    exports xyz.firestige.redis.ack.spring.metrics;
    exports xyz.firestige.redis.ack.spring.http;
    exports xyz.firestige.redis.ack.spring.redis;
    exports xyz.firestige.redis.ack.spring.config;

    // 依赖模块
    requires transitive xyz.firestige.redis.ack.api;
    requires transitive xyz.firestige.redis.ack.core;

    // Spring Boot 依赖
    requires spring.boot;
    requires spring.boot.autoconfigure;
    requires spring.context;
    requires spring.data.redis;
    requires spring.web;
    requires spring.beans;
    requires com.fasterxml.jackson.databind;

    // Micrometer（可选）
    requires static micrometer.core;
    requires static spring.boot.actuator;

    // 允许 Spring 进行反射访问
    opens xyz.firestige.redis.ack.spring.autoconfigure to spring.core, spring.beans;
    opens xyz.firestige.redis.ack.spring.config to spring.core, spring.beans;
}

