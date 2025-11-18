package xyz.firestige.deploy.infrastructure.execution.stage.steps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.deploy.infrastructure.execution.stage.StageStep;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;

/**
 * 配置下发步骤：模拟向目标服务写入新版本配置。
 * 后续可扩展：实际调用配置中心、Redis等；当前只记录日志并更新聚合的 deployUnitVersion。
 */
public class ConfigUpdateStep implements StageStep {
    private static final Logger log = LoggerFactory.getLogger(ConfigUpdateStep.class);

    private final String stepName;
    private final Long targetVersion; // 期望切换到的版本

    public ConfigUpdateStep(String stepName, Long targetVersion) {
        this.stepName = stepName;
        this.targetVersion = targetVersion;
    }

    @Override
    public String getStepName() { return stepName; }

    public Long getTargetVersion() { return targetVersion; }

    @Override
    public void execute(TaskRuntimeContext ctx) throws Exception {
        if (ctx == null) return;
        // 从上下文中无法直接访问聚合，这里通过运行时的 PipelineContext 暂不处理；执行器会在阶段结束更新聚合。
        // 占位：只打日志；后续改为通过 TaskExecutor 提供 TaskAggregate 注入或扩展 TaskRuntimeContext。
        log.info("[ConfigUpdateStep] 下发新配置 version={} step={} taskId={}", targetVersion, stepName, ctx.getTaskId());
    }
}
