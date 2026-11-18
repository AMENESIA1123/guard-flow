package dev.guardflow.server.distributed;

import dev.guardflow.core.Decision;
import dev.guardflow.core.Limiter;
import dev.guardflow.core.RejectReason;
import dev.guardflow.core.Snapshot;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

/**
 * Redis + Lua 令牌桶。判定时间由 Lua 内的 redis.call('TIME') 决定，不使用应用侧时间。
 */
public final class RedisTokenBucketLimiter implements Limiter {

    private final StringRedisTemplate redis;
    private final String key;
    private final double capacity;
    private final double ratePerSecond;

    public RedisTokenBucketLimiter(StringRedisTemplate redis, String key, double capacity, double ratePerSecond) {
        if (!Double.isFinite(capacity) || capacity < 1.0 || capacity > 1_000_000.0) {
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
                LuaScripts.TOKEN_BUCKET_ACQUIRE,
                List.of(key, LuaScripts.CLOCK_KEY),
                Double.toString(capacity),
                Double.toString(ratePerSecond),
                Integer.toString(permits));
        String[] parts = parse(raw);
        int allowed = Integer.parseInt(parts[0]);
        double tokens = Double.parseDouble(parts[1]);
        boolean allAllowed = allowed == permits;
        return new Decision(allAllowed, allAllowed ? RejectReason.PASSED : RejectReason.NO_TOKEN, tokens / capacity);
    }

    @Override
    public Snapshot snapshot(long nowMillis) {
        String raw = redis.execute(
                LuaScripts.TOKEN_BUCKET_SNAPSHOT,
                List.of(key, LuaScripts.CLOCK_KEY),
                Double.toString(capacity),
                Double.toString(ratePerSecond));
        if (raw == null) {
            throw new IllegalStateException("Redis token bucket snapshot returned no value");
        }
        double tokens = Double.parseDouble(raw);
        return new Snapshot(nowMillis, tokens / capacity, capacity, tokens);
    }

    private static String[] parse(String raw) {
        if (raw == null) {
            throw new IllegalStateException("Redis token bucket script returned no value");
        }
        String[] parts = raw.split("\\|", 2);
        if (parts.length != 2) {
            throw new IllegalStateException("Unexpected Redis token bucket result: " + raw);
        }
        return parts;
    }
}
