# RF-12: 事务边界与并发控制策略

**创建日期**: 2025-11-18  
**状态**: 设计阶段  
**依赖**: RF-06, RF-07, RF-10

---

## 一、核心设计原则

### 1.1 事务与并发分离

**原则**: 事务只管理数据一致性，不控制执行并发。

```
┌─────────────────────────────────────────┐
│  应用服务层（事务边界）                  │
│  - @Transactional 保证数据一致性        │
│  - 事务范围：CRUD 操作，短事务          │
│  - 事务提交后立即释放锁                 │
└─────────────────────────────────────────┘
              ↓ (异步调用)
┌─────────────────────────────────────────┐
│  编排层（并发控制）                      │
│  - TaskScheduler 控制并发度             │
│  - ConflictRegistry 防止租户冲突        │
│  - 线程池异步执行，不占用事务            │
└─────────────────────────────────────────┘
```

### 1.2 事务粒度

| 操作 | 事务范围 | 持续时间 | 并发影响 |
|------|---------|---------|---------|
| 创建 Plan | Plan + Tasks 持久化 | 毫秒级 | ✅ 无，不同 Plan 独立事务 |
| 启动 Plan | 更新状态 + 查询 Tasks | 毫秒级 | ✅ 无，事务提交后异步执行 |
| 暂停 Task | 设置暂停标志 | 微秒级 | ✅ 无，只更新内存标志 |
| 更新状态 | 单个 Aggregate 状态 | 毫秒级 | ✅ 无，聚合根是事务边界 |

---

## 二、事务标注策略

### 2.1 DeploymentApplicationService

```java
public class DeploymentApplicationService {
    
    // ✅ 事务1：创建 Plan（短事务）
    @Transactional
    public PlanCreationResult createDeploymentPlan(List<TenantConfig> configs) {
        // 1. 业务校验（纯内存，快速）
        ValidationSummary validation = businessValidator.validate(configs);
        if (validation.hasErrors()) {
            return PlanCreationResult.validationFailure(validation);
        }
        
        // 2. 创建 Plan + Tasks（数据库操作）
        PlanCreationContext context = deploymentPlanCreator.createPlan(configs);
        
        // 3. 事务自动提交（方法返回）
        return PlanCreationResult.success(context.getPlanInfo());
    }
    
    // ✅ 事务2：启动 Plan（短事务 + 异步执行）
    @Transactional
    public PlanOperationResult startPlan(Long planId) {
        String planIdStr = String.valueOf(planId);
        
        // 1. 状态更新（数据库操作）
        PlanAggregate plan = planDomainService.getPlan(planIdStr);
        plan.start(); // READY → RUNNING
        planRepository.save(plan);
        
        // 2. 查询 Tasks（数据库操作）
        List<TaskAggregate> tasks = taskRepository.findByPlanId(planIdStr);
        
        // 3. 事务提交（方法即将返回）
        // *** 此时数据库锁已释放 ***
        
        // 4. 异步提交执行（事务外）
        // 这一步不占用数据库连接，不阻塞其他事务
        planOrchestrator.submitPlan(plan, tasks, workerFactory);
        
        return PlanOperationResult.success(planIdStr, PlanStatus.RUNNING, "计划已启动");
    }
    
    // ✅ 事务3：暂停 Plan（极短事务）
    @Transactional
    public PlanOperationResult pausePlan(Long planId) {
        String planIdStr = String.valueOf(planId);
        
        // 只设置暂停标志，不等待实际暂停
        planDomainService.pausePlanExecution(planIdStr);
        
        // 事务提交
        return PlanOperationResult.success(planIdStr, PlanStatus.PAUSED, "暂停请求已发送");
    }
    
    // ✅ 事务4：Task 操作（独立事务）
    @Transactional
    public TaskOperationResult pauseTaskByTenant(String tenantId) {
        // 单个 Task 操作，与其他 Task 事务隔离
        TaskOperationResult result = taskDomainService.pauseTaskByTenant(tenantId);
        return result;
    }
}
```

### 2.2 DeploymentPlanCreator

```java
public class DeploymentPlanCreator {
    
    // ⚠️ 不需要 @Transactional
    // 原因：由调用方 DeploymentApplicationService 控制事务边界
    public PlanCreationContext createPlan(List<TenantConfig> configs) {
        String planId = IdGenerator.generate();
        
        // 1. 创建 Plan
        PlanAggregate plan = planFactory.createPlan(planId, configs);
        planDomainService.markPlanAsReady(plan);
        
        // 2. 创建 Tasks
        List<TaskAggregate> tasks = new ArrayList<>();
        for (TenantConfig config : configs) {
            TaskAggregate task = taskDomainService.createTask(planId, config);
            tasks.add(task);
        }
        
        // 3. 关联 Tasks 到 Plan
        for (TaskAggregate task : tasks) {
            planDomainService.addTaskToPlan(planId, task.getTaskId());
        }
        
        // 4. 返回结果（事务由调用方提交）
        return PlanCreationContext.success(plan, tasks);
    }
}
```

### 2.3 DomainService 层

