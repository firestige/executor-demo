# RF-19-06 策略化重构边界分析报告

**分析日期**: 2025-11-22  
**当前状态**: DynamicStageFactory 单类承载 4 个 Stage 编排，约 700+ 行

---

## 1. 现状分析

### 1.1 当前 DynamicStageFactory 结构

**文件位置**: `src/main/java/xyz/firestige/deploy/infrastructure/execution/stage/factory/DynamicStageFactory.java`

**核心职责**:
- 实现 `StageFactory` 接口
- 注入 4 个基础设施依赖（RestTemplate, StringRedisTemplate, DeploymentConfigLoader, ObjectMapper）
- 提供 `buildStages(TenantConfig)` 方法，按固定顺序创建 Stage 列表
- 包含 4 个 `createXxxStage()` 方法及其辅助方法

**4 个 Stage 创建方法**:
| 方法 | 行数（估算） | 条件判断 | Step 数 |
|------|------------|---------|---------|
| createASBCStage() | ~140 行 | `getMediaRoutingConfig() != null` | 1 |
| createPortalStage() | ~80 行 | `getDeployUnit() != null` | 1 |
| createBlueGreenGatewayStage() | ~210 行 | `getRouteRules() != null && !isEmpty()` | 3 |
| createOBServiceStage() | ~130 行 | `shouldCreateOBServiceStage()` | 2 |

**辅助方法**:
- 每个 Stage 包含 2-6 个私有方法（DataPreparer、ResultValidator、辅助转换）
- 共享方法：`extractSourceUnit()`, `extractTargetUnit()`, `convertRouteRulesToMap()`, `extractHealthCheckPath()`, `resolveEndpoints()`, `generateToken()`, `generateRandomHex()`

### 1.2 依赖注入现状

**当前依赖**:
```java
private final RestTemplate restTemplate;
private final StringRedisTemplate redisTemplate;
private final DeploymentConfigLoader configLoader;
private final ObjectMapper objectMapper;
```

**缺失注入** (OBService 反射调用):
- `AgentService` - 当前通过 TaskRuntimeContext 传入，策略化后需显式注入

### 1.3 外部调用点

**使用方**:
1. `DeploymentPlanCreator` - 注入 `StageFactory` 接口
2. `ExecutorConfiguration` - 构造 TaskExecutor 时传入 `StageFactory`

**测试用例**:
- `DeploymentPlanCreatorTest` - mock StageFactory

**结论**: 外部只依赖 `StageFactory` 接口，可透明替换实现。

---

## 2. 重构边界定义

### 2.1 需要新增的类（7个）

#### 核心接口与资源类
1. **StageAssembler** (接口)
   - 位置: `src/main/java/xyz/firestige/deploy/infrastructure/execution/stage/factory/StageAssembler.java`
   - 方法: `String stageName()`, `int order()`, `boolean supports(TenantConfig)`, `TaskStage buildStage(TenantConfig, SharedStageResources)`

2. **SharedStageResources** (聚合类)
   - 位置: `src/main/java/xyz/firestige/deploy/infrastructure/execution/stage/factory/SharedStageResources.java`
   - 字段: 现有 4 个依赖 + AgentService (可选)
   - 作用: 避免每个策略重复注入

#### 4 个策略实现类
3. **AsbcStageAssembler** (@Component)
   - 位置: `src/main/java/xyz/firestige/deploy/infrastructure/execution/stage/factory/assembler/AsbcStageAssembler.java`
   - order: 10
   - 迁移: createASBCStage() + 相关辅助方法

4. **PortalStageAssembler** (@Component)
   - 位置: `src/main/java/xyz/firestige/deploy/infrastructure/execution/stage/factory/assembler/PortalStageAssembler.java`
   - order: 20
   - 迁移: createPortalStage() + 相关辅助方法

5. **BlueGreenStageAssembler** (@Component)
   - 位置: `src/main/java/xyz/firestige/deploy/infrastructure/execution/stage/factory/assembler/BlueGreenStageAssembler.java`
   - order: 30
   - 迁移: createBlueGreenGatewayStage() + 相关辅助方法

