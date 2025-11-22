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
   - 方法: 
     - `String stageName()` - 返回 Stage 逻辑名称（用于日志与顺序推断）
     - `boolean supports(TenantConfig)` - 判断是否需要创建该 Stage
     - `TaskStage buildStage(TenantConfig, SharedStageResources)` - 构建 Stage 实例
   - 顺序控制: 通过 Spring `@Order` 注解标注在实现类上（不在接口中定义方法）

2. **SharedStageResources** (聚合类)
   - 位置: `src/main/java/xyz/firestige/deploy/infrastructure/execution/stage/factory/SharedStageResources.java`
   - 字段: 现有 4 个依赖 + AgentService (可选)
   - 职责定义:
     - **依赖聚合器**: 集中管理所有 StageAssembler 需要的基础设施依赖，避免每个策略类重复注入
     - **不可变容器**: 构造后不可修改，保证线程安全
     - **非业务逻辑**: 只持有依赖引用，不包含任何业务方法（辅助方法抽取到 StageAssemblerUtils）
     - **启动校验**: 构造函数中校验所有必需依赖非空（除 AgentService 可选）

#### 4 个策略实现类
3. **AsbcStageAssembler** (@Component, @Order(10))
   - 位置: `src/main/java/xyz/firestige/deploy/infrastructure/execution/stage/factory/assembler/AsbcStageAssembler.java`
   - 迁移: createASBCStage() + 相关辅助方法

4. **PortalStageAssembler** (@Component, @Order(20))
   - 位置: `src/main/java/xyz/firestige/deploy/infrastructure/execution/stage/factory/assembler/PortalStageAssembler.java`
   - 迁移: createPortalStage() + 相关辅助方法

5. **BlueGreenStageAssembler** (@Component, @Order(30))
   - 位置: `src/main/java/xyz/firestige/deploy/infrastructure/execution/stage/factory/assembler/BlueGreenStageAssembler.java`
   - 迁移: createBlueGreenGatewayStage() + 相关辅助方法

6. **ObServiceStageAssembler** (@Component, @Order(40))
   - 位置: `src/main/java/xyz/firestige/deploy/infrastructure/execution/stage/factory/assembler/ObServiceStageAssembler.java`
   - 迁移: createOBServiceStage() + 相关辅助方法

#### 新工厂
7. **OrchestratedStageFactory** (@Component, implements StageFactory)
   - 位置: `src/main/java/xyz/firestige/deploy/infrastructure/execution/stage/factory/OrchestratedStageFactory.java`
   - 职责: 
     - 注入所有 StageAssembler 实现（Spring 自动装配 List<StageAssembler>）
     - 按顺序排序（@Order 注解优先，无注解则从 defaultServiceNames 推断）
     - 过滤条件（调用 supports(TenantConfig)）
     - 执行构建（调用 buildStage(TenantConfig, SharedStageResources)）
     - 启动时打印策略清单与执行顺序
   - 注入机制:
     ```java
     @Autowired
     public OrchestratedStageFactory(
         List<StageAssembler> assemblers,  // Spring 自动收集所有 @Component 实现
         RestTemplate restTemplate,
         StringRedisTemplate redisTemplate,
         DeploymentConfigLoader configLoader,
         ObjectMapper objectMapper,
         @Autowired(required = false) AgentService agentService  // 可选依赖
     ) {
         this.resources = new SharedStageResources(...);
         this.assemblers = assemblers;
         this.configLoader = configLoader;
     }
     ```
   - 顺序策略:
     1. 优先使用 `@Order` 注解值（Spring 标准注解）
     2. 无 @Order 则根据 stageName 在 defaultServiceNames 中的索引位置计算 order（索引 * 10）
     3. 两者都无则置为 Integer.MAX_VALUE（最后执行）
     4. 启动时对所有策略按最终 order 排序并缓存

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

---

## 10. 详细实现方案

