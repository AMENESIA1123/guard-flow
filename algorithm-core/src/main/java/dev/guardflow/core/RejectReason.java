package dev.guardflow.core;

/**
 * 拒绝原因。枚举值是稳定契约，固定 5 个，不得增删。
 */
public enum RejectReason {
    PASSED,
    WINDOW_LIMIT,
    QUEUE_FULL,
    NO_TOKEN,
    INVALID_PARAM
}
