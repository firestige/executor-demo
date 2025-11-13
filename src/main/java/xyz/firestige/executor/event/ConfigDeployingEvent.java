package xyz.firestige.executor.event;

/**
 * 配置下发中事件（事前）
 */
public class ConfigDeployingEvent extends TaskEvent {
    
    public ConfigDeployingEvent(String taskId) {
        super(taskId, TaskEventType.BEFORE);
    }
    
    @Override
    public String getEventName() {
        return "ConfigDeploying";
    }
}
