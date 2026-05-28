package ma.enset.backend.controller;

import ma.enset.backend.dto.StrategyDTO;
import ma.enset.backend.dto.StrategyRequestDTO;
import ma.enset.backend.entity.User;
import ma.enset.backend.service.CurrentUserService;
import ma.enset.backend.service.StrategyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Tous les endpoints utilisent CurrentUserService (JWT).
 * Plus aucun userId en @RequestParam — sécurité renforcée.
 */
@RestController
@RequestMapping("/api/strategies")
@RequiredArgsConstructor
public class StrategyController {

    private final StrategyService   strategyService;
    private final CurrentUserService currentUserService;

    /** POST /api/strategies */
    @PostMapping
    public ResponseEntity<StrategyDTO> create(@RequestBody StrategyRequestDTO req) {
        User user = currentUserService.getCurrentUser();
        return ResponseEntity.ok(strategyService.createStrategy(user.getId(), req));
    }

    /** GET /api/strategies — stratégies actives uniquement */
    @GetMapping
    public ResponseEntity<List<StrategyDTO>> getActive() {
        User user = currentUserService.getCurrentUser();
        return ResponseEntity.ok(strategyService.getUserStrategies(user.getId()));
    }

    /** GET /api/strategies/all — actives + archivées */
    @GetMapping("/all")
    public ResponseEntity<List<StrategyDTO>> getAll() {
        User user = currentUserService.getCurrentUser();
        return ResponseEntity.ok(strategyService.getAllUserStrategies(user.getId()));
    }

    /** GET /api/strategies/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<StrategyDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(strategyService.getStrategyById(id));
    }

    /** PATCH /api/strategies/{id}/deactivate */
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        User user = currentUserService.getCurrentUser();
        strategyService.deactivateStrategy(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    /** PATCH /api/strategies/{id}/reactivate */
    @PatchMapping("/{id}/reactivate")
    public ResponseEntity<Void> reactivate(@PathVariable Long id) {
        User user = currentUserService.getCurrentUser();
        strategyService.reactivateStrategy(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    /** DELETE /api/strategies/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        User user = currentUserService.getCurrentUser();
        strategyService.deleteStrategy(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    /** GET /api/strategies/{id}/report — rapport IA JSON */
    @GetMapping("/{id}/report")
    public ResponseEntity<?> getReport(@PathVariable Long id) {
        User user = currentUserService.getCurrentUser();
        return ResponseEntity.ok(strategyService.generateReport(id, user.getId()));
    }

    /** GET /api/strategies/{id}/report/pdf — téléchargement PDF */
    @GetMapping("/{id}/report/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        User user = currentUserService.getCurrentUser();
        byte[] pdf = strategyService.generateReportPdf(id, user.getId());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"strategy-report-" + id + ".pdf\"")
                .body(pdf);
    }
}
