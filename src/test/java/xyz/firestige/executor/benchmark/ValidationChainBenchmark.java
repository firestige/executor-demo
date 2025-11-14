package xyz.firestige.executor.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import xyz.firestige.dto.TenantDeployConfig;
import xyz.firestige.executor.util.TestDataFactory;
import xyz.firestige.executor.validation.ValidationChain;
import xyz.firestige.executor.validation.ValidationSummary;
import xyz.firestige.executor.validation.validator.ConflictValidator;
import xyz.firestige.executor.validation.validator.NetworkEndpointValidator;
import xyz.firestige.executor.validation.validator.TenantIdValidator;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * ValidationChain 性能基准测试
 *
 * 测试场景：
 * - 单个配置校验性能
 * - 批量配置校验性能
 * - 校验链性能
 *
 * 运行方式：
 * mvn test -Pbenchmark -Dtest=ValidationChainBenchmark
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class ValidationChainBenchmark {

    private ValidationChain validationChain;
    private List<TenantDeployConfig> smallBatch;   // 10个配置
    private List<TenantDeployConfig> mediumBatch;  // 50个配置
    private List<TenantDeployConfig> largeBatch;   // 100个配置

    @Setup
    public void setup() {
        // 清理冲突检测器
        ConflictValidator.clearAll();

        // 初始化校验链
        validationChain = new ValidationChain(false);
        validationChain.addValidator(new TenantIdValidator());
        validationChain.addValidator(new NetworkEndpointValidator());
        validationChain.addValidator(new ConflictValidator());

        // 准备测试数据
        smallBatch = TestDataFactory.createConfigList(10);
        mediumBatch = TestDataFactory.createConfigList(50);
        largeBatch = TestDataFactory.createConfigList(100);
    }

    @TearDown
    public void tearDown() {
        ConflictValidator.clearAll();
    }

    /**
     * 基准测试：校验10个配置
     */
    @Benchmark
    public ValidationSummary validateSmallBatch() {
        return validationChain.validateAll(smallBatch);
    }

    /**
     * 基准测试：校验50个配置
     */
    @Benchmark
    public ValidationSummary validateMediumBatch() {
        return validationChain.validateAll(mediumBatch);
    }

    /**
     * 基准测试：校验100个配置
     */
    @Benchmark
    public ValidationSummary validateLargeBatch() {
        return validationChain.validateAll(largeBatch);
    }

    /**
     * 基准测试：单个校验器性能 - TenantIdValidator
     */
    @Benchmark
    public ValidationSummary validateWithSingleValidator() {
        ValidationChain chain = new ValidationChain(false);
        chain.addValidator(new TenantIdValidator());
        return chain.validateAll(mediumBatch);
    }

    /**
     * 主方法 - 用于独立运行
     */
    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(ValidationChainBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}

