package dev.guardflow.server.api;

import dev.guardflow.server.api.dto.ControlRequest;
import dev.guardflow.server.api.dto.CreateExperimentRequest;
import dev.guardflow.server.api.dto.ExperimentView;
import dev.guardflow.server.runtime.ExperimentRegistry;
import dev.guardflow.server.runtime.ExperimentRuntime;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/experiments")
public class ExperimentController {

    private final ExperimentRegistry registry;
    private final SseHub sseHub;

    public ExperimentController(ExperimentRegistry registry, SseHub sseHub) {
        this.registry = registry;
        this.sseHub = sseHub;
    }

    @PostMapping
    public ResponseEntity<CreateResponse> create(@RequestBody CreateExperimentRequest request) {
        ExperimentRuntime runtime = registry.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreateResponse(
                runtime.id(),
                runtime.status().wireName(),
                request.algorithm(),
                request.params(),
                request.input(),
                runtime.speed(),
                runtime.createdAt()));
    }

    @PostMapping("/{id}/control")
    public ControlResponse control(@PathVariable String id, @RequestBody ControlRequest request) {
        ExperimentRuntime runtime = registry.control(id, request.action());
        sseHub.broadcast(id, "state", Map.of("status", runtime.status().wireName()));
        return new ControlResponse(runtime.id(), runtime.status().wireName());
    }

    @GetMapping("/{id}")
    public ExperimentView view(@PathVariable String id) {
        return registry.view(id);
    }

    @GetMapping(path = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String id) {
        ExperimentView snapshot = registry.view(id);
        return sseHub.subscribe(id, snapshot);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        registry.delete(id);
        sseHub.close(id);
        return ResponseEntity.noContent().build();
    }

    public record CreateResponse(String id, String status, String algorithm, Map<String, Number> params,
                                 CreateExperimentRequest.InputSpec input, int speed, OffsetDateTime createdAt) {
    }

    public record ControlResponse(String id, String status) {
    }
}
