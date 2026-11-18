package dev.guardflow.server.runtime;

import dev.guardflow.server.api.SseHub;
import dev.guardflow.server.api.dto.Sample;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 单线程定时任务驱动所有实验。每 100ms 真实时间按实验 speed 推进对应数量的逻辑帧。
 */
@Configuration
@ConditionalOnProperty(name = "guardflow.ticker.enabled", havingValue = "true", matchIfMissing = true)
public class TickerConfig {

    private static final Logger log = LoggerFactory.getLogger(TickerConfig.class);

    private final ExperimentRegistry registry;
    private final SseHub sseHub;
    private final long periodMillis;

    private ScheduledExecutorService executor;

    public TickerConfig(ExperimentRegistry registry, SseHub sseHub,
                        @Value("${guardflow.tick-millis:100}") long periodMillis) {
        this.registry = registry;
        this.sseHub = sseHub;
        this.periodMillis = periodMillis;
    }

    @PostConstruct
    void start() {
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "guardflow-ticker");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(this::tickAll, periodMillis, periodMillis, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void tickAll() {
        for (ExperimentRuntime runtime : registry.all()) {
            if (runtime.status() != RunStatus.RUNNING) {
                continue;
            }
            try {
                for (int i = 0; i < runtime.speed(); i++) {
                    Optional<Sample> sample = runtime.tick();
                    sample.ifPresent(value -> sseHub.broadcast(runtime.id(), "sample", value));
                }
            } catch (RuntimeException exception) {
                log.warn("tick failed for experiment {}", runtime.id(), exception);
            }
        }
    }
}
