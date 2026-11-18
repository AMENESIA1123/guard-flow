package dev.guardflow.server.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InputGeneratorTest {

    @Test
    void constant_qps20_spreadsTwoPerFrame() {
        InputGenerator generator = InputGenerator.constant(20);
        for (int tick = 0; tick < 10; tick++) {
            assertThat(generator.offersAt(tick)).isEqualTo(2);
        }
    }

    @Test
    void constant_qps1_putsOneRequestInFirstSecond() {
        InputGenerator generator = InputGenerator.constant(1);
        assertThat(generator.offersAt(0)).isEqualTo(1);
        int total = 1;
        for (int tick = 1; tick < 10; tick++) {
            total += generator.offersAt(tick);
        }
        assertThat(total).isEqualTo(1);
    }

    @Test
    void step_switchesQpsAtSegmentBoundary() {
        InputGenerator generator = InputGenerator.step(List.of(
                new InputGenerator.Segment(0L, 10),
                new InputGenerator.Segment(500L, 20)));
        for (int tick = 0; tick < 5; tick++) {
            assertThat(generator.offersAt(tick)).isEqualTo(1);
        }
        for (int tick = 5; tick < 10; tick++) {
            assertThat(generator.offersAt(tick)).isEqualTo(2);
        }
    }

    @Test
    void spike_addsBurstEverySecond() {
        InputGenerator generator = InputGenerator.spike(10, 5, 1000L);
        assertThat(generator.offersAt(0)).isEqualTo(6);
        for (int tick = 1; tick < 10; tick++) {
            assertThat(generator.offersAt(tick)).isEqualTo(1);
        }
        assertThat(generator.offersAt(10)).isEqualTo(6);
        assertThat(generator.offersAt(11)).isEqualTo(1);
    }

    @Test
    void poisson_sameSeed_isReproducible() {
        int[] expected = {3, 3, 4, 1, 1, 2, 4, 2, 3, 4, 2, 2, 2, 1, 3, 2, 4, 2, 3, 2};
        InputGenerator first = InputGenerator.poisson(20, 42L);
        InputGenerator second = InputGenerator.poisson(20, 42L);
        for (int tick = 0; tick < expected.length; tick++) {
            assertThat(first.offersAt(tick)).isEqualTo(expected[tick]);
            assertThat(second.offersAt(tick)).isEqualTo(expected[tick]);
        }
    }
}
