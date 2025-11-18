package xyz.firestige.executor.state.strategy;

import xyz.firestige.executor.state.TaskStatus;

import java.util.Objects;

/**
 * 状态转换键（用于策略映射）
 *
 * @since Phase 18 - RF-13
 */
public class StateTransitionKey {

    private final TaskStatus fromStatus;
    private final TaskStatus toStatus;

    public StateTransitionKey(TaskStatus fromStatus, TaskStatus toStatus) {
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }

    public TaskStatus getFromStatus() {
        return fromStatus;
    }

    public TaskStatus getToStatus() {
        return toStatus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StateTransitionKey that = (StateTransitionKey) o;
        return fromStatus == that.fromStatus && toStatus == that.toStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromStatus, toStatus);
    }

    @Override
    public String toString() {
        return fromStatus + " -> " + toStatus;
    }
}
