# RF-19-06 DynamicStageFactory 策略化重构设计

**状态**: 待实施  
**优先级**: P0  
**目标**: 拆分当前巨大的 `DynamicStageFactory` 为独立的 Stage 组装策略，提升扩展性、可测试性、开闭原则符合度；确保已完成的 Portal 与 OBService 逻辑完整迁移不丢失功能。

---
## 1. 背景与现状
- 现有 `DynamicStageFactory` 单类 >700 行，包含 ASBC / Portal / Blue-Green / OBService 全部编排逻辑。
- 每新增一个服务需要修改此类，违反开闭原则。
- 不利于单元测试（无法针对单个 Stage 的编排进行隔离测试）。
- Portal 与 OBService 已完成实现，不允许功能回退；新机制必须覆盖它们。

---
## 2. 设计目标
| 目标 | 描述 |
|------|------|
| 可插拔 | 新增服务只需添加一个策略 Bean |
| 低耦合 | Stage 构建逻辑与总工厂分离 |
| 可测试 | 单独测试某个 StageAssembler 的 supports + build |
| 顺序可控 | 支持通过 order 显式控制执行顺序 |
| 条件过滤 | 支持根据 TenantConfig 决定是否创建该 Stage |
| 资源集中 | 避免每个策略重复注入大量基础设施 Bean |
| 渐进迁移 | 允许新旧工厂并行对比，保证零回归 |

---
## 3. 核心接口设计
```java
public interface StageAssembler {
    String stageName();           // 逻辑名称和标识
    int order();                  // 顺序，越小越先执行
    boolean supports(TenantConfig cfg); // 是否需要创建
    TaskStage buildStage(TenantConfig cfg, SharedStageResources r);
}
```

`SharedStageResources`（聚合依赖）:
```java
public class SharedStageResources {
    private final RestTemplate restTemplate;
    private final StringRedisTemplate redisTemplate;
    private final DeploymentConfigLoader configLoader;
    private final ObjectMapper objectMapper;
    private final AgentService agentService; // 可选，OBService 依赖
    // 预留: MetricsCollector, Tracer, LoggerAdapter 等
}
```

---
## 4. 四个策略实现（覆盖现有逻辑）
| Bean | stageName | order (建议) | supports 条件 |
|------|-----------|--------------|----------------|
| AsbcStageAssembler | asbc-gateway | 10 | `cfg.getMediaRoutingConfig() != null` |
| PortalStageAssembler | portal | 20 | `cfg.getDeployUnit() != null` |
| BlueGreenStageAssembler | blue-green-gateway | 30 | `cfg.getRouteRules() != null && !empty` |
| ObServiceStageAssembler | ob-service | 40 | `cfg.getDeployUnit() != null` |

注意：Portal 与 OBService 已完成编排，策略中需直接复用其 DataPreparer / Validator 构造逻辑，不可降级或丢失 Step。功能保持一致：
- Portal: HttpRequestStep + code=="0" 校验
- OBService: PollingStep + ConfigWriteStep（Agent 轮询 + Redis 写入）

---
## 5. 新的总工厂
```java
@Component
public class OrchestratedStageFactory implements StageFactory {
    private final List<StageAssembler> assemblers; // Spring 自动注入所有实现
    private final SharedStageResources resources;

    @Override
    public List<TaskStage> buildStages(TenantConfig cfg) {
        return assemblers.stream()
            .sorted(Comparator.comparingInt(StageAssembler::order))
            .filter(a -> a.supports(cfg))
            .map(a -> a.buildStage(cfg, resources))
            .toList();
    }
}
```

启动校验：
- 检查是否有重复的 `stageName`（Set 去重）
- 输出最终执行顺序和启用的策略列表

---
## 6. 渐进迁移步骤
| 步骤 | 内容 | 回退策略 |
|------|------|----------|
| 1 | 抽取 SharedStageResources | 保留旧工厂不动 |
| 2 | 创建四个 StageAssembler（复制旧逻辑） | 若差异 → 打印 diff |
| 3 | 新建 OrchestratedStageFactory | 与旧 DynamicStageFactory 并行运行（feature flag）|
| 4 | 编写对比测试：`old.buildStages(cfg)` VS `new.buildStages(cfg)` | 若不一致 → 记录并修复 |
| 5 | 切换 `StageFactory` Bean 指向新工厂 | 可以配置回退 `use.old.factory=true` |
| 6 | 删除旧 `DynamicStageFactory` | Git 回滚保留历史 |
| 7 | 编写文档与测试完善 | - |

---
## 7. 测试计划
### 单元测试
- 每个策略的 `supports()` 分支（正/负）
- 每个策略的 `buildStage()` 构建 Step 数与名称断言

### 集成测试
- 构造不同租户配置：
  - 仅 MediaRouting → 只创建 ASBC
  - 仅 DeployUnit → Portal + OBService
  - 全配置 → 4 个 Stage 全部出现（顺序: ASBC → Portal → BG → OB）

### 迁移对比测试
```
List<TaskStage> oldStages = oldFactory.buildStages(cfg);
List<TaskStage> newStages = newFactory.buildStages(cfg);
assertSameOrderAndNames(oldStages, newStages);
```

### 回归测试
- 运行真实任务验证事件发布（started/completed/failed）不变
- 验证 Redis 写入与 HTTP 调用仍正常

---
## 8. 日志与监控
- 启动时打印："Stage assemblers loaded: [asbc-gateway(10), portal(20), ...]"
- 编排后打印：构建的 Stage 数与名称列表
- 遇到异常：包含策略名称 + 租户 ID
- 可选：暴露 `StageAssembler` 执行耗时指标

---
## 9. 风险与缓解
| 风险 | 描述 | 缓解 |
|------|------|------|
| 复制逻辑时遗漏细节 | Portal/OBService 编排不一致 | 编写对比测试 + 代码行审查 |
| 顺序变更影响语义 | Stage 执行语义不同 | 固定 order 常量集中管理 |
| Bean 注入缺失 | 某策略依赖未注入导致 NPE | SharedStageResources 构造时校验非空 |
| Feature flag 切换不彻底 | 使用新旧工厂混乱 | 单一 `@Primary` Bean 控制 |

---
## 10. 验收标准
- 新工厂产出 Stage 顺序与旧工厂完全一致
- Portal / OBService 逻辑不回退（Step 数量与类型保持）
- 删除旧 DynamicStageFactory 后编译通过
- 所有测试（策略+对比+回归）通过
- 文档齐全（本文件 + 使用指南）

---
## 11. 后续扩展（非当前范围）
- 支持并行 Stage（DAG）编排
- 引入条件注解（@ConditionalOnProperty("stage.portal.enabled=true")）
- SPI 加载外部插件式 StageAssembler
- 运行时调整顺序（管理端下发）

---
**准备就绪，等待实施确认。**

