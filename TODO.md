# 重构路线图（Plan / Task / Stage）

**最后更新**: 2025-11-22  
**当前 Phase**: Phase 19 ✅ 已完成  
**DDD 符合度**: 90%

---

## 🎉 Phase 19 完成总结 (2025-11-21 ~ 2025-11-22)

**完成报告**: [RF19_COMPLETE_SUMMARY.md](./RF19_COMPLETE_SUMMARY.md) | [RF19_ALL_TASKS_COMPLETION_CHECK.md](./RF19_ALL_TASKS_COMPLETION_CHECK.md)

### RF-19 重构任务完成情况

| 任务 | 状态 | 实际时间 | 完成日期 |
|------|------|---------|---------|
| RF-19-01: CompositeServiceStage 事件发布 | ✅ | 2.5h | 2025-11-21 |
| RF-19-02: ASBC Gateway Stage | ✅ | 3h | 2025-11-21 |
| RF-19-03: OBService Stage | ✅ | 2h | 2025-11-21 |
| RF-19-04: Portal Stage | ✅ | 1h | 2025-11-21 |
| 额外: 蓝绿网关迁移到 RF-19 | ✅ | 3h | 2025-11-21 |
| 额外: Auth 配置修复 | ✅ | 0.5h | 2025-11-22 |
| 额外: 旧架构代码清理 | ✅ | 1h | 2025-11-22 |

### 核心成果

- ✅ **4 个服务** 全部使用 RF-19 三层抽象架构
- ✅ **4 个通用 Step**，100% 复用率
- ✅ **YAML 退化** 为运行时无关配置
- ✅ **删除旧代码** ~950 行（9个类）
- ✅ **架构统一** 100%

### 代码统计

- 新增代码: ~1400 行
- 删除代码: ~950 行
- 净增代码: ~450 行

### Git 提交

- ✅ 7 个提交，全部成功
- ✅ 编译验证通过
- ✅ 无遗留引用

---

## 🚨 新的最高优先级任务 (P0)

### RF-19-05: 环境变量占位符机制设计与实现 (P0)
**目标**: 支持在 deploy-stages.yml 中用 `{$VAR:default}` 格式引用环境变量，增强多环境配置灵活性，减少直接硬编码的 IP / 端口 / 前缀。
**范围**:
- 语法: `{$VAR_NAME}` 或 `{$VAR_NAME:defaultValue}`
- 解析时机: YAML 反序列化后，对所有 String 深度遍历替换
- 失败策略: 缺省且无默认值 → 启动失败并列出全部缺失变量
- 安全策略: 支持 _SECRET 变量不打印实际值
**交付**:
- 占位符解析器（含缓存）
- 集成 DeploymentConfigLoader 或 TemplateResolver
- 单元测试 + 集成测试（覆盖存在/缺失/默认值）
- 文档: ENV_PLACEHOLDER_GUIDE.md

### RF-19-06: DynamicStageFactory 策略化重构 (P0)
**目标**: 将当前庞大的 `DynamicStageFactory` 拆分为多策略（StageAssembler）+ 统一 orchestrator，满足开闭原则与可测试性。
**接口设计**:
```
public interface StageAssembler {
  String stageName();
  int order();
  boolean supports(TenantConfig cfg);
  TaskStage buildStage(TenantConfig cfg, SharedStageResources r);
}
```
**实现**:
- 新建 `SharedStageResources` 聚合依赖 (RestTemplate, RedisTemplate, DeploymentConfigLoader, ObjectMapper, AgentService ...)
- 每个现有 Stage 拆分为单独 Assembler Bean
- 新建 `OrchestratedStageFactory` 读取所有策略 → 排序 → 过滤 supports → build
- 旧 `DynamicStageFactory` 逐步退役（双运行期对比 → 替换 → 删除）
**交付**:
- 接口 + 资源类 + 4 个策略实现
- 运行期差异对比日志 (diff)
- 单元测试（每个 assembler）+ 集成测试（组合顺序）
- 文档: STAGE_ASSEMBLER_STRATEGY_DESIGN.md

---

## 📋 当前待办事项（旧）

(已完成的 RF-19 任务已归档；以下为保留的优化建议)
1. 实现 Nacos 服务发现（当前使用 fallback）
2. 实现 OAuth2 token provider（当前只有 random）
3. 添加更多单元测试和集成测试
4. 完善监控和可观测性

---

## 📐 架构设计原则
**优先级**: P1 - 中等  
**预计时间**: 6-8 小时  
**状态**: 🟡 待设计评审  
**责任人**: 待分配  

**需求描述**:
- 新增 OBServiceStage，定时调用 AgentService 判断是否写入 Redis

**执行逻辑**:
1. 定时调用 `xyz.firestige.service.AgentService#judgeAgent()`
2. 当返回 true 时，执行类似 KeyValueWriteStep 的逻辑
3. 使用 tenantId 拼接 Redis key
4. 使用 HSET 命令写入：Field = "ob-campaign"，Value = ObConfig 的 JSON 字符串

**待确认设计点**:
1. ✅ AgentService 的接口定义（参数、返回值）
2. ✅ 定时调用的间隔配置（可配置？固定？默认值？）
3. ✅ 定时调用的最大尝试次数
4. ✅ 超时失败的处理策略
5. ✅ ObConfig 模型定义（包含哪些字段？）
6. ✅ ObConfig 的来源（TenantConfig 中？还是外部获取？）
7. ✅ Redis key 的命名规则（前缀？namespace？）
8. ✅ 与 CheckpointService 的集成（是否需要保存检查点？）
9. ✅ 是否需要健康检查确认（类似 HealthCheckStep）

