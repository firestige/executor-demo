# 文档更新流程

> **最后更新**: 2025-11-22  
> **状态**: Active

---

## 流程概述

文档更新与任务生命周期同步，确保文档始终反映当前架构设计和实现状态。

```
TODO 任务 → 临时方案 → 实施 → 归档总结 → 更新架构文档
```

---

## 任务启动阶段

### 1. 创建任务（TODO.md）

在 `TODO.md` 添加新任务：

```markdown
## 待办 (Backlog)
| ID | 任务 | 负责人 | 优先级 | 备注 |
|----|------|--------|--------|------|
| T-105 | 引入领域事件机制 | Alice | P1 | 解耦 Plan 与 Task |
```

### 2. 任务开始时

**步骤**:
1. 将任务从"待办"移到"进行中"
2. 在 `docs/temp/` 创建临时方案文档
3. 在 TODO 表格添加方案链接
4. 在 `developlog.md` 顶部记录任务启动

**TODO.md 更新**:
```markdown
## 进行中 (In Progress)
| ID | 任务 | 负责人 | 优先级 | 开始日期 | 预计完成 | 临时方案 |
|----|------|--------|--------|----------|----------|----------|
| T-105 | 引入领域事件机制 | Alice | P1 | 2025-11-23 | 2025-11-28 | [方案](docs/temp/task-105-domain-events.md) |
```

**developlog.md 更新**:
```markdown
# 开发日志

2025-11-23: [T-105] 开始引入领域事件机制；创建临时方案 task-105-domain-events.md
...
```

### 3. 临时方案文档模板

在 `docs/temp/task-{ID}-{简短描述}.md` 创建方案文档：

```markdown
# T-105: 引入领域事件机制

> **任务 ID**: T-105  
> **负责人**: Alice  
> **创建日期**: 2025-11-23  
> **状态**: 进行中

---

## 背景

当前 Plan 和 Task 状态变更时，需要手动触发关联操作（如通知、审计日志）。
耦合度高，扩展性差。

## 目标

- 引入领域事件，实现状态变更时的解耦通知
- 支持事件持久化和异步处理
- 为未来的事件溯源做准备

## 方案选项

### 方案 A: Spring Events
- **优点**: 轻量级，Spring 内置
- **缺点**: 同步执行，无持久化

### 方案 B: Spring Events + @Async
- **优点**: 异步处理，简单
- **缺点**: 无持久化，应用重启丢失

### 方案 C: 领域事件 + Event Store
- **优点**: 持久化，支持事件溯源
- **缺点**: 实现复杂，引入新组件

## 选定方案

**方案 B**: Spring Events + @Async

**理由**:
- 满足当前解耦需求
- 实现简单，无需额外组件
- 未来可升级到方案 C

## 设计细节

### 事件定义
```java
public abstract class DomainEvent {
    private final LocalDateTime occurredOn;
    private final AggregateId aggregateId;
}

public class PlanStartedEvent extends DomainEvent {
    private final PlanId planId;
    private final List<TaskId> taskIds;
}
```

### 事件发布
```java
public class Plan {
    @Transient
    private List<DomainEvent> domainEvents = new ArrayList<>();
    
    public Result<Void> start() {
        // 业务逻辑
        this.state = PlanState.RUNNING;
        
        // 发布事件
        this.domainEvents.add(new PlanStartedEvent(this.planId, this.taskIds));
        
        return Result.success();
    }
}
```

### 事件监听
```java
@Component
public class PlanEventHandler {
    @Async
    @EventListener
    public void handlePlanStarted(PlanStartedEvent event) {
        // 记录审计日志
        // 发送通知
    }
}
```

## 影响分析

### 修改文件
- `Plan.java`: 添加事件发布逻辑
- `Task.java`: 添加事件发布逻辑
- `PlanApplicationService.java`: 事件分发
- 新增: `DomainEvent.java`, `PlanStartedEvent.java`, `PlanEventHandler.java`

### 影响文档
- `docs/architecture-overview.md`: 更新架构原则
- `docs/views/logical-view.puml`: 添加事件类
- `docs/design/domain-model.md`: 添加事件说明

### 测试计划
- 单元测试: 事件发布逻辑
- 集成测试: 事件监听器触发

## 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 异步事件丢失 | 审计日志缺失 | 记录事件发布日志 |
| 事件处理失败 | 功能异常 | 添加重试机制 |
| 性能影响 | 响应变慢 | 监控事件处理时间 |

## 实施计划

- [x] 2025-11-23: 创建方案文档
- [ ] 2025-11-24: 实现 DomainEvent 基类
- [ ] 2025-11-25: 实现 Plan/Task 事件发布
- [ ] 2025-11-26: 实现事件监听器
- [ ] 2025-11-27: 编写测试
- [ ] 2025-11-28: 更新文档，合并代码

## 参考资料

- [Domain Events Pattern](https://martinfowler.com/eaaDev/DomainEvent.html)
- [Spring Events Documentation](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#context-functionality-events)
```

---

## 任务进行阶段

### 日常更新

**每日更新 developlog.md**:
```markdown
2025-11-24: [T-105] 实现 DomainEvent 基类和 PlanStartedEvent
2025-11-25: [T-105] 为 Plan.start() 添加事件发布逻辑；更新单元测试
```

**更新临时方案文档**:
- 标记完成的步骤（勾选 checklist）
- 记录遇到的问题和解决方案
- 调整设计细节（如有变化）

---

## 任务完成阶段

