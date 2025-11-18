package xyz.firestige.deploy.infrastructure.state;

import xyz.firestige.deploy.domain.task.StateTransitionService;
import xyz.firestige.deploy.domain.task.TaskStatus;
import xyz.firestige.deploy.domain.task.TaskAggregate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.infrastructure.state.strategy.CancelTransitionStrategy;
import xyz.firestige.deploy.infrastructure.state.strategy.CompleteTransitionStrategy;
import xyz.firestige.deploy.infrastructure.state.strategy.FailTransitionStrategy;
import xyz.firestige.deploy.infrastructure.state.strategy.MarkAsPendingTransitionStrategy;
import xyz.firestige.deploy.infrastructure.state.strategy.PauseTransitionStrategy;
import xyz.firestige.deploy.infrastructure.state.strategy.ResumeTransitionStrategy;
import xyz.firestige.deploy.infrastructure.state.strategy.RetryTransitionStrategy;
import xyz.firestige.deploy.infrastructure.state.strategy.RollbackCompleteTransitionStrategy;
import xyz.firestige.deploy.infrastructure.state.strategy.RollbackFailTransitionStrategy;
import xyz.firestige.deploy.infrastructure.state.strategy.RollbackTransitionStrategy;
import xyz.firestige.deploy.infrastructure.state.strategy.StartTransitionStrategy;
import xyz.firestige.deploy.infrastructure.state.strategy.StateTransitionKey;
import xyz.firestige.deploy.infrastructure.state.strategy.StateTransitionStrategy;

import java.util.HashMap;
import java.util.Map;

/**
 * 任务状态管理器（RF-13 重构版）
 * 使用策略模式管理状态转换，委托给聚合的业务方法
 */
public class TaskStateManager implements StateTransitionService {
    /**
     * RF-13: 状态转换策略注册表
     */
    private final Map<StateTransitionKey, StateTransitionStrategy> strategies = new HashMap<>();
    private Integer globalMaxRetry;

    public TaskStateManager() {
        initializeStrategies();
    }

    public TaskStateManager(Integer globalMaxRetry) {
        this.globalMaxRetry = globalMaxRetry;
        initializeStrategies();
    }

    /**
     * RF-13: 初始化所有状态转换策略
     */
    private void initializeStrategies() {
        // 1. CREATED -> PENDING
        registerStrategy(new MarkAsPendingTransitionStrategy());
        
        // 2. PENDING -> RUNNING (启动)
        registerStrategy(new StartTransitionStrategy());
        
        // 3. RUNNING -> PAUSED (暂停)
        registerStrategy(new PauseTransitionStrategy());
        
        // 4. PAUSED -> RUNNING (恢复)
        registerStrategy(new ResumeTransitionStrategy());
        
        // 5. RUNNING -> COMPLETED (完成)
        registerStrategy(new CompleteTransitionStrategy(null)); // totalStages 动态获取
        
        // 6. RUNNING -> FAILED (失败)
        registerStrategy(new FailTransitionStrategy());
        
        // 7. FAILED/ROLLED_BACK -> RUNNING (重试)
        registerStrategy(new RetryTransitionStrategy(globalMaxRetry));
        
        // 8. * -> ROLLING_BACK (开始回滚)
        registerStrategy(new RollbackTransitionStrategy());
        
        // 9. ROLLING_BACK -> ROLLED_BACK (回滚完成)
        registerStrategy(new RollbackCompleteTransitionStrategy());
        
        // 10. ROLLING_BACK -> ROLLBACK_FAILED (回滚失败)
        registerStrategy(new RollbackFailTransitionStrategy());
        
        // 11. * -> CANCELLED (取消)
        registerStrategy(new CancelTransitionStrategy());
    }

    /**
     * RF-13: 注册策略
     */
    private void registerStrategy(StateTransitionStrategy strategy) {
        StateTransitionKey key = new StateTransitionKey(strategy.getFromStatus(), strategy.getToStatus());
        strategies.put(key, strategy);
    }

    /**
     * 执行状态转换（核心路由方法）
     *
     * @param aggregate Task 聚合
     * @param targetStatus 目标状态
     * @param runtimeContext 运行时上下文
     * @return 是否成功转换
     */
    public boolean transition(
            TaskAggregate aggregate,
            TaskStatus targetStatus,
            TaskRuntimeContext runtimeContext) {

        TaskStatus currentStatus = aggregate.getStatus();

        // 1. 查找策略
        StateTransitionStrategy strategy = findStrategy(currentStatus, targetStatus);

        if (strategy == null) {
            // 没有找到策略
            return false;
        }

        // 2. 检查前置条件
        if (!strategy.canTransition(aggregate, runtimeContext, targetStatus)) {
            return false;
        }

        // 3. 执行策略（委托给聚合）
        try {
            strategy.execute(aggregate, runtimeContext);
            return true;
        } catch (Exception e) {
            // 策略执行失败
            return false;
        }
    }

    /**
     * 查找策略（支持特殊转换规则）
     */
    private StateTransitionStrategy findStrategy(
            TaskStatus currentStatus,
            TaskStatus targetStatus) {

        // 1. 精确匹配
        StateTransitionKey key = new StateTransitionKey(currentStatus, targetStatus);
        StateTransitionStrategy strategy = strategies.get(key);

        if (strategy != null) {
            return strategy;
        }

        // 2. 特殊处理：重试（FAILED/ROLLED_BACK → RUNNING）
        if (targetStatus == TaskStatus.RUNNING &&
                (currentStatus == TaskStatus.FAILED || currentStatus == TaskStatus.ROLLED_BACK)) {
            return strategies.get(new StateTransitionKey(TaskStatus.FAILED, TaskStatus.RUNNING));
        }

        // 3. 特殊处理：任意状态 → CANCELLED
        if (targetStatus == TaskStatus.CANCELLED) {
            return strategies.get(new StateTransitionKey(null, TaskStatus.CANCELLED));
        }

        // 4. 特殊处理：任意状态 → ROLLING_BACK
        if (targetStatus == TaskStatus.ROLLING_BACK) {
            return strategies.get(new StateTransitionKey(null, TaskStatus.ROLLING_BACK));
        }

        return null;
    }

    /**
     * 检查是否可以转换（查询 API）
     */
    public boolean canTransition(
            TaskAggregate aggregate,
            TaskStatus targetStatus,
            TaskRuntimeContext runtimeContext) {

        StateTransitionStrategy strategy = findStrategy(aggregate.getStatus(), targetStatus);

        if (strategy == null) {
            return false;
        }

        return strategy.canTransition(aggregate, runtimeContext, targetStatus);
    }
}
