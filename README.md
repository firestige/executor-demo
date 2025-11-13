# 蓝绿环境切换执行器 - 使用指南

## 概述

蓝绿环境切换执行器是一个用于管理多租户、多服务蓝绿环境切换的执行框架。它封装了切换任务的执行细节，对外只暴露简洁的API接口。

## 核心特性

1. **异步执行** - 任务异步执行，立即返回任务ID
2. **租户级FIFO队列** - 同一租户的任务顺序执行，不同租户可并发
3. **检查点机制** - 支持任务暂停、恢复，从检查点重启
4. **灵活的部署策略** - 支持并发/顺序两种部署策略
5. **状态机保护** - 严格的状态转换规则，防止非法操作
6. **事件驱动** - 发布任务生命周期事件，便于扩展

## 快速开始

### 1. 注入门面类

```java
@Autowired
private BlueGreenExecutorFacade executorFacade;
```

### 2. 创建执行单

```java
ExecutionOrder order = new ExecutionOrder();

// 设置目标环境
order.setTargetEnvironment("production");

// 设置租户列表
order.setTenantIds(List.of("tenant-001", "tenant-002"));

// 添加服务配置
ServiceConfig service = new ServiceConfig();
service.setServiceId("user-service");
service.setServiceName("用户服务");
service.setDeployStrategy(DeployStrategy.CONCURRENT);

Map<String, Object> config = new HashMap<>();
config.put("version", "2.0.0");
config.put("endpoint", "https://api.example.com/users");
service.setConfigData(config);

order.addServiceConfig(service);
```

### 3. 创建任务

```java
String taskId = executorFacade.createTask(order);
System.out.println("任务创建成功: " + taskId);
```

### 4. 管理任务

```java
// 查询任务
Task task = executorFacade.getTask(taskId);

// 暂停任务
executorFacade.pauseTask(taskId);

// 恢复任务
executorFacade.resumeTask(taskId);

// 停止任务
executorFacade.stopTask(taskId);

// 重试任务（仅失败状态）
executorFacade.retryTask(taskId);

// 回滚任务
String rollbackTaskId = executorFacade.rollbackTask(taskId);
```

## API 说明

### BlueGreenExecutorFacade

对外暴露的门面类，提供以下方法：

#### createTask(ExecutionOrder order)
- **功能**: 创建蓝绿切换任务
- **参数**: ExecutionOrder - 执行单
- **返回**: String - 任务ID
- **说明**: 任务创建后立即返回，异步执行

#### stopTask(String taskId)
- **功能**: 停止任务
- **参数**: taskId - 任务ID
- **说明**: 仅支持 RUNNING 或 PAUSED 状态的任务

#### pauseTask(String taskId)
- **功能**: 暂停任务
- **参数**: taskId - 任务ID
- **说明**: 仅支持 RUNNING 状态的任务

#### resumeTask(String taskId)
- **功能**: 恢复任务
- **参数**: taskId - 任务ID
- **说明**: 仅支持 PAUSED 状态的任务，从检查点恢复

#### retryTask(String taskId)
- **功能**: 重试任务
- **参数**: taskId - 任务ID
- **说明**: 仅支持 FAILED 状态的任务

#### rollbackTask(String taskId)
- **功能**: 回滚任务
- **参数**: taskId - 任务ID
- **返回**: String - 回滚任务ID
- **说明**: 创建新的回滚任务，原任务标记为 ROLLING_BACK

#### getTask(String taskId)
- **功能**: 查询任务信息
- **参数**: taskId - 任务ID
- **返回**: Task - 任务对象

#### taskExists(String taskId)
- **功能**: 检查任务是否存在
- **参数**: taskId - 任务ID
- **返回**: boolean

## 扩展开发

### 自定义配置处理器

实现 `ConfigProcessor` 接口：

```java
@Component
public class CustomConfigProcessor implements ConfigProcessor {
    
    @Override
    public Map<String, Object> process(ServiceConfig serviceConfig, 
                                      String tenantId, 
                                      Map<String, Object> config) {
        // 处理配置逻辑
        Map<String, Object> processed = new HashMap<>(config);
        processed.put("tenantId", tenantId);
        return processed;
    }
    
    @Override
    public int getOrder() {
        return 100; // 优先级
    }
}
```

### 注册处理器

```java
@Autowired
private ConfigProcessorRegistry processorRegistry;

// 为特定服务注册
processorRegistry.register("user-service", processor);

// 注册默认处理器（对所有服务生效）
processorRegistry.registerDefault(processor);
```

### 自定义配置部署器

实现 `ConfigDeployer` 接口：

```java
@Component
public class CustomConfigDeployer implements ConfigDeployer {
    
    @Override
    public boolean deploy(ServiceConfig serviceConfig, 
                         String tenantId, 
                         Map<String, Object> config) {
        // 部署逻辑
        return true;
    }
}
```

### 监听任务事件

使用 Spring 的 `@EventListener`：

```java
@Component
public class CustomEventListener {
    
    @EventListener
    public void onTaskCompleted(TaskCompletedEvent event) {
        String taskId = event.getTaskId();
        // 处理任务完成事件
    }
}
```

## 任务状态

任务生命周期包含以下状态：

- **READY** - 已创建，等待执行
- **RUNNING** - 执行中
- **PAUSED** - 已暂停
- **COMPLETED** - 已完成
- **FAILED** - 执行失败
- **STOPPED** - 已停止
- **ROLLING_BACK** - 回滚中
- **ROLLBACK_COMPLETE** - 回滚完成

## 配置说明

### 线程池配置

在 `ExecutorConfiguration` 中配置线程池参数：

```java
@Bean(name = "taskExecutor")
public Executor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(10);      // 核心线程数
    executor.setMaxPoolSize(50);       // 最大线程数
    executor.setQueueCapacity(200);    // 队列容量
    return executor;
}
```

## 注意事项

1. **执行单验证**: 创建任务前会验证执行单的有效性
2. **状态转换**: 只允许合法的状态转换，否则抛出异常
3. **租户隔离**: 同一租户的任务按FIFO顺序执行
4. **检查点恢复**: 暂停后恢复会从最后一个检查点继续
5. **异步执行**: 任务创建后立即返回，后台异步执行

## 示例代码

完整示例请参考：
- `UsageExample.java` - 基本使用示例
- `CustomConfigProcessor.java` - 自定义处理器示例
- `RegistrationExample.java` - 注册示例
