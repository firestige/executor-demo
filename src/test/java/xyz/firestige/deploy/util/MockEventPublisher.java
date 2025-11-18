package xyz.firestige.deploy.util;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Mock 事件发布器
 * 用于测试时记录发布的事件
 */
public class MockEventPublisher implements ApplicationEventPublisher {

    private final List<Object> publishedEvents = new ArrayList<>();

    @Override
    public void publishEvent(Object event) {
        publishedEvents.add(event);
    }

    @Override
    public void publishEvent(ApplicationEvent event) {
        publishedEvents.add(event);
    }

    /**
     * 获取所有发布的事件
     */
    public List<Object> getPublishedEvents() {
        return new ArrayList<>(publishedEvents);
    }

    /**
     * 获取指定类型的事件
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getEventsOfType(Class<T> eventType) {
        return publishedEvents.stream()
                .filter(eventType::isInstance)
                .map(event -> (T) event)
                .collect(Collectors.toList());
    }

    /**
     * 检查是否发布了指定类型的事件
     */
    public <T> boolean hasEvent(Class<T> eventType) {
        return publishedEvents.stream()
                .anyMatch(eventType::isInstance);
    }

    /**
     * 获取事件数量
     */
    public int getEventCount() {
        return publishedEvents.size();
    }

    /**
     * 获取指定类型事件的数量
     */
    public <T> int getEventCount(Class<T> eventType) {
        return (int) publishedEvents.stream()
                .filter(eventType::isInstance)
                .count();
    }

    /**
     * 清空所有事件
     */
    public void clear() {
        publishedEvents.clear();
    }

    /**
     * 获取第一个指定类型的事件
     */
    public <T> T getFirstEvent(Class<T> eventType) {
        return getEventsOfType(eventType).stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取最后一个指定类型的事件
     */
    public <T> T getLastEvent(Class<T> eventType) {
        List<T> events = getEventsOfType(eventType);
        return events.isEmpty() ? null : events.get(events.size() - 1);
    }
}

