package xyz.firestige.deploy.infrastructure.execution.stage.steps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.infrastructure.execution.stage.StageStep;

/**
 * 轮询 Step（通用，支持函数注入）
 *
 * <p>职责：
 * <ul>
 *   <li>从 TaskRuntimeContext 提取轮询配置和条件函数</li>
 *   <li>定期调用条件函数检查是否就绪</li>
 *   <li>将轮询结果放回 TaskRuntimeContext</li>
 * </ul>
 *
 * <p>不包含业务逻辑，条件判断通过函数注入
 *
 * <p>数据约定：
 * <ul>
 *   <li>输入：TaskRuntimeContext 中的 "pollInterval", "pollMaxAttempts", "pollCondition"</li>
 *   <li>输出：TaskRuntimeContext 中的 "pollingResult"（Boolean）</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 * // 注入轮询条件函数
 * runtimeContext.addVariable("pollCondition", (PollingStep.PollCondition) (ctx) -> {
 *     return agentService.judgeAgent(ctx.getTenantId().getValue());
 * });
 * </pre>
 *
 * @since RF-19 三层抽象架构
 */
public class PollingStep implements StageStep {

    private static final Logger log = LoggerFactory.getLogger(PollingStep.class);

    private final String stepName;

    /**
     * 轮询条件函数式接口
     */
    @FunctionalInterface
    public interface PollCondition {
        /**
         * 检查条件是否满足
         *
         * @param context Task 运行时上下文
         * @return true 表示条件满足（停止轮询），false 表示继续轮询
         * @throws Exception 检查异常
         */
        boolean check(TaskRuntimeContext context) throws Exception;
    }

    public PollingStep(String stepName) {
        this.stepName = stepName;
    }

    @Override
    public String getStepName() {
        return stepName;
    }

    @Override
    public void execute(TaskRuntimeContext ctx) throws Exception {
        // 1. 从 context 获取配置
        Integer intervalMs = ctx.getAdditionalData("pollInterval", Integer.class);
        Integer maxAttempts = ctx.getAdditionalData("pollMaxAttempts", Integer.class);
        PollCondition condition = ctx.getAdditionalData("pollCondition", PollCondition.class);

        // 参数验证
        if (intervalMs == null || intervalMs <= 0) {
            throw new IllegalArgumentException("pollInterval must be positive");
        }
        if (maxAttempts == null || maxAttempts <= 0) {
            throw new IllegalArgumentException("pollMaxAttempts must be positive");
        }
        if (condition == null) {
            throw new IllegalArgumentException("pollCondition is required in TaskRuntimeContext");
        }

        log.debug("开始轮询: interval={}ms, maxAttempts={}", intervalMs, maxAttempts);

        // 2. 执行轮询
        int attempts = 0;
        while (attempts < maxAttempts) {
            attempts++;

            try {
                boolean isReady = condition.check(ctx);  // ← 调用注入的函数

                if (isReady) {
                    // 条件满足，轮询成功
                    ctx.addVariable("pollingResult", true);
                    log.info("轮询成功: attempts={}", attempts);
                    return;
                }

                log.debug("轮询未就绪: attempts={}/{}", attempts, maxAttempts);

            } catch (Exception e) {
                log.warn("轮询检查异常: attempts={}, error={}", attempts, e.getMessage());
                // 继续轮询，不立即失败
            }

            // 未就绪，等待后重试
            if (attempts < maxAttempts) {
                Thread.sleep(intervalMs);
            }
        }

        // 3. 超过最大尝试次数，轮询失败
        ctx.addVariable("pollingResult", false);
        log.error("轮询超时: maxAttempts={}", maxAttempts);
        throw new Exception(String.format("轮询超时：已尝试 %d 次", maxAttempts));
    }
}

