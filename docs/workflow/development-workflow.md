# 开发工作流规范

> **最后更新**: 2025-11-22  
> **状态**: Active

---

## 概述

本文档定义项目的开发流程、代码规范、Git 提交规范和测试策略。

---

## 开发流程

### 1. 需求接收
- 在 `TODO.md` 创建新任务
- 分配任务 ID（T-xxx）和优先级（P1/P2/P3）
- 明确任务目标和验收标准

### 2. 设计阶段
- 任务开始时，创建 `docs/temp/task-{ID}-{描述}.md` 设计方案
- 方案包含：背景、目标、方案选项、选定方案、影响分析
- 涉及架构变更的任务必须先评审设计方案

### 3. 开发阶段
- 从 `main` 分支拉取最新代码
- 创建特性分支：`feature/T-{ID}-{简短描述}`
- 遵循代码规范进行开发
- 每日更新 `developlog.md`（记录关键进展）

### 4. 测试阶段
- 编写单元测试（Domain 层 100% 覆盖）
- 编写集成测试（关键流程覆盖）
- 本地运行测试，确保全部通过
- 使用 JMH 进行性能测试（如涉及性能优化）

### 5. 代码审查
- 提交 Pull Request
- PR 标题格式：`[T-{ID}] {任务描述}`
- PR 描述包含：
  - 任务背景
  - 主要变更
  - 测试结果
  - 文档更新（如有）
- 至少 1 人审查通过后合并

### 6. 完成阶段
- 合并到 `main` 分支
- 从 `TODO.md` 删除任务
- 提取设计方案核心内容到正式文档
- 在 `developlog.md` 顶部添加完成记录
- 删除或归档临时设计文档

---

## 代码规范

### 包结构
```
xyz.firestige.deploy/
├── facade/           # 防腐层（DTO、Facade）
├── application/      # 应用服务层（编排、事务）
├── domain/           # 领域层（聚合、实体、值对象、领域服务）
└── infrastructure/   # 基础设施层（仓储实现、执行引擎、外部集成）
    └── execution/    # ⭐ 核心执行引擎（最重要）
```

**分层依赖规则**:
- Facade → Application → Domain ← Infrastructure
- Domain 层不依赖任何外层
- Infrastructure 实现 Domain 定义的接口

### 命名约定

#### 类命名
- **Facade**: `*Facade`（如 `DeploymentTaskFacade`）
- **Application Service**: `*ApplicationService`
- **Domain Service**: `*Service`（接口）+ `*ServiceImpl`（实现）
- **Repository**: `*Repository`（接口）+ `InMemory*Repository` 或 `Redis*Repository`（实现）
- **Executor**: `*Executor`、`*Engine`
- **Value Object**: `*Id`、`*Info`、`*Context`、`*Config`
- **Event**: `*Event`

#### 方法命名
- **查询**: `get*()`, `find*()`, `query*()`
- **命令**: `create*()`, `update*()`, `delete*()`, `execute*()`
- **业务操作**: `start()`, `pause()`, `resume()`, `retry()`, `rollback()`
- **验证**: `validate*()`, `check*()`
- **转换**: `to*()`, `from*()`, `map*()`

#### 变量命名
- 使用完整单词，避免缩写（除非是通用缩写如 `id`、`dto`）
- 布尔变量使用 `is*`、`has*`、`can*` 前缀
- 集合变量使用复数形式（`tasks`、`stages`）

### 代码风格

#### Result 模式
领域层方法返回 `Result<T>` 而非抛出异常：
```java
// ✅ 推荐
public Result<Void> start() {
    if (!canStart()) {
        return Result.failure("Cannot start in current state");
    }
    this.state = PlanState.RUNNING;
    return Result.success();
}

// ❌ 避免
public void start() throws IllegalStateException {
    if (!canStart()) {
        throw new IllegalStateException("Cannot start");
    }
    this.state = PlanState.RUNNING;
}
```

#### Optional 使用
处理可能不存在的对象：
```java
// ✅ 推荐
public Optional<Task> findTaskById(TaskId taskId) {
    return taskRepository.findById(taskId);
}

// ❌ 避免返回 null
public Task findTaskById(TaskId taskId) {
    return taskRepository.findById(taskId).orElse(null);
}
```

#### 不可变对象
值对象和事件应该是不可变的：
```java
public final class TaskId {
    private final String value;
    
    private TaskId(String value) {
        this.value = value;
    }
    
    public static TaskId of(String value) {
        return new TaskId(value);
    }
    
    public String getValue() {
        return value;
    }
}
```

#### 防御式编程
```java
public Result<Void> addTask(Task task) {
    if (task == null) {
        return Result.failure("Task cannot be null");
    }
    if (this.state != PlanState.CREATED) {
        return Result.failure("Cannot add task to non-created plan");
    }
    this.tasks.add(task);
    return Result.success();
}
```

---

## Git 工作流

### 分支策略
- **main**: 主分支，始终保持可发布状态
- **feature/T-{ID}-{描述}**: 特性分支，开发新功能
- **bugfix/T-{ID}-{描述}**: Bug 修复分支
- **hotfix/{描述}**: 紧急修复分支

### 提交规范（Conventional Commits）

**格式**: `<type>(<scope>): <subject>`

**类型（type）**:
- `feat`: 新功能
- `fix`: Bug 修复
- `refactor`: 重构（不改变功能）
- `docs`: 文档更新
- `test`: 测试相关
- `chore`: 构建/工具相关
- `perf`: 性能优化

**范围（scope）**: 可选，表示影响的模块
- `facade`, `application`, `domain`, `infrastructure`
- `execution`, `persistence`, `state`

**主题（subject）**: 简短描述（50 字符内）

