package xyz.firestige.executor.example;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import xyz.firestige.executor.processor.ConfigProcessorRegistry;

/**
 * 处理器和部署器注册示例
 * 展示如何注册自定义的配置处理器和部署器
 */
@Component
public class RegistrationExample implements CommandLineRunner {
    
    private final ConfigProcessorRegistry processorRegistry;
    private final CustomConfigProcessor customProcessor;
    
    public RegistrationExample(ConfigProcessorRegistry processorRegistry,
                               CustomConfigProcessor customProcessor) {
        this.processorRegistry = processorRegistry;
        this.customProcessor = customProcessor;
    }
    
    @Override
    public void run(String... args) throws Exception {
        // 注册配置处理器示例
        registerProcessors();
    }
    
    /**
     * 注册配置处理器
     */
    private void registerProcessors() {
        System.out.println("=== 注册配置处理器 ===");
        
        // 1. 为特定服务注册处理器
        processorRegistry.register("user-service", customProcessor);
        System.out.println("已为 user-service 注册自定义处理器");
        
        processorRegistry.register("order-service", customProcessor);
        System.out.println("已为 order-service 注册自定义处理器");
        
        // 2. 注册默认处理器（对所有服务生效）
        processorRegistry.registerDefault(customProcessor);
        System.out.println("已注册默认处理器（对所有服务生效）");
        
        System.out.println("=== 处理器注册完成 ===\n");
    }
}
