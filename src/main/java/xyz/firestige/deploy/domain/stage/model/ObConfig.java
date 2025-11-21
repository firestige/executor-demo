package xyz.firestige.deploy.domain.stage.model;

/**
 * OB 服务配置（用于 Redis 存储）
 *
 * @since RF-19-03
 */
public class ObConfig {

    private String tenantId;
    private String sourceUnitName;
    private String targetUnitName;
    private Long timestamp;

    public ObConfig() {
    }

    public ObConfig(String tenantId, String sourceUnitName, String targetUnitName) {
        this.tenantId = tenantId;
        this.sourceUnitName = sourceUnitName;
        this.targetUnitName = targetUnitName;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and Setters

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

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
