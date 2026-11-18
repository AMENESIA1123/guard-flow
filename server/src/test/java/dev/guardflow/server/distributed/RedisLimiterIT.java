package dev.guardflow.server.distributed;

import dev.guardflow.core.Decision;
import dev.guardflow.core.RejectReason;
import dev.guardflow.core.Snapshot;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Testcontainers(disabledWithoutDocker = true)
class RedisLimiterIT {

    private static final String KEY = "guardflow:tb:it";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate template;
    private RedisTokenBucketLimiter limiter;

    @BeforeAll
    static void setUpRedis() {
        factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
    }

    @AfterAll
    static void tearDownRedis() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @BeforeEach
    void setUp() {
        template.delete(KEY);
        template.delete(LuaScripts.CLOCK_KEY);
        limiter = new RedisTokenBucketLimiter(template, KEY, 10.0, 2.0);
    }

    @AfterEach
    void tearDown() {
        template.delete(KEY);
        template.delete(LuaScripts.CLOCK_KEY);
    }

    private static void clock(long nowMillis) {
        template.opsForValue().set(LuaScripts.CLOCK_KEY, Long.toString(nowMillis));
    }

    @Test
    void tb01_tenRequestsAtZero_allPass() {
        // TB-01：t=0 连续 10 个请求全部放行，tokens=0。
        clock(0);
        for (int i = 1; i <= 10; i++) {
            Decision decision = limiter.tryAcquire(0L, 1);
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.reason()).isEqualTo(RejectReason.PASSED);
            assertThat(decision.level()).isCloseTo((10.0 - i) / 10.0, within(1e-9));
        }
        Snapshot snapshot = limiter.snapshot(0L);
        assertThat(snapshot.used()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void tb02_eleventhRequestAtZero_rejectedWithNoToken() {
        clock(0);
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire(0L, 1).allowed()).isTrue();
        }
        Decision rejected = limiter.tryAcquire(0L, 1);
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.reason()).isEqualTo(RejectReason.NO_TOKEN);
        assertThat(rejected.level()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void tb03_refillAt500_allowsOneRequest() {
        clock(0);
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire(0L, 1).allowed()).isTrue();
        }
        clock(500);
        Decision decision = limiter.tryAcquire(500L, 1);
        assertThat(decision.allowed()).isTrue();
        assertThat(limiter.snapshot(500L).used()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void tb04_refillAt499_rejectedWithFractionalToken() {
        clock(0);
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire(0L, 1).allowed()).isTrue();
        }
        clock(499);
        Decision rejected = limiter.tryAcquire(499L, 1);
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.reason()).isEqualTo(RejectReason.NO_TOKEN);
        assertThat(limiter.snapshot(499L).used()).isCloseTo(0.998, within(1e-9));
    }

    @Test
    void tb05_refillCappedAtCapacity() {
        clock(0);
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire(0L, 1).allowed()).isTrue();
        }
        clock(10000);
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
        clock(0);
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire(0L, 1).allowed()).isTrue();
        }
        clock(200);
        assertThat(limiter.tryAcquire(200L, 1).allowed()).isFalse();
        assertThat(limiter.snapshot(200L).used()).isCloseTo(0.4, within(1e-9));
        clock(400);
        assertThat(limiter.tryAcquire(400L, 1).allowed()).isFalse();
        assertThat(limiter.snapshot(400L).used()).isCloseTo(0.8, within(1e-9));
        clock(600);
        assertThat(limiter.tryAcquire(600L, 1).allowed()).isTrue();
        assertThat(limiter.snapshot(600L).used()).isCloseTo(0.2, within(1e-9));
    }
}
