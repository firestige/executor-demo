# RF-01 关键设计决策总结

**日期**: 2025-11-17  
**状态**: ✅ 已达成共识

---

## 🎯 核心设计原则

### 1. 分层架构
```
外部系统
   ↓
Facade Layer (防腐层)
   ↓ [外部 DTO → 内部 DTO]
Application Service Layer (业务编排)
   ↓
Domain Layer (领域模型)
```

### 2. 职责划分

| 层次 | 职责 | 输入 | 输出 |
|------|------|------|------|
| **Facade** | DTO 转换 + 参数校验 + 异常转换 | 外部 DTO | void + 异常 |
| **Application Service** | 业务编排 + 状态管理 | 内部 DTO | Result 对象 |
| **Domain** | 领域逻辑 + 状态机 | 领域模型 | 领域模型 |

---

## ✅ 关键决策

### 决策 1：防腐层不定义接口

**结论**: `DeploymentTaskFacade` 直接使用具体类，不定义接口

**理由**：
1. ✅ **YAGNI 原则**：目前只有一个实现，无需过早抽象
2. ✅ **不同外部系统需要不同的防腐层**：
   - 当前：方法调用（`DeploymentTaskFacade`）
   - 未来：REST API（`TaskRestController`）
   - 未来：MQ（`TaskMqListener`）
3. ✅ **接口签名不同**：无法共享接口，各自独立演进
4. ✅ **测试简单**：直接实例化具体类，无需 mock

**用户确认**: ✅ "从上面的场景来看，对防腐层设计接口好像是没有必要的"

---

### 决策 2：Facade 返回 void + 异常

**结论**: Facade 方法返回 `void`，失败时抛出异常（查询操作除外）

**理由**：
1. ✅ 现代 API 设计风格（异常机制 vs 错误码）
2. ✅ 便于上层统一异常处理（如 REST Controller 的 @ControllerAdvice）
3. ✅ 职责清晰：Facade 只负责协调，不关心业务结果

**例外**: `queryTaskStatus()` 保留返回值（查询本质需要返回数据）

---

### 决策 3：应用服务层返回 Result 对象

**结论**: `PlanApplicationService` / `TaskApplicationService` 返回 `TaskCreationResult` / `TaskOperationResult`

**理由**：
1. ✅ 业务语义清晰（成功 vs 失败 + 上下文信息）
2. ✅ 便于 Facade 进行精细化异常转换（校验失败 vs 系统错误）
3. ✅ 保持测试风格一致（应用服务层断言 Result）

---

### 决策 4：Facade 负责 DTO 转换

**结论**: Facade 负责 `TenantDeployConfig`（外部 DTO）→ `TenantConfig`（内部 DTO）

**理由**：
1. ✅ **保护应用层接口稳定性**：外部 DTO 变化不影响应用层
2. ✅ **防腐层职责**：隔离外部系统变化
3. ✅ **易于演进**：支持多种外部 DTO 映射到同一内部 DTO

**用户确认**: ✅ "Facade 应该负责把外部的 DTO 转换成内部的 DTO，从而保护应用层服务的接口稳定"

**设计细节**：
```java
// Facade 层
public void createSwitchTask(List<TenantDeployConfig> externalConfigs) {
    // 1. 参数校验
    // 2. DTO 转换
    List<TenantConfig> internalConfigs = convertToInternalConfigs(externalConfigs);
    // 3. 调用应用服务
    TaskCreationResult result = planApplicationService.createSwitchTask(internalConfigs);
    // 4. 异常转换
}

// 应用服务层
public TaskCreationResult createSwitchTask(List<TenantConfig> internalConfigs) {
    // 业务逻辑（使用内部 DTO）
}
```

---

### 决策 5：不保留旧代码

**结论**: 项目处于开发阶段，直接替换旧代码，不保留兼容性

**理由**：
1. ✅ 项目无已发布版本，无需向后兼容
2. ✅ Git 管理风险，可回退
3. ✅ 避免代码膨胀（V1/V2/V3 命名混乱）

**用户确认**: ✅ "项目目前还在开发过程中，没有已发布版本，所以不需要考虑前向兼容，不要保留旧代码"

**实施策略**：
```bash
# 每个 Phase 完成后打 tag
git tag rf01-phase1-internal-dto
git tag rf01-phase2-application-service
git tag rf01-phase3-new-facade
git tag rf01-phase4-cleanup

# 出现问题时回退
git reset --hard rf01-phase2-application-service
```

---

### 决策 6：Result DTO 重构（DDD 视角）

**结论**: 拆分返回值对象，明确 Plan 和 Task 的聚合边界

**当前问题**：
1. ❌ `TaskCreationResult` 实际创建的是 Plan，但命名为 "Task"，语义不清
2. ❌ `TaskOperationResult` 既用于 Plan 操作，也用于 Task 操作，职责混淆
3. ❌ 返回值中 `taskIds` 只是字符串列表，丢失了聚合关系上下文

**重构方案**：
```java
// ✅ 明确 Plan 聚合边界
PlanCreationResult {
    PlanInfo planInfo {           // Plan 聚合信息（值对象）
        String planId;
        List<TaskInfo> tasks;     // 体现 Plan 包含 Task 的聚合关系
    }
}

// ✅ 区分 Plan 和 Task 操作结果
PlanOperationResult {             // Plan 级别操作
    String planId;
    PlanStatus status;
}

TaskOperationResult {             // Task 级别操作
    String taskId;
    TaskStatus status;
}
```