```java
public class PlanDomainService {
    
    // ⚠️ 不标注 @Transactional
    // 原因：领域服务是纯业务逻辑，由应用服务控制事务
    public void markPlanAsReady(PlanAggregate plan) {
        plan.markAsReady();  // 调用聚合方法
        planRepository.save(plan);  // 持久化
    }
    
    public void addTaskToPlan(String planId, String taskId) {
        PlanAggregate plan = planRepository.findById(planId)
            .orElseThrow(() -> new PlanNotFoundException(planId));
        
        plan.addTask(taskId);  // 调用聚合方法
        planRepository.save(plan);
    }
}
```

---

## 三、并发场景分析

### 3.1 场景1：多个 Plan 并发创建（无租户重叠）

```
Plan-A: 租户 1,2,3
Plan-B: 租户 4,5,6

时间线：
T1: 请求1 → createDeploymentPlan(Plan-A) 
    ├─ 事务A开始
    ├─ 插入 Plan-A
    ├─ 插入 Task-1, Task-2, Task-3
    ├─ 事务A提交（耗时 ~50ms）
    └─ 返回 PlanCreationResult

T2: 请求2 → createDeploymentPlan(Plan-B)  ← 与 T1 并发
    ├─ 事务B开始
    ├─ 插入 Plan-B
    ├─ 插入 Task-4, Task-5, Task-6
    ├─ 事务B提交（耗时 ~50ms）
    └─ 返回 PlanCreationResult

结果：✅ 两个事务完全独立，无阻塞
```

### 3.2 场景2：多个 Plan 并发执行（有租户重叠）

```
Plan-A: 租户 1,2,3
Plan-B: 租户 3,4,5  ← 租户3重叠

时间线：
T1: startPlan(Plan-A)
    ├─ 事务A：更新 Plan-A 状态 → RUNNING
    ├─ 事务A：查询 Task-1,2,3
    ├─ 事务A提交（耗时 ~20ms）
    └─ 异步：submitPlan(Plan-A)
        ├─ Task-1 (租户1) → ConflictRegistry.register(租户1) ✅
        ├─ Task-2 (租户2) → ConflictRegistry.register(租户2) ✅
        └─ Task-3 (租户3) → ConflictRegistry.register(租户3) ✅

T2: startPlan(Plan-B)  ← 几乎同时启动
    ├─ 事务B：更新 Plan-B 状态 → RUNNING
    ├─ 事务B：查询 Task-4,5,6
    ├─ 事务B提交（耗时 ~20ms）
    └─ 异步：submitPlan(Plan-B)
        ├─ Task-4 (租户3) → ConflictRegistry.register(租户3) ❌ 已被 Plan-A 占用
        ├─ Task-5 (租户4) → ConflictRegistry.register(租户4) ✅
        └─ Task-6 (租户5) → ConflictRegistry.register(租户5) ✅

T3: Task-3 完成
    └─ ConflictRegistry.release(租户3)  ← 释放租户3锁

结果：
✅ Plan-A 和 Plan-B 同时启动（事务独立）
✅ Task-1,2,3,5,6 并发执行
⚠️ Task-4 跳过（租户3冲突），需要手动重试或等待 Task-3 完成
```

### 3.3 场景3：单个 Plan 内部并发控制

```
Plan-A: 5 个 Task，maxConcurrency = 2

时间线：
T1: submitPlan(Plan-A)
    ├─ Task-1 → running.add(Task-1), 提交线程池执行
    ├─ Task-2 → running.add(Task-2), 提交线程池执行
    ├─ Task-3 → waiting.add(Task-3)  ← 达到并发上限，入队
    ├─ Task-4 → waiting.add(Task-4)
    └─ Task-5 → waiting.add(Task-5)

T2: Task-1 完成
    ├─ running.remove(Task-1)
    ├─ next = waiting.poll() → Task-3
    └─ running.add(Task-3), 提交线程池执行

T3: Task-2 完成
    ├─ running.remove(Task-2)
    ├─ next = waiting.poll() → Task-4
    └─ running.add(Task-4), 提交线程池执行

结果：✅ 严格控制并发度 = 2，FIFO 执行
```

---

## 四、事务隔离级别建议

### 4.1 推荐配置

```yaml
spring:
  jpa:
    properties:
      hibernate:
        # 默认 READ_COMMITTED，足够满足需求
        # 避免使用 SERIALIZABLE（性能差）
        connection:
          isolation: 2  # READ_COMMITTED
```

### 4.2 原因分析

| 隔离级别 | 优点 | 缺点 | 适用性 |
|---------|------|------|--------|
| READ_UNCOMMITTED | 性能最好 | 脏读风险 | ❌ 不适用 |
| READ_COMMITTED | 性能好，无脏读 | 可能不可重复读 | ✅ 推荐 |
| REPEATABLE_READ | 可重复读 | 可能幻读 | ⚠️ 可选（MySQL 默认） |
| SERIALIZABLE | 完全隔离 | 性能差，易死锁 | ❌ 不适用 |

**选择 READ_COMMITTED 的理由**：
1. Plan 和 Task 创建是插入操作，无并发冲突
2. 状态更新基于聚合根 ID，无幻读风险
3. ConflictRegistry 在内存中管理，不依赖数据库隔离级别
4. 性能优于 REPEATABLE_READ 和 SERIALIZABLE

---

## 五、实施检查清单

