package xyz.firestige.redis.ack.examples;

import xyz.firestige.redis.ack.api.AckResult;
import xyz.firestige.redis.ack.api.RedisAckService;

import java.time.Duration;
import java.util.Map;

/**
 * Redis ACK 服务使用示例
 * <p>
 * 展示如何在 deploy 模块中使用 ACK 服务
 *
 * @author AI
 * @since 1.0
 */
public class DeployAckExamples {

    /**
     * 示例 1：BlueGreen Gateway 配置更新
     */
    public static AckResult blueGreenGatewayExample(
            RedisAckService ackService,
            String tenantId,
            Map<String, Object> bgConfig) {

        return ackService.write()
            // Write 阶段
            .hashKey("config:tenant:" + tenantId, "blue-green")
            .value(bgConfig)
            .footprint("version")
            .ttl(Duration.ofMinutes(30))

            // Pub/Sub 阶段
            .andPublish()
                .topic("gateway-config-updates")
                .message("BG_CONFIG_UPDATED:" + tenantId)

            // Verify 阶段
            .andVerify()
                .httpGet("http://gateway:8080/actuator/config/" + tenantId)
                .extractJson("blueGreenConfig.version")
                .retryFixedDelay(10, Duration.ofSeconds(3))
                .timeout(Duration.ofSeconds(60))

            .executeAndWait();
    }

    /**
     * 示例 2：OB Service 配置更新
     */
    public static AckResult obServiceExample(
            RedisAckService ackService,
            String tenantId,
            Map<String, Object> obConfig) {

        return ackService.write()
            .hashKey("config:tenant:" + tenantId, "ob-campaign")
            .value(obConfig)
            .footprint((java.util.function.Function<Object, String>)
                cfg -> ((Map<String, Object>)cfg).get("sourceUnit").toString())

            .andPublish()
                .topic("ob-config-updates")
                .message("OB_CONFIG_UPDATED:" + tenantId)

            .andVerify()
                .httpGet("http://agent:8080/api/ob/status/" + tenantId)
                .extractWith(response -> {
                    // 自定义提取逻辑
                    return response.split(":")[1];
                })
                .retryFixedDelay(10, Duration.ofSeconds(3))

            .executeAndWait();
    }

    /**
     * 示例 3：通用配置推送
     */
    public static void genericConfigExample(
            RedisAckService ackService,
            String key,
            Object config,
            String topic,
            String verifyUrl) {

        ackService.write()
            .key(key)
            .value(config)
            .footprint("configVersion")

            .andPublish()
                .topic(topic)
                .message("CONFIG_UPDATED")

            .andVerify()
                .httpGet(verifyUrl)
                .extractJson("currentVersion")
                .retryFixedDelay(5, Duration.ofSeconds(2))

            .executeAsync()
            .thenAccept(result -> {
                if (result.isSuccess()) {
                    System.out.println("配置验证成功: " + result);
                } else {
                    System.err.println("配置验证失败: " + result.getReason());
                }
            });
    }
}

