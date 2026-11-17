package dev.guardflow.core;

public interface Limiter {

    /**
     * 在 nowMillis 时刻尝试获取 permits 个许可；一次调用内 permits 个请求按顺序逐个判定。
     */
    Decision tryAcquire(long nowMillis, int permits);

    /**
     * 读取当前状态用于绘图，不改变任何内部状态。
     */
    Snapshot snapshot(long nowMillis);
}
