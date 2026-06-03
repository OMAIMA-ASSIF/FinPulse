package ma.enset.backend.controller;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.enset.backend.entity.User;
import ma.enset.backend.orchestrator.MultiAgentOrchestrator;
import ma.enset.backend.service.CurrentUserService;

/**
 * Contrôleur pour l'assistant multi-agent FinPulse.
 * Expose l'endpoint /api/v2/assistant/chat pour l'orchestration multi-agent.
 *
 * Modes de fonctionnement:
 * - CHATBOT: conversation simple basée sur les données SEC
 * - REPORT: génération d'un rapport d'analyse stratégique avec PDF
 * - CLARIFICATION: demande de clarification si l'argument est insuffisant
 * - OUT_OF_SCOPE: si la question est hors sujet
 */
@RestController
@RequestMapping("/api/v2/assistant")
@RequiredArgsConstructor
@Slf4j
public class AssistantController {

    private final CurrentUserService currentUserService;
    private final MultiAgentOrchestrator orchestrator;

    /**
     * POST /api/v2/assistant/chat
     *
     * Point d'entrée principal pour l'orchestrateur multi-agent.
     * Gère l'analyse financière intelligente avec les 5 agents.
     *
     * Request body:
     * {
     *   "message": "Crois-tu que Tesla va bien se porter avec la révolution de l'IA?",
     *   "ticker": "TSLA" (optional - sera extrait du message si absent),
     *   "conversationId": "uuid-conversation" (optional - pour maintenir le contexte)
     * }
     *
     * Response:
     *
     * MODE CHATBOT (réponse conversationnelle):
     * {
     *   "message": "Texte de réponse",
     *   "mode": "CHATBOT",
     *   "success": true,
     *   "conversationId": "uuid"
     * }
     *
     * MODE CLARIFICATION (besoin d'informations):
     * {
     *   "message": "Pourriez-vous être plus précis sur...?",
     *   "mode": "CLARIFICATION",
     *   "success": true,
     *   "conversationId": "uuid"
     * }
     *
     * MODE REPORT (génération PDF avec analyse complète):
     * Content-Type: application/pdf
     * Headers:
     *   X-Report-Ticker: TSLA
     *   X-Report-Company: Tesla Inc.
     *   X-NCI-Global: 0.75
     *   X-NCI-Personalized: 0.62
     *   X-F-Consistency: 0.45
     *   X-Sentiment: 0.80
     * Body: [PDF binaire]
     *
     * @param request Requête contenant message, ticker (optional), conversationId (optional)
     * @return Réponse selon le mode de l'orchestrateur
     */
    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, String> request) {

        String message = request.get("message");
        String ticker = request.get("ticker");
        String conversationId = request.get("conversationId");

        // Validation
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error", "Message is required",
                            "success", false
                    ));
        }

        try {
            // Récupère l'utilisateur courant (via Keycloak)
            User user = currentUserService.getCurrentUser();
            log.info("Assistant request: user={}, ticker={}, message_len={}",
                    user.getUsername(), ticker, message.length());

            // Lance l'orchestration
            MultiAgentOrchestrator.OrchestratorResult result =
                    orchestrator.handleMessage(user, ticker, message, conversationId);

            // Routage selon le mode de réponse
            return switch (result.mode()) {

                // ============= MODE CHATBOT =============
                // Réponse conversationnelle simple basée sur les données SEC
                case "CHATBOT" -> ResponseEntity.ok(
                        Map.of(
                                "message", result.textResponse(),
                                "success", true,
                                "mode", "CHATBOT",
                                "conversationId", result.conversationId() != null ? result.conversationId() : ""
                        )
                );

                // ============= MODE CLARIFICATION =============
                // L'utilisateur doit clarifier son argument pour générer un rapport
                case "CLARIFICATION" -> ResponseEntity.ok(
                        Map.of(
                                "message", result.textResponse(),
                                "success", true,
                                "mode", "CLARIFICATION",
                                "conversationId", result.conversationId() != null ? result.conversationId() : ""
                        )
                );

                // ============= MODE REPORT =============
                // Génération complète: PDF + données d'analyse
                case "REPORT" -> {
                    MultiAgentOrchestrator.ReportResult report = result.reportResult();
                    log.info("Generating report for {} ({} bytes)",
                            report.ticker(), report.pdfBytes().length);

                    yield ResponseEntity.ok()
                            // Métadonnées du rapport
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=rapport_" + report.ticker() + ".pdf")
                            .header("X-Report-Ticker", report.ticker())
                            .header("X-Report-Company", report.companyName())
                            .header("X-NCI-Global", String.valueOf(report.nciGlobal()))
                            .header("X-NCI-Personalized", String.valueOf(report.nciPersonalized()))
                            .header("X-F-Consistency", String.valueOf(report.fConsistency()))
                            .header("X-Sentiment", String.valueOf(report.sentiment()))
                            // Contenu PDF binaire
                            .contentType(MediaType.APPLICATION_PDF)
                            .body(report.pdfBytes());
                }

                // Par défaut
                default -> ResponseEntity.ok(
                        Map.of(
                                "message", result.textResponse(),
                                "success", true,
                                "mode", result.mode(),
                                "conversationId", result.conversationId() != null ? result.conversationId() : ""
                        )
                );
            };

        } catch (Exception e) {
            log.error("Error in assistant chat", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "error", "An error occurred: " + e.getMessage(),
                            "success", false
                    ));
        }
    }

    // =====================================================================
    // METADONNÉES DU RAPPORT
    // =====================================================================
    public record ReportMetadataRequest(String ticker, String userArgument) {}

    public record ReportMetadataResponse(
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

    @PostMapping("/report-metadata")
    public ResponseEntity<?> getReportMetadata(@RequestBody ReportMetadataRequest request) {
        log.info("POST /report-metadata — ticket={}", request.ticker());
        try {
            User user = currentUserService.getCurrentUser();
            MultiAgentOrchestrator.ReportResult report =
                    orchestrator.generateStrategyReport(user, request.ticker(), request.userArgument());

            return ResponseEntity.ok(new ReportMetadataResponse(
                    report.ticker(),
                    report.companyName(),
                    report.userArgument(),
                    report.nciGlobal(),
                    report.nciPersonalized(),
                    report.fConsistency(),
                    report.sentiment(),
                    report.supportPoints(), // note this is called supportPoints in backend's ReportResult
                    report.redFlags(),
                    report.finalConclusion()
            ));
        } catch (Exception e) {
            log.error("Error in report-metadata", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage(), "success", false));
        }
    }

    // =====================================================================
    // VÉRIFICATION ENTREPRISE
    // =====================================================================
    public record CompanyCheckRequest(String ticker) {}

    public record CompanyCheckResponse(
            boolean exists,
            String  message,
            String  status
    ) {}

    @PostMapping("/check-company")
    public ResponseEntity<CompanyCheckResponse> checkCompany(@RequestBody CompanyCheckRequest request) {
        log.info("POST /check-company — ticker={}", request.ticker());
        try {
            currentUserService.getCurrentUser(); // validation auth
            boolean exists = orchestrator.checkIfCompanyExists(request.ticker());

            if (!exists) {
                return ResponseEntity.ok(new CompanyCheckResponse(
                        false,
                        "L'entreprise " + request.ticker() + " n'est pas encore disponible. Ingestion en cours...",
                        "PENDING"
                ));
            }

            return ResponseEntity.ok(new CompanyCheckResponse(
                    true,
                    "Entreprise disponible",
                    null
            ));
        } catch (Exception e) {
            log.error("Error in check-company", e);
            return ResponseEntity.internalServerError()
                    .body(new CompanyCheckResponse(false, e.getMessage(), null));
        }
    }
}
