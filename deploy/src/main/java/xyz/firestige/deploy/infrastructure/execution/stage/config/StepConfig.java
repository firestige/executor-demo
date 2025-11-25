package xyz.firestige.deploy.infrastructure.execution.stage.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 阶段内部步骤配置 (Phase3 新增)
 */
public class StepConfig {
    private String type; // redis-write / health-check / pubsub-broadcast / http-request
    private Map<String, String> params = new LinkedHashMap<>();

    public StepConfig() {}
    public StepConfig(String type) { this.type = type; }
    public StepConfig(String type, Map<String,String> params){
        this.type = type;
        if(params!=null) this.params.putAll(params);
    }

    public static StepConfig redisWrite(String keyPattern){
        Map<String,String> p = new LinkedHashMap<>();
        p.put("keyPattern", keyPattern);
        return new StepConfig("redis-write", p);
    }
    public static StepConfig healthCheck(){ return new StepConfig("health-check"); }
    public static StepConfig pubsubBroadcast(String channel){
        Map<String,String> p = new LinkedHashMap<>();
        p.put("channel", channel);
        return new StepConfig("pubsub-broadcast", p);
    }
    public static StepConfig httpRequest(String method, String url){
        Map<String,String> p = new LinkedHashMap<>();
        p.put("method", method);
        p.put("url", url);
        return new StepConfig("http-request", p);
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Map<String, String> getParams() { return Collections.unmodifiableMap(params); }
    public void setParams(Map<String, String> params) { this.params = new LinkedHashMap<>(params); }
}

