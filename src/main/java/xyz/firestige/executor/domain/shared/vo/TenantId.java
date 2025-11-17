package xyz.firestige.executor.domain.shared.vo;

import java.util.Objects;

/**
 * TenantId 值对象
 *
 * DDD 重构：将原始的 String tenantId 封装为值对象
 *
 * 职责：
 * 1. 封装租户 ID 的验证规则
 * 2. 提供类型安全（无法与 TaskId、PlanId 混淆）
 * 3. 不可变对象，线程安全
 */
public final class TenantId {

    private final String value;

    /**
     * 私有构造函数
     *
     * @param value 租户 ID 字符串
     */
    private TenantId(String value) {
        this.value = value;
    }

    /**
     * 创建 TenantId（带验证）
     *
     * @param value 租户 ID 字符串
     * @return TenantId 实例
     * @throws IllegalArgumentException 如果格式无效
     */
    public static TenantId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Tenant ID 不能为空");
        }

        // 可以添加更多验证规则，例如长度限制、字符限制等
        if (value.length() > 128) {
            throw new IllegalArgumentException(
                String.format("Tenant ID 长度不能超过 128 个字符: %s", value)
            );
        }

        return new TenantId(value);
    }

    /**
     * 创建 TenantId（不验证，用于已知合法的场景）
     *
     * @param value 租户 ID 字符串
     * @return TenantId 实例
     */
    public static TenantId ofTrusted(String value) {
        return new TenantId(value);
    }

    /**
     * 获取原始值
     *
     * @return 租户 ID 字符串
     */
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TenantId tenantId = (TenantId) o;
        return Objects.equals(value, tenantId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

