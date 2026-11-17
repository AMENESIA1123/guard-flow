package dev.guardflow.core;

/**
 * 时间源。实验运行时通过它取逻辑时间，再以 long 毫秒传给限流器，
 * 这样算法本身不依赖系统时钟，测试和倍速回放才能可控。
 */
public interface Clock {
    long nowMillis();
}
