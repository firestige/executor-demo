package xyz.firestige.deploy.domain.task;

import java.util.List;

import xyz.firestige.deploy.domain.stage.TaskStage;
import xyz.firestige.deploy.execution.TaskExecutor;

/**
 * Task 执行上下文（RF-15: 领域层与执行层解耦）
 * 
 * 用于在领域层和应用层之间传递执行所需的数据。
 * 领域服务准备好聚合和运行时数据，应用层负责创建执行器并执行。
 *
 * @since RF-15: TaskDomainService 执行层解耦
 */
public class TaskExecutionContext {

    private final TaskAggregate task;
    private final List<TaskStage> stages;
    private final TaskRuntimeContext runtimeContext;
    private final TaskExecutor existingExecutor;

    public TaskExecutionContext(
            TaskAggregate task,
            List<TaskStage> stages,
            TaskRuntimeContext runtimeContext,
            TaskExecutor existingExecutor) {
        this.task = task;
        this.stages = stages;
        this.runtimeContext = runtimeContext;
        this.existingExecutor = existingExecutor;
    }

    public TaskAggregate getTask() {
        return task;
    }

    public List<TaskStage> getStages() {
        return stages;
    }

    public TaskRuntimeContext getRuntimeContext() {
        return runtimeContext;
    }

    public TaskExecutor getExistingExecutor() {
        return existingExecutor;
    }

    public boolean hasExistingExecutor() {
        return existingExecutor != null;
    }
}
