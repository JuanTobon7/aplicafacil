package com.aplicafacil.linkedin.application.services;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.aplicafacil.linkedin.application.services.ConnectionStateService.StatusView;

/** Suscriptores SSE de {@code /linkedin/status/stream}, por usuario. */
@Component
public class StatusBroadcaster {

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID userId, StatusView current) {
        SseEmitter emitter = new SseEmitter(0L);
        List<SseEmitter> list = emitters.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable remove = () -> list.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        send(emitter, current);
        return emitter;
    }

    public void publish(UUID userId, StatusView status) {
        emitters.getOrDefault(userId, List.of()).forEach(emitter -> send(emitter, status));
    }

    private static void send(SseEmitter emitter, StatusView status) {
        try {
            emitter.send(SseEmitter.event().name("status").data(status));
        } catch (IOException | IllegalStateException e) {
            emitter.completeWithError(e);
        }
    }
}
