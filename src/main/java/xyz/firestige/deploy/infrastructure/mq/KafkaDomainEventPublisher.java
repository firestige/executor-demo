package xyz.firestige.deploy.infrastructure.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.firestige.deploy.event.DomainEventPublisher;

public class KafkaDomainEventPublisher implements DomainEventPublisher {
    public KafkaDomainEventPublisher(Object kafkaTemplate, ObjectMapper objectMapper, String topicPrefix) {
    }

    @Override
    public void publish(Object event) {

    }
}
