package xyz.firestige.executor.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.execution.PipelineResult;
import xyz.firestige.executor.execution.StageResult;
import xyz.firestige.executor.execution.pipeline.Pipeline;
import xyz.firestige.executor.execution.pipeline.PipelineContext;
import xyz.firestige.executor.execution.pipeline.PipelineStage;
import xyz.firestige.executor.util.TestDataFactory;

import java.util.concurrent.TimeUnit;

/**
 * Pipeline 执行性能基准测试
 *
 * 测试场景：
 * - 空Pipeline执行性能
 * - 3个Stage的Pipeline执行
 * - 5个Stage的Pipeline执行
 * - 10个Stage的Pipeline执行
 *
 * 运行方式：
 * mvn test -Pbenchmark -Dtest=PipelineExecutionBenchmark
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class PipelineExecutionBenchmark {

    private Pipeline emptyPipeline;
    private Pipeline smallPipeline;    // 3个Stage
    private Pipeline mediumPipeline;   // 5个Stage
    private Pipeline largePipeline;    // 10个Stage

    private PipelineContext context;

    @Setup
    public void setup() {
        TenantDeployConfig config = TestDataFactory.createMinimalConfig("tenant1", 1001L);
        context = new PipelineContext("task1", config);

        // 空Pipeline
        emptyPipeline = new Pipeline();

        // 3个Stage的Pipeline
        smallPipeline = new Pipeline();
        for (int i = 0; i < 3; i++) {
            smallPipeline.addStage(new DummyStage("Stage" + i, i * 10));
        }

        // 5个Stage的Pipeline
        mediumPipeline = new Pipeline();
        for (int i = 0; i < 5; i++) {
            mediumPipeline.addStage(new DummyStage("Stage" + i, i * 10));
        }

        // 10个Stage的Pipeline
        largePipeline = new Pipeline();
        for (int i = 0; i < 10; i++) {
            largePipeline.addStage(new DummyStage("Stage" + i, i * 10));
        }
    }

    /**
     * 基准测试：空Pipeline执行
     */
    @Benchmark
    public PipelineResult executeEmptyPipeline() {
        return emptyPipeline.execute(context);
    }

    /**
     * 基准测试：3个Stage的Pipeline
     */
    @Benchmark
    public PipelineResult executeSmallPipeline() {
        return smallPipeline.execute(context);
    }

    /**
     * 基准测试：5个Stage的Pipeline
     */
    @Benchmark
    public PipelineResult executeMediumPipeline() {
        return mediumPipeline.execute(context);
    }

    /**
     * 基准测试：10个Stage的Pipeline
     */
    @Benchmark
    public PipelineResult executeLargePipeline() {
        return largePipeline.execute(context);
    }

    /**
     * 虚拟Stage - 用于性能测试
     */
    private static class DummyStage implements PipelineStage {
        private final String name;
        private final int order;

        public DummyStage(String name, int order) {
            this.name = name;
            this.order = order;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public StageResult execute(PipelineContext context) {
            // 模拟轻量级处理
            return StageResult.success(name);
        }

        @Override
        public void rollback(PipelineContext context) {
            // 空实现
        }

        @Override
        public boolean supportsRollback() {
            return false;
        }

        @Override
        public boolean canSkip(PipelineContext context) {
            return false;
        }
    }

    /**
     * 主方法 - 用于独立运行
     */
    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(PipelineExecutionBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}

