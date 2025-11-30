package xyz.firestige.deploy.testutil.stage;

import xyz.firestige.deploy.domain.shared.exception.ErrorType;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.infrastructure.execution.StageResult;
import xyz.firestige.deploy.infrastructure.execution.stage.StageStep;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * 条件失败的测试Stage
 * <p>
 * 用途：
 * - 测试回滚场景(旧配置成功，新配置失败)
 * - 测试基于上下文的条件执行
 * - 灵活控制失败场景
 *
 * @since T-023 测试体系重建
 */
public class ConditionalFailStage implements TaskStage {

    private final String name;
    private final Predicate<TaskRuntimeContext> shouldFailPredicate;
    private final ErrorType errorType;
    private final String errorMessage;

    /**
     * 构造器：使用Predicate控制失败条件
     */
    public ConditionalFailStage(String name, Predicate<TaskRuntimeContext> shouldFailPredicate) {
        this(name, shouldFailPredicate, ErrorType.SYSTEM_ERROR, "Conditional failure");
    }

    /**
     * 构造器：完全自定义
     */
    public ConditionalFailStage(String name,
                                Predicate<TaskRuntimeContext> shouldFailPredicate,
                                ErrorType errorType,
                                String errorMessage) {
        this.name = name;
        this.shouldFailPredicate = shouldFailPredicate;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
    }

    /**
     * 静态工厂：基于部署版本失败
     * 用途：模拟"旧版本成功，新版本失败"的回滚场景
     * 注意：需要通过context中的"deployVersion"变量传入版本信息
     */
    public static ConditionalFailStage failOnVersion(String name, String failVersion) {
        return new ConditionalFailStage(
                name,
                ctx -> {
                    String currentVersion = ctx.getAdditionalData("deployVersion", String.class);
                    return failVersion.equals(currentVersion);
                },
                ErrorType.BUSINESS_ERROR,
                "Failed on version: " + failVersion
        );
    }

    /**
     * 静态工厂：基于租户ID失败
     */
    public static ConditionalFailStage failOnTenant(String name, String failTenantId) {
        return new ConditionalFailStage(
                name,
                ctx -> failTenantId.equals(ctx.getTenantId().getValue()),
                ErrorType.BUSINESS_ERROR,
                "Failed on tenant: " + failTenantId
        );
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean canSkip(TaskRuntimeContext ctx) {
        return false;
    }

    @Override
    public StageResult execute(TaskRuntimeContext ctx) {
        StageResult res;
        if (shouldFailPredicate.test(ctx)) {
            FailureInfo failureInfo = FailureInfo.of(errorType, errorMessage, name);
            res = StageResult.failure(name, failureInfo);
        } else {
            res = StageResult.success(name);
        }

        res.setDuration(Duration.ZERO);

        return res;
    }

    @Override
    public void rollback(TaskRuntimeContext ctx) {
        // 条件失败Stage的回滚逻辑
    }

    @Override
    public List<StageStep> getSteps() {
        return Collections.emptyList();
    }
}
