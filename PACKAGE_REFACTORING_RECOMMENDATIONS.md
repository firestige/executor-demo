# 包结构重构建议

## 📋 问题概述

经过详细分析，项目的包结构存在以下几个**违反 DDD 分层原则**的问题：

---

## ❌ 问题 1：顶级包结构混乱

### 问题描述
存在两个不在 DDD 分层内部的顶级包：

```
xyz.firestige.dto.deploy.TenantDeployConfig
xyz.firestige.entity.deploy.NetworkEndpoint
```

### 影响范围
- **21+ 个文件**引用 `TenantDeployConfig`
- **11+ 个文件**引用外部 `NetworkEndpoint`
- **Domain 层被污染**：PlanFactory、ValidationSummary、HealthCheckStep 等直接依赖外部包

### 违反原则
- ❌ Domain 层依赖外部包（应该是自包含的）
- ❌ 分层边界不清晰
- ❌ DTO 职责混乱（既是外部契约，又是内部模型）

### 重构建议

#### 方案 A：移动到 Facade 层（推荐）
```
移动：
xyz.firestige.dto.deploy.TenantDeployConfig
  → xyz.firestige.deploy.facade.dto.TenantDeployConfig

删除：
xyz.firestige.entity.deploy.NetworkEndpoint
  → 统一使用 domain.shared.vo.NetworkEndpoint
```

**优点**：
- ✅ 清晰的分层边界
- ✅ DTO 只在 Facade 层作为外部契约
- ✅ Domain 层使用自己的 VO

#### 方案 B：保持现状，但增加转换层
```
保留：xyz.firestige.dto.deploy.TenantDeployConfig (外部 DTO)

新增：
- facade.dto.TenantDeployConfigDTO (Facade 层内部)
- application.dto.TenantConfig (Application 层内部，已存在)

流程：
外部 DTO → Facade 转换 → Application DTO → Domain VO
```

**优点**：
- ✅ 外部 API 契约不变
- ✅ Domain 层保持纯净
- ❌ 需要多次转换（性能影响小）

---

## ❌ 问题 2：Infrastructure 包简化导入

### 问题描述
大量代码使用简化的包路径：

```java
// 错误示例（当前代码）
import xyz.firestige.deploy.state.TaskStateManager;
import xyz.firestige.deploy.execution.TaskExecutor;
import xyz.firestige.deploy.metrics.MetricsRegistry;

// 应该使用
import xyz.firestige.deploy.infrastructure.state.TaskStateManager;
import xyz.firestige.deploy.infrastructure.execution.TaskExecutor;
import xyz.firestige.deploy.infrastructure.metrics.MetricsRegistry;
```

### 影响范围
- `state.*`: 20+ 处引用
- `execution.*`: 20+ 处引用
- `metrics.*`: 7+ 处引用

### 违反原则
- ❌ 分层不清晰，容易误认为是顶级包
- ❌ IDE 自动导入时可能选错包
- ❌ 新人难以理解包的实际位置

### 重构建议

#### 方案：全局替换包导入（推荐）

使用 IDE 的 "Refactor → Move Package" 功能，或者批量替换：

```bash
# 批量替换导入语句
find . -name "*.java" -type f -exec sed -i '' \
  's/import xyz.firestige.deploy.state\./import xyz.firestige.deploy.infrastructure.state./g' {} +

find . -name "*.java" -type f -exec sed -i '' \
  's/import xyz.firestige.deploy.execution\./import xyz.firestige.deploy.infrastructure.execution./g' {} +

find . -name "*.java" -type f -exec sed -i '' \
  's/import xyz.firestige.deploy.metrics\./import xyz.firestige.deploy.infrastructure.metrics./g' {} +
```

**优点**：
- ✅ 分层边界清晰
- ✅ 包结构一目了然
- ✅ 符合 DDD 命名规范