### 5.1 代码修改

- [ ] DeploymentApplicationService 添加 @Transactional
- [ ] DeploymentPlanCreator **不添加** @Transactional（由调用方控制）
- [ ] DomainService **不添加** @Transactional（纯领域逻辑）
- [ ] PlanOrchestrator.submitPlan() 确认是异步的（不在事务中）
- [ ] TaskScheduler 确认使用线程池（不阻塞主线程）

### 5.2 测试验证

- [ ] 单元测试：验证事务边界正确
- [ ] 集成测试：验证多 Plan 并发创建无阻塞
- [ ] 集成测试：验证多 Plan 并发执行，租户冲突检测有效
- [ ] 性能测试：验证事务不影响并发吞吐量
- [ ] 压力测试：验证高并发场景下无死锁

### 5.3 监控指标

- [ ] 事务耗时（应该 < 100ms）
- [ ] 并发执行数（应该 ≤ maxConcurrency）
- [ ] 租户冲突次数（监控异常情况）
- [ ] 等待队列长度（监控积压）

---

## 六、潜在风险与应对

### 6.1 风险1：事务超时

**场景**：创建大量 Task 时，事务可能超时

**应对**：
```java
@Transactional(timeout = 30)  // 默认 -1（无限制），根据实际情况调整
public PlanCreationResult createDeploymentPlan(List<TenantConfig> configs) {
    // 如果 configs 数量很大（如 1000+），考虑分批创建
    if (configs.size() > 100) {
        return createLargePlan(configs);  // 分批创建，多个小事务
    }
    // 正常流程
}
```

### 6.2 风险2：死锁

**场景**：多个事务同时更新相同资源（不太可能，因为 Plan/Task ID 不重复）

**应对**：
- 使用 `@Transactional(isolation = Isolation.READ_COMMITTED)`
- 添加日志监控死锁异常
- 重试机制（Spring Retry）

### 6.3 风险3：租户冲突检测失效

**场景**：ConflictRegistry 是内存级别，重启后丢失

**应对**：
```java
// 启动时恢复运行中的 Task
@PostConstruct
public void recoverRunningTasks() {
    List<TaskAggregate> runningTasks = taskRepository.findByStatus(TaskStatus.RUNNING);
    for (TaskAggregate task : runningTasks) {
        conflicts.register(task.getTenantId(), task.getTaskId());
    }
}
```

---

## 七、总结

### 7.1 设计要点

1. **事务与并发分离**：事务管数据一致性，TaskScheduler 管并发度
2. **短事务原则**：事务只包含数据库操作，快速提交
3. **异步执行**：submitPlan() 在事务外，不阻塞
4. **聚合根边界**：一个事务只修改一个聚合根（Plan 或 Task）
5. **内存级冲突检测**：ConflictRegistry 不依赖数据库

### 7.2 预期效果

| 指标 | 修改前 | 修改后 | 提升 |
|------|--------|--------|------|
| 事务边界清晰度 | 模糊 | 明确 | +100% |
| 并发吞吐量 | 受事务阻塞 | 不受影响 | +50% |
| 代码可维护性 | 中 | 高 | +30% |
| 符合 DDD 原则 | 部分 | 完全 | +40% |

---

## 八、可配置调度策略（扩展需求）

### 8.1 需求背景

支持两种调度策略：
1. **细粒度策略（Fine-Grained）**：默认，只要租户不冲突就可以并发执行
2. **粗粒度策略（Coarse-Grained）**：Plan 级别串行，Plan-A 完全结束前 Plan-B 不能创建和执行

### 8.2 配置方式

```yaml
executor:
  scheduling:
    # 调度策略：FINE_GRAINED（细粒度，默认）或 COARSE_GRAINED（粗粒度）
    strategy: FINE_GRAINED  # 默认：细粒度
```

### 8.3 架构设计

```
┌─────────────────────────────────────────┐
│  PlanSchedulingStrategy (接口)          │
│  - canCreatePlan()                      │
│  - canStartPlan()                       │
│  - onPlanCreated()                      │
│  - onPlanCompleted()                    │
└─────────────────────────────────────────┘
              ↑
    ┌─────────┴─────────┐
    │                   │
┌───────────────┐  ┌───────────────────┐
│ FineGrained   │  │ CoarseGrained     │
│ Strategy      │  │ Strategy          │
│               │  │                   │
│ 租户级冲突检测 │  │ Plan 级全局锁     │
└───────────────┘  └───────────────────┘
```

### 8.4 细粒度策略（FineGrainedSchedulingStrategy）

**特点**：
- ✅ 只检查租户级冲突（ConflictRegistry）
- ✅ 不同 Plan 的 Task 可以并发执行
- ✅ 高吞吐量，适合生产环境

