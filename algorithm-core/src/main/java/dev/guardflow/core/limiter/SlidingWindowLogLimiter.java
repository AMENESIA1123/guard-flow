package dev.guardflow.core.limiter;

import dev.guardflow.core.Decision;
import dev.guardflow.core.Limiter;
import dev.guardflow.core.RejectReason;
import dev.guardflow.core.Snapshot;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 滑动窗口（日志法）。窗口为左开右闭 (t - windowMillis, t]，
 * 日志只保留窗口内的放行时间戳，因此任意窗口内的放行数严格不超过 limit。
 */
public final class SlidingWindowLogLimiter implements Limiter {

    private final int limit;
    private final long windowMillis;
    private final Deque<Long> log = new ArrayDeque<>();

    public SlidingWindowLogLimiter(int limit, long windowMillis) {
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
            // 非法许可数不改变日志，只返回拒绝原因。
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
        long cutoff = nowMillis - windowMillis;
        int used = 0;
        for (long timestamp : log) {
            if (timestamp > cutoff && timestamp <= nowMillis) {
                used++;
            }
        }
        return new Snapshot(nowMillis, (double) used / limit, limit, used);
    }

    private Decision acquireOne(long nowMillis) {
        long cutoff = nowMillis - windowMillis;
        // 左开：时间戳恰好等于 cutoff 的记录已经过期，必须先从队头移除。
        while (!log.isEmpty() && log.peekFirst() <= cutoff) {
            log.pollFirst();
        }
        if (log.size() < limit) {
            log.addLast(nowMillis);
            return new Decision(true, RejectReason.PASSED, (double) log.size() / limit);
        }
        return new Decision(false, RejectReason.WINDOW_LIMIT, 1.0);
    }
}
