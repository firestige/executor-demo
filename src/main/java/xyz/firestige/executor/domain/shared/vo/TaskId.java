package xyz.firestige.executor.domain.shared.vo;

import java.util.Objects;

/**
 * TaskId 值对象
 *
 * DDD 重构：将原始的 String taskId 封装为值对象
 *
 * 职责：
 * 1. 封装 Task ID 的格式验证规则
 * 2. 提供类型安全（无法与其他 String 混淆）
 * 3. 不可变对象，线程安全
 *
 * 格式规则：task-{planId}-{timestamp}-{random}
 * 示例：task-plan123-1700000000000-abc123
 */
public final class TaskId {

    private final String value;

    /**
     * 私有构造函数，通过静态工厂方法创建
     *
     * @param value Task ID 字符串
     */
    private TaskId(String value) {
        this.value = value;
    }

    /**
     * 创建 TaskId（带验证）
     *
     * @param value Task ID 字符串
     * @return TaskId 实例
     * @throws IllegalArgumentException 如果格式无效
     */
    public static TaskId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Task ID 不能为空");
        }

        // 验证格式：必须以 "task-" 开头
        if (!value.startsWith("task-")) {
            throw new IllegalArgumentException(
                String.format("Task ID 格式无效，必须以 'task-' 开头: %s", value)
            );
        }

        return new TaskId(value);
    }

    /**
     * 创建 TaskId（不验证，用于已知合法的场景）
     *
     * @param value Task ID 字符串
     * @return TaskId 实例
     */
    public static TaskId ofTrusted(String value) {
        return new TaskId(value);
    }

    /**
     * 获取原始值
     *
     * @return Task ID 字符串
     */
    public String getValue() {
        return value;
    }

    /**
     * 判断是否属于指定的 Plan
     *
     * @param planId Plan ID
     * @return 如果 Task ID 包含该 Plan ID 则返回 true
     */
    public boolean belongsToPlan(String planId) {
        // task-{planId}-{timestamp}-{random}
        return value.contains("-" + planId + "-");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaskId taskId = (TaskId) o;
        return Objects.equals(value, taskId.value);
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

