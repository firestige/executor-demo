package xyz.firestige.deploy.domain.task;

import com.fasterxml.jackson.annotation.JsonInclude;
import xyz.firestige.deploy.domain.shared.exception.FailureInfo;
import xyz.firestige.deploy.infrastructure.execution.StageResult;

import java.time.LocalDateTime;

/**
 * Task 错误信息视图（宽表设计）
 * <p>
 * 职责：
 * 1. 承载任务失败和错误的详细信息
 * 2. 为领域事件提供统一的错误视图
 * 3. 支持 JSON 稀疏表序列化（忽略 null 字段）
 * <p>
 * 设计理念：
 * - 宽表设计：包含所有可能的错误字段
 * - 稀疏表优化：序列化时自动忽略 null 字段
 * - 多源支持：可从 FailureInfo、StageResult、Exception 创建
 * - 静态工厂方法：事件调用 from() 方法创建
 * <p>
 * 使用场景：
 * - TaskFailedEvent 错误详情
 * - StageFailedEvent 错误详情
 * - TaskRetryStartedEvent 上次错误信息
 * - 错误监控和告警
 *
 * @since T-035 统一事件负载模型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)  // 忽略 null 字段
public class TaskErrorView {

    // ============================================
    // 错误基本信息
    // ============================================

    private final String errorType;        // ERROR, TIMEOUT, VALIDATION_FAILED, etc.
    private final String errorMessage;
    private final String errorCode;

    // ============================================
    // 失败详情
    // ============================================

    private final String failedStageName;
    private final String failedStepName;
    private final Integer failedStageIndex;

    // ============================================
    // 异常信息（可选）
    // ============================================

    private final String exceptionClass;
    private final String stackTrace;       // 调试用，生产环境可选

    // ============================================
    // 重试信息
    // ============================================

    private final Boolean retriable;
    private final String retryStrategy;

    // ============================================
    // 时间信息
    // ============================================

    private final LocalDateTime failedAt;

    // ============================================
    // 私有构造函数
    // ============================================

    private TaskErrorView(Builder builder) {
        this.errorType = builder.errorType;
        this.errorMessage = builder.errorMessage;
        this.errorCode = builder.errorCode;
        this.failedStageName = builder.failedStageName;
        this.failedStepName = builder.failedStepName;
        this.failedStageIndex = builder.failedStageIndex;
        this.exceptionClass = builder.exceptionClass;
        this.stackTrace = builder.stackTrace;
        this.retriable = builder.retriable;
        this.retryStrategy = builder.retryStrategy;
        this.failedAt = builder.failedAt;
    }

    // ============================================
    // 静态工厂方法
    // ============================================

    /**
     * 从 FailureInfo 创建错误视图
     * <p>
     * 事件调用：TaskErrorView.from(failureInfo)
     *
     * @param failureInfo 失败信息对象
     * @return TaskErrorView 实例，如果 failureInfo 为 null 则返回 null
     */
    public static TaskErrorView from(FailureInfo failureInfo) {
        if (failureInfo == null) {
            return null;
        }

        return builder()
            .errorType(failureInfo.getErrorType() != null ? failureInfo.getErrorType().name() : "UNKNOWN")
            .errorMessage(failureInfo.getErrorMessage())
            .errorCode(failureInfo.getErrorCode())
            .failedStageName(failureInfo.getFailedAt())  // 使用 getFailedAt() 而不是 getStageName()
            .retriable(failureInfo.isRetryable())
            .failedAt(LocalDateTime.now())
            .build();
    }

    /**
     * 从 StageResult 创建错误视图（Stage 失败场景）
     * <p>
     * 事件调用：TaskErrorView.from(stageResult)
     *
     * @param stageResult Stage 执行结果
     * @return TaskErrorView 实例，如果成功则返回 null
     */
    public static TaskErrorView from(StageResult stageResult) {
        if (stageResult == null || stageResult.isSuccess()) {
            return null;
        }

        FailureInfo failureInfo = stageResult.getFailureInfo();
        String errorMessage = failureInfo != null ? failureInfo.getErrorMessage() : "执行失败";

        return builder()
            .errorType(failureInfo != null && failureInfo.getErrorType() != null
                ? failureInfo.getErrorType().name()
                : "STAGE_EXECUTION_ERROR")
            .errorMessage(errorMessage)
            .errorCode(failureInfo != null ? failureInfo.getErrorCode() : null)
            .failedStageName(stageResult.getStageName())
            .retriable(failureInfo != null ? failureInfo.isRetryable() : false)
            .failedAt(LocalDateTime.now())
            .build();
    }

    /**
     * 从异常创建错误视图（异常捕获场景）
     * <p>
     * 事件调用：TaskErrorView.fromException(ex)
     *
     * @param ex 异常对象
     * @return TaskErrorView 实例，如果 ex 为 null 则返回 null
     */
    public static TaskErrorView fromException(Exception ex) {
        if (ex == null) {
            return null;
        }

        return builder()
            .errorType("EXCEPTION")
            .errorMessage(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName())
            .exceptionClass(ex.getClass().getName())
            .stackTrace(getStackTraceString(ex))  // 可选
            .retriable(false)
            .failedAt(LocalDateTime.now())
            .build();
    }

    /**
     * 从异常创建错误视图（带 Stage 信息）
     *
     * @param ex 异常对象
     * @param stageName Stage 名称
     * @return TaskErrorView 实例
     */
    public static TaskErrorView fromException(Exception ex, String stageName) {
        TaskErrorView view = fromException(ex);
        if (view == null) {
            return null;
        }

        return builder()
            .errorType(view.errorType)
            .errorMessage(view.errorMessage)
            .errorCode(view.errorCode)
            .failedStageName(stageName)
            .exceptionClass(view.exceptionClass)
            .stackTrace(view.stackTrace)
            .retriable(view.retriable)
            .failedAt(view.failedAt)
            .build();
    }

    // ============================================
    // 辅助方法
    // ============================================

    private static String getStackTraceString(Exception ex) {
        // 只保留前 5 行堆栈（避免过长）
        StackTraceElement[] traces = ex.getStackTrace();
        if (traces == null || traces.length == 0) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        int limit = Math.min(5, traces.length);
        for (int i = 0; i < limit; i++) {
            sb.append(traces[i].toString());
            if (i < limit - 1) {
                sb.append("\n");
            }
        }
        if (traces.length > 5) {
            sb.append("\n... (").append(traces.length - 5).append(" more)");
        }
        return sb.toString();
    }

    // ============================================
    // Builder 模式
    // ============================================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String errorType;
        private String errorMessage;
        private String errorCode;
        private String failedStageName;
        private String failedStepName;
        private Integer failedStageIndex;
        private String exceptionClass;
        private String stackTrace;
        private Boolean retriable;
        private String retryStrategy;
        private LocalDateTime failedAt;

        public Builder errorType(String errorType) {
            this.errorType = errorType;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder failedStageName(String failedStageName) {
            this.failedStageName = failedStageName;
            return this;
        }

        public Builder failedStepName(String failedStepName) {
            this.failedStepName = failedStepName;
            return this;
        }

        public Builder failedStageIndex(Integer failedStageIndex) {
            this.failedStageIndex = failedStageIndex;
            return this;
        }

        public Builder exceptionClass(String exceptionClass) {
            this.exceptionClass = exceptionClass;
            return this;
        }

        public Builder stackTrace(String stackTrace) {
            this.stackTrace = stackTrace;
            return this;
        }

        public Builder retriable(Boolean retriable) {
            this.retriable = retriable;
            return this;
        }

        public Builder retryStrategy(String retryStrategy) {
            this.retryStrategy = retryStrategy;
            return this;
        }

        public Builder failedAt(LocalDateTime failedAt) {
            this.failedAt = failedAt;
            return this;
        }

        public TaskErrorView build() {
            return new TaskErrorView(this);
        }
    }

    // ============================================
    // Getter 方法
    // ============================================

    public String getErrorType() {
        return errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getFailedStageName() {
        return failedStageName;
    }

    public String getFailedStepName() {
        return failedStepName;
    }

    public Integer getFailedStageIndex() {
        return failedStageIndex;
    }

    public String getExceptionClass() {
        return exceptionClass;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public Boolean getRetriable() {
        return retriable;
    }

    public String getRetryStrategy() {
        return retryStrategy;
    }

    public LocalDateTime getFailedAt() {
        return failedAt;
    }

    @Override
    public String toString() {
        return String.format("TaskErrorView{type=%s, message='%s', stage='%s'}",
            errorType, errorMessage, failedStageName);
    }
}