**相关文件**:
- 待创建：`src/main/java/xyz/firestige/deploy/domain/stage/OBServiceStage.java`
- 待创建：`src/main/java/xyz/firestige/deploy/domain/stage/model/ObConfig.java`
- 待定义：`xyz.firestige.service.AgentService`

**设计方案**: 待讨论

---

### RF-19-04: Portal Stage 实施 🟡
**优先级**: P1  
**预计时间**: 1 小时  
**状态**: 🟡 规格已确认，待实施  
**责任人**: GitHub Copilot  

**接口规格**: [RF19_04_PORTAL_SPECIFICATION.md](./RF19_04_PORTAL_SPECIFICATION.md)

**Portal 接口**:
- Endpoint: `POST /icc-agent-portal/inner/v1/notify/bgSwitch`
- 请求: `{tenantId, targetDeployUnit, timestamp}`
- 响应: `{code, msg}`
- 成功判断: `code == "0"`

**实施内容**:
- [ ] PortalResponse 模型类
- [ ] PortalDataPreparer（准备请求数据）
- [ ] PortalResultValidator（验证响应）
- [ ] DynamicStageFactory.createPortalStage()

**复用情况**:
- ✅ 完全复用 HttpRequestStep
- ✅ 业务逻辑在 Preparer + Validator
- ✅ 验证了三层抽象的可扩展性

**相关文件**:
- `HttpRequestStep.java` (复用)
- `PortalResponse.java` (新增)
- Portal DataPreparer 和 Validator (新增)
- `DynamicStageFactory.java` (新增方法)
**优先��**: P1 - 中等  
**预计时间**: 3-4 小时  
**状态**: 🟡 待设计评审  
**责任人**: 待分配  

**需求描述**:
- PortalStage 改为直接通过 NotificationStep 向 endpoint 发送 HTTP 请求
- 解析响应码确认 Portal 是否接受成功

**执行逻辑**:
1. 使用 NotificationStep 发送 HTTP 请求（GET 或 POST）
2. 解析响应状态码（2xx 认为成功）
3. 接收成功 → Stage 成功
4. 接收失败 → Stage 失败

**待确认设计点**:
1. ✅ NotificationStep 的实现方式（HTTP 客户端选择：RestTemplate？OkHttp？）
2. ✅ 请求方法的选择逻辑（如何决定 GET 还是 POST？配置？）
3. ✅ 请求参数的构建（Body 内容？Headers？Query 参数？）
4. ✅ 响应码的成功判定规则（仅 200？还是 2xx 范围？）
5. ✅ 响应 Body 是否需要解析和验证
6. ✅ 超时配置（连接超时、读取超时）
7. ✅ 重试机制（是否需要？几次？）
8. ✅ 与现有 HealthCheck 机制的区别和关系
9. ✅ endpoint 的来源（TenantConfig.networkEndpoints？）

**相关文件**:
- `src/main/java/xyz/firestige/deploy/domain/stage/PortalStage.java`
- `src/main/java/xyz/firestige/deploy/infrastructure/execution/stage/steps/NotificationStep.java`

**设计方案**: 待讨论

---

## 📋 Phase 19 执行计划

### 设计评审阶段（本周）
- [ ] **Day 1-2**: RF-19-01 CompositeServiceStage 事件发布设计评审
- [ ] **Day 2-3**: RF-19-02 ASBCConfigRequestStep 设计评审
- [ ] **Day 3-4**: RF-19-03 OBServiceStage 设计评审
- [ ] **Day 4-5**: RF-19-04 PortalStage 设计评审

### 实现阶段（下周）
- [ ] RF-19-01 实现 + 测试（用户确认后）
- [ ] RF-19-02 实现 + 测试（用户确认后）
- [ ] RF-19-03 实现 + 测试（用户确认后）
- [ ] RF-19-04 实现 + 测试（用户确认后）

### 验证阶段
- [ ] 集成测试验证
- [ ] 文档更新
- [ ] Code Review

---



- **稳定性优先**: Facade 方法签名与入参 DTO 保持稳定
- **简洁架构**: 不保留双系统，旧代码经审核后直接移除
- **手动控制**: 回滚与重试均为手动触发
- **健康检查**: 固定 3 秒间隔，最多 10 次尝试，所有端点成功才通过
- **事件幂等**: task/plan 级 sequenceId 单调递增
- **并发控制**: Plan.maxConcurrency 控制任务并行度；租户级冲突锁防止同租户并发
- **协作式控制**: 暂停/取消仅在 Stage 边界响应
- **MDC 管理**: 执行完成或异常必须清理
- **配置优先级**: TenantDeployConfig > application 配置 > 默认值
- **防腐层**: PlanFactory 深拷贝外部 DTO 为内部聚合
- **Checkpoint**: 存储可插拔（InMemory/Redis）
- **Plan 独立**: 不同 Plan 之间无依赖

---

## 📚 历史归档（Phase 0-18 已完成）

### Phase 0-16 — ✅ 完成 (2025-11-17)
**核心成就**:
- ✅ 核心架构建立（领域模型、状态机、调度器）
- ✅ Stage/Step 执行模型与健康检查
- ✅ Checkpoint 持久化（InMemory + Redis）
- ✅ 回滚快照与事件系统
- ✅ 并发控制与冲突锁释放
- ✅ 可观测性（Metrics + MDC）
- ✅ 完整文档体系

**详细日志**: `develop.log`

---

### Phase 17 — ✅ 完成 (2025-11-17)
