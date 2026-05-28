package ma.enset.backend.controller;

import ma.enset.backend.dto.WatchlistDTO;
import ma.enset.backend.service.CurrentUserService;
import ma.enset.backend.service.WatchlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistService   watchlistService;
    private final CurrentUserService currentUserService;

    /** GET /api/watchlist — liste toutes les entreprises épinglées */
    @GetMapping
    public ResponseEntity<List<WatchlistDTO>> getWatchlist() {
        Long userId = currentUserService.getCurrentUser().getId();
        return ResponseEntity.ok(watchlistService.getWatchlist(userId));
    }

    /** GET /api/watchlist/ids — liste des companyId épinglés (léger) */
    @GetMapping("/ids")
    public ResponseEntity<List<Long>> getPinnedIds() {
        Long userId = currentUserService.getCurrentUser().getId();
        return ResponseEntity.ok(watchlistService.getPinnedCompanyIds(userId));
    }

    /** GET /api/watchlist/status/{companyId} */
    @GetMapping("/status/{companyId}")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable Long companyId) {
        Long userId = currentUserService.getCurrentUser().getId();
        return ResponseEntity.ok(watchlistService.getStatus(userId, companyId));
    }

    /** POST /api/watchlist/{companyId} — épingle une entreprise */
    @PostMapping("/{companyId}")
    public ResponseEntity<WatchlistDTO> pin(@PathVariable Long companyId) {
        Long userId = currentUserService.getCurrentUser().getId();
        return ResponseEntity.ok(watchlistService.pin(userId, companyId));
    }

    /** DELETE /api/watchlist/{companyId} — dépingle (NE touche PAS aux stratégies) */
    @DeleteMapping("/{companyId}")
    public ResponseEntity<Void> unpin(@PathVariable Long companyId) {
        Long userId = currentUserService.getCurrentUser().getId();
        watchlistService.unpin(userId, companyId);
        return ResponseEntity.noContent().build();
    }
}
