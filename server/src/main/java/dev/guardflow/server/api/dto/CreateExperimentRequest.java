package dev.guardflow.server.api.dto;

import java.util.List;
import java.util.Map;

public record CreateExperimentRequest(
        String algorithm,
        Map<String, Number> params,
        InputSpec input,
        Integer speed) {

    public record InputSpec(
            String mode,
            Integer qps,
            List<Segment> segments,
            Integer burstSize,
            Long everyMillis,
            Long seed) {
    }

    public record Segment(long atMillis, int qps) {
    }
}
