package xyz.firestige.executor.unit.application;

/**
 * PlanDomainService 单元测试（待重构）
 *
 * ===== DDD 重构说明 =====
 * 原 PlanApplicationService 已重构为 PlanDomainService
 * 测试文件需要根据新架构重新实现
 *
 * ===== 原测试场景 =====
 * 1. createSwitchTask 成功场景
 *    - 输入：有效的租户配置列表
 *    - 期望：返回成功的 PlanCreationResult，包含 PlanInfo 和 TaskInfo 列表
 *    - 验证：Plan 创建成功，Task 列表不为空，状态正确
 *
 * 2. createSwitchTask 验证失败场景
 *    - 输入：无效的租户配置（缺少必需字段）
 *    - 期望：返回验证失败的 PlanCreationResult
 *    - 验证：包含 ValidationSummary 和错误详情
 *
 * 3. pausePlan 成功场景
 *    - 输入：有效的 planId
 *    - 期望：返回成功的 PlanOperationResult
 *    - 验证：Plan 状态变为 PAUSED
 *
 * 4. pausePlan Plan不存在场景
 *    - 输入：不存在的 planId
 *    - 期望：返回失败的 PlanOperationResult
 *    - 验证：错误类型为 VALIDATION_ERROR
 *
 * 5. resumePlan 成功场景
 *    - 输入：已暂停的 planId
 *    - 期望：返回成功的 PlanOperationResult
 *    - 验证：Plan 状态变为 RUNNING
 *
 * 6. rollbackPlan 成功场景
 *    - 输入：失败的 planId
 *    - 期望：返回成功的 PlanOperationResult
 *    - 验证：回滚操作已触发
 *
 * 7. retryPlan 成功场景（不使用 checkpoint）
 *    - 输入：失败的 planId, fromCheckpoint=false
 *    - 期望：返回成功的 PlanOperationResult
 *    - 验证：Task 从头开始重试
 *
 * 8. retryPlan 成功场景（使用 checkpoint）
 *    - 输入：失败的 planId, fromCheckpoint=true
 *    - 期望：返回成功的 PlanOperationResult
 *    - 验证：Task 从 checkpoint 恢复
 *
 * ===== 新架构测试路径 =====
 * 位置：src/test/java/xyz/firestige/executor/unit/domain/plan/PlanDomainServiceTest.java
 *
 * 依赖变化：
 * - 使用 PlanRepository 接口（Mock InMemoryPlanRepository）
 * - 使用 TaskRepository 接口（Mock InMemoryTaskRepository）
 * - 移除直接的 Map 依赖
 *
 * 方法名变化：
 * - createSwitchTask → createPlan （职责变化：只创建 Plan，不创建 Task）
 * - 其他方法保持不变
 *
 * ===== TODO =====
 * [ ] 根据新的 PlanDomainService 设计重写测试
 * [ ] 使用 Repository Mock 替代 Map
 * [ ] 更新测试数据准备逻辑
 * [ ] 验证领域服务的单一职责
 */
@Deprecated
public class PlanApplicationServiceTest {
    // Tests disabled - waiting for DDD refactoring completion
    // See class-level javadoc for test scenarios and migration path
}

