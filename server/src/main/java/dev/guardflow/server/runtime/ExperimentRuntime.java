package dev.guardflow.server.runtime;

import dev.guardflow.core.Limiter;
import dev.guardflow.server.api.dto.ExperimentView;
import dev.guardflow.server.api.dto.Sample;

import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 单个实验的运行时。tick 只在 running 时推进，逻辑帧长度由构造参数决定。
 */
public final class ExperimentRuntime {

    private static final long DEFAULT_TICK_MILLIS = 100L;
    private static final int DEFAULT_BUFFER_SIZE = 300;

    private final String id;
    private final Supplier<Limiter> limiterSupplier;
    private final Supplier<InputGenerator> inputSupplier;
    private final int speed;
    private final long tickMillis;
    private final int bufferSize;
    private final OffsetDateTime createdAt;
    private final String backend;
    private final SampleAggregator aggregator = new SampleAggregator();
    private final Deque<Sample> samples = new ArrayDeque<>();

    private Limiter limiter;
    private InputGenerator input;
    private volatile RunStatus status = RunStatus.IDLE;
    private long tickIndex;

    public ExperimentRuntime(String id, Limiter limiter, InputGenerator input, int speed) {
        this(id, () -> limiter, () -> input, speed, DEFAULT_TICK_MILLIS, DEFAULT_BUFFER_SIZE);
    }

    public ExperimentRuntime(String id, Supplier<Limiter> limiterSupplier,
                             Supplier<InputGenerator> inputSupplier, int speed,
                             long tickMillis, int bufferSize) {
        this(id, limiterSupplier, inputSupplier, speed, tickMillis, bufferSize, "local");
    }

    public ExperimentRuntime(String id, Supplier<Limiter> limiterSupplier,
                             Supplier<InputGenerator> inputSupplier, int speed,
                             long tickMillis, int bufferSize, String backend) {
        if (tickMillis <= 0) {
            throw new IllegalArgumentException("tickMillis must be > 0");
        }
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("bufferSize must be > 0");
        }
        this.id = id;
        this.limiterSupplier = limiterSupplier;
        this.inputSupplier = inputSupplier;
        this.speed = speed;
        this.tickMillis = tickMillis;
        this.bufferSize = bufferSize;
        this.backend = backend;
        this.createdAt = OffsetDateTime.now();
        this.limiter = limiterSupplier.get();
        this.input = inputSupplier.get();
    }

    public synchronized void start() {
        if (status == RunStatus.RUNNING) {
            throw new IllegalStateException("experiment is already running: " + id);
        }
        status = RunStatus.RUNNING;
    }

    public synchronized void pause() {
        if (status != RunStatus.RUNNING) {
            throw new IllegalStateException("experiment is not running: " + id);
        }
        status = RunStatus.PAUSED;
    }

    /**
     * 依赖构造时提供的 Supplier 重建限流器与输入源；单实例构造时只能清空计数与时间。
     */
    public synchronized void reset() {
        this.limiter = limiterSupplier.get();
        this.input = inputSupplier.get();
        this.tickIndex = 0L;
        this.aggregator.reset();
        this.samples.clear();
        this.status = RunStatus.IDLE;
    }

    public synchronized Optional<Sample> tick() {
        if (status != RunStatus.RUNNING) {
            return Optional.empty();
        }
        long t = tickIndex * tickMillis;
        int offered = input.offersAt(tickIndex);
        Sample sample = aggregator.aggregate(t, limiter, offered);
        tickIndex++;
        samples.addLast(sample);
        while (samples.size() > bufferSize) {
            samples.removeFirst();
        }
        return Optional.of(sample);
    }

    public synchronized ExperimentView view() {
        long t = samples.isEmpty() ? 0L : samples.peekLast().t();
        double level = samples.isEmpty() ? limiter.snapshot(t).level() : samples.peekLast().level();
        return new ExperimentView(id, status.wireName(), t, aggregator.totals(), level, List.copyOf(samples), backend);
    }

    public String id() {
        return id;
    }

    public RunStatus status() {
        return status;
    }

    public int speed() {
        return speed;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public String backend() {
        return backend;
    }
}