6. **ObServiceStageAssembler** (@Component)
   - 位置: `src/main/java/xyz/firestige/deploy/infrastructure/execution/stage/factory/assembler/ObServiceStageAssembler.java`
   - order: 40
   - 迁移: createOBServiceStage() + 相关辅助方法

#### 新工厂
7. **OrchestratedStageFactory** (@Component, implements StageFactory)
   - 位置: `src/main/java/xyz/firestige/deploy/infrastructure/execution/stage/factory/OrchestratedStageFactory.java`
   - 职责: 注入所有 StageAssembler，排序、过滤、执行
   - 替代: 旧 DynamicStageFactory

### 2.2 需要修改的类（0-1个）

**不修改外部调用方** (接口兼容):
- `DeploymentPlanCreator`
- `ExecutorConfiguration`
- `DeploymentPlanCreatorTest`

**可选修改** (切换实现):
- 配置开关：使用 `@ConditionalOnProperty` 或 `@Primary` 控制新旧工厂选择
- 启动验证：在 OrchestratedStageFactory 启动时打印策略列表与顺序

### 2.3 需要删除的类（1个）

**最终删除**:
- `DynamicStageFactory.java` (待对比测试通过后删除)

**保留路径**:
- 在对比阶段保留旧类，使用 `@Deprecated` 标记
- Git 历史中永久保存

---

## 3. 迁移策略与步骤

### Phase 1: 基础设施搭建（不影响现有代码）
- [ ] 创建 `StageAssembler` 接口
- [ ] 创建 `SharedStageResources` 聚合类
- [ ] 创建 `OrchestratedStageFactory` 空壳（仅注入，buildStages 返回空列表）
- [ ] 编译验证无错误

### Phase 2: 策略迁移（逐个复制）
- [ ] 创建 `AsbcStageAssembler` - 复制 createASBCStage() 逻辑
- [ ] 创建 `PortalStageAssembler` - 复制 createPortalStage() 逻辑
- [ ] 创建 `BlueGreenStageAssembler` - 复制 createBlueGreenGatewayStage() 逻辑
- [ ] 创建 `ObServiceStageAssembler` - 复制 createOBServiceStage() 逻辑
- [ ] 实现 `OrchestratedStageFactory.buildStages()` - 遍历策略、排序、过滤

### Phase 3: 对比验证（双工厂运行）
- [ ] 编写对比测试：比较新旧工厂输出 Stage 列表（名称、顺序、数量）
- [ ] 测试各种 TenantConfig 组合：
  - 仅 MediaRouting
  - 仅 DeployUnit
  - 仅 RouteRules
  - 全配置
  - 空配置
- [ ] 验证 order 顺序正确
- [ ] 验证 supports 条件正确

### Phase 4: 切换与清理
- [ ] 标记旧 `DynamicStageFactory` 为 `@Deprecated`
- [ ] 使用 `@Primary` 让 `OrchestratedStageFactory` 成为默认实现
- [ ] 回归测试：运行完整集成测试
- [ ] 删除旧 `DynamicStageFactory`
- [ ] 更新文档

---

## 4. 风险评估与缓解

### 4.1 功能风险

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| 逻辑迁移遗漏 | Stage 行为不一致 | 中 | 对比测试 + 代码行审查 |
| 顺序错误 | Stage 执行顺序变化 | 低 | 固定 order 常量 + 启动日志 |
| 依赖注入缺失 | NPE 运行时异常 | 低 | SharedStageResources 构造时校验非空 |
| 条件判断变化 | Stage 缺失或多余 | 中 | 对比测试覆盖所有分支 |

### 4.2 性能风险

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| 策略遍历开销 | 启动变慢 | 极低 | Stage 数量 <10，可忽略 |
| 重复注入 | 内存占用 | 无 | SharedStageResources 单例 |

### 4.3 兼容性风险

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| 外部调用断裂 | 编译失败 | 无 | 保持 StageFactory 接口不变 |
| 测试用例失败 | CI 阻断 | 低 | mock 策略仍可工作 |

