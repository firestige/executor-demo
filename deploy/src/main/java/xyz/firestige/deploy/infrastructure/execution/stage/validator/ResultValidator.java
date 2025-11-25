package xyz.firestige.deploy.infrastructure.execution.stage.validator;

import xyz.firestige.deploy.domain.task.TaskRuntimeContext;

/**
 * 结果验证器接口
 * 负责验证 Step 执行结果是否符合业务预期
 *
 * <p>职责：
 * <ul>
 *   <li>从 TaskRuntimeContext 提取 Step 的执行结果</li>
 *   <li>根据业务规则判断成功或失败</li>
 *   <li>返回 ValidationResult（包含成功/失败和详细消息）</li>
 * </ul>
 *
 * <p>示例：
 * <pre>
 * ResultValidator asbcValidator = (runtimeContext) -> {
 *     HttpResponseData response = runtimeContext.getAdditionalData("httpResponse", HttpResponseData.class);
 *
 *     // 业务验证逻辑
 *     if (!response.is2xx()) {
 *         return ValidationResult.failure("HTTP 状态码错误: " + response.getStatusCode());
 *     }
 *
 *     // 解析响应并验证
 *     ASBCResponse asbcResponse = response.parseBody(ASBCResponse.class);
 *     if (asbcResponse.getCode() != 0) {
 *         return ValidationResult.failure("ASBC 返回错误: " + asbcResponse.getMsg());
 *     }
 *
 *     return ValidationResult.success("配置成功");
 * };
 * </pre>
 *
 * @since RF-19 三层抽象架构
 */
@FunctionalInterface
public interface ResultValidator {

    /**
     * 验证 Step 执行结果
     *
     * @param runtimeContext Task 运行时上下文（包含 Step 的执行结果）
     * @return 验证结果（成功/失败 + 详细消息）
     */
    ValidationResult validate(TaskRuntimeContext runtimeContext);
}

