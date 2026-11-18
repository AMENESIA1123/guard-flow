package dev.guardflow.server.runtime;

import dev.guardflow.core.Limiter;
import dev.guardflow.core.LimiterConfig;
import dev.guardflow.core.LimiterFactory;
import dev.guardflow.server.api.dto.CreateExperimentRequest;
import dev.guardflow.server.api.dto.ExperimentView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExperimentRegistry {

    private static final Set<Integer> ALLOWED_SPEEDS = Set.of(1, 5, 10, 20, 50);

    private final Map<String, ExperimentRuntime> experiments = new ConcurrentHashMap<>();
    private final long tickMillis;
    private final int bufferSize;
    private final int maxExperiments;

    public ExperimentRegistry(
            @Value("${guardflow.tick-millis:100}") long tickMillis,
            @Value("${guardflow.buffer-size:300}") int bufferSize,
            @Value("${guardflow.max-experiments:20}") int maxExperiments) {
        this.tickMillis = tickMillis;
        this.bufferSize = bufferSize;
        this.maxExperiments = maxExperiments;
    }

    public ExperimentRuntime create(CreateExperimentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        String algorithm = request.algorithm();
        if (algorithm == null || algorithm.isBlank()) {
            throw new IllegalArgumentException("algorithm must not be blank");
        }
        int speed = request.speed() == null ? 10 : request.speed();
        if (!ALLOWED_SPEEDS.contains(speed)) {
            throw new IllegalArgumentException("speed must be one of " + ALLOWED_SPEEDS);
        }
        if (request.input() == null) {
            throw new IllegalArgumentException("input must not be null");
        }

        Map<String, Number> params = copyParams(request.params());
        CreateExperimentRequest.InputSpec inputSpec = request.input();
        buildInput(inputSpec);
        LimiterFactory.create(new LimiterConfig(algorithm, params));

        if (experiments.size() >= maxExperiments) {
            throw new IllegalStateException("max experiments reached: " + maxExperiments);
        }

        String id = "e_" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        ExperimentRuntime runtime = new ExperimentRuntime(
                id,
                () -> LimiterFactory.create(new LimiterConfig(algorithm, params)),
                () -> buildInput(inputSpec),
                speed,
                tickMillis,
                bufferSize);
        experiments.put(id, runtime);
        return runtime;
    }

    public ExperimentRuntime get(String id) {
        ExperimentRuntime runtime = experiments.get(id);
        if (runtime == null) {
            throw new NoSuchElementException("experiment not found: " + id);
        }
        return runtime;
    }

    public List<ExperimentRuntime> all() {
        return List.copyOf(experiments.values());
    }

    public ExperimentView view(String id) {
        return get(id).view();
    }

    public ExperimentRuntime control(String id, String action) {
        ExperimentRuntime runtime = get(id);
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action must not be blank");
        }
        switch (action) {
            case "start" -> runtime.start();
            case "pause" -> runtime.pause();
            case "reset" -> runtime.reset();
            default -> throw new IllegalArgumentException("unknown action: " + action);
        }
        return runtime;
    }

    public void delete(String id) {
        experiments.remove(id);
    }

    private static Map<String, Number> copyParams(Map<String, Number> params) {
        if (params == null) {
            return Map.of();
        }
        for (Map.Entry<String, Number> entry : params.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("params must not contain null keys or values");
            }
        }
        return Map.copyOf(params);
    }

    private InputGenerator buildInput(CreateExperimentRequest.InputSpec spec) {
        String mode = spec.mode();
        if (mode == null || mode.isBlank()) {
            throw new IllegalArgumentException("input.mode must not be blank");
        }
        return switch (mode) {
            case "constant" -> InputGenerator.constant(requireInt(spec.qps(), "input.qps"));
            case "step" -> InputGenerator.step(toSegments(spec.segments()));
            case "spike" -> InputGenerator.spike(
                    requireInt(spec.qps(), "input.qps"),
                    requireInt(spec.burstSize(), "input.burstSize"),
                    requireLong(spec.everyMillis(), "input.everyMillis"));
            case "poisson" -> InputGenerator.poisson(
                    requireInt(spec.qps(), "input.qps"),
                    requireLong(spec.seed(), "input.seed"));
            default -> throw new IllegalArgumentException("unknown input mode: " + mode);
        };
    }

    private List<InputGenerator.Segment> toSegments(List<CreateExperimentRequest.Segment> segments) {
        if (segments == null || segments.isEmpty()) {
            throw new IllegalArgumentException("input.segments must not be empty");
        }
        return segments.stream()
                .map(segment -> {
                    if (segment == null) {
                        throw new IllegalArgumentException("input.segments must not contain null");
                    }
                    return new InputGenerator.Segment(segment.atMillis(), segment.qps());
                })
                .toList();
    }

    private static int requireInt(Integer value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }

    private static long requireLong(Long value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}
