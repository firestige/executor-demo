# 技术栈清单

> **最后更新**: 2025-11-22  
> **数据来源**: pom.xml

---

## 核心框架

| 技术 | 版本 | 用途 |
|------|------|------|
| **Java** | 17 | 开发语言 |
| **Spring Boot** | 3.2.12 | 应用框架 |
| **Spring Data Redis** | 3.2.x | Redis 集成（状态持久化） |
| **Spring Web** | 3.2.x | REST API、RestTemplate（调用外部服务） |

---

## 持久化方案

### Redis
- **用途**: Checkpoint 持久化（断点续传）
- **实现**: `RedisCheckpointRepository`
- **位置**: `xyz.firestige.deploy.infrastructure.persistence.checkpoint`

### InMemory
- **用途**: 
  - Plan 运行时状态存储（`InMemoryPlanRepository`）
  - Task 运行时状态存储（`InMemoryTaskRepository`）
  - Checkpoint 备用存储（`InMemoryCheckpointRepository`）
- **位置**: `xyz.firestige.deploy.infrastructure.persistence.*`

**重要说明**: 
- **不使用关系型数据库（MySQL/PostgreSQL）**
- **不使用 JPA/Hibernate**
- 所有状态存储在 Redis（持久化） + 内存（运行时缓存）

---

## 序列化

| 技术 | 用途 |
|------|------|
| **Jackson** | JSON 序列化/反序列化 |
| **jackson-datatype-jsr310** | Java 8 时间类型支持 |
| **jackson-dataformat-yaml** | YAML 配置解析 |

---

## 验证

| 技术 | 用途 |
|------|------|
| **Jakarta Validation API** | 参数校验接口 |
| **Hibernate Validator** | 校验实现（与 JPA 无关） |

---

## 服务发现（可选）

| 技术 | 版本 | 用途 |
|------|------|------|
| **Nacos Client** | 2.2.3 | 服务发现（可选，支持降级到固定 IP） |

**降级策略**: 当 Nacos 不可用时，自动降级到配置的固定 IP 地址

---

## 监控与指标

| 技术 | 版本 | 用途 |
|------|------|------|
| **Micrometer** | 1.12.0 | 可选的指标集成 |

---

## 测试框架

| 技术 | 用途 |
|------|------|
| **spring-boot-starter-test** | 单元测试、集成测试 |
| **JMH** | 基准测试（性能测试） |
| **Testcontainers** | 容器化测试（Redis 测试环境） |
| **Awaitility** | 异步测试辅助 |
| **JavaFaker** | 测试数据生成 |

---

## 外部依赖（Mock）

以下服务在 `xyz.firestige.deploy` 包外，为 Mock 的外部依赖：

| 服务 | 用途 | 说明 |
|------|------|------|
| **OB Service** | OceanBase 租户管理 | 模拟外部服务，非项目核心 |
| **Gateway** | 网关管理服务 | 模拟外部服务，非项目核心 |
| **Portal** | Portal 服务 | 模拟外部服务，非项目核心 |

**重要**: 文档和分析应聚焦于 `xyz.firestige.deploy` 包内的代码，外部依赖仅作为集成点理解。

---

## 架构模式

- **DDD 分层架构**: Facade → Application → Domain → Infrastructure
- **策略模式**: TaskExecutor 执行器扩展
- **状态机模式**: Plan/Task 生命周期管理
- **Result 模式**: 显式错误处理（替代异常）
- **仓储模式**: 领域对象持久化抽象

---

## 配置管理

- **application.yml**: 主配置文件
- **环境变量**: 敏感配置覆盖
- **Nacos 配置中心**: 可选的动态配置

---

## 部署方式

- **可执行 JAR**: Spring Boot 打包
- **Docker**: 容器化部署（可选）
- **多实例**: 支持水平扩展（无状态设计）

---

## 相关文档

- [架构总纲](architecture-overview.md) - 架构设计概览
- [物理视图](views/physical-view.puml) - 部署架构图
- [持久化设计](design/persistence.md) - Redis + InMemory 详细设计

