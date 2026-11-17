package dev.guardflow.core;

/**
 * 单次限流决策。allowed 为 true 时 reason 必须是 PASSED，level 必须落在 [0, 1]。
 */
public record Decision(boolean allowed, RejectReason reason, double level) {
}
