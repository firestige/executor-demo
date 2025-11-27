package xyz.firestige.deploy.testutil.factory;

import xyz.firestige.deploy.domain.plan.PlanAggregate;
import xyz.firestige.deploy.domain.shared.vo.DeployVersion;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.domain.task.RetryPolicy;
import xyz.firestige.deploy.domain.task.StageProgress;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 聚合测试支持类
 * <p>
 * 设计理念：
 * 1. 聚合根不暴露setter，保持封装性
 * 2. 测试通过反射注入中间状态（仅测试代码可用）
 * 3. 避免破坏聚合的业务不变式保护
 * <p>
 * 使用场景：
 * - 构造特定状态的聚合用于测试
 * - 绕过业务规则直接设置内部状态
 * - 仅在测试代码中使用，生产代码禁用
 *
 * @since T-023 测试体系重建
 */
public class AggregateTestSupport {

    /**
     * 为TaskAggregate设置内部字段（通过反射）
     * <p>
     * 警告：仅用于测试，绕过了聚合的封装和不变式保护
     */
    public static void setTaskField(TaskAggregate task, String fieldName, Object value) {
        try {
            Field field = TaskAggregate.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(task, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }

    /**
     * 为PlanAggregate设置内部字段（通过反射）
     */
    public static void setPlanField(PlanAggregate plan, String fieldName, Object value) {
        try {
            Field field = PlanAggregate.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(plan, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }

    /**
     * 调用聚合的私有方法（通过反射）
     */
    public static Object invokePrivateMethod(Object target, String methodName, Class<?>[] paramTypes, Object... args) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke method: " + methodName, e);
        }
    }

    // ========== TaskAggregate专用便捷方法 ==========

    /**
     * 设置DeployVersion（测试专用）
     */
    public static void setDeployVersion(TaskAggregate task, DeployVersion version) {
        setTaskField(task, "deployVersion", version);
    }

    /**
     * 设置DeployUnitName（测试专用）
     */
    public static void setDeployUnitName(TaskAggregate task, String unitName) {
        setTaskField(task, "deployUnitName", unitName);
    }

    /**
     * 设置StageProgress（测试专用）
     */
    public static void setStageProgress(TaskAggregate task, StageProgress progress) {
        setTaskField(task, "stageProgress", progress);
    }

    /**
     * 设置RetryPolicy（测试专用）
     */
    public static void setRetryPolicy(TaskAggregate task, RetryPolicy policy) {
        setTaskField(task, "retryPolicy", policy);
    }

    /**
     * 初始化Task的Stages（测试专用）
     * 直接设置totalStages和stageProgress
     */
    public static void initializeTaskStages(TaskAggregate task, List<TaskStage> stages) {
        // 创建StageProgress（使用initial方法）
        StageProgress progress = StageProgress.initial(stages);
        setStageProgress(task, progress);
    }
    
    /**
     * 初始化Task的Stages并设置当前进度（测试专用）
     */
    public static void initializeTaskStages(TaskAggregate task, List<TaskStage> stages, int currentStageIndex) {
        // 创建StageProgress（指定当前索引）
        StageProgress progress = StageProgress.of(currentStageIndex, stages);
        setStageProgress(task, progress);
    }

    // ========== PlanAggregate专用便捷方法 ==========

    /**
     * 为Plan添加TaskId（测试专用，绕过状态检查）
     */
    public static void addTaskIdDirectly(PlanAggregate plan, TaskId taskId) {
        try {
            Field field = PlanAggregate.class.getDeclaredField("taskIds");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<TaskId> taskIds = (List<TaskId>) field.get(plan);
            taskIds.add(taskId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to add taskId", e);
        }
    }

    /**
     * 设置Plan的maxConcurrency（测试专用）
     */
    public static void setMaxConcurrency(PlanAggregate plan, int maxConcurrency) {
        setPlanField(plan, "maxConcurrency", maxConcurrency);
    }
}
