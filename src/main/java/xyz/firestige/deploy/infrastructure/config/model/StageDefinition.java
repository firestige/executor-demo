package xyz.firestige.deploy.infrastructure.config.model;

import java.util.List;

/**
 * Stage 定义
 * 包含 Stage 名称和步骤列表
 */
public class StageDefinition {
    
    private String name;
    private List<StepDefinition> steps;
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public List<StepDefinition> getSteps() {
        return steps;
    }
    
    public void setSteps(List<StepDefinition> steps) {
        this.steps = steps;
    }
}
