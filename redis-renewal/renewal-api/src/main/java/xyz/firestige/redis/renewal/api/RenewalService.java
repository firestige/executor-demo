package xyz.firestige.redis.renewal.api;

import java.time.Duration;
import java.util.List;

/**
 * Redis Key 续期服务接口
 * <p>
 * 提供 Key TTL 自动续期能力，基于时间轮调度实现高性能批量续期。
 *
 * @author AI
 * @since 1.0
 */
public interface RenewalService {

    /**
     * 注册一个续期任务
     *
     * @param task 续期任务
     * @return 任务 ID
     */
    String register(RenewalTask task);

    /**
     * 取消一个续期任务
     *
     * @param taskId 任务 ID
     * @return 是否成功取消
     */
    boolean cancel(String taskId);

    /**
     * 暂停一个续期任务
     *
     * @param taskId 任务 ID
     * @return 是否成功暂停
     */
    boolean pause(String taskId);

    /**
     * 恢复一个暂停的续期任务
     *
     * @param taskId 任务 ID
     * @return 是否成功恢复
     */
    boolean resume(String taskId);

    /**
     * 获取任务状态
     *
     * @param taskId 任务 ID
     * @return 任务状态，不存在返回 null
     */
    RenewalTaskStatus getStatus(String taskId);

    /**
     * 获取所有活跃任务 ID
     *
     * @return 任务 ID 列表
     */
    List<String> getActiveTasks();

    /**
     * 启动续期服务
     */
    void start();

    /**
     * 停止续期服务
     */
    void stop();

    /**
     * 服务是否正在运行
     *
     * @return true 如果服务正在运行
     */
    boolean isRunning();
}

