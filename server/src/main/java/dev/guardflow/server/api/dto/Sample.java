package dev.guardflow.server.api.dto;

public record Sample(long t, int offered, int passed, int rejected, double level, String reason) {
}
