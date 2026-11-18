package dev.guardflow.server.distributed;

import dev.guardflow.core.Decision;
import dev.guardflow.core.Limiter;
import dev.guardflow.core.RejectReason;
import dev.guardflow.core.Snapshot;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

/**
 * Redis + Lua 漏桶。判定时间由 Lua 内的 redis.call('TIME') 决定，不使用应用侧时间。
 */
public final class RedisLeakyBucketLimiter implements Limiter {

    private final StringRedisTemplate redis;
    private final String key;
    private final int capacity;
    private final double ratePerSecond;

    public RedisLeakyBucketLimiter(StringRedisTemplate redis, String key, int capacity, double ratePerSecond) {
        if (capacity < 1 || capacity > 1_000_000) {
            throw new IllegalArgumentException("capacity must be in [1, 1000000]");
        }
        if (!Double.isFinite(ratePerSecond) || ratePerSecond < 0.001 || ratePerSecond > 1_000_000.0) {
            throw new IllegalArgumentException("ratePerSecond must be in [0.001, 1000000]");
        }
        this.redis = redis;
        this.key = key;
        this.capacity = capacity;
        this.ratePerSecond = ratePerSecond;
    }

    @Override
    public Decision tryAcquire(long nowMillis, int permits) {
        if (permits <= 0) {
            return new Decision(false, RejectReason.INVALID_PARAM, snapshot(nowMillis).level());
        }
        String raw = redis.execute(
                LuaScripts.LEAKY_BUCKET_ACQUIRE,
                List.of(key, LuaScripts.CLOCK_KEY),
                Integer.toString(capacity),
                Double.toString(ratePerSecond),
                Integer.toString(permits));
        String[] parts = parse(raw);
        int allowed = Integer.parseInt(parts[0]);
        int queued = Integer.parseInt(parts[1]);
        boolean allAllowed = allowed == permits;
        return new Decision(allAllowed, allAllowed ? RejectReason.PASSED : RejectReason.QUEUE_FULL, (double) queued / capacity);
    }

    @Override
    public Snapshot snapshot(long nowMillis) {
        String raw = redis.execute(
                LuaScripts.LEAKY_BUCKET_SNAPSHOT,
                List.of(key, LuaScripts.CLOCK_KEY),
                Integer.toString(capacity),
                Double.toString(ratePerSecond));
        if (raw == null) {
            throw new IllegalStateException("Redis leaky bucket snapshot returned no value");
        }
        int queued = Integer.parseInt(raw);
        return new Snapshot(nowMillis, (double) queued / capacity, capacity, queued);
    }

    private static String[] parse(String raw) {
        if (raw == null) {
            throw new IllegalStateException("Redis leaky bucket script returned no value");
        }
        String[] parts = raw.split("\\|", 2);
        if (parts.length != 2) {
            throw new IllegalStateException("Unexpected Redis leaky bucket result: " + raw);
        }
        return parts;
    }
}
