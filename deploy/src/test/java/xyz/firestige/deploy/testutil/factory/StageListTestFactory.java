package xyz.firestige.deploy.testutil.factory;

import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;
import xyz.firestige.deploy.testutil.stage.AlwaysSuccessStage;
import xyz.firestige.deploy.testutil.stage.AlwaysFailStage;
import xyz.firestige.deploy.testutil.stage.FailOnceStage;
import xyz.firestige.deploy.testutil.stage.ConditionalFailStage;
import xyz.firestige.deploy.testutil.stage.SlowStage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Stage列表测试工厂
 * <p>
 * 用途：快速组装各类测试场景的Stage列表
 *
 * @since T-023 测试体系重建
 */
public class StageListTestFactory {

    /**
     * 3个成功Stage
     * 用途：测试正常执行流程
     */
    public static List<TaskStage> threeSuccessStages() {
        return Arrays.asList(
                new AlwaysSuccessStage("stage-0"),
                new AlwaysSuccessStage("stage-1"),
                new AlwaysSuccessStage("stage-2")
        );
    }

    /**
     * 5个成功Stage
     * 用途：测试多阶段串联
     */
    public static List<TaskStage> fiveSuccessStages() {
        return Arrays.asList(
                new AlwaysSuccessStage("stage-0"),
                new AlwaysSuccessStage("stage-1"),
                new AlwaysSuccessStage("stage-2"),
                new AlwaysSuccessStage("stage-3"),
                new AlwaysSuccessStage("stage-4")
        );
    }

    /**
     * 2成功 + 1失败 (在第3个Stage失败)
     * 用途：测试中途失败、checkpoint保存
     */
    public static List<TaskStage> failAtThirdStage() {
        return Arrays.asList(
                new AlwaysSuccessStage("stage-0"),
                new AlwaysSuccessStage("stage-1"),
                new AlwaysFailStage("stage-2")
        );
    }

    /**
     * 1成功 + 1失败一次 + 1成功
     * 用途：测试重试fromCheckpoint
     */
    public static List<TaskStage> failOnceAtSecondStage() {
        return Arrays.asList(
                new AlwaysSuccessStage("stage-0"),
                new FailOnceStage("stage-1"),  // 第一次失败，重试后成功
                new AlwaysSuccessStage("stage-2")
        );
    }

    /**
     * 最后一个Stage失败
     * 用途：测试接近完成时失败的场景
     */
    public static List<TaskStage> failAtLastStage() {
        return Arrays.asList(
                new AlwaysSuccessStage("stage-0"),
                new AlwaysSuccessStage("stage-1"),
                new AlwaysFailStage("stage-2")
        );
    }

    /**
     * 带延迟的Stage列表
     * 用途：测试暂停/取消的协作式响应
     */
    public static List<TaskStage> slowStages() {
        return Arrays.asList(
                SlowStage.withMillis("stage-0", 100),
                SlowStage.withMillis("stage-1", 100),
                SlowStage.withMillis("stage-2", 100)
        );
    }

    /**
     * 条件失败Stage列表（用于回滚测试）
     * 用途：模拟"旧版本成功，新版本失败"的回滚场景
     */
    public static List<TaskStage> conditionalFailOnVersion(String failVersion) {
        return Arrays.asList(
                new AlwaysSuccessStage("stage-0"),
                ConditionalFailStage.failOnVersion("stage-1", failVersion),
                new AlwaysSuccessStage("stage-2")
        );
    }

    /**
     * 自定义Stage列表
     */
    public static List<TaskStage> customStages(TaskStage... stages) {
        return Arrays.asList(stages);
    }

    /**
     * 生成指定数量的成功Stage
     */
    public static List<TaskStage> successStages(int count) {
        List<TaskStage> stages = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            stages.add(new AlwaysSuccessStage("stage-" + i));
        }
        return stages;
    }

    /**
     * 生成指定数量的成功Stage，并在指定位置插入失败Stage
     * @param totalCount 总Stage数量
     * @param failAtIndex 失败的索引位置
     */
    public static List<TaskStage> successWithFailAt(int totalCount, int failAtIndex) {
        List<TaskStage> stages = new ArrayList<>(totalCount);
        for (int i = 0; i < totalCount; i++) {
            if (i == failAtIndex) {
                stages.add(new AlwaysFailStage("stage-" + i));
            } else {
                stages.add(new AlwaysSuccessStage("stage-" + i));
            }
        }
        return stages;
    }

    /**
     * Builder模式：灵活构建Stage列表
     */
    public static StageListBuilder builder() {
        return new StageListBuilder();
    }

    public static class StageListBuilder {
        private final List<TaskStage> stages = new ArrayList<>();
        private int counter = 0;

        public StageListBuilder addSuccess() {
            stages.add(new AlwaysSuccessStage("stage-" + counter++));
            return this;
        }

        public StageListBuilder addSuccess(String name) {
            stages.add(new AlwaysSuccessStage(name));
            counter++;
            return this;
        }

        public StageListBuilder addFail() {
            stages.add(new AlwaysFailStage("stage-" + counter++));
            return this;
        }

        public StageListBuilder addFail(String name) {
            stages.add(new AlwaysFailStage(name));
            counter++;
            return this;
        }

        public StageListBuilder addFailOnce() {
            stages.add(new FailOnceStage("stage-" + counter++));
            return this;
        }

        public StageListBuilder addSlow(Duration delay) {
            stages.add(new SlowStage("stage-" + counter++, delay));
            return this;
        }

        public StageListBuilder addCustom(TaskStage stage) {
            stages.add(stage);
            counter++;
            return this;
        }

        public List<TaskStage> build() {
            return stages;
        }
    }
}
