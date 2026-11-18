package dev.guardflow.core.limiter;

import dev.guardflow.core.Decision;
import dev.guardflow.core.RejectReason;
import dev.guardflow.core.Snapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TokenBucketLimiterTest {

    private static final double CAPACITY = 10.0;
    private static final double RATE = 2.0;

    @Test
    void tb01_tenRequestsAtZero_allPass() {
        // TB-01：t=0 连续 10 个请求全部放行，tokens=0。
        TokenBucketLimiter limiter = new TokenBucketLimiter(CAPACITY, RATE);

        for (int i = 1; i <= 10; i++) {
            Decision decision = limiter.tryAcquire(0L, 1);
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.reason()).isEqualTo(RejectReason.PASSED);
            assertThat(decision.level()).isCloseTo((CAPACITY - i) / CAPACITY, within(1e-9));
        }

        Snapshot snapshot = limiter.snapshot(0L);
        assertThat(snapshot.t()).isEqualTo(0L);
        assertThat(snapshot.capacity()).isEqualTo(10.0);
        assertThat(snapshot.used()).isCloseTo(0.0, within(1e-9));
        assertThat(snapshot.level()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void tb02_eleventhRequestAtZero_rejectedWithNoToken() {
        // TB-02：t=0 满 10 个后再来 1 个，第 11 个因 NO_TOKEN 被拒绝。
        TokenBucketLimiter limiter = new TokenBucketLimiter(CAPACITY, RATE);
        acquireAllowed(limiter, 0L, 10);

        Decision rejected = limiter.tryAcquire(0L, 1);

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.reason()).isEqualTo(RejectReason.NO_TOKEN);
        assertThat(rejected.level()).isCloseTo(0.0, within(1e-9));
        assertThat(limiter.snapshot(0L).used()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void tb03_refillAt500_allowsOneRequest() {
        // TB-03：t=500 补充 1.0 个令牌，恰好够放行 1 个。
        TokenBucketLimiter limiter = new TokenBucketLimiter(CAPACITY, RATE);
        acquireAllowed(limiter, 0L, 10);

        Decision decision = limiter.tryAcquire(500L, 1);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).isEqualTo(RejectReason.PASSED);
        assertThat(decision.level()).isCloseTo(0.0, within(1e-9));
        assertThat(limiter.snapshot(500L).used()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void tb04_refillAt499_rejectedWithFractionalToken() {
        // TB-04：t=499 只补充 0.998 个令牌，不足 1 个，拒绝；tokens≈0.998。
        TokenBucketLimiter limiter = new TokenBucketLimiter(CAPACITY, RATE);
        acquireAllowed(limiter, 0L, 10);

        Decision rejected = limiter.tryAcquire(499L, 1);

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.reason()).isEqualTo(RejectReason.NO_TOKEN);
        assertThat(limiter.snapshot(499L).used()).isCloseTo(0.998, within(1e-9));
        assertThat(rejected.level()).isCloseTo(0.0998, within(1e-9));
    }

    @Test
    void tb05_refillCappedAtCapacity() {
        // TB-05：t=10000 补充封顶为 capacity=10，不得变成 25；随后 10 放行、2 拒绝。
        TokenBucketLimiter limiter = new TokenBucketLimiter(CAPACITY, RATE);
        acquireAllowed(limiter, 0L, 5);

        assertThat(limiter.snapshot(10000L).used()).isCloseTo(10.0, within(1e-9));

        int passed = 0;
        int rejected = 0;
        for (int i = 0; i < 12; i++) {
            Decision decision = limiter.tryAcquire(10000L, 1);
            if (decision.allowed()) {
                passed++;
            } else {
                rejected++;
                assertThat(decision.reason()).isEqualTo(RejectReason.NO_TOKEN);
            }
        }

        assertThat(passed).isEqualTo(10);
        assertThat(rejected).isEqualTo(2);
        assertThat(limiter.snapshot(10000L).used()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void tb06_fractionalTokensAccumulate() {
        // TB-06：t=200/400/600 各补充 0.4，累积 0.4 → 0.8 → 1.2，前两次拒绝、第三次放行。
        TokenBucketLimiter limiter = new TokenBucketLimiter(CAPACITY, RATE);
        acquireAllowed(limiter, 0L, 10);

        Decision first = limiter.tryAcquire(200L, 1);
        assertThat(first.allowed()).isFalse();
        assertThat(first.reason()).isEqualTo(RejectReason.NO_TOKEN);
        assertThat(limiter.snapshot(200L).used()).isCloseTo(0.4, within(1e-9));

        Decision second = limiter.tryAcquire(400L, 1);
        assertThat(second.allowed()).isFalse();
        assertThat(second.reason()).isEqualTo(RejectReason.NO_TOKEN);
        assertThat(limiter.snapshot(400L).used()).isCloseTo(0.8, within(1e-9));

        Decision third = limiter.tryAcquire(600L, 1);
        assertThat(third.allowed()).isTrue();
        assertThat(third.reason()).isEqualTo(RejectReason.PASSED);
        assertThat(limiter.snapshot(600L).used()).isCloseTo(0.2, within(1e-9));
    }

    private static void acquireAllowed(TokenBucketLimiter limiter, long nowMillis, int times) {
        for (int i = 0; i < times; i++) {
            Decision decision = limiter.tryAcquire(nowMillis, 1);
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.reason()).isEqualTo(RejectReason.PASSED);
        }
    }
}
