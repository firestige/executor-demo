package xyz.firestige.deploy.infrastructure.execution.stage.preparer;

import xyz.firestige.deploy.domain.task.TaskRuntimeContext;

/**
 * 数据准备器接口
 * 负责为 Step 准备执行所需的数据
 *
 * <p>职责：
 * <ul>
 *   <li>从 TenantConfig 等业务对象提取数据</li>
 *   <li>转换为 Step 可消费的格式</li>
 *   <li>放入 TaskRuntimeContext 供 Step 使用</li>
 * </ul>
 *
 * <p>示例：
 * <pre>
 * DataPreparer asbcPreparer = (runtimeContext) -> {
 *     // 提取数据
 *     String endpoint = resolveEndpoint("asbc");
 *     Map<String, Object> body = buildRequestBody(config);
 *
 *     // 放入 TaskRuntimeContext
 *     runtimeContext.addVariable("url", endpoint);
 *     runtimeContext.addVariable("body", body);
 * };
 * </pre>
 *
 * @since RF-19 三层抽象架构
 */
@FunctionalInterface
public interface DataPreparer {

    /**
     * 准备 Step 执行所需的数据
     *
     * @param runtimeContext Task 运行时上下文（用于存放准备好的数据）
     */
    void prepare(TaskRuntimeContext runtimeContext);
}

