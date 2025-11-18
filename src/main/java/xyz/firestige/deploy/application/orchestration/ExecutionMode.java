package xyz.firestige.deploy.application.orchestration;

/**
 * 执行模式枚举
 * 定义执行单内多个租户任务的执行方式
 */
public enum ExecutionMode {

    /**
     * 并发执行
     * 执行单内的多个租户任务可以并发执行
     */
    CONCURRENT("并发执行"),

    /**
     * FIFO 顺序执行
     * 执行单内的多个租户任务按照先进先出的顺序依次执行
     */
    FIFO("顺序执行");

    private final String description;

    ExecutionMode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

