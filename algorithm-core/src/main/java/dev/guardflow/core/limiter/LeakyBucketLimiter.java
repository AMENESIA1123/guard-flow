package dev.guardflow.core.limiter;

import dev.guardflow.core.Decision;
import dev.guardflow.core.Limiter;
import dev.guardflow.core.RejectReason;
import dev.guardflow.core.Snapshot;

/**
 * 漏桶（队列计数模型）。出队发生在 t = k * intervalMillis 的整点，且在判定前结算；
 * 不模拟真实等待，只维护队列长度。
 */
public final class LeakyBucketLimiter implements Limiter {

    private final int capacity;
    private final double intervalMillis;

    private int queued;
    private long lastDrainMillis;

    public LeakyBucketLimiter(int capacity, double ratePerSecond) {
        if (capacity < 1 || capacity > 1_000_000) {
            throw new IllegalArgumentException("capacity must be in [1, 1000000]");
        }
        if (!Double.isFinite(ratePerSecond) || ratePerSecond < 0.001 || ratePerSecond > 1_000_000.0) {
            throw new IllegalArgumentException("ratePerSecond must be in [0.001, 1000000]");
        }
        this.capacity = capacity;
        this.intervalMillis = 1000.0 / ratePerSecond;
    }

    /**
     * permits 个请求按顺序逐个判定，返回最后一个请求的决策；permits ≤ 0 返回 INVALID_PARAM。
     */
    @Override
    public Decision tryAcquire(long nowMillis, int permits) {
        if (permits <= 0) {
            return new Decision(false, RejectReason.INVALID_PARAM, snapshot(nowMillis).level());
        }
        Decision last = null;
        for (int i = 0; i < permits; i++) {
            drain(nowMillis);
            if (queued < capacity) {
                queued++;
                last = new Decision(true, RejectReason.PASSED, (double) queued / capacity);
            } else {
                last = new Decision(false, RejectReason.QUEUE_FULL, 1.0);
            }
        }
        return last;
    }

    @Override
    public Snapshot snapshot(long nowMillis) {
        int projected = queuedAt(nowMillis);
        return new Snapshot(nowMillis, (double) projected / capacity, capacity, projected);
    }

    private void drain(long nowMillis) {
        if (nowMillis > lastDrainMillis) {
            queued = queuedAt(nowMillis);
            lastDrainMillis = nowMillis;
        }
    }

    private int queuedAt(long nowMillis) {
        if (nowMillis <= lastDrainMillis) {
            return queued;
        }
        long currentSlot = (long) Math.floor(nowMillis / intervalMillis);
        long lastSlot = (long) Math.floor(lastDrainMillis / intervalMillis);
        long drained = currentSlot - lastSlot;
        return (int) Math.max(0L, queued - drained);
    }
}
