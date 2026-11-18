package dev.guardflow.core.limiter;

import dev.guardflow.core.Decision;
import dev.guardflow.core.RejectReason;
import dev.guardflow.core.Snapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class LeakyBucketLimiterTest {

    private static final int CAPACITY = 10;
    private static final double RATE = 2.0;

    @Test
    void lb01_tenRequestsAtZero_allPass() {
        // LB-01：t=0 连续 10 个请求全部放行，queued=10。
        LeakyBucketLimiter limiter = new LeakyBucketLimiter(CAPACITY, RATE);

        for (int i = 1; i <= CAPACITY; i++) {
            Decision decision = limiter.tryAcquire(0L, 1);
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.reason()).isEqualTo(RejectReason.PASSED);
            assertThat(decision.level()).isCloseTo(i / (double) CAPACITY, within(1e-9));
        }

        Snapshot snapshot = limiter.snapshot(0L);
        assertThat(snapshot.t()).isEqualTo(0L);
        assertThat(snapshot.capacity()).isEqualTo(10.0);
        assertThat(snapshot.used()).isEqualTo(10.0);
        assertThat(snapshot.level()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void lb02_eleventhRequestAtZero_rejectedWithQueueFull() {
        // LB-02：t=0 满 10 个后再来 1 个，第 11 个因 QUEUE_FULL 被拒绝。
        LeakyBucketLimiter limiter = new LeakyBucketLimiter(CAPACITY, RATE);
        acquireAllowed(limiter, 0L, CAPACITY);

        Decision rejected = limiter.tryAcquire(0L, 1);

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.reason()).isEqualTo(RejectReason.QUEUE_FULL);
        assertThat(rejected.level()).isCloseTo(1.0, within(1e-9));
        assertThat(limiter.snapshot(0L).used()).isEqualTo(10.0);
    }

    @Test
    void lb03_requestAt500_drainsOneThenPasses() {
        // LB-03：t=500 先出队 1 个（10 → 9），再入队 1 个（9 → 10），放行。
        LeakyBucketLimiter limiter = new LeakyBucketLimiter(CAPACITY, RATE);
        acquireAllowed(limiter, 0L, CAPACITY);

        Decision decision = limiter.tryAcquire(500L, 1);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).isEqualTo(RejectReason.PASSED);
        assertThat(decision.level()).isCloseTo(1.0, within(1e-9));
        assertThat(limiter.snapshot(500L).used()).isEqualTo(10.0);
    }

    @Test
    void lb04_requestAt499_rejectedBeforeDrainTick() {
        // LB-04：t=499 未到 500ms 出队整点，队列仍满，拒绝。
        LeakyBucketLimiter limiter = new LeakyBucketLimiter(CAPACITY, RATE);
        acquireAllowed(limiter, 0L, CAPACITY);

        Decision rejected = limiter.tryAcquire(499L, 1);

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.reason()).isEqualTo(RejectReason.QUEUE_FULL);
        assertThat(limiter.snapshot(499L).used()).isEqualTo(10.0);
    }

    @Test
    void lb05_idleUntil5000_drainsAllThenAcceptsFive() {
        // LB-05：t=5000 已过 10 个出队整点，队列排空；随后 5 个全部放行，queued=5。
        LeakyBucketLimiter limiter = new LeakyBucketLimiter(CAPACITY, RATE);
        acquireAllowed(limiter, 0L, CAPACITY);

        for (int i = 1; i <= 5; i++) {
            Decision decision = limiter.tryAcquire(5000L, 1);
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.reason()).isEqualTo(RejectReason.PASSED);
            assertThat(decision.level()).isCloseTo(i / (double) CAPACITY, within(1e-9));
        }

        Snapshot snapshot = limiter.snapshot(5000L);
        assertThat(snapshot.used()).isEqualTo(5.0);
        assertThat(snapshot.level()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void lb06_requestAt1000_drainsTwoThenPasses() {
        // LB-06：t=1000 出队 2 个（10 → 8），再入队 1 个（8 → 9），放行。
        LeakyBucketLimiter limiter = new LeakyBucketLimiter(CAPACITY, RATE);
        acquireAllowed(limiter, 0L, CAPACITY);

        Decision decision = limiter.tryAcquire(1000L, 1);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).isEqualTo(RejectReason.PASSED);
        assertThat(decision.level()).isCloseTo(0.9, within(1e-9));
        assertThat(limiter.snapshot(1000L).used()).isEqualTo(9.0);
    }

    private static void acquireAllowed(LeakyBucketLimiter limiter, long nowMillis, int times) {
        for (int i = 0; i < times; i++) {
            Decision decision = limiter.tryAcquire(nowMillis, 1);
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.reason()).isEqualTo(RejectReason.PASSED);
        }
    }
}
