package xyz.firestige.redis.renewal.strategy.stop;

import xyz.firestige.redis.renewal.RenewalContext;

import java.util.Arrays;
import java.util.List;

/**
 * 组合停止策略，实现可以将多个停止条件组合在一起
 */
public class CompositeStopStrategy implements StopStrategy {
    public enum Mode { ALL, ANY }
    private final List<StopStrategy> conditions;
    private final Mode mode;
    private CompositeStopStrategy(Mode mode, List<StopStrategy> conditions) {
        this.mode = mode;
        this.conditions = conditions;
    }

    public static CompositeStopStrategy allOf(StopStrategy... c){
        return new CompositeStopStrategy(Mode.ALL, Arrays.asList(c));
    }

    public static CompositeStopStrategy anyOf(StopStrategy... c){
        return new CompositeStopStrategy(Mode.ANY, Arrays.asList(c));
    }

    @Override
    public boolean shouldStop(RenewalContext context) {
        return switch (mode){
            case ALL -> conditions.stream().allMatch(c->c.shouldStop(context));
            case ANY -> conditions.stream().anyMatch(c->c.shouldStop(context));
        };
    }

    @Override
    public String getName() {
        return "CompositeStop";
    }
}

