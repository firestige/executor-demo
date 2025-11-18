package xyz.firestige.deploy.infrastructure.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.firestige.deploy.domain.shared.event.DomainEventPublisher;

import java.util.List;

public class RocketMQDomainEventPublisher implements DomainEventPublisher {
    public RocketMQDomainEventPublisher(Object rocketMQTemplate, ObjectMapper objectMapper, String topicPrefix) {
    }

    @Override
    public void publish(Object event) {

    }

    @Override
    public void publishAll(List<?> events) {
        DomainEventPublisher.super.publishAll(events);
    }
}
