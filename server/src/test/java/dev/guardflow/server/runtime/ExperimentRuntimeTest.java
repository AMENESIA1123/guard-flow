package dev.guardflow.server.runtime;

import dev.guardflow.core.limiter.TokenBucketLimiter;
import dev.guardflow.server.api.dto.ExperimentView;
import dev.guardflow.server.api.dto.Sample;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ExperimentRuntimeTest {

    @Test
    void twentyTicksWithConstantInputAndTokenBucket_produceExpectedTotals() {
        ExperimentRuntime runtime = new ExperimentRuntime(
                "e_test",
                new TokenBucketLimiter(10.0, 2.0),
                InputGenerator.constant(20),
                10);

        runtime.start();
        for (int i = 0; i < 20; i++) {
            assertThat(runtime.tick()).isPresent();
        }

        ExperimentView view = runtime.view();
        assertThat(view.status()).isEqualTo("running");
        assertThat(view.t()).isEqualTo(1900L);
        assertThat(view.totals().offered()).isEqualTo(40L);
        assertThat(view.totals().passed()).isEqualTo(13L);
        assertThat(view.totals().rejected()).isEqualTo(27L);
        assertThat(view.samples()).hasSize(20);
        assertThat(view.level()).isCloseTo(0.08, within(1e-9));

        for (Sample sample : view.samples()) {
            assertThat(sample.offered()).isEqualTo(2);
            assertThat(sample.passed() + sample.rejected()).isEqualTo(sample.offered());
        }
    }
}
