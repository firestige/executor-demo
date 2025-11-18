package xyz.firestige.deploy.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.firestige.deploy.execution.DefaultTaskWorkerFactory;
import xyz.firestige.deploy.execution.TaskWorkerFactory;
import xyz.firestige.deploy.metrics.MicrometerMetricsRegistry;
import xyz.firestige.deploy.metrics.NoopMetricsRegistry;

/**
 * Optional Spring configuration: if a Micrometer MeterRegistry is present,
 * wire TaskWorkerFactory with MicrometerMetricsRegistry; otherwise use Noop.
 */
@Configuration
public class ExecutorAutoConfiguration {

    @Bean
    public TaskWorkerFactory taskWorkerFactory(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        MeterRegistry mr = meterRegistryProvider.getIfAvailable();
        if (mr != null) {
            return new DefaultTaskWorkerFactory(new MicrometerMetricsRegistry(mr));
        }
        return new DefaultTaskWorkerFactory(new NoopMetricsRegistry());
    }
}

