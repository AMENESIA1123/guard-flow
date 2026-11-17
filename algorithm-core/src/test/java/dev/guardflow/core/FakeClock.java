package dev.guardflow.core;

/**
 * 测试用可控时间源。算法测试只关心给定的时间序列，不能依赖真实时钟。
 */
public final class FakeClock implements Clock {

    private long nowMillis;

    public FakeClock() {
        this(0L);
    }

    public FakeClock(long initialMillis) {
        this.nowMillis = initialMillis;
    }

    @Override
    public long nowMillis() {
        return nowMillis;
    }

    public void advanceTo(long millis) {
        this.nowMillis = millis;
    }

    public void advanceBy(long deltaMillis) {
        this.nowMillis += deltaMillis;
    }
}
