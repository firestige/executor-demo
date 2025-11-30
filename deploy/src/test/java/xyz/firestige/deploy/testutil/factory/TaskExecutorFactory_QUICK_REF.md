# TaskExecutorFactory 快速参考

## 一行代码创建 TaskExecutor

```java
@Autowired TaskExecutorFactory factory;

TaskExecutor executor = factory.create(task, stages);
```

## 常用 API

| 方法 | 用途 | 示例 |
|------|------|------|
| `create(task, stages)` | 标准配置 | `factory.create(task, stages)` |
| `create(task, stages, context)` | 自定义 Context | `factory.create(task, stages, pauseContext)` |
| `builder()` | 复杂配置 | `factory.builder().task(task).build()` |

## 事件跟踪

```java
@Autowired TestEventTracker eventTracker;

// 获取所有事件
List<TrackedEvent> events = eventTracker.getEvents();

// 状态历史
List<TaskStatus> history = eventTracker.getTaskStatusHistory(taskId);

// Stage 执行
List<String> stages = eventTracker.getExecutedStages(taskId);

// 特定类型事件
List<TrackedEvent> fails = eventTracker.getEventsOfType(taskId, EventType.TASK_FAILED);
```

## 完整示例

```java
@SpringBootTest
public class MyTest {
    @Autowired TaskExecutorFactory factory;
    @Autowired TestEventTracker tracker;
    
    @BeforeEach void setUp() { tracker.clear(); }
    
    @Test
    void test() {
        TaskAggregate task = new TaskAggregateTestBuilder().buildPending();
        TaskExecutor executor = factory.create(task, StageListTestFactory.threeSuccessStages());
        
        TaskResult result = executor.execute();
        
        assertThat(result.isSuccess()).isTrue();
        assertThat(tracker.getTaskStatusHistory(task.getTaskId()))
            .containsExactly(TaskStatus.RUNNING, TaskStatus.COMPLETED);
    }
}
```

## 测试场景

| 场景 | Stage 工厂方法 |
|------|----------------|
| 成功执行 | `StageListTestFactory.threeSuccessStages()` |
| 中途失败 | `StageListTestFactory.failAtThirdStage()` |
| 重试成功 | `StageListTestFactory.failOnceAtSecondStage()` |
| 慢速执行 | `StageListTestFactory.slowStages()` |

## 事件类型

- `TASK_STARTED`
- `TASK_COMPLETED`
- `TASK_FAILED`
- `TASK_PAUSED`
- `TASK_RESUMED`
- `STAGE_STARTED`
- `STAGE_COMPLETED`
- `STAGE_FAILED`

## 详细文档

📖 查看 `TaskExecutorFactory_README.md` 获取完整文档

