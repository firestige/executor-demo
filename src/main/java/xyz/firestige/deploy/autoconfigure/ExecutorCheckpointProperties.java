package xyz.firestige.deploy.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "executor.checkpoint")
public class ExecutorCheckpointProperties {

    public enum StoreType { memory, redis }

    /** store type selector, default memory */
    private StoreType storeType = StoreType.memory;
    /** namespace/prefix for redis keys, default executor:ckpt: */
    private String namespace = "executor:ckpt:";
    /** TTL for redis keys */
    private Duration ttl = Duration.ofHours(24);

    public StoreType getStoreType() { return storeType; }
    public void setStoreType(StoreType storeType) { this.storeType = storeType; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public Duration getTtl() { return ttl; }
    public void setTtl(Duration ttl) { this.ttl = ttl; }
}

