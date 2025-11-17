# RF-12: 调度策略扩展设计（精简版）

**创建日期**: 2025-11-18  
**状态**: 设计阶段

---

## 一、策略对比

### 细粒度策略（Fine-Grained）- 默认

**行为**：
- ✅ 创建时：不检查冲突，总是允许创建
- ⚠️ 启动时：跳过冲突租户的任务，其他任务正常执行
- ✅ 并发能力：高

**场景示例**：
```
Plan-A: 租户 1,2,3 (运行中)
Plan-B: 租户 3,4,5 (尝试创建)

结果：
✅ Plan-B 创建成功
✅ Plan-B 启动成功
✅ 租户 4,5 正常执行
⚠️ 租户 3 被跳过（冲突）
```

---

### 粗粒度策略（Coarse-Grained）

**行为**：
- ❌ 创建时：检查租户冲突，有任何重叠租户则**立即拒绝创建**
- ✅ 无冲突：允许创建和并发执行
- ⚠️ 创建失败率较高

**场景示例 1 - 有租户重叠（拒绝创建）**：
```
Plan-A: 租户 1,2,3 (运行中)
Plan-B: 租户 3,4,5 (尝试创建)

时间线：
T1: createPlan(Plan-B)
    ├─ canCreatePlan([3,4,5])
    ├─ conflictRegistry.hasConflict(3) → true ❌
    └─ 返回 false，立即拒绝创建

结果：
❌ Plan-B 创建失败
📋 错误信息："租户冲突: [租户3]"
💡 用户需要：等待 Plan-A 完成，或修改 Plan-B 移除租户3
```

**场景示例 2 - 无租户重叠（允许并发）**：
```
Plan-A: 租户 1,2,3 (运行中)
Plan-C: 租户 4,5,6 (尝试创建)

时间线：
T1: createPlan(Plan-C)
    ├─ canCreatePlan([4,5,6])
    ├─ conflictRegistry.hasConflict(4,5,6) → 全部 false ✅
    └─ 返回 true，允许创建

T2: startPlan(Plan-C)
    └─ 租户 4,5,6 并发执行

结果：
✅ Plan-C 创建成功
✅ Plan-C 与 Plan-A 并发执行
✅ 租户完全隔离，无冲突
```

---

## 二、配置方式

```yaml
executor:
  scheduling:
    # 调度策略：FINE_GRAINED（细粒度，默认）或 COARSE_GRAINED（粗粒度）
    strategy: FINE_GRAINED  # 默认
```

---

## 三、实现要点

### 3.1 CoarseGrainedSchedulingStrategy

```java
public class CoarseGrainedSchedulingStrategy implements PlanSchedulingStrategy {
    private final ConflictRegistry conflictRegistry;
    
    @Override
    public boolean canCreatePlan(List<String> tenantIds) {
        // 粗粒度策略：创建前检查所有租户
        List<String> conflictTenants = new ArrayList<>();
        for (String tenantId : tenantIds) {
            if (conflictRegistry.hasConflict(tenantId)) {
                conflictTenants.add(tenantId);
            }
        }
        
        if (!conflictTenants.isEmpty()) {
            log.warn("拒绝创建 Plan，冲突租户: {}", conflictTenants);
            return false;  // 立即拒绝，不等待
        }
        
        return true;
    }
    
    @Override
    public boolean canStartPlan(String planId, List<String> tenantIds) {
        // 启动时再次检查（双重保险）
        return canCreatePlan(tenantIds);
    }
    
    // ...其他方法
}
```

### 3.2 应用服务集成

```java
@Transactional
public PlanCreationResult createDeploymentPlan(List<TenantConfig> configs) {
    List<String> tenantIds = extractTenantIds(configs);
    
    // 策略检查（纯内存操作，< 1ms）
    if (!schedulingStrategy.canCreatePlan(tenantIds)) {
        // 找出冲突租户
        List<String> conflictTenants = tenantIds.stream()
            .filter(tid -> conflictRegistry.hasConflict(tid))
            .collect(Collectors.toList());
        
        return PlanCreationResult.failure(
            FailureInfo.of(ErrorType.CONFLICT, 
                "租户冲突: " + conflictTenants),
            "请等待相关 Plan 完成或移除冲突租户后重试"
        );
    }
    
    // 继续创建流程...
    PlanCreationContext context = deploymentPlanCreator.createPlan(configs);
    return PlanCreationResult.success(context.getPlanInfo());
}
```

---

## 四、事务影响

| 策略 | 冲突时行为 | 事务耗时 | 数据库影响 |
|------|-----------|---------|-----------|
| 细粒度 | 创建成功，启动时跳过 | ~50ms | 正常写入 |
| 粗粒度（无冲突） | 创建成功 | ~50ms | 正常写入 |
| 粗粒度（有冲突） | **立即拒绝** | < 1ms | 事务回滚，无写入 |

**关键点**：
- ✅ 粗粒度策略的检查是纯内存操作（ConflictRegistry）
- ✅ 有冲突时立即返回 false，事务快速回滚
- ✅ 不占用数据库连接，不阻塞其他事务
- ✅ 无重叠租户的 Plan 可以并发执行

---

## 五、对比总结

| 特性 | 细粒度策略 | 粗粒度策略 |
|------|-----------|-----------|
| **冲突检测时机** | 启动时 | 创建时 |
| **冲突处理方式** | 跳过冲突租户任务 | **立即拒绝整个 Plan** |
| **并发能力** | 高（只要租户不冲突） | 中（无重叠租户可并发） |
| **创建失败率** | 低（总能创建） | 较高（有冲突就失败） |
| **用户体验** | 部分任务可能被跳过 | 要么全执行，要么全拒绝 |
| **适用场景** | 生产环境（高吞吐） | 严格租户隔离场景 |

**关键区别**：
```
假设 Plan-A (租户 1,2,3) 正在运行
场景：创建 Plan-B (租户 3,4,5)

细粒度策略：
✅ 创建成功 → 启动成功 → 租户4,5执行，租户3跳过

粗粒度策略：
❌ 创建失败（租户3冲突）→ 无法启动
💡 用户需要等待 Plan-A 完成或移除租户3
```

---

## 六、配置类

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
    public PlanSchedulingStrategy coarseGrainedStrategy(ConflictRegistry conflictRegistry) {
        log.info("启用粗粒度调度策略（Coarse-Grained）");
        return new CoarseGrainedSchedulingStrategy(conflictRegistry);
    }
}
```

---

## 七、下一步

1. ✅ 接口已创建（PlanSchedulingStrategy）
2. ✅ 细粒度策略已创建（FineGrainedSchedulingStrategy）
3. 🔄 需要修改粗粒度策略实现（CoarseGrainedSchedulingStrategy）
   - 删除 AtomicReference<String> runningPlanId（全局锁）
   - 改为检查 ConflictRegistry（租户冲突）
4. 🔄 集成到 DeploymentApplicationService
5. 🔄 编写配置类（SchedulingStrategyConfiguration）
6. 🔄 编写集成测试

---

_创建日期: 2025-11-18 by GitHub Copilot_

