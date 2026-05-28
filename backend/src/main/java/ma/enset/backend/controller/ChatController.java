package ma.enset.backend.controller;

import ma.enset.backend.dto.ChatSessionDTO;
import ma.enset.backend.service.ChatService;
import ma.enset.backend.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat-sessions")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService        chatService;
    private final CurrentUserService currentUserService;

    // ── Sessions ─────────────────────────────────────────────────────────────

    /** GET /api/chat-sessions — list all sessions (summary) */
    @GetMapping
    public ResponseEntity<List<ChatSessionDTO>> getSessions() {
        Long userId = currentUserService.getCurrentUser().getId();
        return ResponseEntity.ok(chatService.getSessions(userId));
    }

    /** GET /api/chat-sessions/{id} — get session with messages */
    @GetMapping("/{id}")
    public ResponseEntity<ChatSessionDTO> getSession(@PathVariable Long id) {
        Long userId = currentUserService.getCurrentUser().getId();
        return ResponseEntity.ok(chatService.getSession(id, userId));
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    /**
     * POST /api/chat-sessions/message
     * Body: { message, intent, sessionId (optional), companyId (optional) }
     * Returns: { sessionId, response, intent }
     */
    @PostMapping("/message")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @RequestBody Map<String, Object> body) {

        Long userId = currentUserService.getCurrentUser().getId();

        String message   = (String)  body.get("message");
        String intent    = (String)  body.get("intent");
        Long   sessionId = body.get("sessionId")  != null
                ? Long.valueOf(body.get("sessionId").toString())  : null;
        Long   companyId = body.get("companyId")  != null
                ? Long.valueOf(body.get("companyId").toString())  : null;

        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Message is required"));
        }

        Map<String, Object> result = chatService.sendMessage(
                userId, sessionId, message, intent, companyId
        );
        return ResponseEntity.ok(result);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /** DELETE /api/chat-sessions/{id} — delete one session + its messages */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable Long id) {
        Long userId = currentUserService.getCurrentUser().getId();
        chatService.deleteSession(id, userId);
        return ResponseEntity.noContent().build();
    }

    /** DELETE /api/chat-sessions — delete ALL sessions for current user */
    @DeleteMapping
    public ResponseEntity<Void> deleteAllSessions() {
        Long userId = currentUserService.getCurrentUser().getId();
        chatService.deleteAllSessions(userId);
        return ResponseEntity.noContent().build();
    }
}
