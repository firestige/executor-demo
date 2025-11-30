package xyz.firestige.deploy.domain.task;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Task 检查点（领域值对象）
 * <p>
 * 用于保存 Task 执行的中间状态，支持故障恢复和重试。
 * <p>
 * 核心功能：
 * <ul>
 *   <li>记录最后完成的 Stage 索引</li>
 *   <li>记录已完成的 Stage 名称列表</li>
 *   <li>支持自定义数据存储（扩展字段）</li>
 *   <li>用于 Task 重试时从检查点恢复</li>
 * </ul>
 * <p>
 * 使用场景：
 * <ul>
 *   <li>Task 执行过程中，每完成一个 Stage 保存检查点</li>
 *   <li>Task 失败后重试，从检查点恢复继续执行</li>
 *   <li>Task 暂停后恢复，从检查点继续执行</li>
 * </ul>
 * <p>
 * 设计说明：
 * <ul>
 *   <li>线程安全：customData 使用 ConcurrentHashMap</li>
 *   <li>不可变性：completedStageNames 返回不可变列表</li>
 *   <li>轻量级：只记录索引和名称，不保存 Stage 输出数据</li>
 * </ul>
 *
 * @since T-032 状态机重构
 */
public class TaskCheckpoint {

    /**
     * 最后完成的 Stage 索引（0-based）
     * <p>
     * 示例：如果完成了 Stage 0, 1, 2，则此值为 2
     * 重试时从索引 3 开始执行
     */
    private int lastCompletedStageIndex;

    /**
     * 已完成的 Stage 名称列表
     * <p>
     * 按完成顺序记录，用于验证和日志
     */
    private final List<String> completedStageNames = new ArrayList<>();

    /**
     * 所有 Stage 名称列表（完整列表）
     * <p>
     * 用于重建 StageProgress，支持重启后恢复
     *
     * @since T-033 状态机简化
     */
    private final List<String> allStageNames = new ArrayList<>();

    /**
     * 自定义数据（扩展字段）
     * <p>
     * 用于存储额外的检查点信息，例如：
     * - Stage 的执行耗时
     * - Stage 的输出摘要
     * - 重试次数
     * <p>
     * Key: 数据键名, Value: 数据值
     * 线程安全：使用 ConcurrentHashMap
     */
    private final Map<String, Object> customData = new ConcurrentHashMap<>();

    /**
     * 检查点创建时间
     */
    private LocalDateTime timestamp = LocalDateTime.now();

    // ========== 构造函数 ==========

    /**
     * 默认构造函数
     */
    public TaskCheckpoint() {
        // 空构造函数，字段已初始化
    }

    /**
     * 带参数的构造函数
     *
     * @param lastCompletedStageIndex 最后完成的 Stage 索引
     * @param completedStageNames 已完成的 Stage 名称列表
     * @param allStageNames 所有 Stage 名称列表（用于重建 StageProgress）
     */
    public TaskCheckpoint(int lastCompletedStageIndex, List<String> completedStageNames, List<String> allStageNames) {
        this.lastCompletedStageIndex = lastCompletedStageIndex;
        if (completedStageNames != null) {
            this.completedStageNames.addAll(completedStageNames);
        }
        if (allStageNames != null) {
            this.allStageNames.addAll(allStageNames);
        }
        this.timestamp = LocalDateTime.now();
    }

    /**
     * 带参数的构造函数（兼容旧版本）
     *
     * @param lastCompletedStageIndex 最后完成的 Stage 索引
     * @param completedStageNames 已完成的 Stage 名称列表
     * @deprecated 使用 {@link #TaskCheckpoint(int, List, List)} 代替
     */
    @Deprecated
    public TaskCheckpoint(int lastCompletedStageIndex, List<String> completedStageNames) {
        this(lastCompletedStageIndex, completedStageNames, null);
    }

    // ========== 业务方法 ==========

    /**
     * 添加已完成的 Stage
     *
     * @param stageName Stage 名称
     */
    public void addCompletedStage(String stageName) {
        if (stageName != null && !completedStageNames.contains(stageName)) {
            completedStageNames.add(stageName);
        }
    }

    /**
     * 是否包含指定 Stage
     *
     * @param stageName Stage 名称
     * @return 如果已完成返回 true
     */
    public boolean hasCompletedStage(String stageName) {
        return completedStageNames.contains(stageName);
    }

    /**
     * 获取已完成的 Stage 数量
     *
     * @return Stage 数量
     */
    public int getCompletedStageCount() {
        return completedStageNames.size();
    }

    /**
     * 保存自定义数据
     *
     * @param key 数据键
     * @param value 数据值
     */
    public void putCustomData(String key, Object value) {
        if (key != null) {
            customData.put(key, value);
        }
    }

    /**
     * 获取自定义数据
     *
     * @param key 数据键
     * @return 数据值，不存在返回 null
     */
    public Object getCustomData(String key) {
        return customData.get(key);
    }

    /**
     * 是否包含自定义数据
     *
     * @param key 数据键
     * @return 如果包含返回 true
     */
    public boolean hasCustomData(String key) {
        return customData.containsKey(key);
    }

    // ========== Getters and Setters ==========

    /**
     * 获取最后完成的 Stage 索引
     *
     * @return Stage 索引（0-based）
     */
    public int getLastCompletedStageIndex() {
        return lastCompletedStageIndex;
    }

    /**
     * 设置最后完成的 Stage 索引
     *
     * @param lastCompletedStageIndex Stage 索引
     */
    public void setLastCompletedStageIndex(int lastCompletedStageIndex) {
        this.lastCompletedStageIndex = lastCompletedStageIndex;
    }

    /**
     * 获取已完成的 Stage 名称列表（不可变）
     *
     * @return Stage 名称列表
     */
    public List<String> getCompletedStageNames() {
        return Collections.unmodifiableList(completedStageNames);
    }

    /**
     * 获取所有 Stage 名称列表（不可变）
     * <p>
     * 用于重建 StageProgress
     *
     * @return 所有 Stage 名称列表
     * @since T-033 状态机简化
     */
    public List<String> getAllStageNames() {
        return Collections.unmodifiableList(allStageNames);
    }

    /**
     * 获取自定义数据 Map（不可变）
     *
     * @return 自定义数据 Map
     */
    public Map<String, Object> getCustomData() {
        return Collections.unmodifiableMap(customData);
    }

    /**
     * 获取检查点创建时间
     *
     * @return 创建时间
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    /**
     * 设置检查点创建时间
     *
     * @param timestamp 创建时间
     */
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    // ========== Object 方法 ==========

    @Override
    public String toString() {
        return "TaskCheckpoint{" +
                "lastCompletedStageIndex=" + lastCompletedStageIndex +
                ", completedStages=" + completedStageNames +
                ", customDataSize=" + customData.size() +
                ", timestamp=" + timestamp +
                '}';
    }
}

