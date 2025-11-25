package xyz.firestige.service;

/**
 * Agent 服务接口（OB 服务使用）
 *
 * 用途：判断 OB 服务的 Agent 是否就绪
 *
 * @since RF-19-03
 */
public interface AgentService {

    /**
     * 判断 Agent 是否就绪
     *
     * @param tenantId 租户 ID
     * @param planId 计划 ID
     * @return true 表示 Agent 就绪，可以继续；false 表示需要继续轮询
     */
    boolean judgeAgent(String tenantId, Long planId);
}
