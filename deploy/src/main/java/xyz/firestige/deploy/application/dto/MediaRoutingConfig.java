package xyz.firestige.deploy.application.dto;

/**
 * 媒体路由配置
 * trunkGroup 和 calledNumberRules 成对激活媒体路由配置
 * 使用 record 保证配对约束和不可变性
 */
public record MediaRoutingConfig(String trunkGroup, String calledNumberRules) {

    public MediaRoutingConfig {
        // 验证：成对存在或同时为 null
        if ((trunkGroup == null) != (calledNumberRules == null)) {
            throw new IllegalArgumentException(
                "trunkGroup and calledNumberRules must be both present or both absent");
        }
    }

    /**
     * 检查媒体路由配置是否已启用
     */
    public boolean isEnabled() {
        return trunkGroup != null && calledNumberRules != null;
    }
}

