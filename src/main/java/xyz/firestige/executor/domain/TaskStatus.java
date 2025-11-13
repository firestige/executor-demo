package xyz.firestige.executor.domain;

/**
 * 任务状态枚举
 * 定义蓝绿环境切换任务的所有可能状态
 */
public enum TaskStatus {
    /**
     * 就绪状态 - 任务已创建，等待执行
     */
    READY,
    
    /**
     * 运行中 - 任务正在执行
     */
    RUNNING,
    
    /**
     * 已暂停 - 任务在检查点暂停，可以恢复或停止
     */
    PAUSED,
    
    /**
     * 已完成 - 任务成功完成（终态）
     */
    COMPLETED,
    
    /**
     * 失败 - 任务执行失败，等待重试或回滚（终态）
     */
    FAILED,
    
    /**
     * 回滚中 - 任务正在执行回滚操作
     */
    ROLLING_BACK,
    
    /**
     * 回滚完成 - 回滚操作完成（终态）
     */
    ROLLBACK_COMPLETE,
    
    /**
     * 已停止 - 任务被手动停止（终态）
     */
    STOPPED
}