**注意**：
- ⚠️ 需要检查是否有**物理包路径不一致**的情况
- ⚠️ 如果物理路径确实是 `xyz.firestige.deploy.state`，则需要先移动包

---

## ❌ 问题 3：Domain 层依赖外部 DTO

### 问题文件

#### 3.1 PlanFactory
```java
package xyz.firestige.deploy.domain.plan;

import xyz.firestige.dto.deploy.TenantDeployConfig;      // ❌ 外部 DTO
import xyz.firestige.entity.deploy.NetworkEndpoint;      // ❌ 外部 Entity
```

#### 3.2 ValidationSummary
```java
package xyz.firestige.deploy.domain.shared.validation;

import xyz.firestige.dto.deploy.TenantDeployConfig;      // ❌ 外部 DTO
```

#### 3.3 HealthCheckStep
```java
package xyz.firestige.deploy.domain.stage.steps;

import xyz.firestige.entity.deploy.NetworkEndpoint;      // ❌ 外部 Entity
```

### 重构建议

#### 3.1 PlanFactory 重构
```java
// 修改前
public class PlanFactory {
    public PlanAggregate create(List<TenantDeployConfig> configs) { // ❌
        // ...
    }
}

// 修改后
public class PlanFactory {
    // 接受 Application 层的内部 DTO
    public PlanAggregate create(List<TenantConfig> configs) { // ✅
        // 或者接受领域对象
        // public PlanAggregate create(List<TenantSnapshot> snapshots) { // ✅
    }
}
```

#### 3.2 ValidationSummary 重构
```java
// 修改前
public class ValidationSummary {
    private List<TenantDeployConfig> configs; // ❌
}

// 修改后 - 方案 A：泛型化
public class ValidationSummary<T> {
    private List<T> validatedItems; // ✅
}

// 修改后 - 方案 B：只保留验证结果
public class ValidationSummary {
    private List<String> tenantIds; // ✅
    private List<ValidationError> errors;
    private List<ValidationWarning> warnings;
}
```

#### 3.3 HealthCheckStep 重构

```java
// 修改前

// 修改后

```

---

## ❌ 问题 4：重复的 NetworkEndpoint

### 问题描述
存在两个 `NetworkEndpoint` 类：

```
1. xyz.firestige.entity.deploy.NetworkEndpoint        (贫血模型，只有 getter/setter)
2. xyz.firestige.deploy.domain.shared.vo.RouteRule  (值对象)
```

### 影响
- ❌ 职责重复
- ❌ Domain 层使用了外部 entity 版本
- ❌ 容易混淆

### 重构建议

**删除 `entity.deploy.NetworkEndpoint`，统一使用 Value Object**

```bash
# 步骤 1：全局替换导入
find . -name "*.java" -type f -exec sed -i '' \
  's/import xyz.firestige.entity.deploy.NetworkEndpoint/import xyz.firestige.deploy.domain.shared.vo.RouteRule/g' {} +

# 步骤 2：检查 VO 是否包含所有必要字段
# 如果缺少字段，补充到 domain.shared.vo.NetworkEndpoint

# 步骤 3：删除文件
rm src/main/java/xyz/firestige/entity/deploy/NetworkEndpoint.java
```

---

## 📊 重构优先级

| 优先级 | 问题 | 影响范围 | 难度 | 预计工时 |
|--------|------|----------|------|----------|
| **P0** | 删除重复的 NetworkEndpoint | 11+ 文件 | 低 | 0.5h |
| **P1** | 修复 Domain 层依赖外部 DTO | 3 个核心类 | 中 | 2h |
| **P2** | 移动顶级 dto/entity 包 | 21+ 文件 | 中 | 3h |
| **P3** | 统一包导入路径 | 50+ 文件 | 低 | 1h |

---

## 🎯 重构步骤（推荐顺序）

### Step 1: 删除重复的 NetworkEndpoint (P0)
```bash
# 1. 确认 domain.shared.vo.NetworkEndpoint 包含所有字段
# 2. 全局替换导入
# 3. 删除 entity.deploy.NetworkEndpoint
# 4. 运行测试
```

