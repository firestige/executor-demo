# 重构路线图（Plan / Task / Stage）

**最后更新**: 2025-11-21  
**当前 Phase**: Phase 19 - Stage 执行增强与扩展  
**DDD 符合度**: 85%

---

## 🚀 Phase 19 - 当前最高优先级任务（2025-11-21）

> ⚠️ **重要提示**: 这些任务在执行前需要详细设计评审，**未经用户明确确认不得修改代码**

---

### RF-19-01: CompositeServiceStage 事件发布增强 ✅
**优先级**: P0 - 最高  
**预计时间**: 2-3 小时  
**实际时间**: 2.5 小时  
**状态**: ✅ 已完成 (2025-11-21)  
**责任人**: GitHub Copilot  

**完成报告**: [RF19_01_IMPLEMENTATION_COMPLETE.md](./RF19_01_IMPLEMENTATION_COMPLETE.md)

**实施方案**: 方案 B - TaskAggregate 产生 Stage 事件

**核心修改**:
- ✅ TaskAggregate 新增 2 个方法：startStage(), failStage()
- ✅ TaskDomainService 新增 2 个方法：startStage(), failStage()
- ✅ TaskExecutor 调用领域服务方法
- ✅ 编译成功，BUILD SUCCESS

**DDD 原则符合性**:
- ✅ 聚合产生事件（TaskAggregate）
- ✅ 领域服务发布事件（TaskDomainService）
- ✅ Infrastructure 调用领域服务（TaskExecutor）
- ✅ 与现有架构完全一致

**代码统计**:
- 新增代码: ~80 行（含注释）
- 修改文件: 3 个
- 编译状态: ✅ SUCCESS

**待补充**:
- ⚠️ 单元测试（推迟）
- ⚠️ 集成测试（推迟）
- ⚠️ 文档更新（推迟）

---

### RF-19-02: ASBCConfigRequestStep 请求响应重构 🔴
**优先级**: P0 - 最高  
**预计时间**: 4-6 小时  
**状态**: 🟡 待设计评审  
**责任人**: 待分配  

**需求细节**:

**1. 请求格式修改**:
```bash
curl -X POST "https://${ip}:${port}/api/sbc/traffic-switch" \
-H "Authorization: Bearer ${access_token}" \
-H "Content-Type: application/json" \
-d '{
  "calledNumberMatch": ["96765", "96755"],
  "targetTrunkGroupName": "ka-gw"
}'
```

**2. 响应格式解析**:
```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "successList": [{
      "code": 0,
      "msg": "ok",
      "calledNumberMatch": "96765",
      "targetTrunkGroupName": "ka-gw"
    }],
    "failList": [{
      "code": 500,
      "msg": "too many calledNumberMatch!",
      "calledNumberMatch": "96765",
      "targetTrunkGroupName": "ka-gw"
    }]
  }
}
```

**3. 失败信息处理**:
- 在 failureInfo 中明确提示哪些 calledNumberMatch 成功，哪些失败
- 如果 failList 不为空，整个 Step 是否失败？还是部分成功？

**待确认设计点**:
1. ✅ calledNumberRules 的拆分逻辑（逗号分隔？JSON 数组？）
2. ✅ MediaRoutingConfig 模型是否需要调整
3. ✅ 响应解析失败的处理策略
4. ✅ 部分成功的判定逻辑（全部成功才算成功？还是有成功就算成功？）
5. ✅ failureInfo 的格式设计（JSON？纯文本？）
6. ✅ access_token 的获取方式（配置？动态获取？）
7. ✅ 请求 URL 的构建方式（ip, port 的来源）

**相关文件**:
- `src/main/java/xyz/firestige/deploy/infrastructure/execution/stage/steps/ASBCConfigRequestStep.java`
- `src/main/java/xyz/firestige/deploy/application/dto/TenantConfig.java`（MediaRoutingConfig）

**设计方案**: 待讨论

---

### RF-19-03: 新增 OBServiceStage 🟡
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

### RF-19-04: PortalStage 通知重构 🟡
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