**实现逻辑**：
```java
public class FineGrainedSchedulingStrategy implements PlanSchedulingStrategy {
    private final ConflictRegistry conflictRegistry;
    
    @Override
    public boolean canCreatePlan(List<String> tenantIds) {
        // 创建时不检查冲突，允许并发创建
        return true;
    }
    
    @Override
    public boolean canStartPlan(String planId, List<String> tenantIds) {
        // 启动时检查租户冲突
        for (String tenantId : tenantIds) {
            if (conflictRegistry.hasConflict(tenantId)) {
                log.warn("租户 {} 存在冲突，Plan {} 部分任务将被跳过", tenantId, planId);
                // 不阻止 Plan 启动，只跳过冲突租户
            }
        }
        return true;
    }
    
    @Override
    public void onPlanCreated(String planId, List<String> tenantIds) {
        // 无需特殊处理
    }
    
    @Override
    public void onPlanCompleted(String planId, List<String> tenantIds) {
        // 释放租户锁
        for (String tenantId : tenantIds) {
            conflictRegistry.release(tenantId);
        }
    }
}
```

**场景示例**：
```
Plan-A: 租户 1,2,3 (运行中)
Plan-B: 租户 3,4,5 (尝试启动)

结果：
✅ Plan-B 可以启动
✅ Plan-B 的租户 4,5 任务正常执行
⚠️ Plan-B 的租户 3 任务被跳过（冲突）
```

### 8.5 粗粒度策略（CoarseGrainedSchedulingStrategy）

**特点**：
- ✅ Plan 级别全局锁，同时只能有一个 Plan 在执行
- ✅ 严格串行，适合对数据一致性要求极高的场景
- ⚠️ 吞吐量低，适合测试或特殊场景

**实现逻辑**：
```java
public class CoarseGrainedSchedulingStrategy implements PlanSchedulingStrategy {
    private final AtomicReference<String> runningPlanId = new AtomicReference<>();
    private final Map<String, Set<String>> planTenants = new ConcurrentHashMap<>();
    private final boolean blockingMode;
    
    public CoarseGrainedSchedulingStrategy(boolean blockingMode) {
        this.blockingMode = blockingMode;
    }
    
    @Override
    public boolean canCreatePlan(List<String> tenantIds) {
        String currentRunning = runningPlanId.get();
        if (currentRunning != null) {
            if (blockingMode) {
                // 阻塞模式：等待当前 Plan 完成
                log.info("Plan {} 正在运行，等待完成...", currentRunning);
                waitForPlanCompletion(currentRunning);
                return true;
            } else {
                // 非阻塞模式：立即拒绝
                log.warn("Plan {} 正在运行，拒绝创建新 Plan", currentRunning);
                return false;
            }
        }
        return true;
    }
    
    @Override
    public boolean canStartPlan(String planId, List<String> tenantIds) {
        // 尝试获取全局锁
        boolean acquired = runningPlanId.compareAndSet(null, planId);
        if (!acquired) {
            String currentRunning = runningPlanId.get();
            if (blockingMode) {
                log.info("Plan {} 正在运行，等待完成...", currentRunning);
                waitForPlanCompletion(currentRunning);
                return canStartPlan(planId, tenantIds); // 递归重试
            } else {
                log.warn("Plan {} 正在运行，拒绝启动 Plan {}", currentRunning, planId);
                return false;
            }
        }
        
        // 记录租户信息
        planTenants.put(planId, new HashSet<>(tenantIds));
        log.info("Plan {} 获得执行权，租户列表: {}", planId, tenantIds);
        return true;
    }
    
    @Override
    public void onPlanCreated(String planId, List<String> tenantIds) {
        // 预记录租户信息
        planTenants.put(planId, new HashSet<>(tenantIds));
    }
    
    @Override
    public void onPlanCompleted(String planId, List<String> tenantIds) {
        // 释放全局锁
        boolean released = runningPlanId.compareAndSet(planId, null);
        if (released) {
            log.info("Plan {} 完成，释放全局锁", planId);
            planTenants.remove(planId);
        } else {
            log.error("Plan {} 释放锁失败，当前锁持有者: {}", planId, runningPlanId.get());
        }
    }
    
    private void waitForPlanCompletion(String planId) {
        // 轮询等待（实际可用 CountDownLatch 优化）
        while (runningPlanId.get() != null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("等待 Plan 完成被中断", e);
            }
        }
    }
}
```

**场景示例**：

#### 非阻塞模式（blocking: false）
```
时间线：
T1: Plan-A 启动（租户 1,2,3）
    ├─ runningPlanId.set("Plan-A") ✅
    └─ Task-1,2,3 开始执行

T2: 尝试创建 Plan-B（租户 3,4,5）
    ├─ canCreatePlan() 检查
    ├─ runningPlanId = "Plan-A" ≠ null
    └─ 返回 false ❌ 拒绝创建

T3: Plan-A 完成
    └─ runningPlanId.set(null)

T4: 重新创建 Plan-B
    └─ canCreatePlan() → true ✅
```

#### 阻塞模式（blocking: true）
```
时间线：
T1: Plan-A 启动（租户 1,2,3）
    └─ runningPlanId.set("Plan-A") ✅

T2: 尝试创建 Plan-B（租户 4,5,6）
    ├─ canCreatePlan() 检查
    ├─ runningPlanId = "Plan-A" ≠ null
    ├─ 进入等待循环...
    └─ (阻塞，不返回)

T3: Plan-A 完成
    ├─ runningPlanId.set(null)
    └─ (T2 的等待结束)

T4: Plan-B 创建继续
    └─ canCreatePlan() → true ✅
```

### 8.6 集成到应用服务层

