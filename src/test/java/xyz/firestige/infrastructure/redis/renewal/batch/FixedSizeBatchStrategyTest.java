package xyz.firestige.infrastructure.redis.renewal.batch;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FixedSizeBatchStrategyTest {

    @Test
    void batch_emptyList_returnsEmpty() {
        FixedSizeBatchStrategy strategy = new FixedSizeBatchStrategy(10);
        assertTrue(strategy.batch(List.of()).isEmpty());
    }

    @Test
    void batch_lessThanSize_singleBatch() {
        FixedSizeBatchStrategy strategy = new FixedSizeBatchStrategy(10);
        List<List<String>> result = strategy.batch(List.of("k1", "k2", "k3"));

        assertEquals(1, result.size());
        assertEquals(3, result.get(0).size());
    }

    @Test
    void batch_exactSize_singleBatch() {
        FixedSizeBatchStrategy strategy = new FixedSizeBatchStrategy(3);
        List<List<String>> result = strategy.batch(List.of("k1", "k2", "k3"));

        assertEquals(1, result.size());
        assertEquals(3, result.get(0).size());
    }

    @Test
    void batch_multipleBatches() {
        FixedSizeBatchStrategy strategy = new FixedSizeBatchStrategy(2);
        List<List<String>> result = strategy.batch(List.of("k1", "k2", "k3", "k4", "k5"));

        assertEquals(3, result.size());
        assertEquals(2, result.get(0).size());
        assertEquals(2, result.get(1).size());
        assertEquals(1, result.get(2).size());
    }
}

