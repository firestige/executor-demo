package xyz.firestige.executor.factory;

import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * 基于UUID的任务ID生成工厂实现
 * 使用UUID作为任务ID生成策略
 */
@Component
public class UuidTaskIdFactory implements TaskIdFactory {
    
    @Override
    public String generateTaskId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
