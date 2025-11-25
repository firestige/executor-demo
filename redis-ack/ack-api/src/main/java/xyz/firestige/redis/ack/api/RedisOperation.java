package xyz.firestige.redis.ack.api;

/**
 * Redis 操作类型枚举
 *
 * @author AI
 * @since 1.0
 */
public enum RedisOperation {

    /**
     * String: SET key value
     */
    SET,

    /**
     * Hash: HSET key field value
     */
    HSET,

    /**
     * List: LPUSH key value
     */
    LPUSH,

    /**
     * Set: SADD key value
     */
    SADD,

    /**
     * ZSet: ZADD key score value
     */
    ZADD
}

