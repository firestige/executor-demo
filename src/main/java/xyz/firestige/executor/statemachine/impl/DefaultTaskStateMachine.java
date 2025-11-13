package xyz.firestige.executor.statemachine.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import xyz.firestige.executor.domain.Task;
import xyz.firestige.executor.domain.TaskStatus;
import xyz.firestige.executor.statemachine.TaskStateMachine;

/**
 * 默认任务状态机实现
 * 定义任务状态转换规则
 * 
 * 状态转换规则：
 * READY -> RUNNING
 * RUNNING -> PAUSED | COMPLETED | FAILED | STOPPED
 * PAUSED -> RUNNING | STOPPED
 * FAILED -> ROLLING_BACK
 * ROLLING_BACK -> ROLLBACK_COMPLETE | FAILED
 * COMPLETED, STOPPED, ROLLBACK_COMPLETE 为终态，不可再转换
 */
@Component
public class DefaultTaskStateMachine implements TaskStateMachine {
    
    /**
     * 状态转换规则表
     * Key: 源状态, Value: 可转换到的目标状态列表
     */
    private static final Map<TaskStatus, List<TaskStatus>> TRANSITION_RULES = new HashMap<>();
    
    static {
        // READY 只能转换到 RUNNING
        TRANSITION_RULES.put(TaskStatus.READY, Arrays.asList(TaskStatus.RUNNING));
        
        // RUNNING 可以转换到 PAUSED, COMPLETED, FAILED, STOPPED
        TRANSITION_RULES.put(TaskStatus.RUNNING, Arrays.asList(
            TaskStatus.PAUSED,
            TaskStatus.COMPLETED,
            TaskStatus.FAILED,
            TaskStatus.STOPPED
        ));
        
        // PAUSED 可以转换到 RUNNING（恢复）或 STOPPED
        TRANSITION_RULES.put(TaskStatus.PAUSED, Arrays.asList(
            TaskStatus.RUNNING,
            TaskStatus.STOPPED
        ));
        
        // FAILED 只能转换到 ROLLING_BACK
        TRANSITION_RULES.put(TaskStatus.FAILED, Arrays.asList(TaskStatus.ROLLING_BACK));
        
        // ROLLING_BACK 可以转换到 ROLLBACK_COMPLETE 或 FAILED（回滚失败）
        TRANSITION_RULES.put(TaskStatus.ROLLING_BACK, Arrays.asList(
            TaskStatus.ROLLBACK_COMPLETE,
            TaskStatus.FAILED
        ));
        
        // 终态不允许转换
        TRANSITION_RULES.put(TaskStatus.COMPLETED, Collections.emptyList());
        TRANSITION_RULES.put(TaskStatus.STOPPED, Collections.emptyList());
        TRANSITION_RULES.put(TaskStatus.ROLLBACK_COMPLETE, Collections.emptyList());
    }
    
    @Override
    public boolean canTransition(TaskStatus from, TaskStatus to) {
        if (from == null || to == null) {
            return false;
        }
        
        // 相同状态不需要转换
        if (from == to) {
            return false;
        }
        
        List<TaskStatus> allowedTransitions = TRANSITION_RULES.get(from);
        if (allowedTransitions == null) {
            return false;
        }
        
        return allowedTransitions.contains(to);
    }
    
    @Override
    public void transition(Task task, TaskStatus targetStatus) {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        
        if (targetStatus == null) {
            throw new IllegalArgumentException("Target status cannot be null");
        }
        
        TaskStatus currentStatus = task.getStatus();
        
        // 检查是否允许转换
        if (!canTransition(currentStatus, targetStatus)) {
            throw new IllegalStateException(
                String.format("Cannot transition from %s to %s for task %s",
                    currentStatus, targetStatus, task.getTaskId())
            );
        }
        
        // 执行状态转换
        task.setStatus(targetStatus);
        
        // 更新时间戳
        if (targetStatus == TaskStatus.RUNNING && task.getStartTime() == null) {
            task.setStartTime(LocalDateTime.now());
        }
        
        if (task.isTerminalState()) {
            task.setEndTime(LocalDateTime.now());
        }
    }
    
    @Override
    public List<TaskStatus> getAvailableTransitions(TaskStatus currentStatus) {
        if (currentStatus == null) {
            return Collections.emptyList();
        }
        
        List<TaskStatus> transitions = TRANSITION_RULES.get(currentStatus);
        return transitions != null ? new ArrayList<>(transitions) : Collections.emptyList();
    }
}
