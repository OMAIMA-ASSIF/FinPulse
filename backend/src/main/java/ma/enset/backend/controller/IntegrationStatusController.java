package ma.enset.backend.controller;

import lombok.RequiredArgsConstructor;
import ma.enset.backend.dto.IntegrationStatusDTO;
import ma.enset.backend.service.IngestionPipelineService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integration")
@RequiredArgsConstructor
public class IntegrationStatusController {

    private final IngestionPipelineService ingestionPipelineService;

    @Value("${p1.api.url:http://localhost:8000}")
    private String p1ApiUrl;

    /** Public diagnostic — no auth required. */
    @GetMapping("/status")
    public ResponseEntity<IntegrationStatusDTO> status() {
        try {
            boolean p1Up = ingestionPipelineService.isReachable();
            int companyCount = 0;
            if (p1Up) {
                try {
                    companyCount = ingestionPipelineService.listCompanies().size();
                } catch (Exception e) {
                    return ResponseEntity.ok(IntegrationStatusDTO.builder()
                            .backend("UP")
                            .p1ApiUrl(p1ApiUrl)
                            .p1Reachable(true)
                            .p1CompanyCount(0)
                            .message("P1 joignable mais lecture companies échouée: " + e.getMessage())
                            .build());
                }
            }

            return ResponseEntity.ok(IntegrationStatusDTO.builder()
                    .backend("UP")
                    .p1ApiUrl(p1ApiUrl)
                    .p1Reachable(p1Up)
                    .p1CompanyCount(companyCount)
                    .message(p1Up
                            ? (companyCount > 0
                                ? "P1 OK — " + companyCount + " société(s)"
                                : "P1 OK mais aucune société dans l'API")
                            : "P1 injoignable sur " + p1ApiUrl + " — vérifiez uvicorn :8000 et P1_API_URL Docker")
                    .build());
        } catch (Exception e) {
            return ResponseEntity.ok(IntegrationStatusDTO.builder()
                    .backend("UP")
                    .p1ApiUrl(p1ApiUrl)
                    .p1Reachable(false)
                    .p1CompanyCount(0)
                    .message("Diagnostic erreur: " + e.getMessage())
                    .build());
        }
    }
}
