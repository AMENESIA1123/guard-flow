package dev.guardflow.core.limiter;

import dev.guardflow.core.Decision;
import dev.guardflow.core.RejectReason;
import dev.guardflow.core.Snapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FixedWindowLimiterTest {

    private static final int LIMIT = 10;
    private static final long WINDOW_MILLIS = 1000L;

    @Test
    void fw01_tenRequestsAtZero_allPass() {
        // FW-01：t=0 连续 10 个请求全部放行，count=10。
        FixedWindowLimiter limiter = new FixedWindowLimiter(LIMIT, WINDOW_MILLIS);

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
    void fw02_eleventhRequestAtZero_rejectedWithWindowLimit() {
        // FW-02：t=0 满 10 个后再来 1 个，第 11 个被拒绝。
        FixedWindowLimiter limiter = new FixedWindowLimiter(LIMIT, WINDOW_MILLIS);
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
    void fw03_requestAt999_rejectedBecauseWindowNotReset() {
        // FW-03：t=999 仍在 [0,1000) 内，第 11 个被拒绝。
        FixedWindowLimiter limiter = new FixedWindowLimiter(LIMIT, WINDOW_MILLIS);
        for (int i = 0; i < LIMIT; i++) {
            assertThat(limiter.tryAcquire(0L, 1).allowed()).isTrue();
        }

        Decision rejected = limiter.tryAcquire(999L, 1);

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.reason()).isEqualTo(RejectReason.WINDOW_LIMIT);
        assertThat(limiter.snapshot(999L).used()).isEqualTo(10.0);
    }

    @Test
    void fw04_requestAt1000_passesAndStartsNewWindow() {
        // FW-04：t=1000 已进入 [1000,2000)，新窗口重置，count=1。
        FixedWindowLimiter limiter = new FixedWindowLimiter(LIMIT, WINDOW_MILLIS);
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
    void fw05_windowBoundary_allowsDoubleLimit() {
        // FW-05：这是预期缺陷，不是 bug——窗口切换瞬间可放行 2×limit。
        FixedWindowLimiter limiter = new FixedWindowLimiter(LIMIT, WINDOW_MILLIS);

        int passed = 0;
        for (int i = 0; i < LIMIT; i++) {
            if (limiter.tryAcquire(900L, 1).allowed()) {
                passed++;
            }
        }
        assertThat(limiter.snapshot(999L).used()).isEqualTo(10.0);

        for (int i = 0; i < LIMIT; i++) {
            if (limiter.tryAcquire(1000L, 1).allowed()) {
                passed++;
            }
        }

        assertThat(passed).isEqualTo(20);
        assertThat(limiter.snapshot(1000L).used()).isEqualTo(10.0);
    }
}
