# 文件清单与类职责

> 建立骨架时严格按此表创建文件，**不要**多建、不要改名、不要自创目录。
> 标注 `[T?]` 的文件在对应任务卡中才创建。

---

## 1. 完整目录树

```
guard-flow/
├─ AGENTS.md
├─ PROGRESS.md
├─ README.md                          [T12]
├─ LICENSE                            [T0]  MIT
├─ .gitignore                         [T0]
├─ pom.xml                            聚合 POM（packaging=pom）
├─ docker-compose.yml                 [T11] 仅 redis
├─ .github/workflows/ci.yml           [T0]
│
├─ docs/
│  ├─ README.md
│  ├─ spec-algorithms.md
│  ├─ api.md
│  ├─ file-manifest.md
│  ├─ tasks.md
│  ├─ ui-spec.md
│  └─ dependencies.md
│
├─ algorithm-core/
│  ├─ pom.xml
│  └─ src/
│     ├─ main/java/dev/guardflow/core/
│     │  ├─ Clock.java                [T1]
│     │  ├─ SystemClock.java          [T1]
│     │  ├─ Limiter.java              [T1]
│     │  ├─ Decision.java             [T1]
│     │  ├─ Snapshot.java             [T1]
│     │  ├─ RejectReason.java         [T1]
│     │  ├─ LimiterConfig.java        [T1]
│     │  ├─ LimiterFactory.java       [T2]
│     │  └─ limiter/
│     │     ├─ FixedWindowLimiter.java        [T2]
│     │     ├─ SlidingWindowLogLimiter.java   [T3]
│     │     ├─ TokenBucketLimiter.java        [T4]
│     │     └─ LeakyBucketLimiter.java        [T5]
│     └─ test/java/dev/guardflow/core/
│        ├─ FakeClock.java            [T1]
│        └─ limiter/
│           ├─ FixedWindowLimiterTest.java        [T2]
│           ├─ SlidingWindowLogLimiterTest.java   [T3]
│           ├─ TokenBucketLimiterTest.java        [T4]
│           └─ LeakyBucketLimiterTest.java        [T5]
│
└─ server/
   ├─ pom.xml
   └─ src/
      ├─ main/java/dev/guardflow/server/
      │  ├─ GuardFlowApplication.java     [T6]
      │  ├─ api/
      │  │  ├─ AlgorithmController.java   [T6]
      │  │  ├─ ExperimentController.java  [T6]
      │  │  ├─ GlobalExceptionHandler.java[T6]
      │  │  ├─ SseHub.java                [T7]
      │  │  └─ dto/
      │  │     ├─ CreateExperimentRequest.java  [T6]
      │  │     ├─ ExperimentView.java           [T6]
      │  │     ├─ ControlRequest.java           [T6]
      │  │     ├─ Sample.java                   [T6]
      │  │     └─ ErrorResponse.java            [T6]
      │  ├─ runtime/
      │  │  ├─ ExperimentRegistry.java    [T6]
      │  │  ├─ ExperimentRuntime.java     [T6]
      │  │  ├─ InputGenerator.java        [T6]
      │  │  ├─ SampleAggregator.java      [T6]
      │  │  ├─ RunStatus.java             [T6]
      │  │  └─ TickerConfig.java          [T7]
      │  └─ distributed/                  [T11]
      │     ├─ RedisTokenBucketLimiter.java
      │     ├─ RedisLeakyBucketLimiter.java
      │     └─ LuaScripts.java
      ├─ main/resources/
      │  ├─ application.yml               [T6]
      │  └─ static/
      │     ├─ index.html                 [T8]
      │     ├─ styles.css                 [T8]
      │     ├─ app.js                     [T8]
      │     └─ charts.js                  [T9]
      └─ test/java/dev/guardflow/server/
         ├─ runtime/InputGeneratorTest.java        [T6]
         ├─ runtime/ExperimentRuntimeTest.java     [T7]
         ├─ api/ApiSmokeTest.java                  [T6]
         └─ distributed/RedisLimiterIT.java        [T11]
```

---

## 2. 关键类型签名（不得偏离）

### algorithm-core

```java
public interface Clock { long nowMillis(); }

public final class SystemClock implements Clock { ... }

public enum RejectReason { PASSED, WINDOW_LIMIT, QUEUE_FULL, NO_TOKEN, INVALID_PARAM }

public record Decision(boolean allowed, RejectReason reason, double level) {}
public record Snapshot(long t, double level, double capacity, double used) {}
public record LimiterConfig(String algorithm, Map<String, Number> params) {}

public interface Limiter {
    Decision tryAcquire(long nowMillis, int permits);
    Snapshot snapshot(long nowMillis);
}
```

`LimiterFactory`：

```java
public static Limiter create(LimiterConfig config);   // 参数非法抛 IllegalArgumentException
```

四个实现类构造参数：

```java
new FixedWindowLimiter(int limit, long windowMillis)
new SlidingWindowLogLimiter(int limit, long windowMillis)
new TokenBucketLimiter(double capacity, double ratePerSecond)
new LeakyBucketLimiter(int capacity, double ratePerSecond)
```

> 构造函数注入初值，**不注入 Clock**——算法本身不读时钟，时间由 `tryAcquire(t, n)` 传入。这样测试无需 mock 时间。

### server

```java
public record Sample(long t, int offered, int passed, int rejected, double level, String reason) {}

public final class ExperimentRuntime {
    ExperimentRuntime(String id, Limiter limiter, InputGenerator input, int speed);
    void start(); void pause(); void reset();
    /** 推进一帧（逻辑 100ms），返回本帧采样；非 running 返回 empty。 */
    Optional<Sample> tick();
    ExperimentView view();   // 含最近 300 条 samples
}

public interface InputGenerator {
    /** 第 tickIndex 帧（逻辑时间 tickIndex*100ms）到达的请求数，确定性。 */
    int offersAt(long tickIndex);
}
```

`SseHub`：持有 `Map<String, List<SseEmitter>>`，提供 `broadcast(String id, String event, Object payload)`；单线程 `ScheduledExecutorService` 驱动所有实验的 `tick()`。

---

## 3. 配置

`application.yml`：

```yaml
server:
  port: 8080
spring:
  application:
    name: guard-flow
guardflow:
  tick-millis: 100        # 逻辑帧长度
  buffer-size: 300        # 每实验保留的采样数
  max-experiments: 20
```

`distributed` profile 追加：

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

---

## 4. 构建产物

- `server/target/guard-flow-server-1.0.0-SNAPSHOT.jar`（`spring-boot-maven-plugin` 打可执行 jar）
- `algorithm-core` 为普通 jar，被 `server` 以 `compile` 依赖引入
