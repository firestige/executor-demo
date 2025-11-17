package xyz.firestige.executor.metrics;

import io.micrometer.core.instrument.MeterRegistry;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MicrometerMetricsRegistry implements MetricsRegistry {
    private final MeterRegistry registry;
    private final ConcurrentMap<String, DoubleHolder> gauges = new ConcurrentHashMap<>();

    public MicrometerMetricsRegistry(MeterRegistry registry) { this.registry = registry; }

    @Override
    public void incrementCounter(String name) { registry.counter(name).increment(); }

    @Override
    public void setGauge(String name, double value) {
        DoubleHolder holder = gauges.computeIfAbsent(name, n -> {
            DoubleHolder h = new DoubleHolder();
            registry.gauge(n, h, DoubleHolder::get);
            return h;
        });
        holder.set(value);
    }

    static class DoubleHolder {
        private volatile double v;
        double get() { return v; }
        void set(double v) { this.v = v; }
    }
}