### 10.1 SharedStageResources 职责与实现

**职责边界**:
```
✅ 应该做的:
- 聚合所有 StageAssembler 需要的基础设施依赖（RestTemplate, RedisTemplate, ConfigLoader, ObjectMapper, AgentService）
- 提供不可变的 getter 访问
- 构造时校验必需依赖非空

❌ 不应该做的:
- 包含任何业务逻辑方法（如 extractSourceUnit, generateToken 等）
- 持有可变状态
- 直接调用外部服务
```

**实现示例**:
```java
@Component
public class SharedStageResources {
    private final RestTemplate restTemplate;
    private final StringRedisTemplate redisTemplate;
    private final DeploymentConfigLoader configLoader;
    private final ObjectMapper objectMapper;
    private final AgentService agentService;  // 可选，OBService 使用
    
    @Autowired
    public SharedStageResources(
            RestTemplate restTemplate,
            StringRedisTemplate redisTemplate,
            DeploymentConfigLoader configLoader,
            ObjectMapper objectMapper,
            @Autowired(required = false) AgentService agentService) {
        // 启动校验
        Objects.requireNonNull(restTemplate, "RestTemplate cannot be null");
        Objects.requireNonNull(redisTemplate, "StringRedisTemplate cannot be null");
        Objects.requireNonNull(configLoader, "DeploymentConfigLoader cannot be null");
        Objects.requireNonNull(objectMapper, "ObjectMapper cannot be null");
        // agentService 可选（OBService 降级逻辑）
        
        this.restTemplate = restTemplate;
        this.redisTemplate = redisTemplate;
        this.configLoader = configLoader;
        this.objectMapper = objectMapper;
        this.agentService = agentService;
    }
    
    // 只提供 getter，无任何业务方法
    public RestTemplate getRestTemplate() { return restTemplate; }
    public StringRedisTemplate getRedisTemplate() { return redisTemplate; }
    public DeploymentConfigLoader getConfigLoader() { return configLoader; }
    public ObjectMapper getObjectMapper() { return objectMapper; }
    public AgentService getAgentService() { return agentService; }  // 可能为 null
}
```

---

### 10.2 OrchestratedStageFactory 注入与顺序控制

**核心设计问题 1: 如何注入所有 StageAssembler？**

答案：Spring 自动装配 `List<StageAssembler>`

```java
@Component
@Primary  // 优先使用新工厂
public class OrchestratedStageFactory implements StageFactory {
    
    private static final Logger log = LoggerFactory.getLogger(OrchestratedStageFactory.class);
    
    private final List<StageAssembler> sortedAssemblers;  // 启动时排序后缓存
    private final SharedStageResources resources;
    private final DeploymentConfigLoader configLoader;
    
    @Autowired
    public OrchestratedStageFactory(
            List<StageAssembler> assemblers,  // Spring 自动收集所有 @Component 实现
            SharedStageResources resources,    // 聚合依赖对象
            DeploymentConfigLoader configLoader) {
        this.resources = resources;
        this.configLoader = configLoader;
        
        // 启动时计算并缓存排序后的策略列表
        this.sortedAssemblers = sortAndCache(assemblers);
        
        // 启动日志
        logAssemblerInfo();
    }
    
    @Override
    public List<TaskStage> buildStages(TenantConfig cfg) {
        log.info("Building stages for tenant: {}", cfg.getTenantId());
        
        return sortedAssemblers.stream()
            .filter(a -> a.supports(cfg))  // 条件过滤
            .map(a -> {
                log.debug("Building stage: {}", a.stageName());
                return a.buildStage(cfg, resources);
            })
            .toList();
    }
    
    // 排序逻辑见下方
    private List<StageAssembler> sortAndCache(List<StageAssembler> assemblers) { ... }
    private void logAssemblerInfo() { ... }
}
```

