package xyz.firestige.executor.facade.exception;

/**
 * 计划未找到异常
 * 当根据 planId 查询计划时，计划不存在则抛出此异常
 */
public class PlanNotFoundException extends RuntimeException {

    public PlanNotFoundException(String message) {
        super(message);
    }

    public PlanNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

