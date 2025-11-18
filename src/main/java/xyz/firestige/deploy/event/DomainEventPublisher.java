package xyz.firestige.deploy.event;

import java.util.List;

/**
 * 领域事件发布器接口（DDD 标准接口）
 *
 * 职责：
 * - 定义领域事件发布的标准契约
 * - 解耦领域层与具体的事件传输机制
 * - 支持多种实现：本地事件总线、Kafka、RocketMQ 等
 *
 * 设计原则：
 * - 依赖倒置原则（DIP）：领域层依赖抽象接口，而非具体实现
 * - 开闭原则（OCP）：易于扩展新的事件发布机制
 * - 接口隔离原则（ISP）：简单清晰的接口定义
 *
 * @since RF-11 改进版
 */
public interface DomainEventPublisher {

    /**
     * 发布单个领域事件
     *
     * @param event 领域事件对象
     */
    void publish(Object event);

    /**
     * 批量发布领域事件
     *
     * @param events 领域事件列表
     */
    default void publishAll(List<?> events) {
        if (events != null) {
            events.forEach(this::publish);
        }
    }
}

