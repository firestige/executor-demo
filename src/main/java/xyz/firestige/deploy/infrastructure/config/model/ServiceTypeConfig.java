package xyz.firestige.deploy.infrastructure.config.model;

import java.util.List;

/**
 * 服务类型配置
 * 定义一个服务类型的 Stage 和 Step 组合
 */
public class ServiceTypeConfig {
    
    private List<StageDefinition> stages;
    
    public List<StageDefinition> getStages() {
        return stages;
    }
    
    public void setStages(List<StageDefinition> stages) {
        this.stages = stages;
    }
}
