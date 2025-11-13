package xyz.firestige.executor.factory;

/**
 * 任务ID生成工厂接口
 * 负责生成任务的唯一标识
 */
public interface TaskIdFactory {
    /**
     * 生成任务ID
     * 
     * @return 任务唯一标识
     */
    String generateTaskId();
}
