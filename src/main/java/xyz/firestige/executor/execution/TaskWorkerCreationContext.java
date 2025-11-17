package xyz.firestige.executor.execution;

import xyz.firestige.executor.checkpoint.CheckpointService;
import xyz.firestige.executor.domain.stage.TaskStage;
import xyz.firestige.executor.domain.task.TaskAggregate;
import xyz.firestige.executor.domain.task.TaskRuntimeContext;
import xyz.firestige.executor.event.TaskEventSink;
import xyz.firestige.executor.state.TaskStateManager;
import xyz.firestige.executor.support.conflict.ConflictRegistry;

import java.util.List;

/**
 * TaskWorker 创建上下文
 *
 * 封装 TaskExecutor 创建所需的所有参数，使用 Builder 模式提升可读性和可维护性。
 *
 * 设计目标（RF-02）：
 * - 减少方法参数数量（9个 → 1个）
 * - 提升代码可读性（Builder 模式 + 命名参数风格）
 * - 便于扩展（新增参数不影响现有调用）
 * - 参数验证集中化
 *
 * @since RF-02
 */
public class TaskWorkerCreationContext {

    // Required parameters
    private final String planId;
    private final TaskAggregate task;
    private final List<TaskStage> stages;
    private final TaskRuntimeContext runtimeContext;
    private final CheckpointService checkpointService;
    private final TaskEventSink eventSink;
    private final TaskStateManager stateManager;

    // Optional parameters with defaults
    private final int progressIntervalSeconds;
    private final ConflictRegistry conflictRegistry;

    private TaskWorkerCreationContext(Builder builder) {
        this.planId = builder.planId;
        this.task = builder.task;
        this.stages = builder.stages;
        this.runtimeContext = builder.runtimeContext;
        this.checkpointService = builder.checkpointService;
        this.eventSink = builder.eventSink;
        this.stateManager = builder.stateManager;
        this.progressIntervalSeconds = builder.progressIntervalSeconds;
        this.conflictRegistry = builder.conflictRegistry;
    }

    // Getters

    public String getPlanId() {
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

    public CheckpointService getCheckpointService() {
        return checkpointService;
    }

    public TaskEventSink getEventSink() {
        return eventSink;
    }

    public TaskStateManager getStateManager() {
        return stateManager;
    }

    public int getProgressIntervalSeconds() {
        return progressIntervalSeconds;
    }

    public ConflictRegistry getConflictRegistry() {
        return conflictRegistry;
    }

    // Static factory method for builder

    public static Builder builder() {
        return new Builder();
    }

    // Builder class

    public static class Builder {
        // Required parameters (no defaults)
        private String planId;
        private TaskAggregate task;
        private List<TaskStage> stages;
        private TaskRuntimeContext runtimeContext;
        private CheckpointService checkpointService;
        private TaskEventSink eventSink;
        private TaskStateManager stateManager;

        // Optional parameters with defaults
        private int progressIntervalSeconds = 10;  // default: 10 seconds
        private ConflictRegistry conflictRegistry = null;  // optional

        private Builder() {}

        public Builder planId(String planId) {
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

        public Builder checkpointService(CheckpointService checkpointService) {
            this.checkpointService = checkpointService;
            return this;
        }

        public Builder eventSink(TaskEventSink eventSink) {
            this.eventSink = eventSink;
            return this;
        }

        public Builder stateManager(TaskStateManager stateManager) {
            this.stateManager = stateManager;
            return this;
        }

        public Builder progressIntervalSeconds(int progressIntervalSeconds) {
            this.progressIntervalSeconds = progressIntervalSeconds;
            return this;
        }

        public Builder conflictRegistry(ConflictRegistry conflictRegistry) {
            this.conflictRegistry = conflictRegistry;
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
            if (planId == null || planId.trim().isEmpty()) {
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
            if (checkpointService == null) {
                throw new IllegalArgumentException("checkpointService is required");
            }
            if (eventSink == null) {
                throw new IllegalArgumentException("eventSink is required");
            }
            if (stateManager == null) {
                throw new IllegalArgumentException("stateManager is required");
            }

            return new TaskWorkerCreationContext(this);
        }
    }
}

