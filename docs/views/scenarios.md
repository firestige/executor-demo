# 场景视图 - 补充说明

> **最后更新**: 2025-11-22  
> **PlantUML 图**: [scenarios.puml](scenarios.puml)

---

## 核心用例场景

### 场景 1: 创建部署计划

**参与者**: 运维人员

**前置条件**: 
- 已获取待部署的租户列表
- 确定部署策略（蓝绿切换/金丝雀）

**主流程**:
1. 运维人员在管理界面输入部署参数
2. 系统创建 Plan 聚合
3. 为每个租户创建对应的 Task
4. 返回 Plan ID 用于后续操作

**成功后置条件**: Plan 状态为 CREATED，包含所有 Task

**API 示例**:
```json
POST /api/plans
{
  "name": "2025-11-22 蓝绿切换部署",
  "tenantIds": ["tenant1", "tenant2", "tenant3"],
  "executorType": "BLUE_GREEN_SWITCH"
}

Response:
{
  "planId": 123,
  "state": "CREATED",
  "taskCount": 3
}
```

---

### 场景 2: 启动部署

**参与者**: 运维人员

**前置条件**: 
- Plan 状态为 CREATED
- 所有 Task 准备就绪

**主流程**:
1. 运维人员点击"启动"按钮
2. Plan 状态变更为 RUNNING
3. 系统按顺序执行每个 Task
4. 每个 Task 完成后更新状态
5. 所有 Task 完成后，Plan 变更为 COMPLETED

**成功后置条件**: Plan 状态为 COMPLETED，所有 Task 状态为 COMPLETED

**API 示例**:
```json
POST /api/plans/123/start

Response:
{
  "planId": 123,
  "state": "RUNNING",
  "startedAt": "2025-11-22T10:00:00"
}
```

---

### 场景 3: 执行蓝绿切换

**参与者**: Task执行器（系统内部）

**前置条件**: 
- Task 状态为 RUNNING
- 租户信息可获取
- Gateway 服务可用

**主流程**:
1. 执行器调用 OB Service 获取租户信息
2. 执行器调用 Gateway 切换流量到蓝环境
3. 保存 Checkpoint（记录"已切换"状态）
4. 执行器调用 OB Service 更新租户状态
5. 返回执行成功结果

**成功后置条件**: Task 状态为 COMPLETED，租户已切换到蓝环境

**关键步骤详解**:
```java
// BlueGreenSwitchExecutor.execute()
public Result<Void> execute(Task task, Checkpoint checkpoint) {
    // 步骤1: 获取租户信息
    TenantInfo tenant = obServiceGateway.getTenantInfo(task.getTenantId());
    
    // 步骤2: 切换网关
    gatewayClient.switchToBlue(task.getTenantId());
    
    // 步骤3: 保存Checkpoint
    task.saveCheckpoint(Checkpoint.of("SWITCHED", Map.of("tenant", tenant)));
    
    // 步骤4: 更新租户状态
    obServiceGateway.updateTenantStatus(task.getTenantId(), "BLUE");
    
    return Result.success();
}
```

---

### 场景 4: 异常处理 - Task 失败

**参与者**: 系统（自动）

**触发条件**: 
- 执行器操作失败（网络超时、服务不可用）
- 业务规则校验失败

**主流程**:
1. 执行器捕获异常
2. 保存当前 Checkpoint（记录失败位置）
3. Task 状态变更为 FAILED
4. 记录 FailureInfo（失败原因、错误码）
5. Plan 检测到 Task 失败，状态变更为 FAILED
6. 通知运维人员

**成功后置条件**: 
- Task 状态为 FAILED，保存了 Checkpoint 和 FailureInfo
- Plan 状态为 FAILED

**失败详情示例**:
```json
GET /api/tasks/456

Response:
{
  "taskId": 456,
  "state": "FAILED",
  "failureInfo": {
    "reason": "Gateway API timeout",
    "errorCode": "GATEWAY_TIMEOUT",
    "timestamp": "2025-11-22T10:15:30"
  },
  "checkpoint": {
    "step": "SWITCH_GATEWAY",
    "data": {
      "completedSteps": ["GET_TENANT_INFO"],
      "failedStep": "SWITCH_TO_BLUE"
    }
  }
}
```

---

### 场景 5: 重试失败的 Task

**参与者**: 运维人员

**前置条件**: 
- Task 状态为 FAILED
- 失败原因已排查（如网络恢复）

**主流程**:
1. 运维人员选择失败的 Task
2. 选择"从断点重试"或"从头重试"
3. 系统恢复 Checkpoint（如果指定）
4. Task 状态变更为 RUNNING
5. 执行器从断点继续执行
6. 执行成功后，Task 状态变更为 COMPLETED
7. 检查 Plan 是否所有 Task 都完成

**成功后置条件**: 
- Task 状态为 COMPLETED
- Checkpoint 已清除
- 如果所有 Task 完成，Plan 状态变更为 COMPLETED

**API 示例**:
```json
POST /api/tasks/456/retry
{
  "fromCheckpoint": true
}

Response:
{
  "taskId": 456,
  "state": "RUNNING",
  "retriedAt": "2025-11-22T10:30:00"
}
```

**重试逻辑**:
```java
// Task.retry()
public Result<Void> retry(TaskExecutor executor, boolean fromCheckpoint) {
    // 验证状态
    if (this.state != TaskState.FAILED && this.state != TaskState.PAUSED) {
        return Result.failure("Cannot retry task in state: " + this.state);
    }
    
    // 恢复Checkpoint
    Checkpoint checkpoint = fromCheckpoint ? this.checkpoint : null;
    
    // 执行
    this.changeState(TaskState.RUNNING);
    Result<Void> result = executor.execute(this, checkpoint);
    
    if (result.isSuccess()) {
        this.changeState(TaskState.COMPLETED);
        this.checkpoint = null;  // 清除Checkpoint
    } else {
        this.changeState(TaskState.FAILED);
    }
    
    return result;
}
```

---

## 扩展场景

### 场景 6: 配置写入并确认生效（Redis ACK）

**参与者**: 运维人员 / 系统内部 StageExecutor

**前置条件**: 目标服务已在运行；Redis 可用

**主流程**:
1. 写入新配置到 Redis（含版本号）
2. 发布 Pub/Sub 通知客户端
3. 轮询客户端状态端点提取当前版本
4. 比对版本一致 → 成功；否则按重试策略继续

**失败分支**: 超时 / 版本不匹配 / 端点错误

**结果**: AckResult 记录 attempts/elapsed/reason

---

### 场景 7: 关键任务锁/配置续期（Redis Renewal）

**参与者**: RenewalService 后台线程

**前置条件**: 已注册 RenewalTask（key 集合 + 策略）

**主流程**:
1. 调度时间轮 tick 到期
2. 评估停止条件（已完成 / 超时 / 次数耗尽）
3. 满足续期条件则刷新 TTL
4. 记录指标（成功/失败/跳过）

**结果**: Key 保持活跃直至业务结束；失败不阻断其他 Key

---

## 用例关系图

```
创建部署计划 ──► 启动部署 ──► 执行蓝绿切换
                    │
                    ├─► 暂停部署 ──► 恢复部署
                    │
                    └─► Task失败 ──┬─► 重试Task
                                   │
                                   └─► 回滚Task
```

---

## 相关文档

- [架构总纲](../architecture-overview.md)
- [进程视图](process-view.puml) - 执行流程细节
- [执行策略设计](../design/execution-strategy.md)
- [API 文档](../api/rest-api.md)（待创建）