---

## 5. 共享代码处理

### 5.1 通用辅助方法（需决策）

**当前在 DynamicStageFactory 中的共享方法**:
- `extractSourceUnit(TenantConfig)` - BG + OB 使用
- `extractTargetUnit(TenantConfig)` - BG + OB 使用
- `convertRouteRulesToMap(TenantConfig)` - BG 使用
- `extractHealthCheckPath(TenantConfig)` - BG 使用
- `resolveEndpoints(String, String)` - BG 使用
- `generateToken(String)` - ASBC 使用
- `generateRandomHex(int)` - ASBC 使用

**决策方案**:
- **选项 A**: 复制到各自策略类（简单但有重复）
- **选项 B**: 提取为工具类 `StageAssemblerUtils` (推荐)
- **选项 C**: 放入 `SharedStageResources` 作为方法（不推荐，职责混乱）

**推荐**: 选项 B - 创建 `StageAssemblerUtils` 工具类，包含所有共享方法。

### 5.2 共享常量

**建议提取**:
```java
public class StageAssemblerConstants {
    public static final int ORDER_ASBC = 10;
    public static final int ORDER_PORTAL = 20;
    public static final int ORDER_BLUE_GREEN = 30;
    public static final int ORDER_OB_SERVICE = 40;
}
```

---

## 6. 测试策略

### 6.1 单元测试（每个 Assembler）

**测试文件**:
- `AsbcStageAssemblerTest`
- `PortalStageAssemblerTest`
- `BlueGreenStageAssemblerTest`
- `ObServiceStageAssemblerTest`

**测试点**:
- supports() 正/负分支
- buildStage() 返回正确 Stage 名称
- buildStage() 返回正确 Step 数量
- buildStage() Step 类型正确

### 6.2 集成测试（OrchestratedStageFactory）

**测试文件**:
- `OrchestratedStageFactoryTest`

**测试点**:
- 顺序正确（order 生效）
- 条件过滤（supports 生效）
- 全配置 → 4 个 Stage
- 部分配置 → 对应数量 Stage
- 空配置 → 0 个 Stage

### 6.3 对比测试（新旧工厂）

**测试文件**:
- `StageFactoryMigrationComparisonTest`

**测试逻辑**:
```java
List<TaskStage> oldStages = oldFactory.buildStages(cfg);
List<TaskStage> newStages = newFactory.buildStages(cfg);
assertEquals(oldStages.size(), newStages.size());
for (int i = 0; i < oldStages.size(); i++) {
    assertEquals(oldStages.get(i).getName(), newStages.get(i).getName());
}
```

---

## 7. 实施时间估算

| 阶段 | 任务 | 预计时间 |
|------|------|----------|
| Phase 1 | 基础设施搭建 | 1 小时 |
| Phase 2 | 4 个策略迁移 | 3 小时 |
| Phase 3 | 对比验证与测试 | 2 小时 |
| Phase 4 | 切换与清理 | 1 小时 |
| **总计** | | **7 小时** |

---

## 8. 验收标准

- [ ] 所有新类编译通过
- [ ] 所有单元测试通过（策略 + 工厂）
- [ ] 对比测试通过（新旧工厂输出一致）
- [ ] 回归测试通过（DeploymentPlanCreatorTest）
- [ ] 旧 DynamicStageFactory 已删除
- [ ] 启动日志输出策略列表与顺序
- [ ] 文档更新（RF19_06_STAGE_ASSEMBLER_STRATEGY_DESIGN.md）

---

## 9. 推荐实施顺序

**建议按以下顺序逐步推进**:
1. 创建接口与资源类（StageAssembler + SharedStageResources + StageAssemblerUtils）
2. 创建 OrchestratedStageFactory 空壳
3. 迁移最简单的 PortalStageAssembler（验证流程）
4. 迁移其他 3 个策略
5. 编写对比测试
6. 切换 @Primary
7. 删除旧类

---

**边界分析完成，等待实施确认。**

是否开始 Phase 1 实施？