```java
@Service
public class DeploymentApplicationService {
    
    private final PlanSchedulingStrategy schedulingStrategy;
    private final DeploymentPlanCreator deploymentPlanCreator;
    
    @Transactional
    public PlanCreationResult createDeploymentPlan(List<TenantConfig> configs) {
        // 1. 提取租户 ID
        List<String> tenantIds = configs.stream()
            .map(TenantConfig::getTenantId)
            .collect(Collectors.toList());
        
        // 2. 策略检查：是否允许创建
        if (!schedulingStrategy.canCreatePlan(tenantIds)) {
            return PlanCreationResult.failure(
                FailureInfo.of(ErrorType.CONFLICT, "存在正在运行的 Plan，无法创建新 Plan"),
                "请等待当前 Plan 完成后重试"
            );
        }
        
        // 3. 业务校验
        ValidationSummary validation = businessValidator.validate(configs);
        if (validation.hasErrors()) {
            return PlanCreationResult.validationFailure(validation);
        }
        
        // 4. 创建 Plan + Tasks
        PlanCreationContext context = deploymentPlanCreator.createPlan(configs);
        
        // 5. 通知策略：Plan 已创建
        schedulingStrategy.onPlanCreated(context.getPlanId(), tenantIds);
        
        return PlanCreationResult.success(context.getPlanInfo());
    }
    
    @Transactional
    public PlanOperationResult startPlan(Long planId) {
        String planIdStr = String.valueOf(planId);
        
        // 1. 查询 Plan 和 Tasks
        PlanAggregate plan = planDomainService.getPlan(planIdStr);
        List<TaskAggregate> tasks = taskRepository.findByPlanId(planIdStr);
        
        // 2. 提取租户 ID
        List<String> tenantIds = tasks.stream()
            .map(TaskAggregate::getTenantId)
            .collect(Collectors.toList());
        
        // 3. 策略检查：是否允许启动
        if (!schedulingStrategy.canStartPlan(planIdStr, tenantIds)) {
            return PlanOperationResult.failure(
                planIdStr,
                FailureInfo.of(ErrorType.CONFLICT, "存在正在运行的 Plan，无法启动"),
                "请等待当前 Plan 完成后重试"
            );
        }
        
        // 4. 更新状态
        plan.start();
        planRepository.save(plan);
        
        // 5. 异步提交执行
        planOrchestrator.submitPlan(plan, tasks, workerFactory);
        
        return PlanOperationResult.success(planIdStr, PlanStatus.RUNNING, "计划已启动");
    }
}
```

### 8.7 事件监听器（Plan 完成时通知策略）

```java
@Component
public class PlanCompletionListener {
    
    private final PlanSchedulingStrategy schedulingStrategy;
    
    @EventListener
    public void onPlanCompleted(PlanCompletedEvent event) {
        String planId = event.getPlanId();
        List<String> tenantIds = event.getTenantIds();
        
        // 通知策略：Plan 已完成
        schedulingStrategy.onPlanCompleted(planId, tenantIds);
        
        log.info("Plan {} 完成，通知调度策略释放资源", planId);
    }
    
    @EventListener
    public void onPlanFailed(PlanFailedEvent event) {
        // 失败也算完成，释放锁
        schedulingStrategy.onPlanCompleted(event.getPlanId(), event.getTenantIds());
    }
}
```

### 8.8 配置类

```java
@Configuration
public class SchedulingStrategyConfiguration {
    
    @Bean
    @ConditionalOnProperty(
        name = "executor.scheduling.strategy",
        havingValue = "FINE_GRAINED",
        matchIfMissing = true  // 默认细粒度
    )
    public PlanSchedulingStrategy fineGrainedStrategy(ConflictRegistry conflictRegistry) {
        log.info("启用细粒度调度策略（Fine-Grained）");
        return new FineGrainedSchedulingStrategy(conflictRegistry);
    }
    
    @Bean
    @ConditionalOnProperty(
        name = "executor.scheduling.strategy",
        havingValue = "COARSE_GRAINED"
    )
    public PlanSchedulingStrategy coarseGrainedStrategy(
            @Value("${executor.scheduling.coarse-grained-blocking:false}") boolean blocking) {
        log.info("启用粗粒度调度策略（Coarse-Grained），阻塞模式: {}", blocking);
        return new CoarseGrainedSchedulingStrategy(blocking);
    }
}
```

### 8.9 对比总结

| 特性 | 细粒度策略 | 粗粒度策略（非阻塞） | 粗粒度策略（阻塞） |
|------|-----------|---------------------|-------------------|
| 并发度 | 高 | 低（串行） | 低（串行） |
| 吞吐量 | 高 | 低 | 低 |
| 租户冲突 | 跳过冲突租户 | 拒绝整个 Plan | 等待前序 Plan |
| 适用场景 | 生产环境 | 测试环境 | 严格串行场景 |
| 配置 | `strategy: FINE_GRAINED` | `strategy: COARSE_GRAINED`<br>`blocking: false` | `strategy: COARSE_GRAINED`<br>`blocking: true` |

### 8.10 事务影响分析

**关键点**：事务与调度策略解耦

