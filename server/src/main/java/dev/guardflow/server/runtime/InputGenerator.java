package dev.guardflow.server.runtime;

import java.util.List;
import java.util.Random;

/**
 * 输入生成器。所有模式都必须确定性：同一组参数与同一 tick 序列产生同样的请求数。
 */
public interface InputGenerator {

    int offersAt(long tickIndex);

    static InputGenerator constant(int qps) {
        requireQps(qps);
        return tickIndex -> constantOffers(tickIndex, qps);
    }

    static InputGenerator step(List<Segment> segments) {
        if (segments == null || segments.isEmpty()) {
            throw new IllegalArgumentException("segments must not be empty");
        }
        long previous = Long.MIN_VALUE;
        for (Segment segment : segments) {
            if (segment == null) {
                throw new IllegalArgumentException("segments must not contain null");
            }
            if (segment.atMillis() < 0) {
                throw new IllegalArgumentException("segment.atMillis must be >= 0");
            }
            if (segment.atMillis() <= previous) {
                throw new IllegalArgumentException("segments must be strictly ascending by atMillis");
            }
            requireQps(segment.qps());
            previous = segment.atMillis();
        }
        List<Segment> copy = List.copyOf(segments);
        return tickIndex -> {
            int qps = qpsAt(copy, tickIndex * 100L);
            return qps == 0 ? 0 : constantOffers(tickIndex, qps);
        };
    }

    static InputGenerator spike(int qps, int burstSize, long everyMillis) {
        requireQps(qps);
        if (burstSize < 1 || burstSize > 500) {
            throw new IllegalArgumentException("burstSize must be in [1, 500]");
        }
        if (everyMillis <= 0) {
            throw new IllegalArgumentException("everyMillis must be > 0");
        }
        return tickIndex -> {
            long millis = tickIndex * 100L;
            int base = constantOffers(tickIndex, qps);
            return millis % everyMillis == 0 ? base + burstSize : base;
        };
    }

    static InputGenerator poisson(int qps, long seed) {
        requireQps(qps);
        Random random = new Random(seed);
        double mean = qps / 10.0;
        return tickIndex -> samplePoisson(random, mean);
    }

    record Segment(long atMillis, int qps) {
    }

    private static int qpsAt(List<Segment> segments, long millis) {
        int qps = 0;
        for (Segment segment : segments) {
            if (segment.atMillis() <= millis) {
                qps = segment.qps();
            } else {
                break;
            }
        }
        return qps;
    }

    private static int constantOffers(long tickIndex, int qps) {
        // 每秒 qps 个请求尽量均匀摊到 10 个 100ms 帧上，且每秒总数严格等于 qps。
        long before = (long) Math.ceil(tickIndex * qps / 10.0);
        long after = (long) Math.ceil((tickIndex + 1) * qps / 10.0);
        return (int) (after - before);
    }

    private static int samplePoisson(Random random, double mean) {
        double limit = Math.exp(-mean);
        double product = 1.0;
        int count = 0;
        do {
            count++;
            product *= random.nextDouble();
        } while (product > limit);
        return count - 1;
    }

    private static void requireQps(int qps) {
        if (qps < 1 || qps > 500) {
            throw new IllegalArgumentException("qps must be in [1, 500]");
        }
    }
}
