# 聚合根测试方案：封装性 vs 可测试性

> **设计困境**：聚合根需要保持封装性（不暴露setter），但测试需要构造中间状态  
> **解决方案**：反射 + 测试专用工具类

---

## 一、问题背景

### 1.1 DDD的封装原则
```java
public class TaskAggregate {
    private TaskStatus status;
    private StageProgress stageProgress;
    
    // ✅ 业务行为暴露
    public void start() { ... }
    public void pause() { ... }
    
    // ❌ 不应暴露setter（破坏封装）
    // public void setStatus(TaskStatus status) { ... }
    // public void setStageProgress(StageProgress progress) { ... }
}
```

**封装的价值**：
- 保护业务不变式（如：只有PENDING状态才能start）
- 防止外部直接修改内部状态
- 强制通过业务方法改变状态

### 1.2 测试的需求
```java
// 测试场景：验证"已完成2个Stage的RUNNING任务可以暂停"
@Test
void testPauseRunningTask() {
    // ❌ 问题：如何构造"已完成2个Stage"的状态？
    TaskAggregate task = ???;  // 需要RUNNING状态 + completedStages=2
    
    task.pause();
    
    assertEquals(TaskStatus.PAUSED, task.getStatus());
}
```

**测试困境**：
- 不暴露setter → 无法直接构造中间状态
- 通过业务方法构造 → 需要mock大量依赖、执行完整流程
- 测试变得复杂、脆弱

---

## 二、常见方案对比

| 方案 | 优点 | 缺点 | 评分 |
|------|------|------|------|
| **1. 暴露所有setter** | 测试简单 | 完全破坏封装，生产代码也可调用 | ❌ 0/10 |
| **2. 包级私有setter** | 部分封装 | 同包测试可用，但仍可被滥用 | ⚠️ 3/10 |
| **3. Builder模式** | 优雅，链式调用 | 需要大量Builder代码，难以覆盖所有状态组合 | ⚠️ 5/10 |
| **4. 测试专用构造器** | 封装性好 | 测试构造器参数过多，难以维护 | ⚠️ 6/10 |
| **5. 反射 + 测试工具类** | 封装性完美，测试灵活 | 反射性能低（但测试可接受），需要维护字段名 | ✅ 9/10 |

---

## 三、推荐方案：反射 + 测试工具类

### 3.1 设计原则

```
┌─────────────────────────────────────┐
│   生产代码（domain包）               │
│                                     │
│  ✅ 聚合根不暴露setter               │
│  ✅ 仅暴露业务方法                   │
│  ✅ 保护业务不变式                   │
└─────────────────────────────────────┘
                ↓
┌─────────────────────────────────────┐
│   测试代码（testutil包）             │
│                                     │
│  ✅ 通过反射注入状态                 │
│  ✅ 仅测试代码可用                   │
│  ✅ 不影响生产封装性                 │
└─────────────────────────────────────┘
```

### 3.2 实现：AggregateTestSupport

```java
/**
 * 聚合测试支持类
 * 
 * 使用场景：
 * - 构造特定状态的聚合用于测试
 * - 绕过业务规则直接设置内部状态
 * - 仅在测试代码中使用，生产代码禁用
 */
public class AggregateTestSupport {
    
    /**
     * 通过反射设置TaskAggregate的字段
     * 
     * 警告：仅用于测试，绕过了聚合的封装和不变式保护
     */
    public static void setTaskField(TaskAggregate task, String fieldName, Object value) {
        try {
            Field field = TaskAggregate.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(task, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }
    
    // 便捷方法
    public static void setDeployVersion(TaskAggregate task, DeployVersion version) {
        setTaskField(task, "deployVersion", version);
    }
    
    public static void initializeTaskStages(TaskAggregate task, List<TaskStage> stages) {
        StageProgress progress = StageProgress.initial(stages);
        setTaskField(task, "stageProgress", progress);
    }
}
```

### 3.3 使用示例

```java
@Test
void testPauseRunningTask() {
    // 1. 创建聚合（最小构造）
    TaskAggregate task = new TaskAggregate(
        ValueObjectTestFactory.randomTaskId(),
        ValueObjectTestFactory.randomPlanId(),
        ValueObjectTestFactory.tenantId("tenant-001")
    );
    
    // 2. 通过测试工具注入状态
    List<TaskStage> stages = StageListTestFactory.threeSuccessStages();
    AggregateTestSupport.initializeTaskStages(task, stages, 2);  // 已完成2个Stage
    AggregateTestSupport.setTaskField(task, "status", TaskStatus.RUNNING);
    
    // 3. 执行业务方法
    task.pause();
    
    // 4. 验证结果
    assertEquals(TaskStatus.PAUSED, task.getStatus());
}
```

### 3.4 优化：TaskAggregateTestBuilder集成

```java
public class TaskAggregateTestBuilder {
    
    public TaskAggregate buildRunning(int completedStages) {
        TaskAggregate task = new TaskAggregate(taskId, planId, tenantId);
        
        // 初始化必要字段
        AggregateTestSupport.setDeployVersion(task, deployVersion);
        AggregateTestSupport.setDeployUnitName(task, deployUnitName);
        
        // 设置Stages
        List<TaskStage> stages = StageListTestFactory.successStages(totalStages);
        AggregateTestSupport.initializeTaskStages(task, stages, completedStages);
        
        // 设置状态（绕过start()的前置检查）
        AggregateTestSupport.setTaskField(task, "status", TaskStatus.RUNNING);
        
        return task;
    }
}
```

---

## 四、方案优势

