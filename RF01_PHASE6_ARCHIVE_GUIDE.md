# Phase 6 归档指南 - 快速参考

**目的**：将重构文档的核心信息归档到主文档，然后删除所有临时重构文档

---

## 📝 归档映射

### 1. ARCHITECTURE_PROMPT.md 更新

**来源**：`RF01_DESIGN_DECISIONS.md` + `RF01_REFACTOR_PROPOSAL.md`

**需要归档的内容**：
```markdown
## Current Architecture Snapshot (更新)

新增内容：
- Application Service Layer: PlanApplicationService, TaskApplicationService
- Result DTO (DDD design): PlanCreationResult, PlanInfo, TaskInfo, PlanOperationResult, TaskOperationResult
- Facade Layer: DeploymentTaskFacade (no interface, DTO conversion + exception handling)

## Context Separation (更新)

新增内容：
- Internal DTO (TenantConfig): Used by application service layer, decoupled from external DTO
- External DTO (TenantDeployConfig): Used by facade layer, converted to internal DTO
```

---

### 2. develop.log 更新

**来源**：`RF01_FINAL_SUMMARY.md` + `RF01_RESULT_DTO_ANALYSIS.md`

**格式**：
```markdown
## 2025-11-17

### RF-01: Facade 业务逻辑剥离与 Result DTO 重构（Phase 17）

**核心改进**：
1. 分层架构重构：从 Facade 剥离业务逻辑到应用服务层（PlanApplicationService、TaskApplicationService）
2. Result DTO 重构（DDD 视角）：明确 Plan 和 Task 的聚合边界，使用值对象表达聚合关系

**核心价值**：
- 领域模型清晰度提升：Plan 包含 Task 的聚合关系在返回值中明确表达（PlanInfo 值对象包含 List<TaskInfo>）
- 类型安全：编译期检查，PlanOperationResult vs TaskOperationResult，避免 Plan ID 和 Task ID 混用
- 接口稳定性：Facade 负责外部 DTO → 内部 DTO 转换，保护应用层接口稳定
- 可扩展性：Plan 和 Task 可独立演进，符合 DDD 聚合根、值对象、工厂方法等模式

**新增类**：
- Application Service: PlanApplicationService, TaskApplicationService
- DTO: TenantConfig (内部), PlanCreationResult, PlanInfo, TaskInfo, PlanOperationResult, TaskOperationResult
- Facade: DeploymentTaskFacade (无接口), 异常类（TaskCreationException, TaskOperationException, TaskNotFoundException, PlanNotFoundException）

**删除类**：
- 旧 Facade: DeploymentTaskFacadeImpl
- 旧 Result DTO: facade/TaskCreationResult, facade/TaskOperationResult

**文件**：
- 应用服务层：xyz.firestige.executor.application/
- DTO：xyz.firestige.executor.application.dto/
- Facade：xyz.firestige.executor.facade/

**提交 ID**：[填写 Phase 6 的提交 ID]
```

---

### 3. TODO.md 更新

**来源**：`RF01_PROGRESS.md`

**需要更新的内容**：
```markdown
## 2. 当前待办（Phase 17 & 18）

### 2.1. 架构重构（Phase 17）

#### RF-01: Facade 业务逻辑剥离 — ✅ DONE
- **完成日期**：2025-11-17
- **核心改进**：
  - 分层架构重构：应用服务层承载业务逻辑，Facade 负责 DTO 转换和异常转换
  - Result DTO 重构（DDD）：明确 Plan 和 Task 聚合边界
- **详细信息**：参见 develop.log 2025-11-17 条目

#### RF-02: TaskWorkerFactory 参数简化 — TODO
（保持不变）

#### RF-04: 端到端集成测试套件 — TODO
（保持不变）
```

---

## 🗑️ 删除清单

Phase 6 最后一步，删除以下 6 个文件：

```bash
rm RF01_PROGRESS.md
rm RF01_README.md
rm RF01_FINAL_SUMMARY.md
rm RF01_DESIGN_DECISIONS.md
rm RF01_REFACTOR_PROPOSAL.md
rm RF01_RESULT_DTO_ANALYSIS.md
```

**验证**：
```bash
# 确认文件已删除
ls -la | grep RF01

# 应该没有输出
```

---

## ✅ 归档检查清单

- [ ] `ARCHITECTURE_PROMPT.md` 已更新（反映新架构）
- [ ] `develop.log` 已记录（包含核心价值和新增/删除类）
- [ ] `TODO.md` 已更新（RF-01 标记为 DONE）
- [ ] 所有重构文档已删除（6 个文件）
- [ ] Git commit: "docs: archive RF-01 refactoring information to main docs"
- [ ] 工作区干净，无临时文档残留

---

**原则**：重构文档是临时脚手架，核心信息归档后即可移除，保持项目文档整洁。

