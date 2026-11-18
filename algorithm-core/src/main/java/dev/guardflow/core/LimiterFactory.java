package dev.guardflow.core;

import dev.guardflow.core.limiter.FixedWindowLimiter;
import dev.guardflow.core.limiter.SlidingWindowLogLimiter;
import dev.guardflow.core.limiter.TokenBucketLimiter;

import java.util.Map;

/**
 * 按算法 id 创建限流器；未知算法抛 UnsupportedOperationException，参数缺失抛 IllegalArgumentException。
 */
public final class LimiterFactory {

    private LimiterFactory() {
    }

    public static Limiter create(LimiterConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        String algorithm = config.algorithm();
        if (algorithm == null || algorithm.isBlank()) {
            throw new IllegalArgumentException("algorithm must not be blank");
        }
        if ("fixed_window".equals(algorithm)) {
            Map<String, Number> params = config.params();
            if (params == null) {
                throw new IllegalArgumentException("params must not be null");
            }
            return new FixedWindowLimiter(requireInt(params, "limit"), requireLong(params, "windowMillis"));
        }
        if ("sliding_window".equals(algorithm)) {
            Map<String, Number> params = config.params();
            if (params == null) {
                throw new IllegalArgumentException("params must not be null");
            }
            return new SlidingWindowLogLimiter(requireInt(params, "limit"), requireLong(params, "windowMillis"));
        }
        if ("token_bucket".equals(algorithm)) {
            Map<String, Number> params = config.params();
            if (params == null) {
                throw new IllegalArgumentException("params must not be null");
            }
            return new TokenBucketLimiter(requireDouble(params, "capacity"), requireDouble(params, "ratePerSecond"));
        }
        throw new UnsupportedOperationException("unsupported algorithm: " + algorithm);
    }

    private static int requireInt(Map<String, Number> params, String key) {
        Number value = params.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing parameter: " + key);
        }
        return value.intValue();
    }

    private static long requireLong(Map<String, Number> params, String key) {
        Number value = params.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing parameter: " + key);
        }
        return value.longValue();
    }

    private static double requireDouble(Map<String, Number> params, String key) {
        Number value = params.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing parameter: " + key);
        }
        return value.doubleValue();
    }
}
