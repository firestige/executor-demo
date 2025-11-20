package xyz.firestige.deploy.infrastructure.template;

import org.springframework.stereotype.Component;
import xyz.firestige.deploy.domain.stage.config.ServiceConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * 变量上下文构建器
 *
 * 职责：
 * 1. 从 ServiceConfig 中提取模板变量
 * 2. 构建变量上下文 Map
 * 3. 支持扩展自定义变量
 *
 * 当前支持的变量：
 * - tenantId: 租户 ID
 * - serviceType: 服务类型（如 blue-green-gateway）
 */
@Component
public class VariableContextBuilder {

    /**
     * 从 ServiceConfig 构建变量上下文
     *
     * @param serviceConfig 服务配置
     * @return 变量 Map，key 为变量名，value 为变量值
     */
    public Map<String, String> buildContext(ServiceConfig serviceConfig) {
        Map<String, String> variables = new HashMap<>();

        // 必需变量
        variables.put("tenantId", serviceConfig.getTenantId().getValue());
        variables.put("serviceType", serviceConfig.getServiceType());

        // TODO: 如果未来需要更多变量（如 serviceName），可以扩展 ServiceConfig 接口

        return variables;
    }
}

