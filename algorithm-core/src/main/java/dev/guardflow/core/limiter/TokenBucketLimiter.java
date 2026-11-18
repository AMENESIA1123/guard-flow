package dev.guardflow.core.limiter;

import dev.guardflow.core.Decision;
import dev.guardflow.core.Limiter;
import dev.guardflow.core.RejectReason;
import dev.guardflow.core.Snapshot;

/**
 * 令牌桶。tokens 为剩余令牌数，level = tokens / capacity；
 * 补充是惰性的：只有请求到达时才按经过时间结算，空闲期间不推进状态。
 */
public final class TokenBucketLimiter implements Limiter {

    private final double capacity;
    private final double ratePerSecond;

    private double tokens;
    private long lastRefillMillis;

    public TokenBucketLimiter(double capacity, double ratePerSecond) {
        if (!Double.isFinite(capacity) || capacity < 1.0 || capacity > 1_000_000.0) {
            throw new IllegalArgumentException("capacity must be in [1, 1000000]");
        }
        if (!Double.isFinite(ratePerSecond) || ratePerSecond < 0.001 || ratePerSecond > 1_000_000.0) {
            throw new IllegalArgumentException("ratePerSecond must be in [0.001, 1000000]");
        }
        this.capacity = capacity;
        this.ratePerSecond = ratePerSecond;
        this.tokens = capacity;
        this.lastRefillMillis = 0L;
    }

    /**
     * permits 个请求按顺序逐个判定，返回最后一个请求的决策；permits ≤ 0 返回 INVALID_PARAM。
     */
    @Override
    public Decision tryAcquire(long nowMillis, int permits) {
        if (permits <= 0) {
            return new Decision(false, RejectReason.INVALID_PARAM, tokensAt(nowMillis) / capacity);
        }
        Decision last = null;
        for (int i = 0; i < permits; i++) {
            refill(nowMillis);
            if (tokens >= 1.0) {
                tokens -= 1.0;
                last = new Decision(true, RejectReason.PASSED, tokens / capacity);
            } else {
                last = new Decision(false, RejectReason.NO_TOKEN, tokens / capacity);
            }
        }
        return last;
    }

    @Override
    public Snapshot snapshot(long nowMillis) {
        double projected = tokensAt(nowMillis);
        return new Snapshot(nowMillis, projected / capacity, capacity, projected);
    }

    private void refill(long nowMillis) {
        long elapsed = nowMillis - lastRefillMillis;
        if (elapsed > 0) {
            // elapsed 为 0 时不更新 lastRefillMillis，避免同一时间戳多次调用丢失小数精度。
            tokens = Math.min(capacity, tokens + elapsed * ratePerSecond / 1000.0);
            lastRefillMillis = nowMillis;
        }
    }

    private double tokensAt(long nowMillis) {
        long elapsed = nowMillis - lastRefillMillis;
        if (elapsed <= 0) {
            return tokens;
        }
        return Math.min(capacity, tokens + elapsed * ratePerSecond / 1000.0);
    }
}