**核心价值**：
1. ⭐⭐⭐⭐⭐ **领域模型清晰度提升**：Plan 和 Task 的聚合关系在返回值中明确表达
2. ⭐⭐⭐⭐ **类型安全**：编译期检查，避免把 Plan ID 当作 Task ID 使用
3. ⭐⭐⭐⭐ **可扩展性**：Plan 和 Task 可独立演进，新增字段互不影响
4. ⭐⭐⭐⭐⭐ **符合 DDD 最佳实践**：聚合根边界清晰，值对象不可变

**用户确认**: ✅ "内部 DTO 可以拆分的职责和组合关系更明确。参考 DDD 的设计。"

**详细分析**: 参见 `RF01_RESULT_DTO_ANALYSIS.md`

---

## 📦 包结构设计

```
xyz.firestige.executor
├── application/
│   ├── dto/
│   │   ├── TenantConfig.java              // 内部 DTO（输入）
│   │   ├── PlanCreationResult.java        // Plan 创建结果
│   │   ├── PlanOperationResult.java       // Plan 操作结果
│   │   ├── TaskOperationResult.java       // Task 操作结果
│   │   ├── PlanInfo.java                  // Plan 聚合信息（值对象）
│   │   └── TaskInfo.java                  // Task 信息（值对象）
│   ├── PlanApplicationService.java        // Plan 业务编排
│   └── TaskApplicationService.java        // Task 业务编排
├── facade/
│   ├── DeploymentTaskFacade.java          // 防腐层（无接口）
│   └── exception/
│       ├── TaskCreationException.java
│       ├── TaskOperationException.java
│       ├── TaskNotFoundException.java
│       └── PlanNotFoundException.java
├── domain/
│   ├── plan/
│   │   └── PlanAggregate.java
│   └── task/
│       └── TaskAggregate.java
└── ...
```

---

## 🚀 实施路线

### Phase 1: 创建 Result DTO（新增）
- 创建 `PlanCreationResult`、`PlanInfo`、`TaskInfo`
- 创建 `PlanOperationResult`、新 `TaskOperationResult`
- Git commit + tag: `rf01-phase1-result-dto`

### Phase 2: 创建内部 DTO
- 创建 `TenantConfig`（内部 DTO）
- Git commit + tag: `rf01-phase2-internal-dto`

### Phase 3: 创建应用服务层
- 实现 `PlanApplicationService` / `TaskApplicationService`
- 使用新的 Result DTO 作为返回值
- 迁移业务逻辑 + 注册表
- Git commit + tag: `rf01-phase3-application-service`

### Phase 4: 创建新 Facade
- 实现 `DeploymentTaskFacade`（无接口）
- 实现 DTO 转换 + 异常转换
- 处理新的 Result DTO
- 临时保留旧 Facade 供对比
- Git commit + tag: `rf01-phase4-new-facade`

### Phase 5: 删除旧代码
- 更新所有测试（断言异常 + 新 Result DTO）
- 删除旧 Facade 实现
- 删除旧 Result DTO（`facade/TaskCreationResult`、`facade/TaskOperationResult`）
- Git commit + tag: `rf01-phase5-cleanup`

### Phase 6: 验证与文档
- 运行完整测试套件
- 更新架构文档
- Code Review

---

## 📋 测试策略

### 应用服务层测试
```java
@Test
void should_create_plan_with_tasks_successfully() {
    // 使用内部 DTO
    List<TenantConfig> configs = createValidInternalConfigs();
    
    // 断言新的 Result 对象
    PlanCreationResult result = planApplicationService.createSwitchTask(configs);
    assertTrue(result.isSuccess());
    
    // 验证 Plan 聚合信息
    PlanInfo planInfo = result.getPlanInfo();
    assertNotNull(planInfo.getPlanId());
    assertEquals(3, planInfo.getTasks().size());
    
    // 验证 Task 信息
    TaskInfo firstTask = planInfo.getTasks().get(0);
    assertEquals("tenant-1", firstTask.getTenantId());
    assertEquals(TaskStatus.PENDING, firstTask.getStatus());
}

@Test
void should_pause_plan_successfully() {
    // Given
    Long planId = 123L;
    
    // When
    PlanOperationResult result = planApplicationService.pausePlan(planId);
    
    // Then
    assertTrue(result.isSuccess());
    assertEquals("123", result.getPlanId());
    assertEquals(PlanStatus.PAUSED, result.getStatus());
}
```

### Facade 层测试
```java
@Test
void should_throw_IllegalArgumentException_when_configs_invalid() {
    // 使用外部 DTO
    List<TenantDeployConfig> configs = createInvalidExternalConfigs();
    
    // 断言异常
    IllegalArgumentException ex = assertThrows(
        IllegalArgumentException.class,
        () -> facade.createSwitchTask(configs)
    );
    assertTrue(ex.getMessage().contains("配置校验失败"));
}

@Test
void should_convert_dto_correctly() {
    // 验证 DTO 转换逻辑
    verify(planApplicationService).createSwitchTask(argThat(internalConfigs -> {
        TenantConfig internal = internalConfigs.get(0);
        return internal.getTenantId().equals(external.getTenantId());
    }));
}
```

---

## ✅ 共识确认清单

- [x] 防腐层不定义接口（直接使用具体类）
- [x] Facade 返回 void + 异常
- [x] 应用服务层返回 Result 对象
- [x] Facade 负责 DTO 转换（外部 → 内部）
- [x] 内部 DTO 定义在 `application.dto` 包
- [x] 不保留旧代码（通过 Git 管理风险）
- [x] **Result DTO 重构**：拆分 Plan 和 Task 的返回值对象（DDD 视角）
- [x] 实施路线（6 个 Phase）

---

## 📝 后续行动

**下一步**: 开始实施 Phase 1（创建 Result DTO）

**负责人**: [待分配]

**预计完成**: [待确定]

---

**方案批准**: ✅ 已达成共识，可以开始实施

