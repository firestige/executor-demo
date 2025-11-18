package xyz.firestige.deploy.validation.validator;

import xyz.firestige.dto.deploy.TenantDeployConfig;
import xyz.firestige.deploy.validation.ConfigValidator;
import xyz.firestige.deploy.validation.ValidationError;
import xyz.firestige.deploy.validation.ValidationResult;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 冲突检测校验器
 * 检测同一租户是否在其他正在执行的任务中
 */
public class ConflictValidator implements ConfigValidator {

    /**
     * 正在执行的租户集合
     * Key: tenantId, Value: taskId
     */
    private static final Map<String, String> runningTenants = new ConcurrentHashMap<>();

    /**
     * 当前批次的租户 ID 集合（用于检测同批次内的重复）
     */
    private ThreadLocal<Set<String>> currentBatchTenants = ThreadLocal.withInitial(HashSet::new);

    @Override
    public ValidationResult validate(TenantDeployConfig config) {
        ValidationResult result = new ValidationResult(true);

        String tenantId = config.getTenantId();
        if (tenantId == null || tenantId.trim().isEmpty()) {
            // tenantId 为空的情况由 TenantIdValidator 处理
            return result;
        }

        // 检测是否在其他任务中运行
        String existingTaskId = runningTenants.get(tenantId);
        if (existingTaskId != null) {
            result.addError(ValidationError.of(
                    "tenantId",
                    "租户 " + tenantId + " 已在任务 " + existingTaskId + " 中执行，不能重复提交",
                    tenantId
            ));
        }

        // 检测同批次内是否重复
        Set<String> batchTenants = currentBatchTenants.get();
        if (batchTenants.contains(tenantId)) {
            result.addError(ValidationError.of(
                    "tenantId",
                    "同一批次中存在重复的租户 ID: " + tenantId,
                    tenantId
            ));
        } else {
            batchTenants.add(tenantId);
        }

        return result;
    }

    /**
     * 开始新的批次校验
     */
    public void startBatch() {
        currentBatchTenants.get().clear();
    }

    /**
     * 结束批次校验
     */
    public void endBatch() {
        currentBatchTenants.remove();
    }

    /**
     * 注册租户为正在运行
     */
    public static void registerRunningTenant(String tenantId, String taskId) {
        runningTenants.put(tenantId, taskId);
    }

    /**
     * 取消注册正在运行的租户
     */
    public static void unregisterRunningTenant(String tenantId) {
        runningTenants.remove(tenantId);
    }

    /**
     * 检查租户是否正在运行
     */
    public static boolean isRunning(String tenantId) {
        return runningTenants.containsKey(tenantId);
    }

    /**
     * 获取租户所在的任务 ID
     */
    public static String getRunningTaskId(String tenantId) {
        return runningTenants.get(tenantId);
    }

    /**
     * 清空所有运行中的租户（用于测试或重置）
     */
    public static void clearAll() {
        runningTenants.clear();
    }

    @Override
    public String getValidatorName() {
        return "ConflictValidator";
    }

    @Override
    public int getOrder() {
        return 20;
    }
}

