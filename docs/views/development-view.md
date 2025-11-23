# 开发视图 - 补充说明

> **最后更新**: 2025-11-22  
> **PlantUML 图**: [development-view.puml](development-view.puml)

---

## 分层架构说明

### 依赖方向规则
```
Facade Layer → Application Layer → Domain Layer ← Infrastructure Layer
```

**核心原则**:
- Infrastructure 层依赖 Domain 层的接口（依赖倒置）
- Domain 层不依赖任何外层（保持纯净）
- 上层可以依赖下层，但不能反向依赖

---

## 包结构详细说明

### Facade 层 (`xyz.firestige.deploy.facade`)

**职责**: 
- 作为系统边界，对外提供统一接口
- DTO 与领域对象的转换
- 参数校验与异常转换

**关键类**:
- `PlanFacade`: Plan 相关操作的入口
- `TaskFacade`: Task 相关操作的入口
- `PlanMapper` / `TaskMapper`: MapStruct 映射器

**代码示例**:
```java
@Component
public class PlanFacade {
    private final PlanApplicationService planApplicationService;
    private final PlanMapper planMapper;
    
    public Result<PlanDTO> createPlan(CreatePlanRequest request) {
        return planApplicationService.createPlan(request)
            .map(planMapper::toDTO);
    }
}
```

---

### Application 层 (`xyz.firestige.deploy.application`)

**职责**:
- 编排领域服务和聚合
- 定义事务边界（通常一个应用服务方法 = 一个事务）
- 协调跨聚合的操作

**关键类**:
- `PlanApplicationService`: Plan 应用服务
- `TaskApplicationService`: Task 应用服务
- `PlanAssembler`: Plan 聚合的组装器

**事务边界示例**:
```java
@Service
@Transactional
public class PlanApplicationService {
    private final PlanLifecycleService planLifecycleService;
    private final PlanRepository planRepository;
    
    public Result<Plan> startPlan(Long planId) {
        // 整个方法在一个事务内
        return planRepository.findById(PlanId.of(planId))
            .flatMap(planLifecycleService::startPlan)
            .map(planRepository::save);
    }
}
```

---

### Domain 层 (`xyz.firestige.deploy.domain`)

**职责**:
- 封装核心业务逻辑
- 保证领域不变式
- 定义领域接口（仓储、执行器）

#### model 包
包含所有领域对象：
- **聚合根**: `Plan`
- **实体**: `Task`
- **值对象**: `PlanId`, `TaskId`, `Checkpoint`, `FailureInfo`
- **枚举**: `PlanState`, `TaskState`, `ExecutorType`

#### service 包
领域服务，处理不属于单个聚合的领域逻辑：
- `PlanLifecycleService`: Plan 生命周期管理
- `TaskOperationService`: Task 操作协调

#### repository 包
仓储接口（实现在 Infrastructure 层）：
- `PlanRepository`: Plan 聚合仓储
- `TaskRepository`: Task 查询仓储

#### executor 包
执行器策略接口与实现：
- `TaskExecutor`: 策略接口
- `BlueGreenSwitchExecutor`: 蓝绿切换实现
- `ExecutorFactory`: 执行器工厂

**领域模型示例**:
```java
@Entity
public class Plan {
    private PlanId planId;
    private String name;
    private PlanState state;
    private List<Task> tasks;
    
    public Result<Void> start() {
        // 业务规则检查
        if (!state.canTransitionTo(PlanState.RUNNING)) {
            return Result.failure("Invalid state transition");
        }
        
        this.state = PlanState.RUNNING;
        return Result.success();
    }
}
```

---

### Infrastructure 层 (`xyz.firestige.deploy.infrastructure`)

**职责**:
- 实现领域层定义的接口
- 提供技术基础设施（持久化、消息、配置）
- 集成外部服务

#### persistence 包
JPA 持久化实现：
- `PlanRepositoryImpl`: 实现 `PlanRepository`
- `PlanJpaEntity`: JPA 实体（与领域模型分离）
- `PlanJpaRepository`: Spring Data JPA 接口

**数据模型转换**:
```java
@Repository
public class PlanRepositoryImpl implements PlanRepository {
    private final PlanJpaRepository jpaRepository;
    
    @Override
    public Optional<Plan> findById(PlanId planId) {
        return jpaRepository.findById(planId.getValue())
            .map(this::toDomain);
    }
    
    private Plan toDomain(PlanJpaEntity entity) {
        // JPA Entity → Domain Model 转换
    }
}
```

#### gateway 包
外部服务集成：
- `ObServiceGateway`: OB 服务网关接口
- `ObServiceGatewayImpl`: 实现（HTTP 调用）

#### config 包
框架配置：
- `JpaConfig`: JPA 配置
- `TransactionConfig`: 事务管理配置

---

## 依赖管理

### Maven 模块划分（可选优化）
当前单体应用，未来可拆分为：
```
executor-demo
├── executor-domain        (纯领域模型，无外部依赖)
├── executor-application   (依赖 domain)
├── executor-infrastructure (依赖 domain)
└── executor-facade        (依赖 application + infrastructure)
```

### 依赖约束
- Domain 层：只依赖 JDK 和 Jakarta Validation
- Application 层：依赖 Domain + Spring Core
- Infrastructure 层：依赖 Domain + Spring Data JPA + 第三方库
- Facade 层：依赖 Application + Spring Web

---

## 代码组织规范

### 命名约定
- **Facade**: `*Facade`
- **Application Service**: `*ApplicationService`
- **Domain Service**: `*Service` (接口) + `*ServiceImpl` (实现)
- **Repository**: `*Repository` (接口) + `*RepositoryImpl` (实现)
- **DTO**: `*DTO`, `*Request`, `*Response`
- **Mapper**: `*Mapper`

### 包可见性
- Domain 层的实体字段使用 `private`，通过方法暴露
- Repository 接口在 Domain 层 public，实现在 Infrastructure 层
- Domain Service 实现可以是 package-private（如果仅内部使用）

---

## 测试策略

### 单元测试
- **Domain 层**: 纯单元测试，无需 Spring 容器
- **Application 层**: Mock Repository 和 Domain Service
- **Facade 层**: Mock Application Service

### 集成测试
- **Repository**: 使用 `@DataJpaTest` + H2
- **Application Service**: 使用 `@SpringBootTest` + 事务回滚
- **Facade**: 使用 `@WebMvcTest`

**测试示例**:
```java
// Domain 层单元测试
class PlanTest {
    @Test
    void shouldStartPlanSuccessfully() {
        Plan plan = Plan.create("test", List.of(...));
        Result<Void> result = plan.start();
        
        assertTrue(result.isSuccess());
        assertEquals(PlanState.RUNNING, plan.getState());
    }
}

// Repository 集成测试
@DataJpaTest
class PlanRepositoryImplTest {
    @Autowired
    private PlanRepository planRepository;
    
    @Test
    void shouldSaveAndFindPlan() {
        Plan plan = Plan.create("test", List.of(...));
        Plan saved = planRepository.save(plan);
        
        Optional<Plan> found = planRepository.findById(saved.getPlanId());
        assertTrue(found.isPresent());
    }
}
```

---

## 相关文档

- [架构总纲](../architecture-overview.md)
- [逻辑视图](logical-view.puml) - 领域模型
- [持久化设计](../design/persistence.md)
- [事务策略](../design/transaction-strategy.md)

