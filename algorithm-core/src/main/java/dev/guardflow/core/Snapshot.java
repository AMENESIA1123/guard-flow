package dev.guardflow.core;

/**
 * 某一时刻的只读状态快照，供绘图使用；调用它不得改变限流器状态。
 */
public record Snapshot(long t, double level, double capacity, double used) {
}