### 1. 从 TODO 删除任务

从 `TODO.md` 的"进行中"区块删除该任务行。

### 2. 提取方案核心内容

将 `docs/temp/task-105-domain-events.md` 的核心设计提取到正式文档：

#### 更新 `docs/architecture-overview.md`

在"核心架构原则"添加：
```markdown
### 5. 领域事件驱动
- 聚合状态变更时发布领域事件
- 通过事件实现模块间解耦
- 支持异步处理和扩展
```

在"最近重大更新"添加：
```markdown
- **2025-11-28**: 引入领域事件机制（T-105）
```

#### 创建或更新 `docs/design/domain-events.md`

```markdown
# 领域事件设计

> **最后更新**: 2025-11-28  
> **状态**: Active  
> **相关任务**: T-105

---

## 概述

领域事件用于解耦聚合间的交互，实现状态变更的异步通知。

## 事件定义

### DomainEvent 基类
所有领域事件继承此基类，包含通用属性：
- `occurredOn`: 事件发生时间
- `aggregateId`: 聚合根标识

### 当前事件列表

| 事件 | 触发时机 | 用途 |
|------|---------|------|
| PlanStartedEvent | Plan.start() | 记录审计日志、发送通知 |
| PlanCompletedEvent | Plan.complete() | 记录完成时间、统计 |
| TaskFailedEvent | Task.fail() | 告警、触发重试策略 |

## 事件发布机制

聚合根内部收集事件，由 ApplicationService 统一发布：

```java
@Service
@Transactional
public class PlanApplicationService {
    private final ApplicationEventPublisher eventPublisher;
    
    public Result<Plan> startPlan(Long planId) {
        return planRepository.findById(PlanId.of(planId))
            .flatMap(plan -> {
                Result<Void> result = plan.start();
                
                // 发布领域事件
                plan.getDomainEvents().forEach(eventPublisher::publishEvent);
                plan.clearDomainEvents();
                
                return result.map(() -> planRepository.save(plan));
            });
    }
}
```

## 事件监听

使用 Spring @EventListener + @Async 实现异步处理：

```java
@Component
public class PlanEventHandler {
    @Async
    @EventListener
    public void handlePlanStarted(PlanStartedEvent event) {
        log.info("Plan {} started at {}", event.getPlanId(), event.getOccurredOn());
        // 记录审计日志
        // 发送通知
    }
}
```

## 配置

启用异步事件处理：

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("event-");
        executor.initialize();
        return executor;
    }
}
```

## 未来扩展

- 引入 Event Store 持久化事件
- 实现事件溯源（Event Sourcing）
- 支持事件重放和时间旅行调试

## 相关文档

- [架构总纲](../architecture-overview.md)
- [逻辑视图](../views/logical-view.puml)
```

#### 更新 `docs/views/logical-view.puml`

添加领域事件相关类。

#### 更新 `docs/prompts/architecture-prompt.md`

在"领域事件"章节添加说明。

### 3. 更新 developlog.md

在顶部添加完成记录：

```markdown
2025-11-28: [T-105] 完成领域事件机制引入；更新 architecture-overview.md, domain-events.md, logical-view.puml, architecture-prompt.md
```

### 4. 归档或删除临时方案

**选项 A**: 删除 `docs/temp/task-105-domain-events.md`（核心内容已提取）

**选项 B**: 移动到 `docs/temp/archive/`（保留详细实施记录）

**推荐**: 选项 A（保持 temp 目录干净）

---

## 定期维护

### 每周审查（建议周五）

1. 检查 `TODO.md` 中"进行中"任务进度
2. 检查 `docs/temp/` 是否有遗留的临时方案
3. 确保 `developlog.md` 记录了本周的关键变更

### 每月审查（建议月初）

1. 审查 `docs/architecture-overview.md` 是否需要更新
2. 检查 `developlog.md` 长度，考虑是否归档
3. 审查 `docs/design/` 中的文档状态（active/deprecated）

### 每季度审查

1. 检查文档与代码的一致性
2. 清理过期或重复的文档
3. 优化文档结构和索引

---

## 文档版本控制

### Git Commit 规范

```bash
# 新增文档
docs: add domain-events design document

# 更新文档
docs: update architecture-overview with domain events

# 归档文档
docs: archive RF19 temporary design documents

# 完成任务相关文档更新
docs(T-105): complete domain-events implementation and update architecture docs
```

### 分支策略

- 文档更新与代码更改在同一分支
- PR 中同时审查代码和文档
- 文档更新不单独发版

---

## 常见问题

### Q: 临时方案文档必须写吗？
A: 对于 P1 任务或涉及架构变更的任务，强烈建议编写。简单的 Bug 修复可以跳过。

### Q: 如果任务跨越多个迭代怎么办？
A: 在临时方案文档中分阶段记录，每个阶段完成后更新 developlog。任务完全结束后再合入正式文档。

### Q: 如何处理方案调整？
A: 在临时方案文档中记录调整原因和新方案，保持完整的决策历史。最终合入正式文档时只保留最终方案。

### Q: developlog 太长怎么办？
A: 每年归档一次，创建 `developlog-2025.md`，当前 `developlog.md` 保留最近 6 个月的记录。

---

## 相关文档

- [TODO.md](../../TODO.md) - 任务跟踪
- [developlog.md](../../developlog.md) - 开发日志
- [架构总纲](../architecture-overview.md) - 架构入口
- [开发工作流](development-workflow.md) - 开发流程规范

