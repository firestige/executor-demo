# 二层校验方案实施完成报告

**完成时间**: 2024-11-17  
**方案**: Facade 层格式校验 + Application 层业务规则校验

---

## ✅ 实施完成

### 核心架构

```
┌─────────────────────────────────────────┐
│         Facade 层                        │
├─────────────────────────────────────────┤
│  1. 参数校验 (null/empty检查)             │
│  2. DTO 转换 (TenantDeployConfig →      │
│              TenantConfig)               │
│  3. 格式校验 (Jakarta Validator)         │
│     校验转换后的 TenantConfig             │
│     - @NotNull: 部署单元、ID等            │
│     - @NotBlank: 租户ID                  │
│     - @Valid: 级联校验嵌套对象            │
└─────────────────────────────────────────┘
                  ↓
┌─────────────────────────────────────────┐
│      Application 层                      │
├─────────────────────────────────────────┤
│  1. 业务规则校验 (BusinessValidator)     │
│     - 租户ID重复检查                     │
│     - 租户存在性检查 (可访问数据库)       │
│     - 其他业务规则                       │
│  2. 业务逻辑执行                         │
└─────────────────────────────────────────┘
```

---

## 📦 完成的工作

### 1. 添加 Jakarta Validation 依赖

**文件**: `pom.xml`

```xml
<!-- Jakarta Validation API (替代 javax.validation) -->
<dependency>
    <groupId>jakarta.validation</groupId>
    <artifactId>jakarta.validation-api</artifactId>
    <version>3.0.2</version>
</dependency>

<!-- Hibernate Validator (Jakarta Validation 实现) -->
<dependency>
    <groupId>org.hibernate.validator</groupId>
    <artifactId>hibernate-validator</artifactId>
    <version>8.0.1.Final</version>
</dependency>
```

### 2. TenantConfig 添加校验注解

**文件**: `src/main/java/xyz/firestige/executor/application/dto/TenantConfig.java`

```java
@NotNull(message = "部署单元不能为空")
@Valid
private DeployUnitIdentifier deployUnit;

@NotBlank(message = "租户ID不能为空或空白")
private String tenantId;
```

**注解说明**：
- `@NotNull`: 字段不能为 null
- `@NotBlank`: 字符串不能为 null、空字符串或只包含空白字符
- `@Valid`: 级联校验，触发嵌套对象的校验

### 3. DeployUnitIdentifier 添加校验注解

**文件**: `src/main/java/xyz/firestige/executor/application/dto/DeployUnitIdentifier.java`

```java
public record DeployUnitIdentifier(
    @NotNull(message = "部署单元ID不能为空") Long id,
    @NotNull(message = "部署单元版本不能为空") Long version,
    String name) {
    // ...
}
```

### 4. BusinessValidator - 业务规则校验器

**文件**: `src/main/java/xyz/firestige/executor/application/validation/BusinessValidator.java`

**职责**：
- 租户ID重复检查
- 租户存在性检查（可访问数据库）
- 其他业务规则校验

**使用位置**: Application 层（DeploymentApplicationService）

### 5. DeploymentTaskFacade - 先转换后校验

**文件**: `src/main/java/xyz/firestige/executor/facade/DeploymentTaskFacade.java`

**校验流程**：
```java
// Step 1: 参数校验
if (configs == null || configs.isEmpty()) {
    throw new IllegalArgumentException("配置列表不能为空");
}

// Step 2: DTO 转换
List<TenantConfig> internalConfigs = TenantConfigConverter.fromExternal(configs);

// Step 3: 格式校验（校验转换后的 TenantConfig）
for (TenantConfig config : internalConfigs) {
    Set<ConstraintViolation<TenantConfig>> violations = validator.validate(config);
    if (!violations.isEmpty()) {
        // 抛出异常
    }
}

// Step 4: 调用应用服务（会执行业务规则校验）
```

### 6. DeploymentApplicationService - 业务规则校验

**文件**: `src/main/java/xyz/firestige/executor/application/DeploymentApplicationService.java`

```java
public PlanCreationResult createDeploymentPlan(List<TenantConfig> configs) {
    // Step 1: 业务规则校验
    ValidationSummary businessValidation = businessValidator.validate(configs);
    if (businessValidation.hasErrors()) {
        return PlanCreationResult.validationFailure(businessValidation);
    }
    
    // Step 2: 业务逻辑执行
    // ...
}
```

---

## 🎯 设计原则遵循

### 1. 防腐层原则 ✅

- **外部 DTO** (`TenantDeployConfig`)：只在 Facade 层
- **内部 DTO** (`TenantConfig`)：应用层和领域层
- Facade 负责转换和隔离

### 2. 校验分层原则 ✅

