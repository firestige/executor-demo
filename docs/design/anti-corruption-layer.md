# 防腐层工厂设计（Anti-Corruption Layer for ServiceConfigFactory）
> 参见：`xyz.firestige.deploy.domain.stage.factory.*`，`xyz.firestige.deploy.domain.stage.config.*`。
---

- [x] 标注与 StageFactory 的层次边界
- [x] 给出调用链路与约束
- [x] 梳理工厂族与组合器结构
- [x] 明确 ACL 工厂职责与边界
## 7. Definition of Done（T-010）
---

- 混用外部模型：禁止在 StageFactory 中直接使用 TenantConfig。
- 字段丢失：转换时校验必填字段，抛出明确异常（ValidationException）。
- 类型识别错误：serviceType 与工厂 supports 对齐；建议集中枚举或常量定义。
## 6. 风险与约束
---

| 层次 | Domain（防腐层职责） | Infrastructure（执行引擎阶段构造）|
| 产出 | ServiceConfig | List<TaskStage> |
| 输入 | TenantConfig (DTO) | ServiceConfig (VO) |
|--------|---------|-------------|
| 关注点 | ACL 工厂 | StageFactory |
## 5. 与 StageFactory 的边界
---

5. 交给 StageFactory 组装 `TaskStage`
4. 产出 `ServiceConfig`（领域层 VO）
3. 命中具体工厂（如 BlueGreenGatewayConfigFactory）
2. 调用 `ServiceConfigFactoryComposite.convert(cfg)`
1. 应用层拿到 `TenantConfig`（含 serviceType 等）
## 4. 交互流程
---

- 扩展性：新增服务类型时，只需新增工厂实现并注册。
- 组合器：统一入口，遍历工厂列表，命中即返回转换结果。
- 工厂+策略：不同服务类型由不同工厂处理（supports(String type)）。
- 防腐层：`TenantConfig` -> `ServiceConfig` 的边界隔离，避免外部 DTO 渗透领域层。
## 3. 设计要点
---

| ServiceConfig | `domain.stage.config` | 领域层配置 VO（父类型）|
| ServiceConfigFactoryComposite | 同上 | 组合器，委派给具体实现 |
| ASBCGatewayConfigFactory | 同上 | ASBC 网关配置转换 |
| PortalConfigFactory | 同上 | Portal 配置转换 |
| BlueGreenGatewayConfigFactory | 同上 | 蓝绿网关配置转换 |
| ServiceConfigFactory | `domain.stage.factory` | 工厂接口，定义 supports/convert |
|--------|----|------|
| 类/接口 | 包 | 角色 |
## 2. 代码结构
---

应用层输入 `TenantConfig`（DTO）需要转换为领域层 `ServiceConfig`（值对象）供 StageFactory/TaskStage 使用。为隔离外部模型并保持领域纯净，引入防腐层（ACL）工厂族：`ServiceConfigFactory` 及其实现。
## 1. 背景与目标
---

> 最后更新: 2025-11-23
> 状态: 完成  
> 任务: T-010  