**示例**:
```bash
feat(execution): add checkpoint support for task retry
fix(domain): correct task state transition validation
refactor(infrastructure): simplify redis repository implementation
docs: update architecture overview with execution engine design
test(execution): add integration tests for stage executor
```

**Commit Message 长度规范**:
- **标题（第一行）**: 最多 **240 个字符**（约 80 个中文字符）
- **超长时**: 拆分为多个 commit 或使用 Body 详细说明
- **Body**: 每行最多 72 字符，用于详细描述

**提交 Body**（可选）:
```bash
git commit -m "feat(execution): add checkpoint support for task retry

- Implement checkpoint save/load in TaskExecutionEngine
- Add fromCheckpoint parameter to retry API
- Update Task state machine to support checkpoint recovery

Relates to T-105"
```

### Pull Request 规范

**标题**: `[T-{ID}] {任务描述}`

**描述模板**:
```markdown
## 任务背景
解决 #T-105：引入 Checkpoint 断点续传机制

## 主要变更
- [ ] 实现 Checkpoint 保存/加载逻辑
- [ ] 扩展 Task.retry() 支持 fromCheckpoint 参数
- [ ] 更新状态机支持断点恢复
- [ ] 添加单元测试和集成测试

## 测试结果
- ✅ 单元测试: 100% 通过
- ✅ 集成测试: 5/5 通过
- ✅ 性能测试: 无退化

## 文档更新
- [x] 更新 docs/design/checkpoint-mechanism.md
- [x] 更新 docs/views/process-view.puml
- [x] 更新 developlog.md

## 审查重点
请重点关注 TaskExecutionEngine 中的 Checkpoint 恢复逻辑
```

---

## 测试策略

### 测试分层

#### 1. 单元测试（Unit Tests）
- **位置**: `src/test/java/xyz/firestige/deploy/domain`
- **框架**: JUnit 5 + Mockito
- **覆盖目标**: Domain 层 100%
- **特点**: 
  - 不依赖 Spring 容器
  - Mock 所有外部依赖
  - 快速执行（< 1秒）

**示例**:
```java
class PlanTest {
    @Test
    void shouldStartPlanSuccessfully() {
        // Given
        Plan plan = Plan.create("test-plan", List.of(task1, task2));
        
        // When
        Result<Void> result = plan.start();
        
        // Then
        assertTrue(result.isSuccess());
        assertEquals(PlanState.RUNNING, plan.getState());
    }
}
```

#### 2. 集成测试（Integration Tests）
- **位置**: `src/test/java/xyz/firestige/deploy/infrastructure`
- **框架**: Spring Boot Test + Testcontainers
- **覆盖目标**: 关键流程 + 外部依赖集成
- **特点**:
  - 使用 `@SpringBootTest`
  - Testcontainers 启动 Redis
  - 测试真实的持久化逻辑

**示例**:
```java
@SpringBootTest
@Testcontainers
class TaskExecutionEngineIntegrationTest {
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7")
        .withExposedPorts(6379);
    
    @Autowired
    private TaskExecutionEngine engine;
    
    @Test
    void shouldExecuteTaskWithCheckpoint() {
        // 测试真实执行流程
    }
}
```

#### 3. 性能测试（Benchmark Tests）
- **框架**: JMH
- **用途**: 关键路径性能测试
- **执行**: `mvn test -Pbenchmark`

**示例**:
```java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class TaskExecutionBenchmark {
    @Benchmark
    public void benchmarkTaskExecution() {
        // 性能测试
    }
}
```

### 测试规范

#### 测试命名
- 单元测试: `should{ExpectedBehavior}When{StateUnderTest}`
- 集成测试: `should{ExpectedBehavior}Integration`

#### 测试结构（Given-When-Then）
```java
@Test
void shouldReturnFailureWhenTaskNotFound() {
    // Given: 准备测试数据
    TaskId nonExistentId = TaskId.of("non-existent");
    
    // When: 执行被测方法
    Result<Task> result = taskRepository.findById(nonExistentId);
    
    // Then: 验证结果
    assertTrue(result.isFailure());
    assertEquals("Task not found", result.getError().getMessage());
}
```

#### Mock 使用
- 优先使用真实对象
- 仅 Mock 外部依赖（数据库、HTTP 客户端）
- Domain 层不应该有 Mock（纯单元测试）

---

## 代码审查清单

### 功能正确性
- [ ] 功能符合需求
- [ ] 边界条件处理正确
- [ ] 错误处理完善

### 代码质量
- [ ] 符合命名规范
- [ ] 无重复代码
- [ ] 方法长度合理（< 30 行）
- [ ] 类职责单一

### 架构合规
- [ ] 遵循分层架构
- [ ] 依赖方向正确
- [ ] Domain 层纯净（无外部依赖）

### 测试覆盖
- [ ] 单元测试覆盖核心逻辑
- [ ] 集成测试覆盖关键流程
- [ ] 测试命名清晰

### 文档更新
- [ ] 代码注释充分
- [ ] 更新相关设计文档
- [ ] 更新 developlog.md

---

## 性能优化指南

### 性能目标
- Task 创建: < 100ms
- Task 执行: 视 Stage 数量，单个 Stage < 5s
- Checkpoint 保存/加载: < 50ms
- 并发执行: 支持 100+ Task 同时运行

### 优化策略
1. **异步执行**: 使用线程池并发执行 Task
2. **缓存**: InMemory 缓存热点数据
3. **批量操作**: Redis 批量读写
4. **避免阻塞**: 使用异步 HTTP 客户端

---

## 相关文档

- [文档更新流程](documentation-workflow.md) - 文档维护规范
- [架构总纲](../architecture-overview.md) - 架构设计原则
- [技术栈清单](../tech-stack.md) - 使用的技术和框架