| 层级 | 职责 | 工具 | 时机 |
|------|------|------|------|
| **Facade** | 参数校验 + 格式校验 | Jakarta Validator | **转换后** |
| **Application** | 业务规则校验 | BusinessValidator | 业务逻辑前 |
| **Domain** | 领域规则校验 | 领域对象方法 | 状态变更时 |

### 3. 关键设计点 ⭐

**为什么校验转换后的对象？**

1. ✅ **Facade 会跟随外部变化**：外部 DTO 不稳定，不值得为其添加校验
2. ✅ **校验最终使用的对象**：TenantConfig 是内部真正使用的，应该保证其正确性
3. ✅ **防腐层职责清晰**：转换 + 校验 = 确保进入应用层的数据合法

---

## 📊 架构验证

### 编译状态 ✅
- ✅ 无编译错误
- ⚠️ 仅有警告（方法未使用、unused import）

### 依赖关系 ✅
```
Facade层依赖:
  - DeploymentApplicationService
  - Validator (Jakarta)
  
Application层依赖:
  - PlanDomainService
  - TaskDomainService
  - BusinessValidator
  - StageFactory
  - HealthCheckClient
```

### 校验流程测试场景

#### 场景 1：参数为 null
```java
facade.createSwitchTask(null);
// 预期: 抛出 IllegalArgumentException("配置列表不能为空")
```

#### 场景 2：TenantConfig 格式错误
```java
TenantDeployConfig config = new TenantDeployConfig();
config.setTenantId(null); // 违反 @NotBlank
// 预期: 抛出 IllegalArgumentException("TenantConfig 格式校验失败: [tenantId] 租户ID不能为空或空白")
```

#### 场景 3：业务规则违反
```java
// 两个配置有相同的租户ID
configs.add(config1); // tenantId = "tenant1"
configs.add(config2); // tenantId = "tenant1"
// 预期: ApplicationService 返回 PlanCreationResult.validationFailure(...)
```

---

## 📚 技术要点

### Jakarta Validation 注解

| 注解 | 作用 | 适用类型 |
|------|------|---------|
| `@NotNull` | 不能为 null | 任意对象 |
| `@NotBlank` | 不能为 null/空/空白 | String |
| `@NotEmpty` | 不能为 null/空集合 | Collection/Array |
| `@Valid` | 级联校验 | 嵌套对象 |
| `@Size` | 限制大小 | String/Collection |
| `@Min/@Max` | 数值范围 | Number |
| `@Pattern` | 正则匹配 | String |

### Validator 使用

```java
@Autowired
private Validator validator;

Set<ConstraintViolation<T>> violations = validator.validate(object);
if (!violations.isEmpty()) {
    // 处理校验错误
    violations.forEach(v -> {
        String field = v.getPropertyPath().toString();
        String message = v.getMessage();
        // ...
    });
}
```

---

## 🔧 后续优化建议

### 短期
1. 为其他必填字段添加校验注解
2. 添加 `@Size` 限制集合大小
3. 补充单元测试验证校验逻辑

### 中期
1. 自定义校验注解（如 `@ValidTenantId`）
2. 校验分组（不同场景不同规则）
3. 国际化错误消息

### 长期
1. 统一异常处理（`@ControllerAdvice`）
2. 校验结果缓存
3. 性能优化（批量校验）

---

## ✅ 验收标准

- [x] Jakarta Validation 依赖添加
- [x] TenantConfig 添加校验注解
- [x] DeployUnitIdentifier 添加校验注解
- [x] Facade 先转换后校验
- [x] Application 使用 BusinessValidator
- [x] 编译通过（无错误）
- [x] Git 提交完成
- [ ] 单元测试（待后续）
- [ ] 集成测试（待后续）

---

## 📝 关键代码片段

### Facade 层校验
```java
// 转换后校验
List<TenantConfig> internalConfigs = TenantConfigConverter.fromExternal(configs);

for (int i = 0; i < internalConfigs.size(); i++) {
    TenantConfig config = internalConfigs.get(i);
    Set<ConstraintViolation<TenantConfig>> violations = validator.validate(config);
    
    if (!violations.isEmpty()) {
        String errorDetail = violations.stream()
                .map(v -> String.format("[%s] %s", v.getPropertyPath(), v.getMessage()))
                .collect(Collectors.joining("; "));
        throw new IllegalArgumentException("TenantConfig 格式校验失败: " + errorDetail);
    }
}
```

### Application 层校验
```java
// 业务规则校验
ValidationSummary businessValidation = businessValidator.validate(configs);
if (businessValidation.hasErrors()) {
    return PlanCreationResult.validationFailure(businessValidation);
}
```

---

**实施状态**: ✅ **完成**  
**最后更新**: 2024-11-17  
**负责人**: GitHub Copilot  
**审核状态**: 待人工审核

