package xyz.firestige.deploy.infrastructure.metrics;

public class NoopMetricsRegistry implements MetricsRegistry {
    @Override public void incrementCounter(String name) {}
    @Override public void setGauge(String name, double value) {}
}

