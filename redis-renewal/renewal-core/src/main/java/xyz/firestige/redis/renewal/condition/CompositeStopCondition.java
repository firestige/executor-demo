package xyz.firestige.infrastructure.redis.renewal.condition;

import xyz.firestige.infrastructure.redis.renewal.api.RenewalContext;
import xyz.firestige.infrastructure.redis.renewal.api.StopCondition;

import java.util.Arrays;
import java.util.List;

public class CompositeStopCondition implements StopCondition {
    public enum Mode { ALL, ANY }
    private final List<StopCondition> conditions;
    private final Mode mode;
    private CompositeStopCondition(Mode mode, List<StopCondition> conditions) { this.mode = mode; this.conditions = conditions; }
    public static CompositeStopCondition allOf(StopCondition... c){ return new CompositeStopCondition(Mode.ALL, Arrays.asList(c)); }
    public static CompositeStopCondition anyOf(StopCondition... c){ return new CompositeStopCondition(Mode.ANY, Arrays.asList(c)); }
    @Override public boolean shouldStop(RenewalContext context) {
        return switch (mode){
            case ALL -> conditions.stream().allMatch(c->c.shouldStop(context));
            case ANY -> conditions.stream().anyMatch(c->c.shouldStop(context));
        };
    }
    @Override public String getName() { return "CompositeStop"; }
}

