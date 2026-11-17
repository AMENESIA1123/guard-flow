package dev.guardflow.core;

import java.util.Map;

/**
 * 限流器参数载体；algorithm 决定实现类，params 由各实现自行取值与校验。
 */
public record LimiterConfig(String algorithm, Map<String, Number> params) {
}
