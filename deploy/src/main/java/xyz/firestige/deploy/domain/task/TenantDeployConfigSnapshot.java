package xyz.firestige.deploy.domain.task;

import java.util.List;

/**
 * 内部快照：保存上一版可用配置，用于快速回滚。
 * 不直接持有外部 DTO 引用，避免耦合。
 */
public class TenantDeployConfigSnapshot {
    private final String tenantId;
    private final Long deployUnitId;
    private final Long deployUnitVersion;
    private final String deployUnitName;
    private final List<String> networkEndpoints; // 简化，只保存字符串形式

    public TenantDeployConfigSnapshot(String tenantId,
                                      Long deployUnitId,
                                      Long deployUnitVersion,
                                      String deployUnitName,
                                      List<String> networkEndpoints) {
        this.tenantId = tenantId;
        this.deployUnitId = deployUnitId;
        this.deployUnitVersion = deployUnitVersion;
        this.deployUnitName = deployUnitName;
        this.networkEndpoints = networkEndpoints;
    }

    public String getTenantId() { return tenantId; }
    public Long getDeployUnitId() { return deployUnitId; }
    public Long getDeployUnitVersion() { return deployUnitVersion; }
    public String getDeployUnitName() { return deployUnitName; }
    public List<String> getNetworkEndpoints() { return networkEndpoints; }
}

