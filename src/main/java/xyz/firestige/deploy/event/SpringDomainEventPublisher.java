package xyz.firestige.deploy.event;

import org.springframework.context.ApplicationEventPublisher;

/**
 * Spring 本地事件总线实现（单机部署）
 *
 * 适用场景：
 * - 单机部署
 * - 进程内事件传递
 * - 开发/测试环境
 *
 * 特点：
 * - 同步/异步发布（取决于 @EventListener 配置）
 * - 零延迟，高性能
 * - 无需外部中间件
 *
 * @since RF-11 改进版
 */
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public SpringDomainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(Object event) {
        applicationEventPublisher.publishEvent(event);
    }
}

