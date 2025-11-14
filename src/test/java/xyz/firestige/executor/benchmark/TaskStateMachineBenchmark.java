package xyz.firestige.executor.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import xyz.firestige.executor.state.TaskStateMachine;
import xyz.firestige.executor.state.TaskStatus;

import java.util.concurrent.TimeUnit;

/**
 * TaskStateMachine 状态转移性能基准测试
 *
 * 测试场景：
 * - 单次状态转移性能
 * - 完整状态链转移性能
 * - 状态验证性能
 *
 * 运行方式：
 * mvn test -Pbenchmark -Dtest=TaskStateMachineBenchmark
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class TaskStateMachineBenchmark {

    private TaskStateMachine stateMachine;

    @Setup(Level.Iteration)
    public void setup() {
        stateMachine = new TaskStateMachine(TaskStatus.CREATED);
    }

    /**
     * 基准测试：获取当前状态
     */
    @Benchmark
    public TaskStatus getCurrentStatus() {
        return stateMachine.getCurrentStatus();
    }

    /**
     * 基准测试：验证合法的状态转移
     */
    @Benchmark
    public void validateValidTransition() {
        TaskStateMachine sm = new TaskStateMachine(TaskStatus.CREATED);
        sm.validateTransition(TaskStatus.VALIDATING);
    }

    /**
     * 基准测试：状态机创建
     */
    @Benchmark
    public TaskStateMachine createStateMachine() {
        return new TaskStateMachine(TaskStatus.CREATED);
    }

    /**
     * 基准测试：获取转移历史
     */
    @Benchmark
    public Object getTransitionHistory() {
        return stateMachine.getTransitionHistory();
    }

    /**
     * 基准测试：多次状态验证
     */
    @Benchmark
    public void multipleValidations() {
        TaskStateMachine sm = new TaskStateMachine(TaskStatus.CREATED);
        try {
            sm.validateTransition(TaskStatus.VALIDATING);
        } catch (Exception e) {
            // 忽略异常
        }

        try {
            sm.validateTransition(TaskStatus.PENDING);
        } catch (Exception e) {
            // 忽略异常
        }

        try {
            sm.validateTransition(TaskStatus.RUNNING);
        } catch (Exception e) {
            // 忽略异常
        }
    }

    /**
     * 主方法 - 用于独立运行
     */
    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(TaskStateMachineBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}

