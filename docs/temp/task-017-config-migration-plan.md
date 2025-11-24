# T-017: 配置文件合并 - deploy-stages.yml → application.yml
- 项目现有配置结构：`ExecutorProperties`, `ExecutorPersistenceProperties`
- [Spring Boot Profiles](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.profiles)
- [Spring Boot Configuration Properties](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)

## 9. 参考资料

---

- [ ] 代码审查通过
- [ ] 文档已更新
- [ ] 支持多环境配置（dev/test/prod）
- [ ] 单元测试和集成测试通过
- [ ] 所有使用配置的地方已更新为 Spring 注入
- [ ] 移除自定义配置加载逻辑
- [ ] 创建 ExecutorStagesProperties 及相关配置类
- [ ] deploy-stages.yml 内容已迁移到 application.yml

## 8. Definition of Done

---

- 文档同步更新（README.md 等）
- 配置迁移后充分测试
- 保留原 `deploy-stages.yml` 作为备份（暂不删除）
### 7.2 注意事项

- 需要确保向后兼容性
- 配置绑定失败可能导致启动失败
- 配置结构变更可能影响现有功能
### 7.1 风险

## 7. 风险与注意事项

---

- 测试类
- 使用配置的业务类
- `src/main/resources/application.yml`
### 6.3 修改的文件

- 相关配置类（BlueGreenGatewayStageConfig 等）
- `ExecutorStagesProperties.java`
### 6.2 新增的文件

- 自定义配置加载类（待识别）
- `src/main/resources/deploy-stages.yml`
### 6.1 删除的文件

## 6. 影响范围

---

```
          max-attempts: 20
          interval-seconds: 5
        - type: health-check
      steps:
    blue-green-gateway:
  stages:
executor:
# application-prod.yml

          max-attempts: 3
          interval-seconds: 1
        - type: health-check
      steps:
    blue-green-gateway:
  stages:
executor:
# application-dev.yml
```yaml

### 5.3 多环境支持

```
      enabled: false
    asbc-gateway:
    
          channel: "portal:reload"
        - type: pubsub-broadcast
        - type: redis-write
      steps:
      enabled: true
    portal:
    
          expected-version-key: version
          path: /health
        - type: health-check
          key-pattern: "gateway:config:{tenantId}"
        - type: redis-write
      steps:
      enabled: true
    blue-green-gateway:
  stages:
executor:
```yaml

### 5.2 配置示例

```
}
    // getters/setters
    
    private ASBCGatewayStageConfig asbcGateway;
    private PortalStageConfig portal;
    private BlueGreenGatewayStageConfig blueGreenGateway;
public class ExecutorStagesProperties {
@Validated
@ConfigurationProperties(prefix = "executor.stages")
```java

### 5.1 配置类设计

## 5. 技术方案

---

3. 多环境测试：验证 Profile 支持
2. 集成测试：验证配置加载和使用正常
1. 单元测试：验证配置绑定正确
### 4.3 测试验证

4. 更新依赖这些配置的类
3. 替换自定义加载逻辑为 Spring 标准注入
2. 创建 `ExecutorStagesProperties` 配置类
1. 识别当前自定义加载逻辑的位置
### 4.2 代码重构

4. 创建对应的 `@ConfigurationProperties` 类
3. 将配置内容迁移到 `application.yml`
2. 设计新的配置结构（在 `executor.stages` 命名空间下）
1. 分析 `deploy-stages.yml` 当前结构
### 4.1 配置迁移

## 4. 实施计划

---

- 支持外部化配置（命令行参数、环境变量等）
- 支持多环境配置（dev/test/prod）
- 使用 Spring Boot 的 `@ConfigurationProperties` 绑定
- 移除自定义配置加载代码
### 3.2 加载逻辑

```
      ...
    asbc-gateway:
      ...
    portal:
      ...
    blue-green-gateway:
    # 原 deploy-stages.yml 的内容
  stages:
executor:
# application.yml
```yaml

将 `deploy-stages.yml` 内容迁移到 `application.yml`，使用统一的命名空间：
### 3.1 配置结构

## 3. 期望结果

---

- 难以利用 Spring 的配置特性（Profile、外部化配置等）
- 不符合 Spring Boot 最佳实践
- 自定义加载逻辑增加维护成本
- 配置文件分散，不易管理
### 2.2 问题

- 与 Spring Boot 标准配置体系分离
- 使用自定义加载逻辑读取配置
- 存在独立的 `deploy-stages.yml` 配置文件
### 2.1 现状

## 2. 当前问题

---

将当前独立的 `deploy-stages.yml` 配置文件合并到 Spring Boot 标准的 `application.yml` 中，统一配置文件管理和加载逻辑。

## 1. 任务目标

---

> **创建时间**: 2025-11-24
> **状态**: 待办  
> **优先级**: P1  
> **任务 ID**: T-017  


