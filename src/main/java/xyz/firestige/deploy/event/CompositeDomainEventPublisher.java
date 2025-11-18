package xyz.firestige.deploy.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 复合领域事件发布器
 *
 * 适用场景：
 * - 同时发布到多个目标（如本地事件总线 + Kafka）
 * - 渐进式迁移（本地事件用于监控，Kafka用于跨服务通信）
 * - 双写保障（主从切换场景）
 *
 * 特点：
 * - 支持多个发布器组合
 * - 按顺序执行所有发布器
 * - 单个发布器失败不影响其他发布器
 * - 记录所有失败的发布器
 *
 * 示例配置：
 * <pre>
 * // 同时发布到本地事件总线和 Kafka
 * DomainEventPublisher composite = new CompositeDomainEventPublisher(
 *     new SpringDomainEventPublisher(applicationEventPublisher),  // 本地事件
 *     new KafkaDomainEventPublisher(kafkaTemplate, objectMapper, "executor.events")  // Kafka
 * );
 * </pre>
 *
 * @since RF-11 改进版
 */
public class CompositeDomainEventPublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(CompositeDomainEventPublisher.class);

    private final List<DomainEventPublisher> publishers;
    private final boolean failFast;

    /**
     * 构造函数（默认非快速失败模式）
     *
     * @param publishers 事件发布器列表
     */
    public CompositeDomainEventPublisher(DomainEventPublisher... publishers) {
        this(false, publishers);
    }

    /**
     * 构造函数
     *
     * @param failFast 是否快速失败（true: 任一发布器失败则立即抛出异常；false: 记录错误并继续）
     * @param publishers 事件发布器列表
     */
    public CompositeDomainEventPublisher(boolean failFast, DomainEventPublisher... publishers) {
        this.failFast = failFast;
        this.publishers = new ArrayList<>();
        if (publishers != null) {
            for (DomainEventPublisher publisher : publishers) {
                if (publisher != null) {
                    this.publishers.add(publisher);
                }
            }
        }
    }

    @Override
    public void publish(Object event) {
        if (publishers.isEmpty()) {
            log.warn("No publishers configured, event not published: {}", event.getClass().getSimpleName());
            return;
        }

        List<Exception> errors = new ArrayList<>();

        for (int i = 0; i < publishers.size(); i++) {
            DomainEventPublisher publisher = publishers.get(i);
            try {
                publisher.publish(event);
                log.trace("Event published successfully by publisher #{}: {}", i, publisher.getClass().getSimpleName());
            } catch (Exception e) {
                log.error("Publisher #{} ({}) failed to publish event: {}",
                    i, publisher.getClass().getSimpleName(), event.getClass().getSimpleName(), e);
                errors.add(e);

                if (failFast) {
                    throw new CompositePublishException(
                        String.format("Publisher #%d failed (fail-fast mode)", i), e);
                }
            }
        }

        if (!errors.isEmpty() && !failFast) {
            log.warn("Event published with {} error(s) out of {} publisher(s): {}",
                errors.size(), publishers.size(), event.getClass().getSimpleName());
        }
    }

    @Override
    public void publishAll(List<?> events) {
        if (events != null) {
            events.forEach(this::publish);
        }
    }

    /**
     * 获取已配置的发布器数量
     */
    public int getPublisherCount() {
        return publishers.size();
    }

    /**
     * 复合发布异常
     */
    public static class CompositePublishException extends RuntimeException {
        public CompositePublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

