package dev.guardflow.server.api.dto;

import java.util.List;

public record ExperimentView(
        String id,
        String status,
        long t,
        Totals totals,
        double level,
        List<Sample> samples,
        String backend) {

    public record Totals(long offered, long passed, long rejected) {
    }
}
