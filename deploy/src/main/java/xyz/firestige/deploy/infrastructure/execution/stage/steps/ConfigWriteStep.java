package xyz.firestige.deploy.infrastructure.execution.stage.steps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.infrastructure.execution.stage.StageStep;
import xyz.firestige.deploy.infrastructure.execution.stage.redis.ConfigWriteData;
import xyz.firestige.deploy.infrastructure.execution.stage.redis.ConfigWriteResult;

/**
 * 配置写入 Step（Redis HSET，通用，数据无关）
 *
 * <p>职责：
 * <ul>
 *   <li>从 TaskRuntimeContext 提取配置写入数据</li>
 *   <li>执行 Redis HSET 操作</li>
 *   <li>将结果放回 TaskRuntimeContext</li>
 * </ul>
 *
 * <p>不做业务判断，只负责技术实现
 *
 * <p>数据约定：
 * <ul>
 *   <li>输入：TaskRuntimeContext 中的 "key", "field", "value"</li>
 *   <li>输出：TaskRuntimeContext 中的 "configWriteResult"（ConfigWriteResult）</li>
 * </ul>
 *
 * @since RF-19 三层抽象架构
 */
public class ConfigWriteStep implements StageStep {

    private static final Logger log = LoggerFactory.getLogger(ConfigWriteStep.class);

    private final RedisTemplate<String, String> redisTemplate;

    public ConfigWriteStep(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String getStepName() {
        return "config-write";
    }

    @Override
    public void execute(TaskRuntimeContext ctx) throws Exception {
        // 1. 准备数据（从 TaskRuntimeContext 提取）
        ConfigWriteData writeData = prepareData(ctx);

        // 2. 执行动作（Redis HSET）
        ConfigWriteResult writeResult = executeAction(writeData);

        // 3. 返回结果（放入 TaskRuntimeContext）
        ctx.addVariable("configWriteResult", writeResult);

        log.info("写入配置: key={}, field={}, success={}",
            writeData.getKey(), writeData.getField(), writeResult.isSuccess());
    }

    /**
     * 准备数据（从 TaskRuntimeContext 提取）
     */
    private ConfigWriteData prepareData(TaskRuntimeContext ctx) {
        String key = (String) ctx.getAdditionalData("key");
        String field = (String) ctx.getAdditionalData("field");
        String value = (String) ctx.getAdditionalData("value");

        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("key is required in TaskRuntimeContext");
        }
        if (field == null || field.isEmpty()) {
            throw new IllegalArgumentException("field is required in TaskRuntimeContext");
        }
        if (value == null) {
            throw new IllegalArgumentException("value is required in TaskRuntimeContext");
        }

        return ConfigWriteData.builder()
            .key(key)
            .field(field)
            .value(value)
            .build();
    }

    /**
     * 执行动作（Redis HSET）
     */
    private ConfigWriteResult executeAction(ConfigWriteData writeData) {
        ConfigWriteResult.Builder resultBuilder = ConfigWriteResult.builder()
            .key(writeData.getKey())
            .field(writeData.getField());

        try {
            // 执行 Redis HSET
            Boolean success = redisTemplate.opsForHash().putIfAbsent(
                writeData.getKey(),
                writeData.getField(),
                writeData.getValue()
            );

            if (success != null && success) {
                resultBuilder.success(true);
                resultBuilder.message("配置写入成功");
            } else {
                // putIfAbsent 返回 false 表示 key 已存在
                // 这里我们仍然认为是成功（因为配置已经存在）
                resultBuilder.success(true);
                resultBuilder.message("配置已存在，跳过写入");
            }

        } catch (Exception e) {
            log.error("Redis HSET 失败: key={}, field={}", writeData.getKey(), writeData.getField(), e);
            resultBuilder.success(false);
            resultBuilder.message("Redis 操作异常: " + e.getMessage());
        }

        return resultBuilder.build();
    }
}

