package dev.guardflow.server.api;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SseHub {

    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * 注册一个 SSE 连接，并立即推送一次 snapshot；断线、超时、异常都会移除 emitter。
     */
    public SseEmitter subscribe(String id, Object snapshot) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(id, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(id, emitter));
        emitter.onTimeout(() -> remove(id, emitter));
        emitter.onError(error -> remove(id, emitter));
        try {
            emitter.send(SseEmitter.event().name("snapshot").data(snapshot));
        } catch (IOException | IllegalStateException exception) {
            remove(id, emitter);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    public void broadcast(String id, String event, Object payload) {
        List<SseEmitter> list = emitters.get(id);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(event).data(payload));
            } catch (IOException | IllegalStateException exception) {
                remove(id, emitter);
                try {
                    emitter.completeWithError(exception);
                } catch (RuntimeException ignored) {
                    // 客户端已经断开，完成回调会负责移除。
                }
            }
        }
    }

    public void close(String id) {
        List<SseEmitter> list = emitters.remove(id);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.complete();
            } catch (RuntimeException ignored) {
                // 已完成的 emitter 不需要再次处理。
            }
        }
    }

    private void remove(String id, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(id);
        if (list == null) {
            return;
        }
        list.remove(emitter);
        if (list.isEmpty()) {
            emitters.remove(id, list);
        }
    }
}
