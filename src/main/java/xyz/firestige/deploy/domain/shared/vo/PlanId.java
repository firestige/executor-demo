package xyz.firestige.deploy.domain.shared.vo;

import java.util.Objects;

/**
 * Plan ID 值对象（Value Object）
 * <p>
 * 职责：
 * 1. 封装 Plan ID（String）
 * 2. 提供类型安全（无法与 String、TaskId、TenantId 混淆）
 * 3. 不可变对象，线程安全
 * <p>
 * 格式建议：plan-{timestamp}
 * 示例：plan-1700000000000
 *
 * @since Phase 17 - RF-08
 */
public final class PlanId {

    private final String value;

    private PlanId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("planId 不能为空");
        }
        this.value = value;
    }

    // ============================================
    // 工厂方法
    // ============================================

    /**
     * 创建 PlanId（带验证）
     */
    public static PlanId of(String value) {
        return new PlanId(value);
    }

    /**
     * 创建 PlanId（不验证，已知合法）
     */
    public static PlanId ofTrusted(String value) {
        return new PlanId(value);
    }

    // ============================================
    // 业务方法
    // ============================================

    /**
     * 获取原始值
     */
    public String getValue() {
        return value;
    }

    // ============================================
    // equals / hashCode / toString
    // ============================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanId planId = (PlanId) o;
        return Objects.equals(value, planId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "PlanId[" + value + "]";
    }
}
