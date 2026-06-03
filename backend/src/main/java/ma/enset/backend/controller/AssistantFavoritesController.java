package ma.enset.backend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.enset.backend.dto.FavoriteCompanyDTO;
import ma.enset.backend.entity.User;
import ma.enset.backend.service.CurrentUserService;
import ma.enset.backend.service.FavoriteCompanyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/favorites")
@RequiredArgsConstructor
@Slf4j
public class AssistantFavoritesController {

    private final FavoriteCompanyService favoriteService;
    private final CurrentUserService currentUserService;

    public record AddFavoriteRequest(String ticker, String companyName) {}

    @PostMapping
    public ResponseEntity<?> addFavorite(@RequestBody AddFavoriteRequest request) {
        User user = currentUserService.getCurrentUser();
        log.info("POST /api/v2/favorites — Add favorite ticker={} for user={}", request.ticker(), user.getUsername());
        try {
            FavoriteCompanyDTO dto = favoriteService.addFavorite(user.getId(), request.ticker(), request.companyName());
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            log.error("Error adding favorite", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "success", false));
        }
    }

    @GetMapping
    public ResponseEntity<List<FavoriteCompanyDTO>> getFavorites() {
        User user = currentUserService.getCurrentUser();
        log.info("GET /api/v2/favorites — Get favorites for user={}", user.getUsername());
        List<FavoriteCompanyDTO> favorites = favoriteService.getFavorites(user.getId());
        return ResponseEntity.ok(favorites);
    }

    @DeleteMapping("/{ticker}")
    public ResponseEntity<?> removeFavorite(@PathVariable String ticker) {
        User user = currentUserService.getCurrentUser();
        log.info("DELETE /api/v2/favorites/{} — Remove favorite for user={}", ticker, user.getUsername());
        try {
            favoriteService.removeFavorite(user.getId(), ticker);
            return ResponseEntity.ok(Map.of("success", true, "message", "Favori supprimé avec succès"));
        } catch (Exception e) {
            log.error("Error removing favorite", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "success", false));
        }
    }

    @GetMapping("/{ticker}")
    public ResponseEntity<Map<String, Boolean>> isFavorite(@PathVariable String ticker) {
        User user = currentUserService.getCurrentUser();
        log.info("GET /api/v2/favorites/{} — Check if favorite for user={}", ticker, user.getUsername());
        boolean favorite = favoriteService.isFavorite(user.getId(), ticker);
        return ResponseEntity.ok(Map.of("favorite", favorite));
    }
}
