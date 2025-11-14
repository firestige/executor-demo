# JMH 性能基准测试

## 概述

本项目使用 JMH (Java Microbenchmark Harness) 对关键热点代码进行性能基准测试。

## 已实现的基准测试

### 1. ValidationChainBenchmark - 校验链性能测试
测试场景：
- 10个配置的批量校验
- 50个配置的批量校验
- 100个配置的批量校验
- 单个校验器性能

**预期结果**：
- 单个配置校验 < 100 微秒
- 批量校验应该有良好的线性扩展性

### 2. PipelineExecutionBenchmark - Pipeline执行性能测试
测试场景：
- 空Pipeline执行
- 3个Stage的Pipeline
- 5个Stage的Pipeline
- 10个Stage的Pipeline

**预期结果**：
- 空Pipeline < 10 微秒
- 每增加一个Stage，耗时增加约 5-10 微秒

### 3. TaskStateMachineBenchmark - 状态机性能测试
测试场景：
- 获取当前状态
- 状态转移验证
- 状态机创建
- 获取转移历史
- 多次状态验证

**预期结果**：
- 状态获取 < 100 纳秒
- 状态验证 < 1 微秒
- 状态机创建 < 5 微秒

### 4. CheckpointBenchmark - 检查点性能测试
测试场景：
- Checkpoint创建
- Stage数据保存
- Stage数据获取
- 检查点管理器操作
- PipelineContext创建和数据访问

**预期结果**：
- Checkpoint创建 < 1 微秒
- 数据存取 < 500 纳秒
- CheckpointManager操作 < 5 微秒

## 运行基准测试

### 运行所有基准测试
```bash
mvn clean test -Pbenchmark
```

### 运行特定的基准测试
```bash
# 运行ValidationChain基准测试
mvn test -Pbenchmark -Dtest=ValidationChainBenchmark

# 运行Pipeline基准测试
mvn test -Pbenchmark -Dtest=PipelineExecutionBenchmark

# 运行TaskStateMachine基准测试
mvn test -Pbenchmark -Dtest=TaskStateMachineBenchmark

# 运行Checkpoint基准测试
mvn test -Pbenchmark -Dtest=CheckpointBenchmark
```

### 使用main方法独立运行
每个基准测试都有main方法，可以直接运行：
```bash
# 编译
mvn clean compile test-compile

# 运行
java -cp "target/test-classes:target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
  xyz.firestige.executor.benchmark.ValidationChainBenchmark
```

## 基准测试配置说明

当前配置：
- **模式**: AverageTime (平均时间)
- **Fork**: 1次（1次预热）
- **Warmup**: 3次迭代，每次1秒
- **Measurement**: 5次迭代，每次1秒
- **时间单位**: 
  - ValidationChain/Pipeline: 微秒 (microseconds)
  - TaskStateMachine/Checkpoint: 纳秒 (nanoseconds)

## 解读基准测试结果

### 示例输出
```
Benchmark                                          Mode  Cnt    Score    Error  Units
ValidationChainBenchmark.validateSmallBatch        avgt    5   50.123 ±  2.345  us/op
ValidationChainBenchmark.validateMediumBatch       avgt    5  245.678 ± 10.234  us/op
```

### 关键指标
- **Score**: 平均执行时间
- **Error**: 误差范围（±）
- **Units**: 时间单位
  - ns/op: 纳秒每次操作
  - us/op: 微秒每次操作
  - ms/op: 毫秒每次操作

### 性能评估标准
- **优秀**: Score 低于预期值的 50%
- **良好**: Score 在预期值的 50-100%
- **可接受**: Score 在预期值的 100-150%
- **需优化**: Score 超过预期值的 150%

## 性能优化建议

如果基准测试结果不理想，考虑以下优化：

### ValidationChain
- 减少校验器数量
- 优化单个校验器的实现
- 考虑并行校验（如果配置独立）

### Pipeline
- 减少Stage数量
- 优化Stage的execute方法
- 考虑异步执行（如果Stage独立）

### TaskStateMachine
- 减少状态转移规则的复杂度
- 优化状态查找算法
- 使用更高效的数据结构

### Checkpoint
- 减少存储的数据量
- 优化序列化/反序列化
- 考虑使用更高效的存储结构

## 持续监控

建议在每次重大变更后运行基准测试：
1. 新功能开发完成后
2. 性能优化实施后
3. 依赖库升级后
4. 发布前的性能验证

## 注意事项

1. **环境一致性**: 在相同的硬件和环境下运行基准测试才能比较
2. **JVM预热**: JMH会自动处理JVM预热，无需手动预热
3. **系统负载**: 运行基准测试时避免系统高负载
4. **结果波动**: 轻微的结果波动是正常的，关注趋势而非单次结果
5. **不在CI中运行**: 基准测试耗时较长，建议手动运行而非在CI中

## 扩展基准测试

如需添加新的基准测试：

1. 创建新类继承基准测试规范
2. 添加 @BenchmarkMode、@OutputTimeUnit 等注解
3. 实现 @Setup、@Benchmark、@TearDown 方法
4. 添加main方法支持独立运行
5. 更新本README文档

## 参考资料

- [JMH官方文档](https://github.com/openjdk/jmh)
- [JMH示例](https://github.com/openjdk/jmh/tree/master/jmh-samples)
- [Java性能优化指南](https://docs.oracle.com/en/java/javase/17/docs/api/)

