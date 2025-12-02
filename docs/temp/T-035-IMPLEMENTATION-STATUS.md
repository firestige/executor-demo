# T-035 实施状态报告

> **日期**: 2025-12-02  
> **任务**: T-035 Facade 集成 - 打通 Retry/Rollback 调用链路  
> **状态**: 部分完成（需要 TaskDomainService 适配）

---

## ✅ 已完成的修改

### 1. DeploymentTaskFacade.java ✅
**文件路径**: `deploy/src/main/java/xyz/firestige/deploy/facade/DeploymentTaskFacade.java`

**修改内容**:
- ✅ 更新类注释，明确 Facade 是纯胶水层
- ✅ 修改 `retryTask()` 方法签名：
  - 移除 `taskId` 参数
  - 方法签名：`retryTask(TenantDeployConfig, String lastCompletedStageName)`
- ✅ 修改 `rollbackTask()` 方法签名：
  - 修改参数类型：`String version` 替代 `Long version`
  - 方法签名：`rollbackTask(TenantDeployConfig, String lastCompletedStageName, String version)`
- ✅ 两个方法都只做 DTO 转换和委派

**设计要点**:
- 纯胶水层：只做转换和委派
- 不生成 taskId，不调用 TaskRecoveryService
- 委派给 TaskOperationService 处理业务逻辑

---

### 2. TaskOperationService.java ✅
**文件路径**: `deploy/src/main/java/xyz/firestige/deploy/application/task/TaskOperationService.java`

**修改内容**:
- ✅ 重命名 `retryTaskByTenant()` → `retryTask()`
- ✅ 修改 `retryTask()` 方法签名：
  - 移除 `taskId` 参数
  - 方法签名：`retryTask(TenantConfig, String lastCompletedStageName)`
  - lastCompletedStageName 为 null 时从头重试
- ✅ 重命名 `rollbackTaskByTenant()` → `rollbackTask()`
- ✅ 修改 `rollbackTask()` 方法签名：
  - 添加 `lastCompletedStageName` 参数
  - 方法签名：`rollbackTask(TenantConfig, String lastCompletedStageName, String version)`
  - lastCompletedStageName 为 null 时全部回滚
- ✅ 修复 TenantId 类型错误（`config.getTenantId()` 已返回 TenantId 对象）
- ✅ 修复 `requestRetry()` 调用（传入 boolean 而不是 String）

**设计要点**:
- 应用层服务，负责业务编排
- 异步执行，立即返回
- 调用 TaskDomainService 准备执行上下文

---

### 3. TaskDomainService.java ✅
**文件路径**: `deploy/src/main/java/xyz/firestige/deploy/domain/task/TaskDomainService.java`

**修改内容**:
- ✅ 添加 `prepareRetry(TenantConfig, String)` 方法
- ✅ 添加 `prepareRollback(TenantConfig, String, String)` 方法
- ✅ 添加私有辅助方法 `calculateStartIndex()` 计算起始索引

**实现要点**:
- 根据 config.getTenantId() 查找现有 Task
- 使用 StageFactory 重建 stages
- lastCompletedStageName 为 null 时从头执行
- 返回 TaskWorkerCreationContext 供应用层使用
- 回滚使用旧版本配置重新执行 stages（不是逆向）

---

## ⏳ 待完成的工作

### 1. 单元测试

**需要编写的测试**:

- [ ] DeploymentTaskFacade.retryTask() 测试
- [ ] DeploymentTaskFacade.rollbackTask() 测试
- [ ] TaskOperationService.retryTask() 测试
- [ ] TaskOperationService.rollbackTask() 测试
- [ ] TaskDomainService.prepareRetry() 测试
- [ ] TaskDomainService.prepareRollback() 测试
- [ ] TaskDomainService.calculateStartIndex() 测试

---

### 2. 集成测试

**需要验证的场景**:

- [ ] 重试场景：lastCompletedStageName 为 null
- [ ] 重试场景：lastCompletedStageName 指定有效 stage
- [ ] 重试场景：lastCompletedStageName 指定的 stage 不存在
- [ ] 回滚场景：使用旧版本配置正向执行
- [ ] 回滚场景：lastCompletedStageName 为 null
- [ ] 事件发布和监听机制
- [ ] 完整调用链路：Facade → TaskOperationService → TaskDomainService → StageFactory

---

## 📋 实施检查清单

- [x] 修改 DeploymentTaskFacade.retryTask()
- [x] 修改 DeploymentTaskFacade.rollbackTask()
- [x] 修改 TaskOperationService.retryTask()
- [x] 修改 TaskOperationService.rollbackTask()
- [x] 添加 TaskDomainService.prepareRetry()
- [x] 添加 TaskDomainService.prepareRollback()
- [x] 添加 TaskDomainService.calculateStartIndex()
- [x] 修复所有编译错误
- [x] 代码清理（移除 unused imports/variables）
- [ ] 单元测试更新
- [ ] 集成测试验证

---

## ✅ 编译状态

**当前状态**: ✅ 所有编译错误已解决，只剩优化建议

**剩余 Warnings** (仅性能优化建议，不影响功能):
- TaskDomainService.java:
  - 第 440 行: `Long.parseLong()` 可优化（性能建议）
  - 第 451 行: `Long.parseLong()` 可优化（性能建议）

**已清理**:
- ✅ TaskOperationService: 移除未使用的 imports (`TaskStatus`, `Function`)
- ✅ TaskOperationService: 移除未使用的字段 (`taskRuntimeRepository`)
- ✅ TaskDomainService: 移除未使用的 import (`TaskRetryStartedEvent`)
- ✅ TaskDomainService: 修正未使用的局部变量 (`startIndex`)

---

## 📌 注意事项

1. **回滚不是逆向操作**：
   - ❌ 错误理解：逆向执行 stages
   - ✅ 正确理解：用旧版本配置正向执行 stages

2. **lastCompletedStageName 的特殊处理**：
   - `null` 或不存在的 stage：RecoveryService 按从头到尾全部执行
   - retry: null 表示从头重试
   - rollback: null 表示全部回滚

3. **version 参数**：
   - 类型：`String`（不是 `Long`）
   - 用途：单调递增版本号校验，避免版本回拨

4. **异步执行**：
   - Facade 和 TaskOperationService 方法立即返回
   - Caller 监听领域事件获取执行结果

---

## 🎯 下一步行动

1. **添加 TaskDomainService 方法**（优先级最高）
   - 实现 `prepareRetry(TenantConfig, String)`
   - 实现 `prepareRollback(TenantConfig, String, String)`

2. **编译验证**
   - 确保所有编译错误已解决

3. **单元测试**
   - 测试 Facade 的 retry/rollback 方法
   - 测试 TaskOperationService 的 retry/rollback 方法

4. **集成测试**
   - 验证完整调用链路
   - 验证事件发布和监听

---

**实施负责人**: _待指定_  
**预计完成时间**: _待评估_