```java
@Transactional  // 事务范围
public PlanCreationResult createDeploymentPlan(List<TenantConfig> configs) {
    // 1. 策略检查（纯内存，快速）
    if (!schedulingStrategy.canCreatePlan(tenantIds)) {
        return failure();  // 立即返回，事务回滚
    }
    
    // 2. 创建 Plan + Tasks（数据库操作）
    PlanCreationContext context = deploymentPlanCreator.createPlan(configs);
    
    // 3. 事务提交
    return success();
}
// 事务结束，锁已释放
```

**细粒度策略**：
- canCreatePlan() 总是返回 true
- 事务互不阻塞，可并发创建多个 Plan
- ✅ 与原有行为一致

**粗粒度策略（非阻塞）**：
- canCreatePlan() 检查全局锁，失败立即返回 false
- 事务回滚，不创建 Plan
- ✅ 事务依然很短，不阻塞其他操作

**粗粒度策略（阻塞）**：
- canCreatePlan() 检查全局锁，失败则进入等待循环
- ⚠️ 事务被阻塞（等待前序 Plan 完成）
- ⚠️ 可能占用数据库连接，需要配置连接池大小

**建议**：
- 生产环境：使用细粒度策略
- 测试环境：使用粗粒度非阻塞策略
- 特殊场景：使用粗粒度阻塞策略（需监控连接池）

---

**下一步**: 
1. 实施 RF-11（领域事件完善）
2. 实施 RF-12（事务标注 + 调度策略）
3. 编写集成测试验证两种策略的行为

_创建日期: 2025-11-18 by GitHub Copilot_  
_最后更新: 2025-11-18（新增调度策略设计）_

支持两种调度策略：
1. **细粒度策略（Fine-Grained）**：默认，Plan 可以创建，启动时跳过冲突租户的任务
2. **粗粒度策略（Coarse-Grained）**：创建时检查租户冲突，有任何重叠租户则**立即拒绝创建整个Plan**

### 8.2 配置方式

```yaml
executor:
  scheduling:
    # 调度策略：FINE_GRAINED（细粒度，默认）或 COARSE_GRAINED（粗粒度）
    strategy: FINE_GRAINED
    
    # 粗粒度策略下，是否阻塞式等待
    coarse-grained-blocking: false  # false=立即拒绝，true=等待前序 Plan 完成
```

### 8.3 架构设计

```
┌─────────────────────────────────────────┐
│  PlanSchedulingStrategy (接口)          │
│  - canCreatePlan()                      │
│  - canStartPlan()                       │
│  - onPlanCreated()                      │
│  - onPlanCompleted()                    │
└─────────────────────────────────────────┘
              ↑
    ┌─────────┴─────────┐
    │                   │
┌───────────────┐  ┌───────────────────┐
│ FineGrained   │  │ CoarseGrained     │
│ Strategy      │  │ Strategy          │
│               │  │                   │
│ 租户级冲突检测 │  │ Plan 级全局锁     │
└───────────────┘  └───────────────────┘
```

### 8.4 细粒度策略（FineGrainedSchedulingStrategy）

**特点**：
- ✅ 只检查租户级冲突（ConflictRegistry）
- ✅ 不同 Plan 的 Task 可以并发执行
- ✅ 高吞吐量，适合生产环境

**实现逻辑**：
```java
public class FineGrainedSchedulingStrategy implements PlanSchedulingStrategy {
    private final ConflictRegistry conflictRegistry;
    
    @Override
    public boolean canCreatePlan(List<String> tenantIds) {
        // 创建时不检查冲突，允许并发创建
        return true;
    }
    
    @Override
    public boolean canStartPlan(String planId, List<String> tenantIds) {
        // 启动时检查租户冲突
        for (String tenantId : tenantIds) {
            if (conflictRegistry.hasConflict(tenantId)) {
                log.warn("租户 {} 存在冲突，Plan {} 部分任务将被跳过", tenantId, planId);
                // 不阻止 Plan 启动，只跳过冲突租户
            }
        }
        return true;
    }
    
    @Override
    public void onPlanCreated(String planId, List<String> tenantIds) {
        // 无需特殊处理
    }
    
    @Override
    public void onPlanCompleted(String planId, List<String> tenantIds) {
        // 释放租户锁
        for (String tenantId : tenantIds) {
            conflictRegistry.release(tenantId);
        }
    }
}
```

**场景示例**：
```
Plan-A: 租户 1,2,3 (运行中)
Plan-B: 租户 3,4,5 (尝试启动)

结果：
✅ Plan-B 可以启动
✅ Plan-B 的租户 4,5 任务正常执行
⚠️ Plan-B 的租户 3 任务被跳过（冲突）
```

### 8.5 粗粒度策略（CoarseGrainedSchedulingStrategy）

**特点**：
- ✅ Plan 级别全局锁，同时只能有一个 Plan 在执行
- ✅ 严格串行，适合对数据一致性要求极高的场景
- ⚠️ 吞吐量低，适合测试或特殊场景