**Spring 注入机制说明**:
- `List<StageAssembler> assemblers` 参数会自动收集所有标注了 `@Component` 且实现 `StageAssembler` 接口的 Bean
- 无需手动注册，添加新策略只需创建新的 @Component 类
- 顺序由 @Order 注解或后续逻辑控制

---

**核心设计问题 2: 如何控制顺序？**

答案：混合策略 - @Order 注解优先，无注解则从 defaultServiceNames 推断

```java
private List<StageAssembler> sortAndCache(List<StageAssembler> assemblers) {
    // 1. 加载配置中的默认顺序
    List<String> defaultServiceNames = configLoader.getDefaultServiceNames();
    Map<String, Integer> defaultOrderMap = new HashMap<>();
    for (int i = 0; i < defaultServiceNames.size(); i++) {
        defaultOrderMap.put(defaultServiceNames.get(i), i * 10);
    }
    
    // 2. 为每个 assembler 计算最终 order
    List<AssemblerWithOrder> withOrders = new ArrayList<>();
    for (StageAssembler assembler : assemblers) {
        int finalOrder = computeOrder(assembler, defaultOrderMap);
        withOrders.add(new AssemblerWithOrder(assembler, finalOrder));
    }
    
    // 3. 按 order 排序
    withOrders.sort(Comparator.comparingInt(AssemblerWithOrder::order));
    
    // 4. 提取排序后的 assembler 列表
    return withOrders.stream()
        .map(AssemblerWithOrder::assembler)
        .toList();
}

private int computeOrder(StageAssembler assembler, Map<String, Integer> defaultOrderMap) {
    // 策略 1: 优先使用 @Order 注解
    Order orderAnnotation = assembler.getClass().getAnnotation(Order.class);
    if (orderAnnotation != null) {
        return orderAnnotation.value();
    }
    
    // 策略 2: 从 defaultServiceNames 推断（stageName → index * 10）
    String stageName = assembler.stageName();
    Integer configOrder = defaultOrderMap.get(stageName);
    if (configOrder != null) {
        return configOrder;
    }
    
    // 策略 3: 都无则置为最后
    return Integer.MAX_VALUE;
}

private void logAssemblerInfo() {
    log.info("Loaded {} StageAssemblers:", sortedAssemblers.size());
    for (int i = 0; i < sortedAssemblers.size(); i++) {
        StageAssembler a = sortedAssemblers.get(i);
        int order = computeOrder(a, ...);  // 重新计算用于日志
        log.info("  [{}] {} (order={})", i + 1, a.stageName(), order);
    }
}

// 内部辅助类
private record AssemblerWithOrder(StageAssembler assembler, int order) {}
```

**顺序控制逻辑流程图**:
```
输入: List<StageAssembler> assemblers
  ↓
遍历每个 assembler
  ↓
检查 @Order 注解？
  ├─ 有 → 使用注解值 (优先级 1)
  └─ 无 ↓
     查找 stageName 在 defaultServiceNames 中的索引？
       ├─ 找到 → 使用 (索引 * 10) (优先级 2)
       └─ 未找到 → 使用 Integer.MAX_VALUE (优先级 3)
  ↓
按 order 升序排序
  ↓
缓存到 sortedAssemblers
  ↓
输出: 有序的 List<StageAssembler>
```

---

### 10.3 StageAssembler 接口定义

```java
public interface StageAssembler {
    
    /**
     * Stage 逻辑名称（用于日志与配置推断）
     * 例如: "asbc-gateway", "portal", "blue-green-gateway", "ob-service"
     */
    String stageName();
    
    /**
     * 判断是否需要为给定租户配置创建该 Stage
     * @param cfg 租户配置
     * @return true 表示需要创建
     */
    boolean supports(TenantConfig cfg);
    
    /**
     * 构建 Stage 实例
     * @param cfg 租户配置
     * @param resources 共享基础设施依赖
     * @return 构建的 TaskStage
     */
    TaskStage buildStage(TenantConfig cfg, SharedStageResources resources);
}
```

