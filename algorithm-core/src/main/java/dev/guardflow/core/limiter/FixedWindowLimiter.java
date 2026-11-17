package dev.guardflow.core.limiter;

import dev.guardflow.core.Decision;
import dev.guardflow.core.Limiter;
import dev.guardflow.core.RejectReason;
import dev.guardflow.core.Snapshot;

/**
 * 固定窗口限流器。窗口为左闭右开 [windowStart, windowStart + windowMillis)，
 * 窗口切换时计数归零；窗口边界处的 2×limit 突发是刻意保留的演示缺陷。
 */
public final class FixedWindowLimiter implements Limiter {

    private final int limit;
    private final long windowMillis;

    private long windowStart;
    private int count;

    public FixedWindowLimiter(int limit, long windowMillis) {
        if (limit < 1 || limit > 1_000_000) {
            throw new IllegalArgumentException("limit must be in [1, 1000000]");
        }
        if (windowMillis < 10 || windowMillis > 86_400_000L) {
            throw new IllegalArgumentException("windowMillis must be in [10, 86400000]");
        }
        this.limit = limit;
        this.windowMillis = windowMillis;
    }

    /**
     * permits 个请求按顺序逐个判定，返回最后一个请求的决策；permits ≤ 0 返回 INVALID_PARAM。
     */
    @Override
    public Decision tryAcquire(long nowMillis, int permits) {
        if (permits <= 0) {
            // 非法许可数不改变窗口状态，只返回拒绝原因。
            return new Decision(false, RejectReason.INVALID_PARAM, snapshot(nowMillis).level());
        }
        Decision last = null;
        for (int i = 0; i < permits; i++) {
            last = acquireOne(nowMillis);
        }
        return last;
    }

    @Override
    public Snapshot snapshot(long nowMillis) {
        long currentWindowStart = windowStartOf(nowMillis);
        if (currentWindowStart != windowStart) {
            return new Snapshot(nowMillis, 0.0, limit, 0);
        }
        return new Snapshot(nowMillis, (double) count / limit, limit, count);
    }

    private Decision acquireOne(long nowMillis) {
        long currentWindowStart = windowStartOf(nowMillis);
        if (currentWindowStart != windowStart) {
            // 进入新窗口时清零，旧窗口的计数不再影响判定。
            windowStart = currentWindowStart;
            count = 0;
        }
        if (count < limit) {
            count++;
            return new Decision(true, RejectReason.PASSED, (double) count / limit);
        }
        return new Decision(false, RejectReason.WINDOW_LIMIT, 1.0);
    }

    private long windowStartOf(long nowMillis) {
        return Math.floorDiv(nowMillis, windowMillis) * windowMillis;
    }
}
