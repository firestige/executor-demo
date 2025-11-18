package xyz.firestige.deploy.unit.state;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Guard 测试：验证状态转换策略的前置条件检查
 * RF-13: 策略模式重构后，这些测试已经过时
 * 新的转换逻辑在 StateTransitionStrategy 实现类中
 * 应该编写策略类的单元测试，而不是测试旧的 Guard 逻辑
 */
@Disabled("RF-13: 已被策略模式取代，需要重写为策略测试")
public class TaskStateGuardsTest {

    @Test
    void failedToRunningGuardBlocksWhenRetryCountAtMax() {
        // TODO: 重写为 RetryTransitionStrategy 的单元测试
    }

    @Test
    void failedToRunningGuardAllowsWhenRetryBelowMax() {
        // TODO: 重写为 RetryTransitionStrategy 的单元测试
    }

    @Test
    void runningToPausedRequiresFlag() {
        // TODO: 重写为 PauseTransitionStrategy 的单元测试
    }

    @Test
    void runningToCompletedOnlyAfterAllStages() {
        // TODO: 重写为 CompleteTransitionStrategy 的单元测试
    }
}
