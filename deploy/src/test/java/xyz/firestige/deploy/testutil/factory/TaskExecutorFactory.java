package xyz.firestige.deploy.testutil.factory;

import org.springframework.stereotype.Component;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.infrastructure.execution.TaskExecutor;
import xyz.firestige.deploy.infrastructure.execution.TaskWorkerCreationContext;
import xyz.firestige.deploy.infrastructure.execution.TaskWorkerFactory;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;

import java.util.List;

/**
 * TaskExecutor 测试工厂（简化版）
 * <p>
 * 职责：为测试场景提供简化的 TaskExecutor 创建接口
 * <p>
 * <b>与 {@link TaskWorkerFactory} 的区别：</b>
 * <ul>
 *   <li>TaskWorkerFactory - 生产环境工厂，管理所有基础设施依赖</li>
 *   <li>TaskExecutorFactory - 测试专用工厂，简化参数，委托给 TaskWorkerFactory</li>
 * </ul>
 * <p>
 * <b>设计原则：</b>
 * <ul>
 *   <li>不重复实现创建逻辑（委托给生产环境的 TaskWorkerFactory）</li>
 *   <li>提供测试友好的简化 API（自动创建 Context）</li>
 *   <li>保持生产和测试代码的一致性</li>
 * </ul>
 *
 * @since T-023 测试体系重建
 * @see TaskWorkerFactory 生产环境的工厂实现
 */
@Component
public class TaskExecutorFactory {

    // ✅ 委托给生产环境的工厂
    private final TaskWorkerFactory taskWorkerFactory;

    public TaskExecutorFactory(TaskWorkerFactory taskWorkerFactory) {
        this.taskWorkerFactory = taskWorkerFactory;
    }

    /**
     * 最简单的创建方式（推荐）
     * <p>
     * 自动从 task 提取 planId 和创建默认 Context
     * 
     * @param task 任务聚合根
     * @param stages Stage 列表
     * @return TaskExecutor 实例
     */
    public TaskExecutor create(TaskAggregate task, List<TaskStage> stages) {
        return create(task.getPlanId(), task, stages, createDefaultContext(task));
    }

    /**
     * 带自定义 Context 的创建方式
     * <p>
     * 用于测试暂停、取消等需要自定义 Context 的场景
     * 
     * @param task 任务聚合根
     * @param stages Stage 列表
     * @param context 自定义运行时上下文
     * @return TaskExecutor 实例
     */
    public TaskExecutor create(TaskAggregate task, List<TaskStage> stages, TaskRuntimeContext context) {
        return create(task.getPlanId(), task, stages, context);
    }

    /**
     * 带自定义 PlanId 的创建方式
     * <p>
     * 用于测试多计划场景
     * 
     * @param planId 部署计划ID
     * @param task 任务聚合根
     * @param stages Stage 列表
     * @return TaskExecutor 实例
     */
    public TaskExecutor create(PlanId planId, TaskAggregate task, List<TaskStage> stages) {
        return create(planId, task, stages, createDefaultContext(task));
    }

    /**
     * 完整参数的创建方式（内部方法）
     * <p>
     * 构建 TaskWorkerCreationContext 并委托给生产环境的 TaskWorkerFactory
     */
    private TaskExecutor create(
            PlanId planId,
            TaskAggregate task,
            List<TaskStage> stages,
            TaskRuntimeContext context) {
        
        // ✅ 构建 TaskWorkerCreationContext
        TaskWorkerCreationContext creationContext = TaskWorkerCreationContext.builder()
                .planId(planId)
                .task(task)
                .stages(stages)
                .runtimeContext(context)
                .build();

        // ✅ 委托给生产环境的工厂
        return taskWorkerFactory.create(creationContext);
    }

    /**
     * 创建默认的运行时上下文
     */
    private TaskRuntimeContext createDefaultContext(TaskAggregate task) {
        return new TaskRuntimeContext(
                task.getPlanId(),
                task.getTaskId(),
                task.getTenantId()
        );
    }

    /**
     * Builder 模式（用于复杂配置场景）
     * 
     * @return TaskExecutorBuilder 实例
     */
    public TaskExecutorBuilder builder() {
        return new TaskExecutorBuilder(this);
    }

    /**
     * TaskExecutor 构建器（用于复杂配置）
     */
    public static class TaskExecutorBuilder {
        private final TaskExecutorFactory factory;
        private PlanId planId;
        private TaskAggregate task;
        private List<TaskStage> stages;
        private TaskRuntimeContext context;

        public TaskExecutorBuilder(TaskExecutorFactory factory) {
            this.factory = factory;
        }

        public TaskExecutorBuilder planId(PlanId planId) {
            this.planId = planId;
            return this;
        }

        public TaskExecutorBuilder task(TaskAggregate task) {
            this.task = task;
            if (this.planId == null) {
                this.planId = task.getPlanId();
            }
            return this;
        }

        public TaskExecutorBuilder stages(List<TaskStage> stages) {
            this.stages = stages;
            return this;
        }

        public TaskExecutorBuilder context(TaskRuntimeContext context) {
            this.context = context;
            return this;
        }

        /**
         * 构建 TaskExecutor
         */
        public TaskExecutor build() {
            if (task == null) {
                throw new IllegalStateException("Task must be set");
            }
            if (stages == null) {
                throw new IllegalStateException("Stages must be set");
            }
            if (planId == null) {
                planId = task.getPlanId();
            }
            if (context == null) {
                context = factory.createDefaultContext(task);
            }

            // ✅ 委托给工厂方法
            return factory.create(planId, task, stages, context);
        }
    }
}

