package ma.enset.backend.controller;

import ma.enset.backend.dto.AlertDTO;
import ma.enset.backend.service.AlertService;
import ma.enset.backend.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;
    private final CurrentUserService currentUserService;

    /** GET /api/alerts?page=0&size=20 */
    @GetMapping
    public ResponseEntity<Page<AlertDTO>> getAlerts(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = currentUserService.getCurrentUser().getId();
        return ResponseEntity.ok(alertService.getAlertsForUser(userId, page, size));
    }

    /** GET /api/alerts/unread */
    @GetMapping("/unread")
    public ResponseEntity<List<AlertDTO>> getUnread() {
        Long userId = currentUserService.getCurrentUser().getId();
        return ResponseEntity.ok(alertService.getUnreadAlertsForUser(userId));
    }

    /** GET /api/alerts/count */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> countUnread() {
        Long userId = currentUserService.getCurrentUser().getId();
        return ResponseEntity.ok(Map.of("unread", alertService.countUnreadAlerts(userId)));
    }

    /** PATCH /api/alerts/{id}/read */
    @PatchMapping("/{id}/read")
    public ResponseEntity<AlertDTO> markAsRead(@PathVariable Long id) {
        Long userId = currentUserService.getCurrentUser().getId();
        return ResponseEntity.ok(alertService.markAsRead(id, userId));
    }

    /** PATCH /api/alerts/read-all */
    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllAsRead() {
        Long userId = currentUserService.getCurrentUser().getId();
        int count = alertService.markAllAsRead(userId);
        return ResponseEntity.ok(Map.of("marked", count));
    }
}
