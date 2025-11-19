package xyz.firestige.deploy.domain.stage.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import xyz.firestige.deploy.domain.shared.vo.TenantId;

import java.util.List;

/**
 * 蓝绿网关 Redis 配置值对象
 *
 * 用途：写入 Redis Hash 的 Value（JSON 序列化）
 *
 * Redis 结构：
 * Key: icc_ai_ops_srv:tenant_config:{tenantId}
 * Field: icc-bg-gateway
 * Value: 本对象的 JSON 序列化
 *
 * JSON 示例：
 * {
 *   "tenantId": "tenant_12345",
 *   "sourceUnitName": "unit_a",
 *   "targetUnitName": "unit_b",
 *   "routes": [
 *     {
 *       "id": "route_001",
 *       "sourceUri": "uri1",
 *       "targetUri": "uri2"
 *     }
 *   ]
 * }
 */
public class BlueGreenGatewayRedisValue {

    /**
     * 租户 ID
     */
    @JsonProperty("tenantId")
    private String tenantId;

    /**
     * 来源部署单元名称
     * 对应 TenantConfig.previousConfig.deployUnit.name()
     */
    @JsonProperty("sourceUnitName")
    private String sourceUnitName;

    /**
     * 目标部署单元名称
     * 对应 TenantConfig.deployUnit.name()
     */
    @JsonProperty("targetUnitName")
    private String targetUnitName;

    /**
     * 路由列表
     * 对应 TenantConfig.routeRules
     */
    @JsonProperty("routes")
    private List<RouteInfo> routes;

    public BlueGreenGatewayRedisValue() {
    }

    public BlueGreenGatewayRedisValue(TenantId tenantId, String sourceUnitName,
                                      String targetUnitName, List<RouteInfo> routes) {
        this.tenantId = tenantId.getValue();
        this.sourceUnitName = sourceUnitName;
        this.targetUnitName = targetUnitName;
        this.routes = routes;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getSourceUnitName() {
        return sourceUnitName;
    }

    public void setSourceUnitName(String sourceUnitName) {
        this.sourceUnitName = sourceUnitName;
    }

    public String getTargetUnitName() {
        return targetUnitName;
    }

    public void setTargetUnitName(String targetUnitName) {
        this.targetUnitName = targetUnitName;
    }

    public List<RouteInfo> getRoutes() {
        return routes;
    }

    public void setRoutes(List<RouteInfo> routes) {
        this.routes = routes;
    }

    @Override
    public String toString() {
        return "BlueGreenGatewayRedisValue{" +
                "tenantId='" + tenantId + '\'' +
                ", sourceUnitName='" + sourceUnitName + '\'' +
                ", targetUnitName='" + targetUnitName + '\'' +
                ", routes=" + routes +
                '}';
    }
}

