# Redis 续期服务扩展指南

本文档介绍如何扩展 Redis 续期服务，包括添加新的 Redis 客户端实现、自定义策略等。

## 目录
- [扩展 Redis 客户端](#扩展-redis-客户端)
- [使用 SPI 加载机制](#使用-spi-加载机制)
- [自定义策略](#自定义策略)
- [测试建议](#测试建议)

---

## 扩展 Redis 客户端

### 1. Jedis 客户端扩展示例

#### 步骤 1：创建模块或包
```
xyz.firestige.infrastructure.redis.renewal.client.jedis
```

#### 步骤 2：引入 Jedis 依赖
```xml
<dependency>
    <groupId>redis.clients</groupId>
    <artifactId>jedis</artifactId>
    <version>5.0.0</version>
</dependency>
```

#### 步骤 3：实现 RedisClient 接口
```java
package xyz.firestige.infrastructure.redis.renewal.client.jedis;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;
import xyz.firestige.infrastructure.redis.renewal.api.RedisClient;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class JedisRedisClient implements RedisClient {
    
    private final JedisPool jedisPool;
    
    public JedisRedisClient(JedisPool jedisPool) {
        this.jedisPool = Objects.requireNonNull(jedisPool);
    }
    
    @Override
    public boolean expire(String key, long ttlSeconds) {
        try (var jedis = jedisPool.getResource()) {
            return jedis.expire(key, ttlSeconds) == 1;
        }
    }
    
    @Override
    public Map<String, Boolean> batchExpire(Collection<String> keys, long ttlSeconds) {
        Map<String, Boolean> results = new LinkedHashMap<>();
        
        try (var jedis = jedisPool.getResource()) {
            Pipeline pipeline = jedis.pipelined();
            
            for (String key : keys) {
                pipeline.expire(key, ttlSeconds);
            }
            
            List<Object> responses = pipeline.syncAndReturnAll();
            int index = 0;
            for (String key : keys) {
                results.put(key, Long.valueOf(1).equals(responses.get(index++)));
            }
        }
        
        return results;
    }
    
    @Override
    public CompletableFuture<Boolean> expireAsync(String key, long ttlSeconds) {
        return CompletableFuture.supplyAsync(() -> expire(key, ttlSeconds));
    }
    
    @Override
    public CompletableFuture<Map<String, Boolean>> batchExpireAsync(
            Collection<String> keys, long ttlSeconds) {
        return CompletableFuture.supplyAsync(() -> batchExpire(keys, ttlSeconds));
    }
    
    @Override
    public Collection<String> scan(String pattern, int count) {
        Set<String> result = new HashSet<>();
        
        try (var jedis = jedisPool.getResource()) {
            String cursor = "0";
            ScanParams params = new ScanParams().match(pattern).count(count);
            
            do {
                ScanResult<String> scanResult = jedis.scan(cursor, params);
                result.addAll(scanResult.getResult());
                cursor = scanResult.getCursor();
            } while (!"0".equals(cursor));
        }
        
        return result;
    }
    
    @Override
    public boolean exists(String key) {
        try (var jedis = jedisPool.getResource()) {
            return jedis.exists(key);
        }
    }
    
    @Override
    public long ttl(String key) {
        try (var jedis = jedisPool.getResource()) {
            return jedis.ttl(key);
        }
    }
    
    @Override
    public void close() {
        jedisPool.close();
    }
}
```

#### 步骤 4：实现 SPI 提供者
```java
package xyz.firestige.infrastructure.redis.renewal.client.jedis;

import redis.clients.jedis.JedisPool;
import xyz.firestige.infrastructure.redis.renewal.api.RedisClient;
import xyz.firestige.redis.renewal.spi.RedisClientProvider;

public class JedisRedisClientProvider implements RedisClientProvider {
    
    private JedisPool jedisPool;
    
    public JedisRedisClientProvider() {
    }
    
    public void setJedisPool(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }
    
    @Override
    public String getName() {
        return "jedis";
    }
    
    @Override
    public RedisClient createClient() {
        if (jedisPool == null) {
            throw new IllegalStateException("JedisPool 未设置");
        }
        return new JedisRedisClient(jedisPool);
    }
}
```

#### 步骤 5：注册 SPI
在 `META-INF/services/xyz.firestige.redis.renewal.spi.RedisClientProvider` 中添加：
```
xyz.firestige.infrastructure.redis.renewal.client.jedis.JedisRedisClientProvider
```

#### 步骤 6：（可选）提供 Spring Boot 自动配置
```java
@AutoConfiguration
@ConditionalOnClass(JedisPool.class)
@ConditionalOnMissingBean(RedisClient.class)
public class JedisRenewalAutoConfiguration {
    
    @Bean
    public RedisClient redisClient(JedisPool jedisPool) {
        return new JedisRedisClient(jedisPool);
    }
}
```

#### 步骤 7：添加测试
```java
class JedisRedisClientTest {
    
    @Test
    void expire_setsExpiration() {
        // 使用 TestContainers 启动 Redis
        try (GenericContainer<?> redis = new GenericContainer<>("redis:7")
                .withExposedPorts(6379)) {
            redis.start();
            
            JedisPool pool = new JedisPool(
                redis.getHost(), 
                redis.getFirstMappedPort()
            );
            
            JedisRedisClient client = new JedisRedisClient(pool);
            
            // 设置测试数据
            try (var jedis = pool.getResource()) {
                jedis.set("test-key", "value");
            }
            
            // 测试续期
            assertTrue(client.expire("test-key", 60));
            assertTrue(client.ttl("test-key") > 0);
        }
    }
}
```

---

## 使用 SPI 加载机制

### 非 Spring 环境使用 SPI

```java
import xyz.firestige.redis.renewal.spi.RedisClientLoader;

// 加载第一个可用的客户端
RedisClient client = RedisClientLoader.loadFirst();

// 加载指定名称的客户端
RedisClient jedisClient = RedisClientLoader.load("jedis");
```

### 注意事项

1. **SCAN 命令行为差异**
   - 不同客户端的 SCAN 实现可能有细微差异
   - 统一使用 `pattern` + `count` 参数
   - 注意游标处理（Jedis 使用字符串，Lettuce 使用 ScanCursor）

2. **异步操作**
   - 如果客户端不支持原生异步，可用 `CompletableFuture.supplyAsync()` 包装
   - Lettuce 支持原生异步：`RedisAsyncCommands`

3. **连接池参数**
   - 建议暴露在构造参数或配置对象中
   - 避免硬编码

4. **错误处理与日志**
   - 保持与 Spring 实现一致的格式
   - 使用 SLF4J 记录日志

---

## 自定义策略

### 自定义 TTL 策略

```java
public class BusinessHoursTtlStrategy implements RenewalStrategy {
    
    @Override
    public Duration calculateTtl(RenewalContext context) {
        LocalTime now = LocalTime.now();
        
        // 工作时间（9:00-18:00）短 TTL，非工作时间长 TTL
        if (now.isAfter(LocalTime.of(9, 0)) && 
            now.isBefore(LocalTime.of(18, 0))) {
            return Duration.ofMinutes(5);
        } else {
            return Duration.ofHours(1);
        }
    }
    
    @Override
    public boolean shouldContinue(RenewalContext context) {
        return true;
    }
    
    @Override
    public String getName() {
        return "BusinessHoursTtl";
    }
}
```

### 使用自定义策略

```java
RenewalTask task = RenewalTask.builder()
    .keys(List.of("business:key"))
    .ttlStrategy(new BusinessHoursTtlStrategy())
    .intervalStrategy(new FixedIntervalStrategy(Duration.ofMinutes(2)))
    .stopStrategy(new NeverStopCondition())
    .build();

renewalService.register(task);
```

---

## Lettuce 客户端扩展（参考）

### 关键差异

1. **异步支持**
   ```java
   RedisAsyncCommands<String, String> async = connection.async();
   RedisFuture<Boolean> future = async.expire(key, ttlSeconds);
   ```

2. **批量操作**
   - 可使用事务或 Pipeline
   - 性能需评估后选择

3. **SCAN 实现**
   ```java
   ScanCursor cursor = ScanCursor.INITIAL;
   ScanArgs args = ScanArgs.Builder.matches(pattern).limit(count);
   
   do {
       ScanCursor<String> result = commands.scan(cursor, args);
       keys.addAll(result.getKeys());
       cursor = ScanCursor.of(result.getCursor());
   } while (!result.isFinished());
   ```

---

## 测试建议

### 单元测试
- 测试所有 `RedisClient` 接口方法
- Mock Redis 行为或使用内存实现

### 集成测试
- 使用 [TestContainers](https://www.testcontainers.org/) 启动真实 Redis
- 测试 Pipeline 批量操作
- 测试 SCAN 分页

### 性能测试
- 批量续期延迟
- 并发场景下的吞吐量
- 内存和 CPU 占用

---

## 扩展验收清��

- [ ] 实现 `RedisClient` 接口所有方法
- [ ] 使用 Pipeline 优化批量操作
- [ ] 使用 SCAN 命令避免阻塞
- [ ] 实现 `RedisClientProvider`（如需 SPI）
- [ ] 注册到 `META-INF/services`
- [ ] （可选）提供 Spring Boot AutoConfiguration
- [ ] 添加单元测试
- [ ] 添加集成测试（TestContainers）
- [ ] 更新文档

---

## 参考资料

- [Spring Data Redis 官方文档](https://spring.io/projects/spring-data-redis)
- [Jedis GitHub](https://github.com/redis/jedis)
- [Lettuce GitHub](https://github.com/lettuce-io/lettuce-core)
- [Redis SCAN 命令文档](https://redis.io/commands/scan/)
- [TestContainers Redis 模块](https://www.testcontainers.org/modules/databases/redis/)

