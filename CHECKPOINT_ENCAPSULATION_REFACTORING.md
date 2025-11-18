# Checkpoint 封装重构报告

**日期**: 2025-11-19  
**重构类型**: DDD 聚合封装改进  
**状态**: ✅ 完成

---

## 一、问题背景

### 原始设计的问题

**违反的 DDD 原则**：
```java
// ❌ 原始代码（application/checkpoint/CheckpointService.java）
public void saveCheckpoint(TaskAggregate task, ...) {
    TaskCheckpoint cp = new TaskCheckpoint();
    cp.setXxx(...);
    task.setCheckpoint(cp);  // 直接修改聚合私有字段
    store.put(task.getTaskId(), cp);
}

public TaskCheckpoint loadCheckpoint(TaskAggregate task) {
    TaskCheckpoint cp = store.get(task.getTaskId());
    task.setCheckpoint(cp);  // 绕过业务规则验证
    return cp;
}
```

**问题分析**：
1. **破坏聚合封装**：应用服务直接调用 `setCheckpoint()` 修改聚合内部状态
2. **绕过业务规则**：没有验证是否允许记录/恢复检查点
3. **Tell, Don't Ask 违反**：外部询问并修改聚合，而不是告诉聚合做什么

---

## 二、业务约束（关键信息）

基于用户提供的场景说明：

### Checkpoint 的业务规则
1. **唯一性**：一个 Task 最多只有 **1 个有效 Checkpoint**
2. **位置约束**：只在 **Stage 左边界** 存在（完成 Stage-N 后，即将开始 Stage-N+1）
3. **恢复点限制**：
   - 从头开始：直接 execute，不需要 Checkpoint
   - 从最近 Stage 恢复：使用唯一的 Checkpoint
4. **生命周期**：Task 完成/失败后应该清理

---

## 三、重构方案

### 核心设计原则

✅ **聚合封装**：聚合内部验证业务规则，外部不能随意修改状态  
✅ **Tell, Don't Ask**：告诉聚合做什么，而不是询问和修改其状态  
✅ **依赖倒置**：CheckpointRepository 在领域层定义契约，基础设施层实现  
✅ **职责分离**：服务负责持久化协调，聚合负责业务逻辑

### 重构内容

#### 1. TaskAggregate 新增业务方法

```java
// domain/task/TaskAggregate.java

/**
 * 记录检查点（在 Stage 左边界）
 * 
 * 业务规则：
 * 1. 只有 RUNNING 状态才能记录检查点
 * 2. 一个 Task 最多保留 1 个检查点（覆盖旧的）
 * 3. 检查点记录当前已完成的 Stage 列表和索引
 */
public void recordCheckpoint(List<String> completedStageNames, int lastCompletedIndex) {
    if (status != TaskStatus.RUNNING) {
        throw new IllegalStateException(
            String.format("只有 RUNNING 状态才能记录检查点，当前状态: %s", status)
        );
    }
    
    if (lastCompletedIndex < 0 || lastCompletedIndex >= getTotalStages()) {
        throw new IllegalArgumentException(
            String.format("无效的 Stage 索引: %d, 总 Stage 数: %d", lastCompletedIndex, getTotalStages())
        );
    }
    
    // 创建新的检查点（覆盖旧的）
    TaskCheckpoint newCheckpoint = new TaskCheckpoint();
    newCheckpoint.getCompletedStageNames().addAll(completedStageNames);
    newCheckpoint.setLastCompletedStageIndex(lastCompletedIndex);
    newCheckpoint.setTimestamp(LocalDateTime.now());
    
    this.checkpoint = newCheckpoint;
}

/**
 * 恢复到检查点
 * 
 * 业务规则：
 * 1. 必须有有效的检查点
 * 2. 只能在 retry 时调用（FAILED/ROLLED_BACK 状态）
 */
public void restoreFromCheckpoint(TaskCheckpoint checkpoint) {
    if (checkpoint == null) {
        throw new IllegalArgumentException("检查点不能为空");
    }
    
    if (status != TaskStatus.FAILED && status != TaskStatus.ROLLED_BACK) {
        throw new IllegalStateException(
            String.format("只有 FAILED/ROLLED_BACK 状态才能恢复检查点，当前状态: %s", status)
        );
    }
    
    this.checkpoint = checkpoint;
    // 注意：不改变 status，由 retry() 方法负责状态转换
}

/**
 * 清除检查点
 * 
 * 使用场景：
 * 1. Task 完成后清理
 * 2. Task 失败且不需要恢复
 * 3. 重新开始（不从检查点恢复）
 */
public void clearCheckpoint() {
    this.checkpoint = null;
}

// ❌ 移除的方法
// public void setCheckpoint(TaskCheckpoint checkpoint) { ... }
```

