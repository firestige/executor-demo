package xyz.firestige.deploy.testutil.factory;

import xyz.firestige.deploy.application.dto.TenantConfig;

public final class AggregationFactory {
    public static PlanAggregateTestBuilder buildPlanAggregationFrom(TenantConfig config) {
        return new PlanAggregateTestBuilder(config);
    }

    public static TaskAggregateTestBuilder buildTaskAggregationFrom(TenantConfig config) {
        return new TaskAggregateTestBuilder();
    }
}
