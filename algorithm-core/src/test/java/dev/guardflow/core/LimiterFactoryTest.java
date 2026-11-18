package dev.guardflow.core;

import dev.guardflow.core.limiter.FixedWindowLimiter;
import dev.guardflow.core.limiter.LeakyBucketLimiter;
import dev.guardflow.core.limiter.SlidingWindowLogLimiter;
import dev.guardflow.core.limiter.TokenBucketLimiter;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LimiterFactoryTest {

    @Test
    void createsAllFourAlgorithms() {
        assertThat(LimiterFactory.create(new LimiterConfig(
                "fixed_window", Map.<String, Number>of("limit", 10, "windowMillis", 1000))))
                .isInstanceOf(FixedWindowLimiter.class);
        assertThat(LimiterFactory.create(new LimiterConfig(
                "sliding_window", Map.<String, Number>of("limit", 10, "windowMillis", 1000))))
                .isInstanceOf(SlidingWindowLogLimiter.class);
        assertThat(LimiterFactory.create(new LimiterConfig(
                "token_bucket", Map.<String, Number>of("capacity", 10.0, "ratePerSecond", 2.0))))
                .isInstanceOf(TokenBucketLimiter.class);
        assertThat(LimiterFactory.create(new LimiterConfig(
                "leaky_bucket", Map.<String, Number>of("capacity", 10, "ratePerSecond", 2.0))))
                .isInstanceOf(LeakyBucketLimiter.class);
    }

    @Test
    void unknownAlgorithm_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> LimiterFactory.create(new LimiterConfig("unknown", Map.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported algorithm");
    }

    @Test
    void missingParameter_throwsIllegalArgumentException() {
        // token_bucket 缺少 ratePerSecond。
        assertThatThrownBy(() -> LimiterFactory.create(new LimiterConfig(
                "token_bucket", Map.<String, Number>of("capacity", 10.0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ratePerSecond");
    }

    @Test
    void outOfRangeParameter_throwsIllegalArgumentException() {
        // leaky_bucket 的 capacity 越界，由构造函数拒绝。
        assertThatThrownBy(() -> LimiterFactory.create(new LimiterConfig(
                "leaky_bucket", Map.<String, Number>of("capacity", 0, "ratePerSecond", 2.0))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullConfig_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> LimiterFactory.create(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("config");
    }
}
