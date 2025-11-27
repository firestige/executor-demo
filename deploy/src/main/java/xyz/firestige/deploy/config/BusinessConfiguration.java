package xyz.firestige.deploy.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import xyz.firestige.deploy.config.properties.DomainEventPublisherProperties;
import xyz.firestige.deploy.domain.shared.event.DomainEventPublisher;
import xyz.firestige.deploy.infrastructure.event.SpringDomainEventPublisher;

@Configuration
@EnableConfigurationProperties(DomainEventPublisherProperties.class)
public class BusinessConfiguration {
    /**
     * Spring 本地事件发布器（默认配置）
     */
    @Bean
    @ConditionalOnMissingBean(DomainEventPublisher.class)
    @ConditionalOnProperty(name = "executor.event.publisher.type", havingValue = "spring", matchIfMissing = true)
    public DomainEventPublisher domainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        return new SpringDomainEventPublisher(applicationEventPublisher);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