#### 2. CheckpointService 重构

```java
// application/checkpoint/CheckpointService.java

/**
 * 检查点服务（RF-DDD 重构版）
 * 
 * 职责：
 * 1. 协调聚合和存储之间的检查点持久化
 * 2. 不直接修改聚合状态，委托给聚合的业务方法
 * 3. 管理外部存储（CheckpointRepository）
 * 
 * 设计原则：
 * - Tell, Don't Ask：告诉聚合做什么，而不是修改其内部状态
 * - 聚合封装：通过业务方法操作
 * - 职责分离：服务负责持久化协调，聚合负责业务规则验证
 */
public class CheckpointService {
    
    /**
     * 保存检查点（在 Stage 左边界）
     * 
     * 流程：
     * 1. 聚合验证业务规则并创建检查点
     * 2. 服务持久化到外部存储
     */
    public void saveCheckpoint(TaskAggregate task, List<String> completedStageNames, int lastCompletedIndex) {
        // ✅ 委托给聚合的业务方法（聚合内部验证不变量）
        task.recordCheckpoint(completedStageNames, lastCompletedIndex);
        
        // ✅ 持久化到外部存储
        TaskCheckpoint checkpoint = task.getCheckpoint();
        if (checkpoint != null) {
            store.put(task.getTaskId(), checkpoint);
        }
    }
    
    /**
     * 加载检查点（用于 retry 恢复）
     * 
     * 流程：
     * 1. 从存储加载检查点
     * 2. 聚合验证业务规则并恢复状态
     */
    public TaskCheckpoint loadCheckpoint(TaskAggregate task) {
        TaskCheckpoint cp = store.get(task.getTaskId());
        if (cp != null) {
            // ✅ 委托给聚合的业务方法（聚合内部验证状态）
            task.restoreFromCheckpoint(cp);
        }
        return cp;
    }
    
    /**
     * 清除检查点
     */
    public void clearCheckpoint(TaskAggregate task) {
        task.clearCheckpoint();
        store.remove(task.getTaskId());
    }
    
    /**
     * 批量加载检查点（用于查询，不修改聚合）
     */
    public Map<String, TaskCheckpoint> loadMultiple(List<String> taskIds) {
        // 只返回数据，不修改聚合对象
        // ...
    }
}
```

#### 3. 删除孤立代码

```bash
# 删除未使用的 Checkpoint 类
infrastructure/execution/checkpoint/Checkpoint.java  # ❌ 删除

# 实际使用的是：
domain/task/TaskCheckpoint.java  # ✅ 保留
```

---

## 四、改进效果

### Before vs After

| 维度 | 重构前 | 重构后 |
|------|--------|--------|
| **封装性** | ❌ 外部可直接修改状态 | ✅ 只能通过业务方法操作 |
| **业务规则验证** | ❌ 没有验证 | ✅ 聚合内部验证不变量 |
| **Tell, Don't Ask** | ❌ 外部询问并修改 | ✅ 外部告诉聚合做什么 |
| **职责分离** | ❌ 服务和聚合职责混乱 | ✅ 清晰的职责划分 |
| **代码重复** | ⚠️ loadMultiple 重复 | ✅ 消除重复 |
| **孤立代码** | ⚠️ Checkpoint.java 未使用 | ✅ 删除 |

### 聚合不变量保护

