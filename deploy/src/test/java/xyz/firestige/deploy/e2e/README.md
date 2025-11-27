# E2E 测试指南

## 📋 概述

E2E（End-to-End）测试通过加载完整的Spring上下文，验证整个部署流程的正确性。

## 🎯 测试范围

### 1. 新建切换任务 (NewDeployTaskE2ETest)
- ✅ 创建Plan和Task
- ✅ 提交执行
- ✅ 验证状态转换：PENDING → RUNNING → COMPLETED
- ✅ 验证Stage执行进度
- ✅ 验证所有Stage按顺序执行

### 2. 重试切换任务 (RetryDeployTaskE2ETest)
- ✅ 从头重试失败任务
- ✅ 从Checkpoint重试任务
- ✅ 验证重试计数器递增
- ✅ 验证超过最大重试次数的保护
- ✅ 验证重试成功后清除失败信息

### 3. 回滚切换任务 (RollbackDeployTaskE2ETest)
- ✅ 回滚失败的任务
- ✅ 验证状态转换：FAILED → ROLLING_BACK → ROLLED_BACK
- ✅ 验证配置恢复到上一版本
- ✅ 拒绝没有回滚快照的任务
- ✅ 记录回滚失败
- ✅ 回滚后允许重试
- ✅ 保留任务记录完整性

## 🏗️ 测试架构

```
BaseE2ETest (基类)
├── 加载完整Spring上下文 (@SpringBootTest)
├── 自动注入Repository
├── 提供setUp/tearDown清理逻辑
└── 配置测试Profile (@ActiveProfiles("test"))

NewDeployTaskE2ETest
├── 继承BaseE2ETest
├── 注入TaskOperationService
└── 测试新建切换任务完整流程

RetryDeployTaskE2ETest
├── 继承BaseE2ETest
├── 注入TaskOperationService
└── 测试重试机制各种场景

RollbackDeployTaskE2ETest
├── 继承BaseE2ETest
├── 注入TaskOperationService
└── 测试回滚机制各种场景
```

## 🔧 配置说明

### 测试Profile (application-test.yml)
```yaml
# 建议配置：
spring:
  redis:
    # 使用embedded redis或testcontainers
  datasource:
    # 使用H2或testcontainers
```

### InMemory Repository
E2E测试依赖Repository的InMemory实现：
- `InMemoryTaskRepository`
- `InMemoryPlanRepository`
- `InMemoryTaskRuntimeRepository`

每个测试前后会自动清理数据（通过`cleanupRepositories()`）。

## 🚀 运行测试

### 运行所有E2E测试
```bash
mvn test -Dtest=xyz.firestige.deploy.e2e.**
```

### 运行单个测试类
```bash
mvn test -Dtest=NewDeployTaskE2ETest
mvn test -Dtest=RetryDeployTaskE2ETest
mvn test -Dtest=RollbackDeployTaskE2ETest
```

### 运行单个测试方法
```bash
mvn test -Dtest=NewDeployTaskE2ETest#shouldCreateAndExecuteNewDeployTask
```

## ⏱️ 执行时间

E2E测试包含异步执行等待，预计执行时间：
- 单个测试：2-5秒
- 整个E2E测试套件：30-60秒

## 📝 编写新测试

### 1. 继承BaseE2ETest
```java
@DisplayName("E2E: 你的场景")
class YourE2ETest extends BaseE2ETest {
    
    @Autowired
    private TaskOperationService taskOperationService;
    
    @Test
    @DisplayName("应该...")
    void shouldDoSomething() {
        // 测试逻辑
    }
}
```

### 2. 测试数据准备
使用`ValueObjectTestFactory`创建测试数据：
```java
TenantId tenantId = ValueObjectTestFactory.tenantId("tenant-001");
PlanId planId = ValueObjectTestFactory.randomPlanId();
TaskId taskId = ValueObjectTestFactory.randomTaskId();
```

### 3. 验证异步执行
```java
// 触发异步操作
taskOperationService.retryTaskByTenant(tenantId, false);

// 等待完成（实际场景可能需要轮询）
TimeUnit.SECONDS.sleep(2);

// 验证结果
TaskAggregate result = taskRepository.findById(taskId).orElseThrow();
assertEquals(TaskStatus.RUNNING, result.getStatus());
```

### 4. 使用断言
```java
// JUnit5断言
assertEquals(expected, actual, "失败消息");
assertTrue(condition, "失败消息");
assertNotNull(value, "失败消息");
assertThrows(Exception.class, () -> {...});
```

## 🐛 调试技巧

### 1. 查看日志
```java
@Test
void test() {
    // 添加日志输出
    logger.info("Task status: {}", task.getStatus());
}
```

### 2. 断点调试
在IDE中设置断点，运行单个测试方法。

### 3. 增加等待时间
```java
// 如果测试不稳定，增加等待时间
TimeUnit.SECONDS.sleep(5);
```

### 4. 检查Repository状态
```java
// 验证数据是否正确保存
Optional<TaskAggregate> saved = taskRepository.findById(taskId);
assertTrue(saved.isPresent(), "Task应该已保存");
```

## ⚠️ 注意事项

1. **数据隔离**：每个测试使用唯一的ID（tenant-xxx-001, tenant-xxx-002...）
2. **异步等待**：包含异步操作的测试需要适当等待
3. **清理逻辑**：BaseE2ETest会自动清理，但需要Repository支持clear()方法
4. **Spring上下文**：E2E测试会启动完整Spring上下文，较慢但更真实
5. **Mock vs Real**：E2E测试尽量使用真实组件，避免过度Mock

## 🔗 相关文档

- [测试工具指南](../testutil/README.md)
- [聚合根测试设计](../testutil/AGGREGATE_TEST_DESIGN.md)
- [单元测试指南](../unit/README.md)
- [集成测试指南](../integration/README.md)

## 📊 测试覆盖

E2E测试覆盖的关键路径：
- ✅ 新建部署完整流程
- ✅ 失败重试（从头/从Checkpoint）
- ✅ 回滚机制（成功/失败）
- ✅ 状态转换（所有关键状态）
- ✅ 业务不变式验证

## 🎓 最佳实践

1. **命名规范**：`should...When...` 或 `should...`
2. **测试结构**：Given-When-Then（准备-执行-验证）
3. **独立性**：每个测试独立运行，不依赖执行顺序
4. **可读性**：使用`@DisplayName`提供清晰的中文描述
5. **快速失败**：尽早验证前置条件
6. **清理资源**：利用BaseE2ETest的自动清理机制

---

**Created by**: T-023 测试体系重建  
**Last Updated**: 2025-11-28
