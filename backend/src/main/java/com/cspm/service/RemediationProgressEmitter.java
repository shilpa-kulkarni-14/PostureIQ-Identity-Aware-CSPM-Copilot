package com.cspm.service;

import com.cspm.model.RemediationProgressEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class RemediationProgressEmitter {

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * Create a new SSE emitter for the given session. The emitter has a 5-minute
     * timeout and is automatically cleaned up on completion, timeout, or error.
     */
    public SseEmitter createEmitter(String sessionId) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L); // 5-minute timeout

        emitter.onCompletion(() -> {
            log.debug("SSE emitter completed for session {}", sessionId);
            emitters.remove(sessionId);
        });
        emitter.onTimeout(() -> {
            log.debug("SSE emitter timed out for session {}", sessionId);
            emitters.remove(sessionId);
        });
        emitter.onError(ex -> {
            log.debug("SSE emitter error for session {}: {}", sessionId, ex.getMessage());
            emitters.remove(sessionId);
        });

        emitters.put(sessionId, emitter);
        log.info("Created SSE emitter for session {}", sessionId);
        return emitter;
    }

    /**
     * Emit a progress event to the SSE stream for the given session.
     * Silently ignored if no emitter exists for the session.
     */
    public void emit(String sessionId, RemediationProgressEvent event) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter == null) {
            log.debug("No SSE emitter for session {} — skipping event {}", sessionId, event.getType());
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(event);
            emitter.send(SseEmitter.event()
                    .name("progress")
                    .data(json));
            log.debug("Emitted {} event for session {}", event.getType(), sessionId);
        } catch (Exception e) {
            log.warn("Failed to emit SSE event for session {}: {}", sessionId, e.getMessage());
            emitters.remove(sessionId);
        }
    }

    /**
     * Complete and remove the SSE emitter for the given session.
     */
    public void complete(String sessionId) {
        SseEmitter emitter = emitters.remove(sessionId);
        if (emitter != null) {
            try {
                emitter.complete();
                log.debug("Completed SSE emitter for session {}", sessionId);
            } catch (Exception e) {
                log.debug("Error completing SSE emitter for session {}: {}", sessionId, e.getMessage());
            }
        }
    }
}
