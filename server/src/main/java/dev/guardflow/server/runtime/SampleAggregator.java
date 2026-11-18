package dev.guardflow.server.runtime;

import dev.guardflow.core.Decision;
import dev.guardflow.core.Limiter;
import dev.guardflow.server.api.dto.ExperimentView;
import dev.guardflow.server.api.dto.Sample;

/**
 * 逐帧调用限流器并累计总量；permits 一律按 1 个逐个判定，保证与 n 次单请求等价。
 */
public final class SampleAggregator {

    private long offered;
    private long passed;
    private long rejected;

    public Sample aggregate(long t, Limiter limiter, int frameOffered) {
        if (frameOffered < 0) {
            throw new IllegalArgumentException("frameOffered must be >= 0");
        }
        int framePassed = 0;
        int frameRejected = 0;
        String reason = "PASSED";
        double level = limiter.snapshot(t).level();

        for (int i = 0; i < frameOffered; i++) {
            Decision decision = limiter.tryAcquire(t, 1);
            level = decision.level();
            if (decision.allowed()) {
                framePassed++;
            } else {
                frameRejected++;
                reason = decision.reason().name();
            }
        }

        offered += frameOffered;
        passed += framePassed;
        rejected += frameRejected;
        return new Sample(t, frameOffered, framePassed, frameRejected, level, reason);
    }

    public ExperimentView.Totals totals() {
        return new ExperimentView.Totals(offered, passed, rejected);
    }

    public void reset() {
        offered = 0;
        passed = 0;
        rejected = 0;
    }
}