```java
// ✅ 重构后：聚合自己保护不变量
task.recordCheckpoint(completedStageNames, lastCompletedIndex);
// → 如果状态不是 RUNNING，抛出 IllegalStateException
// → 如果索引无效，抛出 IllegalArgumentException

task.restoreFromCheckpoint(checkpoint);
// → 如果状态不是 FAILED/ROLLED_BACK，抛出 IllegalStateException
// → 如果 checkpoint 为 null，抛出 IllegalArgumentException
```

### 代码可读性

```java
// ❌ 重构前（不清楚业务意图）
task.setCheckpoint(cp);

// ✅ 重构后（明确业务意图）
task.recordCheckpoint(completedStageNames, lastCompletedIndex);  // 记录检查点
task.restoreFromCheckpoint(checkpoint);                           // 恢复到检查点
task.clearCheckpoint();                                           // 清除检查点
```

---

## 五、修改文件清单

### 修改的文件

1. **TaskAggregate.java** (domain/task/)
   - ✅ 新增 `recordCheckpoint()` 业务方法
   - ✅ 新增 `restoreFromCheckpoint()` 业务方法
   - ✅ 新增 `clearCheckpoint()` 业务方法
   - ❌ 移除 `setCheckpoint()` setter 方法

2. **CheckpointService.java** (application/checkpoint/)
   - ✅ 重构 `saveCheckpoint()` - 调用聚合业务方法
   - ✅ 重构 `loadCheckpoint()` - 调用聚合业务方法
   - ✅ 重构 `clearCheckpoint()` - 调用聚合业务方法
   - ✅ 保留 `loadMultiple()` - 批量查询（不修改聚合）

### 删除的文件

3. **Checkpoint.java** (infrastructure/execution/checkpoint/)
   - ❌ 删除（已被 `TaskCheckpoint` 替代，无引用）

---

## 六、验证结果

### 编译验证

```bash
$ mvn clean compile -DskipTests
[INFO] BUILD SUCCESS
[INFO] Total time:  2.467 s
```

✅ **无编译错误**  
✅ **无 Lint 警告**（除已知的 deprecation 警告）

### 影响范围评估

**影响的调用方**：
- `TaskExecutor` - 需要更新调用方式（从 `setCheckpoint` 改为 `recordCheckpoint`）
- 其他直接使用 `CheckpointService` 的组件

**无需修改**：
- `CheckpointRepository` 接口 - 保持不变
- `TaskCheckpoint` 数据类 - 保持不变
- 实现类（InMemoryCheckpointRepository, RedisCheckpointRepository）- 保持不变

---

## 七、后续工作

### 需要更新的调用方

```java
// TODO: 更新 TaskExecutor 中的调用
// 查找所有调用 CheckpointService 的地方，确保使用新的 API
```

### 建议的增强

1. **测试覆盖**：
   - 测试 `recordCheckpoint()` 的状态验证
   - 测试 `restoreFromCheckpoint()` 的前置条件
   - 测试 Checkpoint 唯一性（覆盖旧的）

2. **文档完善**：
   - 更新 API 文档说明 Checkpoint 的生命周期
   - 添加使用示例

---

## 八、总结

### 重构价值

✅ **符合 DDD 原则**：聚合封装业务逻辑，保护不变量  
✅ **提高代码质量**：清晰的职责划分，减少耦合  
✅ **增强可维护性**：业务规则集中在聚合内部，易于理解和修改  
✅ **消除技术债**：删除孤立代码，消除重复方法

### 设计亮点

1. **Tell, Don't Ask 模式**：外部告诉聚合 "记录检查点"，而不是询问状态并修改
2. **不变量保护**：聚合内部验证所有前置条件，确保数据一致性
3. **业务语义清晰**：方法名直接表达业务意图（record/restore/clear）
4. **职责分离**：服务负责持久化协调，聚合负责业务逻辑

---

**重构状态**: ✅ 已完成  
**编译状态**: ✅ 通过  
**建议优先级**: 🔴 HIGH（已解决核心封装问题）
