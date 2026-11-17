package dev.guardflow.core;

/**
 * 生产环境时间源。这是 algorithm-core 中唯一允许读取系统墙上时钟的地方；
 * 算法只接收调用方传入的 nowMillis，便于测试与倍速回放。
 */
public final class SystemClock implements Clock {

    @Override
    public long nowMillis() {
        return System.currentTimeMillis();
    }
}
