package xyz.firestige.deploy.infrastructure.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import xyz.firestige.deploy.domain.shared.event.DomainEventPublisher;

import java.util.List;

/**
 * Spring 本地事件总线实现（单机部署）
 * <p>
 * 适用场景：
 * - 单机部署
 * - 进程内事件传递
 * - 开发/测试环境
 * <p>
 * 特点：
 * - 同步/异步发布（取决于 @EventListener 配置）
 * - 零延迟，高性能
 * - 无需外部中间件
 *
 */
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public SpringDomainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(Object event) {
        if (event != null) {
            applicationEventPublisher.publishEvent(event);
        }
    }

    /**
     * RF-18: 批量发布领域事件
     */
    @Override
    public void publishAll(List<?> events) {
        if (events != null && !events.isEmpty()) {
            events.forEach(this::publish);
        }
    }
}

