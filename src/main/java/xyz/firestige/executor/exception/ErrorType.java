package xyz.firestige.executor.exception;

/**
 * 错误类型枚举
 * 用于分类不同类型的错误，便于错误处理和监控
 */
public enum ErrorType {

    /**
     * 数据校验错误
     */
    VALIDATION_ERROR("校验错误"),

    /**
     * 网络错误
     */
    NETWORK_ERROR("网络错误"),

    /**
     * 超时错误
     */
    TIMEOUT_ERROR("超时错误"),

    /**
     * 服务不可用
     */
    SERVICE_UNAVAILABLE("服务不可用"),

    /**
     * 业务错误
     */
    BUSINESS_ERROR("业务错误"),

    /**
     * 系统错误
     */
    SYSTEM_ERROR("系统错误"),

    /**
     * 未知错误
     */
    UNKNOWN_ERROR("未知错误");

    private final String description;

    ErrorType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

