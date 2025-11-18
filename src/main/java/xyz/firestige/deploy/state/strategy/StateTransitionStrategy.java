package xyz.firestige.deploy.state.strategy;

import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.state.TaskStatus;

/**
 * 状态转换策略接口（RF-13: 方案C架构）
 * <p>
 * 职责：
 * 1. 判断是否可以执行状态转换（canTransition）
 * 2. 执行状态转换（execute）
 * 3. 委托给聚合的业务方法，而非直接修改状态
 * <p>
 * 设计原则：
 * - 状态机负责策略路由和事件发布
 * - 策略负责前置条件检查和业务方法调用
 * - 聚合负责状态转换逻辑和不变式保护
 *
 * @since Phase 18 - RF-13
 */
public interface StateTransitionStrategy {

    /**
     * 判断是否可以执行此状态转换
     *
     * @param agg 任务聚合
     * @param context 运行时上下文
     * @param targetStatus 目标状态
     * @return true=可以转换，false=不允许转换
     */
    boolean canTransition(TaskAggregate agg, TaskRuntimeContext context, TaskStatus targetStatus);

    /**
     * 执行状态转换
     * <p>
     * 注意：此方法不应直接修改聚合状态，而是调用聚合的业务方法
     *
     * @param agg 任务聚合
     * @param context 运行时上下文
     * @param additionalData 额外数据（用于某些特殊转换）
     */
    void execute(TaskAggregate agg, TaskRuntimeContext context, Object additionalData);

    /**
     * 获取此策略支持的源状态
     */
    TaskStatus getFromStatus();

    /**
     * 获取此策略支持的目标状态
     */
    TaskStatus getToStatus();
}
