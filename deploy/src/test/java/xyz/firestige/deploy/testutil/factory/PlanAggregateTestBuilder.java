package xyz.firestige.deploy.testutil.factory;

import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.domain.plan.PlanAggregate;
import xyz.firestige.deploy.domain.plan.PlanStatus;
import xyz.firestige.deploy.domain.shared.vo.PlanId;
import xyz.firestige.deploy.domain.shared.vo.TaskId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * PlanAggregate 测试构建器
 * <p>
 * 用途：快速构建各种状态的Plan聚合根
 *
 * @since T-023 测试体系重建
 */
public class PlanAggregateTestBuilder {

    private PlanId planId;
    private int maxConcurrency = 10;
    private List<TaskId> taskIds = new ArrayList<>();

    public PlanAggregateTestBuilder(){
        this.planId = ValueObjectTestFactory.randomPlanId();
    }

    public PlanAggregateTestBuilder(TenantConfig config) {
        this.planId = config.getPlanId();
    }

    public PlanAggregateTestBuilder maxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
        return this;
    }

    public PlanAggregateTestBuilder withTaskIds(TaskId... taskIds) {
        this.taskIds = Arrays.asList(taskIds);
        return this;
    }

    public PlanAggregateTestBuilder withTaskCount(int count) {
        this.taskIds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            this.taskIds.add(ValueObjectTestFactory.taskId("task-" + i));
        }
        return this;
    }

    /**
     * 构建READY状态的Plan
     */
    public PlanAggregate buildReady() {
        PlanAggregate plan = new PlanAggregate(planId);
        
        // 添加TaskIds
        for (TaskId taskId : taskIds) {
            plan.addTask(taskId);
        }
        
        // 标记为READY
        plan.markAsReady();
        
        return plan;
    }

    /**
     * 构建RUNNING状态的Plan
     */
    public PlanAggregate buildRunning() {
        PlanAggregate plan = buildReady();
        plan.start();
        return plan;
    }

    /**
     * 构建带任务的Plan（默认3个任务）
     */
    public PlanAggregate buildWithTasks(int taskCount) {
        return new PlanAggregateTestBuilder()
                .withTaskCount(taskCount)
                .buildReady();
    }

    // ========== 快捷方法 ==========

    /**
     * 快速构建：空Plan（READY状态）
     */
    public static PlanAggregate ready() {
        return new PlanAggregateTestBuilder()
                .withTaskCount(3)
                .buildReady();
    }

    /**
     * 快速构建：RUNNING Plan
     */
    public static PlanAggregate running() {
        return new PlanAggregateTestBuilder()
                .withTaskCount(3)
                .buildRunning();
    }

    /**
     * 快速构建：带指定数量任务的Plan
     */
    public static PlanAggregate withTasks(int count) {
        return new PlanAggregateTestBuilder()
                .withTaskCount(count)
                .buildReady();
    }
}