### Step 2: 修复 HealthCheckStep 依赖 (P1)

```java
// 修改 HealthCheckStep.java

-
+ 
```

### Step 3: 重构 PlanFactory (P1)
```java
// 方案 A：修改参数类型
- public PlanAggregate create(List<TenantDeployConfig> configs)
+ public PlanAggregate create(List<TenantConfig> configs)

// 方案 B：增加转换层
// Facade → TenantConfig → PlanFactory
```

### Step 4: 重构 ValidationSummary (P1)
```java
// 泛型化或移除外部依赖
public class ValidationSummary<T> { ... }
```

### Step 5: 移动 TenantDeployConfig (P2)
```bash
# 如果选择方案 A
git mv src/main/java/xyz/firestige/dto/deploy/TenantDeployConfig.java \
       src/main/java/xyz/firestige/deploy/facade/dto/TenantDeployConfig.java

# 更新所有导入
find . -name "*.java" -type f -exec sed -i '' \
  's/import xyz.firestige.dto.deploy.TenantDeployConfig/import xyz.firestige.deploy.facade.dto.TenantDeployConfig/g' {} +
```

### Step 6: 统一包导入路径 (P3)
```bash
# 批量替换 infrastructure 包的简化导入
# (见问题 2 的脚本)
```

---

## ✅ 重构后的理想结构

```
xyz.firestige.deploy/
├── facade/
│   ├── dto/
│   │   └── TenantDeployConfig.java        ← 外部 DTO (如果保留在 Facade)
│   ├── converter/
│   │   └── TenantConfigConverter.java     ← DTO 转换器
│   └── DeploymentTaskFacade.java
│
├── application/
│   ├── dto/
│   │   └── TenantConfig.java              ← 内部 DTO
│   └── DeploymentApplicationService.java
│
├── domain/
│   ├── plan/
│   │   └── PlanFactory.java               ✅ 只依赖 application.dto 或领域对象
│   ├── stage/
│   │   └── steps/
│   │       └── HealthCheckStep.java       ✅ 使用 domain.shared.vo.NetworkEndpoint
│   └── shared/
│       ├── vo/
│       │   └── NetworkEndpoint.java       ✅ 唯一的 NetworkEndpoint
│       └── validation/
│           └── ValidationSummary.java      ✅ 泛型或无外部依赖
│
└── infrastructure/
    ├── state/                              ✅ 完整路径导入
    ├── execution/                          ✅ 完整路径导入
    └── metrics/                            ✅ 完整路径导入
```

---

## 📝 总结

当前项目的包结构**基本符合 DDD 分层**，但存在以下需要改进的地方：

1. ✅ **已做对**：
   - Facade → Application → Domain → Infrastructure 分层清晰
   - 聚合根、领域服务、值对象设计良好
   - Repository 接口在 Domain，实现在 Infrastructure

2. ❌ **需要改进**：
   - 顶级 `dto`/`entity` 包不在 DDD 分层内
   - Domain 层依赖外部 DTO（违反纯净性）
   - Infrastructure 包简化导入（分层不清晰）
   - NetworkEndpoint 重复定义

3. 🎯 **重构建议**：
   - **立即执行**：删除重复的 NetworkEndpoint (0.5h)
   - **高优先级**：修复 Domain 层依赖 (2h)
   - **中优先级**：移动顶级 dto/entity 包 (3h)
   - **低优先级**：统一包导入路径 (1h)

**总计工时**：约 6.5 小时即可完成所有重构。

---

## 📚 参考资料

- [DDD 分层架构最佳实践](https://martinfowler.com/bliki/DomainDrivenDesign.html)
- [值对象 vs 实体](https://martinfowler.com/bliki/ValueObject.html)
- [防腐层（ACL）模式](https://docs.microsoft.com/en-us/azure/architecture/patterns/anti-corruption-layer)
