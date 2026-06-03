package ma.enset.backend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.enset.backend.entity.User;
import ma.enset.backend.entity.UserStrategy;
import ma.enset.backend.service.CurrentUserService;
import ma.enset.backend.service.UserStrategyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v2/strategy")
@RequiredArgsConstructor
@Slf4j
public class AssistantStrategyController {

    private final UserStrategyService strategyService;
    private final CurrentUserService currentUserService;

    public record StrategyDTO(
            Long id, String ticker, String companyName, String userArgument,
            Double nciGlobal, Double nciPersonalized, Double fConsistency,
            Boolean isActive, LocalDateTime createdAt
    ) {}

    public record SaveStrategyRequest(
            String  ticker,
            String  companyName,
            String  userArgument,
            Double  nciGlobal,
            Double  nciPersonalized,
            Double  fConsistency,
            Double  sentiment,
            String  supportEvidence,
            String  redFlags,
            String  finalConclusion
    ) {}

    public record SaveStrategyResponse(
            boolean success,
            Long    strategyId,
            String  message
    ) {}

    @PostMapping("/save")
    public ResponseEntity<SaveStrategyResponse> saveStrategy(@RequestBody SaveStrategyRequest request) {
        log.info("POST /api/v2/strategy/save — ticket={}", request.ticker());

        try {
            User user = currentUserService.getCurrentUser();

            // Sauvegarde en base
            UserStrategy strategy = strategyService.createStrategy(
                    user.getId(),
                    request.ticker(),
                    request.companyName(),
                    request.userArgument(),
                    request.nciGlobal(),
                    request.nciPersonalized(),
                    request.fConsistency(),
                    request.supportEvidence(),
                    request.redFlags(),
                    request.sentiment(),
                    request.finalConclusion(),
                    null // path PDF non utilisé côté serveur car on sauvegarde dans SavedReport si besoin
            );

            log.info("Stratégie enregistrée: id={}, user={}", strategy.getId(), user.getUsername());

            return ResponseEntity.ok(new SaveStrategyResponse(
                    true,
                    strategy.getId(),
                    "Stratégie enregistrée avec succès"
            ));

        } catch (Exception e) {
            log.error("Erreur sauvegarde stratégie", e);
            return ResponseEntity.internalServerError()
                    .body(new SaveStrategyResponse(false, null, e.getMessage()));
        }
    }

    @GetMapping("/my-strategies")
    public ResponseEntity<List<StrategyDTO>> getUserStrategies() {
        try {
            User user = currentUserService.getCurrentUser();

            List<StrategyDTO> dtos = strategyService
                    .getActiveStrategiesByUser(user.getId())
                    .stream()
                    .map(s -> new StrategyDTO(
                            s.getId(),
                            s.getCompany().getTicker(),
                            s.getCompany().getName(),
                            s.getUserArgument(),
                            s.getCompany().getNciGlobal() != null ? s.getCompany().getNciGlobal().doubleValue() : null,
                            s.getNciPersonalized() != null ? s.getNciPersonalized().doubleValue() : null,
                            null, // fConsistency (Not stored in backend UserStrategy table)
                            s.getIsActive(),
                            s.getCreatedAt()
                    ))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(dtos);

        } catch (Exception e) {
            log.error("Erreur récupération stratégies", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{strategyId}")
    public ResponseEntity<String> deactivateStrategy(@PathVariable Long strategyId) {
        try {
            User user = currentUserService.getCurrentUser();

            // Vérifier propriété
            boolean owned = strategyService.getActiveStrategiesByUser(user.getId())
                    .stream().anyMatch(s -> s.getId().equals(strategyId));

            if (!owned) {
                return ResponseEntity.status(403).body("Accès refusé");
            }

            strategyService.deactivateStrategy(strategyId);
            return ResponseEntity.ok("Stratégie désactivée");

        } catch (Exception e) {
            log.error("Erreur désactivation", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
