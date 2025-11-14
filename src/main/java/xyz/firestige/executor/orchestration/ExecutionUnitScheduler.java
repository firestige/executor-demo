package xyz.firestige.executor.orchestration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.firestige.dto.TenantDeployConfig;
import xyz.firestige.executor.execution.PipelineResult;
import xyz.firestige.executor.execution.TenantTaskExecutor;
import xyz.firestige.executor.execution.pipeline.Pipeline;
import xyz.firestige.executor.state.TaskStateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 执行单调度器
 * 负责调度执行单的执行，支持并发和 FIFO 两种模式
 */
public class ExecutionUnitScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionUnitScheduler.class);

    /**
     * 线程池（用于并发执行）
     */
    private final ExecutorService executorService;

    /**
     * Pipeline 工厂（用于创建 Pipeline）
     */
    private final PipelineFactory pipelineFactory;

    /**
     * 状态管理器
     */
    private final TaskStateManager stateManager;

    /**
     * 默认线程池大小
     */
    private static final int DEFAULT_POOL_SIZE = 10;

    public ExecutionUnitScheduler(PipelineFactory pipelineFactory, TaskStateManager stateManager) {
        this(DEFAULT_POOL_SIZE, pipelineFactory, stateManager);
    }

    public ExecutionUnitScheduler(int poolSize, PipelineFactory pipelineFactory, TaskStateManager stateManager) {
        this.executorService = new ThreadPoolExecutor(
                poolSize,
                poolSize * 2,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        this.pipelineFactory = pipelineFactory;
        this.stateManager = stateManager;
    }

    /**
     * 调度执行单
     */
    public Future<ExecutionUnitResult> schedule(ExecutionUnit executionUnit) {
        logger.info("调度执行单: id={}, tenantCount={}, mode={}",
                executionUnit.getId(), executionUnit.getTenantCount(), executionUnit.getExecutionMode());

        executionUnit.markAsScheduled();

        return executorService.submit(() -> {
            return executeExecutionUnit(executionUnit);
        });
    }

    /**
     * 执行执行单
     */
    private ExecutionUnitResult executeExecutionUnit(ExecutionUnit executionUnit) {
        logger.info("开始执行执行单: id={}", executionUnit.getId());

        executionUnit.markAsRunning();

        ExecutionUnitResult result = new ExecutionUnitResult(executionUnit.getId());

        try {
            if (executionUnit.getExecutionMode() == ExecutionMode.CONCURRENT) {
                // 并发执行模式
                executeConcurrent(executionUnit, result);
            } else {
                // FIFO 顺序执行模式
                executeFifo(executionUnit, result);
            }

            // 根据结果更新执行单状态
            if (result.hasFailure()) {
                executionUnit.markAsFailed();
                result.setStatus(ExecutionUnitStatus.FAILED);
            } else {
                executionUnit.markAsCompleted();
                result.setStatus(ExecutionUnitStatus.COMPLETED);
            }

            logger.info("执行单完成: id={}, 成功={}, 失败={}",
                    executionUnit.getId(), result.getSuccessCount(), result.getFailureCount());

        } catch (Exception e) {
            logger.error("执行单执行异常: id={}", executionUnit.getId(), e);
            executionUnit.markAsFailed();
            result.setStatus(ExecutionUnitStatus.FAILED);
        }

        return result;
    }

    /**
     * 并发执行模式
     */
    private void executeConcurrent(ExecutionUnit executionUnit, ExecutionUnitResult result) throws InterruptedException {
        logger.info("使用并发模式执行租户任务，数量: {}", executionUnit.getTenantCount());

        List<Future<PipelineResult>> futures = new ArrayList<>();

        // 提交所有租户任务
        for (TenantDeployConfig config : executionUnit.getTenantConfigs()) {
            Future<PipelineResult> future = executorService.submit(() -> {
                return executeTenantTask(executionUnit.getId(), config);
            });
            futures.add(future);
        }

        // 等待所有任务完成
        for (int i = 0; i < futures.size(); i++) {
            try {
                Future<PipelineResult> future = futures.get(i);
                PipelineResult pipelineResult = future.get();

                TenantDeployConfig config = executionUnit.getTenantConfigs().get(i);
                result.addTenantResult(config.getTenantId(), pipelineResult);

                if (pipelineResult.isSuccess()) {
                    result.incrementSuccess();
                } else {
                    result.incrementFailure();
                }

            } catch (ExecutionException e) {
                logger.error("租户任务执行失败", e);
                result.incrementFailure();
            }
        }
    }

    /**
     * FIFO 顺序执行模式
     */
    private void executeFifo(ExecutionUnit executionUnit, ExecutionUnitResult result) {
        logger.info("使用 FIFO 模式执行租户任务，数量: {}", executionUnit.getTenantCount());

        for (TenantDeployConfig config : executionUnit.getTenantConfigs()) {
            try {
                PipelineResult pipelineResult = executeTenantTask(executionUnit.getId(), config);

                result.addTenantResult(config.getTenantId(), pipelineResult);

                if (pipelineResult.isSuccess()) {
                    result.incrementSuccess();
                } else {
                    result.incrementFailure();
                    // FIFO 模式下，可以选择遇到失败是否继续
                    // 这里选择继续执行
                    logger.warn("租户任务失败，但继续执行下一个: tenantId={}", config.getTenantId());
                }

            } catch (Exception e) {
                logger.error("租户任务执行异常: tenantId={}", config.getTenantId(), e);
                result.incrementFailure();
            }
        }
    }

    /**
     * 执行单个租户任务
     */
    private PipelineResult executeTenantTask(String executionUnitId, TenantDeployConfig config) {
        String taskId = executionUnitId + "_" + config.getTenantId();

        logger.info("执行租户任务: taskId={}, tenantId={}", taskId, config.getTenantId());

        // 创建 Pipeline
        Pipeline pipeline = pipelineFactory.createPipeline();

        // 创建租户任务执行器
        TenantTaskExecutor executor = new TenantTaskExecutor(taskId, config, pipeline, stateManager);

        // 执行任务
        return executor.execute();
    }

    /**
     * 关闭调度器
     */
    public void shutdown() {
        logger.info("关闭执行单调度器");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Pipeline 工厂接口
     */
    public interface PipelineFactory {
        Pipeline createPipeline();
    }
}

