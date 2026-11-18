package dev.guardflow.core.limiter;

import dev.guardflow.core.Decision;
import dev.guardflow.core.RejectReason;
import dev.guardflow.core.Snapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SlidingWindowLogLimiterTest {

    private static final int LIMIT = 10;
    private static final long WINDOW_MILLIS = 1000L;

    @Test
    void sw01_tenRequestsAtZero_allPass() {
        // SW-01：t=0 连续 10 个请求全部放行，log.size()=10。
        SlidingWindowLogLimiter limiter = new SlidingWindowLogLimiter(LIMIT, WINDOW_MILLIS);

        for (int i = 1; i <= LIMIT; i++) {
            Decision decision = limiter.tryAcquire(0L, 1);
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.reason()).isEqualTo(RejectReason.PASSED);
            assertThat(decision.level()).isCloseTo(i / (double) LIMIT, within(1e-9));
        }

        Snapshot snapshot = limiter.snapshot(0L);
        assertThat(snapshot.t()).isEqualTo(0L);
        assertThat(snapshot.capacity()).isEqualTo(10.0);
        assertThat(snapshot.used()).isEqualTo(10.0);
        assertThat(snapshot.level()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void sw02_eleventhRequestAtZero_rejected() {
        // SW-02：t=0 满 10 个后再来 1 个，第 11 个被拒绝。
        SlidingWindowLogLimiter limiter = new SlidingWindowLogLimiter(LIMIT, WINDOW_MILLIS);
        for (int i = 0; i < LIMIT; i++) {
            assertThat(limiter.tryAcquire(0L, 1).allowed()).isTrue();
        }

        Decision rejected = limiter.tryAcquire(0L, 1);

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.reason()).isEqualTo(RejectReason.WINDOW_LIMIT);
        assertThat(rejected.level()).isCloseTo(1.0, within(1e-9));
        assertThat(limiter.snapshot(0L).used()).isEqualTo(10.0);
    }

    @Test
    void sw03_requestAt999_rejectedBecauseNotExpired() {
        // SW-03：t=999 时 0 > 999-1000=-1，t=0 的记录仍在窗口内，第 11 个被拒绝。
        SlidingWindowLogLimiter limiter = new SlidingWindowLogLimiter(LIMIT, WINDOW_MILLIS);
        for (int i = 0; i < LIMIT; i++) {
            assertThat(limiter.tryAcquire(0L, 1).allowed()).isTrue();
        }

        Decision rejected = limiter.tryAcquire(999L, 1);

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.reason()).isEqualTo(RejectReason.WINDOW_LIMIT);
        assertThat(limiter.snapshot(999L).used()).isEqualTo(10.0);
    }

    @Test
    void sw04_requestAt1000_passesAfterOldEntriesExpire() {
        // SW-04：t=1000 时 0 <= 1000-1000=0，t=0 的记录恰好过期，新请求放行。
        SlidingWindowLogLimiter limiter = new SlidingWindowLogLimiter(LIMIT, WINDOW_MILLIS);
        for (int i = 0; i < LIMIT; i++) {
            assertThat(limiter.tryAcquire(0L, 1).allowed()).isTrue();
        }

        Decision decision = limiter.tryAcquire(1000L, 1);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).isEqualTo(RejectReason.PASSED);
        assertThat(decision.level()).isCloseTo(0.1, within(1e-9));
        Snapshot snapshot = limiter.snapshot(1000L);
        assertThat(snapshot.used()).isEqualTo(1.0);
        assertThat(snapshot.level()).isCloseTo(0.1, within(1e-9));
    }

    @Test
    void sw05_requestsAt500Then1000_rejected() {
        // SW-05：t=1000 时 500 > 0，t=500 的 10 条记录仍未过期，新请求被拒绝。
        SlidingWindowLogLimiter limiter = new SlidingWindowLogLimiter(LIMIT, WINDOW_MILLIS);
        for (int i = 0; i < LIMIT; i++) {
            assertThat(limiter.tryAcquire(500L, 1).allowed()).isTrue();
        }

        Decision rejected = limiter.tryAcquire(1000L, 1);

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.reason()).isEqualTo(RejectReason.WINDOW_LIMIT);
        assertThat(rejected.level()).isCloseTo(1.0, within(1e-9));
        assertThat(limiter.snapshot(1000L).used()).isEqualTo(10.0);
    }

    @Test
    void sw06_uniformTraffic_allPass() {
        // SW-06：t=0,100,...,1900 各 1 个，共 20 个全部放行。
        SlidingWindowLogLimiter limiter = new SlidingWindowLogLimiter(LIMIT, WINDOW_MILLIS);

        int passed = 0;
        for (int i = 0; i < 20; i++) {
            Decision decision = limiter.tryAcquire(i * 100L, 1);
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.reason()).isEqualTo(RejectReason.PASSED);
            passed++;
        }

        assertThat(passed).isEqualTo(20);
        Snapshot snapshot = limiter.snapshot(1900L);
        assertThat(snapshot.used()).isEqualTo(10.0);
        assertThat(snapshot.level()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void sw07_burstAt999_allRejected() {
        // SW-07：t=0 满 10 个后，t=999 再来 5 个，全部被拒绝。
        SlidingWindowLogLimiter limiter = new SlidingWindowLogLimiter(LIMIT, WINDOW_MILLIS);
        for (int i = 0; i < LIMIT; i++) {
            assertThat(limiter.tryAcquire(0L, 1).allowed()).isTrue();
        }

        for (int i = 0; i < 5; i++) {
            Decision rejected = limiter.tryAcquire(999L, 1);
            assertThat(rejected.allowed()).isFalse();
            assertThat(rejected.reason()).isEqualTo(RejectReason.WINDOW_LIMIT);
        }

        assertThat(limiter.snapshot(999L).used()).isEqualTo(10.0);
    }
}
