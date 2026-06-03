package ma.enset.backend.sse;

import ma.enset.backend.dto.NciUpdateDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class SseManager {

    private final ObjectMapper objectMapper;

    // clientId → SseEmitter  (thread-safe)
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    // clientId → List of watched tickers
    private final Map<String, List<String>> watchlists = new ConcurrentHashMap<>();

    /**
     * Register a new SSE client.
     *
     * @param clientId  unique identifier for the client (e.g., userId + sessionId)
     * @param tickers   optional list of tickers to filter events
     * @param timeout   emitter timeout in milliseconds
     */
    public SseEmitter registerClient(String clientId, List<String> tickers, long timeout) {
        SseEmitter emitter = new SseEmitter(timeout);

        // Clean up on completion, timeout, or error
        emitter.onCompletion(() -> removeClient(clientId));
        emitter.onTimeout(() -> {
            log.debug("SSE timeout: {}", clientId);
            removeClient(clientId);
        });
        emitter.onError(e -> {
            log.debug("SSE error for client {}: {}", clientId, e.getMessage());
            removeClient(clientId);
        });

        emitters.put(clientId, emitter);
        if (tickers != null && !tickers.isEmpty()) {
            watchlists.put(clientId,
                    new CopyOnWriteArrayList<>(tickers.stream().map(String::toUpperCase).toList()));
        }

        // Send a "connected" handshake event
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"status\":\"connected\",\"clientId\":\"" + clientId + "\"}"));
        } catch (IOException e) {
            log.warn("Could not send connect event to {}: {}", clientId, e.getMessage());
        }

        log.info("SSE client registered: {} (watching: {})", clientId,
                tickers != null ? tickers : "ALL");
        return emitter;
    }

    /**
     * Broadcast an NCI update event to all interested clients.
     */
    public void broadcastNciUpdate(NciUpdateDTO event) {
        if (emitters.isEmpty()) return;

        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("Failed to serialize NCI event: {}", e.getMessage());
            return;
        }

        List<String> toRemove = new CopyOnWriteArrayList<>();

        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            String clientId = entry.getKey();
            SseEmitter emitter = entry.getValue();

            // Filter: only send to clients watching this ticker (or watching ALL)
            List<String> clientWatchlist = watchlists.get(clientId);
            if (clientWatchlist != null && !clientWatchlist.isEmpty()
                    && !clientWatchlist.contains(event.getTicker().toUpperCase())) {
                continue;
            }

            try {
                emitter.send(SseEmitter.event()
                        .name("nci-update")
                        .id(String.valueOf(System.currentTimeMillis()))
                        .data(jsonPayload));
            } catch (IOException e) {
                log.debug("Failed to send to client {}, removing: {}", clientId, e.getMessage());
                toRemove.add(clientId);
            }
        }

        toRemove.forEach(this::removeClient);
    }

    /**
     * Send a heartbeat ping to all clients to keep connections alive.
     */
    public void sendHeartbeat() {
        if (emitters.isEmpty()) return;

        List<String> toRemove = new CopyOnWriteArrayList<>();
        String heartbeat = "{\"type\":\"heartbeat\",\"ts\":" + System.currentTimeMillis() + "}";

        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            try {
                entry.getValue().send(SseEmitter.event().name("heartbeat").data(heartbeat));
            } catch (IOException e) {
                toRemove.add(entry.getKey());
            }
        }

        toRemove.forEach(this::removeClient);
        if (!toRemove.isEmpty()) {
            log.debug("Removed {} stale SSE clients during heartbeat", toRemove.size());
        }
    }

    public int getActiveClientCount() {
        return emitters.size();
    }

    private void removeClient(String clientId) {
        emitters.remove(clientId);
        watchlists.remove(clientId);
        log.debug("SSE client removed: {}", clientId);
    }
}
