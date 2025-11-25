package xyz.firestige.deploy.infrastructure.validation.validator;

import xyz.firestige.deploy.infrastructure.validation.ConfigValidator;
import xyz.firestige.entity.deploy.NetworkEndpoint;
import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.deploy.domain.shared.validation.ValidationError;
import xyz.firestige.deploy.domain.shared.validation.ValidationResult;

import java.util.List;

/**
 * 网络端点校验器
 * 校验网络端点配置的有效性
 */
public class NetworkEndpointValidator implements ConfigValidator {

    @Override
    public ValidationResult validate(TenantDeployConfig config) {
        ValidationResult result = new ValidationResult(true);

        // 校验网络端点列表不为空
        List<NetworkEndpoint> endpoints = config.getNetworkEndpoints();
        if (endpoints == null || endpoints.isEmpty()) {
            result.addError(ValidationError.of(
                    "networkEndpoints",
                    "网络端点列表不能为空"
            ));
            return result;
        }

        // 校验每个端点
        for (int i = 0; i < endpoints.size(); i++) {
            NetworkEndpoint endpoint = endpoints.get(i);
            String fieldPrefix = "networkEndpoints[" + i + "]";

            // 校验 key
            if (endpoint.getKey() == null || endpoint.getKey().trim().isEmpty()) {
                result.addError(ValidationError.of(
                        fieldPrefix + ".key",
                        "端点 key 不能为空"
                ));
            }

            // 校验 sourceIp（如果存在）
            if (endpoint.getSourceIp() != null && !endpoint.getSourceIp().trim().isEmpty()) {
                if (!isValidIpAddress(endpoint.getSourceIp())) {
                    result.addError(ValidationError.of(
                            fieldPrefix + ".sourceIp",
                            "源 IP 地址格式不正确: " + endpoint.getSourceIp(),
                            endpoint.getSourceIp()
                    ));
                }
            }

            // 校验 targetIp（如果存在）
            if (endpoint.getTargetIp() != null && !endpoint.getTargetIp().trim().isEmpty()) {
                if (!isValidIpAddress(endpoint.getTargetIp())) {
                    result.addError(ValidationError.of(
                            fieldPrefix + ".targetIp",
                            "目标 IP 地址格式不正确: " + endpoint.getTargetIp(),
                            endpoint.getTargetIp()
                    ));
                }
            }

            // 至少需要有一个有效的地址（IP 或 Domain）
            boolean hasSource = (endpoint.getSourceIp() != null && !endpoint.getSourceIp().trim().isEmpty()) ||
                                (endpoint.getSourceDomain() != null && !endpoint.getSourceDomain().trim().isEmpty());
            boolean hasTarget = (endpoint.getTargetIp() != null && !endpoint.getTargetIp().trim().isEmpty()) ||
                                (endpoint.getTargetDomain() != null && !endpoint.getTargetDomain().trim().isEmpty());

            if (!hasSource) {
                result.addError(ValidationError.of(
                        fieldPrefix + ".source",
                        "必须指定源地址（sourceIp 或 sourceDomain）"
                ));
            }

            if (!hasTarget) {
                result.addError(ValidationError.of(
                        fieldPrefix + ".target",
                        "必须指定目标地址（targetIp 或 targetDomain）"
                ));
            }
        }

        return result;
    }

    /**
     * 简单的 IP 地址格式校验
     */
    private boolean isValidIpAddress(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return false;
        }

        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }

        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public String getValidatorName() {
        return "NetworkEndpointValidator";
    }

    @Override
    public int getOrder() {
        return 10;
    }
}

