package xyz.firestige.executor.application.dto;

/**
 * 部署单元标识
 * 核心标识的组合，使用 record 保证不可变性和类型安全
 */
public record DeployUnitIdentifier(Long id, Long version, String name) {

    public DeployUnitIdentifier {
        // 验证：id 和 version 不能为 null
        if (id == null || version == null) {
            throw new IllegalArgumentException("DeployUnit id and version cannot be null");
        }
    }
}