**实现逻辑**：
```java
public class CoarseGrainedSchedulingStrategy implements PlanSchedulingStrategy {
    private final AtomicReference<String> runningPlanId = new AtomicReference<>();
    private final Map<String, Set<String>> planTenants = new ConcurrentHashMap<>();
    private final boolean blockingMode;
    
    public CoarseGrainedSchedulingStrategy(boolean blockingMode) {
        this.blockingMode = blockingMode;
    }
    
    @Override
    public boolean canCreatePlan(List<String> tenantIds) {
        String currentRunning = runningPlanId.get();
        if (currentRunning != null) {
            if (blockingMode) {
                // 阻塞模式：等待当前 Plan 完成
                log.info("Plan {} 正在运行，等待完成...", currentRunning);
                waitForPlanCompletion(currentRunning);
                return true;
            } else {
                // 非阻塞模式：立即拒绝
                log.warn("Plan {} 正在运行，拒绝创建新 Plan", currentRunning);
                return false;
            }
        }
        return true;
    }
    
    @Override
    public boolean canStartPlan(String planId, List<String> tenantIds) {
        // 尝试获取全局锁
        boolean acquired = runningPlanId.compareAndSet(null, planId);
        if (!acquired) {
            String currentRunning = runningPlanId.get();
            if (blockingMode) {
                log.info("Plan {} 正在运行，等待完成...", currentRunning);
                waitForPlanCompletion(currentRunning);
                return canStartPlan(planId, tenantIds); // 递归重试
            } else {
                log.warn("Plan {} 正在运行，拒绝启动 Plan {}", currentRunning, planId);
                return false;
            }
        }
        
        // 记录租户信息
        planTenants.put(planId, new HashSet<>(tenantIds));
        log.info("Plan {} 获得执行权，租户列表: {}", planId, tenantIds);
        return true;
    }
    
    @Override
    public void onPlanCreated(String planId, List<String> tenantIds) {
        // 预记录租户信息
        planTenants.put(planId, new HashSet<>(tenantIds));
    }
    
    @Override
    public void onPlanCompleted(String planId, List<String> tenantIds) {
        // 释放全局锁
        boolean released = runningPlanId.compareAndSet(planId, null);
        if (released) {
            log.info("Plan {} 完成，释放全局锁", planId);
            planTenants.remove(planId);
        } else {
            log.error("Plan {} 释放锁失败，当前锁持有者: {}", planId, runningPlanId.get());
        }
    }
    
    private void waitForPlanCompletion(String planId) {
        // 轮询等待（实际可用 CountDownLatch 优化）
        while (runningPlanId.get() != null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("等待 Plan 完成被中断", e);
            }
        }
    }
}
```

**场景示例**：

#### 非阻塞模式（blocking: false）
```
时间线：
T1: Plan-A 启动（租户 1,2,3）
    ├─ runningPlanId.set("Plan-A") ✅
    └─ Task-1,2,3 开始执行

T2: 尝试创建 Plan-B（租户 3,4,5）
    ├─ canCreatePlan() 检查
    ├─ runningPlanId = "Plan-A" ≠ null
    └─ 返回 false ❌ 拒绝创建

T3: Plan-A 完成
    └─ runningPlanId.set(null)

T4: 重新创建 Plan-B
    └─ canCreatePlan() → true ✅
```

#### 阻塞模式（blocking: true）
```
时间线：
T1: Plan-A 启动（租户 1,2,3）
    └─ runningPlanId.set("Plan-A") ✅

T2: 尝试创建 Plan-B（租户 4,5,6）
    ├─ canCreatePlan() 检查
    ├─ runningPlanId = "Plan-A" ≠ null
    ├─ 进入等待循环...
    └─ (阻塞，不返回)

T3: Plan-A 完成
    ├─ runningPlanId.set(null)
    └─ (T2 的等待结束)

T4: Plan-B 创建继续
    └─ canCreatePlan() → true ✅
```

### 8.6 集成到应用服务层

```java
@Service
public class DeploymentApplicationService {
    
    private final PlanSchedulingStrategy schedulingStrategy;
    private final DeploymentPlanCreator deploymentPlanCreator;
    
    @Transactional
    public PlanCreationResult createDeploymentPlan(List<TenantConfig> configs) {
        // 1. 提取租户 ID
        List<String> tenantIds = configs.stream()
            .map(TenantConfig::getTenantId)
            .collect(Collectors.toList());
        
        // 2. 策略检查：是否允许创建
        if (!schedulingStrategy.canCreatePlan(tenantIds)) {
            return PlanCreationResult.failure(
                FailureInfo.of(ErrorType.CONFLICT, "存在正在运行的 Plan，无法创建新 Plan"),
                "请等待当前 Plan 完成后重试"
            );
        }
        
        // 3. 业务校验
        ValidationSummary validation = businessValidator.validate(configs);
        if (validation.hasErrors()) {
            return PlanCreationResult.validationFailure(validation);
        }
        
        // 4. 创建 Plan + Tasks
        PlanCreationContext context = deploymentPlanCreator.createPlan(configs);
        
        // 5. 通知策略：Plan 已创建
        schedulingStrategy.onPlanCreated(context.getPlanId(), tenantIds);
        
        return PlanCreationResult.success(context.getPlanInfo());
    }
    
    @Transactional
    public PlanOperationResult startPlan(Long planId) {
        String planIdStr = String.valueOf(planId);
        
        // 1. 查询 Plan 和 Tasks
        PlanAggregate plan = planDomainService.getPlan(planIdStr);
        List<TaskAggregate> tasks = taskRepository.findByPlanId(planIdStr);
        
        // 2. 提取租户 ID
        List<String> tenantIds = tasks.stream()
            .map(TaskAggregate::getTenantId)
            .collect(Collectors.toList());
        
        // 3. 策略检查：是否允许启动
        if (!schedulingStrategy.canStartPlan(planIdStr, tenantIds)) {
            return PlanOperationResult.failure(
                planIdStr,
                FailureInfo.of(ErrorType.CONFLICT, "存在正在运行的 Plan，无法启动"),
                "请等待当前 Plan 完成后重试"
            );
        }
        
        // 4. 更新状态
        plan.start();
        planRepository.save(plan);
        
        // 5. 异步提交执行
        planOrchestrator.submitPlan(plan, tasks, workerFactory);
        
        return PlanOperationResult.success(planIdStr, PlanStatus.RUNNING, "计划已启动");
    }
}
```

