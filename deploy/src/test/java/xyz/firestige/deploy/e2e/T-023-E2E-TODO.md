# T-023 E2E 测试待完成事项

## ✅ 已完成

1. **测试基础设施**
   - ✅ BaseE2ETest - 基类，加载Spring上下文
   - ✅ NewDeployTaskE2ETest - 新建切换任务测试框架
   - ✅ RetryDeployTaskE2ETest - 重试任务测试框架
   - ✅ RollbackDeployTaskE2ETest - 回滚任务测试框架（部分）
   - ✅ E2E测试README文档

2. **测试工具**
   - ✅ ValueObjectTestFactory - 值对象工厂
   - ✅ StageListTestFactory - Stage列表工厂
   - ✅ AggregateTestSupport - 反射工具
   - ✅ TaskAggregateTestBuilder - Task构建器
   - ✅ PlanAggregateTestBuilder - Plan构建器

## ⚠️ 待修复的API问题

### 1. TenantDeployConfigSnapshot构造
```java
// 当前问题：无参构造器不存在
TenantDeployConfigSnapshot prevSnapshot = new TenantDeployConfigSnapshot();

// 需要查看实际的构造签名：
// record TenantDeployConfigSnapshot(String, Long, Long, String, List<String>)
```

### 2. DeployVersion API
```java
// ✅ 已修复：改为DeployVersion.of(Long, Long)
// 不再支持：DeployVersion.of(String)
```

### 3. rollbackTaskByTenant参数
```java
// 需要确认实际方法签名
taskOperationService.rollbackTaskByTenant(tenantId, version);
```

## 📋 下一步工作

### Phase 1: 修复编译错误
1. 查看`TenantDeployConfigSnapshot`的实际构造方法
2. 查看`rollbackTaskByTenant`的实际参数
3. 修复所有E2E测试的编译错误

### Phase 2: 补充测试实现
1. 补充实际的异步执行逻辑
2. 添加轮询等待机制
3. 补充断言验证

### Phase 3: 集成测试
1. 运行E2E测试
2. 调试失败用例
3. 完善测试覆盖

## 🎯 优先级

- **P0 - 立即**：修复编译错误，让代码能够编译通过
- **P1 - 短期**：补充核心测试逻辑（新建、重试、回滚）
- **P2 - 中期**：添加边界case测试
- **P3 - 长期**：性能测试、压力测试

## 💡 建议

1. **分阶段实现**：先让测试编译通过，再逐步补充逻辑
2. **使用TODO注释**：标记待实现的部分
3. **Mock vs Real**：E2E测试应尽量使用真实组件
4. **异步处理**：考虑使用`CompletableFuture`或`CountDownLatch`

## 📝 API查询命令

```bash
# 查看TenantDeployConfigSnapshot定义
grep -r "class TenantDeployConfigSnapshot" deploy/

# 查看rollbackTaskByTenant签名
grep -r "rollbackTaskByTenant" deploy/src/main/

# 查看DeployVersion API
grep -r "public static DeployVersion" deploy/
```

---
**Created**: 2025-11-28  
**Status**: In Progress  
**Related**: T-023 测试体系重建
