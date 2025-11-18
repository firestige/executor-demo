package xyz.firestige.deploy.metrics;

public interface MetricsRegistry {
    void incrementCounter(String name);
    void setGauge(String name, double value);
}