**说明**:
- 不包含 `order()` 方法，顺序通过 @Order 注解或配置推断
- stageName 返回值应与 defaultServiceNames 中的名称保持一致（用于推断顺序）

---

### 10.4 策略实现示例（以 Portal 为例）

```java
@Component
@Order(20)  // 显式指定顺序（可选，无注解则从配置推断）
public class PortalStageAssembler implements StageAssembler {
    
    @Override
    public String stageName() {
        return "portal";  // 对应 defaultServiceNames 中的 "portal"
    }
    
    @Override
    public boolean supports(TenantConfig cfg) {
        return cfg.getDeployUnit() != null;
    }
    
    @Override
    public TaskStage buildStage(TenantConfig cfg, SharedStageResources resources) {
        // 复制原 DynamicStageFactory.createPortalStage() 逻辑
        ConfigurableServiceStage.StepConfig stepConfig = ConfigurableServiceStage.StepConfig.builder()
            .stepName("portal-notify")
            .dataPreparer(createPortalDataPreparer(cfg, resources))
            .step(new HttpRequestStep(resources.getRestTemplate()))
            .resultValidator(createPortalResultValidator())
            .build();
        
        return new ConfigurableServiceStage(stageName(), Collections.singletonList(stepConfig));
    }
    
    // 私有辅助方法（从 DynamicStageFactory 迁移）
    private DataPreparer createPortalDataPreparer(TenantConfig cfg, SharedStageResources res) { ... }
    private ResultValidator createPortalResultValidator() { ... }
}
```

---

### 10.5 配置文件与 @Order 注解协同示例

**deploy-stages.yml**:
```yaml
defaultServiceNames:
  - asbc-gateway     # 索引 0 → order = 0 * 10 = 0  (若无 @Order)
  - portal           # 索引 1 → order = 1 * 10 = 10 (若无 @Order)
  - blue-green-gateway  # 索引 2 → order = 2 * 10 = 20 (若无 @Order)
```

**实际策略类**:
```java
@Component
@Order(10)  // 显式 @Order 优先
class AsbcStageAssembler { ... }

@Component
// 无 @Order，从配置推断: portal 在索引 1 → order = 10
class PortalStageAssembler { ... }

@Component
@Order(5)  // 显式 @Order 优先（可以插队到最前）
class CustomStageAssembler { ... }
```

**最终顺序**:
1. CustomStageAssembler (order=5, @Order 注解)
2. AsbcStageAssembler (order=10, @Order 注解)
3. PortalStageAssembler (order=10, 配置推断)
4. BlueGreenStageAssembler (order=20, 配置推断)

---

### 10.6 启动日志示例

```
INFO  OrchestratedStageFactory - Loaded 4 StageAssemblers:
INFO  OrchestratedStageFactory -   [1] asbc-gateway (order=10, source=@Order)
INFO  OrchestratedStageFactory -   [2] portal (order=20, source=@Order)
INFO  OrchestratedStageFactory -   [3] blue-green-gateway (order=30, source=@Order)
INFO  OrchestratedStageFactory -   [4] ob-service (order=40, source=@Order)
INFO  OrchestratedStageFactory - Building stages for tenant: TenantId(tenant-001)
DEBUG OrchestratedStageFactory - Building stage: asbc-gateway
DEBUG OrchestratedStageFactory - Building stage: portal
INFO  OrchestratedStageFactory - Built 2 stages
```

---

**详细实现方案完成，问题回答汇总**:

1. **StageAssembler 注入**: Spring 自动装配 `List<StageAssembler>`，无需手动注册
2. **SharedStageResources 职责**: 依赖聚合器，不含业务逻辑，只提供 getter
3. **顺序控制**: @Order 注解优先 → 配置文件推断 → Integer.MAX_VALUE 兜底

是否开始 Phase 1 实施？

