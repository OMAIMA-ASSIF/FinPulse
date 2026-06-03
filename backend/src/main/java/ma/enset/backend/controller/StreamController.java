package ma.enset.backend.controller;

import ma.enset.backend.sse.SseManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/stream")
@RequiredArgsConstructor
@Slf4j
public class StreamController {

    private final SseManager sseManager;



    @GetMapping(value = "/watchlist", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamWatchlist(
            @RequestParam(required = false, defaultValue = "anonymous") String userId,
            @RequestParam(required = false) String tickers,
            @RequestParam(required = false, defaultValue = "300000") long timeout) {

        String clientId = userId + "-" + UUID.randomUUID().toString().substring(0, 8);

        List<String> tickerList = null;
        if (tickers != null && !tickers.isBlank()) {
            tickerList = Arrays.stream(tickers.split(","))
                    .map(String::trim)
                    .filter(t -> !t.isBlank())
                    .toList();
        }

        log.info("SSE connection opened: clientId={} tickers={}", clientId, tickerList);
        return sseManager.registerClient(clientId, tickerList, timeout);
    }

    /**
     * GET /api/stream/status — how many clients are currently connected
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "activeClients", sseManager.getActiveClientCount(),
                "status", "running"
        ));
    }
}
