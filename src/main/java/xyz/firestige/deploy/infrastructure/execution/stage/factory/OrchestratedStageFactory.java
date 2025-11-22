package xyz.firestige.deploy.infrastructure.execution.stage.factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import xyz.firestige.deploy.application.dto.TenantConfig;
import xyz.firestige.deploy.infrastructure.config.DeploymentConfigLoader;
import xyz.firestige.deploy.infrastructure.execution.stage.StageFactory;
import xyz.firestige.deploy.infrastructure.execution.stage.TaskStage;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 编排式 Stage 工厂（策略化重构）
 * 职责：
 * - 自动注入所有 StageAssembler 实现
 * - 按顺序排序（@Order 注解优先，无注解则从 defaultServiceNames 推断）
 * - 过滤条件（supports）
 * - 执行构建（buildStage）
 *
 * @since RF-19-06 策略化重构
 */
@Component
// @Primary 将在对比验证通过后启用
public class OrchestratedStageFactory implements StageFactory {

    private static final Logger log = LoggerFactory.getLogger(OrchestratedStageFactory.class);

    private final List<StageAssembler> sortedAssemblers;
    private final SharedStageResources resources;
    private final DeploymentConfigLoader configLoader;

    @Autowired
    public OrchestratedStageFactory(
            List<StageAssembler> assemblers,
            SharedStageResources resources,
            DeploymentConfigLoader configLoader) {
        this.resources = resources;
        this.configLoader = configLoader;

        // 启动时计算并缓存排序后的策略列表
        this.sortedAssemblers = sortAndCache(assemblers);

        // 启动日志
        logAssemblerInfo();
    }

    @Override
    public List<TaskStage> buildStages(TenantConfig cfg) {
        log.info("Building stages for tenant: {}", cfg.getTenantId());

        List<TaskStage> stages = sortedAssemblers.stream()
            .filter(a -> a.supports(cfg))
            .map(a -> {
                log.debug("Building stage: {}", a.stageName());
                return a.buildStage(cfg, resources);
            })
            .collect(Collectors.toList());

        log.info("Built {} stages", stages.size());
        return stages;
    }

    /**
     * 排序并缓存策略列表
     */
    private List<StageAssembler> sortAndCache(List<StageAssembler> assemblers) {
        // 1. 加载配置中的默认顺序
        List<String> defaultServiceNames = configLoader.getDefaultServiceNames();
        Map<String, Integer> defaultOrderMap = new HashMap<>();
        for (int i = 0; i < defaultServiceNames.size(); i++) {
            defaultOrderMap.put(defaultServiceNames.get(i), i * 10);
        }

        // 2. 为每个 assembler 计算最终 order
        List<AssemblerWithOrder> withOrders = new ArrayList<>();
        for (StageAssembler assembler : assemblers) {
            int finalOrder = computeOrder(assembler, defaultOrderMap);
            withOrders.add(new AssemblerWithOrder(assembler, finalOrder));
        }

        // 3. 按 order 排序
        withOrders.sort(Comparator.comparingInt(AssemblerWithOrder::order));

        // 4. 提取排序后的 assembler 列表
        return withOrders.stream()
            .map(AssemblerWithOrder::assembler)
            .collect(Collectors.toList());
    }

    /**
     * 计算单个 assembler 的 order
     */
    private int computeOrder(StageAssembler assembler, Map<String, Integer> defaultOrderMap) {
        // 策略 1: 优先使用 @Order 注解
        Order orderAnnotation = assembler.getClass().getAnnotation(Order.class);
        if (orderAnnotation != null) {
            return orderAnnotation.value();
        }

        // 策略 2: 从 defaultServiceNames 推断（stageName → index * 10）
        String stageName = assembler.stageName();
        Integer configOrder = defaultOrderMap.get(stageName);
        if (configOrder != null) {
            return configOrder;
        }

        // 策略 3: 都无则置为最后
        return Integer.MAX_VALUE;
    }

    /**
     * 打印策略清单与顺序
     */
    private void logAssemblerInfo() {
        log.info("Loaded {} StageAssemblers:", sortedAssemblers.size());

        // 重新计算 orderMap 用于日志（避免重复逻辑）
        List<String> defaultServiceNames = configLoader.getDefaultServiceNames();
        Map<String, Integer> defaultOrderMap = new HashMap<>();
        for (int i = 0; i < defaultServiceNames.size(); i++) {
            defaultOrderMap.put(defaultServiceNames.get(i), i * 10);
        }

        for (int i = 0; i < sortedAssemblers.size(); i++) {
            StageAssembler a = sortedAssemblers.get(i);
            int order = computeOrder(a, defaultOrderMap);

            // 判断顺序来源
            Order orderAnnotation = a.getClass().getAnnotation(Order.class);
            String source = orderAnnotation != null ? "@Order" : "config";

            log.info("  [{}] {} (order={}, source={})",
                i + 1, a.stageName(), order, source);
        }
    }

    /**
     * 内部辅助类：携带 order 的 assembler
     */
    private record AssemblerWithOrder(StageAssembler assembler, int order) {}
}

