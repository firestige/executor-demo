package xyz.firestige.deploy.domain.shared.event;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

public abstract class DomainEvent {
    private final String eventId;
    private final LocalDateTime timestamp;
    private String message;

    public DomainEvent() {
        this(UUID.randomUUID().toString(), LocalDateTime.now());
    }

    public DomainEvent(String eventId, LocalDateTime timestamp) {
        this(eventId, timestamp, "");
    }

    public DomainEvent(String eventId, LocalDateTime timestamp, String message) {
        this.eventId = eventId;
        this.timestamp = timestamp;
        this.message = message;
    }

    public String getEventName() {
        return this.getClass().getSimpleName();
    }

    public String getEventId() {
        return eventId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getFormattedTimestamp() {
        return getFormattedTimestamp(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public String getFormattedTimestamp(@NotNull DateTimeFormatter formatter) {
        Objects.requireNonNull(formatter, "formatter must not be null");
        return timestamp.format(formatter);
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
