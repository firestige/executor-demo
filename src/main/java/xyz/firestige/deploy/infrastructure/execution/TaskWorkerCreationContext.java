package xyz.firestige.deploy.infrastructure.execution;

import java.util.List;

import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;

/**
 * TaskWorker 创建上下文
 * <p>
 * 封装 TaskExecutor 创建所需的领域数据，使用 Builder 模式提升可读性和可维护性。
 * <p>
 * 设计目标（RF-02）：
 * - 减少方法参数数量（9个 → 1个）
 * - 提升代码可读性（Builder 模式 + 命名参数风格）
 * - 便于扩展（新增参数不影响现有调用）
 * - 参数验证集中化
 * <p>
 * RF-17 重构：
 * - 只保留领域数据（planId, task, stages, runtimeContext）
 * - 基础设施依赖通过 DefaultTaskWorkerFactory 构造器注入
 * - existingExecutor 用于复用场景
 *
 * @since RF-02
 */
public class TaskWorkerCreationContext {

    // Domain data
    private final PlanId planId;
    private final TaskAggregate task;
    private final List<TaskStage> stages;
    private final TaskRuntimeContext runtimeContext;
    private final TaskExecutor existingExecutor;

    private TaskWorkerCreationContext(Builder builder) {
        this.planId = builder.planId;
        this.task = builder.task;
        this.stages = builder.stages;
        this.runtimeContext = builder.runtimeContext;
        this.existingExecutor = builder.existingExecutor;
    }

    // Getters

    public PlanId getPlanId() {
        return planId;
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

    // Static factory method for builder

    public static Builder builder() {
        return new Builder();
    }

    // Builder class

    public static class Builder {
        // Required parameters (no defaults)
        private PlanId planId;
        private TaskAggregate task;
        private List<TaskStage> stages;
        private TaskRuntimeContext runtimeContext;
        private TaskExecutor existingExecutor;

        private Builder() {}

        public Builder planId(PlanId planId) {
            this.planId = planId;
            return this;
        }

        public Builder task(TaskAggregate task) {
            this.task = task;
            return this;
        }

        public Builder stages(List<TaskStage> stages) {
            this.stages = stages;
            return this;
        }

        public Builder runtimeContext(TaskRuntimeContext runtimeContext) {
            this.runtimeContext = runtimeContext;
            return this;
        }

        public Builder existingExecutor(TaskExecutor existingExecutor) {
            this.existingExecutor = existingExecutor;
            return this;
        }

        /**
         * 构建 TaskWorkerCreationContext，验证必需参数
         *
         * @return 创建的上下文对象
         * @throws IllegalArgumentException 如果必需参数缺失
         */
        public TaskWorkerCreationContext build() {
            // Validate required parameters
            if (planId == null) {
                throw new IllegalArgumentException("planId is required");
            }
            if (task == null) {
                throw new IllegalArgumentException("task is required");
            }
            if (stages == null) {
                throw new IllegalArgumentException("stages is required");
            }
            if (runtimeContext == null) {
                throw new IllegalArgumentException("runtimeContext is required");
            }

            return new TaskWorkerCreationContext(this);
        }
    }
}