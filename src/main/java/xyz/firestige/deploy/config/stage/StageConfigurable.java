package xyz.firestige.deploy.config.stage;

/**
 * 可配置阶段标记接口
 *
 * <p>所有阶段配置类实现此接口，以支持自动发现和统一管理。
 *
 * <p>设计理念：
 * <ul>
 *   <li>约定优于配置：通过接口约定行为</li>
 *   <li>零侵入扩展：新增配置类只需实现接口</li>
 *   <li>自动发现：无需手动注册配置类</li>
 * </ul>
 *
 * @since T-017
 */
public interface StageConfigurable {

    /**
     * 是否启用此阶段
     *
     * @return true 如果阶段已启用
     */
    boolean isEnabled();

    /**
     * 阶段名称（用于日志和报告）
     *
     * <p>默认实现：从类名推断（移除 "StageConfig" 或 "Config" 后缀）
     *
     * @return 阶段显示名称
     */
    default String getStageName() {
        String className = this.getClass().getSimpleName();
        return className
            .replace("StageConfig", "")
            .replace("Config", "");
    }

    /**
     * 验证配置有效性
     *
     * <p>设计原则：
     * <ul>
     *   <li>永不抛异常：返回验证结果，不阻塞启动</li>
     *   <li>自动修复：发现问题时尝试使用默认值</li>
     *   <li>记录警告：将问题记录在 ValidationResult 中</li>
     * </ul>
     *
     * <p>默认实现：返回成功（无验证）
     *
     * @return 验证结果
     */
    default ValidationResult validate() {
        return ValidationResult.success();
    }
}

