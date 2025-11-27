package xyz.firestige.deploy.testutil.stage;

import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.infrastructure.execution.StageResult;
import xyz.firestige.deploy.infrastructure.execution.stage.StageStep;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * 可控延迟的测试Stage
 * <p>
 * 用途：
 * - 测试暂停的协作式响应(在Stage边界检查)
 * - 测试取消的协作式响应
 * - 测试超时场景
 * - 模拟长时间运行的Stage
 *
 * @since T-023 测试体系重建
 */
public class SlowStage implements TaskStage {

    private final String name;
    private final Duration delay;
    private volatile boolean interrupted = false;

    public SlowStage(String name, Duration delay) {
        this.name = name;
        this.delay = delay;
    }

    /**
     * 静态工厂：创建延迟指定秒数的Stage
     */
    public static SlowStage withSeconds(String name, long seconds) {
        return new SlowStage(name, Duration.ofSeconds(seconds));
    }

    /**
     * 静态工厂：创建延迟指定毫秒数的Stage
     */
    public static SlowStage withMillis(String name, long millis) {
        return new SlowStage(name, Duration.ofMillis(millis));
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
        interrupted = false;

        try {
            Thread.sleep(delay.toMillis());
            return StageResult.success(name);
        } catch (InterruptedException e) {
            interrupted = true;
            Thread.currentThread().interrupt();
            return StageResult.success(name);
        }
    }

    @Override
    public void rollback(TaskRuntimeContext ctx) {
        // 慢速Stage回滚也可能需要时间
        try {
            Thread.sleep(delay.toMillis() / 2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public List<StageStep> getSteps() {
        return Collections.emptyList();
    }

    /**
     * 检查是否被中断（用于测试验证）
     */
    public boolean wasInterrupted() {
        return interrupted;
    }
}
