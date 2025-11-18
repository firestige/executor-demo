package xyz.firestige.deploy.domain.shared.vo;

import java.util.Objects;

/**
 * DeployVersion 值对象
 *
 * DDD 重构：封装部署单元的 ID 和版本号
 *
 * 职责：
 * 1. 将 deployUnitId 和 deployUnitVersion 作为一个整体对待
 * 2. 提供版本比较能力
 * 3. 封装业务规则（版本号验证）
 * 4. 不可变对象，线程安全
 */
public final class DeployVersion {

    private final Long deployUnitId;
    private final Long deployUnitVersion;

    /**
     * 私有构造函数
     *
     * @param deployUnitId 部署单元 ID
     * @param deployUnitVersion 部署单元版本号
     */
    private DeployVersion(Long deployUnitId, Long deployUnitVersion) {
        this.deployUnitId = deployUnitId;
        this.deployUnitVersion = deployUnitVersion;
    }

    /**
     * 创建 DeployVersion（带验证）
     *
     * @param deployUnitId 部署单元 ID
     * @param deployUnitVersion 部署单元版本号
     * @return DeployVersion 实例
     * @throws IllegalArgumentException 如果参数无效
     */
    public static DeployVersion of(Long deployUnitId, Long deployUnitVersion) {
        if (deployUnitId == null || deployUnitId <= 0) {
            throw new IllegalArgumentException(
                String.format("Deploy Unit ID 必须是正整数: %s", deployUnitId)
            );
        }

        if (deployUnitVersion == null || deployUnitVersion < 0) {
            throw new IllegalArgumentException(
                String.format("Deploy Unit Version 不能为负数: %s", deployUnitVersion)
            );
        }

        return new DeployVersion(deployUnitId, deployUnitVersion);
    }

    /**
     * 创建 DeployVersion（不验证，用于已知合法的场景）
     *
     * @param deployUnitId 部署单元 ID
     * @param deployUnitVersion 部署单元版本号
     * @return DeployVersion 实例
     */
    public static DeployVersion ofTrusted(Long deployUnitId, Long deployUnitVersion) {
        return new DeployVersion(deployUnitId, deployUnitVersion);
    }

    /**
     * 获取部署单元 ID
     *
     * @return 部署单元 ID
     */
    public Long getDeployUnitId() {
        return deployUnitId;
    }

    /**
     * 获取部署单元版本号
     *
     * @return 部署单元版本号
     */
    public Long getDeployUnitVersion() {
        return deployUnitVersion;
    }

    /**
     * 判断是否比另一个版本更新
     *
     * @param other 另一个版本
     * @return 如果当前版本更新则返回 true
     */
    public boolean isNewerThan(DeployVersion other) {
        if (other == null) {
            return true;
        }

        // 只有相同的 deployUnitId 才能比较版本
        if (!this.deployUnitId.equals(other.deployUnitId)) {
            throw new IllegalArgumentException(
                String.format("无法比较不同 Deploy Unit 的版本: %d vs %d",
                    this.deployUnitId, other.deployUnitId)
            );
        }

        return this.deployUnitVersion > other.deployUnitVersion;
    }

    /**
     * 判断是否与另一个版本相同
     *
     * @param other 另一个版本
     * @return 如果版本号相同则返回 true
     */
    public boolean isSameVersion(DeployVersion other) {
        if (other == null) {
            return false;
        }
        return this.deployUnitId.equals(other.deployUnitId)
            && this.deployUnitVersion.equals(other.deployUnitVersion);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeployVersion that = (DeployVersion) o;
        return Objects.equals(deployUnitId, that.deployUnitId)
            && Objects.equals(deployUnitVersion, that.deployUnitVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deployUnitId, deployUnitVersion);
    }

    @Override
    public String toString() {
        return String.format("DeployVersion{unitId=%d, version=%d}",
            deployUnitId, deployUnitVersion);
    }
}

