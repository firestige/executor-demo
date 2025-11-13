package xyz.firestige.executor.event;

/**
 * 配置已下发事件（事后）
 */
public class ConfigDeployedEvent extends TaskEvent {
    
    public ConfigDeployedEvent(String taskId) {
        super(taskId, TaskEventType.AFTER);
    }
    
    @Override
    public String getEventName() {
        return "ConfigDeployed";
    }
}
