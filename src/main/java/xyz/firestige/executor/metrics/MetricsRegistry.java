package xyz.firestige.executor.metrics;

public interface MetricsRegistry {
    void incrementCounter(String name);
    void setGauge(String name, double value);
}

