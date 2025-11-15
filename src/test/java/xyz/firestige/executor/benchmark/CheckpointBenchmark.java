package xyz.firestige.executor.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.executor.execution.checkpoint.Checkpoint;
import xyz.firestige.executor.execution.pipeline.InMemoryCheckpointManager;
import xyz.firestige.executor.execution.pipeline.PipelineContext;
import xyz.firestige.executor.util.TestDataFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Checkpoint 检查点性能基准测试
 *
 * 测试场景：
 * - Checkpoint 创建性能
 * - 数据保存性能
 * - 数据加载性能
 * - 检查点管理器性能
 *
 * 运行方式：
 * mvn test -Pbenchmark -Dtest=CheckpointBenchmark
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class CheckpointBenchmark {

    private Checkpoint checkpoint;
    private InMemoryCheckpointManager checkpointManager;
    private Map<String, Object> stageData;

    @Setup
    public void setup() {
        checkpoint = new Checkpoint("task1", "stage1");
        checkpointManager = new InMemoryCheckpointManager();

        // 准备Stage数据
        stageData = new HashMap<>();
        stageData.put("key1", "value1");
        stageData.put("key2", 123);
        stageData.put("key3", true);
    }

    /**
     * 基准测试：创建Checkpoint
     */
    @Benchmark
    public Checkpoint createCheckpoint() {
        return new Checkpoint("task1", "stage1");
    }

    /**
     * 基准测试：保存Stage数据
     */
    @Benchmark
    public void saveStageData() {
        checkpoint.saveStageData("stage1", stageData);
    }

    /**
     * 基准测试：获取Stage数据
     */
    @Benchmark
    public Map<String, Object> getStageData() {
        checkpoint.saveStageData("stage1", stageData);
        return checkpoint.getStageData("stage1");
    }

    /**
     * 基准测试：检查Stage数据存在性
     */
    @Benchmark
    public boolean hasStageData() {
        return checkpoint.hasStageData("stage1");
    }

    /**
     * 基准测试：保存和加载检查点
     */
    @Benchmark
    public Checkpoint saveAndLoadCheckpoint() {
        Checkpoint cp = new Checkpoint("task1", "stage1");
        cp.saveStageData("stage1", stageData);
        checkpointManager.saveCheckpoint("task1", "stage1", cp);
        return checkpointManager.loadCheckpoint("task1");
    }

    /**
     * 基准测试：创建PipelineContext
     */
    @Benchmark
    public PipelineContext createPipelineContext() {
        TenantDeployConfig config = TestDataFactory.createMinimalConfig("tenant1", 1001L);
        return new PipelineContext("task1", config);
    }

    /**
     * 基准测试：PipelineContext数据存取
     */
    @Benchmark
    public Object contextDataAccess() {
        TenantDeployConfig config = TestDataFactory.createMinimalConfig("tenant1", 1001L);
        PipelineContext context = new PipelineContext("task1", config);
        context.putData("key1", "value1");
        context.putData("key2", 123);
        return context.getData("key1");
    }

    /**
     * 主方法 - 用于独立运行
     */
    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(CheckpointBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}

