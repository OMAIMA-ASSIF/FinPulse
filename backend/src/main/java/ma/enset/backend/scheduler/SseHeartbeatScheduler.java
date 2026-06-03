package ma.enset.backend.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import ma.enset.backend.sse.SseManager;

/** Keeps SSE connections alive for the watchlist stream. */
@Component
@RequiredArgsConstructor
public class SseHeartbeatScheduler {

    private final SseManager sseManager;

    @Scheduled(fixedDelayString = "${sse.heartbeat-interval:15000}")
    public void sendHeartbeat() {
        sseManager.sendHeartbeat();
    }
}
