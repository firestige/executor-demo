# RF-09 简化方案：实用的 Repository 重构

**决策**: 不引入复杂的 CQRS 和读写分离，采用简化的实用设计

---

## 一、为什么不需要复杂设计？

### 1.1 项目现状

✅ **当前情况**:
- 单体应用，内存/Redis 存储
- 中小规模，租户级任务
- 读写比例相对均衡
- 查询需求简单明确

❌ **不适用 CQRS 的场景**:
- 海量数据（百万级以上）
- 极高读写比（100:1 以上）
- 复杂查询（多维度、全文搜索）
- 分布式系统读写分离

### 1.2 过度设计的代价

如果引入完整的 CQRS：
- ❌ 增加 QueryService、CommandService 两套接口
- ❌ 增加实现复杂度（20%+ 代码量）
- ❌ 增加维护成本
- ❌ 增加学习曲线
- ❌ 性能提升不明显（内存存储已经很快）

---

## 二、简化方案：清理 Repository 接口

### 2.1 核心原则

1. **Repository 只管理聚合根**
   - Task 聚合的完整生命周期
   - 不暴露 Stages、Context、Executor（这些应该是聚合的内部细节）

2. **保持简单查询方法**
   - findById (主键查询)
   - findByTenantId (业务查询)
   - findByPlanId (业务查询)
   - 不需要单独的 QueryService

3. **聚合根包含所有必要信息**
   - Task 聚合内部持有 stages
   - Task 聚合内部持有 context
   - 不需要分开存储和查询

### 2.2 重构目标

**Before（当前问题）**:
```java
public interface TaskRepository {
    void save(TaskAggregate task);
    TaskAggregate get(String taskId);
    
    // ❌ 问题：暴露聚合内部细节
    void saveStages(String taskId, List<TaskStage> stages);
    List<TaskStage> getStages(String taskId);
    void saveContext(String taskId, TaskRuntimeContext context);
    TaskRuntimeContext getContext(String taskId);
    void saveExecutor(String taskId, TaskExecutor executor);
    TaskExecutor getExecutor(String taskId);
    
    // ❌ 问题：Repository 不应该管理运行时标志
    void requestPause(String taskId);
    void clearPause(String taskId);
    boolean isPauseRequested(String taskId);
}
```

**After（简化方案）**:
```java
public interface TaskRepository {
    // 命令方法
    void save(TaskAggregate task);  // 保存整个聚合
    void remove(String taskId);
    
    // 查询方法
    Optional<TaskAggregate> findById(String taskId);
    Optional<TaskAggregate> findByTenantId(String tenantId);
    List<TaskAggregate> findByPlanId(String planId);
}

// ✅ 运行时状态管理独立
public interface TaskRuntimeRepository {
    void saveExecutor(String taskId, TaskExecutor executor);
    Optional<TaskExecutor> getExecutor(String taskId);
    void saveContext(String taskId, TaskRuntimeContext context);
    Optional<TaskRuntimeContext> getContext(String taskId);
    void remove(String taskId);
}
```

---

## 三、实施步骤

### Step 1: 重构 TaskRepository 接口（30 分钟）
- 简化接口方法
- 移除 Stages、Context 的单独存取
- 改为 Optional 返回值

### Step 2: 创建 TaskRuntimeRepository（30 分钟）
- 专门管理运行时状态（Executor、Context）
- 与持久化的聚合分离

### Step 3: 更新实现类（1 小时）
- InMemoryTaskRepository
- 未来的 RedisTaskRepository

### Step 4: 更新调用方（1 小时）
- TaskDomainService
- DeploymentApplicationService

---

## 四、收益分析

### 4.1 简化收益 ✅

| 维度 | 改进前 | 改进后 | 收益 |
|------|--------|--------|------|
| 接口方法数 | 15+ | 5 (TaskRepo) + 4 (RuntimeRepo) | -40% |
| 职责清晰度 | ⚠️ 混杂 | ✅ 分离 | 明确 |
| 实现复杂度 | 高 | 中 | 降低 |
| 可维护性 | ⚠️ 一般 | ✅ 好 | 提升 |

### 4.2 符合 DDD 原则 ✅

- ✅ Repository 只管理聚合根
- ✅ 不暴露聚合内部结构
- ✅ 简单的查询方法足够
- ✅ 未来可扩展（需要时再引入 CQRS）

---

## 五、何时需要引入 CQRS？

### 5.1 触发条件

考虑引入 CQRS 当满足以下**任意 2 个**条件：

1. **数据量大**
   - 单表数据 > 100 万
   - 需要复杂索引优化

2. **读写比例极端**
   - 读:写 > 100:1
   - 写操作成为瓶颈

3. **复杂查询需求**
   - 多维度查询
   - 全文搜索
   - 聚合统计

4. **分布式部署**
   - 读库、写库分离
   - 多副本读优化

### 5.2 渐进式演进

```
Phase 1: 简化 Repository（当前）
  └─ 接口清晰，职责单一

Phase 2: 引入缓存（如果需要）
  └─ 读操作加缓存层

Phase 3: 读写分离（如果需要）
  └─ 添加 QueryService
  └─ 读写使用不同数据源

Phase 4: 完整 CQRS（如果需要）
  └─ 事件溯源
  └─ 最终一致性
```

---

## 六、总结

**结论**: 你的项目**不需要**复杂的 CQRS 和读写分离

**推荐方案**: RF-09 简化为"清理 Repository 接口"
- 简化接口方法
- 分离运行时状态管理
- 保持实用主义

**预计时间**: 3-4 小时（而非 1 天）

**收益**:
- ✅ 接口更清晰
- ✅ 职责更单一
- ✅ 易于实现和维护
- ✅ 符合 DDD 原则
- ✅ 保持灵活性（未来可扩展）

---

**决策**: 采用简化方案，避免过度设计！

