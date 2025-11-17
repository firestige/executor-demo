# DDD 架构重构进行中

**日期**: 2025-11-17  
**状态**: 🚧 重构中  

---

## ✅ 已完成

### 1. Repository 层创建
- ✅ `TaskRepository` 接口
- ✅ `PlanRepository` 接口
- ✅ `InMemoryTaskRepository` 实现
- ✅ `InMemoryPlanRepository` 实现

### 2. DTO 迁移（方案 A）
- ✅ `PlanInfo` → `domain/plan/`
- ✅ `TaskInfo` → `domain/task/`
- ✅ `PlanCreationResult` → `domain/plan/`
- ✅ `PlanOperationResult` → `domain/plan/`
- ✅ `TaskOperationResult` → `domain/task/`
- ✅ 删除旧的 DTO 文件

### 3. 测试文件处理
- ✅ `PlanApplicationServiceTest` - 注释说明，保留场景
- ✅ `TaskApplicationServiceTest` - 注释说明，保留场景
- ✅ `TaskApplicationServicePositiveFlowTest` - 注释说明
- ✅ `TaskApplicationServiceAdvancedTest` - 注释说明

---

## 🚧 进行中

### 4. 移动和重构 ApplicationService
- [x] **Phase 2.1**: 更新所有 DTO import 路径 ✅ (已提交)
- [ ] **Phase 2.2**: 重构为领域服务（详细计划见下）

---

## 📋 Phase 2.2 详细实施计划

### 总体策略
采用**渐进式重构**，分 6 个子步骤完成，每个步骤独立提交。

---

### Step 2.2.1: 创建 PlanDomainService 骨架 ✅
**目标**: 在 domain/plan 包下创建新的领域服务

**操作**:
1. 创建 `domain/plan/PlanDomainService.java`
2. 从 PlanApplicationService 复制代码
3. 更新类注释（说明职责变化）
4. 移除跨聚合协调逻辑（标记 TODO）
5. 保留原 PlanApplicationService（不删除）

**职责调整**:
- ❌ 原：创建 Plan + 创建 Task + 编排执行
- ✅ 新：只负责 Plan 聚合的创建、状态管理、生命周期操作

**预期成果**: 新文件编译通过，无破坏性变更 ✅ (已提交)

---

### Step 2.2.2: 创建 TaskDomainService 骨架 ✅
**目标**: 在 domain/task 包下创建新的领域服务

**操作**:
1. 创建 `domain/task/TaskDomainService.java`
2. 从 TaskApplicationService 复制代码
3. 更新类注释
4. 保留原 TaskApplicationService（不删除）

**职责调整**:
- ✅ 只负责 Task 聚合的创建、状态管理、执行管理

**预期成果**: 新文件编译通过 ✅ (已提交)

---

### Step 2.2.3: 重构领域服务使用 Repository ⏳
**目标**: 将 Map 替换为 Repository 调用

**PlanDomainService 改造**:
```java
// 原：Map<String, PlanAggregate> planRegistry
// 新：PlanRepository planRepository
// 11个参数 → 5个参数
```

**TaskDomainService 改造**:
```java
// 原：4个共享Map
// 新：TaskRepository taskRepository
// 10个参数 → 5个参数
```

**预期成果**: 领域服务使用 Repository，构造器参数减少

---

### Step 2.2.4: 创建 DeploymentApplicationService
**目标**: 创建真正的应用服务协调两个领域服务

**文件**: `application/DeploymentApplicationService.java`

**依赖**: (3个)
- PlanDomainService
- TaskDomainService  
- ValidationChain

**核心方法**:
```java
PlanCreationResult createDeploymentPlan(List<TenantConfig> configs)
// 1. 业务校验
// 2. 创建Plan（委托PlanDomainService）
// 3. 创建Tasks（循环调用TaskDomainService）
// 4. 启动Plan执行
// 5. 返回结果
```

**预期成果**: 清晰的应用层协调逻辑

---

### Step 2.2.5: 更新 Facade 和配置
**目标**: 系统使用新架构

**Facade 更新**:
```java
// 原：依赖 PlanApplicationService
// 新：依赖 DeploymentApplicationService
```

**Spring 配置**:
- 注册 Repository Beans
- 注册 DomainService Beans
- 注册 ApplicationService Bean
- 更新 Facade 依赖

**预期成果**: Facade使用新架构，系统可运行

---

### Step 2.2.6: 删除旧的 ApplicationService
**目标**: 清理旧代码

**删除**:
- application/PlanApplicationService.java
- application/TaskApplicationService.java

**预期成果**: 架构清晰，旧代码完全移除

---

## 🎯 执行检查点

每个 Step 完成后：
- [ ] 代码编译通过
- [ ] Git 提交
- [ ] 更新本文档
- [ ] 等待确认再继续

---

## 📊 最终架构预览

```
Facade Layer
  └─ DeploymentTaskFacade

Application Layer (新)
  └─ DeploymentApplicationService
      ├─ 协调 PlanDomainService
      └─ 协调 TaskDomainService

Domain Layer
  ├─ plan/PlanDomainService (新)
  └─ task/TaskDomainService (新)

Infrastructure Layer
  └─ Repository 实现
```

**关键改进**:
- ✅ 职责单一
- ✅ 依赖减少 (10-11个 → 5个)
- ✅ 可测试性提升
- ✅ 可扩展性增强

---

### 5-7. 其他步骤（已包含在 Phase 2.2 中）

---

## 📝 注意事项

1. **测试代码处理策略**
   - 原有测试全部标记为 @Deprecated
   - 保留详细的测试场景说明
   - 标注新架构下的测试路径
   - 待架构稳定后重新实现

2. **编译错误处理**
   - 暂时会有编译错误（import 路径变化）
   - 等所有文件迁移完成后统一修复

3. **职责变化**
   - `PlanDomainService`: 只管 Plan 聚合
   - `TaskDomainService`: 只管 Task 聚合
   - `DeploymentApplicationService`: 协调两个领域服务

---

## 🎯 下一步

继续执行重构步骤 4-7...

