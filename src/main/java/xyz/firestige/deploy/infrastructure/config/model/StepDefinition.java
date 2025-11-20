package xyz.firestige.deploy.infrastructure.config.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Step 定义
 * 包含步骤类型、配置和重试策略
 */
public class StepDefinition {
    
    private String type;                      // 步骤类型
    private Map<String, Object> config;// 步骤配置
    @JsonProperty("retry-policy")
    private RetryPolicy retryPolicy;          // 重试策略
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public Map<String, Object> getConfig() {
        return config;
    }
    
    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }
    
    public RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }
    
    public void setRetryPolicy(RetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy;
    }
    
    /**
     * 重试策略
     */
    public static class RetryPolicy {
        @JsonProperty("max-attempts")
        private int maxAttempts;
        @JsonProperty("interval-seconds")
        private int intervalSeconds;
        
        public int getMaxAttempts() {
            return maxAttempts;
        }
        
        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }
        
        public int getIntervalSeconds() {
            return intervalSeconds;
        }
        
        public void setIntervalSeconds(int intervalSeconds) {
            this.intervalSeconds = intervalSeconds;
        }
    }
}
