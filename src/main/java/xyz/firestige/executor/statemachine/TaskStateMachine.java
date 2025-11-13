package xyz.firestige.executor.statemachine;

import java.util.List;

import xyz.firestige.executor.domain.Task;
import xyz.firestige.executor.domain.TaskStatus;

/**
 * 任务状态机接口
 * 定义状态转换的规则和操作
 */
public interface TaskStateMachine {
    
    /**
     * 检查是否允许从当前状态转换到目标状态
     * 
     * @param from 当前状态
     * @param to 目标状态
     * @return true 如果允许转换，false 否则
     */
    boolean canTransition(TaskStatus from, TaskStatus to);
    
    /**
     * 执行状态转换
     * 
     * @param task 任务对象
     * @param targetStatus 目标状态
     * @throws IllegalStateException 如果状态转换不合法
     */
    void transition(Task task, TaskStatus targetStatus);
    
    /**
     * 获取当前状态可以转换到的所有状态
     * 
     * @param currentStatus 当前状态
     * @return 可转换的状态列表
     */
    List<TaskStatus> getAvailableTransitions(TaskStatus currentStatus);
}