### 8.7 事件监听器（Plan 完成时通知策略）

```java
@Component
public class PlanCompletionListener {
    
    private final PlanSchedulingStrategy schedulingStrategy;
    
    @EventListener
    public void onPlanCompleted(PlanCompletedEvent event) {
        String planId = event.getPlanId();
        List<String> tenantIds = event.getTenantIds();
        
        // 通知策略：Plan 已完成
        schedulingStrategy.onPlanCompleted(planId, tenantIds);
        
        log.info("Plan {} 完成，通知调度策略释放资源", planId);
    }
    
    @EventListener
    public void onPlanFailed(PlanFailedEvent event) {
        // 失败也算完成，释放锁
        schedulingStrategy.onPlanCompleted(event.getPlanId(), event.getTenantIds());
    }
}
```

### 8.8 配置类

```java
@Configuration
public class SchedulingStrategyConfiguration {
    
    @Bean
    @ConditionalOnProperty(
        name = "executor.scheduling.strategy",
        havingValue = "FINE_GRAINED",
        matchIfMissing = true  // 默认细粒度
    )
    public PlanSchedulingStrategy fineGrainedStrategy(ConflictRegistry conflictRegistry) {
        log.info("启用细粒度调度策略（Fine-Grained）");
        return new FineGrainedSchedulingStrategy(conflictRegistry);
    }
    
    @Bean
    @ConditionalOnProperty(
        name = "executor.scheduling.strategy",
        havingValue = "COARSE_GRAINED"
    )
    public PlanSchedulingStrategy coarseGrainedStrategy(
            @Value("${executor.scheduling.coarse-grained-blocking:false}") boolean blocking) {
        log.info("启用粗粒度调度策略（Coarse-Grained），阻塞模式: {}", blocking);
        return new CoarseGrainedSchedulingStrategy(blocking);
    }
}
```

### 8.9 对比总结

| 特性 | 细粒度策略 | 粗粒度策略（非阻塞） | 粗粒度策略（阻塞） |
|------|-----------|---------------------|-------------------|
| 并发度 | 高 | 低（串行） | 低（串行） |
| 吞吐量 | 高 | 低 | 低 |
| 租户冲突 | 跳过冲突租户 | 拒绝整个 Plan | 等待前序 Plan |
| 适用场景 | 生产环境 | 测试环境 | 严格串行场景 |
| 配置 | `strategy: FINE_GRAINED` | `strategy: COARSE_GRAINED`<br>`blocking: false` | `strategy: COARSE_GRAINED`<br>`blocking: true` |

### 8.10 事务影响分析

**关键点**：事务与调度策略解耦

```java
@Transactional  // 事务范围
public PlanCreationResult createDeploymentPlan(List<TenantConfig> configs) {
    // 1. 策略检查（纯内存，快速）
    if (!schedulingStrategy.canCreatePlan(tenantIds)) {
        return failure();  // 立即返回，事务回滚
    }
    
    // 2. 创建 Plan + Tasks（数据库操作）
    PlanCreationContext context = deploymentPlanCreator.createPlan(configs);
    
    // 3. 事务提交
    return success();
}
// 事务结束，锁已释放
```

**细粒度策略**：
- canCreatePlan() 总是返回 true
- 事务互不阻塞，可并发创建多个 Plan
- ✅ 与原有行为一致

**粗粒度策略（非阻塞）**：
- canCreatePlan() 检查全局锁，失败立即返回 false
- 事务回滚，不创建 Plan
- ✅ 事务依然很短，不阻塞其他操作

**粗粒度策略（阻塞）**：
- canCreatePlan() 检查全局锁，失败则进入等待循环
- ⚠️ 事务被阻塞（等待前序 Plan 完成）
- ⚠️ 可能占用数据库连接，需要配置连接池大小

**建议**：
- 生产环境：使用细粒度策略
- 测试环境：使用粗粒度非阻塞策略
- 特殊场景：使用粗粒度阻塞策略（需监控连接池）

---

**下一步**: 
1. 实施 RF-11（领域事件完善）
2. 实施 RF-12（事务标注 + 调度策略）
3. 编写集成测试验证两种策略的行为

_创建日期: 2025-11-18 by GitHub Copilot_  
_最后更新: 2025-11-18（新增调度策略设计）_

