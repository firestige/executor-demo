package xyz.firestige.redis.renewal.strategy.batch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 固定批次大小策略（默认实现）
 * <p>将 Key 按固定大小分批
 */
public class FixedSizeBatchStrategy implements BatchStrategy {
    private final int batchSize;

    public FixedSizeBatchStrategy() {
        this(100);
    }

    public FixedSizeBatchStrategy(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.batchSize = batchSize;
    }

    @Override
    public List<List<String>> batch(Collection<String> keys) {
        List<List<String>> batches = new ArrayList<>();
        if (keys == null || keys.isEmpty()) {
            return batches;
        }

        List<String> currentBatch = new ArrayList<>(batchSize);
        for (String key : keys) {
            currentBatch.add(key);
            if (currentBatch.size() >= batchSize) {
                batches.add(currentBatch);
                currentBatch = new ArrayList<>(batchSize);
            }
        }

        if (!currentBatch.isEmpty()) {
            batches.add(currentBatch);
        }

        return batches;
    }

    @Override
    public String getName() {
        return "FixedSizeBatch";
    }
}

