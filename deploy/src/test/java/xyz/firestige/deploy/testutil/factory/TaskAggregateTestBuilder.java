package xyz.firestige.deploy.testutil.factory;

import xyz.firestige.deploy.domain.shared.exception.ErrorType;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.domain.shared.vo.DeployVersion;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;
import xyz.firestige.deploy.domain.shared.vo.TenantId;
import xyz.firestige.deploy.domain.task.RetryPolicy;
import xyz.firestige.deploy.domain.task.TaskAggregate;

import java.util.List;

/**
 * TaskAggregate 测试构建器
 * <p>
 * 用途：快速构建各种状态的Task聚合根
 *
 * @since T-023 测试体系重建
 */
public class TaskAggregateTestBuilder {

    private TaskId taskId;
    private PlanId planId;
    private TenantId tenantId;
    private DeployVersion deployVersion;
    private String deployUnitName;
    private boolean pauseRequested;
    private int totalStages = 3;

    public TaskAggregateTestBuilder() {
        // 设置默认值
        this.taskId = ValueObjectTestFactory.randomTaskId();
        this.planId = ValueObjectTestFactory.randomPlanId();
        this.tenantId = ValueObjectTestFactory.randomTenantId();
        this.deployVersion = ValueObjectTestFactory.randomVersion();
        this.deployUnitName = "test-unit";
    }

    public TaskAggregateTestBuilder taskId(TaskId taskId) {
        this.taskId = taskId;
        return this;
    }

    public TaskAggregateTestBuilder planId(PlanId planId) {
        this.planId = planId;
        return this;
    }

    public TaskAggregateTestBuilder tenantId(TenantId tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    public TaskAggregateTestBuilder deployVersion(DeployVersion version) {
        this.deployVersion = version;
        return this;
    }

    public TaskAggregateTestBuilder totalStages(int totalStages) {
        this.totalStages = totalStages;
        return this;
    }

    public TaskAggregateTestBuilder pauseRequested(boolean pauseRequested) {
        this.pauseRequested = pauseRequested;
        return this;
    }

    /**
     * 构建PENDING状态的Task
     */
    public TaskAggregate buildPending() {
        TaskAggregate task = new TaskAggregate(taskId, planId, tenantId);
        task.setDeployVersion(deployVersion);
        task.setDeployUnitName(deployUnitName);
        task.markAsPending();
        
        // 使用反射设置内部状态
        AggregateTestSupport.setRetryPolicy(task, RetryPolicy.initial(null));
        
        return task;
    }

    /**
     * 构建RUNNING状态的Task（已完成指定数量的Stage）
     */
    public TaskAggregate buildRunning(int completedStages) {
        TaskAggregate task = buildPending();
        
        // 初始化Stages并启动
        List<xyz.firestige.deploy.infrastructure.execution.stage.TaskStage> stages = 
            StageListTestFactory.successStages(totalStages);
        AggregateTestSupport.initializeTaskStages(task, stages);
        
        task.start();
        
        // 模拟完成指定数量的Stage（通过反射设置进度）
        if (completedStages > 0) {
            AggregateTestSupport.initializeTaskStages(task, stages, completedStages);
        }
        
        if (pauseRequested) {
            task.requestPause();
        }
        
        return task;
    }

    /**
     * 构建FAILED状态的Task
     */
    public TaskAggregate buildFailed(String failedStageName) {
        TaskAggregate task = buildRunning(1);
        
        // 模拟失败
        FailureInfo failureInfo = FailureInfo.of(
                ErrorType.SYSTEM_ERROR,
                "Test failure at " + failedStageName
        );
        task.fail(failureInfo);
        
        return task;
    }

    /**
     * 构建PAUSED状态的Task
     */
    public TaskAggregate buildPaused(int completedStages) {
        TaskAggregate task = buildRunning(completedStages);
        task.requestPause();
        task.applyPauseAtStageBoundary();
        return task;
    }

    /**
     * 构建COMPLETED状态的Task
     */
    public TaskAggregate buildCompleted() {
        TaskAggregate task = buildRunning(0);
        
        // 使用反射设置为已完成所有Stage
        List<xyz.firestige.deploy.infrastructure.execution.stage.TaskStage> stages = 
            StageListTestFactory.successStages(totalStages);
        AggregateTestSupport.initializeTaskStages(task, stages, totalStages);
        
        task.complete();
        return task;
    }

    /**
     * 构建带Checkpoint的Task（用于恢复测试）
     */
    public TaskAggregate buildWithCheckpoint(int lastCompletedStageIndex) {
        TaskAggregate task = buildRunning(0);
        
        // 创建Checkpoint
        List<String> completedStageNames = new java.util.ArrayList<>();
        for (int i = 0; i <= lastCompletedStageIndex; i++) {
            completedStageNames.add("stage-" + i);
        }
        
        task.recordCheckpoint(completedStageNames, lastCompletedStageIndex);
        return task;
    }

    // ========== 快捷方法 ==========

    /**
     * 快速构建：PENDING Task
     */
    public static TaskAggregate pending() {
        return new TaskAggregateTestBuilder().buildPending();
    }

    /**
     * 快速构建：RUNNING Task（已完成1个Stage）
     */
    public static TaskAggregate running() {
        return new TaskAggregateTestBuilder().buildRunning(1);
    }

    /**
     * 快速构建：FAILED Task
     */
    public static TaskAggregate failed() {
        return new TaskAggregateTestBuilder().buildFailed("stage-1");
    }

    /**
     * 快速构建：PAUSED Task
     */
    public static TaskAggregate paused() {
        return new TaskAggregateTestBuilder().buildPaused(1);
    }

    /**
     * 快速构建：COMPLETED Task
     */
    public static TaskAggregate completed() {
        return new TaskAggregateTestBuilder().buildCompleted();
    }
}
