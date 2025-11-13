package xyz.firestige.executor.example;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import xyz.firestige.executor.api.dto.DeployStrategy;
import xyz.firestige.executor.api.dto.ExecutionOrder;
import xyz.firestige.executor.api.dto.ServiceConfig;
import xyz.firestige.executor.domain.Task;
import xyz.firestige.executor.facade.BlueGreenExecutorFacade;

/**
 * 使用示例
 * 展示如何使用 BlueGreenExecutorFacade 进行蓝绿环境切换
 */
@Component
public class UsageExample implements CommandLineRunner {
    
    private final BlueGreenExecutorFacade executorFacade;
    
    public UsageExample(BlueGreenExecutorFacade executorFacade) {
        this.executorFacade = executorFacade;
    }
    
    @Override
    public void run(String... args) throws Exception {
        // 注释掉自动运行，避免启动时执行
        // demonstrateBasicUsage();
    }
    
    /**
     * 基本使用示例
     */
    public void demonstrateBasicUsage() {
        System.out.println("=== 蓝绿环境切换执行器使用示例 ===\n");
        
        // 1. 创建执行单
        ExecutionOrder order = createSampleExecutionOrder();
        
        // 2. 创建任务
        System.out.println("1. 创建蓝绿切换任务...");
        String taskId = executorFacade.createTask(order);
        System.out.println("   任务创建成功，taskId: " + taskId + "\n");
        
        // 3. 查询任务状态
        System.out.println("2. 查询任务状态...");
        Task task = executorFacade.getTask(taskId);
        System.out.println("   任务状态: " + task.getStatus());
        System.out.println("   创建时间: " + task.getCreateTime() + "\n");
        
        // 模拟等待任务执行
        sleepQuietly(2000);
        
        // 4. 暂停任务（如果正在执行）
        System.out.println("3. 暂停任务...");
        try {
            executorFacade.pauseTask(taskId);
            System.out.println("   任务暂停请求已发送\n");
        } catch (Exception e) {
            System.out.println("   无法暂停任务: " + e.getMessage() + "\n");
        }
        
        sleepQuietly(1000);
        
        // 5. 恢复任务
        System.out.println("4. 恢复任务...");
        try {
            executorFacade.resumeTask(taskId);
            System.out.println("   任务恢复请求已发送\n");
        } catch (Exception e) {
            System.out.println("   无法恢复任务: " + e.getMessage() + "\n");
        }
        
        sleepQuietly(2000);
        
        // 6. 查询最终状态
        System.out.println("5. 查询最终状态...");
        task = executorFacade.getTask(taskId);
        System.out.println("   最终状态: " + task.getStatus());
        if (task.getEndTime() != null) {
            System.out.println("   结束时间: " + task.getEndTime());
        }
        
        System.out.println("\n=== 示例完成 ===");
    }
    
    /**
     * 回滚示例
     */
    public void demonstrateRollback() {
        System.out.println("=== 回滚功能示例 ===\n");
        
        // 1. 创建并执行任务
        ExecutionOrder order = createSampleExecutionOrder();
        String originalTaskId = executorFacade.createTask(order);
        System.out.println("原始任务创建: " + originalTaskId);
        
        // 等待任务执行
        sleepQuietly(3000);
        
        // 2. 执行回滚
        System.out.println("\n执行回滚操作...");
        String rollbackTaskId = executorFacade.rollbackTask(originalTaskId);
        System.out.println("回滚任务创建: " + rollbackTaskId);
        
        // 3. 查询原始任务状态
        Task originalTask = executorFacade.getTask(originalTaskId);
        System.out.println("原始任务状态: " + originalTask.getStatus());
        
        // 4. 查询回滚任务状态
        Task rollbackTask = executorFacade.getTask(rollbackTaskId);
        System.out.println("回滚任务状态: " + rollbackTask.getStatus());
        
        System.out.println("\n=== 回滚示例完成 ===");
    }
    
    /**
     * 重试示例
     */
    public void demonstrateRetry() {
        System.out.println("=== 重试功能示例 ===\n");
        
        // 1. 创建任务（假设会失败）
        ExecutionOrder order = createSampleExecutionOrder();
        String taskId = executorFacade.createTask(order);
        System.out.println("任务创建: " + taskId);
        
        // 等待任务执行并失败
        sleepQuietly(3000);
        
        // 2. 检查任务状态
        Task task = executorFacade.getTask(taskId);
        System.out.println("任务状态: " + task.getStatus());
        
        // 3. 如果失败，执行重试
        if (task.getStatus().toString().equals("FAILED")) {
            System.out.println("\n任务失败，执行重试...");
            executorFacade.retryTask(taskId);
            System.out.println("重试请求已发送");
            
            // 等待重试完成
            sleepQuietly(3000);
            
            // 查询重试后的状态
            task = executorFacade.getTask(taskId);
            System.out.println("重试后状态: " + task.getStatus());
        }
        
        System.out.println("\n=== 重试示例完成 ===");
    }
    
    /**
     * 创建示例执行单
     */
    private ExecutionOrder createSampleExecutionOrder() {
        ExecutionOrder order = new ExecutionOrder();
        
        // 设置目标环境
        order.setTargetEnvironment("production");
        
        // 设置租户列表
        order.setTenantIds(List.of("tenant-001", "tenant-002", "tenant-003"));
        
        // 设置全局部署策略
        order.setGlobalDeployStrategy(DeployStrategy.CONCURRENT);
        
        // 添加服务配置 - 服务1
        ServiceConfig service1 = new ServiceConfig();
        service1.setServiceId("user-service");
        service1.setServiceName("用户服务");
        service1.setDeployStrategy(DeployStrategy.CONCURRENT);
        service1.setMaxConcurrency(5);
        
        Map<String, Object> config1 = new HashMap<>();
        config1.put("version", "2.0.0");
        config1.put("endpoint", "https://api.example.com/users");
        service1.setConfigData(config1);
        
        order.addServiceConfig(service1);
        
        // 添加服务配置 - 服务2
        ServiceConfig service2 = new ServiceConfig();
        service2.setServiceId("order-service");
        service2.setServiceName("订单服务");
        service2.setDeployStrategy(DeployStrategy.SEQUENTIAL);
        
        Map<String, Object> config2 = new HashMap<>();
        config2.put("version", "1.5.0");
        config2.put("endpoint", "https://api.example.com/orders");
        service2.setConfigData(config2);
        
        order.addServiceConfig(service2);
        
        // 设置超时时间
        order.setTimeoutMillis(300000L); // 5分钟
        
        // 设置描述
        order.setDescription("生产环境蓝绿切换 - 用户服务和订单服务");
        
        // 设置创建者
        order.setCreatedBy("admin");
        
        return order;
    }
    
    /**
     * 静默睡眠
     */
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