### 4.1 完美的封装性 ✅
```java
// 生产代码
public class TaskAggregate {
    private TaskStatus status;  // ✅ 私有字段，无setter
    
    public void start() {
        // ✅ 强制业务规则
        if (status != TaskStatus.PENDING) {
            throw new IllegalStateException("...");
        }
        this.status = TaskStatus.RUNNING;
    }
}

// 外部调用
TaskAggregate task = ...;
// task.setStatus(...)  // ❌ 编译错误！无法直接修改
task.start();           // ✅ 必须通过业务方法
```

### 4.2 灵活的测试性 ✅
```java
// 测试代码
@Test
void testComplexScenario() {
    TaskAggregate task = new TaskAggregate(...);
    
    // ✅ 可以构造任意中间状态
    AggregateTestSupport.setTaskField(task, "status", TaskStatus.RUNNING);
    AggregateTestSupport.initializeTaskStages(task, stages, 5);
    AggregateTestSupport.setTaskField(task, "pauseRequested", true);
    
    // 测试特定场景
    task.checkPauseAndPause();
}
```

### 4.3 清晰的边界 ✅
```
生产代码（domain）：
  ✅ 完全封装
  ✅ 业务规则保护
  ✅ 不依赖测试工具

测试代码（testutil）：
  ✅ 通过反射突破封装
  ✅ 仅测试环境可用
  ✅ 不污染生产代码
```

### 4.4 架构简洁性 ✅
```
❌ 不需要：
  - 额外的Builder类（for 测试）
  - 包级私有setter
  - 测试专用构造器

✅ 只需要：
  - 一个AggregateTestSupport工具类
  - 清晰的测试意图表达
```

---

## 五、注意事项

### 5.1 反射的限制
```java
// ⚠️ 字段名硬编码，重构时需要同步更新
AggregateTestSupport.setTaskField(task, "status", TaskStatus.RUNNING);
//                                        ^^^^^^ 字符串，IDE无法重构

// ✅ 解决方案：集中在AggregateTestSupport中，便于统一维护
public static void setStatus(TaskAggregate task, TaskStatus status) {
    setTaskField(task, "status", status);  // 字段名集中管理
}
```

### 5.2 性能考虑
```java
// 反射性能较低，但在测试中完全可接受
@Test
void performanceTest() {
    long start = System.currentTimeMillis();
    
    for (int i = 0; i < 10000; i++) {
        TaskAggregate task = new TaskAggregate(...);
        AggregateTestSupport.setTaskField(task, "status", TaskStatus.RUNNING);
    }
    
    long duration = System.currentTimeMillis() - start;
    // 通常 < 100ms，测试环境完全可接受
}
```

### 5.3 测试代码职责
```java
// ✅ 好的实践：测试工具仅用于构造状态
@Test
void testBusinessLogic() {
    // 1. 使用测试工具构造状态
    TaskAggregate task = TaskAggregateTestBuilder.buildRunning(2);
    
    // 2. 执行真实的业务方法
    task.completeStage("stage-2");
    
    // 3. 验证业务规则
    assertEquals(3, task.getStageProgress().getCurrentStageIndex());
}

// ❌ 坏的实践：测试工具用于绕过业务逻辑
@Test
void badTest() {
    TaskAggregate task = ...;
    
    // ❌ 直接修改状态，跳过业务规则
    AggregateTestSupport.setTaskField(task, "status", TaskStatus.COMPLETED);
    // 这样测试没有验证业务逻辑！
}
```

---

## 六、总结

### 6.1 方案评估

| 维度 | 评分 | 说明 |
|------|------|------|
| **封装性** | ⭐⭐⭐⭐⭐ | 生产代码完全封装，无setter暴露 |
| **可测试性** | ⭐⭐⭐⭐⭐ | 通过反射可构造任意状态 |
| **代码简洁性** | ⭐⭐⭐⭐⭐ | 仅需一个工具类，无额外复杂度 |
| **维护成本** | ⭐⭐⭐⭐ | 字段名硬编码需要维护 |
| **性能** | ⭐⭐⭐⭐ | 反射性能低但测试可接受 |
| **架构清晰度** | ⭐⭐⭐⭐⭐ | 测试/生产边界清晰 |

**综合评分：9.5/10** ✅

### 6.2 关键决策

✅ **采用此方案的理由**：
1. 完美平衡封装性和可测试性
2. 不破坏DDD原则和聚合边界
3. 测试代码清晰、灵活
4. 架构简洁，无额外复杂度
5. 符合"测试复杂度反映架构质量"的原则

❌ **不采用setter的理由**：
1. 破坏封装性
2. 生产代码可滥用
3. 无法保护业务不变式
4. 违反DDD设计原则

### 6.3 使用指南

**测试代码应该：**
- ✅ 使用AggregateTestSupport构造中间状态
- ✅ 执行真实的业务方法
- ✅ 验证业务规则和不变式

**测试代码不应该：**
- ❌ 直接在测试中使用反射（应封装到工具类）
- ❌ 用反射绕过业务逻辑进行"快捷"测试
- ❌ 在生产代码中引用AggregateTestSupport

---

## 七、文件清单

```
deploy/src/test/java/xyz/firestige/deploy/testutil/factory/
├── AggregateTestSupport.java           # ✅ 核心：反射工具类
├── TaskAggregateTestBuilder.java      # 集成反射工具
├── PlanAggregateTestBuilder.java      # 集成反射工具
└── ValueObjectTestFactory.java        # 值对象工厂
```

**这个方案证明了当前架构的优秀设计：即使不暴露setter，测试依然简洁明了！** 🎉
